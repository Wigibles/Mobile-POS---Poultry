package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.PendingBorrowEntity
import com.example.data.local.PendingCashOutEntity
import com.example.data.local.PendingTransactionEntity
import com.example.data.local.PendingTransactionItemEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "FirestorePOSRepo"

/**
 * Cloud-synced repository backed by Firestore.
 * All read operations return Flows that emit updates in real-time across devices.
 * Write operations persist to the cloud and propagate to all connected clients.
 *
 * **Offline support:** When the device has no internet, transactions are saved to a local
 * Room database and automatically pushed to Firestore when connectivity returns. Product
 * reads are cached by Firestore's built-in offline persistence so the catalog is always
 * available.
 *
 * Reliability guarantees:
 *  - IDs are allocated atomically via a Firestore transaction (no duplicate/overwritten records).
 *  - Multi-document writes (checkout, void, product edits) are committed as atomic batches,
 *    so a sale never leaves a half-written transaction behind.
 *  - Read and write failures are surfaced through [errors] instead of being silently swallowed.
 */
class FirestorePOSRepository(
    private val context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // ── Error surface — collected by the ViewModel and shown to the user ──
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private fun reportListenerError(what: String, error: Throwable) {
        Log.e(TAG, "$what listener failed", error)
        _errors.tryEmit("Couldn't sync $what: ${error.message ?: "unknown error"}")
    }

    // ── Offline-support infrastructure ──
    private val localDb = AppDatabase.getInstance(context)
    private val pendingDao = localDb.pendingTransactionDao()
    val networkMonitor = NetworkMonitor.getInstance(context)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Google Sheets sync manager — queue & flush transaction rows to Sheets. */
    val sheetSyncManager = SheetSyncManager(pendingDao, networkMonitor)

    /** How many unsynced transactions are waiting to be pushed. */
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    /** Whether a sync of pending offline transactions is currently in progress. */
    private val _isOfflineSyncing = MutableStateFlow(false)
    val isOfflineSyncing: StateFlow<Boolean> = _isOfflineSyncing.asStateFlow()

    init {
        // Enable Firestore's built-in offline persistence so product catalog reads are
        // cached locally and simple writes (categories, auth, cash-outs, borrows) are
        // queued automatically when offline.
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        db.firestoreSettings = settings

        // Keep the pending-count flow up to date for the UI (excludes sheet entries).
        syncScope.launch {
            pendingDao.userPendingCount().collect { _pendingSyncCount.value = it }
        }

        // Auto-sync whenever the device comes back online.
        syncScope.launch {
            networkMonitor.onReconnected.collect { connected ->
                if (connected) {
                    syncPendingTransactions()
                    sheetSyncManager.syncPendingSheetEntries()
                }
            }
        }

        // Also attempt a sync at startup — catches any pending transactions that
        // accumulated before the app was restarted.
        syncScope.launch {
            syncPendingTransactions()
            sheetSyncManager.syncPendingSheetEntries()
        }
    }

    // ── Products ──
    val products: Flow<List<Product>> = callbackFlow {
        val listener = db.collection(PRODUCTS)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("products", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toProduct() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Variations ──
    val variations: Flow<List<ProductVariation>> = callbackFlow {
        val listener = db.collection(VARIATIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("variations", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toVariation() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Categories ──
    val categories: Flow<List<Category>> = callbackFlow {
        val listener = db.collection(CATEGORIES)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("categories", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toCategory() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Transactions ──
    // Bounded to the most recent RECENT_TRANSACTIONS_LIMIT records — see the constant's doc
    // comment for why this is a safety cap rather than true pagination.
    val transactions: Flow<List<TransactionRecord>> = callbackFlow {
        val listener = db.collection(TRANSACTIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(RECENT_TRANSACTIONS_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("transactions", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toTransaction() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Transaction Items ──
    // Bounded the same way as [transactions]. Ordered by id (assigned sequentially at sale
    // time, like timestamp) so this window stays aligned with the transactions window above.
    val allTransactionItems: Flow<List<TransactionItem>> = callbackFlow {
        val listener = db.collection(TRANSACTION_ITEMS)
            .orderBy("id", Query.Direction.DESCENDING)
            .limit(RECENT_TRANSACTION_ITEMS_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("transaction items", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toTransactionItem() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Auth Settings (Admin/Cashier PINs) ──
    // Single doc at settings/auth. A missing doc emits defaults without writing —
    // avoids two devices racing to "create" it on first read; it's only written when
    // Admin explicitly changes a PIN.
    val authSettings: Flow<AuthSettings> = callbackFlow {
        val listener = db.collection(SETTINGS).document(AUTH_DOC)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("auth settings", error); return@addSnapshotListener }
                trySend(snapshot?.toAuthSettings() ?: AuthSettings())
            }
        awaitClose { listener.remove() }
    }

    // Written as one atomic doc (not two merged partial writes) since the security rule
    // requires both adminPin and cashierPin to be present on every write to this doc.
    suspend fun updatePins(adminPin: String, cashierPin: String) = withContext(Dispatchers.IO) {
        db.collection(SETTINGS).document(AUTH_DOC)
            .set(mapOf("adminPin" to adminPin, "cashierPin" to cashierPin)).await()
    }

    // ── Cash-Out Log ──
    // Bounded the same way as [transactions] — see RECENT_TRANSACTIONS_LIMIT's doc comment.
    val cashOutEntries: Flow<List<CashOutEntry>> = callbackFlow {
        val listener = db.collection(CASH_OUTS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(RECENT_CASH_OUTS_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("cash-out log", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toCashOutEntry() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun addCashOutEntry(entry: CashOutEntry): Int = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline.value) {
            val localId = pendingDao.insertCashOut(
                PendingCashOutEntity(
                    cashierName = entry.cashierName,
                    amount = entry.amount.roundToCentavos(),
                    note = entry.note,
                    createdAt = entry.timestamp
                )
            )
            Log.i(TAG, "Queued cash-out locally (localId=$localId) — will sync when online")
            return@withContext (-localId).toInt()
        }
        val id = nextId(CASH_OUTS)
        db.collection(CASH_OUTS).document("$id").set(entry.copy(id = id).toMap()).await()
        id
    }

    suspend fun updateCashOutEntry(entry: CashOutEntry) = withContext(Dispatchers.IO) {
        db.collection(CASH_OUTS).document("${entry.id}").set(entry.toMap()).await()
    }

    suspend fun deleteCashOutEntry(entry: CashOutEntry) = withContext(Dispatchers.IO) {
        db.collection(CASH_OUTS).document("${entry.id}").delete().await()
    }

    // ── Family Borrowing Log ──
    // Bounded the same way as [transactions] — see RECENT_TRANSACTIONS_LIMIT's doc comment.
    val borrowEntries: Flow<List<BorrowEntry>> = callbackFlow {
        val listener = db.collection(BORROWS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(RECENT_BORROWS_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("borrowing log", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toBorrowEntry() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun addBorrowEntry(entry: BorrowEntry): Int = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline.value) {
            val localId = pendingDao.insertBorrow(
                PendingBorrowEntity(
                    borrowerName = entry.borrowerName,
                    amount = entry.amount.roundToCentavos(),
                    note = entry.note,
                    createdAt = entry.timestamp
                )
            )
            Log.i(TAG, "Queued borrow entry locally (localId=$localId) — will sync when online")
            return@withContext (-localId).toInt()
        }
        val id = nextId(BORROWS)
        db.collection(BORROWS).document("$id").set(entry.copy(id = id).toMap()).await()
        id
    }

    suspend fun updateBorrowEntry(entry: BorrowEntry) = withContext(Dispatchers.IO) {
        db.collection(BORROWS).document("${entry.id}").set(entry.toMap()).await()
    }

    suspend fun markBorrowReturned(id: Int) = withContext(Dispatchers.IO) {
        db.collection(BORROWS).document("$id")
            .update(mapOf("returnedTimestamp" to System.currentTimeMillis())).await()
    }

    suspend fun deleteBorrowEntry(entry: BorrowEntry) = withContext(Dispatchers.IO) {
        db.collection(BORROWS).document("${entry.id}").delete().await()
    }

    // ── Store Operational Expenses Log ──
    val operationalExpenses: Flow<List<OperationalExpense>> = callbackFlow {
        val listener = db.collection(OPERATIONAL_EXPENSES)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(RECENT_OPERATIONAL_EXPENSES_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("operational expenses", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toOperationalExpense() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun addOperationalExpense(entry: OperationalExpense): Int = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline.value) {
            val localId = pendingDao.insertOperationalExpense(
                com.example.data.local.PendingOperationalExpenseEntity(
                    title = entry.title,
                    amount = entry.amount.roundToCentavos(),
                    note = entry.note,
                    createdAt = entry.timestamp
                )
            )
            Log.i(TAG, "Queued operational expense locally (localId=$localId) — will sync when online")
            return@withContext (-localId).toInt()
        }
        val id = nextId(OPERATIONAL_EXPENSES)
        db.collection(OPERATIONAL_EXPENSES).document("$id").set(entry.copy(id = id).toMap()).await()
        id
    }

    suspend fun updateOperationalExpense(entry: OperationalExpense) = withContext(Dispatchers.IO) {
        db.collection(OPERATIONAL_EXPENSES).document("${entry.id}").set(entry.toMap()).await()
    }

    suspend fun deleteOperationalExpense(entry: OperationalExpense) = withContext(Dispatchers.IO) {
        db.collection(OPERATIONAL_EXPENSES).document("${entry.id}").delete().await()
    }

    fun getVariationsForProduct(productId: Int): Flow<List<ProductVariation>> = callbackFlow {
        val listener = db.collection(VARIATIONS)
            .whereEqualTo("productId", productId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("variations", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toVariation() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun getTransactionItems(transactionId: Int): Flow<List<TransactionItem>> = callbackFlow {
        val listener = db.collection(TRANSACTION_ITEMS)
            .whereEqualTo("transactionId", transactionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("transaction items", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toTransactionItem() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Insert Product ── (product + variations committed atomically)
    suspend fun insertProduct(product: Product, variationsList: List<ProductVariation>): Int =
        withContext(Dispatchers.IO) {
            ensureCategoryExists(product.category)
            val productId = nextId(PRODUCTS)
            val variationIds = reserveIds(VARIATIONS, variationsList.size)

            val batch = db.batch()
            batch.set(db.collection(PRODUCTS).document("$productId"), product.copy(id = productId).toMap())
            variationsList.forEachIndexed { i, v ->
                val vId = variationIds[i]
                batch.set(
                    db.collection(VARIATIONS).document("$vId"),
                    v.copy(id = vId, productId = productId).toMap()
                )
            }
            batch.commit().await()
            productId
        }

    // ── Update Product ── (product + full variation replacement committed atomically)
    suspend fun updateProduct(product: Product, variationsList: List<ProductVariation>) =
        withContext(Dispatchers.IO) {
            ensureCategoryExists(product.category)
            val oldVars = db.collection(VARIATIONS)
                .whereEqualTo("productId", product.id).get().await()
            val variationIds = reserveIds(VARIATIONS, variationsList.size)

            val batch = db.batch()
            batch.set(db.collection(PRODUCTS).document("${product.id}"), product.toMap())
            oldVars.documents.forEach { batch.delete(it.reference) }
            variationsList.forEachIndexed { i, v ->
                val vId = variationIds[i]
                batch.set(
                    db.collection(VARIATIONS).document("$vId"),
                    v.copy(id = vId, productId = product.id).toMap()
                )
            }
            batch.commit().await()
        }

    // ── Delete Product ── (product + its variations committed atomically)
    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        val vars = db.collection(VARIATIONS).whereEqualTo("productId", product.id).get().await()
        val batch = db.batch()
        batch.delete(db.collection(PRODUCTS).document("${product.id}"))
        vars.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    // ── Categories ──
    suspend fun insertCategory(category: Category): Boolean = withContext(Dispatchers.IO) {
        val existing = categories.first()
        if (existing.any { it.name.equals(category.name, ignoreCase = true) }) {
            return@withContext false
        }
        val nextId = nextId(CATEGORIES)
        db.collection(CATEGORIES).document("$nextId")
            .set(mapOf("id" to nextId, "name" to category.name)).await()
        true
    }

    suspend fun deleteCategory(category: Category): Boolean = withContext(Dispatchers.IO) {
        val prods = db.collection(PRODUCTS)
            .whereEqualTo("category", category.name).get().await()
        if (!prods.isEmpty) return@withContext false
        db.collection(CATEGORIES).document("${category.id}").delete().await()
        true
    }

    private suspend fun ensureCategoryExists(categoryName: String) {
        if (categoryName.isBlank()) return
        val existing = categories.first()
        if (existing.none { it.name.equals(categoryName, ignoreCase = true) }) {
            val nextId = nextId(CATEGORIES)
            db.collection(CATEGORIES).document("$nextId")
                .set(mapOf("id" to nextId, "name" to categoryName)).await()
        }
    }

    // ── Process Transaction ──
    // Records the sale. When online this is a single atomic Firestore batch with
    // server-assigned IDs. When offline the transaction is saved to the local Room
    // database and will be pushed automatically when connectivity returns.
    // After writing to Firestore, the transaction is also queued for Google Sheets sync.
    suspend fun processTransaction(
        transaction: TransactionRecord,
        items: List<TransactionItem>
    ): Int = withContext(Dispatchers.IO) {
        val online = networkMonitor.isOnline.value

        // Compute daily order number: count today's transactions + 1
        val dailyOrderNumber = if (online) {
            countTodayTransactions() + 1
        } else {
            0 // can't query Firestore offline; sheet will show "—"
        }

        // Build sheet payload regardless of path — enqueued after success
        val sheetPayload = buildSheetPayload(transaction, items, dailyOrderNumber)

        if (!online) {
            // ── OFFLINE PATH: save to local Room queue ──
            val rounded = transaction.copy(
                subtotal = transaction.subtotal.roundToCentavos(),
                tax = transaction.tax.roundToCentavos(),
                discount = transaction.discount.roundToCentavos(),
                totalAmount = transaction.totalAmount.roundToCentavos()
            )
            val txEntity = PendingTransactionEntity(
                customerName = rounded.customerName,
                status = rounded.status,
                subtotal = rounded.subtotal,
                tax = rounded.tax,
                discount = rounded.discount,
                totalAmount = rounded.totalAmount,
                createdAt = rounded.timestamp
            )
            val itemEntities = items.map { item ->
                PendingTransactionItemEntity(
                    pendingTransactionLocalId = 0, // filled by enqueueTransaction
                    productId = item.productId,
                    productName = item.productName,
                    variationName = item.variationName,
                    price = item.price.roundToCentavos(),
                    quantity = item.quantity,
                    multiplier = item.multiplier
                )
            }
            val localId = pendingDao.enqueueTransaction(txEntity, itemEntities)
            Log.i(TAG, "Queued transaction locally (localId=$localId) — will sync when online")

            // Queue sheet entry too (uses negative localId as transaction reference)
            sheetPayload.put("transactionId", (-localId).toString())
            sheetSyncManager.enqueueSheetEntry((-localId).toInt(), sheetPayload)

            // Return a negative id as a signal that this is a local-only record for now.
            (-localId).toInt()
        } else {
            // ── ONLINE PATH: existing atomic Firestore write ──
            val txId = nextId(TRANSACTIONS)
            val itemIds = reserveIds(TRANSACTION_ITEMS, items.size)

            val roundedTx = transaction.copy(
                id = txId,
                subtotal = transaction.subtotal.roundToCentavos(),
                tax = transaction.tax.roundToCentavos(),
                discount = transaction.discount.roundToCentavos(),
                totalAmount = transaction.totalAmount.roundToCentavos()
            )

            val batch = db.batch()
            batch.set(db.collection(TRANSACTIONS).document("$txId"), roundedTx.toMap())
            items.forEachIndexed { i, item ->
                val itemId = itemIds[i]
                batch.set(
                    db.collection(TRANSACTION_ITEMS).document("$itemId"),
                    item.copy(id = itemId, transactionId = txId, price = item.price.roundToCentavos()).toMap()
                )
            }

            // Deduct stock for products with supply tracking (quantity * multiplier)
            applySupplyDeduction(batch, items.map { it.productId to (it.quantity * it.multiplier) })

            batch.commit().await()

            // Send to Google Sheets (queued if offline / send fails)
            sheetPayload.put("transactionId", txId.toString())
            sheetSyncManager.enqueueSheetEntry(txId, sheetPayload)

            txId
        }
    }

    // ── Sync Pending Transactions ──
    // Pushes all unsynced local transactions to Firestore in FIFO order. Each transaction
    // gets a fresh server-assigned ID and its stock is deducted atomically. On success the
    // local copy is deleted; on failure it stays queued for the next retry.
    suspend fun syncPendingTransactions(): Int = withContext(Dispatchers.IO) {
        if (_isOfflineSyncing.value) return@withContext 0 // guard: only one sync at a time
        _isOfflineSyncing.value = true
        var synced = 0
        try {
            val unsynced = pendingDao.getUnsyncedTransactions()
            if (unsynced.isEmpty()) {
                Log.d(TAG, "No pending transactions to sync")
                return@withContext 0
            }
            Log.i(TAG, "Starting sync of ${unsynced.size} pending transaction(s)")

            for (tx in unsynced) {
                try {
                    val items = pendingDao.getItemsForTransaction(tx.localId)
                    if (items.isEmpty()) {
                        // Orphaned — clean up
                        pendingDao.deleteById(tx.localId)
                        continue
                    }

                    // Reserve server-side IDs
                    val txId = nextId(TRANSACTIONS)
                    val itemIds = reserveIds(TRANSACTION_ITEMS, items.size)

                    val batch = db.batch()
                    batch.set(
                        db.collection(TRANSACTIONS).document("$txId"),
                        mapOf(
                            "id" to txId,
                            "customerName" to tx.customerName,
                            "timestamp" to tx.createdAt,
                            "status" to tx.status,
                            "subtotal" to tx.subtotal,
                            "tax" to tx.tax,
                            "discount" to tx.discount,
                            "totalAmount" to tx.totalAmount,
                            "settledTimestamp" to null
                        )
                    )
                    items.forEachIndexed { i, item ->
                        val itemId = itemIds[i]
                        batch.set(
                            db.collection(TRANSACTION_ITEMS).document("$itemId"),
                            mapOf(
                                "id" to itemId,
                                "transactionId" to txId,
                                "productId" to item.productId,
                                "productName" to item.productName,
                                "variationName" to item.variationName,
                                "price" to item.price,
                                "quantity" to item.quantity,
                                "multiplier" to item.multiplier
                            )
                        )
                    }

                    // Deduct stock for products with supply tracking (quantity * multiplier)
                    applySupplyDeduction(batch, items.map { it.productId to (it.quantity * it.multiplier) })

                    batch.commit().await()
                    pendingDao.deleteById(tx.localId)
                    synced++
                    Log.i(TAG, "Synced transaction localId=${tx.localId} → Firestore id=$txId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync transaction localId=${tx.localId}; will retry later", e)
                    // Stop on first failure — remaining transactions stay queued for next attempt
                    break
                }
            }

            // ── Sync pending cash-outs ──
            val unsyncedCashOuts = pendingDao.getUnsyncedCashOuts()
            for (co in unsyncedCashOuts) {
                try {
                    val id = nextId(CASH_OUTS)
                    db.collection(CASH_OUTS).document("$id").set(
                        mapOf(
                            "id" to id,
                            "cashierName" to co.cashierName,
                            "amount" to co.amount,
                            "note" to co.note,
                            "timestamp" to co.createdAt
                        )
                    ).await()
                    pendingDao.deleteCashOutById(co.localId)
                    synced++
                    Log.i(TAG, "Synced cash-out localId=${co.localId} → Firestore id=$id")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync cash-out localId=${co.localId}; will retry later", e)
                    break
                }
            }

            // ── Sync pending borrows ──
            val unsyncedBorrows = pendingDao.getUnsyncedBorrows()
            for (br in unsyncedBorrows) {
                try {
                    val id = nextId(BORROWS)
                    db.collection(BORROWS).document("$id").set(
                        mapOf(
                            "id" to id,
                            "borrowerName" to br.borrowerName,
                            "amount" to br.amount,
                            "note" to br.note,
                            "timestamp" to br.createdAt,
                            "returnedTimestamp" to null
                        )
                    ).await()
                    pendingDao.deleteBorrowById(br.localId)
                    synced++
                    Log.i(TAG, "Synced borrow localId=${br.localId} → Firestore id=$id")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync borrow localId=${br.localId}; will retry later", e)
                    break
                }
            }

            // ── Sync pending operational expenses ──
            val unsyncedOperational = pendingDao.getUnsyncedOperationalExpenses()
            for (oe in unsyncedOperational) {
                try {
                    val id = nextId(OPERATIONAL_EXPENSES)
                    db.collection(OPERATIONAL_EXPENSES).document("$id").set(
                        mapOf(
                            "id" to id,
                            "title" to oe.title,
                            "amount" to oe.amount,
                            "note" to oe.note,
                            "timestamp" to oe.createdAt
                        )
                    ).await()
                    pendingDao.deleteOperationalExpenseById(oe.localId)
                    synced++
                    Log.i(TAG, "Synced operational expense localId=${oe.localId} → Firestore id=$id")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync operational expense localId=${oe.localId}; will retry later", e)
                    break
                }
            }
        } finally {
            _isOfflineSyncing.value = false
        }
        synced
    }

    // ── Mark Transaction as Paid ──
    suspend fun markTransactionAsPaid(transactionId: Int) = withContext(Dispatchers.IO) {
        db.collection(TRANSACTIONS).document("$transactionId")
            .update(mapOf("status" to "PAID", "settledTimestamp" to System.currentTimeMillis())).await()

        // Also update Google Sheets: find the row by transaction ID and set Status → PAID
        sheetSyncManager.enqueueStatusUpdate(transactionId, "PAID")
    }

    // ── Resync All Transactions to Google Sheets ──
    // Fetches every transaction from Firestore and sends them to the sheet.
    // The Apps Script handles duplicates (skip) and inserts at the correct
    // chronological position. Use this to recover deleted sheet rows.
    suspend fun resyncAllToSheets(): Int = withContext(Dispatchers.IO) {
        if (sheetSyncManager.sheetWebAppUrl.isBlank()) {
            Log.w(TAG, "Sheet sync disabled — cannot resync")
            return@withContext 0
        }
        try {
            val allTxs = db.collection(TRANSACTIONS)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toTransaction() }

            Log.i(TAG, "Resyncing ${allTxs.size} transactions to Google Sheets")

            var sent = 0
            for (tx in allTxs) {
                // Fetch items for this transaction
                val items = db.collection(TRANSACTION_ITEMS)
                    .whereEqualTo("transactionId", tx.id)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.toTransactionItem() }

                // Count today's transactions up to this one for daily order number
                val cal = Calendar.getInstance(TimeZone.getTimeZone(SHEET_TIMEZONE))
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val endOfDay = cal.timeInMillis

                val dailyNumber = allTxs.count {
                    it.timestamp >= startOfDay && it.timestamp < endOfDay && it.timestamp <= tx.timestamp
                }

                val payload = buildSheetPayload(tx, items, dailyNumber)
                payload.put("transactionId", tx.id.toString())
                sheetSyncManager.enqueueSheetEntry(tx.id, payload)
                sent++
            }

            // Flush all queued entries now
            sheetSyncManager.syncPendingSheetEntries()

            Log.i(TAG, "Resync complete: $sent transactions sent to Sheets")
            sent
        } catch (e: Exception) {
            Log.e(TAG, "Resync to Sheets failed", e)
            throw e
        }
    }

    // ── Void Transaction ── (flip status to VOIDED & restore supply)
    suspend fun voidTransaction(transaction: TransactionRecord) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Voiding transaction id=${transaction.id}")
        val wasAlreadyVoided = transaction.status == "VOIDED"

        val itemDocs = db.collection(TRANSACTION_ITEMS)
            .whereEqualTo("transactionId", transaction.id).get().await()
        val items = itemDocs.documents.mapNotNull { it.toTransactionItem() }

        val batch = db.batch()
        batch.update(db.collection(TRANSACTIONS).document("${transaction.id}"), "status", "VOIDED")

        // Restore supply if the transaction was active (not already voided)
        if (!wasAlreadyVoided && items.isNotEmpty()) {
            applySupplyRestoration(batch, items.map { it.productId to (it.quantity * it.multiplier) })
        }

        batch.commit().await()
        Log.i(TAG, "Successfully voided transaction ${transaction.id}")

        // Also update Google Sheets: set Status → VOIDED
        sheetSyncManager.enqueueStatusUpdate(transaction.id, "VOIDED")
    }

    // ── Delete Transaction ── (delete items + delete tx + restore supply if active, atomically)
    suspend fun deleteTransactionWithStockRestore(transaction: TransactionRecord) =
        withContext(Dispatchers.IO) {
            val itemDocs = db.collection(TRANSACTION_ITEMS)
                .whereEqualTo("transactionId", transaction.id).get().await()
            val items = itemDocs.documents.mapNotNull { it.toTransactionItem() }

            val batch = db.batch()
            itemDocs.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection(TRANSACTIONS).document("${transaction.id}"))

            // Restore supply if the deleted transaction was active
            if (transaction.status != "VOIDED" && items.isNotEmpty()) {
                applySupplyRestoration(batch, items.map { it.productId to (it.quantity * it.multiplier) })
            }

            batch.commit().await()
        }

    private suspend fun applySupplyDeduction(
        batch: WriteBatch,
        itemPairs: List<Pair<Int, Double>> // (productId, totalBaseQuantity = quantity * multiplier)
    ) {
        val deductions = itemPairs.groupBy({ it.first }, { it.second })
            .mapValues { (_, units) -> units.sum() }

        for ((pId, deduction) in deductions) {
            if (pId <= 0) continue
            try {
                val productRef = db.collection(PRODUCTS).document("$pId")
                val prodSnap = productRef.get().await()
                val currentSupply = prodSnap.getDouble("supplyCount") ?: prodSnap.getLong("supplyCount")?.toDouble()
                if (currentSupply != null) {
                    val newSupply = (currentSupply - deduction).coerceAtLeast(0.0)
                    batch.update(productRef, "supplyCount", newSupply)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deduct supply for product $pId", e)
            }
        }
    }

    private suspend fun applySupplyRestoration(
        batch: WriteBatch,
        itemPairs: List<Pair<Int, Double>> // (productId, totalBaseQuantity = quantity * multiplier)
    ) {
        val restorations = itemPairs.groupBy({ it.first }, { it.second })
            .mapValues { (_, units) -> units.sum() }

        for ((pId, restoration) in restorations) {
            if (pId <= 0) continue
            try {
                val productRef = db.collection(PRODUCTS).document("$pId")
                val prodSnap = productRef.get().await()
                val currentSupply = prodSnap.getDouble("supplyCount") ?: prodSnap.getLong("supplyCount")?.toDouble()
                if (currentSupply != null) {
                    val newSupply = currentSupply + restoration
                    batch.update(productRef, "supplyCount", newSupply)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore supply for product $pId", e)
            }
        }
    }

    // ── Customer Suggestions ──
    suspend fun getUnpaidCustomerNames(): List<String> = withContext(Dispatchers.IO) {
        db.collection(TRANSACTIONS)
            .whereEqualTo("status", "UNPAID")
            .get().await()
            .documents
            .mapNotNull { it.getString("customerName") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // ── Clear All Data (Settings) ── (batched in chunks; Firestore caps a batch at 500 writes)
    suspend fun clearAllData(): Int = withContext(Dispatchers.IO) {
        // First clear local pending queue so they don't sync back up
        pendingDao.clearAllPending()

        var deleted = 0
        val collections = listOf(TRANSACTION_ITEMS, VARIATIONS, TRANSACTIONS, PRODUCTS, CATEGORIES, COUNTERS, CASH_OUTS, BORROWS)
        for (col in collections) {
            val docs = db.collection(col).get().await().documents
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
                deleted += chunk.size
            }
        }
        deleted
    }

    // ── Count All Records (Settings sync) ──
    suspend fun countAllRecords(): Int = withContext(Dispatchers.IO) {
        var total = 0
        val collections = listOf(PRODUCTS, VARIATIONS, CATEGORIES, TRANSACTIONS, TRANSACTION_ITEMS, CASH_OUTS, BORROWS)
        for (col in collections) {
            total += db.collection(col).get().await().size()
        }
        total
    }

    // ── ID Generator ──
    // Atomic allocation via a Firestore transaction so two devices can never mint the same id.
    private suspend fun nextId(collection: String): Int = reserveIds(collection, 1).first()

    /**
     * Counts how many transactions already exist for today (shop timezone).
     * Used to compute the daily order number (#N) for Google Sheets.
     */
    private suspend fun countTodayTransactions(): Int = withContext(Dispatchers.IO) {
        try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone(SHEET_TIMEZONE))
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis

            cal.add(Calendar.DAY_OF_MONTH, 1)
            val endOfDay = cal.timeInMillis

            val result = db.collection(TRANSACTIONS)
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .whereLessThan("timestamp", endOfDay)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            result.size()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count today's transactions: ${e.message}")
            0
        }
    }

    // Reserves a contiguous block of [count] ids in a single atomic transaction and returns them.
    private suspend fun reserveIds(collection: String, count: Int): List<Int> =
        withContext(Dispatchers.IO) {
            if (count <= 0) return@withContext emptyList()
            val counterRef = db.collection(COUNTERS).document(collection)
            val end = db.runTransaction { txn ->
                val current = txn.get(counterRef).getLong("value") ?: 0L
                val newValue = current + count
                txn.set(counterRef, mapOf("value" to newValue))
                newValue
            }.await()
            val start = (end - count + 1).toInt()
            (start..end.toInt()).toList()
        }

    // ── Collection Names ──
    companion object {
        private const val PRODUCTS = "products"
        private const val VARIATIONS = "product_variations"
        private const val CATEGORIES = "categories"
        private const val TRANSACTIONS = "transactions"
        private const val TRANSACTION_ITEMS = "transaction_items"
        private const val COUNTERS = "counters"
        private const val SETTINGS = "settings"
        private const val AUTH_DOC = "auth"
        private const val CASH_OUTS = "cash_outs"
        private const val BORROWS = "borrows"
        private const val OPERATIONAL_EXPENSES = "operational_expenses"
        private const val SHEET_TIMEZONE = "Asia/Manila"

        private const val RECENT_TRANSACTIONS_LIMIT: Long = 3000
        private const val RECENT_TRANSACTION_ITEMS_LIMIT: Long = 10000
        private const val RECENT_CASH_OUTS_LIMIT: Long = 3000
        private const val RECENT_BORROWS_LIMIT: Long = 3000
        private const val RECENT_OPERATIONAL_EXPENSES_LIMIT: Long = 3000

        // ── Google Sheets payload builder ──────────────────────────────────

        private val sheetTimeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(SHEET_TIMEZONE)
        }
        private val sheetDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(SHEET_TIMEZONE)
        }

        /**
         * Builds a JSON payload for the Google Apps Script web app that writes
         * one row per transaction into the connected Google Sheet.
         */
        fun buildSheetPayload(
            transaction: TransactionRecord,
            items: List<TransactionItem>,
            dailyOrderNumber: Int = 0
        ): JSONObject {
            val itemSummary = items.joinToString(", ") { item ->
                "${item.quantity.toLong()}× ${item.productName} (${item.variationName})"
            }
            val totalQty = items.sumOf { it.quantity }

            return JSONObject().apply {
                put("date", sheetDateFormatter.format(Date(transaction.timestamp)))
                put("time", sheetTimeFormatter.format(Date(transaction.timestamp)))
                put("orderNumber", if (dailyOrderNumber > 0) "#$dailyOrderNumber" else "—")
                put("customer", transaction.customerName ?: "")
                put("status", transaction.status)
                put("items", itemSummary)
                put("quantity", totalQty.toLong())
                put("total", transaction.totalAmount.roundToCentavos())
            }
        }
    }
}

// ── Firestore Document → Entity Mappers ──

private fun com.google.firebase.firestore.DocumentSnapshot.toProduct(): Product? {
    return try {
        Product(
            id = getLong("id")?.toInt() ?: return null,
            name = getString("name") ?: "",
            category = getString("category") ?: "",
            supplyCount = getDouble("supplyCount") ?: getLong("supplyCount")?.toDouble()
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed product ${id}", e); null
    }
}

private fun Product.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "name" to name, "category" to category, "supplyCount" to supplyCount
)

private fun com.google.firebase.firestore.DocumentSnapshot.toVariation(): ProductVariation? {
    return try {
        ProductVariation(
            id = getLong("id")?.toInt() ?: return null,
            productId = getLong("productId")?.toInt() ?: 0,
            name = getString("name") ?: "",
            price = getDouble("price") ?: 0.0,
            multiplier = getDouble("multiplier") ?: 1.0
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed variation ${id}", e); null
    }
}

private fun ProductVariation.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "productId" to productId, "name" to name,
    "price" to price, "multiplier" to multiplier
)

private fun com.google.firebase.firestore.DocumentSnapshot.toCategory(): Category? {
    return try {
        Category(
            id = getLong("id")?.toInt() ?: return null,
            name = getString("name") ?: ""
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed category ${id}", e); null
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toTransaction(): TransactionRecord? {
    return try {
        TransactionRecord(
            id = getLong("id")?.toInt() ?: return null,
            customerName = getString("customerName"),
            timestamp = getLong("timestamp") ?: System.currentTimeMillis(),
            status = getString("status") ?: "PAID",
            subtotal = getDouble("subtotal") ?: 0.0,
            tax = getDouble("tax") ?: 0.0,
            discount = getDouble("discount") ?: 0.0,
            totalAmount = getDouble("totalAmount") ?: 0.0,
            settledTimestamp = getLong("settledTimestamp")
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed transaction ${id}", e); null
    }
}

private fun TransactionRecord.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "customerName" to customerName, "timestamp" to timestamp,
    "status" to status, "subtotal" to subtotal, "tax" to tax,
    "discount" to discount, "totalAmount" to totalAmount,
    "settledTimestamp" to settledTimestamp
)

private fun com.google.firebase.firestore.DocumentSnapshot.toTransactionItem(): TransactionItem? {
    return try {
        TransactionItem(
            id = getLong("id")?.toInt() ?: return null,
            transactionId = getLong("transactionId")?.toInt() ?: 0,
            productId = getLong("productId")?.toInt() ?: 0,
            productName = getString("productName") ?: "",
            variationName = getString("variationName") ?: "",
            price = getDouble("price") ?: 0.0,
            quantity = getDouble("quantity") ?: 0.0,
            multiplier = getDouble("multiplier") ?: 1.0
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed transaction item ${id}", e); null
    }
}

private fun TransactionItem.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "transactionId" to transactionId, "productId" to productId,
    "productName" to productName, "variationName" to variationName,
    "price" to price, "quantity" to quantity, "multiplier" to multiplier
)

private fun com.google.firebase.firestore.DocumentSnapshot.toCashOutEntry(): CashOutEntry? {
    return try {
        CashOutEntry(
            id = getLong("id")?.toInt() ?: return null,
            cashierName = getString("cashierName") ?: "",
            amount = getDouble("amount") ?: 0.0,
            note = getString("note"),
            timestamp = getLong("timestamp") ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed cash-out entry ${id}", e); null
    }
}

private fun CashOutEntry.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "cashierName" to cashierName, "amount" to amount,
    "note" to note, "timestamp" to timestamp
)

private fun com.google.firebase.firestore.DocumentSnapshot.toBorrowEntry(): BorrowEntry? {
    return try {
        BorrowEntry(
            id = getLong("id")?.toInt() ?: return null,
            borrowerName = getString("borrowerName") ?: "",
            amount = getDouble("amount") ?: 0.0,
            note = getString("note"),
            timestamp = getLong("timestamp") ?: System.currentTimeMillis(),
            returnedTimestamp = getLong("returnedTimestamp")
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed borrow entry ${id}", e); null
    }
}

private fun BorrowEntry.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "borrowerName" to borrowerName, "amount" to amount,
    "note" to note, "timestamp" to timestamp, "returnedTimestamp" to returnedTimestamp
)

private fun com.google.firebase.firestore.DocumentSnapshot.toOperationalExpense(): OperationalExpense? {
    return try {
        OperationalExpense(
            id = getLong("id")?.toInt() ?: return null,
            title = getString("title") ?: "",
            amount = getDouble("amount") ?: 0.0,
            note = getString("note"),
            timestamp = getLong("timestamp") ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed operational expense ${id}", e); null
    }
}

private fun OperationalExpense.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "title" to title, "amount" to amount,
    "note" to note, "timestamp" to timestamp
)

private fun com.google.firebase.firestore.DocumentSnapshot.toAuthSettings(): AuthSettings {
    if (!exists()) return AuthSettings()
    val defaults = AuthSettings()
    return AuthSettings(
        adminPin = getString("adminPin") ?: defaults.adminPin,
        cashierPin = getString("cashierPin") ?: defaults.cashierPin
    )
}

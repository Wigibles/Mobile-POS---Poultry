package com.example.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "FirestorePOSRepo"

/**
 * Cloud-synced repository backed by Firestore.
 * All read operations return Flows that emit updates in real-time across devices.
 * Write operations persist to the cloud and propagate to all connected clients.
 *
 * Reliability guarantees:
 *  - IDs are allocated atomically via a Firestore transaction (no duplicate/overwritten records).
 *  - Multi-document writes (checkout, void, product edits) are committed as atomic batches,
 *    so a sale never leaves a half-written transaction behind.
 *  - Stock changes use server-side [FieldValue.increment], so concurrent devices can't clobber
 *    each other's deductions.
 *  - Read and write failures are surfaced through [errors] instead of being silently swallowed.
 */
class FirestorePOSRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // ── Error surface — collected by the ViewModel and shown to the user ──
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private fun reportListenerError(what: String, error: Throwable) {
        Log.e(TAG, "$what listener failed", error)
        _errors.tryEmit("Couldn't sync $what: ${error.message ?: "unknown error"}")
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
    val transactions: Flow<List<TransactionRecord>> = callbackFlow {
        val listener = db.collection(TRANSACTIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("transactions", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toTransaction() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Transaction Items ──
    val allTransactionItems: Flow<List<TransactionItem>> = callbackFlow {
        val listener = db.collection(TRANSACTION_ITEMS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportListenerError("transaction items", error); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toTransactionItem() } ?: emptyList())
            }
        awaitClose { listener.remove() }
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
    // Records the sale and deducts stock as ONE atomic batch. Stock is decremented with
    // FieldValue.increment (server-side, concurrency-safe). Deltas are aggregated per product
    // so a sale containing two variations of the same product commits a single stock update.
    suspend fun processTransaction(
        transaction: TransactionRecord,
        items: List<TransactionItem>
    ): Int = withContext(Dispatchers.IO) {
        val txId = nextId(TRANSACTIONS)
        val itemIds = reserveIds(TRANSACTION_ITEMS, items.size)

        val roundedTx = transaction.copy(
            id = txId,
            subtotal = transaction.subtotal.roundToCentavos(),
            tax = transaction.tax.roundToCentavos(),
            discount = transaction.discount.roundToCentavos(),
            totalAmount = transaction.totalAmount.roundToCentavos()
        )

        val stockDeltas = items.groupBy { it.productId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity * it.multiplier }.roundToCentavos() }

        val batch = db.batch()
        batch.set(db.collection(TRANSACTIONS).document("$txId"), roundedTx.toMap())
        items.forEachIndexed { i, item ->
            val itemId = itemIds[i]
            batch.set(
                db.collection(TRANSACTION_ITEMS).document("$itemId"),
                item.copy(id = itemId, transactionId = txId, price = item.price.roundToCentavos()).toMap()
            )
        }
        stockDeltas.forEach { (productId, deducted) ->
            batch.update(
                db.collection(PRODUCTS).document("$productId"),
                "stockLevel", FieldValue.increment(-deducted)
            )
        }
        batch.commit().await()
        txId
    }

    // ── Mark Transaction as Paid ──
    suspend fun markTransactionAsPaid(transactionId: Int) = withContext(Dispatchers.IO) {
        db.collection(TRANSACTIONS).document("$transactionId")
            .update(mapOf("status" to "PAID", "settledTimestamp" to System.currentTimeMillis())).await()
    }

    // ── Void Transaction ── (restore stock + flip status, atomically)
    suspend fun voidTransaction(transaction: TransactionRecord) = withContext(Dispatchers.IO) {
        val items = db.collection(TRANSACTION_ITEMS)
            .whereEqualTo("transactionId", transaction.id).get().await()
            .documents.mapNotNull { it.toTransactionItem() }

        val batch = db.batch()
        restoreStockInto(batch, items)
        batch.update(db.collection(TRANSACTIONS).document("${transaction.id}"), "status", "VOIDED")
        batch.commit().await()
    }

    // ── Delete Transaction with Stock Restore ── (restore + delete items + delete tx, atomically)
    suspend fun deleteTransactionWithStockRestore(transaction: TransactionRecord) =
        withContext(Dispatchers.IO) {
            val itemDocs = db.collection(TRANSACTION_ITEMS)
                .whereEqualTo("transactionId", transaction.id).get().await()
            val items = itemDocs.documents.mapNotNull { it.toTransactionItem() }

            val batch = db.batch()
            // Already-voided transactions already had their stock returned — don't double-restore.
            if (transaction.status != "VOIDED") {
                restoreStockInto(batch, items)
            }
            itemDocs.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection(TRANSACTIONS).document("${transaction.id}"))
            batch.commit().await()
        }

    // Aggregates per-product restore amounts and adds the increments to [batch].
    private fun restoreStockInto(
        batch: com.google.firebase.firestore.WriteBatch,
        items: List<TransactionItem>
    ) {
        items.groupBy { it.productId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity * it.multiplier }.roundToCentavos() }
            .forEach { (productId, restored) ->
                batch.update(
                    db.collection(PRODUCTS).document("$productId"),
                    "stockLevel", FieldValue.increment(restored)
                )
            }
    }

    // ── Stock Validation ──
    suspend fun validateStockAvailability(
        productId: Int,
        variationId: Int,
        requestedQuantity: Double
    ): StockValidationResult = withContext(Dispatchers.IO) {
        val doc = db.collection(PRODUCTS).document("$productId").get().await()
        val product = doc.toProduct() ?: return@withContext StockValidationResult(false, 0.0, 0.0)
        val varDoc = db.collection(VARIATIONS).document("$variationId").get().await()
        val multiplier = varDoc.toVariation()?.multiplier ?: 1.0
        StockValidationResult(
            isAvailable = (requestedQuantity * multiplier) <= product.stockLevel,
            availableStock = product.stockLevel,
            baseUnitsNeeded = requestedQuantity * multiplier
        )
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
        var deleted = 0
        val collections = listOf(TRANSACTION_ITEMS, VARIATIONS, TRANSACTIONS, PRODUCTS, CATEGORIES, COUNTERS)
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
        val collections = listOf(PRODUCTS, VARIATIONS, CATEGORIES, TRANSACTIONS, TRANSACTION_ITEMS)
        for (col in collections) {
            total += db.collection(col).get().await().size()
        }
        total
    }

    // ── ID Generator ──
    // Atomic allocation via a Firestore transaction so two devices can never mint the same id.
    private suspend fun nextId(collection: String): Int = reserveIds(collection, 1).first()

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
    }
}

// ── Firestore Document → Entity Mappers ──

private fun com.google.firebase.firestore.DocumentSnapshot.toProduct(): Product? {
    return try {
        Product(
            id = getLong("id")?.toInt() ?: return null,
            name = getString("name") ?: "",
            category = getString("category") ?: "",
            stockLevel = getDouble("stockLevel") ?: 0.0,
            lowStockThreshold = getDouble("lowStockThreshold") ?: 5.0,
            imageUrl = getString("imageUrl")
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed product ${id}", e); null
    }
}

private fun Product.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "name" to name, "category" to category,
    "stockLevel" to stockLevel, "lowStockThreshold" to lowStockThreshold,
    "imageUrl" to imageUrl
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

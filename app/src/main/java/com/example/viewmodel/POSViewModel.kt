package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.roundToCentavos
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class CartItem(
    val product: Product,
    val variation: ProductVariation,
    val quantity: Double,
    val customPrice: Double? = null
) {
    val unitPrice: Double get() = customPrice ?: variation.price
    val lineTotal: Double get() = unitPrice * quantity
}

class POSViewModel(
    private val repository: FirestorePOSRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // ── User-facing messages (errors + confirmations) surfaced to the UI as a snackbar/toast ──
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private fun report(message: String) { _userMessages.tryEmit(message) }

    init {
        // Forward repository read/sync errors so silent data loss becomes visible.
        viewModelScope.launch { repository.errors.collect { report(it) } }
    }

    // ── Offline / sync state exposed to the UI ──
    val isOnline: StateFlow<Boolean> = repository.networkMonitor.isOnline
    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
    val isOfflineSyncing: StateFlow<Boolean> = repository.isOfflineSyncing

    /** Manually trigger a sync of all pending offline records (sales, cash-outs, borrows). */
    fun syncNow() {
        viewModelScope.launch {
            try {
                val count = repository.syncPendingTransactions()
                val sheetCount = repository.sheetSyncManager.syncPendingSheetEntries()
                val total = count + sheetCount
                if (total > 0) report("$total pending record(s) synced to cloud.")
            } catch (e: Exception) {
                report("Sync failed: ${e.message ?: ""}".trim())
            }
        }
    }

    /** Re-send ALL Firestore transactions to Google Sheets (recovery). */
    fun resyncToSheets() {
        if (_isResyncingSheets.value) return // guard: prevent double-tap
        _isResyncingSheets.value = true
        viewModelScope.launch {
            try {
                report("Resyncing all transactions to Google Sheets…")
                val count = repository.resyncAllToSheets()
                report("$count transaction(s) sent to Sheets.")
            } catch (e: Exception) {
                report("Sheets resync failed: ${e.message ?: ""}".trim())
            } finally {
                _isResyncingSheets.value = false
            }
        }
    }

    // ── Processing guards — prevent duplicate submissions ──
    private val _isResyncingSheets = MutableStateFlow(false)
    val isResyncingSheets: StateFlow<Boolean> = _isResyncingSheets.asStateFlow()

    private val _isProcessingTransaction = MutableStateFlow(false)
    val isProcessingTransaction: StateFlow<Boolean> = _isProcessingTransaction.asStateFlow()

    private val _isSavingProduct = MutableStateFlow(false)
    val isSavingProduct: StateFlow<Boolean> = _isSavingProduct.asStateFlow()

    private val _isSavingCashOut = MutableStateFlow(false)
    val isSavingCashOut: StateFlow<Boolean> = _isSavingCashOut.asStateFlow()

    private val _isSavingBorrow = MutableStateFlow(false)
    val isSavingBorrow: StateFlow<Boolean> = _isSavingBorrow.asStateFlow()

    private val _isSavingOperationalExpense = MutableStateFlow(false)
    val isSavingOperationalExpense: StateFlow<Boolean> = _isSavingOperationalExpense.asStateFlow()

    // UI State Flows from Repository
    val products = repository.products.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories = combine(repository.categories, repository.products) { cats, prods ->
        val usedCategoryNames = prods.map { it.category }.distinct()
        cats.filter { it.name in usedCategoryNames }.sortedBy { it.name }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val variations = repository.variations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val transactions = repository.transactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTransactionItems = repository.allTransactionItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val cashOutEntries = repository.cashOutEntries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val borrowEntries = repository.borrowEntries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val operationalExpenses = repository.operationalExpenses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ── Auth / Session ──
    val authSettings: StateFlow<AuthSettings?> = repository.authSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val currentRole: StateFlow<Role?> = sessionManager.sessionFlow.map { it.first }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val currentCashierName: StateFlow<String?> = sessionManager.sessionFlow.map { it.second }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun login(role: Role, pin: String, cashierName: String?, onResult: (Boolean) -> Unit) {
        val settings = authSettings.value
        if (settings == null) {
            report("Still loading — try again in a moment.")
            onResult(false)
            return
        }
        val expectedPin = if (role == Role.ADMIN) settings.adminPin else settings.cashierPin
        if (pin != expectedPin) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            sessionManager.saveSession(role, if (role == Role.CASHIER) cashierName else null)
            navigateTo("HOME")
            onResult(true)
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            // Clear transient UI state so it doesn't leak into whoever logs in next on this device.
            _cartItems.value = emptyList()
            _editingProduct.value = null
            clearHistoryFilters()
            _cashLogDateFilter.value = null
        }
    }

    fun updatePins(adminPin: String, cashierPin: String) {
        viewModelScope.launch {
            try {
                repository.updatePins(adminPin, cashierPin)
            } catch (e: Exception) {
                report("Failed to update PINs. ${e.message ?: ""}".trim())
            }
        }
    }

    // Active Shopping Cart State
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    // POS Screen Filtering & Searching
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // Screen navigation flow
    private val _currentScreen = MutableStateFlow("HOME")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Selected product for variation selection overlay
    private val _selectedProductForVariations = MutableStateFlow<Product?>(null)
    val selectedProductForVariations: StateFlow<Product?> = _selectedProductForVariations.asStateFlow()

    // Filter states for Transaction History
    private val _historyHorizon = MutableStateFlow(TimeHorizon.ALL)
    val historyHorizon: StateFlow<TimeHorizon> = _historyHorizon.asStateFlow()

    private val _historyStartDateFilter = MutableStateFlow<String?>(null) // Format: "yyyy-MM-dd" or null
    val historyStartDateFilter: StateFlow<String?> = _historyStartDateFilter.asStateFlow()

    private val _historyEndDateFilter = MutableStateFlow<String?>(null) // Format: "yyyy-MM-dd" or null
    val historyEndDateFilter: StateFlow<String?> = _historyEndDateFilter.asStateFlow()

    val historyDateFilter: StateFlow<String?> = _historyStartDateFilter.asStateFlow()

    private val _historyStatusFilter = MutableStateFlow<String?>(null) // "PAID", "UNPAID", or null
    val historyStatusFilter: StateFlow<String?> = _historyStatusFilter.asStateFlow()

    private val _historyCustomerFilter = MutableStateFlow<String?>(null) // Customer name or null
    val historyCustomerFilter: StateFlow<String?> = _historyCustomerFilter.asStateFlow()

    // Filter state for the Cash Log
    private val _cashLogDateFilter = MutableStateFlow<String?>(null) // Format: "yyyy-MM-dd" or null
    val cashLogDateFilter: StateFlow<String?> = _cashLogDateFilter.asStateFlow()

    fun setCashLogDateFilter(date: String?) { _cashLogDateFilter.value = date }

    // Dashboard Time Horizon state
    private val _dashboardHorizon = MutableStateFlow(TimeHorizon.DAY)
    val dashboardHorizon: StateFlow<TimeHorizon> = _dashboardHorizon.asStateFlow()

    private val _customDashboardStartDate = MutableStateFlow<String?>(null)
    val customDashboardStartDate: StateFlow<String?> = _customDashboardStartDate.asStateFlow()

    private val _customDashboardEndDate = MutableStateFlow<String?>(null)
    val customDashboardEndDate: StateFlow<String?> = _customDashboardEndDate.asStateFlow()

    val customDashboardDate: StateFlow<String?> = _customDashboardStartDate.asStateFlow()

    fun setDashboardHorizon(horizon: TimeHorizon, startDate: String? = null, endDate: String? = null) {
        _dashboardHorizon.value = horizon
        _customDashboardStartDate.value = startDate
        _customDashboardEndDate.value = endDate ?: startDate
        when (horizon) {
            TimeHorizon.DAY -> _chartPeriod.value = "DAY"
            TimeHorizon.WEEK -> _chartPeriod.value = "7D"
            TimeHorizon.THIRTY_DAYS -> _chartPeriod.value = "30D"
            TimeHorizon.MTD -> _chartPeriod.value = "MTD"
            else -> {}
        }
    }

    // Product form state (for adding/editing products)
    private val _editingProduct = MutableStateFlow<Product?>(null)
    val editingProduct: StateFlow<Product?> = _editingProduct.asStateFlow()

    // Search and filtering implementation
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    fun selectProductForVariations(product: Product?) {
        _selectedProductForVariations.value = product
    }

    // Cart Management
    fun addToCart(
        product: Product,
        variation: ProductVariation,
        quantity: Double,
        customPrice: Double? = null
    ) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst {
            it.product.id == product.id && it.variation.id == variation.id && it.customPrice == customPrice
        }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            currentList.add(CartItem(product, variation, quantity, customPrice))
        }
        _cartItems.value = currentList
    }

    fun updateCartItemQuantity(item: CartItem, newQuantity: Double) {
        if (newQuantity <= 0.0) {
            removeCartItem(item)
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it == item }
        if (index != -1) {
            currentList[index] = currentList[index].copy(quantity = newQuantity)
            _cartItems.value = currentList
        }
    }

    fun updateCartItemPrice(item: CartItem, newPrice: Double?) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it == item }
        if (index != -1) {
            currentList[index] = currentList[index].copy(customPrice = newPrice)
            _cartItems.value = currentList
        }
    }

    fun removeCartItem(item: CartItem) {
        val currentList = _cartItems.value.toMutableList()
        currentList.remove(item)
        _cartItems.value = currentList
    }

    fun updateCartQuantity(product: Product, variation: ProductVariation, quantity: Double) {
        if (quantity <= 0.0) {
            removeFromCart(product, variation)
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id && it.variation.id == variation.id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(quantity = quantity)
            _cartItems.value = currentList
        }
    }

    fun removeFromCart(product: Product, variation: ProductVariation) {
        val currentList = _cartItems.value.toMutableList()
        currentList.removeAll { it.product.id == product.id && it.variation.id == variation.id }
        _cartItems.value = currentList
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    // Process payment and finalize transaction
    fun finalizeTransaction(status: String, customerName: String?, onSuccess: () -> Unit) {
        if (_isProcessingTransaction.value) return // guard: prevent double-tap
        _isProcessingTransaction.value = true
        viewModelScope.launch {
            try {
                val items = _cartItems.value
                if (items.isEmpty()) return@launch

                val subtotal = items.sumOf { it.lineTotal }.roundToCentavos()
                val tax = 0.0
                val discount = 0.0
                val total = subtotal.roundToCentavos()

                val record = TransactionRecord(
                    customerName = if (status == "UNPAID") customerName else null,
                    status = status,
                    subtotal = subtotal,
                    tax = tax,
                    discount = discount,
                    totalAmount = total
                )

                val transactionItems = items.map { cartItem ->
                    TransactionItem(
                        transactionId = 0, // will be replaced in repository
                        productId = cartItem.product.id,
                        productName = cartItem.product.name,
                        variationName = if (cartItem.customPrice != null) "${cartItem.variation.name} (Custom)" else cartItem.variation.name,
                        price = cartItem.unitPrice.roundToCentavos(),
                        quantity = cartItem.quantity,
                        multiplier = cartItem.variation.multiplier
                    )
                }

                repository.processTransaction(record, transactionItems)
                clearCart()
                onSuccess()
            } catch (e: Exception) {
                report("Failed to save sale — nothing was recorded. ${e.message ?: ""}".trim())
            } finally {
                _isProcessingTransaction.value = false
            }
        }
    }

    // Product Management
    fun saveProduct(
        id: Int,
        name: String,
        category: String,
        supplyCount: Double? = null,
        imageUri: String? = null,
        variationsList: List<ProductVariation>,
        onSuccess: () -> Unit
    ) {
        if (_isSavingProduct.value) return // guard: prevent double-tap
        _isSavingProduct.value = true
        viewModelScope.launch {
            try {
                val product = Product(
                    id = id,
                    name = name,
                    category = category,
                    supplyCount = supplyCount,
                    imageUri = imageUri
                )
                if (id == 0) {
                    repository.insertProduct(product, variationsList)
                } else {
                    repository.updateProduct(product, variationsList)
                }
                onSuccess()
            } catch (e: Exception) {
                report("Failed to save product. ${e.message ?: ""}".trim())
            } finally {
                _isSavingProduct.value = false
            }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(product)
            } catch (e: Exception) {
                report("Failed to delete product. ${e.message ?: ""}".trim())
            }
        }
    }

    fun startEditingProduct(product: Product?) {
        _editingProduct.value = product
    }

    // Category Management
    fun addCategory(name: String) {
        viewModelScope.launch {
            try {
                repository.insertCategory(Category(name = name))
            } catch (e: Exception) {
                report("Failed to add category. ${e.message ?: ""}".trim())
            }
        }
    }

    fun deleteCategory(category: Category, onBlocked: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val deleted = repository.deleteCategory(category)
                if (!deleted) {
                    onBlocked()
                }
            } catch (e: Exception) {
                report("Failed to delete category. ${e.message ?: ""}".trim())
            }
        }
    }

    // Mark an unpaid transaction as paid
    fun markTransactionAsPaid(transactionId: Int) {
        viewModelScope.launch {
            try {
                repository.markTransactionAsPaid(transactionId)
            } catch (e: Exception) {
                report("Failed to mark as paid. ${e.message ?: ""}".trim())
            }
        }
    }

    // Void a transaction and restore stock
    fun voidTransaction(transaction: TransactionRecord) {
        viewModelScope.launch {
            try {
                repository.voidTransaction(transaction)
            } catch (e: Exception) {
                report("Failed to void transaction. ${e.message ?: ""}".trim())
            }
        }
    }

    // Delete transaction WITH stock restoration
    fun deleteTransactionWithStockRestore(transaction: TransactionRecord) {
        viewModelScope.launch {
            try {
                repository.deleteTransactionWithStockRestore(transaction)
            } catch (e: Exception) {
                report("Failed to delete transaction. ${e.message ?: ""}".trim())
            }
        }
    }

    fun deleteTransaction(transaction: TransactionRecord) {
        deleteTransactionWithStockRestore(transaction)
    }

    // Get customer name suggestions for autocomplete
    private val _customerSuggestions = MutableStateFlow<List<String>>(emptyList())
    val customerSuggestions: StateFlow<List<String>> = _customerSuggestions.asStateFlow()

    fun loadCustomerSuggestions() {
        viewModelScope.launch {
            try {
                _customerSuggestions.value = repository.getUnpaidCustomerNames()
            } catch (e: Exception) {
                report("Couldn't load customer suggestions. ${e.message ?: ""}".trim())
            }
        }
    }

    // ── Cash-Out Log ──
    fun addCashOutEntry(cashierName: String, amount: Double, note: String?, onSuccess: () -> Unit) {
        if (_isSavingCashOut.value) return
        _isSavingCashOut.value = true
        viewModelScope.launch {
            try {
                repository.addCashOutEntry(
                    CashOutEntry(cashierName = cashierName, amount = amount, note = note)
                )
                onSuccess()
            } catch (e: Exception) {
                report("Failed to log cash out. ${e.message ?: ""}".trim())
            } finally {
                _isSavingCashOut.value = false
            }
        }
    }

    fun updateCashOutEntry(entry: CashOutEntry, newAmount: Double, newNote: String?, onSuccess: () -> Unit) {
        if (_isSavingCashOut.value) return
        _isSavingCashOut.value = true
        viewModelScope.launch {
            try {
                repository.updateCashOutEntry(entry.copy(amount = newAmount, note = newNote))
                onSuccess()
            } catch (e: Exception) {
                report("Failed to update cash-out entry. ${e.message ?: ""}".trim())
            } finally {
                _isSavingCashOut.value = false
            }
        }
    }

    fun deleteCashOutEntry(entry: CashOutEntry) {
        viewModelScope.launch {
            try {
                repository.deleteCashOutEntry(entry)
            } catch (e: Exception) {
                report("Failed to delete cash-out entry. ${e.message ?: ""}".trim())
            }
        }
    }

    // ── Family Borrowing Log ──
    fun addBorrowEntry(borrowerName: String, amount: Double, note: String?, onSuccess: () -> Unit) {
        if (_isSavingBorrow.value) return
        _isSavingBorrow.value = true
        viewModelScope.launch {
            try {
                repository.addBorrowEntry(
                    BorrowEntry(borrowerName = borrowerName, amount = amount, note = note)
                )
                onSuccess()
            } catch (e: Exception) {
                report("Failed to log borrow. ${e.message ?: ""}".trim())
            } finally {
                _isSavingBorrow.value = false
            }
        }
    }

    fun updateBorrowEntry(entry: BorrowEntry, newAmount: Double, newNote: String?, onSuccess: () -> Unit) {
        if (_isSavingBorrow.value) return
        _isSavingBorrow.value = true
        viewModelScope.launch {
            try {
                repository.updateBorrowEntry(entry.copy(amount = newAmount, note = newNote))
                onSuccess()
            } catch (e: Exception) {
                report("Failed to update borrow entry. ${e.message ?: ""}".trim())
            } finally {
                _isSavingBorrow.value = false
            }
        }
    }

    fun markBorrowReturned(entry: BorrowEntry) {
        viewModelScope.launch {
            try {
                repository.markBorrowReturned(entry.id)
            } catch (e: Exception) {
                report("Failed to mark borrow as returned. ${e.message ?: ""}".trim())
            }
        }
    }

    fun deleteBorrowEntry(entry: BorrowEntry) {
        viewModelScope.launch {
            try {
                repository.deleteBorrowEntry(entry)
            } catch (e: Exception) {
                report("Failed to delete borrow entry. ${e.message ?: ""}".trim())
            }
        }
    }

    // ── Store Operational Expenses Log & Delivery Cycles ──
    fun addOperationalExpense(title: String, amount: Double, note: String?, timestamp: Long = System.currentTimeMillis(), onSuccess: () -> Unit) {
        if (_isSavingOperationalExpense.value) return
        _isSavingOperationalExpense.value = true
        viewModelScope.launch {
            try {
                repository.addOperationalExpense(
                    OperationalExpense(title = title, amount = amount, note = note, timestamp = timestamp)
                )
                onSuccess()
            } catch (e: Exception) {
                report("Failed to log operational expense. ${e.message ?: ""}".trim())
            } finally {
                _isSavingOperationalExpense.value = false
            }
        }
    }

    fun updateOperationalExpense(entry: OperationalExpense, newTitle: String, newAmount: Double, newNote: String?, newTimestamp: Long = entry.timestamp, onSuccess: () -> Unit) {
        if (_isSavingOperationalExpense.value) return
        _isSavingOperationalExpense.value = true
        viewModelScope.launch {
            try {
                repository.updateOperationalExpense(
                    entry.copy(title = newTitle, amount = newAmount, note = newNote, timestamp = newTimestamp)
                )
                onSuccess()
            } catch (e: Exception) {
                report("Failed to update operational expense. ${e.message ?: ""}".trim())
            } finally {
                _isSavingOperationalExpense.value = false
            }
        }
    }

    fun deleteOperationalExpense(entry: OperationalExpense) {
        viewModelScope.launch {
            try {
                repository.deleteOperationalExpense(entry)
            } catch (e: Exception) {
                report("Failed to delete operational expense. ${e.message ?: ""}".trim())
            }
        }
    }

    // ── Settings: Clear all Firestore data ──
    private val _isClearingData = MutableStateFlow(false)
    val isClearingData: StateFlow<Boolean> = _isClearingData.asStateFlow()

    fun clearAllData(onResult: (deletedCount: Int) -> Unit) {
        if (_isClearingData.value) return
        _isClearingData.value = true
        viewModelScope.launch {
            try {
                val count = repository.clearAllData()
                onResult(count)
            } catch (e: Exception) {
                report("Failed to clear data. ${e.message ?: ""}".trim())
            } finally {
                _isClearingData.value = false
            }
        }
    }

    // ── Settings: Sync data (health check) ──
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun syncData(onResult: (totalRecords: Int) -> Unit) {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                val total = repository.countAllRecords()
                onResult(total)
            } catch (e: Exception) {
                report("Sync check failed. ${e.message ?: ""}".trim())
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Transaction History Filter Actions
    fun setHistoryHorizon(horizon: TimeHorizon) {
        _historyHorizon.value = horizon
        if (horizon != TimeHorizon.CUSTOM) {
            _historyStartDateFilter.value = null
            _historyEndDateFilter.value = null
        }
    }

    fun setHistoryFilters(
        startDate: String?,
        endDate: String? = null,
        status: String? = null,
        customer: String? = null,
        horizon: TimeHorizon? = null
    ) {
        _historyStartDateFilter.value = startDate
        _historyEndDateFilter.value = endDate ?: startDate
        _historyStatusFilter.value = status
        _historyCustomerFilter.value = customer
        if (horizon != null) {
            _historyHorizon.value = horizon
        } else if (startDate != null) {
            _historyHorizon.value = TimeHorizon.CUSTOM
        }
    }

    fun clearHistoryFilters() {
        _historyHorizon.value = TimeHorizon.ALL
        _historyStartDateFilter.value = null
        _historyEndDateFilter.value = null
        _historyStatusFilter.value = null
        _historyCustomerFilter.value = null
    }

    // Dashboard Calculations & State Selectors (Dynamic)
    val dashboardStats = transactions.map { txList ->
        val sdf = shopDateFormat("yyyy-MM-dd")
        val todayStr = sdf.format(Date())

        val todayTxs = txList.filter {
            sdf.format(Date(it.timestamp)) == todayStr && it.status != "VOIDED"
        }

        val totalSalesToday = todayTxs.sumOf { it.totalAmount }
        val paidToday = todayTxs.filter { it.status == "PAID" }.sumOf { it.totalAmount }
        val unpaidToday = todayTxs.filter { it.status == "UNPAID" }.sumOf { it.totalAmount }
        val salesCount = todayTxs.size
        val avgTicketSize = if (salesCount > 0) totalSalesToday / salesCount else 0.0

        DashboardStats(
            totalSalesToday = totalSalesToday,
            paidToday = paidToday,
            unpaidToday = unpaidToday,
            salesCount = salesCount,
            avgTicketSize = avgTicketSize
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStats()
    )

    // Dynamic Dashboard Stats reacting to selected TimeHorizon and date range
    val dashboardRangeStats = combine(
        transactions,
        cashOutEntries,
        _dashboardHorizon,
        _customDashboardStartDate,
        _customDashboardEndDate
    ) { txList, expenseList, horizon, startDate, endDate ->
        val boundary = getTimeHorizonBoundary(horizon, startDate, endDate)
        val validTxs = txList.filter {
            it.status != "VOIDED" && (horizon == TimeHorizon.ALL || it.timestamp in boundary.startTimestamp..boundary.endTimestamp)
        }
        val totalSales = validTxs.sumOf { it.totalAmount }
        val paid = validTxs.filter { it.status == "PAID" }.sumOf { it.totalAmount }
        val unpaid = validTxs.filter { it.status == "UNPAID" }.sumOf { it.totalAmount }
        val count = validTxs.size

        val expenses = expenseList.filter {
            horizon == TimeHorizon.ALL || it.timestamp in boundary.startTimestamp..boundary.endTimestamp
        }.sumOf { it.amount }

        RangeSalesStats(
            horizon = horizon,
            rangeLabel = boundary.displayLabel,
            totalSales = totalSales,
            paidAmount = paid,
            unpaidAmount = unpaid,
            salesCount = count,
            expenseTotal = expenses,
            netAmount = totalSales - expenses
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RangeSalesStats()
    )

    // Today's total employee expenses
    val todayExpenseTotal = cashOutEntries.map { entries ->
        val sdf = shopDateFormat("yyyy-MM-dd")
        val todayStr = sdf.format(Date())
        entries.filter { sdf.format(Date(it.timestamp)) == todayStr }.sumOf { it.amount }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Outstanding-borrow summary
    val borrowStats = borrowEntries.map { entries ->
        val outstanding = entries.filter { it.returnedTimestamp == null }
        BorrowStats(
            totalOutstanding = outstanding.sumOf { it.amount },
            outstandingCount = outstanding.size,
            oldestDays = outstanding.maxOfOrNull {
                ((System.currentTimeMillis() - it.timestamp) / (1000 * 60 * 60 * 24)).toInt()
            } ?: 0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BorrowStats()
    )

    // Chart period filter
    private val _chartPeriod = MutableStateFlow("7D")
    val chartPeriod: StateFlow<String> = _chartPeriod.asStateFlow()

    fun setChartPeriod(period: String) { 
        _chartPeriod.value = period
        when (period) {
            "DAY", "1D" -> _dashboardHorizon.value = TimeHorizon.DAY
            "7D", "WEEK" -> _dashboardHorizon.value = TimeHorizon.WEEK
            "30D" -> _dashboardHorizon.value = TimeHorizon.THIRTY_DAYS
            "MTD", "1M" -> _dashboardHorizon.value = TimeHorizon.MTD
            else -> {}
        }
    }

    // Chart data — reacts to period filter for weekly / monthly / yearly views
    val salesChartData = combine(transactions, _chartPeriod) { txList, period ->
        val sdf = shopDateFormat("yyyy-MM-dd")
        val cal = shopCalendar()
        val items = mutableListOf<ChartDataPoint>()

        when (period) {
            "DAY", "1D" -> {
                val todayStr = sdf.format(Date())
                val todayTxs = txList.filter { sdf.format(Date(it.timestamp)) == todayStr && it.status != "VOIDED" }
                val hourFmt = shopDateFormat("h a")
                val hourSlots = listOf(6, 8, 10, 12, 14, 16, 18, 20)
                hourSlots.forEach { h ->
                    cal.time = Date()
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    val label = hourFmt.format(cal.time)
                    val slotTotal = todayTxs.filter {
                        cal.time = Date(it.timestamp)
                        val th = cal.get(Calendar.HOUR_OF_DAY)
                        th in h until (h + 2)
                    }.sumOf { it.totalAmount }
                    items.add(ChartDataPoint(label, slotTotal, todayStr))
                }
            }
            "7D" -> {
                for (i in 0 until 7) {
                    cal.time = Date()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val dateStr = sdf.format(cal.time)
                    val label = shopDateFormat("EEE").format(cal.time)
                    val total = txList.filter { sdf.format(Date(it.timestamp)) == dateStr && it.status != "VOIDED" }.sumOf { it.totalAmount }
                    items.add(ChartDataPoint(label, total, dateStr))
                }
                items.reverse()
            }
            "30D" -> {
                for (i in 0 until 30) {
                    cal.time = Date()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val dateStr = sdf.format(cal.time)
                    val label = shopDateFormat("d").format(cal.time)
                    val total = txList.filter { sdf.format(Date(it.timestamp)) == dateStr && it.status != "VOIDED" }.sumOf { it.totalAmount }
                    items.add(ChartDataPoint(label, total, dateStr))
                }
                items.reverse()
            }
            "MTD", "1M" -> {
                cal.time = Date()
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                for (d in 1..currentDay) {
                    cal.time = Date()
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    val dateStr = sdf.format(cal.time)
                    val label = "$d"
                    val total = txList.filter { sdf.format(Date(it.timestamp)) == dateStr && it.status != "VOIDED" }.sumOf { it.totalAmount }
                    items.add(ChartDataPoint(label, total, dateStr))
                }
            }
            "1Y" -> {
                val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                cal.time = Date()
                val thisYear = cal.get(Calendar.YEAR)
                for (m in 0..11) {
                    cal.set(thisYear, m, 1)
                    val monthStart = sdf.format(cal.time)
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val monthEnd = sdf.format(cal.time)
                    val total = txList.filter {
                        val d = sdf.format(Date(it.timestamp))
                        d >= monthStart && d <= monthEnd && it.status != "VOIDED"
                    }.sumOf { it.totalAmount }
                    items.add(ChartDataPoint(monthNames[m], total, monthStart))
                }
            }
        }
        items
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Grouped criteria to cleanly combine with transaction flows without exceeding combine arity limits
    private val _historyFilterCriteria = combine(
        _historyHorizon,
        _historyStartDateFilter,
        _historyEndDateFilter,
        _historyStatusFilter,
        _historyCustomerFilter
    ) { horizon, startDate, endDate, status, customer ->
        HistoryFilterCriteria(horizon, startDate, endDate, status, customer)
    }

    // Filtered transaction history
    val filteredTransactions = combine(
        transactions,
        _historyFilterCriteria
    ) { txList, criteria ->
        val boundary = getTimeHorizonBoundary(criteria.horizon, criteria.startDate, criteria.endDate)
        txList.filter { tx ->
            val matchRange = criteria.horizon == TimeHorizon.ALL || (tx.timestamp in boundary.startTimestamp..boundary.endTimestamp)
            val matchStatus = criteria.status == null || tx.status.equals(criteria.status, ignoreCase = true)
            val matchCustomer = criteria.customer == null || (tx.customerName != null && tx.customerName.contains(criteria.customer, ignoreCase = true))
            matchRange && matchStatus && matchCustomer
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Range summary for the currently filtered transactions (total sales, paid, unpaid, order count)
    val historyRangeSummary = combine(
        filteredTransactions,
        _historyFilterCriteria
    ) { txList, criteria ->
        val validTxs = txList.filter { it.status != "VOIDED" }
        val boundary = getTimeHorizonBoundary(criteria.horizon, criteria.startDate, criteria.endDate)
        RangeSalesSummary(
            totalSales = validTxs.sumOf { it.totalAmount },
            paidAmount = validTxs.filter { it.status == "PAID" }.sumOf { it.totalAmount },
            unpaidAmount = validTxs.filter { it.status == "UNPAID" }.sumOf { it.totalAmount },
            count = validTxs.size,
            rangeLabel = boundary.displayLabel
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RangeSalesSummary()
    )

    // Filtered cash-out log
    val filteredCashOutEntries = combine(cashOutEntries, _cashLogDateFilter) { entries, date ->
        val sdf = shopDateFormat("yyyy-MM-dd")
        entries.filter { date == null || sdf.format(Date(it.timestamp)) == date }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Unique customer list for unpaid tracking
    val unpaidCustomers = transactions.map { txList ->
        txList.filter { it.status == "UNPAID" && !it.customerName.isNullOrBlank() }
            .mapNotNull { it.customerName }
            .distinct()
            .sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dynamic Store Operational Cycles:
    // When a store expense (e.g. ₱10,000 delivery) is logged, it calculates period sales and employee expenses
    // from that timestamp up to the next logged store expense (or now if ongoing).
    val operationalCycles: StateFlow<List<OperationalCycleSummary>> = combine(
        operationalExpenses,
        transactions,
        cashOutEntries
    ) { opList, txList, cashList ->
        if (opList.isEmpty()) return@combine emptyList()
        val sortedAsc = opList.sortedBy { it.timestamp }
        val summaries = mutableListOf<OperationalCycleSummary>()
        val n = sortedAsc.size

        for (i in 0 until n) {
            val curr = sortedAsc[i]
            val startTime = curr.timestamp
            val endTime = if (i + 1 < n) sortedAsc[i + 1].timestamp else null
            val isActive = (i == n - 1)

            val cycleTxs = txList.filter { tx ->
                tx.status != "VOIDED" &&
                tx.timestamp >= startTime &&
                (endTime == null || tx.timestamp < endTime)
            }
            val periodSales = cycleTxs.sumOf { it.totalAmount }
            val periodOrders = cycleTxs.size

            val periodEmpExpenses = cashList.filter { co ->
                co.timestamp >= startTime &&
                (endTime == null || co.timestamp < endTime)
            }.sumOf { it.amount }

            val totalCost = (curr.amount + periodEmpExpenses).roundToCentavos()
            val net = (periodSales - totalCost).roundToCentavos()
            val recoveryRate = if (totalCost > 0) (periodSales / totalCost) * 100.0 else 100.0

            summaries.add(
                OperationalCycleSummary(
                    expense = curr,
                    periodStartTimestamp = startTime,
                    periodEndTimestamp = endTime,
                    periodSalesTotal = periodSales,
                    periodOrderCount = periodOrders,
                    periodEmployeeExpenses = periodEmpExpenses,
                    totalPeriodCost = totalCost,
                    netBalance = net,
                    recoveryRate = recoveryRate,
                    isActive = isActive
                )
            )
        }

        summaries.reversed()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}

data class OperationalCycleSummary(
    val expense: OperationalExpense,
    val periodStartTimestamp: Long,
    val periodEndTimestamp: Long?,
    val periodSalesTotal: Double,
    val periodOrderCount: Int,
    val periodEmployeeExpenses: Double,
    val totalPeriodCost: Double,
    val netBalance: Double,
    val recoveryRate: Double,
    val isActive: Boolean
) {
    val storeExpense: Double get() = expense.amount
    val grossProfit: Double get() = (periodSalesTotal - storeExpense).roundToCentavos()
    val netGain: Double get() = netBalance
    val isProfitable: Boolean get() = netGain >= 0.0
    val profitMargin: Double get() = if (periodSalesTotal > 0) ((netGain / periodSalesTotal) * 100.0).roundToCentavos() else 0.0
}

private data class HistoryFilterCriteria(
    val horizon: TimeHorizon = TimeHorizon.ALL,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: String? = null,
    val customer: String? = null
)

data class RangeSalesStats(
    val horizon: TimeHorizon = TimeHorizon.DAY,
    val rangeLabel: String = "Today",
    val totalSales: Double = 0.0,
    val paidAmount: Double = 0.0,
    val unpaidAmount: Double = 0.0,
    val salesCount: Int = 0,
    val expenseTotal: Double = 0.0,
    val netAmount: Double = 0.0
)

data class RangeSalesSummary(
    val totalSales: Double = 0.0,
    val paidAmount: Double = 0.0,
    val unpaidAmount: Double = 0.0,
    val count: Int = 0,
    val rangeLabel: String = ""
)

data class DashboardStats(
    val totalSalesToday: Double = 0.0,
    val paidToday: Double = 0.0,
    val unpaidToday: Double = 0.0,
    val salesCount: Int = 0,
    val avgTicketSize: Double = 0.0
)

data class ChartDataPoint(
    val label: String,
    val amount: Double,
    val dateString: String
)

data class BorrowStats(
    val totalOutstanding: Double = 0.0,
    val outstandingCount: Int = 0,
    val oldestDays: Int = 0
)

class POSViewModelFactory(
    private val repository: FirestorePOSRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(POSViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return POSViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

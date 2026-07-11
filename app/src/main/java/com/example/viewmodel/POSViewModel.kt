package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.StockValidationResult
import com.example.data.roundToCentavos
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CartItem(
    val product: Product,
    val variation: ProductVariation,
    val quantity: Double
)

class POSViewModel(private val repository: FirestorePOSRepository) : ViewModel() {

    // ── Processing guards — prevent duplicate submissions ──
    private val _isProcessingTransaction = MutableStateFlow(false)
    val isProcessingTransaction: StateFlow<Boolean> = _isProcessingTransaction.asStateFlow()

    private val _isSavingProduct = MutableStateFlow(false)
    val isSavingProduct: StateFlow<Boolean> = _isSavingProduct.asStateFlow()

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

    // Active Shopping Cart State
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    // POS Screen Filtering & Searching
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // Screen navigation flow (Custom state-based navigation for high-performance and simplicity)
    private val _currentScreen = MutableStateFlow("HOME") // HOME, POS, ADD_PRODUCT, TRANSACTIONS, REPORTS
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Selected product for variation selection overlay (Screen 3)
    private val _selectedProductForVariations = MutableStateFlow<Product?>(null)
    val selectedProductForVariations: StateFlow<Product?> = _selectedProductForVariations.asStateFlow()

    // Filter states for Transaction History
    private val _historyDateFilter = MutableStateFlow<String?>(null) // Format: "yyyy-MM-dd" or null
    val historyDateFilter: StateFlow<String?> = _historyDateFilter.asStateFlow()

    private val _historyStatusFilter = MutableStateFlow<String?>(null) // "PAID", "UNPAID", or null
    val historyStatusFilter: StateFlow<String?> = _historyStatusFilter.asStateFlow()

    private val _historyCustomerFilter = MutableStateFlow<String?>(null) // Customer name or null
    val historyCustomerFilter: StateFlow<String?> = _historyCustomerFilter.asStateFlow()

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
    fun addToCart(product: Product, variation: ProductVariation, quantity: Double) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id && it.variation.id == variation.id }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            currentList.add(CartItem(product, variation, quantity))
        }
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

    // Process payment and finalize transaction (6.7 — centavo-rounded calculations)
    fun finalizeTransaction(status: String, customerName: String?, onSuccess: () -> Unit) {
        if (_isProcessingTransaction.value) return // guard: prevent double-tap
        _isProcessingTransaction.value = true
        viewModelScope.launch {
            try {
                val items = _cartItems.value
                if (items.isEmpty()) return@launch

            val subtotal = items.sumOf { it.variation.price * it.quantity }.roundToCentavos()
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
                    variationName = cartItem.variation.name,
                    price = cartItem.variation.price.roundToCentavos(),
                    quantity = cartItem.quantity
                )
            }

            repository.processTransaction(record, transactionItems)
            clearCart()
            onSuccess()
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
        stockLevel: Double,
        lowStockThreshold: Double,
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
                stockLevel = stockLevel,
                lowStockThreshold = lowStockThreshold
            )
            if (id == 0) {
                repository.insertProduct(product, variationsList)
            } else {
                repository.updateProduct(product, variationsList)
            }
            onSuccess()
            } finally {
                _isSavingProduct.value = false
            }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    fun startEditingProduct(product: Product?) {
        _editingProduct.value = product
    }

    // Category Management
    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name))
        }
    }

    fun deleteCategory(category: Category, onBlocked: () -> Unit = {}) {
        viewModelScope.launch {
            val deleted = repository.deleteCategory(category)
            if (!deleted) {
                onBlocked() // 6.8 — blocked because products still use this category
            }
        }
    }

    // 6.3 — Mark an unpaid transaction as paid (settle balance)
    fun markTransactionAsPaid(transactionId: Int) {
        viewModelScope.launch {
            repository.markTransactionAsPaid(transactionId)
        }
    }

    // 6.6 — Void a transaction and restore stock
    fun voidTransaction(transaction: TransactionRecord) {
        viewModelScope.launch {
            repository.voidTransaction(transaction)
        }
    }

    // 6.6 — Delete transaction WITH stock restoration
    fun deleteTransactionWithStockRestore(transaction: TransactionRecord) {
        viewModelScope.launch {
            repository.deleteTransactionWithStockRestore(transaction)
        }
    }

    // Legacy delete (kept for backward compatibility, but prefer deleteTransactionWithStockRestore)
    fun deleteTransaction(transaction: TransactionRecord) {
        viewModelScope.launch {
            repository.deleteTransactionWithStockRestore(transaction)
        }
    }

    // 6.4 — Get customer name suggestions for autocomplete
    private val _customerSuggestions = MutableStateFlow<List<String>>(emptyList())
    val customerSuggestions: StateFlow<List<String>> = _customerSuggestions.asStateFlow()

    fun loadCustomerSuggestions() {
        viewModelScope.launch {
            _customerSuggestions.value = repository.getUnpaidCustomerNames()
        }
    }

    // 6.2 — Validate stock before adding to cart
    fun validateStockForCart(
        product: Product,
        variation: ProductVariation,
        requestedQuantity: Double,
        onResult: (StockValidationResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.validateStockAvailability(
                productId = product.id,
                variationId = variation.id,
                requestedQuantity = requestedQuantity
            )
            onResult(result)
        }
    }

    // Delete transaction (no stock restore — used for admin cleanup of voided records)

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
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Transaction History Filter Actions
    fun setHistoryFilters(date: String?, status: String?, customer: String?) {
        _historyDateFilter.value = date
        _historyStatusFilter.value = status
        _historyCustomerFilter.value = customer
    }

    fun clearHistoryFilters() {
        _historyDateFilter.value = null
        _historyStatusFilter.value = null
        _historyCustomerFilter.value = null
    }

    // Dashboard Calculations & State Selectors (Dynamic)
    val dashboardStats = transactions.map { txList ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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

    // Chart period filter: "7D", "1M", "1Y"
    private val _chartPeriod = MutableStateFlow("7D")
    val chartPeriod: StateFlow<String> = _chartPeriod.asStateFlow()

    fun setChartPeriod(period: String) { _chartPeriod.value = period }

    // Chart data — reacts to period filter for weekly / monthly / yearly views
    val salesChartData = combine(transactions, _chartPeriod) { txList, period ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val items = mutableListOf<ChartDataPoint>()

        when (period) {
            "7D" -> {
                for (i in 0 until 7) {
                    cal.time = Date()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val dateStr = sdf.format(cal.time)
                    val label = SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
                    val total = txList.filter { sdf.format(Date(it.timestamp)) == dateStr && it.status != "VOIDED" }.sumOf { it.totalAmount }
                    items.add(ChartDataPoint(label, total, dateStr))
                }
                items.reverse()
            }
            "1M" -> {
                cal.time = Date()
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (d in 1..daysInMonth) {
                    cal.time = Date()
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    val dateStr = sdf.format(cal.time)
                    val label = "${d}"
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

    // Low stock products
    val lowStockProducts = products.map { prodList ->
        prodList.filter { it.stockLevel <= it.lowStockThreshold }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered transaction history
    val filteredTransactions = combine(
        transactions,
        _historyDateFilter,
        _historyStatusFilter,
        _historyCustomerFilter
    ) { txList, date, status, customer ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        txList.filter { tx ->
            val matchDate = date == null || sdf.format(Date(tx.timestamp)) == date
            val matchStatus = status == null || tx.status.equals(status, ignoreCase = true)
            val matchCustomer = customer == null || (tx.customerName != null && tx.customerName.contains(customer, ignoreCase = true))
            matchDate && matchStatus && matchCustomer
        }
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
}

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

class POSViewModelFactory(private val repository: FirestorePOSRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(POSViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return POSViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

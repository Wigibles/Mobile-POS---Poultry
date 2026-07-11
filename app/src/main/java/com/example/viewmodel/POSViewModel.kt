package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CartItem(
    val product: Product,
    val variation: ProductVariation,
    val quantity: Double
)

class POSViewModel(private val repository: POSRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.prepopulateDatabaseIfEmpty()
        }
    }

    // UI State Flows from Repository
    val products = repository.products.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories = repository.categories.stateIn(
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

    // Process payment and finalize transaction
    fun finalizeTransaction(status: String, customerName: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val items = _cartItems.value
            if (items.isEmpty()) return@launch

            val subtotal = items.sumOf { it.variation.price * it.quantity }
            val tax = 0.0 // can be modified if needed
            val discount = 0.0
            val total = subtotal + tax - discount

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
                    price = cartItem.variation.price,
                    quantity = cartItem.quantity
                )
            }

            repository.processTransaction(record, transactionItems)
            clearCart()
            onSuccess()
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
        viewModelScope.launch {
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

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun deleteTransaction(transaction: TransactionRecord) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
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
            sdf.format(Date(it.timestamp)) == todayStr
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

    // Chart Data calculations for Last 7 days
    val last7DaysSalesChart = transactions.map { txList ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val chartItems = mutableListOf<ChartDataPoint>()
        val cal = Calendar.getInstance()

        // Gather for past 7 days
        for (i in 0 until 7) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cal.time)
            val dayLabel = SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time) // "Mon", "Tue" etc.

            val totalForDay = txList.filter {
                sdf.format(Date(it.timestamp)) == dateStr
            }.sumOf { it.totalAmount }

            chartItems.add(ChartDataPoint(dayLabel, totalForDay, dateStr))
        }

        chartItems.reversed() // Oldest to newest
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

class POSViewModelFactory(private val repository: POSRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(POSViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return POSViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

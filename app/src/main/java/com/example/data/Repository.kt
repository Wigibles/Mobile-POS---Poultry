package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * Rounds a Double to exactly 2 decimal places (centavo precision)
 * to avoid floating-point accumulation errors in price calculations.
 * Requirement 6.7 — Currency Precision.
 */
fun Double.roundToCentavos(): Double = (this * 100.0).roundToLong() / 100.0

class POSRepository(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) {
    val products: Flow<List<Product>> = productDao.getAllProducts()
    val variations: Flow<List<ProductVariation>> = productDao.getAllVariations()
    val categories: Flow<List<Category>> = categoryDao.getAllCategories()
    val transactions: Flow<List<TransactionRecord>> = transactionDao.getAllTransactions()
    val allTransactionItems: Flow<List<TransactionItem>> = transactionDao.getAllTransactionItems()

    fun getVariationsForProduct(productId: Int): Flow<List<ProductVariation>> =
        productDao.getVariationsForProduct(productId)

    fun getTransactionItems(transactionId: Int): Flow<List<TransactionItem>> =
        transactionDao.getTransactionItems(transactionId)

    // Compound Product Insertion
    suspend fun insertProduct(product: Product, variationsList: List<ProductVariation>): Int = withContext(Dispatchers.IO) {
        ensureCategoryExists(product.category)
        val productId = productDao.insertProduct(product).toInt()
        val variationsWithId = variationsList.map { it.copy(productId = productId) }
        productDao.insertVariations(variationsWithId)
        productId
    }

    // Compound Product Update
    suspend fun updateProduct(product: Product, variationsList: List<ProductVariation>) = withContext(Dispatchers.IO) {
        ensureCategoryExists(product.category)
        productDao.updateProduct(product)
        productDao.deleteVariationsForProduct(product.id)
        val variationsWithId = variationsList.map { it.copy(productId = product.id) }
        productDao.insertVariations(variationsWithId)
    }

    /**
     * Auto-creates a category if it doesn't already exist.
     * This keeps the category filter chips in sync with product data.
     */
    private suspend fun ensureCategoryExists(categoryName: String) {
        if (categoryName.isBlank()) return
        val existing = categoryDao.getAllCategories().first()
        if (existing.none { it.name.equals(categoryName, ignoreCase = true) }) {
            categoryDao.insertCategory(Category(name = categoryName))
        }
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        productDao.deleteProduct(product)
    }

    suspend fun insertCategory(category: Category): Boolean = withContext(Dispatchers.IO) {
        val current = categoryDao.getAllCategories().first()
        if (current.any { it.name.equals(category.name, ignoreCase = true) }) {
            false
        } else {
            categoryDao.insertCategory(category)
            true
        }
    }

    /**
     * 6.8 — Category Deletion with Existing Products:
     * Block deletion if products are still assigned to this category.
     * Returns true if deleted, false if blocked.
     */
    suspend fun deleteCategory(category: Category): Boolean = withContext(Dispatchers.IO) {
        val productsInCategory = productDao.getProductsByCategory(category.name)
        if (productsInCategory.isNotEmpty()) {
            false // blocked: products still assigned
        } else {
            categoryDao.deleteCategory(category)
            true
        }
    }

    /**
     * 6.2 + 6.1 — Stock validation with variant multiplier:
     * Checks if there is enough base-unit stock for the requested quantity × multiplier.
     * Returns the available stock in base units, or null if product not found.
     */
    suspend fun getAvailableStock(productId: Int): Double? = withContext(Dispatchers.IO) {
        productDao.getProductById(productId)?.stockLevel
    }

    /**
     * Validates whether the requested quantity × variant multiplier does not exceed stock.
     * Returns a StockValidationResult with the outcome.
     */
    suspend fun validateStockAvailability(
        productId: Int,
        variationId: Int,
        requestedQuantity: Double
    ): StockValidationResult = withContext(Dispatchers.IO) {
        val product = productDao.getProductById(productId)
        if (product == null) return@withContext StockValidationResult(false, 0.0, 0.0)

        val variation = productDao.getVariationById(variationId)
        val multiplier = variation?.multiplier ?: 1.0
        val baseUnitsNeeded = requestedQuantity * multiplier

        StockValidationResult(
            isAvailable = baseUnitsNeeded <= product.stockLevel,
            availableStock = product.stockLevel,
            baseUnitsNeeded = baseUnitsNeeded
        )
    }

    /**
     * 6.1 + 6.7 — Process a transaction with variant-multiplier stock deduction
     * and centavo-rounded price calculations.
     *
     * Stock deduction = sum of (quantity × variant.multiplier) per line item.
     * All monetary values are rounded to centavos at each step.
     */
    suspend fun processTransaction(
        transaction: TransactionRecord,
        items: List<TransactionItem>
    ): Int = withContext(Dispatchers.IO) {
        // Round monetary fields to centavos (6.7)
        val roundedTransaction = transaction.copy(
            subtotal = transaction.subtotal.roundToCentavos(),
            tax = transaction.tax.roundToCentavos(),
            discount = transaction.discount.roundToCentavos(),
            totalAmount = transaction.totalAmount.roundToCentavos()
        )

        val transactionId = transactionDao.insertTransaction(roundedTransaction).toInt()
        val itemsWithId = items.map {
            it.copy(
                transactionId = transactionId,
                price = it.price.roundToCentavos()
            )
        }
        transactionDao.insertTransactionItems(itemsWithId)

        // 6.1 — Decrement stock using variant multiplier
        for (item in items) {
            val product = productDao.getProductById(item.productId)
            if (product != null) {
                // Find the matching variation by looking up all variations for this product
                val allVars = productDao.getVariationsForProduct(item.productId).first()
                val matchedVar = allVars.find { it.name == item.variationName }
                val multiplier = matchedVar?.multiplier ?: 1.0
                val baseUnitsDeducted = (item.quantity * multiplier).roundToCentavos()
                val newStock = (product.stockLevel - baseUnitsDeducted).coerceAtLeast(0.0).roundToCentavos()
                productDao.updateProduct(product.copy(stockLevel = newStock))
            }
        }
        transactionId
    }

    // Get variation ID from a TransactionItem (needed for multiplier lookup)
    private suspend fun findVariationId(productId: Int, variationName: String): Int? {
        val vars = productDao.getVariationsForProduct(productId).first()
        return vars.find { it.name == variationName }?.id
    }

    /**
     * 6.3 — Mark an existing Unpaid transaction as Paid (settle balance).
     * Updates status and timestamp without duplicating the record.
     */
    suspend fun markTransactionAsPaid(transactionId: Int) = withContext(Dispatchers.IO) {
        transactionDao.updateTransactionStatus(
            transactionId = transactionId,
            status = "PAID",
            settledTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * 6.6 — Void a finalized transaction and restore deducted stock.
     * The transaction status becomes "VOIDED" and all stock is restored.
     */
    suspend fun voidTransaction(transaction: TransactionRecord) = withContext(Dispatchers.IO) {
        val items = transactionDao.getTransactionItems(transaction.id).first()

        // Restore stock for each item using variant multiplier (matched by variation name)
        for (item in items) {
            val product = productDao.getProductById(item.productId)
            if (product != null) {
                val allVars = productDao.getVariationsForProduct(item.productId).first()
                val matchedVar = allVars.find { it.name == item.variationName }
                val multiplier = matchedVar?.multiplier ?: 1.0
                val baseUnitsRestored = (item.quantity * multiplier).roundToCentavos()
                val newStock = (product.stockLevel + baseUnitsRestored).roundToCentavos()
                productDao.updateProduct(product.copy(stockLevel = newStock))
            }
        }

        // Mark transaction as voided
        transactionDao.updateTransactionStatus(
            transactionId = transaction.id,
            status = "VOIDED",
            settledTimestamp = null
        )
    }

    /**
     * 6.6 — Delete a transaction AND restore stock (unlike plain delete which loses stock).
     */
    suspend fun deleteTransactionWithStockRestore(transaction: TransactionRecord) = withContext(Dispatchers.IO) {
        val items = transactionDao.getTransactionItems(transaction.id).first()

        // Restore stock if transaction was PAID or UNPAID (not already VOIDED)
        if (transaction.status != "VOIDED") {
            for (item in items) {
                val product = productDao.getProductById(item.productId)
                if (product != null) {
                    val allVars = productDao.getVariationsForProduct(item.productId).first()
                    val matchedVar = allVars.find { it.name == item.variationName }
                    val multiplier = matchedVar?.multiplier ?: 1.0
                    val baseUnitsRestored = (item.quantity * multiplier).roundToCentavos()
                    val newStock = (product.stockLevel + baseUnitsRestored).roundToCentavos()
                    productDao.updateProduct(product.copy(stockLevel = newStock))
                }
            }
        }

        transactionDao.deleteTransactionItems(transaction.id)
        transactionDao.deleteTransaction(transaction)
    }

    /**
     * Get all unique customer names from unpaid transactions (for autocomplete — 6.4).
     */
    suspend fun getUnpaidCustomerNames(): List<String> = withContext(Dispatchers.IO) {
        transactionDao.getAllTransactions().first()
            .filter { it.status == "UNPAID" && !it.customerName.isNullOrBlank() }
            .mapNotNull { it.customerName }
            .distinct()
            .sorted()
    }

    // No dummy data — the database starts empty for real data entry.
}

/**
 * Result of stock availability validation (Requirement 6.2).
 */
data class StockValidationResult(
    val isAvailable: Boolean,
    val availableStock: Double,
    val baseUnitsNeeded: Double
)

package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

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
        val productId = productDao.insertProduct(product).toInt()
        val variationsWithId = variationsList.map { it.copy(productId = productId) }
        productDao.insertVariations(variationsWithId)
        productId
    }

    // Compound Product Update
    suspend fun updateProduct(product: Product, variationsList: List<ProductVariation>) = withContext(Dispatchers.IO) {
        productDao.updateProduct(product)
        productDao.deleteVariationsForProduct(product.id)
        val variationsWithId = variationsList.map { it.copy(productId = product.id) }
        productDao.insertVariations(variationsWithId)
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

    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategory(category)
    }

    // Process a transaction: insert the record, insert items, and automatically decrement product stock levels!
    suspend fun processTransaction(
        transaction: TransactionRecord,
        items: List<TransactionItem>
    ): Int = withContext(Dispatchers.IO) {
        val transactionId = transactionDao.insertTransaction(transaction).toInt()
        val itemsWithId = items.map { it.copy(transactionId = transactionId) }
        transactionDao.insertTransactionItems(itemsWithId)

        // Decrement stock levels for each item purchased
        for (item in items) {
            val product = productDao.getProductById(item.productId)
            if (product != null) {
                val newStock = (product.stockLevel - item.quantity).coerceAtLeast(0.0)
                productDao.updateProduct(product.copy(stockLevel = newStock))
            }
        }
        transactionId
    }

    suspend fun deleteTransaction(transaction: TransactionRecord) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transaction)
    }

    // Pre-populate database with default categories, realistic products, variations, and matching past transactions
    suspend fun prepopulateDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        val currentCategories = categoryDao.getAllCategories().first()
        if (currentCategories.isNotEmpty()) return@withContext // Database already initialized

        // 1. Insert Categories
        val cats = listOf("Feeds", "Vitamins", "Medicine", "Equipment")
        cats.forEach { categoryDao.insertCategory(Category(name = it)) }

        // 2. Insert Products & Variations
        val p1 = Product(name = "Chick Booster Feeds", category = "Feeds", stockLevel = 45.0, lowStockThreshold = 10.0)
        val p1Id = productDao.insertProduct(p1).toInt()
        productDao.insertVariations(listOf(
            ProductVariation(productId = p1Id, name = "per Kilo", price = 45.0),
            ProductVariation(productId = p1Id, name = "3 Kilograms", price = 130.0),
            ProductVariation(productId = p1Id, name = "50kg Bag", price = 2100.0)
        ))

        // Low stock products to match the mockup's low stock alert
        val p2 = Product(name = "Broiler Grower Feeds", category = "Feeds", stockLevel = 4.0, lowStockThreshold = 8.0)
        val p2Id = productDao.insertProduct(p2).toInt()
        productDao.insertVariations(listOf(
            ProductVariation(productId = p2Id, name = "per Kilo", price = 42.0),
            ProductVariation(productId = p2Id, name = "3 Kilograms", price = 120.0),
            ProductVariation(productId = p2Id, name = "50kg Bag", price = 1950.0)
        ))

        val p3 = Product(name = "Egg Layer Mash", category = "Feeds", stockLevel = 2.0, lowStockThreshold = 5.0)
        val p3Id = productDao.insertProduct(p3).toInt()
        productDao.insertVariations(listOf(
            ProductVariation(productId = p3Id, name = "per Kilo", price = 38.0),
            ProductVariation(productId = p3Id, name = "3 Kilograms", price = 110.0),
            ProductVariation(productId = p3Id, name = "50kg Bag", price = 1800.0)
        ))

        val p4 = Product(name = "Poultry Vitamins (100g)", category = "Vitamins", stockLevel = 25.0, lowStockThreshold = 5.0)
        val p4Id = productDao.insertProduct(p4).toInt()
        productDao.insertVariations(listOf(
            ProductVariation(productId = p4Id, name = "Standard Pack", price = 180.0)
        ))

        val p5 = Product(name = "Antibiotic Soluble Powder", category = "Medicine", stockLevel = 8.0, lowStockThreshold = 4.0)
        val p5Id = productDao.insertProduct(p5).toInt()
        productDao.insertVariations(listOf(
            ProductVariation(productId = p5Id, name = "50g Pack", price = 220.0),
            ProductVariation(productId = p5Id, name = "150g Pack", price = 550.0)
        ))

        val p6 = Product(name = "Chicken Feeder (Medium)", category = "Equipment", stockLevel = 12.0, lowStockThreshold = 3.0)
        val p6Id = productDao.insertProduct(p6).toInt()
        productDao.insertVariations(listOf(
            ProductVariation(productId = p6Id, name = "Standard Unit", price = 120.0)
        ))

        val p7 = Product(name = "Chicken Waterer (2 Gal)", category = "Equipment", stockLevel = 15.0, lowStockThreshold = 3.0)
        val p7Id = productDao.insertProduct(p7).toInt()
        productDao.insertVariations(listOf(
            ProductVariation(productId = p7Id, name = "Standard Unit", price = 150.0)
        ))

        // 3. Prepopulate past transactions spanning the last 7 days to match the design's graphs
        // Today: ₱5,230 (Paid: ₱3,000, Card/Unpaid split, let's create a few matching records)
        // Yesterday (1 day ago): ₱4,200
        // 2 days ago: ₱3,100
        // 3 days ago: ₱2,500
        // 4 days ago: ₱4,800
        // 5 days ago: ₱3,900
        // 6 days ago: ₱3,400

        val cal = Calendar.getInstance()
        val salesDistribution = listOf(
            5230.0, // Today
            4200.0, // 1 day ago
            3100.0, // 2 days ago
            2500.0, // 3 days ago
            4800.0, // 4 days ago
            3900.0, // 5 days ago
            3400.0  // 6 days ago
        )

        for (i in salesDistribution.indices) {
            val amount = salesDistribution[i]
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val timestamp = cal.timeInMillis

            // For today (index 0), let's split into a Paid and Unpaid transaction to match the mockup
            if (i == 0) {
                // Paid Transaction of ₱3,000
                val tPaidId = transactionDao.insertTransaction(
                    TransactionRecord(
                        customerName = null,
                        timestamp = timestamp - 3600 * 1000, // 1 hour ago
                        status = "PAID",
                        subtotal = 3000.0,
                        tax = 0.0,
                        discount = 0.0,
                        totalAmount = 3000.0
                    )
                ).toInt()
                transactionDao.insertTransactionItems(listOf(
                    TransactionItem(
                        transactionId = tPaidId,
                        productId = p1Id,
                        productName = "Chick Booster Feeds",
                        variationName = "50kg Bag",
                        price = 2100.0,
                        quantity = 1.0
                    ),
                    TransactionItem(
                        transactionId = tPaidId,
                        productId = p4Id,
                        productName = "Poultry Vitamins (100g)",
                        variationName = "Standard Pack",
                        price = 180.0,
                        quantity = 5.0
                    )
                ))

                // Unpaid Transaction of ₱2,230 for customer "Mang Juan"
                val tUnpaidId = transactionDao.insertTransaction(
                    TransactionRecord(
                        customerName = "Mang Juan",
                        timestamp = timestamp,
                        status = "UNPAID",
                        subtotal = 2230.0,
                        tax = 0.0,
                        discount = 0.0,
                        totalAmount = 2230.0
                    )
                ).toInt()
                transactionDao.insertTransactionItems(listOf(
                    TransactionItem(
                        transactionId = tUnpaidId,
                        productId = p2Id,
                        productName = "Broiler Grower Feeds",
                        variationName = "50kg Bag",
                        price = 1950.0,
                        quantity = 1.0
                    ),
                    TransactionItem(
                        transactionId = tUnpaidId,
                        productId = p1Id,
                        productName = "Chick Booster Feeds",
                        variationName = "per Kilo",
                        price = 45.0,
                        quantity = 6.0
                    ),
                    TransactionItem(
                        transactionId = tUnpaidId,
                        productId = p6Id,
                        productName = "Chicken Feeder (Medium)",
                        variationName = "Standard Unit",
                        price = 120.0,
                        quantity = 1.0
                    )
                ))
            } else {
                // Past days: standard paid transactions
                val tId = transactionDao.insertTransaction(
                    TransactionRecord(
                        customerName = if (i == 2) "Aling Nena" else null, // A historic unpaid debt
                        timestamp = timestamp,
                        status = if (i == 2) "UNPAID" else "PAID",
                        subtotal = amount,
                        tax = 0.0,
                        discount = 0.0,
                        totalAmount = amount
                    )
                ).toInt()
                transactionDao.insertTransactionItems(listOf(
                    TransactionItem(
                        transactionId = tId,
                        productId = p1Id,
                        productName = "Chick Booster Feeds",
                        variationName = "per Kilo",
                        price = 45.0,
                        quantity = amount / 45.0
                    )
                ))
            }
        }
    }
}

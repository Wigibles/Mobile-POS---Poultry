package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Int): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    // Variations
    @Query("SELECT * FROM product_variations")
    fun getAllVariations(): Flow<List<ProductVariation>>

    @Query("SELECT * FROM product_variations WHERE productId = :productId")
    fun getVariationsForProduct(productId: Int): Flow<List<ProductVariation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariation(variation: ProductVariation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariations(variations: List<ProductVariation>)

    @Query("DELETE FROM product_variations WHERE productId = :productId")
    suspend fun deleteVariationsForProduct(productId: Int)

    @Delete
    suspend fun deleteVariation(variation: ProductVariation)

    @Query("SELECT * FROM product_variations WHERE id = :id")
    suspend fun getVariationById(id: Int): ProductVariation?

    @Query("SELECT * FROM products WHERE category = :category")
    suspend fun getProductsByCategory(category: String): List<Product>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionRecord>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionById(id: Int): Flow<TransactionRecord?>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    fun getTransactionItems(transactionId: Int): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transaction_items")
    fun getAllTransactionItems(): Flow<List<TransactionItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItem>)

    @Update
    suspend fun updateTransaction(transaction: TransactionRecord)

    @Query("UPDATE transactions SET status = :status, settledTimestamp = :settledTimestamp WHERE id = :transactionId")
    suspend fun updateTransactionStatus(transactionId: Int, status: String, settledTimestamp: Long?)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionRecord)

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun deleteTransactionItems(transactionId: Int)
}

package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String,
    val stockLevel: Double,
    val lowStockThreshold: Double = 5.0,
    val imageUrl: String? = null
)

@Entity(
    tableName = "product_variations",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"])]
)
data class ProductVariation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val name: String, // e.g., "per kilo", "per 3kg", "50kg bag"
    val price: Double,
    val multiplier: Double = 1.0 // base-unit multiplier: 1.0 = per kilo, 3.0 = per 3kg, 50.0 = 50kg bag
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "PAID", "UNPAID", or "VOIDED"
    val subtotal: Double,
    val tax: Double,
    val discount: Double,
    val totalAmount: Double,
    val settledTimestamp: Long? = null // when an unpaid transaction was marked as paid
)

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionRecord::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"])]
)
data class TransactionItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionId: Int,
    val productId: Int,
    val productName: String,
    val variationName: String,
    val price: Double,
    val quantity: Double,
    // Base-unit multiplier captured at sale time so stock restore on void/delete
    // is always accurate even if the variation's multiplier is edited later.
    val multiplier: Double = 1.0
)

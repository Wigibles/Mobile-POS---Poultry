package com.example.data

data class Product(
    val id: Int = 0,
    val name: String,
    val category: String,
    val stockLevel: Double,
    val lowStockThreshold: Double = 5.0
)

data class ProductVariation(
    val id: Int = 0,
    val productId: Int,
    val name: String, // e.g., "per kilo", "per 3kg", "50kg bag"
    val price: Double,
    val multiplier: Double = 1.0 // base-unit multiplier: 1.0 = per kilo, 3.0 = per 3kg, 50.0 = 50kg bag
)

data class Category(
    val id: Int = 0,
    val name: String
)

data class TransactionRecord(
    val id: Int = 0,
    val customerName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "PAID", "UNPAID", or "VOIDED"
    val subtotal: Double,
    val tax: Double,
    val discount: Double,
    val totalAmount: Double,
    val settledTimestamp: Long? = null // when an unpaid transaction was marked as paid
)

data class TransactionItem(
    val id: Int = 0,
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

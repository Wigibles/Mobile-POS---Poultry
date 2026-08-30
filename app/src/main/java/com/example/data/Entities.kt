package com.example.data

data class Product(
    val id: Int = 0,
    val name: String,
    val category: String,
    val supplyCount: Double? = null
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
    val multiplier: Double = 1.0
)

enum class Role { ADMIN, CASHIER }

// Synced across devices via Firestore (settings/auth doc) so a PIN change on one
// device takes effect everywhere. Not hashed — a 4-digit PIN with no server secret
// to protect gains nothing from hashing.
data class AuthSettings(
    val adminPin: String = "1234",
    val cashierPin: String = "0000"
)

// Cash a cashier takes out of the till (e.g. their daily pay), logged for the owner
// to audit later since they don't monitor daily operations in person.
data class CashOutEntry(
    val id: Int = 0,
    val cashierName: String,
    val amount: Double,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// Money a family member borrowed from the store, tracked separately from employee
// expenses. Binary outstanding/returned, like TransactionRecord's PAID/UNPAID.
data class BorrowEntry(
    val id: Int = 0,
    val borrowerName: String,
    val amount: Double,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val returnedTimestamp: Long? = null // null = still outstanding
)

// Store operational expense (e.g. delivery of feeds/supplies) that initiates a sales-tracking cycle.
data class OperationalExpense(
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)


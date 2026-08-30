package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A transaction that was logged while the device was offline.
 *
 * When the device regains connectivity these are flushed to Firestore in FIFO order,
 * assigned real server-side IDs, and then removed from this table. Until that happens
 * the stock they consume is tracked via [PendingTransactionItemEntity] so the UI can
 * show an "effective" stock level that accounts for what's already been reserved.
 */
@Entity(
    tableName = "pending_transactions",
    indices = [Index(value = ["synced_at"], name = "idx_pending_tx_synced")]
)
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    @ColumnInfo(name = "customer_name")
    val customerName: String?,

    @ColumnInfo(name = "status")
    val status: String, // "PAID" or "UNPAID"

    @ColumnInfo(name = "subtotal")
    val subtotal: Double,

    @ColumnInfo(name = "tax")
    val tax: Double,

    @ColumnInfo(name = "discount")
    val discount: Double,

    @ColumnInfo(name = "total_amount")
    val totalAmount: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null // null = still pending; non-null = synced (kept for audit)
)

/**
 * One line item inside a pending transaction. Mirrors [com.example.data.TransactionItem]
 * but uses [pendingTransactionLocalId] instead of a server-assigned transaction id.
 */
@Entity(
    tableName = "pending_transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = PendingTransactionEntity::class,
            parentColumns = ["localId"],
            childColumns = ["pending_tx_local_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pending_tx_local_id"])]
)
data class PendingTransactionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    @ColumnInfo(name = "pending_tx_local_id")
    val pendingTransactionLocalId: Long,

    @ColumnInfo(name = "product_id")
    val productId: Int,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "variation_name")
    val variationName: String,

    @ColumnInfo(name = "price")
    val price: Double,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "multiplier")
    val multiplier: Double
)

// ── Cash-Out & Borrow ──────────────────────────────────────────────────────
// Simpler than transactions — single-row records, no child items.  Queued when
// the device is offline so cashiers can log expenses and family borrowings
// without waiting for connectivity.

@Entity(
    tableName = "pending_cash_outs",
    indices = [Index(value = ["synced_at"], name = "idx_pending_co_synced")]
)
data class PendingCashOutEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    @ColumnInfo(name = "cashier_name")
    val cashierName: String,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "note")
    val note: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null
)

@Entity(
    tableName = "pending_borrows",
    indices = [Index(value = ["synced_at"], name = "idx_pending_br_synced")]
)
data class PendingBorrowEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    @ColumnInfo(name = "borrower_name")
    val borrowerName: String,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "note")
    val note: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null
)

// ── Google Sheets Sync ─────────────────────────────────────────────────────
// Transactions are also pushed to Google Sheets for record-keeping. When the
// device is offline the payload is queued here and sent when connectivity returns.

@Entity(
    tableName = "pending_sheet_entries",
    indices = [Index(value = ["synced_at"], name = "idx_pending_sheet_synced")]
)
data class PendingSheetEntry(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Int, // Firestore transaction ID (or negative local ID)

    @ColumnInfo(name = "payload_json")
    val payloadJson: String, // JSON string to POST to Google Apps Script

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null
)

@Entity(
    tableName = "pending_operational_expenses",
    indices = [Index(value = ["synced_at"], name = "idx_pending_oe_synced")]
)
data class PendingOperationalExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "note")
    val note: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null
)


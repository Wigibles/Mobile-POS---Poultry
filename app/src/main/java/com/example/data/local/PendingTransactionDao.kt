package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {

    // ── Write ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: PendingTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PendingTransactionItemEntity>)

    @Transaction
    suspend fun enqueueTransaction(
        tx: PendingTransactionEntity,
        items: List<PendingTransactionItemEntity>
    ): Long {
        val localId = insertTransaction(tx)
        insertItems(items.map { it.copy(pendingTransactionLocalId = localId) })
        return localId
    }

    // ── Read (for sync) ──

    /** All unsynced transactions, oldest first. */
    @Query("SELECT * FROM pending_transactions WHERE synced_at IS NULL ORDER BY created_at ASC")
    suspend fun getUnsyncedTransactions(): List<PendingTransactionEntity>

    /** Items for a single pending transaction. */
    @Query("SELECT * FROM pending_transaction_items WHERE pending_tx_local_id = :localId")
    suspend fun getItemsForTransaction(localId: Long): List<PendingTransactionItemEntity>

    // ── Mark synced ──

    @Query("UPDATE pending_transactions SET synced_at = :syncedAt WHERE localId = :localId")
    suspend fun markSynced(localId: Long, syncedAt: Long = System.currentTimeMillis())

    /** Delete a successfully-synced transaction and its items (CASCADE handles items). */
    @Query("DELETE FROM pending_transactions WHERE localId = :localId")
    suspend fun deleteById(localId: Long)

    // ── UI queries ──

    /** Live count of pending transactions so the UI can show a "syncing…" badge. */
    @Query("SELECT COUNT(*) FROM pending_transactions WHERE synced_at IS NULL")
    fun pendingCount(): Flow<Int>

    // ── Housekeeping ──

    /** Delete synced transactions older than [beforeMillis] to keep the table lean. */
    @Query("DELETE FROM pending_transactions WHERE synced_at IS NOT NULL AND synced_at < :beforeMillis")
    suspend fun deleteOldSynced(beforeMillis: Long)

    // ── Cash-Out entries ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashOut(entry: PendingCashOutEntity): Long

    @Query("SELECT * FROM pending_cash_outs WHERE synced_at IS NULL ORDER BY created_at ASC")
    suspend fun getUnsyncedCashOuts(): List<PendingCashOutEntity>

    @Query("DELETE FROM pending_cash_outs WHERE localId = :localId")
    suspend fun deleteCashOutById(localId: Long)

    @Query("SELECT COUNT(*) FROM pending_cash_outs WHERE synced_at IS NULL")
    fun pendingCashOutCount(): Flow<Int>

    // ── Borrow entries ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrow(entry: PendingBorrowEntity): Long

    @Query("SELECT * FROM pending_borrows WHERE synced_at IS NULL ORDER BY created_at ASC")
    suspend fun getUnsyncedBorrows(): List<PendingBorrowEntity>

    @Query("DELETE FROM pending_borrows WHERE localId = :localId")
    suspend fun deleteBorrowById(localId: Long)

    @Query("SELECT COUNT(*) FROM pending_borrows WHERE synced_at IS NULL")
    fun pendingBorrowCount(): Flow<Int>

    // ── Operational Expense entries ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperationalExpense(entry: PendingOperationalExpenseEntity): Long

    @Query("SELECT * FROM pending_operational_expenses WHERE synced_at IS NULL ORDER BY created_at ASC")
    suspend fun getUnsyncedOperationalExpenses(): List<PendingOperationalExpenseEntity>

    @Query("DELETE FROM pending_operational_expenses WHERE localId = :localId")
    suspend fun deleteOperationalExpenseById(localId: Long)

    @Query("SELECT COUNT(*) FROM pending_operational_expenses WHERE synced_at IS NULL")
    fun pendingOperationalExpenseCount(): Flow<Int>

    // ── Combined pending count (all types) for the UI badge ──

    @Query("""
        SELECT (
            (SELECT COUNT(*) FROM pending_transactions WHERE synced_at IS NULL) +
            (SELECT COUNT(*) FROM pending_cash_outs     WHERE synced_at IS NULL) +
            (SELECT COUNT(*) FROM pending_borrows       WHERE synced_at IS NULL) +
            (SELECT COUNT(*) FROM pending_operational_expenses WHERE synced_at IS NULL) +
            (SELECT COUNT(*) FROM pending_sheet_entries WHERE synced_at IS NULL)
        )
    """)
    fun totalPendingCount(): Flow<Int>

    /** User-visible pending count — excludes sheet entries (secondary sync). */
    @Query("""
        SELECT (
            (SELECT COUNT(*) FROM pending_transactions WHERE synced_at IS NULL) +
            (SELECT COUNT(*) FROM pending_cash_outs     WHERE synced_at IS NULL) +
            (SELECT COUNT(*) FROM pending_borrows       WHERE synced_at IS NULL) +
            (SELECT COUNT(*) FROM pending_operational_expenses WHERE synced_at IS NULL)
        )
    """)
    fun userPendingCount(): Flow<Int>

    // ── Google Sheets Sync ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSheetEntry(entry: PendingSheetEntry): Long

    @Query("SELECT * FROM pending_sheet_entries WHERE synced_at IS NULL ORDER BY created_at ASC")
    suspend fun getUnsyncedSheetEntries(): List<PendingSheetEntry>

    @Query("DELETE FROM pending_sheet_entries WHERE localId = :localId")
    suspend fun deleteSheetEntryById(localId: Long)

    @Transaction
    suspend fun clearAllPending() {
        deleteAllPendingTransactions()
        deleteAllPendingCashOuts()
        deleteAllPendingBorrows()
        deleteAllPendingOperationalExpenses()
        deleteAllPendingSheetEntries()
    }

    @Query("DELETE FROM pending_transactions")
    suspend fun deleteAllPendingTransactions()

    @Query("DELETE FROM pending_cash_outs")
    suspend fun deleteAllPendingCashOuts()

    @Query("DELETE FROM pending_borrows")
    suspend fun deleteAllPendingBorrows()

    @Query("DELETE FROM pending_operational_expenses")
    suspend fun deleteAllPendingOperationalExpenses()

    @Query("DELETE FROM pending_sheet_entries")
    suspend fun deleteAllPendingSheetEntries()
}

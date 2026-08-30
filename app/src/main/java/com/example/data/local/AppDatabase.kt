package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PendingTransactionEntity::class,
        PendingTransactionItemEntity::class,
        PendingCashOutEntity::class,
        PendingBorrowEntity::class,
        PendingOperationalExpenseEntity::class,
        PendingSheetEntry::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingTransactionDao(): PendingTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_offline_queue.db"
                )
                    .fallbackToDestructiveMigration() // safe: only pending (unsynced) data lives here
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

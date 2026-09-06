package com.nothing.expensetracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nothing.expensetracker.data.local.AppDatabase
import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.FriendDao
import com.nothing.expensetracker.data.local.CategoryDao
import com.nothing.expensetracker.data.local.BudgetDao
import com.nothing.expensetracker.data.local.PaymentMethodDao
import com.nothing.expensetracker.data.local.AutopayDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Friends
            db.execSQL("ALTER TABLE friends ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'Synced'")
            db.execSQL("ALTER TABLE friends ADD COLUMN lastSyncAttempt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE friends ADD COLUMN syncError TEXT")

            // Categories
            db.execSQL("ALTER TABLE categories ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'Synced'")
            db.execSQL("ALTER TABLE categories ADD COLUMN lastSyncAttempt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE categories ADD COLUMN syncError TEXT")

            // Budgets
            db.execSQL("ALTER TABLE budgets ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'Synced'")
            db.execSQL("ALTER TABLE budgets ADD COLUMN lastSyncAttempt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE budgets ADD COLUMN syncError TEXT")

            // Expenses (Handling existing columns if needed, but assuming version 10 had the basics)
            // If version 10 already had some sync columns, this might need adjustment.
            // Based on logs, version 10 is the stable morning baseline.
            db.execSQL("ALTER TABLE expenses ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'Synced'")
            db.execSQL("ALTER TABLE expenses ADD COLUMN lastSyncAttempt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE expenses ADD COLUMN syncError TEXT")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS payment_methods (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    isSystem INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS autopay_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    amount REAL NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    category TEXT NOT NULL,
                    type TEXT NOT NULL,
                    paymentMethod TEXT NOT NULL,
                    friendId TEXT,
                    dayOfMonth INTEGER NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    lastRunMonth TEXT,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE budgets ADD COLUMN lastAlertThreshold INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "essential_expense_db"
        )
        .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideExpenseDao(database: AppDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    fun provideFriendDao(database: AppDatabase): FriendDao {
        return database.friendDao()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideBudgetDao(database: AppDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    fun providePaymentMethodDao(database: AppDatabase): PaymentMethodDao {
        return database.paymentMethodDao()
    }

    @Provides
    fun provideAutopayDao(database: AppDatabase): AutopayDao {
        return database.autopayDao()
    }
}

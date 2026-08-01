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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "essential_expense_db"
        )
        .addMigrations(MIGRATION_10_11)
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
}

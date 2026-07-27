package com.nothing.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.nothing.expensetracker.data.local.AppDatabase
import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.FriendDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "essential_expense_db"
        ).fallbackToDestructiveMigration()
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
}

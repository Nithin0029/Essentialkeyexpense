package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutopayDao {
    @Query("SELECT * FROM autopay_rules ORDER BY dayOfMonth ASC")
    fun getAllAutopayRules(): Flow<List<AutopayRule>>

    @Query("SELECT * FROM autopay_rules WHERE id = :id")
    suspend fun getAutopayRuleById(id: Long): AutopayRule?

    @Query("SELECT * FROM autopay_rules WHERE isActive = 1")
    suspend fun getActiveAutopayRules(): List<AutopayRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutopayRule(rule: AutopayRule): Long

    @Update
    suspend fun updateAutopayRule(rule: AutopayRule)

    @Delete
    suspend fun deleteAutopayRule(rule: AutopayRule)

    @Query("UPDATE autopay_rules SET lastRunMonth = :monthKey WHERE id = :id")
    suspend fun markRun(id: Long, monthKey: String)

    /**
     * Rules are stored against a payment method's *name*, exactly as transactions are, so a
     * rename has to rewrite them in the same breath or they keep firing into a method that no
     * longer exists — once a month, quietly, with a notification saying it worked.
     */
    @Query("UPDATE autopay_rules SET paymentMethod = :newName WHERE paymentMethod = :oldName COLLATE NOCASE")
    suspend fun updatePaymentMethodNameInRules(oldName: String, newName: String)

    @Query("SELECT * FROM autopay_rules WHERE paymentMethod = :name COLLATE NOCASE")
    suspend fun autopayRulesUsing(name: String): List<AutopayRule>

    /**
     * A rule's category is a name as well, with the same consequence: rename the category and the
     * rule keeps filing into one that no longer exists, every month, reporting success.
     */
    @Query("UPDATE autopay_rules SET category = :newName WHERE category = :oldName COLLATE NOCASE")
    suspend fun updateCategoryNameInRules(oldName: String, newName: String)

    @Query("SELECT * FROM autopay_rules WHERE category = :name COLLATE NOCASE")
    suspend fun autopayRulesUsingCategory(name: String): List<AutopayRule>

    /** And again for friends: [AutopayRule.friendId] holds a name, not an id. */
    @Query("UPDATE autopay_rules SET friendId = :newName WHERE friendId = :oldName COLLATE NOCASE")
    suspend fun updateFriendNameInRules(oldName: String, newName: String)

    /**
     * Unlinks a deleted friend from any rule, mirroring what `nullifyFriendId` does to their
     * transactions. Left in place, the name conjures the friend back into the Friends list the
     * next time the rule fires - with a balance built from one side of a settled account.
     */
    @Query("UPDATE autopay_rules SET friendId = NULL WHERE friendId = :name COLLATE NOCASE")
    suspend fun clearFriendFromRules(name: String)
}

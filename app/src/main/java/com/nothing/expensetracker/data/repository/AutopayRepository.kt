package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.AutopayDao
import com.nothing.expensetracker.data.local.AutopayRule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutopayRepository @Inject constructor(
    private val autopayDao: AutopayDao
) {
    fun getAllAutopayRules() = autopayDao.getAllAutopayRules()

    suspend fun getAutopayRuleById(id: Long) = autopayDao.getAutopayRuleById(id)

    suspend fun getActiveAutopayRules() = autopayDao.getActiveAutopayRules()

    suspend fun insertAutopayRule(rule: AutopayRule): Long = autopayDao.insertAutopayRule(rule)

    suspend fun updateAutopayRule(rule: AutopayRule) = autopayDao.updateAutopayRule(rule)

    suspend fun deleteAutopayRule(rule: AutopayRule) = autopayDao.deleteAutopayRule(rule)

    suspend fun markRun(id: Long, monthKey: String) = autopayDao.markRun(id, monthKey)
}

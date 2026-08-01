package com.nothing.expensetracker.update.repository

import com.nothing.expensetracker.update.model.VersionInfo
import com.nothing.expensetracker.update.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor() {

    suspend fun getLatestVersion(): Result<VersionInfo> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.instance.getLatestVersion()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch version info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.nothing.expensetracker.update.network

import com.nothing.expensetracker.update.model.VersionInfo
import retrofit2.Response
import retrofit2.http.GET

interface UpdateApi {
    @GET("main/version.json")
    suspend fun getLatestVersion(): Response<VersionInfo>
}

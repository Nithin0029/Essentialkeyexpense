package com.nothing.expensetracker.update.model

import com.google.gson.annotations.SerializedName

data class VersionInfo(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("forceUpdate") val forceUpdate: Boolean,
    @SerializedName("apkUrl") val apkUrl: String,
    @SerializedName("releaseNotes") val releaseNotes: List<String>
)

package com.nothing.expensetracker.update.model

import com.google.gson.annotations.SerializedName

data class VersionInfo(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    // version.json has always written this key as "mandatory", so accept both: "forceUpdate"
    // going forward, "mandatory" so older published files still parse.
    @SerializedName("forceUpdate", alternate = ["mandatory"]) val forceUpdate: Boolean,
    @SerializedName("apkUrl") val apkUrl: String,
    @SerializedName("releaseNotes") val releaseNotes: List<String>
)

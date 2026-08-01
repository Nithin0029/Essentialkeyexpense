package com.nothing.expensetracker.update

import com.nothing.expensetracker.update.download.ApkDownloader
import com.nothing.expensetracker.update.model.VersionInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    private val apkDownloader: ApkDownloader
) {
    fun startDownload(versionInfo: VersionInfo) {
        apkDownloader.downloadApk(versionInfo.apkUrl, versionInfo.versionName)
    }
}

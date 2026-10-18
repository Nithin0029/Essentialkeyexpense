package com.nothing.expensetracker.update.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton

private const val TAG = "ApkDownloader"

@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var downloadId: Long = -1L

    /**
     * What stopped an update from installing, so the UI can say something true rather than
     * leaving the user looking at a screen where nothing happened.
     */
    sealed class UpdateProblem {
        /** Android has not been told this app may install packages. */
        object InstallPermissionMissing : UpdateProblem()
        /** The APK never arrived - no network, or a bad download URL. */
        object DownloadFailed : UpdateProblem()
    }

    /** Set when an update cannot proceed; the UI observes this and explains why. */
    private val _problem = MutableStateFlow<UpdateProblem?>(null)
    val problem: StateFlow<UpdateProblem?> = _problem.asStateFlow()

    fun clearProblem() { _problem.value = null }

    /**
     * Whether Android will let this app install an APK.
     *
     * Required since API 26. Without the check the download still runs and the installer still
     * launches, but the system silently refuses and the user is left with a dismissed prompt and
     * no way to know what to do.
     */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Intent that opens the exact settings page for granting install permission to this app. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun downloadApk(url: String, versionName: String) {
        // Checked before downloading rather than after: there is no point pulling 19MB over
        // mobile data if the install is going to be refused at the end of it.
        if (!canInstallPackages()) {
            _problem.value = UpdateProblem.InstallPermissionMissing
            return
        }

        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "EssentialExpenseTracker-$versionName.apk")
        if (file.exists()) {
            file.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Essential Expense Tracker $versionName")
            .setDescription("New update is being downloaded")
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        val receiverFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        }

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                context.unregisterReceiver(this)

                // ACTION_DOWNLOAD_COMPLETE also fires for failures. Without this check a failed
                // download hands a missing or half-written file to the installer, and the user is
                // told "There was a problem parsing the package" - which reads as a corrupt app
                // rather than a download that never finished.
                if (downloadSucceeded(downloadManager, id)) {
                    installApk(file)
                } else {
                    Log.w(TAG, "Update download did not complete successfully")
                    _problem.value = UpdateProblem.DownloadFailed
                }
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), receiverFlags)
    }

    private fun downloadSucceeded(manager: DownloadManager, id: Long): Boolean {
        return try {
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return false
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                status == DownloadManager.STATUS_SUCCESSFUL
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read download status", e)
            false
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}

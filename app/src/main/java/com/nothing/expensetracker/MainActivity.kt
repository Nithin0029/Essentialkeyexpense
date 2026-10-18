package com.nothing.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.data.local.AppPrefs
import com.nothing.expensetracker.ui.navigation.AppNavigation
import com.nothing.expensetracker.ui.theme.EssentialExpenseTrackerTheme
import com.nothing.expensetracker.update.download.ApkDownloader
import com.nothing.expensetracker.update.ui.UpdateDialog
import com.nothing.expensetracker.update.ui.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appPrefs: AppPrefs

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Autopay confirmations just won't show if this is denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val updateViewModel: UpdateViewModel = hiltViewModel()
            val updateState by updateViewModel.uiState.collectAsState()
            val updateProblem by updateViewModel.updateProblem.collectAsState()
            val themeMode by appPrefs.themeMode.collectAsState()

            LaunchedEffect(Unit) {
                updateViewModel.checkForUpdates()
            }

            EssentialExpenseTrackerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()

                    // An update that cannot proceed says so. Previously the download either
                    // silently refused to install, or handed a failed download to the installer
                    // and let Android report a parse error.
                    updateProblem?.let { problem ->
                        val isPermission =
                            problem is ApkDownloader.UpdateProblem.InstallPermissionMissing
                        AlertDialog(
                            onDismissRequest = { updateViewModel.clearProblem() },
                            title = {
                                Text(
                                    if (isPermission) "Allow app installs" else "Download failed",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            text = {
                                Text(
                                    if (isPermission) {
                                        "Android needs your permission before this app can install " +
                                            "its own updates. Open Settings and turn on \"Allow from " +
                                            "this source\", then tap Update again."
                                    } else {
                                        "The update couldn't be downloaded. Check your connection " +
                                            "and try again - nothing on your device has changed."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                if (isPermission) {
                                    Button(onClick = {
                                        startActivity(updateViewModel.installPermissionIntent())
                                        updateViewModel.clearProblem()
                                    }) { Text("Open Settings") }
                                } else {
                                    Button(onClick = { updateViewModel.clearProblem() }) {
                                        Text("OK")
                                    }
                                }
                            },
                            dismissButton = {
                                if (isPermission) {
                                    TextButton(onClick = { updateViewModel.clearProblem() }) {
                                        Text("Not now")
                                    }
                                }
                            }
                        )
                    }

                    if (updateState.showDialog && updateState.versionInfo != null) {
                        UpdateDialog(
                            versionInfo = updateState.versionInfo!!,
                            onUpdateClick = {
                                updateViewModel.startUpdate(updateState.versionInfo!!)
                                if (!updateState.versionInfo!!.forceUpdate) {
                                    updateViewModel.dismissDialog()
                                }
                            },
                            onDismissClick = {
                                updateViewModel.dismissDialog()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

package com.nothing.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.ui.navigation.AppNavigation
import com.nothing.expensetracker.ui.theme.EssentialExpenseTrackerTheme
import com.nothing.expensetracker.update.ui.UpdateDialog
import com.nothing.expensetracker.update.ui.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val updateViewModel: UpdateViewModel = hiltViewModel()
            val updateState by updateViewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                updateViewModel.checkForUpdates()
            }

            EssentialExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    AppNavigation()

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
}

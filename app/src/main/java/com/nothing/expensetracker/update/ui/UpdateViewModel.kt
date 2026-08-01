package com.nothing.expensetracker.update.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.update.UpdateManager
import com.nothing.expensetracker.update.model.VersionInfo
import com.nothing.expensetracker.update.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val versionInfo: VersionInfo? = null,
    val showDialog: Boolean = false,
    val isChecking: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates() {
        if (_uiState.value.isChecking) return

        _uiState.value = _uiState.value.copy(isChecking = true, error = null)

        viewModelScope.launch {
            repository.getLatestVersion()
                .onSuccess { info ->
                    val currentVersionCode = com.nothing.expensetracker.BuildConfig.VERSION_CODE
                    if (info.versionCode > currentVersionCode) {
                        _uiState.value = _uiState.value.copy(
                            versionInfo = info,
                            showDialog = true,
                            isChecking = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isChecking = false)
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isChecking = false,
                        error = error.message
                    )
                }
        }
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(showDialog = false)
    }

    fun startUpdate(versionInfo: VersionInfo) {
        updateManager.startDownload(versionInfo)
    }
}

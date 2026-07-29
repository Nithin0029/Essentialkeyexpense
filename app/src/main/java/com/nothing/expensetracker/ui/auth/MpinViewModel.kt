package com.nothing.expensetracker.ui.auth

import androidx.lifecycle.ViewModel
import com.nothing.expensetracker.auth.MpinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MpinViewModel @Inject constructor(
    private val mpinManager: MpinManager
) : ViewModel() {

    private val _mpin = MutableStateFlow("")
    val mpin: StateFlow<String> = _mpin.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onNumberClick(number: String) {
        if (_mpin.value.length < 4) {
            _mpin.value += number
            _error.value = null
        }
    }

    fun onDeleteClick() {
        if (_mpin.value.isNotEmpty()) {
            _mpin.value = _mpin.value.dropLast(1)
            _error.value = null
        }
    }

    fun clearMpin() {
        _mpin.value = ""
        _error.value = null
    }

    fun isMpinSet() = mpinManager.isMpinSet()

    fun saveMpin(mpin: String) {
        mpinManager.setMpin(mpin)
        _mpin.value = ""
        _error.value = null
    }

    fun removeMpin() {
        mpinManager.removeMpin()
        _mpin.value = ""
        _error.value = null
    }

    fun verifyMpin(input: String): Boolean {
        val isValid = mpinManager.verifyMpin(input)
        if (!isValid) {
            _error.value = "Incorrect MPIN"
            _mpin.value = ""
        }
        return isValid
    }
}

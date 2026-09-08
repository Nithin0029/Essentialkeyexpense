package com.nothing.expensetracker.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.auth.BiometricAuthHelper

@Composable
fun CreateMpinScreen(
    onMpinCreated: (String) -> Unit,
    viewModel: MpinViewModel = hiltViewModel()
) {
    val mpin by viewModel.mpin.collectAsState()
    
    LaunchedEffect(mpin) {
        if (mpin.length == 4) {
            onMpinCreated(mpin)
            viewModel.clearMpin()
        }
    }

    MpinEntryLayout(
        title = "Create MPIN",
        subtitle = "Enter a 4-digit code to secure your app",
        mpin = mpin,
        onNumberClick = viewModel::onNumberClick,
        onDeleteClick = viewModel::onDeleteClick
    )
}

@Composable
fun ConfirmMpinScreen(
    originalMpin: String,
    onMpinConfirmed: () -> Unit,
    onMismatch: () -> Unit,
    viewModel: MpinViewModel = hiltViewModel()
) {
    val mpin by viewModel.mpin.collectAsState()
    val error by viewModel.error.collectAsState()
    
    LaunchedEffect(mpin) {
        if (mpin.length == 4) {
            if (mpin == originalMpin) {
                viewModel.saveMpin(mpin)
                onMpinConfirmed()
            } else {
                kotlinx.coroutines.delay(300) // Small delay to show the last dot
                onMismatch()
                viewModel.clearMpin()
            }
        }
    }

    MpinEntryLayout(
        title = "Confirm MPIN",
        subtitle = "Enter the code again to confirm",
        mpin = mpin,
        error = error,
        onNumberClick = viewModel::onNumberClick,
        onDeleteClick = viewModel::onDeleteClick
    )
}

@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    viewModel: MpinViewModel = hiltViewModel()
) {
    val mpin by viewModel.mpin.collectAsState()
    val error by viewModel.error.collectAsState()
    val activity = LocalContext.current as? FragmentActivity
    val biometricAvailable = activity != null && BiometricAuthHelper.isAvailable(activity)
    val biometricEnabled = viewModel.isBiometricEnabled() && biometricAvailable

    fun promptBiometric() {
        if (activity != null && biometricAvailable) {
            BiometricAuthHelper.authenticate(activity, onSuccess = onUnlocked)
        }
    }

    LaunchedEffect(Unit) {
        if (biometricEnabled) {
            promptBiometric()
        }
    }

    LaunchedEffect(mpin) {
        if (mpin.length == 4) {
            kotlinx.coroutines.delay(200) // Small delay to show the last dot
            if (viewModel.verifyMpin(mpin)) {
                onUnlocked()
            }
        }
    }

    MpinEntryLayout(
        title = "Unlock App",
        subtitle = "Enter your 4-digit code",
        mpin = mpin,
        error = error,
        onNumberClick = viewModel::onNumberClick,
        onDeleteClick = viewModel::onDeleteClick,
        showBiometricButton = biometricEnabled,
        onBiometricClick = { promptBiometric() }
    )
}

@Composable
fun ChangeMpinScreen(
    onNavigateBack: () -> Unit,
    viewModel: MpinViewModel = hiltViewModel()
) {
    var step by remember { mutableIntStateOf(1) } // 1: Verify Current, 2: New, 3: Confirm
    var currentMpin by remember { mutableStateOf("") }
    var newMpin by remember { mutableStateOf("") }
    
    val mpin by viewModel.mpin.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(mpin) {
        if (mpin.length == 4) {
            when (step) {
                1 -> {
                    kotlinx.coroutines.delay(200)
                    if (viewModel.verifyMpin(mpin)) {
                        currentMpin = mpin
                        step = 2
                        viewModel.clearMpin()
                    }
                }
                2 -> {
                    newMpin = mpin
                    step = 3
                    viewModel.clearMpin()
                }
                3 -> {
                    if (mpin == newMpin) {
                        viewModel.saveMpin(mpin)
                        onNavigateBack()
                    } else {
                        kotlinx.coroutines.delay(300)
                        viewModel.clearMpin()
                    }
                }
            }
        }
    }

    val title = when (step) {
        1 -> "Current MPIN"
        2 -> "New MPIN"
        else -> "Confirm New MPIN"
    }
    
    val subtitle = when (step) {
        1 -> "Enter your current 4-digit code"
        2 -> "Enter a new 4-digit code"
        else -> "Confirm your new 4-digit code"
    }

    val displayError = if (step == 3 && mpin.length == 4 && mpin != newMpin) {
        "MPIN Mismatch"
    } else {
        error
    }

    MpinEntryLayout(
        title = title,
        subtitle = subtitle,
        mpin = mpin,
        error = displayError,
        onNumberClick = viewModel::onNumberClick,
        onDeleteClick = viewModel::onDeleteClick
    )
}

@Composable
fun MpinEntryLayout(
    title: String,
    subtitle: String,
    mpin: String,
    error: String? = null,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    showBiometricButton: Boolean = false,
    onBiometricClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // MPIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                val isFilled = index < mpin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) MaterialTheme.colorScheme.primary else Color.DarkGray)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        } else {
            Spacer(modifier = Modifier.height(16.dp)) // Maintain space
        }

        if (showBiometricButton) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .clickable(onClick = onBiometricClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Unlock with biometrics",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Keypad
        val numbers = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )
        
        numbers.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    KeypadButton(
                        text = key,
                        onClick = {
                            when (key) {
                                "DEL" -> onDeleteClick()
                                "" -> {}
                                else -> onNumberClick(key)
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = text.isNotEmpty(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text == "DEL") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onBackground
            )
        } else if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

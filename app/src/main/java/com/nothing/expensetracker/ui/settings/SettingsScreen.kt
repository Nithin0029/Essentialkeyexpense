package com.nothing.expensetracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nothing.expensetracker.auth.AuthState
import java.util.*

enum class MpinVerifyReason {
    EDIT_OPENING_BANK_BALANCE,
    EDIT_OPENING_CASH_BALANCE,
    DISABLE_MPIN
}

@Composable
fun SettingsScreen(
    onNavigateToCreateMpin: () -> Unit,
    onNavigateToChangeMpin: () -> Unit,
    onNavigateToCategoryManagement: () -> Unit,
    onNavigateToOverallBudget: () -> Unit,
    onNavigateToCategoryBudgets: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    mpinViewModel: com.nothing.expensetracker.ui.auth.MpinViewModel = hiltViewModel()
) {
    val openingBankBalance by viewModel.openingBankBalance.collectAsState()
    val openingCashBalance by viewModel.openingCashBalance.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val spreadsheetState by viewModel.spreadsheetState.collectAsState()
    
    var showOpeningBankBalanceDialog by remember { mutableStateOf(false) }
    var showOpeningCashBalanceDialog by remember { mutableStateOf(false) }
    var showMpinVerifyDialog by remember { mutableStateOf(false) }
    var mpinVerifyReason by remember { mutableStateOf<MpinVerifyReason?>(null) }
    
    val mpinEnabled = mpinViewModel.isMpinSet()
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        @Suppress("DEPRECATION")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        viewModel.handleSignInResult(task)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(16.dp),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            FinancialSettingsCard(
                openingBankBalance = openingBankBalance,
                openingCashBalance = openingCashBalance,
                onEditBank = { 
                    if (mpinEnabled) {
                        mpinVerifyReason = MpinVerifyReason.EDIT_OPENING_BANK_BALANCE
                        showMpinVerifyDialog = true
                    } else {
                        showOpeningBankBalanceDialog = true
                    }
                },
                onEditCash = {
                    if (mpinEnabled) {
                        mpinVerifyReason = MpinVerifyReason.EDIT_OPENING_CASH_BALANCE
                        showMpinVerifyDialog = true
                    } else {
                        showOpeningCashBalanceDialog = true
                    }
                }
            )

            SecuritySettingsCard(
                mpinEnabled = mpinEnabled,
                onEnableMpin = onNavigateToCreateMpin,
                onChangeMpin = onNavigateToChangeMpin,
                onDisableMpin = {
                    mpinVerifyReason = MpinVerifyReason.DISABLE_MPIN
                    showMpinVerifyDialog = true
                }
            )

            GoogleSyncSettingsCard(
                authState = authState,
                spreadsheetState = spreadsheetState,
                isSyncing = viewModel.isSyncing.collectAsState().value,
                unsyncedCount = viewModel.unsyncedCount.collectAsState().value,
                syncedCount = viewModel.syncedCount.collectAsState().value,
                failedCount = viewModel.failedCount.collectAsState().value,
                lastSyncTime = viewModel.lastSyncTime.collectAsState().value,
                onConnect = {
                    googleSignInLauncher.launch(viewModel.getSignInIntent())
                },
                onDisconnect = {
                    viewModel.signOut()
                },
                onSyncNow = {
                    viewModel.syncNow()
                },
                onOpenSpreadsheet = { id ->
                    if (id.isBlank()) {
                        Toast.makeText(context, "No spreadsheet connected.", Toast.LENGTH_SHORT).show()
                    } else {
                        val url = "https://docs.google.com/spreadsheets/d/$id"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open spreadsheet.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            CategoryManagementCard(
                onClick = onNavigateToCategoryManagement
            )

            BudgetManagementSection(
                onOverallBudgetClick = onNavigateToOverallBudget,
                onCategoryBudgetsClick = onNavigateToCategoryBudgets
            )

            AboutCard()
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showOpeningBankBalanceDialog) {
        OpeningBalanceDialog(
            title = "Opening Bank Balance",
            initialBalance = openingBankBalance,
            onDismiss = { showOpeningBankBalanceDialog = false },
            onSave = {
                viewModel.updateOpeningBankBalance(it)
                showOpeningBankBalanceDialog = false
            }
        )
    }

    if (showOpeningCashBalanceDialog) {
        OpeningBalanceDialog(
            title = "Opening Cash Balance",
            initialBalance = openingCashBalance,
            onDismiss = { showOpeningCashBalanceDialog = false },
            onSave = {
                viewModel.updateOpeningCashBalance(it)
                showOpeningCashBalanceDialog = false
            }
        )
    }

    if (showMpinVerifyDialog) {
        MpinVerifyDialog(
            onDismiss = {
                showMpinVerifyDialog = false
                mpinVerifyReason = null
            },
            onSuccess = {
                showMpinVerifyDialog = false
                when (mpinVerifyReason) {
                    MpinVerifyReason.EDIT_OPENING_BANK_BALANCE -> showOpeningBankBalanceDialog = true
                    MpinVerifyReason.EDIT_OPENING_CASH_BALANCE -> showOpeningCashBalanceDialog = true
                    MpinVerifyReason.DISABLE_MPIN -> mpinViewModel.removeMpin() 
                    null -> {}
                }
                mpinVerifyReason = null
            }
        )
    }
}

@Composable
fun SecuritySettingsCard(
    mpinEnabled: Boolean,
    onEnableMpin: () -> Unit,
    onChangeMpin: () -> Unit,
    onDisableMpin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Security",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                label = "App Lock (MPIN)",
                value = if (mpinEnabled) "Enabled" else "Disabled",
                action = {
                    if (!mpinEnabled) {
                        Button(onClick = onEnableMpin, shape = RoundedCornerShape(8.dp)) {
                            Text("Enable")
                        }
                    } else {
                        IconButton(onClick = onDisableMpin) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Disable", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )

            if (mpinEnabled) {
                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Change MPIN", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    IconButton(onClick = onChangeMpin) {
                        Icon(Icons.Default.Edit, contentDescription = "Change", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MpinVerifyDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: com.nothing.expensetracker.ui.auth.MpinViewModel = hiltViewModel()
) {
    val mpin by viewModel.mpin.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(mpin) {
        if (mpin.length == 4) {
            if (viewModel.verifyMpin(mpin)) {
                onSuccess()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify MPIN", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Enter your 4-digit code to continue", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (index < mpin.length) MaterialTheme.colorScheme.primary else Color.DarkGray)
                        )
                    }
                }
                if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Simplified keypad for dialog
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "DEL")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                TextButton(
                                    onClick = {
                                        when (key) {
                                            "DEL" -> viewModel.onDeleteClick()
                                            "C" -> viewModel.clearMpin()
                                            else -> viewModel.onNumberClick(key)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape
                                ) {
                                    Text(text = key, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun GoogleSyncSettingsCard(
    authState: AuthState,
    spreadsheetState: SpreadsheetState,
    isSyncing: Boolean,
    unsyncedCount: Int,
    syncedCount: Int,
    failedCount: Int,
    lastSyncTime: Long?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSpreadsheet: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Google Sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            when (authState) {
                is AuthState.NotConnected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Status", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            Text(text = "Not Connected", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        }
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.Gray)
                    }

                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Connect Google Account")
                    }
                }

                is AuthState.Connected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (authState.photoUrl != null) {
                            AsyncImage(
                                model = authState.photoUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.DarkGray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = authState.displayName?.take(1) ?: "?",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authState.displayName ?: "Google User",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = authState.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF4CAF50))
                    }

                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                    SettingRow(
                        label = "Account Status",
                        value = "Connected",
                        action = {
                            IconButton(onClick = onDisconnect) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect", tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                            }
                        }
                    )

                    when (spreadsheetState) {
                        is SpreadsheetState.Initializing -> {
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Initializing Cloud Database...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                        }
                        is SpreadsheetState.Connected -> {
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            SettingRow(label = "Cloud Database", value = "Connected")
                            SettingRow(label = "Database Name", value = spreadsheetState.name)

                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                            // Open Spreadsheet Item
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenSpreadsheet(spreadsheetState.id) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = "Open Spreadsheet", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                        Text(text = "Open your synced Google Sheets spreadsheet", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            
                            SyncStatRow(label = "Synced", value = syncedCount.toString(), color = Color(0xFF4CAF50))
                            SyncStatRow(label = "Pending", value = unsyncedCount.toString(), color = Color(0xFFFFC107))
                            SyncStatRow(label = "Failed", value = failedCount.toString(), color = Color(0xFFF44336))
                            
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                            val syncStatus = when {
                                isSyncing -> "Syncing"
                                failedCount > 0 -> "Failed"
                                unsyncedCount > 0 -> "Pending"
                                else -> "Synced"
                            }
                            SettingRow(label = "Sync Status", value = syncStatus)

                            val syncTimeStr = lastSyncTime?.let {
                                java.text.SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(it))
                            } ?: "Never"
                            SettingRow(label = "Last Sync", value = syncTimeStr)

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onSyncNow,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = (unsyncedCount > 0 || failedCount > 0) && !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing...")
                                } else {
                                    Text("Sync Now")
                                }
                            }
                        }
                        is SpreadsheetState.Error -> {
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            Text(text = spreadsheetState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = onConnect, shape = RoundedCornerShape(8.dp)) {
                                Text("Retry")
                            }
                        }
                        else -> {}
                    }
                }

                is AuthState.Error -> {
                    Text(text = authState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retry Connection")
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialSettingsCard(
    openingBankBalance: Double,
    openingCashBalance: Double,
    onEditBank: () -> Unit,
    onEditCash: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Financial",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            SettingRow(
                label = "Opening Bank Balance",
                value = "₹%,.0f".format(Locale.getDefault(), openingBankBalance),
                action = {
                    IconButton(onClick = onEditBank) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            )

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

            SettingRow(
                label = "Opening Cash Balance",
                value = "₹%,.0f".format(Locale.getDefault(), openingCashBalance),
                action = {
                    IconButton(onClick = onEditCash) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            )

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

            SettingRow(
                label = "Currency",
                value = "INR (₹)",
                action = null
            )

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

            SettingRow(
                label = "Date Format",
                value = "DD/MM/YYYY",
                action = null
            )
        }
    }
}

@Composable
fun SyncStatRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
        }
        action?.invoke()
    }
}

@Composable
fun OpeningBalanceDialog(
    title: String,
    initialBalance: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var balanceText by remember { mutableStateOf(initialBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            OutlinedTextField(
                value = balanceText,
                onValueChange = { if (it.isEmpty() || (it.toDoubleOrNull() != null)) balanceText = it },
                label = { Text("Amount") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val balance = balanceText.toDoubleOrNull() ?: 0.0
                    if (balance >= 0) {
                        onSave(balance)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Essential Expense Tracker",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = "Version 2.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Built with Nothing OS design language. Secure, private, and fully synchronized with your Google Account.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
        }
    }
}

@Composable
fun BudgetManagementSection(
    onOverallBudgetClick: () -> Unit,
    onCategoryBudgetsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Budget",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOverallBudgetClick),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Overall Budget",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Manage your monthly spending limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCategoryBudgetsClick),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Category Budgets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Set limits for specific categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryManagementCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Category Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Add, edit, or remove expense categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

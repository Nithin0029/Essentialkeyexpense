package com.nothing.expensetracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.CloudDownload
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.nothing.expensetracker.ui.onboarding.WelcomeDialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nothing.expensetracker.auth.AuthState
import com.nothing.expensetracker.auth.BiometricAuthHelper
import com.nothing.expensetracker.data.local.ThemeMode
import com.nothing.expensetracker.data.repository.AccountBalance
import com.nothing.expensetracker.util.formatCurrency
import java.util.*

enum class MpinVerifyReason {
    EDIT_ACCOUNT_BALANCE,
    RENAME_ACCOUNT,
    ADD_ACCOUNT,
    DELETE_ACCOUNT,
    DISABLE_MPIN
}

@Composable
fun SettingsScreen(
    onNavigateToCreateMpin: () -> Unit,
    onNavigateToChangeMpin: () -> Unit,
    onNavigateToCategoryManagement: () -> Unit,
    onNavigateToPaymentMethodManagement: () -> Unit,
    onNavigateToAutopay: () -> Unit,
    onNavigateToOverallBudget: () -> Unit,
    onNavigateToCategoryBudgets: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    mpinViewModel: com.nothing.expensetracker.ui.auth.MpinViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val spreadsheetState by viewModel.spreadsheetState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()

    // Which account each dialog is about. Null means it is closed.
    var accountToEdit by remember { mutableStateOf<AccountBalance?>(null) }
    var accountToRename by remember { mutableStateOf<AccountBalance?>(null) }
    var accountToDelete by remember { mutableStateOf<AccountBalance?>(null) }
    var showAddAccount by remember { mutableStateOf(false) }
    // Held while the MPIN is being entered, since the dialog it belongs to opens afterwards.
    var pendingAccount by remember { mutableStateOf<AccountBalance?>(null) }
    var showMpinVerifyDialog by remember { mutableStateOf(false) }
    var mpinVerifyReason by remember { mutableStateOf<MpinVerifyReason?>(null) }

    val mpinEnabled = mpinViewModel.isMpinSet()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = activity != null && BiometricAuthHelper.isAvailable(activity)
    var biometricEnabled by remember { mutableStateOf(mpinViewModel.isBiometricEnabled()) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        @Suppress("DEPRECATION")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        viewModel.handleSignInResult(task)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
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
            AppearanceSettingsCard(
                themeMode = themeMode,
                onThemeModeChange = { viewModel.setThemeMode(it) }
            )

            FinancialSettingsCard(
                accounts = accounts,
                onEditAccount = { account ->
                    if (mpinEnabled) {
                        pendingAccount = account
                        mpinVerifyReason = MpinVerifyReason.EDIT_ACCOUNT_BALANCE
                        showMpinVerifyDialog = true
                    } else {
                        accountToEdit = account
                    }
                },
                onRenameAccount = { account ->
                    if (mpinEnabled) {
                        pendingAccount = account
                        mpinVerifyReason = MpinVerifyReason.RENAME_ACCOUNT
                        showMpinVerifyDialog = true
                    } else {
                        accountToRename = account
                    }
                },
                onAddAccount = {
                    if (mpinEnabled) {
                        mpinVerifyReason = MpinVerifyReason.ADD_ACCOUNT
                        showMpinVerifyDialog = true
                    } else {
                        showAddAccount = true
                    }
                },
                onDeleteAccount = { account ->
                    if (mpinEnabled) {
                        pendingAccount = account
                        mpinVerifyReason = MpinVerifyReason.DELETE_ACCOUNT
                        showMpinVerifyDialog = true
                    } else {
                        accountToDelete = account
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
                },
                biometricAvailable = biometricAvailable,
                biometricEnabled = biometricEnabled,
                onBiometricToggle = { enabled ->
                    mpinViewModel.setBiometricEnabled(enabled)
                    biometricEnabled = enabled
                }
            )

            GoogleSyncSettingsCard(
                authState = authState,
                spreadsheetState = spreadsheetState,
                restoreState = restoreState,
                onRestoreNow = { viewModel.restoreFromSheet() },
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

            PaymentMethodManagementCard(
                onClick = onNavigateToPaymentMethodManagement
            )

            AutopayManagementCard(
                onClick = onNavigateToAutopay
            )

            BudgetManagementSection(
                onOverallBudgetClick = onNavigateToOverallBudget,
                onCategoryBudgetsClick = onNavigateToCategoryBudgets
            )

            AboutCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Only surfaces when a restore actually brought something down — a no-op restore (the
    // normal case once a device is in sync) stays silent.
    (restoreState as? RestoreState.Done)?.summary?.takeIf { !it.isEmpty }?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.acknowledgeRestore() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Synced with Spreadsheet", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    if (summary.added > 0) {
                        Text(
                            "Pulled down data this device was missing:",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RestoreCountRows(
                            listOf(
                                "Transactions" to summary.transactions,
                                "Categories" to summary.categories,
                                "Friends" to summary.friends,
                                "Budgets" to summary.budgets,
                                "Payment methods" to summary.paymentMethods,
                                "Autopay rules" to summary.autopayRules
                            )
                        )
                    }
                    if (summary.transactions > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your transactions are back, but this device doesn't know what your " +
                                "accounts hold today. Set your current balance so the dashboard " +
                                "adds up.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (summary.removed > 0) {
                        if (summary.added > 0) Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Removed locally, because these were deleted from the spreadsheet:",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RestoreCountRows(
                            listOf(
                                "Transactions" to summary.removedTransactions,
                                "Categories" to summary.removedCategories,
                                "Friends" to summary.removedFriends,
                                "Budgets" to summary.removedBudgets
                            )
                        )
                    }
                }
            },
            confirmButton = {
                if (summary.transactions > 0) {
                    // Opening balances live in app storage, so a reinstall loses them while the
                    // transactions come back from the sheet. Without this the dashboard total is
                    // wrong by exactly the old opening balance, with nothing on screen saying so.
                    Button(onClick = {
                        viewModel.acknowledgeRestore()
                        // Opens the first account; the rest are a row each in Settings below.
                        accountToEdit = accounts.firstOrNull()
                    }) { Text("Set Balance") }
                } else {
                    Button(onClick = { viewModel.acknowledgeRestore() }) { Text("Done") }
                }
            },
            dismissButton = {
                if (summary.transactions > 0) {
                    TextButton(onClick = { viewModel.acknowledgeRestore() }) {
                        Text("Later", color = Color.Gray)
                    }
                }
            }
        )
    }

    (restoreState as? RestoreState.Failed)?.let { failure ->
        AlertDialog(
            onDismissRequest = { viewModel.acknowledgeRestore() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Restore Failed", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text(
                        "Couldn't read your spreadsheet, so nothing was changed on this device.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        failure.message,
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.acknowledgeRestore()
                    viewModel.restoreFromSheet()
                }) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.acknowledgeRestore() }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    if (accountToEdit != null) {
        val target = accountToEdit!!
        OpeningBalanceDialog(
            title = "${target.name} Today",
            explanation = "Enter what ${target.name} holds right now. Past transactions stay " +
                "as they are - the opening balance is adjusted to match.",
            initialBalance = target.balance,
            onDismiss = { accountToEdit = null },
            onSave = {
                viewModel.setAccountBalanceById(target.id, it)
                accountToEdit = null
            }
        )
    }

    if (accountToRename != null) {
        val target = accountToRename!!
        RenameAccountDialog(
            currentName = target.name,
            onDismiss = { accountToRename = null },
            onSave = { newName ->
                viewModel.renameAccount(target, newName) { ok, message ->
                    if (!ok && message != null) {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
                accountToRename = null
            }
        )
    }

    if (accountToDelete != null) {
        val target = accountToDelete!!
        DeleteAccountDialog(
            account = target,
            onDismiss = { accountToDelete = null },
            onConfirm = {
                viewModel.deleteAccount(target) { ok, message ->
                    val text = if (ok) "${target.name} deleted" else message
                    if (text != null) {
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    }
                }
                accountToDelete = null
            }
        )
    }

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { name, balance ->
                viewModel.addAccount(name, balance) { ok, message ->
                    if (!ok && message != null) {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
                showAddAccount = false
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
                    MpinVerifyReason.EDIT_ACCOUNT_BALANCE -> {
                        accountToEdit = pendingAccount
                        pendingAccount = null
                    }
                    MpinVerifyReason.RENAME_ACCOUNT -> {
                        accountToRename = pendingAccount
                        pendingAccount = null
                    }
                    MpinVerifyReason.ADD_ACCOUNT -> showAddAccount = true
                    MpinVerifyReason.DELETE_ACCOUNT -> {
                        accountToDelete = pendingAccount
                        pendingAccount = null
                    }
                    MpinVerifyReason.DISABLE_MPIN -> {
                        mpinViewModel.removeMpin()
                        biometricEnabled = false
                    }
                    null -> {}
                }
                mpinVerifyReason = null
            }
        )
    }
}

@Composable
fun AppearanceSettingsCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = mode == themeMode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsCard(
    mpinEnabled: Boolean,
    onEnableMpin: () -> Unit,
    onChangeMpin: () -> Unit,
    onDisableMpin: () -> Unit,
    biometricAvailable: Boolean = false,
    biometricEnabled: Boolean = false,
    onBiometricToggle: (Boolean) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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
                    Text(text = "Change MPIN", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onChangeMpin) {
                        Icon(Icons.Default.Edit, contentDescription = "Change", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }

                if (biometricAvailable) {
                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Biometric Unlock", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = onBiometricToggle,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
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
        title = { Text("Verify MPIN", color = MaterialTheme.colorScheme.onSurface) },
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
                                    Text(text = key, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

/** Label/count rows for the restore summary dialog, skipping anything that was zero. */
@Composable
private fun RestoreCountRows(entries: List<Pair<String, Int>>) {
    entries.filter { it.second > 0 }.forEach { (label, count) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            Text(
                count.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun GoogleSyncSettingsCard(
    authState: AuthState,
    spreadsheetState: SpreadsheetState,
    restoreState: RestoreState,
    onRestoreNow: () -> Unit,
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
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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
                            Text(text = "Not Connected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authState.displayName ?: "Google User",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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

                            // Pull-down from the spreadsheet. Runs automatically on connect; this
                            // row exposes it manually for when the sheet was edited elsewhere.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = restoreState !is RestoreState.Restoring,
                                        onClick = onRestoreNow
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Restore from Spreadsheet",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Pull down anything this device is missing",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                if (restoreState is RestoreState.Restoring) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

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
                                        Text(text = "Open Spreadsheet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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

                            // takeIf { it > 0 }: the value is 0 rather than null before the first
                            // sync, which formatted as "01 Jan 1970" instead of "Never".
                            val syncTimeStr = lastSyncTime?.takeIf { it > 0L }?.let {
                                java.text.SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(it))
                            } ?: "Never"
                            SettingRow(label = "Last Sync", value = syncTimeStr)

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onSyncNow,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                // Not gated on unsyncedCount: payment methods, autopay rules and
                                // opening balances have no syncStatus column, so they never appear
                                // in that count. Gating on it meant a cycle could not be triggered
                                // at all once transactions were settled, leaving those three
                                // permanently unpushed.
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
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
    accounts: List<AccountBalance>,
    onEditAccount: (AccountBalance) -> Unit,
    onRenameAccount: (AccountBalance) -> Unit,
    onAddAccount: () -> Unit,
    onDeleteAccount: (AccountBalance) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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

            // One row per account, not a fixed Bank and Cash pair: with a second bank on file
            // the old pair could only ever describe two of them, and named them in SQL besides,
            // which is what stopped accounts being renamed at all.
            //
            // The editable figure is today's balance, not the opening one: nobody knows what
            // their account held before their first ever transaction, and after a restore the
            // opening value is gone anyway. The opening balance shown underneath is derived.
            accounts.forEach { account ->
                // Keyed by id rather than by position, so an open menu cannot end up attached
                // to a different account when the list reorders after a rename.
                var menuOpen by remember(account.id) { mutableStateOf(false) }

                SettingRow(
                    label = "${account.name} Today",
                    value = formatCurrency(account.balance),
                    subtitle = "Opening: ${formatCurrency(account.openingBalance)}",
                    action = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options for ${account.name}",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Set balance today") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onEditAccount(account)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onRenameAccount(account)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color(0xFFD71921)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = Color(0xFFD71921),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onDeleteAccount(account)
                                    }
                                )
                            }
                        }
                    }
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
            }

            // Adding an account here as well as on the Payment Methods screen: a second bank is
            // something you think of while looking at the first one's balance.
            TextButton(
                onClick = onAddAccount,
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add account")
            }

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
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        action?.invoke()
    }
}

@Composable
fun OpeningBalanceDialog(
    title: String,
    initialBalance: Double,
    explanation: String? = null,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var balanceText by remember { mutableStateOf(initialBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                if (explanation != null) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { if (it.isEmpty() || (it.toDoubleOrNull() != null)) balanceText = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                // Read from BuildConfig rather than hardcoded: this said 3.5.0 on a 3.6.0 build,
                // because the literal was never updated alongside the version bump.
                text = "Version ${com.nothing.expensetracker.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Built with Nothing OS design language. Secure, private, and fully synchronized with your Google Account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // The first-run explanation is one tap from being gone forever, and it is the only
            // place that says where the data actually goes.
            var showWelcome by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showWelcome = true },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("How this works", color = MaterialTheme.colorScheme.primary)
            }
            if (showWelcome) {
                WelcomeDialog(onDismiss = { showWelcome = false })
            }
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
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
                            color = MaterialTheme.colorScheme.onSurface
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
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
                            color = MaterialTheme.colorScheme.onSurface
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
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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
                        color = MaterialTheme.colorScheme.onSurface
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

@Composable
fun PaymentMethodManagementCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Payment Methods",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Add, edit, or remove payment methods",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun AutopayManagementCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Autopay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Set up recurring monthly transactions like rent or SIPs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}


/**
 * Renames an account from the card its balance sits on.
 *
 * The name is not a label over the top of the data - every transaction is recorded against it,
 * and the rename rewrites all of them, so the explanation says so plainly. Someone who thinks a
 * rename might move their money will never use it.
 */
@Composable
private fun RenameAccountDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename account", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    text = "Call it what your bank calls it \u2014 SBI, HDFC Bank. Every transaction " +
                        "recorded against \"" + currentName + "\" moves with the name, so no " +
                        "balance changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank() && text.trim() != currentName
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

/**
 * Adds an account, and what it holds, in one step.
 *
 * Only accounts are offered here, not cards or UPI handles: this card is a list of balances, and
 * a rail that spends from an account holds no balance of its own. Those are still added on the
 * Payment Methods screen, where the funding account can be chosen.
 */
@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }

    fun isAcceptable(value: String) = value.isEmpty() || value.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    text = "An account holds money of its own \u2014 a second bank account, a wallet. " +
                        "A card or UPI handle that spends from one of these is a payment method, " +
                        "added under Payment Methods.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    placeholder = { Text("SBI Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { if (isAcceptable(it)) balanceText = it },
                    label = { Text("Balance today (optional)") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, balanceText.toDoubleOrNull() ?: 0.0) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}


/**
 * Confirms deleting an account.
 *
 * States the balance that stops counting, because the headline figure on the dashboard changes
 * the moment this goes through and a total that moves unexplained is worse than one that drops.
 * What the account still carries is checked afterwards, and reported by name.
 */
@Composable
private fun DeleteAccountDialog(
    account: AccountBalance,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${account.name}?", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                text = "Its ${formatCurrency(account.balance)} stops counting towards your total " +
                    "assets. If it still carries transactions, or a card or UPI handle draws on " +
                    "it, nothing is deleted and you will be told what is in the way.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD71921),
                    contentColor = Color.White
                )
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

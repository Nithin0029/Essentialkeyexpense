package com.nothing.expensetracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nothing.expensetracker.auth.AuthState
import java.util.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val openingBalance by viewModel.openingBalance.collectAsState()
    val authState by viewModel.authState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

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
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FinancialSettingsCard(
                openingBalance = openingBalance,
                onEditOpeningBalance = { showDialog = true }
            )

            GoogleSyncSettingsCard(
                authState = authState,
                onConnect = {
                    googleSignInLauncher.launch(viewModel.getSignInIntent())
                },
                onDisconnect = {
                    viewModel.signOut()
                }
            )
        }
    }

    if (showDialog) {
        OpeningBalanceDialog(
            initialBalance = openingBalance,
            onDismiss = { showDialog = false },
            onSave = {
                viewModel.updateOpeningBalance(it)
                showDialog = false
            }
        )
    }
}

@Composable
fun GoogleSyncSettingsCard(
    authState: AuthState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
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
                        label = "Status",
                        value = "Connected",
                        action = {
                            IconButton(onClick = onDisconnect) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect", tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                            }
                        }
                    )
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
    openingBalance: Double,
    onEditOpeningBalance: () -> Unit
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
                label = "Opening Balance",
                value = "₹%,.0f".format(Locale.getDefault(), openingBalance),
                action = {
                    IconButton(onClick = onEditOpeningBalance) {
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
    initialBalance: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var balanceText by remember { mutableStateOf(initialBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opening Balance", color = Color.White) },
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

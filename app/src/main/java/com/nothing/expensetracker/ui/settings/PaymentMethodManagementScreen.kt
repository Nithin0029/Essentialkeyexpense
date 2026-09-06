package com.nothing.expensetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.data.local.PaymentMethod
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaymentMethodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var methodToEdit by remember { mutableStateOf<PaymentMethod?>(null) }

    // Deletion Flow State
    var methodToDelete by remember { mutableStateOf<PaymentMethod?>(null) }
    var showInUseDialog by remember { mutableStateOf(false) }
    var showFinalDeleteTransactionsDialog by remember { mutableStateOf(false) }
    var showMoveTransactionsDialog by remember { mutableStateOf(false) }
    var selectedReplacementMethod by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Methods", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Payment Method")
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (uiState.methods.isEmpty() && !uiState.isLoading) {
                EmptyPaymentMethodsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                ) {
                    items(
                        items = uiState.methods,
                        key = { it.id }
                    ) { method ->
                        PaymentMethodItem(
                            method = method,
                            onEditClick = { methodToEdit = method },
                            onDeleteClick = { methodToDelete = method }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PaymentMethodDialog(
            title = "Add Payment Method",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, onResult ->
                viewModel.addPaymentMethod(name, onResult)
            }
        )
    }

    if (methodToEdit != null) {
        PaymentMethodDialog(
            title = "Edit Payment Method",
            initialName = methodToEdit!!.name,
            onDismiss = { methodToEdit = null },
            onConfirm = { name, onResult ->
                viewModel.updatePaymentMethod(methodToEdit!!, name, onResult)
            }
        )
    }

    if (methodToDelete != null) {
        AlertDialog(
            onDismissRequest = { methodToDelete = null },
            title = { Text("Delete Payment Method", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${methodToDelete!!.name}\"?\n\nThis action may affect existing transactions.", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = methodToDelete!!
                        viewModel.deletePaymentMethod(toDelete) { success, message ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Payment method deleted successfully.") }
                                methodToDelete = null
                            } else if (message == "IN_USE") {
                                showInUseDialog = true
                            } else if (message != null) {
                                scope.launch { snackbarHostState.showSnackbar(message) }
                                methodToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { methodToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showInUseDialog && methodToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showInUseDialog = false
                methodToDelete = null
            },
            title = { Text("Payment Method In Use", color = Color.White) },
            text = { Text("This payment method is currently used by existing transactions. Choose what you want to do.", color = Color.White) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showInUseDialog = false
                            showMoveTransactionsDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Move transactions to another method")
                    }
                    Button(
                        onClick = {
                            showInUseDialog = false
                            showFinalDeleteTransactionsDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete all associated transactions")
                    }
                    TextButton(
                        onClick = {
                            showInUseDialog = false
                            methodToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showMoveTransactionsDialog && methodToDelete != null) {
        val otherMethods = uiState.methods.filter { it.id != methodToDelete!!.id }

        AlertDialog(
            onDismissRequest = {
                showMoveTransactionsDialog = false
                methodToDelete = null
            },
            title = { Text("Move Transactions", color = Color.White) },
            text = {
                Column {
                    Text("Select a payment method to move existing transactions to:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedReplacementMethod,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Replacement Method") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            otherMethods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.name) },
                                    onClick = {
                                        selectedReplacementMethod = method.name
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedReplacementMethod.isNotBlank(),
                    onClick = {
                        val toDelete = methodToDelete!!
                        viewModel.moveTransactionsAndDelete(toDelete, selectedReplacementMethod) { success, _ ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Transactions moved and payment method deleted.") }
                                showMoveTransactionsDialog = false
                                methodToDelete = null
                                selectedReplacementMethod = ""
                            }
                        }
                    }
                ) {
                    Text("Confirm Move")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMoveTransactionsDialog = false
                    methodToDelete = null
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showFinalDeleteTransactionsDialog && methodToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showFinalDeleteTransactionsDialog = false
                methodToDelete = null
            },
            title = { Text("Delete Transactions?", color = Color.White) },
            text = { Text("This will permanently delete all transactions under \"${methodToDelete!!.name}\". This action cannot be undone.", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = methodToDelete!!
                        viewModel.deleteTransactionsAndDelete(toDelete) { success, _ ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Payment method and transactions deleted.") }
                                showFinalDeleteTransactionsDialog = false
                                methodToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                ) {
                    Text("Delete Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFinalDeleteTransactionsDialog = false
                    methodToDelete = null
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun PaymentMethodItem(
    method: PaymentMethod,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !method.isSystem, onClick = onEditClick)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getPaymentMethodIcon(method.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = method.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        color = if (method.isSystem) Color.DarkGray else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (method.isSystem) "Default" else "Custom",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (method.isSystem) Color.LightGray else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (method.isSystem) {
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Locked",
                        tint = Color.DarkGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun getPaymentMethodIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name.lowercase()) {
        "upi" -> Icons.Default.QrCode
        "bank" -> Icons.Default.AccountBalance
        "cash" -> Icons.Default.Payments
        "card", "credit card", "debit card" -> Icons.Default.CreditCard
        else -> Icons.Default.Payments
    }
}

@Composable
fun EmptyPaymentMethodsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Payments,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.DarkGray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Payment Methods Found",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = "Add a custom one with the + button.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun PaymentMethodDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Payment Method Name") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name) { success, message ->
                        if (success) {
                            onDismiss()
                        } else {
                            error = message
                        }
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

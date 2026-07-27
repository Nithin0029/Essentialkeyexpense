package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.data.local.Expense
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFriends: () -> Unit,
    viewModel: EditTransactionViewModel = hiltViewModel()
) {
    val expenseState by viewModel.expense.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Transaction", fontWeight = FontWeight.Bold) },
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
        containerColor = Color.Black
    ) { paddingValues ->
        val friends by viewModel.getAllFriends().collectAsState(initial = emptyList())
        expenseState?.let { expense ->
            EditTransactionContent(
                modifier = Modifier.padding(paddingValues),
                expense = expense,
                friends = friends,
                onSave = { updatedExpense ->
                    viewModel.updateExpense(updatedExpense)
                    onNavigateBack()
                },
                onCancel = onNavigateBack,
                onNavigateToFriends = onNavigateToFriends
            )
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionContent(
    modifier: Modifier = Modifier,
    expense: Expense,
    friends: List<String>,
    onSave: (Expense) -> Unit,
    onCancel: () -> Unit,
    onNavigateToFriends: () -> Unit
) {
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var category by remember { mutableStateOf(expense.category) }
    var type by remember { mutableStateOf(expense.type) }
    var paymentMethod by remember { mutableStateOf(expense.paymentMethod) }
    var notes by remember { mutableStateOf(expense.notes) }
    var friendId by remember { mutableStateOf(expense.friendId ?: "") }
    var timestamp by remember { mutableLongStateOf(expense.timestamp) }

    val categories = listOf("Food", "Snack", "Home", "Petrol", "Friends", "Income", "Others")
    val types = listOf("Debit", "Credit")
    val methods = listOf("UPI", "Cash", "Bank")

    var categoryExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var friendExpanded by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { timestamp = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date
        OutlinedTextField(
            value = dateFormatter.format(Date(timestamp)),
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Amount
        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Type
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = !typeExpanded }
        ) {
            OutlinedTextField(
                value = type,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                types.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t) },
                        onClick = {
                            type = t
                            typeExpanded = false
                        }
                    )
                }
            }
        }

        // Category
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded }
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                categories.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c) },
                        onClick = {
                            category = c
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        // Payment Method
        ExposedDropdownMenuBox(
            expanded = methodExpanded,
            onExpandedChange = { methodExpanded = !methodExpanded }
        ) {
            OutlinedTextField(
                value = paymentMethod,
                onValueChange = {},
                readOnly = true,
                label = { Text("Payment Method") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = methodExpanded,
                onDismissRequest = { methodExpanded = false }
            ) {
                methods.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m) },
                        onClick = {
                            paymentMethod = m
                            methodExpanded = false
                        }
                    )
                }
            }
        }

        // Friend
        if (category == "Friends") {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = friendId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Friend") },
                    trailingIcon = {
                        IconButton(onClick = { friendExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                DropdownMenu(
                    expanded = friendExpanded,
                    onDismissRequest = { friendExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    if (friends.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No Friends Found", color = Color.Gray) },
                            onClick = { friendExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to Friends Screen", color = MaterialTheme.colorScheme.primary) },
                            onClick = { 
                                friendExpanded = false
                                onNavigateToFriends()
                            }
                        )
                    } else {
                        friends.forEach { friend ->
                            DropdownMenuItem(
                                text = { Text(friend) },
                                onClick = {
                                    friendId = friend
                                    friendExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val amountVal = amount.toDoubleOrNull() ?: 0.0
                    if (amountVal > 0 && category.isNotBlank() && type.isNotBlank() && paymentMethod.isNotBlank()) {
                        if (category == "Friends" && friendId.isBlank()) {
                            // Validation: Friend is mandatory for Friends category
                            return@Button
                        }
                        onSave(expense.copy(
                            amount = amountVal,
                            category = category,
                            type = type,
                            paymentMethod = paymentMethod,
                            notes = notes,
                            friendId = if (category == "Friends") friendId else null,
                            timestamp = timestamp,
                            isSynced = false // Mark for re-sync
                        ))
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        }
    }
}

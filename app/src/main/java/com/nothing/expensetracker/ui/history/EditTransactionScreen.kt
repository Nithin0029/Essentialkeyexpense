package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.data.local.Category
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.ui.history.TransactionConstants
import android.widget.Toast
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
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is EditTransactionViewModel.UiEvent.Success -> {
                    onNavigateBack()
                }
                is EditTransactionViewModel.UiEvent.Info -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    onNavigateBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (expenseState?.id == 0L) "Add Transaction" else "Edit Transaction", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val friends by viewModel.getAllFriends().collectAsState(initial = emptyList())
        val categories by viewModel.getCategories().collectAsState(initial = emptyList())
        val paymentMethods by viewModel.getAllPaymentMethods().collectAsState(initial = emptyList())
        expenseState?.let { expense ->
            EditTransactionContent(
                modifier = Modifier.padding(paddingValues),
                expense = expense,
                friends = friends,
                categories = categories,
                paymentMethods = paymentMethods,
                isSaving = uiState.isSaving,
                onSave = { updatedExpense ->
                    viewModel.updateExpense(updatedExpense)
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
    categories: List<Category>,
    paymentMethods: List<String>,
    isSaving: Boolean,
    onSave: (Expense) -> Unit,
    onCancel: () -> Unit,
    onNavigateToFriends: () -> Unit
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(if (expense.amount == 0.0) "" else expense.amount.toString()) }
    var category by remember { mutableStateOf(expense.category) }
    var type by remember { mutableStateOf(expense.type) }
    var paymentMethod by remember { mutableStateOf(expense.paymentMethod) }
    var notes by remember { mutableStateOf(expense.notes) }
    var friendId by remember { mutableStateOf(expense.friendId ?: "") }
    var timestamp by remember { mutableLongStateOf(expense.timestamp) }

    val types = TransactionConstants.TRANSACTION_TYPES
    val creditCategories = TransactionConstants.CREDIT_CATEGORIES

    // Only Debit categories carry a hierarchy — Credit's category list is a small fixed set
    // (Salary/Refund/etc.) with no need for subcategories.
    val topLevelDebitCategories = categories.filter { it.parentId == null }
    val categoryByName = categories.associateBy { it.name }
    val currentCategories = if (type == "Credit") creditCategories else topLevelDebitCategories.map { it.name }

    // Resolve which top-level category the currently-selected one belongs to (itself, if it's
    // already top-level), so the subcategory picker below can show that parent's children.
    val selectedCategoryObj = categoryByName[category]
    val selectedTopLevel = if (type == "Debit") {
        if (selectedCategoryObj?.parentId != null) {
            categories.find { it.id == selectedCategoryObj.parentId }
        } else selectedCategoryObj
    } else null
    val subcategoriesOfSelected = selectedTopLevel?.let { top -> categories.filter { it.parentId == top.id } } ?: emptyList()

    val isFriendCategory = TransactionConstants.isFriendCategory(type, category)

    val methods = TransactionConstants.getAvailableMethods(type, category, paymentMethods)

    var categoryExpanded by remember { mutableStateOf(false) }
    var subcategoryExpanded by remember { mutableStateOf(false) }
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
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Amount
        OutlinedTextField(
            value = amount,
            onValueChange = { if (TransactionConstants.isValidAmountInput(it)) amount = it },
            label = { Text("Amount") },
            placeholder = { Text("0.00", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                            if (type != t) {
                                type = t
                                category = TransactionConstants.getInitialCategory(t, topLevelDebitCategories.map { it.name })
                                // Reset payment method if RAS was selected but is no longer valid
                                if (paymentMethod == "RAS") {
                                    paymentMethod = "UPI"
                                }
                            }
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
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                currentCategories.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c) },
                        onClick = {
                            category = c
                            categoryExpanded = false
                            // Reset payment method if RAS was selected but is no longer valid for the new category
                            if (paymentMethod == "RAS" && !(type == "Credit" && category == "Friend")) {
                                paymentMethod = "UPI"
                            }
                        }
                    )
                }
            }
        }

        // Subcategory (only when the selected Debit category has any, e.g. Bike -> Petrol/Service)
        if (subcategoriesOfSelected.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = subcategoryExpanded,
                onExpandedChange = { subcategoryExpanded = !subcategoryExpanded }
            ) {
                OutlinedTextField(
                    value = if (selectedCategoryObj?.parentId != null) category else "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Subcategory (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                ExposedDropdownMenu(
                    expanded = subcategoryExpanded,
                    onDismissRequest = { subcategoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None", color = Color.Gray) },
                        onClick = {
                            category = selectedTopLevel!!.name
                            subcategoryExpanded = false
                        }
                    )
                    subcategoriesOfSelected.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub.name) },
                            onClick = {
                                category = sub.name
                                subcategoryExpanded = false
                                if (paymentMethod == "RAS") {
                                    paymentMethod = "UPI"
                                }
                            }
                        )
                    }
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
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
        if (isFriendCategory) {
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
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                        if (isFriendCategory && friendId.isBlank()) {
                            Toast.makeText(context, "Please select a friend.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSave(expense.copy(
                            amount = amountVal,
                            category = category,
                            type = type,
                            paymentMethod = paymentMethod,
                            notes = notes,
                            friendId = if (isFriendCategory) friendId else null,
                            timestamp = timestamp,
                            syncStatus = "Pending" // Mark for sync
                        ))
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save")
                }
            }
        }
    }
}

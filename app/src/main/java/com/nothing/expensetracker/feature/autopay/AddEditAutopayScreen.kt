package com.nothing.expensetracker.feature.autopay

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.data.local.AutopayRule
import com.nothing.expensetracker.ui.history.TransactionConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAutopayScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFriends: () -> Unit,
    viewModel: AddEditAutopayViewModel = hiltViewModel()
) {
    val ruleState by viewModel.rule.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(saved) {
        if (saved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (ruleState?.id == 0L) "New Autopay" else "Edit Autopay",
                        fontWeight = FontWeight.Bold
                    )
                },
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
        val categories by viewModel.getAllCategories().collectAsState(initial = emptyList())
        val paymentMethods by viewModel.getAllPaymentMethods().collectAsState(initial = emptyList())

        ruleState?.let { rule ->
            AutopayForm(
                modifier = Modifier.padding(paddingValues),
                rule = rule,
                friends = friends,
                categories = categories,
                paymentMethods = paymentMethods,
                onSave = { viewModel.save(it) },
                onCancel = onNavigateBack,
                onNavigateToFriends = onNavigateToFriends
            )
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutopayForm(
    modifier: Modifier = Modifier,
    rule: AutopayRule,
    friends: List<String>,
    categories: List<String>,
    paymentMethods: List<String>,
    onSave: (AutopayRule) -> Unit,
    onCancel: () -> Unit,
    onNavigateToFriends: () -> Unit
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(if (rule.amount == 0.0) "" else rule.amount.toString()) }
    var description by remember { mutableStateOf(rule.description) }
    var category by remember { mutableStateOf(rule.category) }
    var type by remember { mutableStateOf(rule.type) }
    var paymentMethod by remember { mutableStateOf(rule.paymentMethod) }
    var friendId by remember { mutableStateOf(rule.friendId ?: "") }
    var dayOfMonth by remember { mutableIntStateOf(rule.dayOfMonth) }
    var isActive by remember { mutableStateOf(rule.isActive) }

    val types = TransactionConstants.TRANSACTION_TYPES
    val creditCategories = TransactionConstants.CREDIT_CATEGORIES
    val currentCategories = if (type == "Credit") creditCategories else categories
    val isFriendCategory = TransactionConstants.isFriendCategory(type, category)
    val methods = TransactionConstants.getAvailableMethods(type, category, paymentMethods)

    var categoryExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var friendExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.DarkGray,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Name
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Name (e.g. Rent, Netflix)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors
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
            colors = fieldColors
        )

        // Day of Month
        Column {
            ExposedDropdownMenuBox(
                expanded = dayExpanded,
                onExpandedChange = { dayExpanded = !dayExpanded }
            ) {
                OutlinedTextField(
                    value = "Day $dayOfMonth of every month",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Repeats On") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
                ExposedDropdownMenu(
                    expanded = dayExpanded,
                    onDismissRequest = { dayExpanded = false }
                ) {
                    (1..31).forEach { day ->
                        DropdownMenuItem(
                            text = { Text(day.toString()) },
                            onClick = {
                                dayOfMonth = day
                                dayExpanded = false
                            }
                        )
                    }
                }
            }
            Text(
                text = "If a month has fewer days, it runs on the last day of that month instead.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

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
                colors = fieldColors
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
                                category = TransactionConstants.getInitialCategory(t, categories)
                                if (paymentMethod == "RAS") paymentMethod = "UPI"
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
                colors = fieldColors
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
                            if (paymentMethod == "RAS" && !(type == "Credit" && category == "Friend")) {
                                paymentMethod = "UPI"
                            }
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
                colors = fieldColors
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
                    colors = fieldColors
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

        // Active toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = isActive,
                onCheckedChange = { isActive = it },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }

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
                    if (amountVal > 0 && category.isNotBlank() && paymentMethod.isNotBlank()) {
                        if (isFriendCategory && friendId.isBlank()) {
                            Toast.makeText(context, "Please select a friend.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSave(
                            rule.copy(
                                amount = amountVal,
                                description = description.trim(),
                                category = category,
                                type = type,
                                paymentMethod = paymentMethod,
                                friendId = if (isFriendCategory) friendId else null,
                                dayOfMonth = dayOfMonth,
                                isActive = isActive
                            )
                        )
                    } else {
                        Toast.makeText(context, "Enter a valid amount and category.", Toast.LENGTH_SHORT).show()
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

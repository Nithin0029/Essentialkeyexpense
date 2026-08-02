package com.nothing.expensetracker.feature.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddOverlayContent(
    initialColor: String,
    debitCategories: List<String>,
    friends: List<String>,
    onSaveExpense: (
        amount: Double,
        description: String,
        category: String,
        type: String,
        paymentMethod: String,
        friendId: String?,
        notes: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf("Debit") }
    var selectedCategory by remember { mutableStateOf(if (debitCategories.isNotEmpty()) debitCategories.first() else "Other") }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var friendIdText by remember { mutableStateOf("") }

    val currentCategories = if (transactionType == "Credit") {
        com.nothing.expensetracker.ui.history.TransactionConstants.CREDIT_CATEGORIES
    } else {
        debitCategories
    }

    val isFriendCategory = com.nothing.expensetracker.ui.history.TransactionConstants.isFriendCategory(transactionType, selectedCategory)
    
    val paymentMethods = com.nothing.expensetracker.ui.history.TransactionConstants.getAvailableMethods(transactionType, selectedCategory)

    var categoryExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }
    var friendExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "QUICK TRANSACTION",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Debit/Credit Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Type:", color = Color.Gray, modifier = Modifier.width(60.dp))
                    FilterChip(
                        selected = transactionType == "Debit",
                        onClick = { 
                            if (transactionType != "Debit") {
                                transactionType = "Debit"
                                selectedCategory = com.nothing.expensetracker.ui.history.TransactionConstants.getInitialCategory("Debit", debitCategories)
                                if (paymentMethod == "RAS") paymentMethod = "UPI"
                            }
                        },
                        label = { Text("Debit") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = transactionType == "Credit",
                        onClick = { 
                            if (transactionType != "Credit") {
                                transactionType = "Credit"
                                selectedCategory = com.nothing.expensetracker.ui.history.TransactionConstants.getInitialCategory("Credit", debitCategories)
                                if (paymentMethod == "RAS") paymentMethod = "UPI"
                            }
                        },
                        label = { Text("Credit") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = Color.Gray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        currentCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                    if (paymentMethod == "RAS" && !com.nothing.expensetracker.ui.history.TransactionConstants.isFriendCategory(transactionType, selectedCategory)) {
                                        paymentMethod = "UPI"
                                    }
                                }
                            )
                        }
                    }
                }

                if (isFriendCategory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Friend Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = friendIdText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Friend", color = Color.Gray) },
                            trailingIcon = {
                                IconButton(onClick = { friendExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth()
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
                            } else {
                                friends.forEach { friend ->
                                    DropdownMenuItem(
                                        text = { Text(friend) },
                                        onClick = {
                                            friendIdText = friend
                                            friendExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Payment Method Dropdown
                ExposedDropdownMenuBox(
                    expanded = paymentExpanded,
                    onExpandedChange = { paymentExpanded = !paymentExpanded }
                ) {
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Method", color = Color.Gray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = paymentExpanded,
                        onDismissRequest = { paymentExpanded = false }
                    ) {
                        paymentMethods.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method) },
                                onClick = {
                                    paymentMethod = method
                                    paymentExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0 && selectedCategory.isNotBlank() && transactionType.isNotBlank() && paymentMethod.isNotBlank()) {
                                val finalFriendId = if (isFriendCategory) friendIdText else null
                                
                                if (isFriendCategory && finalFriendId.isNullOrBlank()) {
                                    android.widget.Toast.makeText(context, "Please select a friend.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                
                                onSaveExpense(
                                    amount,
                                    descriptionText,
                                    selectedCategory,
                                    transactionType,
                                    paymentMethod,
                                    finalFriendId,
                                    notes
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

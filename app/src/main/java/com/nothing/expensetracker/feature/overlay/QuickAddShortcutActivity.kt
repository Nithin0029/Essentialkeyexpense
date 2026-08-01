package com.nothing.expensetracker.feature.overlay

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.sync.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddShortcutActivity : ComponentActivity() {

    @Inject lateinit var repository: ExpenseRepository
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var amountText by remember { mutableStateOf("") }
            var selectedType by remember { mutableStateOf("Debit") }
            var selectedCategory by remember { mutableStateOf("Food") }
            var selectedMethod by remember { mutableStateOf("UPI") }
            var notesText by remember { mutableStateOf("") }
            var friendId by remember { mutableStateOf("") }

            var catExpanded by remember { mutableStateOf(false) }
            var methodExpanded by remember { mutableStateOf(false) }
            var friendExpanded by remember { mutableStateOf(false) }

            val friends by repository.getAllFriends().collectAsState(initial = emptyList())
            val categories = listOf("Food", "Snack", "Home", "Petrol", "Friends", "Income", "Others")
            val paymentMethods = listOf("UPI", "Cash", "Bank")

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("New Transaction", color = Color.White, style = MaterialTheme.typography.titleMedium)

                        // 1. Amount
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (₹)", color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 2. Type (Debit / Credit)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            FilterChip(
                                selected = selectedType == "Debit",
                                onClick = { selectedType = "Debit" },
                                label = { Text("Debit (Expense)") }
                            )
                            FilterChip(
                                selected = selectedType == "Credit",
                                onClick = { selectedType = "Credit" },
                                label = { Text("Credit (Income)") }
                            )
                        }

                        // 3. Category Dropdown
                        Box {
                            OutlinedButton(
                                onClick = { catExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Category: $selectedCategory", color = Color.White)
                            }
                            DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = { selectedCategory = cat; catExpanded = false }
                                    )
                                }
                            }
                        }

                        // Friend specific fields
                        if (selectedCategory == "Friends") {
                            // Friend Selector
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = friendId,
                                    onValueChange = { friendId = it },
                                    label = { Text("Select Friend", color = Color.Gray) },
                                    readOnly = true,
                                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { friendExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                )
                                DropdownMenu(
                                    expanded = friendExpanded,
                                    onDismissRequest = { friendExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    if (friends.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No Friends Found", color = Color.Gray) },
                                            onClick = { 
                                                friendExpanded = false
                                                // Navigate to app to add friends if possible, 
                                                // but typically we stay in the shortcut.
                                                // For now just informative.
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add Friend in App", color = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                friendExpanded = false
                                                val intent = android.content.Intent(this@QuickAddShortcutActivity, com.nothing.expensetracker.MainActivity::class.java)
                                                intent.putExtra("navigate_to", "friends")
                                                startActivity(intent)
                                                finish()
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

                        // 4. Payment Method Dropdown
                        Box {
                            OutlinedButton(
                                onClick = { methodExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Payment: $selectedMethod", color = Color.White)
                            }
                            DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                                paymentMethods.forEach { method ->
                                    DropdownMenuItem(
                                        text = { Text(method) },
                                        onClick = { selectedMethod = method; methodExpanded = false }
                                    )
                                }
                            }
                        }

                        // 5. Notes
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            label = { Text("Notes (Optional)", color = Color.Gray) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Action Buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { finish() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    val amount = amountText.toDoubleOrNull()
                                    if (amount == null || amount <= 0) {
                                        Toast.makeText(applicationContext, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (selectedCategory == "Friends" && friendId.isBlank()) {
                                        Toast.makeText(applicationContext, "Please select a friend", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    
                                    saveTransaction(
                                        amount = amount,
                                        category = selectedCategory,
                                        type = selectedType,
                                        method = selectedMethod,
                                        notes = notesText,
                                        fId = if (selectedCategory == "Friends") friendId else null
                                    )
                                    finish()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveTransaction(
        amount: Double,
        category: String,
        type: String,
        method: String,
        notes: String,
        fId: String?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val expense = Expense(
                amount = amount,
                description = if (notes.isBlank()) category else notes,
                category = category,
                type = type,
                paymentMethod = method,
                friendId = fId,
                notes = notes,
                timestamp = System.currentTimeMillis(),
                syncStatus = "Pending"
            )
            repository.insertExpense(expense)
            syncScheduler.scheduleSync()
        }
    }
}

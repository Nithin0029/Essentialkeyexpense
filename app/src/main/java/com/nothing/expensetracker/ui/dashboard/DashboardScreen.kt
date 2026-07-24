package com.nothing.expensetracker.ui.dashboard

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.sync.SheetsAuthManager
import com.nothing.expensetracker.sync.SyncPrefs
import com.nothing.expensetracker.sync.SyncScheduler
import com.nothing.expensetracker.ui.MainViewModel
import com.nothing.expensetracker.ui.components.CategoryDonutChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// Update this constant with each release!
const val CURRENT_VERSION_TAG = "v2.0.0"
const val GITHUB_REPO_USER = "abhishekmannatharaj"
const val GITHUB_REPO_NAME = "Essentialkeyexpense"

@Composable
fun DashboardScreen(viewModel: MainViewModel = hiltViewModel()) {
    val expenses by viewModel.expenses.collectAsState()
    val categoryExpenses by viewModel.categoryExpenses.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val context = LocalContext.current
    
    var itemToDelete by remember { mutableStateOf<Expense?>(null) }
    var itemToEdit by remember { mutableStateOf<Expense?>(null) }
    
    // 🔍 Check GitHub for app updates silently on launch
    CheckForAppUpdates(context = context)

    var sheetIdInput by remember {
        mutableStateOf(SyncPrefs.getSpreadsheetId(context))
    }

    var accountName by remember { 
        val savedEmail = context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
            .getString("account_email", null)
        mutableStateOf(savedEmail ?: GoogleSignIn.getLastSignedInAccount(context)?.email ?: "Not Connected") 
    }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val sheetsScope = com.google.android.gms.common.api.Scope(com.google.api.services.sheets.v4.SheetsScopes.SPREADSHEETS)

            if (!GoogleSignIn.hasPermissions(account, sheetsScope)) {
                Toast.makeText(context, "Requesting Sheets Permission...", Toast.LENGTH_SHORT).show()
                GoogleSignIn.requestPermissions(
                    context as ComponentActivity,
                    1001,
                    account,
                    sheetsScope
                )
            } else {
                accountName = account?.email ?: "Connected"
                context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("account_email", accountName)
                    .apply()

                Toast.makeText(context, "Connected & Permission Granted! ✅", Toast.LENGTH_SHORT).show()
                SyncScheduler(context).scheduleSync()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Header & Connect Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DASHBOARD",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = accountName,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = {
                    val authManager = SheetsAuthManager(context)
                    authManager.signInClient.signOut().addOnCompleteListener {
                        googleAuthLauncher.launch(authManager.signInClient.signInIntent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
            ) {
                Text("Connect 📊", color = Color.White, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Donut Chart
        CategoryDonutChart(
            expenses = categoryExpenses,
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            onMonthChange = { viewModel.updateMonth(it) },
            onYearChange = { viewModel.updateYear(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Settings Section: Sheet ID
        Text(
            text = "SETTINGS",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = sheetIdInput,
            onValueChange = { newId ->
                sheetIdInput = newId
                SyncPrefs.setSpreadsheetId(context, newId.trim())
            },
            label = { Text("Google Sheet ID", color = Color.Gray, fontSize = 12.sp) },
            placeholder = { Text("Paste ID here", color = Color.DarkGray) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = Color.White
            ),
            singleLine = true
        )
        Text(
            text = "ID is in your sheet URL: /d/YOUR_ID/edit",
            color = Color.DarkGray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // History Section
        Text(
            text = "EXPENSE HISTORY",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (expenses.isEmpty()) {
            Box(
                modifier = Modifier.fillWeight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No local expenses found.\nUse the 💵 widget!",
                    color = Color.DarkGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(expenses) { expense ->
                    ExpenseItem(
                        expense = expense,
                        onEditClick = { itemToEdit = expense },
                        onDeleteClick = { itemToDelete = expense }
                    )
                }
            }
        }
    }

    // Edit Transaction Dialog
    itemToEdit?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedExpense ->
                viewModel.updateExpense(updatedExpense)
                itemToEdit = null
            }
        )
    }

    // Confirmation Popup Dialog
    itemToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text("Delete Transaction?", color = Color.White, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    "Are you sure you want to delete this transaction for ₹${expense.amount}?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(expense)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun Modifier.fillWeight(weight: Float): Modifier = this.then(Modifier.fillMaxWidth().fillMaxHeight(weight))

@Composable
fun ExpenseItem(expense: Expense, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.category.uppercase(),
                    color = getCategoryColor(expense.colorCode),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (expense.notes.isNotBlank()) expense.notes else expense.description,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expense.timestamp)),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (expense.type == "Credit") "+" else "-"}₹${expense.amount}",
                    color = if (expense.type == "Credit") Color(0xFF4CAF50) else Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onConfirm: (Expense) -> Unit
) {
    var amountText by remember { mutableStateOf(expense.amount.toString()) }
    var notes by remember { mutableStateOf(expense.notes) }
    var selectedCategory by remember { mutableStateOf(expense.category) }
    var transactionType by remember { mutableStateOf(expense.type) }
    var paymentMethod by remember { mutableStateOf(expense.paymentMethod) }

    val categories = listOf("Food", "Snack", "Home", "Petrol", "Friends", "Income", "Others")
    val paymentMethods = listOf("UPI", "Cash", "Bank")

    var categoryExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = { Text("Edit Transaction", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )

                // Type Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = transactionType == "Debit",
                        onClick = { transactionType = "Debit" },
                        label = { Text("Debit") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = transactionType == "Credit",
                        onClick = { transactionType = "Credit" },
                        label = { Text("Credit") }
                    )
                }

                // Category
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
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Method
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
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
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

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: expense.amount
                    onConfirm(expense.copy(
                        amount = amount,
                        notes = notes,
                        category = selectedCategory,
                        type = transactionType,
                        paymentMethod = paymentMethod,
                        isSynced = false // Reset sync status so it syncs the update
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Update", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

fun getCategoryColor(colorCode: String): Color {
    return when (colorCode.uppercase()) {
        "GREEN" -> Color(0xFF4CAF50)
        "YELLOW" -> Color(0xFFFFEB3B)
        "RED" -> Color(0xFFF44336)
        "BLUE" -> Color(0xFF2196F3)
        else -> Color.White
    }
}

@Composable
fun CheckForAppUpdates(context: android.content.Context) {
    var updateAvailable by remember { mutableStateOf(false) }
    var latestTag by remember { mutableStateOf("") }
    var releaseUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.github.com/repos/$GITHUB_REPO_USER/$GITHUB_REPO_NAME/releases/latest"
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tag = json.getString("tag_name")
                    val htmlUrl = json.getString("html_url")

                    // Compare version tags
                    if (tag != CURRENT_VERSION_TAG) {
                        latestTag = tag
                        releaseUrl = htmlUrl
                        updateAvailable = true
                    }
                }
            } catch (e: Exception) {
                // Fail gracefully if offline or no updates found
                e.printStackTrace()
            }
        }
    }

    // Nothing OS Style Update Popup
    if (updateAvailable) {
        AlertDialog(
            onDismissRequest = { updateAvailable = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text("Update Available! 🚀", color = Color.White, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    "A new version ($latestTag) is available on GitHub. Update now to get the latest fixes and features!",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(releaseUrl))
                        context.startActivity(intent)
                        updateAvailable = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                ) {
                    Text("Download $latestTag", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { updateAvailable = false }) {
                    Text("Later", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

package com.nothing.expensetracker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.nothing.expensetracker.ui.theme.EssentialExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Paste your Google Sheet ID here (one-time setup)
        SyncPrefs.setSpreadsheetId(
            this, 
            "12O3ilXnYw_xVsg4KhFJqAX5SuH7wEaODR-OS_W0CBoY"
        )

        enableEdgeToEdge()
        setContent {
            EssentialExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    ExpenseTrackerScreen()
                }
            }
        }
    }
}

@Composable
fun ExpenseTrackerScreen(viewModel: MainViewModel = hiltViewModel()) {
    val expenses by viewModel.expenses.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var accountName by remember { mutableStateOf("Not Connected") }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            
            // The specific scope we need for Google Sheets
            val sheetsScope = com.google.android.gms.common.api.Scope(com.google.api.services.sheets.v4.SheetsScopes.SPREADSHEETS)

            // Check if Google actually granted the permission!
            if (!GoogleSignIn.hasPermissions(account, sheetsScope)) {
                Toast.makeText(context, "Requesting Sheets Permission...", Toast.LENGTH_SHORT).show()
                
                // Force the permission dialog to appear over the app
                GoogleSignIn.requestPermissions(
                    context as ComponentActivity,
                    1001,
                    account,
                    sheetsScope
                )
            } else {
                accountName = account?.email ?: "Connected"
                Toast.makeText(context, "Connected & Permission Granted! ✅", Toast.LENGTH_SHORT).show()
                
                // Safe to sync now!
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "EXPENSE HISTORY",
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
                    // Clear cached session so Google forces the permission screen
                    authManager.signInClient.signOut().addOnCompleteListener {
                        googleAuthLauncher.launch(authManager.signInClient.signInIntent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
            ) {
                Text("Connect 📊", color = Color.White, fontSize = 10.sp)
            }
        }

        if (expenses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No expenses logged yet.\nUse the home screen widget!",
                    color = Color.DarkGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(expenses) { expense ->
                    ExpenseItem(expense)
                }
            }
        }
    }
}

@Composable
fun ExpenseItem(expense: Expense) {
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
            Column {
                Text(
                    text = expense.category.uppercase(),
                    color = getCategoryColor(expense.colorCode),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = expense.description,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expense.timestamp)),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "₹${expense.amount}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
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

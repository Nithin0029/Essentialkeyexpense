package com.nothing.expensetracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.nothing.expensetracker.ui.DashboardUiState
import com.nothing.expensetracker.ui.MainViewModel
import com.nothing.expensetracker.data.local.Expense
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
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // 🔍 Check GitHub for app updates silently on launch
    CheckForAppUpdates(context = context)

    when (val state = uiState) {
        is DashboardUiState.Loading -> {
            DashboardLoadingState()
        }
        is DashboardUiState.Empty -> {
            DashboardEmptyState()
        }
        is DashboardUiState.Success -> {
            val dashboardData = state.data
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DashboardHeader()

                Spacer(modifier = Modifier.height(16.dp))

                CurrentBalanceCard(
                    balance = dashboardData.currentBalance,
                    openingBalance = dashboardData.openingBalance
                )

                Spacer(modifier = Modifier.height(16.dp))

                IncomeExpenseCards(income = dashboardData.income, expense = dashboardData.expense)

                Spacer(modifier = Modifier.height(16.dp))

                SummaryCards(
                    today = dashboardData.todaySpending,
                    week = dashboardData.weekSpending,
                    month = dashboardData.monthSpending
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Category Breakdown Card
                CategoryBreakdownCard(
                    totalExpense = dashboardData.expense,
                    topCategories = dashboardData.topCategories
                )

                Spacer(modifier = Modifier.height(24.dp))

                RecentTransactionsCard(transactions = dashboardData.recentTransactions)
            }
        }
    }
}

@Composable
fun DashboardLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Simple Material 3 Placeholders
        Box(modifier = Modifier.fillMaxWidth().height(60.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)))
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(24.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f).height(100.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(20.dp)))
            Box(modifier = Modifier.weight(1f).height(100.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(20.dp)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f).height(80.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(16.dp)))
            Box(modifier = Modifier.weight(1f).height(80.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(16.dp)))
            Box(modifier = Modifier.weight(1f).height(80.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(16.dp)))
        }
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(24.dp)))
    }
}

@Composable
fun DashboardEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Transactions Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start tracking your expenses to see your dashboard data.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { /* TODO: Open Add Transaction */ },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add Transaction")
        }
    }
}

@Composable
fun DashboardHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.padding(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Green, CircleShape)
                )
                Text(
                    text = "Synced",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CurrentBalanceCard(balance: Double, openingBalance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Current Balance",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "₹%,.0f".format(Locale.getDefault(), balance),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Opening Balance: ₹%,.0f".format(Locale.getDefault(), openingBalance),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
        }
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

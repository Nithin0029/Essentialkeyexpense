package com.nothing.expensetracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.ui.DashboardUiState
import com.nothing.expensetracker.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToBudget: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is DashboardUiState.Loading -> {
            DashboardLoadingState()
        }
        is DashboardUiState.Empty -> {
            DashboardEmptyState(onAddClick = onNavigateToAddTransaction)
        }
        is DashboardUiState.Success -> {
            val dashboardData = state.data
            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onNavigateToAddTransaction,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Transaction"
                        )
                    }
                },
                containerColor = Color.Black
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    DashboardHeader(isSynced = dashboardData.isAllSynced)

                    Spacer(modifier = Modifier.height(16.dp))

                    AssetsOverviewCard(
                        totalAssets = dashboardData.totalAssets,
                        bankBalance = dashboardData.bankBalance,
                        cashBalance = dashboardData.cashBalance
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    BudgetSummaryCard(onClick = onNavigateToBudget)

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
fun DashboardEmptyState(onAddClick: () -> Unit) {
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add Transaction")
        }
    }
}

@Composable
fun DashboardHeader(isSynced: Boolean) {
    val currentDate = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()) }
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
                text = currentDate,
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
                        .background(if (isSynced) Color.Green else Color(0xFFFFC107), CircleShape)
                )
                Text(
                    text = if (isSynced) "Synced" else "Pending",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AssetsOverviewCard(
    totalAssets: Double,
    bankBalance: Double,
    cashBalance: Double
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
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Total Assets",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₹%,.0f".format(Locale.getDefault(), totalAssets),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Bank Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹%,.0f".format(Locale.getDefault(), bankBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Cash Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹%,.0f".format(Locale.getDefault(), cashBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetSummaryCard(
    onClick: () -> Unit,
    viewModel: com.nothing.expensetracker.ui.settings.BudgetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val usage = uiState.overallUsage

    if (usage.budget != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Monthly Budget", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        text = "₹%,.0f".format(usage.budget.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { usage.percentage },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = if (usage.percentage > 0.9f) Color.Red else MaterialTheme.colorScheme.primary,
                    trackColor = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "₹%,.0f spent".format(usage.spent),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹%,.0f left".format(usage.remaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (usage.remaining < 1000) Color.Red else Color.Gray
                    )
                }
            }
        }
    }
}

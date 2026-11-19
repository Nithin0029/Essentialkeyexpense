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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.ui.DashboardUiState
import com.nothing.expensetracker.ui.MainViewModel
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.AccountBalance
import com.nothing.expensetracker.util.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToBudget: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val needsBalanceSetup by viewModel.needsBalanceSetup.collectAsState()
    val setupAccounts by viewModel.accounts.collectAsState()

    // Shown once per install, over whichever dashboard state is rendering. The dashboard is the
    // first screen past the MPIN gate, so this is the earliest point the user can be asked.
    //
    // Held back until the accounts exist: they are seeded on the first launch, and a prompt with
    // no fields in it would ask for balances it had nowhere to put.
    if (needsBalanceSetup && setupAccounts.isNotEmpty()) {
        InitialBalanceDialog(
            accounts = setupAccounts,
            onConfirm = { balances -> viewModel.completeBalanceSetup(balances) },
            onSkip = { viewModel.skipBalanceSetup() }
        )
    }

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
                containerColor = MaterialTheme.colorScheme.background
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
                        accounts = dashboardData.accounts,
                        unassignedNet = dashboardData.unassignedNet
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
            color = MaterialTheme.colorScheme.onBackground,
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
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = currentDate,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AssetsOverviewCard(
    totalAssets: Double,
    accounts: List<AccountBalance>,
    unassignedNet: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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
                text = formatCurrency(totalAssets),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // One row per account rather than a fixed bank/cash pair. Two accounts still read
                // as two lines; ten read as ten, which is the whole point of the change.
                accounts.forEachIndexed { index, account ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatCurrency(account.balance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Said out loud rather than quietly dropped: before accounts existed, money on an
            // unrecognised payment method simply never reached any balance and nothing said so.
            if (unassignedNet != 0.0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${formatCurrency(unassignedNet)} is on a payment method that no " +
                        "longer exists, so it is not counted above.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF44336)
                )
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Monthly Budget", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = formatCurrency(usage.budget.amount),
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
                        text = "${formatCurrency(usage.spent)} spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "${formatCurrency(usage.remaining)} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (usage.remaining < 1000) Color.Red else Color.Gray
                    )
                }
            }
        }
    }
}

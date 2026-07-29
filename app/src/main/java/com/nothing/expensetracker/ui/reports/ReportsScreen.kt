package com.nothing.expensetracker.ui.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.ui.settings.BudgetViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        viewModel.setCustomDateRange(start, end)
                    }
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
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.height(400.dp)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Date Filter
            DateFilterSection(
                selectedFilter = uiState.dateFilter,
                onFilterSelected = {
                    if (it == DateFilter.CUSTOM) {
                        showDatePicker = true
                    } else {
                        viewModel.setDateFilter(it)
                    }
                }
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiState.summary.transactionCount == 0) {
                EmptyReportsState()
            } else {
                // 2. Summary Cards
                SummaryCardsRow(summary = uiState.summary)

                // 3. Expense vs Income
                ReportCard(title = "Expense vs Income") {
                    ComparisonBarChart(income = uiState.summary.income, expense = uiState.summary.expense)
                }

                // 4. Expense by Category
                ReportCard(title = "Expense by Category") {
                    CategoryPieChart(reports = uiState.categoryReports)
                }

                // 5. Payment Method Analysis
                ReportCard(title = "Payment Method Analysis") {
                    PaymentMethodAnalysis(reports = uiState.paymentMethodReports)
                }

                // 6. Friends Summary
                ReportCard(title = "Friends Summary") {
                    FriendsSummarySection(summary = uiState.friendsSummary)
                }

                // 7. Top Spending Categories
                ReportCard(title = "Top Spending Categories") {
                    TopCategoriesList(reports = uiState.topCategories)
                }

                // New: Budget vs Reality
                BudgetRealitySection()

                // 8. Recent Insights
                ReportCard(title = "Recent Insights") {
                    InsightsSection(insights = uiState.insights)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ReportCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun DateFilterSection(
    selectedFilter: DateFilter,
    onFilterSelected: (DateFilter) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF1E1E1E),
                    labelColor = Color.Gray
                ),
                border = null,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun SummaryCardsRow(summary: SummaryData) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = "Income",
                amount = summary.income,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                iconColor = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Expense",
                amount = summary.expense,
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                iconColor = Color(0xFFF44336),
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = "Savings",
                amount = summary.savings,
                icon = Icons.Default.Info,
                iconColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Transactions",
                amount = summary.transactionCount.toDouble(),
                isCurrency = false,
                icon = Icons.Default.CalendarToday,
                iconColor = Color.Gray,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    amount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isCurrency: Boolean = true
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isCurrency) "₹%,.0f".format(amount) else amount.toInt().toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ComparisonBarChart(income: Double, expense: Double) {
    val maxVal = maxOf(income, expense).coerceAtLeast(1.0)
    val incomeHeight = (income / maxVal).toFloat()
    val expenseHeight = (expense / maxVal).toFloat()

    val animatedIncome = remember { Animatable(0f) }
    val animatedExpense = remember { Animatable(0f) }

    LaunchedEffect(income, expense) {
        animatedIncome.animateTo(incomeHeight, tween(1000))
        animatedExpense.animateTo(expenseHeight, tween(1000))
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        ChartBar(height = animatedIncome.value, color = Color(0xFF4CAF50), label = "Income")
        ChartBar(height = animatedExpense.value, color = Color(0xFFF44336), label = "Expense")
    }
}

@Composable
fun ChartBar(height: Float, color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight(height.coerceAtLeast(0.05f))
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun CategoryPieChart(reports: List<CategoryReport>) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFFFFC107),
        Color(0xFF9C27B0),
        Color(0xFFF44336),
        Color(0xFF00BCD4)
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var startAngle = -90f
            reports.forEachIndexed { index, report ->
                val sweepAngle = report.percentage * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 40f, cap = StrokeCap.Round)
                )
                startAngle += sweepAngle
            }
        }
        
        Spacer(modifier = Modifier.width(24.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            reports.take(4).forEachIndexed { index, report ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors[index % colors.size]))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${report.name} (${(report.percentage * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentMethodAnalysis(reports: List<PaymentMethodReport>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        reports.forEach { report ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = report.method, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(text = "₹%,.0f".format(report.amount), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { report.percentage },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun FriendsSummarySection(summary: FriendsReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Friends Owe You", color = Color.Gray)
            Text(text = "₹%,.0f".format(summary.friendsOweYou), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "You Owe Friends", color = Color.Gray)
            Text(text = "₹%,.0f".format(summary.youOweFriends), color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Outstanding Balance", color = Color.White)
            Text(text = "₹%,.0f".format(summary.outstandingBalance), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TopCategoriesList(reports: List<CategoryReport>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reports.forEachIndexed { index, report ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${index + 1}.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.width(24.dp))
                Text(text = report.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                Text(text = "₹%,.0f".format(report.amount), style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InsightsSection(insights: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        insights.forEach { insight ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = insight, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun EmptyReportsState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "No report data available.", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(
            text = "Start adding transactions to view your financial insights.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BudgetRealitySection(
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val budgetState by viewModel.uiState.collectAsState()
    val overallUsage = budgetState.overallUsage

    if (overallUsage.budget != null) {
        ReportCard(title = "Budget vs Reality") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Overall
                BudgetRealityItem(
                    label = "Monthly Budget",
                    limit = overallUsage.budget.amount,
                    spent = overallUsage.spent,
                    percentage = overallUsage.percentage
                )

                if (budgetState.categoryUsages.any { it.budget != null }) {
                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                    Text("Category Limits", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    
                    budgetState.categoryUsages.filter { it.budget != null }.forEach { usage ->
                        BudgetRealityItem(
                            label = usage.categoryName,
                            limit = usage.budget!!.amount,
                            spent = usage.spent,
                            percentage = usage.percentage
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetRealityItem(
    label: String,
    limit: Double,
    spent: Double,
    percentage: Float
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(text = "${(percentage * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = if (percentage > 0.9f) Color.Red else MaterialTheme.colorScheme.primary,
            trackColor = Color.DarkGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Spent: ₹%,.0f".format(spent), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(text = "Limit: ₹%,.0f".format(limit), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

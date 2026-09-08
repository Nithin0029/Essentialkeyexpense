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
import com.nothing.expensetracker.util.formatCurrency
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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                // 2. Summary Cards — the headline numbers first.
                SummaryCardsRow(summary = uiState.summary)

                // 3. Insights — the "so what" of this period, surfaced right after the numbers
                // instead of buried at the bottom of a long scroll.
                if (uiState.insights.isNotEmpty()) {
                    ReportCard(title = "Insights") {
                        InsightsSection(insights = uiState.insights)
                    }
                }

                // 4. Expense by Category — the single most useful drill-down, promoted above the
                // supporting charts. Donut + full ranked list combined (previously two separate
                // cards duplicated the same ranking as a legend and as a list).
                if (uiState.categoryReports.isNotEmpty()) {
                    ReportCard(title = "Expense by Category") {
                        CategoryBreakdownSection(reports = uiState.categoryReports)
                    }
                }

                // 5. Expense vs Income
                ReportCard(title = "Expense vs Income") {
                    ComparisonBarChart(income = uiState.summary.income, expense = uiState.summary.expense)
                }

                // 6. Payment Method Analysis
                if (uiState.paymentMethodReports.isNotEmpty()) {
                    ReportCard(title = "Payment Method Analysis") {
                        PaymentMethodAnalysis(reports = uiState.paymentMethodReports)
                    }
                }

                // 6b. Transfers — excluded from income/expense above since they're just money
                // moving between your own accounts, not real spending. Grouped near payment
                // methods since both are about where money moved.
                if (uiState.transferCount > 0) {
                    ReportCard(title = "Transfers") {
                        TransferSummarySection(total = uiState.transferTotal, count = uiState.transferCount)
                    }
                }

                // 7. Friends Summary
                ReportCard(title = "Friends Summary") {
                    FriendsSummarySection(summary = uiState.friendsSummary)
                }

                // 8. Budget vs Reality
                BudgetRealitySection()
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isCurrency) formatCurrency(amount) else amount.toInt().toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
        // A fixed-height track for fillMaxHeight(fraction) to resolve against — without it, this
        // Column sizes to wrap its own content (bar + label), so the bar's requested fraction has
        // no concrete height to be a fraction OF and every bar collapses to the same minimal size.
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(110.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(height.coerceAtLeast(0.05f))
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

/** Category color palette shared by the donut chart and its ranked list below, so a category's
 *  dot color always matches its arc — the two views are one visual, not two disconnected ones. */
val categoryChartColors = listOf(
    Color(0xFF2196F3),
    Color(0xFF4CAF50),
    Color(0xFFFFC107),
    Color(0xFF9C27B0),
    Color(0xFFF44336),
    Color(0xFF00BCD4),
    Color(0xFFFF9800)
)

@Composable
fun CategoryBreakdownSection(reports: List<CategoryReport>) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(150.dp)) {
                var startAngle = -90f
                reports.forEachIndexed { index, report ->
                    val sweepAngle = report.percentage * 360f
                    drawArc(
                        color = categoryChartColors[index % categoryChartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 40f, cap = StrokeCap.Round)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            reports.forEachIndexed { index, report ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryChartColors[index % categoryChartColors.size])
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = report.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatCurrency(report.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(report.percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
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
                    Text(text = report.method, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = formatCurrency(report.amount), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
fun TransferSummarySection(total: Double, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$count transfer${if (count == 1) "" else "s"} between your own accounts",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(text = formatCurrency(total), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FriendsSummarySection(summary: FriendsReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Friends Owe You", color = Color.Gray)
            Text(text = formatCurrency(summary.friendsOweYou), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "You Owe Friends", color = Color.Gray)
            Text(text = formatCurrency(summary.youOweFriends), color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Outstanding Balance", color = MaterialTheme.colorScheme.onSurface)
            Text(text = formatCurrency(summary.outstandingBalance), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                Text(text = insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(text = "No report data available.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
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
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
            Text(text = "Spent: ${formatCurrency(spent)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(text = "Limit: ${formatCurrency(limit)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

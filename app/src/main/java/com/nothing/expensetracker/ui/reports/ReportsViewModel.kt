package com.nothing.expensetracker.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.local.FriendBalance
import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.ui.history.TransactionConstants
import com.nothing.expensetracker.util.formatCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

enum class DateFilter(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

data class SummaryData(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val savings: Double = 0.0,
    val transactionCount: Int = 0
)

data class CategoryReport(
    val name: String,
    val amount: Double,
    val percentage: Float
)

data class PaymentMethodReport(
    val method: String,
    val amount: Double,
    val percentage: Float
)

data class FriendsReport(
    val friendsOweYou: Double = 0.0,
    val youOweFriends: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    val friendTransactionCount: Int = 0
)

data class ReportsUiState(
    val dateFilter: DateFilter = DateFilter.THIS_MONTH,
    val customDateRange: Pair<Long, Long>? = null,
    val summary: SummaryData = SummaryData(),
    val categoryReports: List<CategoryReport> = emptyList(),
    val paymentMethodReports: List<PaymentMethodReport> = emptyList(),
    val friendsSummary: FriendsReport = FriendsReport(),
    val topCategories: List<CategoryReport> = emptyList(),
    val transferTotal: Double = 0.0,
    val transferCount: Int = 0,
    val insights: List<String> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _dateFilter = MutableStateFlow(DateFilter.THIS_MONTH)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)

    val uiState: StateFlow<ReportsUiState> = combine(
        _dateFilter,
        _customDateRange
    ) { filter, customRange ->
        filter to customRange
    }.flatMapLatest { (filter, customRange) ->
        val range = calculateTimeRange(filter, customRange)
        combine(
            repository.getFilteredExpenses(
                query = "",
                type = "All",
                method = "All",
                category = "All",
                sort = "NEWEST",
                startTime = range.first,
                endTime = range.second
            ),
            // Friend balances are always all-time — a friend still owes you regardless of which
            // date filter Reports happens to be showing right now.
            repository.getFriendBalances()
        ) { expenses, friendBalances ->
            calculateReports(expenses, filter, friendBalances)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun setCustomDateRange(start: Long, end: Long) {
        _customDateRange.value = start to end
        _dateFilter.value = DateFilter.CUSTOM
    }

    private fun calculateTimeRange(filter: DateFilter, customRange: Pair<Long, Long>?): Pair<Long, Long> {
        val now = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startOfDay = now.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = now.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        return when (filter) {
            DateFilter.TODAY -> Pair(startOfDay, endOfDay)
            DateFilter.THIS_WEEK -> {
                val start = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                Pair(start, endOfDay)
            }
            DateFilter.THIS_MONTH -> {
                val start = now.with(TemporalAdjusters.firstDayOfMonth())
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                Pair(start, endOfDay)
            }
            DateFilter.THIS_YEAR -> {
                val start = now.with(TemporalAdjusters.firstDayOfYear())
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                Pair(start, endOfDay)
            }
            DateFilter.CUSTOM -> customRange ?: Pair(0L, Long.MAX_VALUE)
        }
    }

    private suspend fun calculateReports(
        expenses: List<Expense>,
        filter: DateFilter,
        friendBalances: List<FriendBalance>
    ): ReportsUiState {
        val friendsSummary = buildFriendsSummary(friendBalances)

        if (expenses.isEmpty()) {
            return ReportsUiState(dateFilter = filter, friendsSummary = friendsSummary, isLoading = false)
        }

        var totalIncome = 0.0
        var totalExpense = 0.0
        val categoryMap = mutableMapOf<String, Double>()
        val methodMap = mutableMapOf<String, Double>()
        var transferTotal = 0.0
        var transferCount = 0

        expenses.forEach { expense ->
            val isNonSpending = TransactionConstants.isNonSpendingCategory(expense.type, expense.category)
            if (isNonSpending) {
                transferTotal += expense.amount
                transferCount++
            } else if (expense.type == "Credit") {
                totalIncome += expense.amount
            } else {
                totalExpense += expense.amount
                categoryMap[expense.category] = categoryMap.getOrDefault(expense.category, 0.0) + expense.amount
                methodMap[expense.paymentMethod] = methodMap.getOrDefault(expense.paymentMethod, 0.0) + expense.amount
            }
        }

        val summary = SummaryData(
            income = totalIncome,
            expense = totalExpense,
            savings = totalIncome - totalExpense,
            transactionCount = expenses.size
        )

        val categoryReports = categoryMap.map { (name, amount) ->
            CategoryReport(name, amount, if (totalExpense > 0) (amount / totalExpense).toFloat() else 0f)
        }.sortedByDescending { it.amount }

        val methodReports = methodMap.map { (method, amount) ->
            PaymentMethodReport(method, amount, if (totalExpense > 0) (amount / totalExpense).toFloat() else 0f)
        }.sortedByDescending { it.amount }

        val monthOverMonthInsight = if (filter == DateFilter.THIS_MONTH) {
            buildMonthOverMonthInsight(categoryReports)
        } else null

        val insights = generateInsights(summary, categoryReports, methodReports, friendsSummary, monthOverMonthInsight)

        return ReportsUiState(
            dateFilter = filter,
            summary = summary,
            categoryReports = categoryReports,
            paymentMethodReports = methodReports,
            friendsSummary = friendsSummary,
            topCategories = categoryReports.take(5),
            transferTotal = transferTotal,
            transferCount = transferCount,
            insights = insights,
            isLoading = false
        )
    }

    /**
     * Friends Summary is always the true, all-time outstanding balance — independent of whatever
     * date filter Reports is showing — since "who owes who" doesn't reset when you switch to
     * "This Week". Nets each friend individually before aggregating so one friend you owe doesn't
     * cancel out against another friend who owes you.
     */
    private fun buildFriendsSummary(friendBalances: List<FriendBalance>): FriendsReport {
        var friendsOweYou = 0.0
        var youOweFriends = 0.0
        friendBalances.forEach { balance ->
            if (balance.outstandingBalance > 0) {
                friendsOweYou += balance.outstandingBalance
            } else if (balance.outstandingBalance < 0) {
                youOweFriends += -balance.outstandingBalance
            }
        }
        return FriendsReport(
            friendsOweYou = friendsOweYou,
            youOweFriends = youOweFriends,
            outstandingBalance = friendsOweYou - youOweFriends,
            friendTransactionCount = friendBalances.size
        )
    }

    /**
     * Compares the current month's top-spending category against the same category last month.
     * Returns null when there's nothing to compare (e.g. brand new category, no prior spending).
     */
    private suspend fun buildMonthOverMonthInsight(categoryReports: List<CategoryReport>): String? {
        val topCategory = categoryReports.firstOrNull() ?: return null

        val previousMonth = LocalDate.now().minusMonths(1)
        val previousMonthStr = String.format(Locale.ROOT, "%02d", previousMonth.monthValue)
        val previousYearStr = previousMonth.year.toString()
        val previousTotals = repository.getExpensesByCategoryFiltered(previousMonthStr, previousYearStr).first()
        val previousAmount = previousTotals.find { it.category == topCategory.name }?.totalAmount ?: 0.0

        return when {
            previousAmount <= 0.0 -> null
            else -> {
                val changePercent = ((topCategory.amount - previousAmount) / previousAmount) * 100
                val rounded = kotlin.math.abs(changePercent).toInt()
                when {
                    rounded == 0 -> "Your ${topCategory.name} spending is about the same as last month."
                    changePercent > 0 -> "You spent $rounded% more on ${topCategory.name} this month than last."
                    else -> "You spent $rounded% less on ${topCategory.name} this month than last."
                }
            }
        }
    }

    private fun generateInsights(
        summary: SummaryData,
        categories: List<CategoryReport>,
        methods: List<PaymentMethodReport>,
        friends: FriendsReport,
        monthOverMonthInsight: String?
    ): List<String> {
        val insights = mutableListOf<String>()

        monthOverMonthInsight?.let { insights.add(it) }

        if (categories.isNotEmpty()) {
            insights.add("You spent the most on ${categories.first().name} this period.")
        }

        if (methods.isNotEmpty()) {
            insights.add("${methods.first().method} is your most used payment method.")
        }

        if (summary.savings > 0) {
            insights.add("You saved ${formatCurrency(summary.savings)} this period.")
        } else if (summary.savings < 0) {
            insights.add("Your expenses exceeded your income by ${formatCurrency(-summary.savings)}.")
        }

        if (friends.outstandingBalance > 0) {
            insights.add("Friends owe you ${formatCurrency(friends.outstandingBalance)}.")
        } else if (friends.outstandingBalance < 0) {
            insights.add("You owe friends ${formatCurrency(-friends.outstandingBalance)}.")
        }

        return insights
    }
}

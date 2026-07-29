package com.nothing.expensetracker.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
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
        repository.getFilteredExpenses(
            query = "",
            type = "All",
            method = "All",
            category = "All",
            sort = "NEWEST",
            startTime = range.first,
            endTime = range.second
        ).map { expenses ->
            calculateReports(expenses, filter)
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

    private fun calculateReports(expenses: List<Expense>, filter: DateFilter): ReportsUiState {
        if (expenses.isEmpty()) {
            return ReportsUiState(dateFilter = filter, isLoading = false)
        }

        var totalIncome = 0.0
        var totalExpense = 0.0
        val categoryMap = mutableMapOf<String, Double>()
        val methodMap = mutableMapOf<String, Double>()
        
        var friendsOweYou = 0.0
        var youOweFriends = 0.0
        var friendTransactionCount = 0

        expenses.forEach { expense ->
            if (expense.type == "Credit") {
                totalIncome += expense.amount
            } else {
                totalExpense += expense.amount
                categoryMap[expense.category] = categoryMap.getOrDefault(expense.category, 0.0) + expense.amount
                methodMap[expense.paymentMethod] = methodMap.getOrDefault(expense.paymentMethod, 0.0) + expense.amount
            }

            if (expense.category == "Friends") {
                friendTransactionCount++
                if (expense.type == "Debit") {
                    friendsOweYou += expense.amount
                } else {
                    youOweFriends += expense.amount
                }
            }
        }

        val summary = SummaryData(
            income = totalIncome,
            expense = totalExpense,
            savings = totalIncome - totalExpense,
            transactionCount = expenses.size
        )

        val categoryReports = categoryMap.map { (name, amount) ->
            CategoryReport(name, amount, (amount / totalExpense).toFloat())
        }.sortedByDescending { it.amount }

        val methodReports = methodMap.map { (method, amount) ->
            PaymentMethodReport(method, amount, (amount / totalExpense).toFloat())
        }.sortedByDescending { it.amount }

        val friendsSummary = FriendsReport(
            friendsOweYou = friendsOweYou,
            youOweFriends = youOweFriends,
            outstandingBalance = friendsOweYou - youOweFriends,
            friendTransactionCount = friendTransactionCount
        )

        val insights = generateInsights(summary, categoryReports, methodReports, friendsSummary)

        return ReportsUiState(
            dateFilter = filter,
            summary = summary,
            categoryReports = categoryReports,
            paymentMethodReports = methodReports,
            friendsSummary = friendsSummary,
            topCategories = categoryReports.take(5),
            insights = insights,
            isLoading = false
        )
    }

    private fun generateInsights(
        summary: SummaryData,
        categories: List<CategoryReport>,
        methods: List<PaymentMethodReport>,
        friends: FriendsReport
    ): List<String> {
        val insights = mutableListOf<String>()

        if (categories.isNotEmpty()) {
            insights.add("You spent the most on ${categories.first().name} this period.")
        }

        if (methods.isNotEmpty()) {
            insights.add("${methods.first().method} is your most used payment method.")
        }

        if (summary.savings > 0) {
            insights.add("You saved ₹${summary.savings.toInt()} this period.")
        } else if (summary.savings < 0) {
            insights.add("Your expenses exceeded your income by ₹${(-summary.savings).toInt()}.")
        }

        if (friends.outstandingBalance > 0) {
            insights.add("Friends owe you ₹${friends.outstandingBalance.toInt()}.")
        } else if (friends.outstandingBalance < 0) {
            insights.add("You owe friends ₹${(-friends.outstandingBalance).toInt()}.")
        }

        return insights
    }
}

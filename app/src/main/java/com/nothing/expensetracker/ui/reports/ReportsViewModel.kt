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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

/** How many months the trend chart covers. Six fits on a phone without crowding. */
private const val TREND_MONTHS = 6

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
    /** Net spending: [debitAmount] minus [creditAmount]. */
    val amount: Double,
    val percentage: Float,
    /** Gross spending filed under this category. */
    val debitAmount: Double = 0.0,
    /** Net spend on the same category in the preceding period, for the change indicator. */
    val previousAmount: Double = 0.0,
    /** Money received back under the same category (refunds, reimbursements, shared costs).
     *  Non-zero only for a category linked across Debit and Credit. */
    val creditAmount: Double = 0.0
) {
    /** True when both sides carry a value, so the UI can show how the net was reached. */
    val isNetted: Boolean get() = creditAmount > 0.0 && debitAmount > 0.0

    /**
     * Percent change against the preceding period, or null when there is nothing to compare —
     * a brand new category, or no spending on it last time. Showing "+100%" for a category that
     * simply did not exist before would be noise dressed up as insight.
     */
    val changePercent: Int?
        get() = if (previousAmount <= 0.0) null
        else (((amount - previousAmount) / previousAmount) * 100).toInt()
}

/**
 * One month on the trend chart.
 *
 * The report was previously a snapshot of a single period with nothing to compare it against,
 * which is the question a finance report actually has to answer: is this normal for me?
 */
data class TrendPoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val isCurrent: Boolean = false
) {
    val net: Double get() = income - expense
}

/**
 * How fast money is going out, and where that lands if nothing changes.
 *
 * Only meaningful for a period that is still running — projecting a finished month would just
 * restate its total.
 */
data class SpendingPace(
    val dailyAverage: Double = 0.0,
    val projectedTotal: Double = 0.0,
    val daysElapsed: Int = 0,
    val daysInPeriod: Int = 0
) {
    val isPartial: Boolean get() = daysElapsed in 1 until daysInPeriod

    /**
     * Whether the projection is worth printing yet.
     *
     * A month front-loaded with rent makes the daily average meaningless for the first few days:
     * on the 2nd of a month holding one rent payment the projection read ten times the real
     * figure. Under a fifth of the period the number is withheld rather than shown wrong - the
     * daily average and the day counter still say everything that is actually known.
     */
    val isProjectionMeaningful: Boolean get() = daysInPeriod > 0 && daysElapsed * 5 >= daysInPeriod
}

/** The same figures for the preceding period, so every headline can carry a direction. */
data class PeriodComparison(
    val previousIncome: Double = 0.0,
    val previousExpense: Double = 0.0,
    val label: String = ""
) {
    val hasData: Boolean get() = previousIncome > 0.0 || previousExpense > 0.0

    /** Percentage change in spending. Positive means more was spent than last time. */
    fun expenseChange(current: Double): Int? {
        if (previousExpense <= 0.0) return null
        return (((current - previousExpense) / previousExpense) * 100).toInt()
    }

    fun incomeChange(current: Double): Int? {
        if (previousIncome <= 0.0) return null
        return (((current - previousIncome) / previousIncome) * 100).toInt()
    }
}

/** Spending by day of the week, aggregated across the selected period. */
data class WeekdayPoint(
    val label: String,
    val amount: Double,
    val isHeaviest: Boolean = false
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
    val trend: List<TrendPoint> = emptyList(),
    val pace: SpendingPace = SpendingPace(),
    val comparison: PeriodComparison = PeriodComparison(),
    val weekdayPattern: List<WeekdayPoint> = emptyList(),
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
            repository.getFriendBalances(),
            // All-time rows, so the trend chart and the previous-period comparison can be derived
            // here rather than issuing a second round of date-ranged queries.
            repository.getAllExpenses()
        ) { expenses, friendBalances, allExpenses ->
            calculateReports(expenses, filter, friendBalances, allExpenses, range)
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
        friendBalances: List<FriendBalance>,
        allExpenses: List<Expense>,
        range: Pair<Long, Long>
    ): ReportsUiState {
        val friendsSummary = buildFriendsSummary(friendBalances)
        val trend = buildTrend(allExpenses)

        if (expenses.isEmpty()) {
            return ReportsUiState(
                dateFilter = filter,
                friendsSummary = friendsSummary,
                trend = trend,
                isLoading = false
            )
        }

        var totalIncome = 0.0
        var totalExpense = 0.0
        val categoryMap = mutableMapOf<String, Double>()
        val creditByCategory = mutableMapOf<String, Double>()
        val methodMap = mutableMapOf<String, Double>()
        var transferTotal = 0.0
        var transferCount = 0

        // Categories the user has linked across both sides. Credit filed under one of these is
        // money coming back against that category's spending (a refund, a reimbursement, a shared
        // cost), so it's subtracted from that category below rather than left as plain income.
        val linkedCategoryNames = repository.getCategories().first()
            .filter { it.isLinked }
            .map { it.name }
            .toSet()

        expenses.forEach { expense ->
            val isNonSpending = TransactionConstants.isNonSpendingCategory(expense.type, expense.category)
            if (isNonSpending) {
                transferTotal += expense.amount
                transferCount++
            } else if (expense.type == "Credit") {
                totalIncome += expense.amount
                if (expense.category in linkedCategoryNames) {
                    creditByCategory[expense.category] =
                        creditByCategory.getOrDefault(expense.category, 0.0) + expense.amount
                }
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

        // Net each linked category, then rebase the percentages on the netted total so the
        // breakdown still adds up to 100%.
        val nettedTotals = (categoryMap.keys + creditByCategory.keys).associateWith { name ->
            val debit = categoryMap.getOrDefault(name, 0.0)
            val credit = creditByCategory.getOrDefault(name, 0.0)
            // A category that earned back more than it cost is clamped at zero rather than shown
            // as negative spending, which would distort the share-of-spending bars.
            Triple(debit, credit, (debit - credit).coerceAtLeast(0.0))
        }
        val nettedExpenseTotal = nettedTotals.values.sumOf { it.third }

        // Previous-period figures, derived from the all-time list rather than re-queried.
        val previousRange = previousTimeRange(filter, range)
        val previousExpenses = allExpenses.filter {
            it.timestamp >= previousRange.first && it.timestamp <= previousRange.second
        }
        val previousByCategory = netSpendByCategory(previousExpenses, linkedCategoryNames)
        val comparison = PeriodComparison(
            previousIncome = previousExpenses
                .filter { it.type == "Credit" && !TransactionConstants.isNonSpendingCategory(it.type, it.category) }
                .sumOf { it.amount },
            previousExpense = previousByCategory.values.sum(),
            label = previousPeriodLabel(filter)
        )

        val categoryReports = nettedTotals
            .filter { (_, totals) -> totals.first > 0.0 }
            .map { (name, totals) ->
                val (debit, credit, net) = totals
                CategoryReport(
                    name = name,
                    amount = net,
                    percentage = if (nettedExpenseTotal > 0) (net / nettedExpenseTotal).toFloat() else 0f,
                    debitAmount = debit,
                    creditAmount = credit,
                    previousAmount = previousByCategory[name] ?: 0.0
                )
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
            trend = trend,
            comparison = comparison,
            pace = buildPace(nettedExpenseTotal, range, filter),
            weekdayPattern = buildWeekdayPattern(expenses),
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
    /**
     * Net spend per category, used for the previous period so each row can show a direction.
     *
     * Mirrors the netting rule applied to the current period: credit filed under a linked
     * category offsets that category's spending rather than counting as plain income, so the
     * two periods are measured the same way and the comparison means something.
     */
    private fun netSpendByCategory(
        expenses: List<Expense>,
        linkedCategoryNames: Set<String>
    ): Map<String, Double> {
        val debit = mutableMapOf<String, Double>()
        val credit = mutableMapOf<String, Double>()
        expenses.forEach { expense ->
            if (TransactionConstants.isNonSpendingCategory(expense.type, expense.category)) return@forEach
            if (expense.type == "Credit") {
                if (expense.category in linkedCategoryNames) {
                    credit[expense.category] = credit.getOrDefault(expense.category, 0.0) + expense.amount
                }
            } else {
                debit[expense.category] = debit.getOrDefault(expense.category, 0.0) + expense.amount
            }
        }
        return debit.mapValues { (name, amount) ->
            (amount - credit.getOrDefault(name, 0.0)).coerceAtLeast(0.0)
        }
    }

    /** Income and spending per month for the last [TREND_MONTHS] months, oldest first. */
    private fun buildTrend(allExpenses: List<Expense>): List<TrendPoint> {
        val zoneId = ZoneId.systemDefault()
        val thisMonth = java.time.YearMonth.now()
        val months = (0 until TREND_MONTHS).map { thisMonth.minusMonths((TREND_MONTHS - 1 - it).toLong()) }

        val income = mutableMapOf<java.time.YearMonth, Double>()
        val expense = mutableMapOf<java.time.YearMonth, Double>()
        allExpenses.forEach { row ->
            if (TransactionConstants.isNonSpendingCategory(row.type, row.category)) return@forEach
            val month = java.time.YearMonth.from(
                Instant.ofEpochMilli(row.timestamp).atZone(zoneId).toLocalDate()
            )
            if (row.type == "Credit") {
                income[month] = income.getOrDefault(month, 0.0) + row.amount
            } else {
                expense[month] = expense.getOrDefault(month, 0.0) + row.amount
            }
        }

        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        return months.map { month ->
            TrendPoint(
                label = month.format(formatter),
                income = income.getOrDefault(month, 0.0),
                expense = expense.getOrDefault(month, 0.0),
                isCurrent = month == thisMonth
            )
        }
    }

    /**
     * Daily burn rate and where it lands by the end of the period.
     *
     * Deliberately linear: it assumes the rest of the period looks like the part already spent.
     * That is wrong for a month with rent on the 1st, which is exactly why the daily average is
     * shown alongside — the projection is a prompt to look, not a forecast to trust.
     */
    private fun buildPace(spentSoFar: Double, range: Pair<Long, Long>, filter: DateFilter): SpendingPace {
        val zoneId = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(range.first).atZone(zoneId).toLocalDate()
        val today = LocalDate.now()

        // Length of the whole period, not of the queried range. calculateTimeRange deliberately
        // ends at the end of *today* so the report never includes future-dated rows, which means
        // its span is "days so far" rather than "days in the month" - using it here made a month
        // two days into itself look like a two-day period that was already over.
        val daysInPeriod = when (filter) {
            DateFilter.TODAY -> 1
            DateFilter.THIS_WEEK -> 7
            DateFilter.THIS_MONTH -> today.lengthOfMonth()
            DateFilter.THIS_YEAR -> today.lengthOfYear()
            DateFilter.CUSTOM -> {
                val end = Instant.ofEpochMilli(range.second).atZone(zoneId).toLocalDate()
                (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt()
            }
        }
        val daysElapsed = (java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1)
            .toInt().coerceIn(1, daysInPeriod)

        if (daysInPeriod <= 1) return SpendingPace()

        val dailyAverage = spentSoFar / daysElapsed
        return SpendingPace(
            dailyAverage = dailyAverage,
            projectedTotal = dailyAverage * daysInPeriod,
            daysElapsed = daysElapsed,
            daysInPeriod = daysInPeriod
        )
    }

    /** Spending by day of the week across the selected period. */
    private fun buildWeekdayPattern(expenses: List<Expense>): List<WeekdayPoint> {
        val zoneId = ZoneId.systemDefault()
        val totals = mutableMapOf<java.time.DayOfWeek, Double>()
        expenses.forEach { row ->
            if (row.type != "Debit") return@forEach
            if (TransactionConstants.isNonSpendingCategory(row.type, row.category)) return@forEach
            val day = Instant.ofEpochMilli(row.timestamp).atZone(zoneId).dayOfWeek
            totals[day] = totals.getOrDefault(day, 0.0) + row.amount
        }
        if (totals.isEmpty()) return emptyList()

        val heaviest = totals.maxByOrNull { it.value }?.key
        return java.time.DayOfWeek.entries.map { day ->
            WeekdayPoint(
                label = day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                amount = totals.getOrDefault(day, 0.0),
                isHeaviest = day == heaviest && totals.getOrDefault(day, 0.0) > 0.0
            )
        }
    }

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

/**
 * The same window one period earlier, truncated to the same point in that period.
 *
 * A report for "this month" opened on the 2nd covers two days. Measured against a *complete*
 * previous month it read as a collapse — "income down 10% vs last month" when nothing had gone
 * wrong and the month had simply just started. So the previous window stops at the same offset
 * into its own period, down to the same time of day, which is what makes "Today vs yesterday"
 * fair as well: at 10am there is no sense in weighing one morning against a whole day.
 *
 * Clamped so a long month never spills past the end of a short one, and stepped back by calendar
 * rather than by a fixed number of days — 31 against 28 would read as an increase that never
 * happened.
 *
 * Top level and [now]-injectable so the arithmetic can be tested without a ViewModel.
 */
internal fun previousTimeRange(
    filter: DateFilter,
    range: Pair<Long, Long>,
    now: ZonedDateTime = ZonedDateTime.now()
): Pair<Long, Long> {
    // A custom range has no natural predecessor, so the same span immediately before it. Handled
    // first because an unset custom range is (0, Long.MAX_VALUE), which no date maths survives.
    if (filter == DateFilter.CUSTOM) {
        val span = range.second - range.first
        return (range.first - span - 1) to (range.first - 1)
    }

    val zoneId = now.zone
    val today = now.toLocalDate()
    val startOfToday = today.atStartOfDay(zoneId)
    val elapsedToday = Duration.between(startOfToday, now).toMillis().coerceAtLeast(0)

    // Days completed plus the one in progress, counted from the start of the selected period.
    // Taken from today rather than from range.second, which deliberately runs to the end of
    // today and so is partly in the future.
    val periodStart = Instant.ofEpochMilli(range.first).atZone(zoneId).toLocalDate()
    val elapsedDays = ChronoUnit.DAYS.between(periodStart, today).coerceAtLeast(0)

    fun window(start: LocalDate, periodEnd: LocalDate): Pair<Long, Long> {
        val sameOffset = start.plusDays(elapsedDays)
        val lastDay = minOf(sameOffset, periodEnd)
        val from = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfLastDay = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        // Stop at the same time of day as now, unless the clamp already cut the window short
        // (today is the 31st, last month ended on the 30th) — then its final day counts whole.
        val to = if (lastDay < sameOffset) endOfLastDay else minOf(
            lastDay.atStartOfDay(zoneId).toInstant().toEpochMilli() + elapsedToday,
            endOfLastDay
        )
        return from to to
    }

    return when (filter) {
        DateFilter.TODAY -> today.minusDays(1).let { window(it, it) }
        DateFilter.THIS_WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
            window(start, start.plusDays(6))
        }
        DateFilter.THIS_MONTH -> {
            val start = today.with(TemporalAdjusters.firstDayOfMonth()).minusMonths(1)
            window(start, start.with(TemporalAdjusters.lastDayOfMonth()))
        }
        DateFilter.THIS_YEAR -> {
            val start = today.with(TemporalAdjusters.firstDayOfYear()).minusYears(1)
            window(start, start.with(TemporalAdjusters.lastDayOfYear()))
        }
        DateFilter.CUSTOM -> error("handled above")
    }
}

/**
 * What the change chips compare against.
 *
 * The wording carries "to date" because [previousTimeRange] truncates the previous window to the
 * same point in its period. A chip that said plainly "vs last month" while comparing two days
 * against thirty-one was the misleading part, not the arithmetic.
 */
internal fun previousPeriodLabel(filter: DateFilter): String = when (filter) {
    DateFilter.TODAY -> "yesterday so far"
    DateFilter.THIS_WEEK -> "last week to date"
    DateFilter.THIS_MONTH -> "last month to date"
    DateFilter.THIS_YEAR -> "last year to date"
    DateFilter.CUSTOM -> "the previous period"
}

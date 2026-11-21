package com.nothing.expensetracker.feature.autopay

import com.nothing.expensetracker.data.local.AutopayRule
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM")

/**
 * How far back a rule will catch up, counting the month being checked.
 *
 * A gap of a month or two is the ordinary case - the phone was off, or Android stopped the app -
 * and those months really were owed. Beyond that it is far more likely that the clock is wrong or
 * a long-dead rule has come back, and inventing a year of rent is worse than missing it. Older
 * months are skipped; see §14.5 of KNOWN_ISSUES.md.
 */
const val MAX_CATCH_UP_MONTHS = 3

/** The "yyyy-MM" stamp a rule carries in [AutopayRule.lastRunMonth] once it has fired. */
fun monthKeyOf(date: LocalDate): String = date.format(MONTH_KEY)

private fun parseMonthKey(key: String?): YearMonth? {
    if (key.isNullOrBlank()) return null
    return try {
        YearMonth.parse(key, MONTH_KEY)
    } catch (e: Exception) {
        // The stamp round-trips through the spreadsheet, where anything at all can be typed into
        // the cell. An unreadable stamp means "no record of it running".
        null
    }
}

/**
 * Every day a rule still owes a transaction for, oldest first. Empty when nothing is due.
 *
 * Two things this answers that the old check could not.
 *
 * **A month that was missed is not lost.** [AutopayRule.lastRunMonth] is a single stamp, and the
 * check was only ever "has this month's due day arrived". If the worker never ran in October - the
 * phone off, or Android having stopped the app - November fired, stamped itself, and October was
 * gone without a trace. Every month between the last run and now is now accounted for.
 *
 * **A rule does not fire for a month it did not exist in.** A new rule has never run, so before
 * [AutopayRule.createdAt] was consulted a rule set up on the 20th for "day 1" was immediately
 * overdue, and the worker runs on every app launch: the transaction appeared within minutes of
 * saving a rule meant to start next month. Created *on* the due day still fires - that is someone
 * setting up today's payment today.
 *
 * A `createdAt` of zero means the day it was made is not known: a rule restored from the
 * spreadsheet, which does not record it. Nothing is held back, because a restored rule is an old
 * one - but neither is anything back-filled from a history that was never stored.
 *
 * @param today the day being checked
 * @param zone the zone to read dates in, so "which day was it made" follows the wall clock rather
 *   than UTC
 * @param maxCatchUp see [MAX_CATCH_UP_MONTHS]
 */
fun autopayDueDates(
    rule: AutopayRule,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    maxCatchUp: Int = MAX_CATCH_UP_MONTHS
): List<LocalDate> {
    if (!rule.isActive) return emptyList()

    val thisMonth = YearMonth.from(today)
    val lastRun = parseMonthKey(rule.lastRunMonth)
    val created =
        if (rule.createdAt == 0L) null
        else Instant.ofEpochMilli(rule.createdAt).atZone(zone).toLocalDate()

    var from = when {
        lastRun != null -> lastRun.plusMonths(1)
        created != null -> YearMonth.from(created)
        else -> thisMonth
    }
    val floor = thisMonth.minusMonths((maxCatchUp - 1).coerceAtLeast(0).toLong())
    if (from.isBefore(floor)) from = floor
    if (from.isAfter(thisMonth)) return emptyList()

    val due = ArrayList<LocalDate>()
    var month = from
    while (!month.isAfter(thisMonth)) {
        // Day 31 in a 30-day month means the last day of that month.
        val dueDate = month.atDay(rule.dayOfMonth.coerceIn(1, month.lengthOfMonth()))
        val notYet = dueDate.isAfter(today)
        val beforeTheRuleExisted = created != null && dueDate.isBefore(created)
        if (!notYet && !beforeTheRuleExisted) due.add(dueDate)
        month = month.plusMonths(1)
    }
    return due
}

/** Whether a rule owes anything at all today. */
fun isAutopayDue(
    rule: AutopayRule,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): Boolean = autopayDueDates(rule, today, zone).isNotEmpty()

/**
 * The stamp to give a rule that is being switched back on.
 *
 * Catching up missed months has a sharp edge: a rule paused in January and resumed in November
 * would count all ten months as owed and create them in one go. Turning a rule back on resumes it
 * from now, so the stamp moves forward to last month - and only ever forward, so a rule switched
 * off and on again in a month it has already fired in does not fire twice.
 */
fun resumeMonthKey(lastRunMonth: String?, today: LocalDate): String {
    val lastMonth = monthKeyOf(today.minusMonths(1))
    // "yyyy-MM" sorts lexicographically the same way it runs chronologically.
    return if (lastRunMonth != null && lastRunMonth > lastMonth) lastRunMonth else lastMonth
}

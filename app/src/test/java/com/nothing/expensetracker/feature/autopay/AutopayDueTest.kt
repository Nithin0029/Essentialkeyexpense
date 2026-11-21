package com.nothing.expensetracker.feature.autopay

import com.nothing.expensetracker.data.local.AutopayRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What a rule owes, and when.
 *
 * Two defects met here. A rule set up today fired for a day that had already passed, because a new
 * rule has never run and the check only asked whether the due day had arrived. And a month the
 * worker never ran in was lost outright, because [AutopayRule.lastRunMonth] records one month and
 * the next firing overwrote it.
 */
class AutopayDueTest {

    private val ist = ZoneId.of("Asia/Kolkata")

    private fun rule(
        dayOfMonth: Int,
        created: LocalDate?,
        lastRunMonth: String? = null,
        isActive: Boolean = true
    ) = AutopayRule(
        id = 1,
        amount = 15000.0,
        description = "Rent",
        category = "Home",
        type = "Debit",
        paymentMethod = "SBI",
        dayOfMonth = dayOfMonth,
        isActive = isActive,
        lastRunMonth = lastRunMonth,
        // Zero is the "not known" case: a rule restored from the spreadsheet, which does not
        // record the day it was made.
        createdAt = created?.atTime(LocalTime.of(11, 0))?.atZone(ist)?.toInstant()?.toEpochMilli()
            ?: 0L
    )

    private fun due(rule: AutopayRule, today: LocalDate) = autopayDueDates(rule, today, ist)

    private fun days(vararg dates: LocalDate) = dates.toList()

    private val nothing = emptyList<LocalDate>()

    // ---------------------------------------------------------------- a rule that is new

    @Test
    fun `a rule created after the due day waits for next month`() {
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 11, 20))

        assertEquals(
            "must not fire for a day that passed before the rule existed",
            nothing, due(r, LocalDate.of(2026, 11, 20))
        )
        assertEquals(nothing, due(r, LocalDate.of(2026, 11, 30)))
        assertEquals(
            "next month is the one it was made for",
            days(LocalDate.of(2026, 12, 1)), due(r, LocalDate.of(2026, 12, 1))
        )
    }

    @Test
    fun `a rule created on its due day fires that day`() {
        // Setting up today's payment today is not a surprise - it is the point.
        val r = rule(dayOfMonth = 20, created = LocalDate.of(2026, 11, 20))
        assertEquals(days(LocalDate.of(2026, 11, 20)), due(r, LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun `a rule created before its due day fires on it, the same month`() {
        val r = rule(dayOfMonth = 25, created = LocalDate.of(2026, 11, 20))

        assertEquals("not before the day", nothing, due(r, LocalDate.of(2026, 11, 24)))
        assertEquals(days(LocalDate.of(2026, 11, 25)), due(r, LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun `a rule never run only owes from the month it was created in`() {
        // Created on the 10th of September, due on the 1st: September had already gone by.
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 9, 10))

        assertEquals(
            days(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
            due(r, LocalDate.of(2026, 11, 21))
        )
    }

    // ---------------------------------------------------------------- months that were missed

    @Test
    fun `a day it missed is still caught later in the month`() {
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 1, 1), lastRunMonth = "2026-10")
        assertEquals(days(LocalDate.of(2026, 11, 1)), due(r, LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun `a month the worker never ran in is caught up, not skipped`() {
        // The phone was off through September and October. Both were owed, and both used to be
        // lost: November would fire, stamp itself, and nothing recorded that they had not.
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 1, 1), lastRunMonth = "2026-08")

        assertEquals(
            days(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
            due(r, LocalDate.of(2026, 11, 21))
        )
    }

    @Test
    fun `catching up does not reach into a month that is not due yet`() {
        val r = rule(dayOfMonth = 25, created = LocalDate.of(2026, 1, 1), lastRunMonth = "2026-08")

        assertEquals(
            days(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 25)),
            due(r, LocalDate.of(2026, 11, 21))
        )
    }

    @Test
    fun `the catch-up is capped, so a wrong clock cannot invent a year of payments`() {
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2024, 1, 1), lastRunMonth = "2025-01")

        val caught = due(r, LocalDate.of(2026, 11, 21))
        assertEquals(MAX_CATCH_UP_MONTHS, caught.size)
        assertEquals(
            days(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
            caught
        )
    }

    @Test
    fun `a rule that already fired this month does not fire again`() {
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 10, 1), lastRunMonth = "2026-11")

        assertEquals(nothing, due(r, LocalDate.of(2026, 11, 21)))
        assertEquals(
            "and is free again in December",
            days(LocalDate.of(2026, 12, 1)), due(r, LocalDate.of(2026, 12, 1))
        )
    }

    // ---------------------------------------------------------------- odds and ends

    @Test
    fun `day 31 means the last day of a shorter month`() {
        val r = rule(dayOfMonth = 31, created = LocalDate.of(2026, 1, 5), lastRunMonth = "2026-10")

        assertEquals(nothing, due(r, LocalDate.of(2026, 11, 29)))
        assertEquals(
            "November ends on the 30th",
            days(LocalDate.of(2026, 11, 30)), due(r, LocalDate.of(2026, 11, 30))
        )
    }

    @Test
    fun `an inactive rule never fires`() {
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 10, 1), isActive = false)
        assertEquals(nothing, due(r, LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `a clock moved backwards does not fire a rule from the future`() {
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 12, 15))
        assertEquals(nothing, due(r, LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `an unreadable last-run stamp is treated as never having run`() {
        // The stamp round-trips through the spreadsheet, where anything can be typed in.
        val r = rule(dayOfMonth = 1, created = LocalDate.of(2026, 11, 1), lastRunMonth = "last week")
        assertEquals(days(LocalDate.of(2026, 11, 1)), due(r, LocalDate.of(2026, 11, 21)))
    }

    // ---------------------------------------------------------------- restored from the sheet

    @Test
    fun `a rule restored from the sheet is not treated as new`() {
        // No createdAt in the sheet. Reading that as "made today" would hold back a payment that
        // is already due.
        val r = rule(dayOfMonth = 1, created = null, lastRunMonth = "2026-10")
        assertEquals(days(LocalDate.of(2026, 11, 1)), due(r, LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `a restored rule with no history back-fills nothing`() {
        // Neither when it was made nor when it last ran is known, so only this month is owed -
        // guessing at the months between would be inventing transactions.
        val r = rule(dayOfMonth = 1, created = null)
        assertEquals(days(LocalDate.of(2026, 11, 1)), due(r, LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `a restored rule that already fired this month still does not fire again`() {
        val r = rule(dayOfMonth = 1, created = null, lastRunMonth = "2026-11")
        assertEquals(nothing, due(r, LocalDate.of(2026, 11, 21)))
    }

    // ---------------------------------------------------------------- switching a rule back on

    @Test
    fun `resuming a paused rule does not bill the months it was paused for`() {
        // Paused in January, switched back on in November: without this it would owe ten months.
        assertEquals("2026-10", resumeMonthKey("2026-01", LocalDate.of(2026, 11, 21)))

        val resumed =
            rule(dayOfMonth = 1, created = LocalDate.of(2025, 1, 1), lastRunMonth = "2026-10")
        assertEquals(days(LocalDate.of(2026, 11, 1)), due(resumed, LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `resuming never moves the stamp backwards`() {
        // Switched off and on again in a month it has already fired in must not fire it twice.
        assertEquals("2026-11", resumeMonthKey("2026-11", LocalDate.of(2026, 11, 21)))
        assertEquals("2026-12", resumeMonthKey("2026-12", LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `resuming a rule that never ran starts it from this month`() {
        assertEquals("2025-12", resumeMonthKey(null, LocalDate.of(2026, 1, 9)))
    }

    // ---------------------------------------------------------------- the stamp itself

    @Test
    fun `the month key is the stamp the rule records`() {
        assertEquals("2026-01", monthKeyOf(LocalDate.of(2026, 1, 9)))
        assertEquals("2026-11", monthKeyOf(LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `isAutopayDue answers the simple question`() {
        val owing =
            rule(dayOfMonth = 1, created = LocalDate.of(2026, 1, 1), lastRunMonth = "2026-10")
        val settled = owing.copy(lastRunMonth = "2026-11")

        assertTrue(isAutopayDue(owing, LocalDate.of(2026, 11, 21), ist))
        assertFalse(isAutopayDue(settled, LocalDate.of(2026, 11, 21), ist))
    }
}

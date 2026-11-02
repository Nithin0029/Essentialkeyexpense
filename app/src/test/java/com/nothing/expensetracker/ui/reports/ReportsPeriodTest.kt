package com.nothing.expensetracker.ui.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The previous-period window and the spending projection.
 *
 * Both exist because the Reports screen was quietly comparing unlike things: two days of a new
 * month against a whole finished one, and one rent payment projected across thirty days. The
 * arithmetic was never wrong, which is why it needed tests rather than a bug report.
 */
class ReportsPeriodTest {

    // Fixed, DST-free zone so a boundary is a boundary.
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(date: String, time: String): ZonedDateTime =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    private fun millis(date: String, time: String): Long =
        at(date, time).toInstant().toEpochMilli()

    /** The range Reports queries for: period start to the end of today. */
    private fun currentRange(start: String, today: String): Pair<Long, Long> =
        millis(start, "00:00") to
            LocalDate.parse(today).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    private fun range(filter: DateFilter, start: String, now: ZonedDateTime) =
        previousTimeRange(filter, currentRange(start, now.toLocalDate().toString()), now)

    @Test
    fun `this month on the 2nd compares against the same two days of last month`() {
        val now = at("2026-11-02", "14:30")
        val (from, to) = range(DateFilter.THIS_MONTH, "2026-11-01", now)

        assertEquals(millis("2026-10-01", "00:00"), from)
        assertEquals(millis("2026-10-02", "14:30"), to)
    }

    @Test
    fun `a short previous month is never overrun`() {
        // The 31st of March has no counterpart in February, so February's last day counts whole
        // rather than the window spilling into March.
        val now = at("2027-03-31", "09:00")
        val (from, to) = range(DateFilter.THIS_MONTH, "2027-03-01", now)

        assertEquals(millis("2027-02-01", "00:00"), from)
        assertEquals(millis("2027-02-28", "23:59:59.999"), to)
    }

    @Test
    fun `the last moment of a period covers the whole previous period`() {
        val now = at("2026-11-30", "23:59:59.999")
        val (from, to) = range(DateFilter.THIS_MONTH, "2026-11-01", now)

        assertEquals(millis("2026-10-01", "00:00"), from)
        assertEquals(millis("2026-10-30", "23:59:59.999"), to)
    }

    @Test
    fun `today compares against the same hours of yesterday`() {
        val now = at("2026-11-02", "10:15")
        val (from, to) = range(DateFilter.TODAY, "2026-11-02", now)

        assertEquals(millis("2026-11-01", "00:00"), from)
        assertEquals(millis("2026-11-01", "10:15"), to)
    }

    @Test
    fun `this week compares against the same weekdays of last week`() {
        // 2026-11-02 is a Monday; 2026-11-04 is the Wednesday of that week.
        val now = at("2026-11-04", "18:00")
        val (from, to) = range(DateFilter.THIS_WEEK, "2026-11-02", now)

        assertEquals(millis("2026-10-26", "00:00"), from)
        assertEquals(millis("2026-10-28", "18:00"), to)
    }

    @Test
    fun `this year compares against the same elapsed days of last year`() {
        val now = at("2026-11-02", "08:00")
        val (from, to) = range(DateFilter.THIS_YEAR, "2026-01-01", now)

        assertEquals(millis("2025-01-01", "00:00"), from)
        // 305 days elapsed since 1 January, applied to the same offset in 2025.
        assertEquals(millis("2025-11-02", "08:00"), to)
    }

    @Test
    fun `a custom range still steps back by its own span`() {
        val from = millis("2026-09-10", "00:00")
        val to = millis("2026-09-20", "23:59:59.999")
        val (prevFrom, prevTo) = previousTimeRange(
            DateFilter.CUSTOM,
            from to to,
            at("2026-11-02", "12:00")
        )

        assertEquals(from - 1, prevTo)
        assertEquals(from - (to - from) - 1, prevFrom)
    }

    @Test
    fun `an unset custom range does not blow up on date arithmetic`() {
        // calculateTimeRange falls back to (0, Long.MAX_VALUE), which no calendar maths survives.
        val (from, to) = previousTimeRange(
            DateFilter.CUSTOM,
            0L to Long.MAX_VALUE,
            at("2026-11-02", "12:00")
        )

        assertTrue("expected a range below the epoch, got $from..$to", to < 0)
    }

    @Test
    fun `the projection is withheld until a fifth of the period has run`() {
        assertFalse(SpendingPace(daysElapsed = 1, daysInPeriod = 30).isProjectionMeaningful)
        assertFalse(SpendingPace(daysElapsed = 5, daysInPeriod = 30).isProjectionMeaningful)
        assertTrue(SpendingPace(daysElapsed = 6, daysInPeriod = 30).isProjectionMeaningful)
        assertTrue(SpendingPace(daysElapsed = 2, daysInPeriod = 7).isProjectionMeaningful)
    }

    @Test
    fun `an empty pace never claims a meaningful projection`() {
        assertFalse(SpendingPace().isProjectionMeaningful)
    }

    @Test
    fun `every label says the previous window is truncated`() {
        // Guards the pairing: the window stops partway through the previous period, so no label
        // may read as a comparison against a complete one.
        val plain = listOf("last month", "last week", "last year", "yesterday")
        DateFilter.entries.forEach { filter ->
            val label = previousPeriodLabel(filter)
            assertFalse("'$label' reads as a whole period", label in plain)
        }
    }
}

package com.nothing.expensetracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Changing a transaction's date must not change its time.
 *
 * The edit screen assigned the date picker's value straight to the timestamp. The picker reports
 * midnight **UTC**, so every edited transaction became 00:00 UTC — displayed as 05:30 in India —
 * and, worse, every transaction on a day collapsed to the same instant, taking the order they
 * happened in with it.
 */
class TransactionTimeTest {

    private val ist = ZoneId.of("Asia/Kolkata")

    private fun ist(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ist).toInstant().toEpochMilli()

    /** What the Material date picker hands back for a chosen day. */
    private fun picked(year: Int, month: Int, day: Int): Long =
        LocalDateTime.of(year, month, day, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun readBack(millis: Long) = Instant.ofEpochMilli(millis).atZone(ist)

    @Test
    fun `the day moves and the time of day stays`() {
        val original = ist(2026, 11, 21, 19, 42)
        val moved = applyPickedDate(picked(2026, 11, 18), original, ist)

        val result = readBack(moved)
        assertEquals(18, result.dayOfMonth)
        assertEquals(11, result.monthValue)
        assertEquals(19, result.hour)
        assertEquals(42, result.minute)
    }

    @Test
    fun `picking the same day leaves the timestamp alone`() {
        // Opening the picker and confirming without changing anything must not move the entry.
        val original = ist(2026, 11, 21, 19, 42)
        assertEquals(original, applyPickedDate(picked(2026, 11, 21), original, ist))
    }

    @Test
    fun `two entries on the same day stay distinguishable`() {
        // This is the one that matters: it is what the history orders by, and what the old
        // behaviour destroyed by giving every entry on a day the same instant.
        val morning = ist(2026, 11, 21, 9, 15)
        val evening = ist(2026, 11, 21, 21, 5)

        val movedMorning = applyPickedDate(picked(2026, 11, 20), morning, ist)
        val movedEvening = applyPickedDate(picked(2026, 11, 20), evening, ist)

        assertNotEquals(movedMorning, movedEvening)
        assert(movedEvening > movedMorning) { "the later entry must still sort later" }
    }

    @Test
    fun `an early morning entry does not slip to the day before`() {
        // 00:30 in India is 19:00 UTC the previous day. Reading the time back in UTC, as the
        // old code effectively did, moves the transaction to the wrong date entirely.
        val justAfterMidnight = ist(2026, 11, 21, 0, 30)
        val moved = applyPickedDate(picked(2026, 11, 21), justAfterMidnight, ist)

        val result = readBack(moved)
        assertEquals(21, result.dayOfMonth)
        assertEquals(0, result.hour)
        assertEquals(30, result.minute)
    }

    @Test
    fun `a transaction that lost its time keeps the day it is moved to`() {
        // Rows already written at 00:00 UTC cannot have their real time recovered, but moving
        // them must still land them on the day that was chosen.
        val collapsed = picked(2026, 11, 15)
        val moved = applyPickedDate(picked(2026, 11, 19), collapsed, ist)

        assertEquals(19, readBack(moved).dayOfMonth)
    }
}

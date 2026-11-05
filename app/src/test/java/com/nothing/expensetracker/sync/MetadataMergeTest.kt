package com.nothing.expensetracker.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The merge behind the App_Metadata push.
 *
 * The rows below are a real sheet, copied from a 3.7.0 install: created once and never corrected,
 * so it claimed schema 1 and app 2.0.0 while running neither. The old writer appended keys that
 * were missing and left everything else alone, which is why that sheet stayed wrong for its
 * entire life. These tests pin the behaviour that fixes it.
 */
class MetadataMergeTest {

    /** Exactly what the App_Metadata tab held on a sheet created by 3.6.0. */
    private val staleSheet: List<List<Any>> = listOf(
        listOf("Schema Version", "1"),
        listOf("App Version", "2.0.0"),
        listOf("Database Version", "1"),
        listOf("Created Date", "02/11/2026 22:03:43"),
        listOf("Last Updated", "02/11/2026 22:03:43")
    )

    private val push = linkedMapOf(
        "Schema Version" to "2",
        "App Version" to "3.7.1",
        "Database Version" to "16",
        "Last Updated" to "05/11/2026 14:30:00"
    )

    @Test
    fun `a stale sheet is corrected in place`() {
        assertEquals(
            listOf(
                listOf("Schema Version", "2"),
                listOf("App Version", "3.7.1"),
                listOf("Database Version", "16"),
                // Genuinely write-once: the sheet was created then, and still was.
                listOf("Created Date", "02/11/2026 22:03:43"),
                listOf("Last Updated", "05/11/2026 14:30:00")
            ),
            mergeKeyValueRows(staleSheet, push)
        )
    }

    @Test
    fun `row order is preserved so the tab does not reshuffle on every sync`() {
        val keys = mergeKeyValueRows(staleSheet, push).map { it.first() }
        assertEquals(staleSheet.map { it.first() }, keys)
    }

    @Test
    fun `rows the app does not own are left exactly as they were`() {
        val withUserRow = staleSheet + listOf(listOf("My own note", "do not touch"))
        val merged = mergeKeyValueRows(withUserRow, push)
        assertEquals(listOf<Any>("My own note", "do not touch"), merged.last())
    }

    @Test
    fun `keys the sheet has never seen are appended`() {
        val merged = mergeKeyValueRows(staleSheet, push + ("Device" to "Pixel"))
        assertEquals(listOf<Any>("Device", "Pixel"), merged.last())
        assertEquals(staleSheet.size + 1, merged.size)
    }

    @Test
    fun `an empty tab is written from scratch`() {
        assertEquals(
            listOf(
                listOf("Schema Version", "2"),
                listOf("App Version", "3.7.1"),
                listOf("Database Version", "16"),
                listOf("Last Updated", "05/11/2026 14:30:00")
            ),
            mergeKeyValueRows(emptyList(), push)
        )
    }

    @Test
    fun `a row missing its value cell does not lose its key`() {
        // Sheets trims trailing empties, so a key whose value was cleared comes back as one cell.
        val merged = mergeKeyValueRows(listOf(listOf("Created Date")), push)
        assertEquals(listOf<Any>("Created Date", ""), merged.first())
    }

    @Test
    fun `the Settings tab keeps the opening balances while Last Sync moves`() {
        val settings: List<List<Any>> = listOf(
            listOf("Opening Bank Balance", "209905.0"),
            listOf("Opening Cash Balance", "17280.0"),
            listOf("Currency", "INR"),
            listOf("Last Sync", "Never")
        )
        assertEquals(
            listOf(
                listOf("Opening Bank Balance", "209905.0"),
                listOf("Opening Cash Balance", "17280.0"),
                listOf("Currency", "INR"),
                listOf("Last Sync", "05/11/2026 14:30:00")
            ),
            mergeKeyValueRows(settings, linkedMapOf("Last Sync" to "05/11/2026 14:30:00"))
        )
    }
}

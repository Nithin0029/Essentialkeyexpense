package com.nothing.expensetracker.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the spreadsheet side of a release honest.
 *
 * The App_Metadata tab existed from the beginning and was wrong from the beginning: "App Version"
 * was the literal `"2.0.0"`, "Schema Version" was `"1"` through two whole tabs being added, and
 * nothing in the app ever read either, so nothing ever complained. These tests are the thing that
 * complains. They fail the build when the sheet layout changes without [SHEET_SCHEMA_VERSION]
 * being bumped, and when the reported versions stop being derived from the real ones.
 *
 * The source is read as text on purpose: the values live inside a class that needs Google
 * Sheets credentials to instantiate, and the property worth protecting is a property of the
 * source, not of a running object.
 */
class SheetSchemaTest {

    private val manager: String by lazy { read("sync/SpreadsheetManager.kt") }

    private fun read(relative: String): String {
        val file = File("src/main/java/com/nothing/expensetracker/$relative")
        assertTrue("not found: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    /** Source with `//` comments removed, so parentheses in prose cannot confuse the parsing. */
    private fun withoutComments(source: String): String =
        source.lineSequence().joinToString("\n") { it.substringBefore("//") }

    /**
     * The tabs and columns the app writes, exactly as `ensureHeadersExist` declares them.
     *
     * **If a test fails against this map, that is the point.** Update the map *and* bump
     * [SHEET_SCHEMA_VERSION], then make sure existing sheets survive the change — columns are
     * appended at the end precisely so old rows stay aligned.
     */
    private val expectedLayout = mapOf(
        "Transactions" to listOf(
            "Transaction ID", "Date", "Type", "Category", "Amount",
            "Payment Method", "Friend Name", "Notes", "Created At",
            "Updated At", "Sync Status", "Description"
        ),
        "Categories" to listOf(
            "Category ID", "Category Name", "Parent Category",
            "Used For Debit", "Used For Credit"
        ),
        "Friends" to listOf(
            "Friend ID", "Friend Name", "Total Debit", "Total Credit",
            "Outstanding Balance", "Status", "Last Updated"
        ),
        "Budgets" to listOf("Budget ID", "Category", "Amount", "Month", "Year", "Alert Threshold"),
        "Settings" to listOf("Setting", "Value"),
        "App_Metadata" to listOf("Key", "Value"),
        "Payment_Methods" to listOf("Name", "Is System"),
        "Autopay" to listOf(
            "Rule ID", "Description", "Category", "Type", "Amount",
            "Payment Method", "Friend Name", "Day Of Month", "Active", "Last Run Month"
        )
    )

    /** Parses the `headerMap` literal out of `ensureHeadersExist`. */
    private fun declaredLayout(): Map<String, List<String>> {
        val source = withoutComments(manager)
        val start = source.indexOf("val headerMap = mapOf(")
        assertTrue("headerMap not found - was ensureHeadersExist renamed?", start >= 0)

        // Walk to the matching close paren so the block ends where the literal does.
        var depth = 0
        var end = -1
        for (i in source.indexOf('(', start) until source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
        }
        assertTrue("unbalanced headerMap literal", end > start)
        val block = source.substring(start, end)

        return Regex("""\"([A-Za-z_]+)\"\s+to\s+listOf\(([^)]*)\)""")
            .findAll(block)
            .associate { match ->
                val headers = Regex("\"([^\"]*)\"").findAll(match.groupValues[2])
                    .map { it.groupValues[1] }.toList()
                match.groupValues[1] to headers
            }
    }

    @Test
    fun `sheet layout matches the version recorded for it`() {
        assertEquals(
            "The spreadsheet layout changed. Update expectedLayout in this test AND bump " +
                "SHEET_SCHEMA_VERSION, or sheets in the wild will report a shape they do not have.",
            expectedLayout,
            declaredLayout()
        )
    }

    @Test
    fun `every tab in the layout is one the app creates`() {
        // The tab list is written out in three places: the header map, the check that runs on
        // every connection, and the creation path. Any one of them drifting leaves a tab that is
        // formatted but never created, or created but never filled.
        val lists = Regex("""listOf\(\s*("Transactions"[^)]*)\)""").findAll(withoutComments(manager))
            .map { match ->
                Regex("\"([^\"]*)\"").findAll(match.groupValues[1]).map { it.groupValues[1] }.toSet()
            }.toList()

        assertTrue("expected the tab list to appear more than once", lists.size >= 2)
        lists.forEach { tabs ->
            assertEquals("a tab list has drifted from the header map", expectedLayout.keys, tabs)
        }
    }

    @Test
    fun `reported app version comes from the build, not a literal`() {
        assertTrue(
            "App Version must be BuildConfig.VERSION_NAME - a literal goes stale the moment it " +
                "is written, which is how every sheet ended up claiming to be 2.0.0",
            manager.contains("\"App Version\" to BuildConfig.VERSION_NAME")
        )
        assertFalse(
            "a hardcoded App Version is back in the metadata",
            Regex(""""App Version" to "\d""").containsMatchIn(manager)
        )
    }

    @Test
    fun `reported schema and database versions come from constants`() {
        assertTrue(
            "Schema Version must be SHEET_SCHEMA_VERSION",
            manager.contains("\"Schema Version\" to SHEET_SCHEMA_VERSION.toString()")
        )
        assertTrue(
            "Database Version must be DATABASE_VERSION",
            manager.contains("\"Database Version\" to DATABASE_VERSION.toString()")
        )
        assertFalse(
            "a hardcoded Schema Version is back in the metadata",
            Regex(""""Schema Version" to "\d""").containsMatchIn(manager)
        )
    }

    @Test
    fun `the database version is single-sourced`() {
        val db = read("data/local/AppDatabase.kt")
        assertTrue(
            "@Database must take version = DATABASE_VERSION so the sheet cannot report a " +
                "different number from the one Room migrates to",
            db.contains("version = DATABASE_VERSION")
        )
        assertTrue("DATABASE_VERSION constant missing", db.contains("const val DATABASE_VERSION ="))
    }

    @Test
    fun `metadata is pushed on every sync, not only at creation`() {
        val sync = read("sync/SyncManager.kt")
        assertTrue(
            "SyncManager must call syncAppMetadataToSheet, or the values only ever describe the " +
                "app version that first created the sheet",
            sync.contains("syncAppMetadataToSheet()")
        )
    }
}

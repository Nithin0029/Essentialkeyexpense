package com.nothing.expensetracker.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Covers the parsing of spreadsheet rows on the restore path.
 *
 * These are the cases most likely to arise in the wild: rows written by an older version of the
 * app (fewer columns), rows a user has hand-edited in Google Sheets, and rows where the API has
 * dropped trailing empty cells.
 */
class SheetRowParserTest {

    // A full row as written by the current version: A..L.
    private fun fullTransactionRow(
        id: String = "42",
        date: String = "2026-03-15",
        type: String = "Debit",
        category: String = "Food",
        amount: String = "250.5",
        method: String = "UPI",
        friend: String = "",
        notes: String = "lunch",
        createdAt: String = "1773532800000",
        updatedAt: String = "1773532800000",
        status: String = "Synced",
        description: String = "Cafe"
    ): List<Any> = listOf(
        id, date, type, category, amount, method, friend, notes,
        createdAt, updatedAt, status, description
    )

    // --- cell() ---

    @Test
    fun `cell returns empty string past the end of a short row`() {
        val row = listOf<Any>("1", "2026-01-01")
        assertEquals("1", row.cell(0))
        assertEquals("", row.cell(11))
        assertEquals("", row.cell(99))
    }

    @Test
    fun `cell trims surrounding whitespace`() {
        assertEquals("Food", listOf<Any>("  Food  ").cell(0))
    }

    // --- transaction rows ---

    @Test
    fun `parses a full transaction row`() {
        val expense = parseTransactionRow(fullTransactionRow())
        requireNotNull(expense)
        assertEquals(42L, expense.id)
        assertEquals(250.5, expense.amount, 0.001)
        assertEquals("Debit", expense.type)
        assertEquals("Food", expense.category)
        assertEquals("UPI", expense.paymentMethod)
        assertEquals("lunch", expense.notes)
        assertEquals("Cafe", expense.description)
        assertEquals(1773532800000L, expense.timestamp)
        assertNull(expense.friendId)
    }

    @Test
    fun `keeps the sheet id so later updates address the same row`() {
        val expense = parseTransactionRow(fullTransactionRow(id = "99812"))
        assertEquals(99812L, expense?.id)
    }

    @Test
    fun `restores a legacy row that has no description column`() {
        // Pre-Description sheets wrote only A..K.
        val legacy = fullTransactionRow().dropLast(1)
        val expense = parseTransactionRow(legacy)
        requireNotNull(expense)
        assertEquals("", expense.description)
        assertEquals("Food", expense.category)
        assertEquals(1773532800000L, expense.timestamp)
    }

    @Test
    fun `rejects a row with an unparseable id`() {
        assertNull(parseTransactionRow(fullTransactionRow(id = "")))
        assertNull(parseTransactionRow(fullTransactionRow(id = "not-a-number")))
    }

    @Test
    fun `rejects a row with an unparseable amount`() {
        assertNull(parseTransactionRow(fullTransactionRow(amount = "")))
        assertNull(parseTransactionRow(fullTransactionRow(amount = "abc")))
    }

    @Test
    fun `falls back to defaults for blank type category and method`() {
        val expense = parseTransactionRow(
            fullTransactionRow(type = "", category = "", method = "")
        )
        requireNotNull(expense)
        assertEquals("Debit", expense.type)
        assertEquals("Other", expense.category)
        assertEquals("UPI", expense.paymentMethod)
    }

    @Test
    fun `reads friend name from column G when present`() {
        val expense = parseTransactionRow(fullTransactionRow(friend = "Arun"))
        assertEquals("Arun", expense?.friendId)
    }

    // --- timestamps ---

    @Test
    fun `prefers epoch millis over the display date`() {
        // Deliberately inconsistent: column I must win.
        val row = fullTransactionRow(date = "2020-01-01", createdAt = "1773532800000")
        assertEquals(1773532800000L, parseTimestamp(row))
    }

    @Test
    fun `falls back to the display date when millis are missing`() {
        val row = fullTransactionRow(date = "2026-03-15", createdAt = "")
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse("2026-03-15")!!.time
        assertEquals(expected, parseTimestamp(row))
    }

    @Test
    fun `still restores a row when neither date column parses`() {
        val before = System.currentTimeMillis()
        val row = fullTransactionRow(date = "garbage", createdAt = "garbage")
        val parsed = parseTimestamp(row)
        // Dated "now" rather than dropped: losing the transaction is worse than losing its date.
        assertTrue(parsed >= before)
    }

    @Test
    fun `parseSheetDate returns null for empty and malformed input`() {
        assertNull(parseSheetDate(""))
        assertNull(parseSheetDate("not a date"))
    }

    @Test
    fun `rejects a day-first date instead of reading it as year 15`() {
        // Regression: SimpleDateFormat is lenient by default and read "15-03-2026" as year 15,
        // dating the transaction to antiquity. The app displays dates day-first, so this is an
        // entirely natural thing for someone to type into the sheet.
        assertNull(parseSheetDate("15-03-2026"))
    }

    @Test
    fun `rejects an out-of-range epoch in the millis column`() {
        // A stray small number would otherwise date a transaction to 1970 and drop it out of
        // every month filter.
        val row = fullTransactionRow(date = "2026-03-15", createdAt = "123")
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse("2026-03-15")!!.time
        assertEquals(expected, parseTimestamp(row))
    }

    @Test
    fun `rejects an absurdly future epoch`() {
        val farFuture = (System.currentTimeMillis() + 50L * 365 * 24 * 60 * 60 * 1000).toString()
        val before = System.currentTimeMillis()
        val row = fullTransactionRow(date = "", createdAt = farFuture)
        assertTrue(parseTimestamp(row) >= before)
    }

    @Test
    fun `accepts a plausible back-dated entry`() {
        val row = fullTransactionRow(date = "2005-06-01", createdAt = "")
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse("2005-06-01")!!.time
        assertEquals(expected, parseTimestamp(row))
    }

    // --- values as a REAL sheet returns them -------------------------------------------
    // Regression suite for the bug that shipped: the Transactions tab is written with
    // USER_ENTERED and then currency/date formatted, so reads returned display strings
    // ("Rs 5,000.00", "15-Mar-2026") and every row was silently dropped. Reads now use
    // UNFORMATTED_VALUE, which returns JSON numbers -- these tests pin that shape.

    @Test
    fun `parses an integer id that arrives as a double`() {
        // UNFORMATTED_VALUE gives a JSON number; toString() yields "9001.0".
        assertEquals(9001L, "9001.0".toSheetLong())
        assertEquals(9001L, "9001".toSheetLong())
    }

    @Test
    fun `parses epoch millis that arrive in scientific notation`() {
        // A 13-digit number through Double.toString() becomes "1.772E12".
        assertEquals(1772000000000L, "1.772E12".toSheetLong())
    }

    @Test
    fun `parses an amount that arrives as an unformatted double`() {
        assertEquals(5000.0, "5000.0".toSheetNumber()!!, 0.001)
        assertEquals(250.5, "250.5".toSheetNumber()!!, 0.001)
    }

    @Test
    fun `still parses a currency-formatted amount if one slips through`() {
        assertEquals(5000.0, "Rs 5,000.00".toSheetNumber()!!, 0.001)
        assertEquals(5000.0, "₹5,000.00".toSheetNumber()!!, 0.001)
        assertEquals(1234567.89, "1,234,567.89".toSheetNumber()!!, 0.001)
    }

    @Test
    fun `transaction row restores when id and amount arrive as numbers`() {
        // Exactly what the live sheet produced, which previously restored nothing.
        val row = listOf<Any>(
            "9001.0", "46096", "Debit", "Home", "5000.0", "Bank", "", "march rent",
            "1.772E12", "1.772E12", "Synced", "Rent"
        )
        val expense = parseTransactionRow(row)
        requireNotNull(expense) { "row should restore" }
        assertEquals(9001L, expense.id)
        assertEquals(5000.0, expense.amount, 0.001)
        assertEquals("Home", expense.category)
        assertEquals(1772000000000L, expense.timestamp)
    }

    @Test
    fun `converts a sheets date serial to the right day`() {
        // 45000 -> 2023-03-15 in the Sheets epoch (days since 1899-12-30).
        val millis = parseSheetDate("45000")
        requireNotNull(millis)
        val rendered = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(millis))
        assertEquals("2023-03-15", rendered)
    }

    @Test
    fun `accepts the displayed dd-MMM-yyyy date format`() {
        val millis = parseSheetDate("15-Mar-2026")
        requireNotNull(millis)
        val rendered = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(millis))
        assertEquals("2026-03-15", rendered)
    }

    @Test
    fun `budget month and year parse when they arrive as doubles`() {
        val row = listOf<Any>("CAT_Food", "Food", "5000.0", "3.0", "2026.0")
        assertEquals(BudgetKey("Food", 3, 2026), parseBudgetKey(row))
    }

    @Test
    fun `toSheetNumber rejects genuinely non-numeric text`() {
        assertNull("".toSheetNumber())
        assertNull("Overall".toSheetNumber())
        assertNull("Synced".toSheetNumber())
    }

    // --- category scope flags ---

    private fun categoryRow(
        id: String = "7",
        name: String = "Home",
        parent: String = "",
        debit: String = "Yes",
        credit: String = "No"
    ): List<Any> = listOf(id, name, parent, debit, credit)

    @Test
    fun `reads both scope flags`() {
        val linked = categoryRow(debit = "Yes", credit = "Yes")
        assertTrue(parseScopeFlag(linked, DEBIT_FLAG_COLUMN))
        assertTrue(parseScopeFlag(linked, CREDIT_FLAG_COLUMN))
    }

    @Test
    fun `reads a credit-only category`() {
        val salary = categoryRow(name = "Salary", debit = "No", credit = "Yes")
        assertFalse(parseScopeFlag(salary, DEBIT_FLAG_COLUMN))
        assertTrue(parseScopeFlag(salary, CREDIT_FLAG_COLUMN))
    }

    @Test
    fun `treats a legacy category row with no flags as debit-only`() {
        // Pre-widening sheets wrote only A..B, so both flag cells are absent.
        val legacy = listOf<Any>("7", "Home")
        assertTrue(parseScopeFlag(legacy, DEBIT_FLAG_COLUMN))
        assertFalse(parseScopeFlag(legacy, CREDIT_FLAG_COLUMN))
    }

    @Test
    fun `accepts hand-typed boolean spellings`() {
        listOf("yes", "YES", "true", "True", "1", "y").forEach { spelling ->
            assertTrue("expected '$spelling' to read as true", spelling.toSheetBoolean())
        }
        listOf("no", "No", "false", "0", "", "maybe").forEach { spelling ->
            assertFalse("expected '$spelling' to read as false", spelling.toSheetBoolean())
        }
    }

    @Test
    fun `exposes the parent name for subcategory restore`() {
        val child = categoryRow(name = "Petrol", parent = "Bike")
        assertEquals("Bike", child.cell(CATEGORY_PARENT_COLUMN))
        assertEquals("Petrol", child.cell(CATEGORY_NAME_COLUMN))
        assertEquals("", categoryRow().cell(CATEGORY_PARENT_COLUMN))
    }

    // --- budget keys ---

    @Test
    fun `maps the Overall row back to a null category`() {
        val row = listOf<Any>("OVERALL", "Overall", "20000", "3", "2026")
        val key = parseBudgetKey(row)
        assertEquals(BudgetKey(null, 3, 2026), key)
    }

    @Test
    fun `maps a category row to its category name`() {
        val row = listOf<Any>("CAT_Food", "Food", "5000", "3", "2026")
        assertEquals(BudgetKey("Food", 3, 2026), parseBudgetKey(row))
    }

    @Test
    fun `rejects a budget row with an unparseable month or year`() {
        assertNull(parseBudgetKey(listOf<Any>("CAT_Food", "Food", "5000", "", "2026")))
        assertNull(parseBudgetKey(listOf<Any>("CAT_Food", "Food", "5000", "3", "")))
        assertNull(parseBudgetKey(listOf<Any>("CAT_Food", "Food", "5000")))
    }

    @Test
    fun `budget keys compare by value so pruning can use a set`() {
        val fromSheet = parseBudgetKey(listOf<Any>("CAT_Food", "Food", "5000", "3", "2026"))
        val fromDevice = BudgetKey("Food", 3, 2026)
        assertTrue(fromDevice in setOf(fromSheet))
    }
}

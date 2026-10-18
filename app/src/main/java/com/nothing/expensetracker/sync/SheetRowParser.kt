package com.nothing.expensetracker.sync

import com.nothing.expensetracker.data.local.Expense
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pure parsing of spreadsheet rows into domain objects.
 *
 * Kept free of Android, Hilt and the Sheets API deliberately: these are the parts of the restore
 * path most likely to meet malformed input — short rows, hand-edited cells, rows written by an
 * older version of the app — so they are covered by plain JVM unit tests in
 * `SheetRowParserTest` rather than requiring a device.
 *
 * Column indices below are zero-based and match the headers written by
 * `SpreadsheetManager.ensureHeadersExist`.
 */

// Categories tab: A=id, B=name, C=parent name, D=used for debit, E=used for credit.
internal const val CATEGORY_NAME_COLUMN = 1
internal const val CATEGORY_PARENT_COLUMN = 2
internal const val DEBIT_FLAG_COLUMN = 3
internal const val CREDIT_FLAG_COLUMN = 4

/**
 * Reads one cell, tolerating short rows.
 *
 * The Sheets API omits trailing empty cells entirely, so a row with nothing after column D comes
 * back with four entries rather than twelve. Every read goes through here so a short row yields
 * an empty string instead of throwing.
 */
internal fun List<Any>.cell(index: Int): String =
    getOrNull(index)?.toString()?.trim().orEmpty()

/**
 * Parses a numeric cell.
 *
 * Values read back with UNFORMATTED_VALUE arrive as JSON numbers, so the Java client hands us a
 * Double or BigDecimal and `toString()` can produce "9001.0" for an integer or "1.772E12" for a
 * large one -- neither of which `toLongOrNull` accepts. Cells a person typed by hand may also
 * carry a currency symbol or thousands separators. Strip everything that is not part of a number
 * and let `toDoubleOrNull` handle the rest, including scientific notation.
 */
internal fun String.toSheetNumber(): Double? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val cleaned = buildString {
        for (c in trimmed) {
            if (c.isDigit() || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') append(c)
        }
    }
    return cleaned.toDoubleOrNull()
}

/**
 * Parses an integral cell (ids, months, years).
 *
 * Goes through [toSheetNumber] so "9001.0" and "1.772E12" both work. Doubles hold integers
 * exactly below 2^53, which is far above any id or epoch-millis value this app produces.
 */
internal fun String.toSheetLong(): Long? = toSheetNumber()?.toLong()

internal fun String.toSheetInt(): Int? = toSheetNumber()?.toInt()

/** Accepts anything a human might plausibly have typed into a boolean column. */
internal fun String.toSheetBoolean(): Boolean =
    lowercase(Locale.getDefault()) in setOf("yes", "true", "1", "y")

/**
 * Reads one of the two category scope flags.
 *
 * A row written before columns D/E existed has both cells empty. Such rows are treated as
 * Debit-only, which is exactly what those categories were before the Credit side was added —
 * so an old sheet restores to the same shape it had when it was written.
 */
internal fun parseScopeFlag(row: List<Any>, column: Int): Boolean {
    val hasAnyFlag = row.cell(DEBIT_FLAG_COLUMN).isNotEmpty() ||
        row.cell(CREDIT_FLAG_COLUMN).isNotEmpty()
    if (!hasAnyFlag) return column == DEBIT_FLAG_COLUMN
    return row.cell(column).toSheetBoolean()
}

/** Identity of a budget row: one budget per category (or overall) per month. */
internal data class BudgetKey(val categoryName: String?, val month: Int, val year: Int)

/**
 * Parses the identifying columns of a Budgets row, or null when they aren't usable.
 *
 * Column B is the literal "Overall" for the whole-month budget and a category name otherwise,
 * matching how `syncBudgetToSheet` writes it; "Overall" maps back to a null categoryName.
 */
internal fun parseBudgetKey(row: List<Any>): BudgetKey? {
    val month = row.cell(3).toSheetInt() ?: return null
    val year = row.cell(4).toSheetInt() ?: return null
    val categoryName = row.cell(1).takeIf { it.isNotEmpty() && !it.equals("Overall", true) }
    return BudgetKey(categoryName, month, year)
}

/**
 * Parses a Transactions row, or null when it carries no usable id or amount.
 *
 * The restored row keeps the sheet's id so that device and sheet stay addressable by the same
 * key — `findRowIndex` looks rows up by id, so a renumbered transaction would later update the
 * wrong row.
 */
internal fun parseTransactionRow(row: List<Any>): Expense? {
    val id = row.cell(0).toSheetLong() ?: return null
    val amount = row.cell(4).toSheetNumber() ?: return null

    return Expense(
        id = id,
        amount = amount,
        // Column L only exists on sheets written after the Description column was added; older
        // rows restore with an empty description because that text was never uploaded.
        description = row.cell(11),
        category = row.cell(3).ifEmpty { "Other" },
        type = row.cell(2).ifEmpty { "Debit" },
        paymentMethod = row.cell(5).ifEmpty { "UPI" },
        friendId = row.cell(6).takeIf { it.isNotEmpty() },
        notes = row.cell(7),
        timestamp = parseTimestamp(row)
    )
}

/**
 * Recovers a transaction's timestamp.
 *
 * Column I holds the original epoch millis and is authoritative. Column B is a display copy, used
 * only when the millis are missing or were overwritten by hand. If neither yields a plausible
 * date the row still restores -- dated now -- rather than being dropped, since losing the
 * transaction entirely would be worse than losing its date.
 */
internal fun parseTimestamp(row: List<Any>): Long =
    row.cell(8).toSheetLong()?.takeIf { it.isPlausibleTimestamp() }
        ?: parseSheetDate(row.cell(1))
        ?: System.currentTimeMillis()

/**
 * Rejects epoch values that cannot be a real transaction date.
 *
 * Both date columns are editable by hand in Google Sheets, and a stray value there would otherwise
 * be taken at face value -- a "123" in the millis column would date a transaction to 1970 and
 * quietly fall out of every month filter and report.
 */
internal fun Long.isPlausibleTimestamp(): Boolean =
    this in EARLIEST_PLAUSIBLE_TIMESTAMP..(System.currentTimeMillis() + TEN_YEARS_MILLIS)

/** 1990-01-01. Comfortably before any plausible personal-finance record in this app. */
private const val EARLIEST_PLAUSIBLE_TIMESTAMP = 631152000000L
private const val TEN_YEARS_MILLIS = 10L * 365 * 24 * 60 * 60 * 1000

/** Days between the Google Sheets epoch (1899-12-30) and the Unix epoch. */
private const val SHEETS_EPOCH_OFFSET_DAYS = 25569.0
private const val MILLIS_PER_DAY = 86_400_000.0

/**
 * Parses the date column, which can arrive in three shapes.
 *
 * The Transactions tab is date-formatted, so a cell written with USER_ENTERED is a real date and
 * UNFORMATTED_VALUE returns it as a **serial number** -- days since 1899-12-30 -- not text. A cell
 * someone typed as text stays text, and could be either the `yyyy-MM-dd` the app writes or the
 * `dd-MMM-yyyy` it displays. All three are accepted; anything else returns null.
 *
 * Text parsing is explicitly non-lenient: by default SimpleDateFormat reads "15-03-2026" -- the
 * day-first order this app displays -- as year 15, producing a date two thousand years off.
 */
internal fun parseSheetDate(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null

    // A date-formatted cell read unformatted comes back as a serial number.
    trimmed.toSheetNumber()?.let { serial ->
        if (serial > 0) {
            val millis = ((serial - SHEETS_EPOCH_OFFSET_DAYS) * MILLIS_PER_DAY).toLong()
            return millis.takeIf { it.isPlausibleTimestamp() }
        }
    }

    for (pattern in listOf("yyyy-MM-dd", "dd-MMM-yyyy")) {
        try {
            val format = SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }
            val parsed = format.parse(trimmed)?.time
            if (parsed != null && parsed.isPlausibleTimestamp()) return parsed
        } catch (e: Exception) {
            // Try the next pattern.
        }
    }
    return null
}

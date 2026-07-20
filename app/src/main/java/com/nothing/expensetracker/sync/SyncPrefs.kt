package com.nothing.expensetracker.sync

import android.content.Context

object SyncPrefs {
    private const val PREFS_NAME = "expense_tracker_sync_prefs"
    private const val KEY_SPREADSHEET_ID = "spreadsheet_id"

    fun setSpreadsheetId(context: Context, id: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SPREADSHEET_ID, id)
            .apply()
    }

    fun getSpreadsheetId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SPREADSHEET_ID, null)
    }
}

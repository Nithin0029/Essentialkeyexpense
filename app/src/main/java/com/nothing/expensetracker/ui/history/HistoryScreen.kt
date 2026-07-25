package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen() {
    Scaffold(
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HistoryHeader()
            HistorySearchBar()
            HistoryFilterRow()
            HistoryStatistics()
            
            // For now, we always show the list as it's UI only with placeholder data
            Box(modifier = Modifier.weight(1f)) {
                TransactionList()
            }
        }
    }
}

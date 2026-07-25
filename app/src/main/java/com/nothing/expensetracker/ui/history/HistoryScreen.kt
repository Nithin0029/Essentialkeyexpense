package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val expenses by viewModel.expenses.collectAsState()

    Scaffold(
        containerColor = Color.Black,
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
            QuickFilterRow()
            
            if (expenses.isEmpty()) {
                EmptyHistory()
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    TransactionList(expenses = expenses)
                }
            }
        }
    }
}

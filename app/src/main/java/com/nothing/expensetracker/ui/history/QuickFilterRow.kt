package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun QuickFilterRow() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Time Filters: Today, Week, Month, Year
        val timeFilters = listOf("Today", "Week", "Month", "Year")
        var selectedTimeFilter by remember { mutableStateOf("Month") }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(timeFilters) { filter ->
                FilterChip(
                    selected = selectedTimeFilter == filter,
                    onClick = { selectedTimeFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.Gray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedTimeFilter == filter,
                        borderColor = Color.DarkGray,
                        selectedBorderColor = Color.Transparent
                    )
                )
            }
        }

        // Transaction Type: All, Debit, Credit
        val typeFilters = listOf("All", "Debit", "Credit")
        var selectedTypeFilter by remember { mutableStateOf("All") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            typeFilters.forEach { filter ->
                FilterChip(
                    selected = selectedTypeFilter == filter,
                    onClick = { selectedTypeFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.Gray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedTypeFilter == filter,
                        borderColor = Color.DarkGray,
                        selectedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }
}

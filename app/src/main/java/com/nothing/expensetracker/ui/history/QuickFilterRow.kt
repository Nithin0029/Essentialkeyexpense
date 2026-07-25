package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun QuickFilterRow(
    filterState: HistoryFilterState,
    categories: List<String>,
    onDateFilterChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onMethodFilterChange: (String) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Date Filters
        FilterGroup(
            title = "Date",
            options = listOf("All", "Today", "Week", "Month", "Year"),
            selected = filterState.dateFilter,
            onSelected = onDateFilterChange
        )

        // 2. Type Filters
        FilterGroup(
            title = "Type",
            options = listOf("All", "Debit", "Credit"),
            selected = filterState.typeFilter,
            onSelected = onTypeFilterChange
        )

        // 3. Payment Method Filters
        FilterGroup(
            title = "Method",
            options = listOf("All", "UPI", "Bank", "Cash"),
            selected = filterState.methodFilter,
            onSelected = onMethodFilterChange
        )

        // 4. Category Filters
        FilterGroup(
            title = "Category",
            options = categories,
            selected = filterState.categoryFilter,
            onSelected = onCategoryFilterChange
        )
    }
}

@Composable
private fun FilterGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(options) { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.Gray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected == option,
                        borderColor = Color.DarkGray,
                        selectedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }
}

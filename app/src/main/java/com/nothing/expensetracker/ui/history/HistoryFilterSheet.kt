package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryFilterSheet(
    initialFilterState: HistoryFilterState,
    categories: List<String>,
    onDismiss: () -> Unit,
    onApply: (HistoryFilterState) -> Unit
) {
    var draftState by remember { mutableStateOf(initialFilterState) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Date Filters
            FilterGroup(
                title = "Date",
                options = listOf("All", "Today", "Week", "Month", "Year"),
                selected = draftState.dateFilter,
                onSelected = { draftState = draftState.copy(dateFilter = it) }
            )

            // Type Filters
            FilterGroup(
                title = "Type",
                options = listOf("All", "Debit", "Credit"),
                selected = draftState.typeFilter,
                onSelected = { draftState = draftState.copy(typeFilter = it) }
            )

            // Payment Method Filters
            FilterGroup(
                title = "Method",
                options = listOf("All", "UPI", "Bank", "Cash"),
                selected = draftState.methodFilter,
                onSelected = { draftState = draftState.copy(methodFilter = it) }
            )

            // Category Filters
            FilterGroup(
                title = "Category",
                options = categories,
                selected = draftState.categoryFilter,
                onSelected = { draftState = draftState.copy(categoryFilter = it) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { draftState = HistoryFilterState() },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = { onApply(draftState) },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Apply")
                }
            }
        }
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
            color = Color.Gray
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

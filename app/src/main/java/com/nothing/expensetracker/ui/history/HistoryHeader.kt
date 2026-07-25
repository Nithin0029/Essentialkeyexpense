package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HistoryHeader(
    currentSort: SortOption,
    onSortChange: (SortOption) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = Color.White)
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                containerColor = Color(0xFF1E1E1E)
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = if (option == currentSort) MaterialTheme.colorScheme.primary else Color.White
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

package com.nothing.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nothing.expensetracker.data.local.CategoryExpense

// Distinct Nothing OS aesthetic color palette
val CategoryColors = listOf(
    Color(0xFFD71921), // Nothing Red
    Color(0xFF4285F4), // Blue
    Color(0xFF34A853), // Green
    Color(0xFFFBBC05), // Yellow
    Color(0xFFA142F4), // Purple
    Color(0xFFFF6D00), // Orange
    Color(0xFF9E9E9E)  // Gray (Others)
)

@Composable
fun CategoryDonutChart(
    expenses: List<CategoryExpense>,
    selectedMonth: String,
    selectedYear: String,
    onMonthChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSpent = expenses.sumOf { it.totalAmount }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    val months = listOf(
        "01" to "Jan", "02" to "Feb", "03" to "Mar", "04" to "Apr",
        "05" to "May", "06" to "Jun", "07" to "Jul", "08" to "Aug",
        "09" to "Sep", "10" to "Oct", "11" to "Nov", "12" to "Dec"
    )
    val years = listOf("2024", "2025", "2026", "2027")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Breakdown 📈",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Month Dropdown
                    Box {
                        Surface(
                            color = Color(0xFF2C2C2C),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { monthExpanded = true }
                        ) {
                            Text(
                                months.firstOrNull { it.first == selectedMonth }?.second ?: "Month",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false },
                            modifier = Modifier.background(Color(0xFF2C2C2C))
                        ) {
                            months.forEach { (num, name) ->
                                DropdownMenuItem(
                                    text = { Text(name, color = Color.White) },
                                    onClick = {
                                        onMonthChange(num)
                                        monthExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Year Dropdown
                    Box {
                        Surface(
                            color = Color(0xFF2C2C2C),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { yearExpanded = true }
                        ) {
                            Text(
                                selectedYear,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(
                            expanded = yearExpanded,
                            onDismissRequest = { yearExpanded = false },
                            modifier = Modifier.background(Color(0xFF2C2C2C))
                        ) {
                            years.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(year, color = Color.White) },
                                    onClick = {
                                        onYearChange(year)
                                        yearExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (totalSpent == 0.0 || expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions for selected period", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier.size(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(120.dp)) {
                            var startAngle = -90f

                            expenses.forEachIndexed { index, item ->
                                val sweepAngle = ((item.totalAmount / totalSpent) * 360f).toFloat()
                                val color = CategoryColors[index % CategoryColors.size]

                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt)
                                )
                                startAngle += sweepAngle
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "₹${totalSpent.toInt()}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        expenses.take(4).forEachIndexed { index, item ->
                            val percentage = ((item.totalAmount / totalSpent) * 100).toInt()
                            val color = CategoryColors[index % CategoryColors.size]

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(color, shape = CircleShape)
                                )
                                Text(
                                    "${item.category} ($percentage%)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.nothing.expensetracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.nothing.expensetracker.data.local.CategoryExpense
import java.util.Locale

@Composable
fun CategoryBreakdownCard(totalExpense: Double, topCategories: List<CategoryExpense>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Category Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Donut Chart Placeholder Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Circular Placeholder (representing donut chart)
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawArc(
                        color = Color.DarkGray.copy(alpha = 0.3f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 24.dp.toPx())
                    )
                    
                    var currentStartAngle = -90f
                    val colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336))
                    
                    topCategories.forEachIndexed { index, category ->
                        val sweepAngle = if (totalExpense > 0) {
                            (category.totalAmount / totalExpense * 360).toFloat()
                        } else 0f
                        
                        drawArc(
                            color = colors.getOrElse(index) { Color.Gray },
                            startAngle = currentStartAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 24.dp.toPx())
                        )
                        currentStartAngle += sweepAngle
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Total Expense",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "₹%,.0f".format(Locale.getDefault(), totalExpense),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Categories List
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336))
                topCategories.forEachIndexed { index, category ->
                    val percentage = if (totalExpense > 0) {
                        (category.totalAmount / totalExpense * 100).toInt()
                    } else 0
                    
                    CategoryItem(
                        name = category.category,
                        amount = "₹%,.0f".format(Locale.getDefault(), category.totalAmount),
                        percentage = "$percentage%",
                        color = colors.getOrElse(index) { Color.Gray }
                    )
                }
            }

            // View All Button
            TextButton(
                onClick = { /* TODO */ },
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(name: String, amount: String, percentage: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = color
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = percentage,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.width(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

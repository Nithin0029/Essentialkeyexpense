package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun HistoryStatistics(statistics: HistoryStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(label = "Count", value = statistics.count.toString())
            StatItem(
                label = "Income",
                value = "₹%,.0f".format(Locale.getDefault(), statistics.totalIncome),
                valueColor = Color(0xFF4CAF50)
            )
            StatItem(
                label = "Expense",
                value = "₹%,.0f".format(Locale.getDefault(), statistics.totalExpense),
                valueColor = Color(0xFFF44336)
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = Color.White) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

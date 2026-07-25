package com.nothing.expensetracker.ui.dashboard

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
fun SummaryCards(today: Double, week: Double, month: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            modifier = Modifier.weight(1f),
            title = "Today",
            amount = "₹%,.0f".format(Locale.getDefault(), today),
            caption = "Today's Spending"
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            title = "Week",
            amount = "₹%,.0f".format(Locale.getDefault(), week),
            caption = "This Week"
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            title = "Month",
            amount = "₹%,.0f".format(Locale.getDefault(), month),
            caption = "This Month"
        )
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    caption: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.Gray
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray
            )
        }
    }
}

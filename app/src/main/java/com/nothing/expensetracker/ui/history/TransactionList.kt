package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nothing.expensetracker.data.local.Expense
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionList(
    expenses: List<Expense>,
    onExpenseClick: (Expense) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(
            items = expenses,
            key = { it.id }
        ) { expense ->
            TransactionCard(
                category = expense.category,
                notes = expense.notes,
                date = dateFormat.format(Date(expense.timestamp)),
                method = expense.paymentMethod,
                type = expense.type,
                amount = "₹${expense.amount.toInt()}",
                icon = getCategoryIcon(expense.category),
                friendName = expense.friendId,
                onClick = { onExpenseClick(expense) }
            )
        }
    }
}

private fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "food" -> Icons.Default.Fastfood
        "medical" -> Icons.Default.LocalHospital
        "shopping" -> Icons.Default.LocalOffer
        "movies", "entertainment" -> Icons.Default.LocalMovies
        "salary", "income" -> Icons.Default.Payments
        else -> Icons.Default.Payments
    }
}

package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.util.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionList(
    expenses: List<Expense>,
    sortOption: SortOption,
    onExpenseClick: (Expense) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    // Month grouping only makes sense for date-ordered lists; amount/category sorts stay flat.
    val groupByMonth = sortOption == SortOption.NEWEST || sortOption == SortOption.OLDEST
    val monthGroups = if (groupByMonth) {
        expenses.groupBy { monthFormat.format(Date(it.timestamp)) }
    } else {
        null
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (monthGroups != null) {
            monthGroups.forEach { (month, monthExpenses) ->
                stickyHeader(key = month) {
                    MonthHeader(label = month, expenses = monthExpenses)
                }
                items(
                    items = monthExpenses,
                    key = { it.id }
                ) { expense ->
                    TransactionCard(
                        category = expense.category,
                        notes = expense.notes,
                        date = dateFormat.format(Date(expense.timestamp)),
                        method = expense.paymentMethod,
                        type = expense.type,
                        amount = formatCurrency(expense.amount),
                        icon = getCategoryIcon(expense.category),
                        friendName = expense.friendId,
                        onClick = { onExpenseClick(expense) }
                    )
                }
            }
        } else {
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
                    amount = formatCurrency(expense.amount),
                    icon = getCategoryIcon(expense.category),
                    friendName = expense.friendId,
                    onClick = { onExpenseClick(expense) }
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(label: String, expenses: List<Expense>) {
    var income = 0.0
    var expense = 0.0
    expenses.forEach {
        if (!TransactionConstants.isNonSpendingCategory(it.type, it.category)) {
            if (it.type == "Credit") income += it.amount else expense += it.amount
        }
    }
    val net = income - expense
    val netColor = if (net >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = (if (net >= 0) "+" else "-") + formatCurrency(kotlin.math.abs(net)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = netColor
        )
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

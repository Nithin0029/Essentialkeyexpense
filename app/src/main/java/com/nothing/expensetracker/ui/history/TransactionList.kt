package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.util.formatCurrency
import com.nothing.expensetracker.util.getCategoryIcon
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionList(
    expenses: List<Expense>,
    sortOption: SortOption,
    onExpenseClick: (Expense) -> Unit,
    onAddForMonth: (timestamp: Long) -> Unit
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
                    // All expenses in a group share the same month/year, so any member's
                    // timestamp is a valid representative date for a new transaction in this month.
                    val representativeTimestamp = monthExpenses.first().timestamp
                    MonthHeader(
                        label = month,
                        expenses = monthExpenses,
                        onAddClick = { onAddForMonth(representativeTimestamp) }
                    )
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
private fun MonthHeader(label: String, expenses: List<Expense>, onAddClick: () -> Unit) {
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
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAddClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Add transaction in $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = (if (net >= 0) "+" else "-") + formatCurrency(kotlin.math.abs(net)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = netColor
        )
    }
}


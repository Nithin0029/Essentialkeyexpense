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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TransactionList() {
    val sampleTransactions = listOf(
        Triple("Food", "24 July, 02:30 PM", Triple("UPI", "-₹250", Icons.Default.Fastfood)),
        Triple("Medical", "23 July, 10:15 AM", Triple("Cash", "-₹700", Icons.Default.LocalHospital)),
        Triple("Salary", "22 July, 09:00 AM", Triple("Bank", "+₹45,000", Icons.Default.Payments)),
        Triple("Movies", "21 July, 08:45 PM", Triple("UPI", "-₹400", Icons.Default.LocalMovies)),
        Triple("Shopping", "20 July, 04:20 PM", Triple("UPI", "-₹1,200", Icons.Default.LocalOffer))
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(sampleTransactions) { (category, date, details) ->
            val (method, amount, icon) = details
            val amountColor = if (amount.startsWith("+")) Color(0xFF4CAF50) else Color(0xFFF44336)
            
            TransactionCard(
                category = category,
                date = date,
                method = method,
                amount = amount,
                icon = icon,
                amountColor = amountColor
            )
        }
    }
}

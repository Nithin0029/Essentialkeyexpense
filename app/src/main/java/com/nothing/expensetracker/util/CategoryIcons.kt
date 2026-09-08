package com.nothing.expensetracker.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for category -> icon mapping. Previously this mapping was duplicated
 * (and disagreed) across CategoryManagementScreen, TransactionList, and RecentTransactionsCard.
 */
fun getCategoryIcon(categoryName: String): ImageVector {
    return when (categoryName.lowercase()) {
        "food", "snacks" -> Icons.Default.Fastfood
        "groceries" -> Icons.Default.ShoppingCart
        "home" -> Icons.Default.Home
        "bills" -> Icons.Default.Receipt
        "fuel" -> Icons.Default.LocalGasStation
        "travel" -> Icons.Default.DirectionsTransit
        "shopping" -> Icons.Default.ShoppingBag
        "medical" -> Icons.Default.LocalHospital
        "fitness" -> Icons.AutoMirrored.Filled.DirectionsRun
        "salary", "income" -> Icons.Default.Payments
        "friends", "friend" -> Icons.Default.Group
        "college" -> Icons.Default.School
        "entertainment", "movies" -> Icons.Default.TheaterComedy
        "books" -> Icons.Default.Book
        "transfer" -> Icons.Default.SwapHoriz
        else -> Icons.Default.Category
    }
}

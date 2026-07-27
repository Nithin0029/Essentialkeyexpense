package com.nothing.expensetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object History : Screen("history", "History", Icons.Default.History)
    object Friends : Screen("friends", "Friends", Icons.Default.Group)
    object Reports : Screen("reports", "Reports", Icons.Default.PieChart)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object EditTransaction : Screen("edit_transaction/{expenseId}", "Edit Transaction", Icons.Default.History)
    object FriendDetail : Screen("friend_detail/{friendName}", "Friend Ledger", Icons.Default.Group)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.History,
    Screen.Friends,
    Screen.Reports,
    Screen.Settings
)

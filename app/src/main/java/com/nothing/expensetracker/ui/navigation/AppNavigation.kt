package com.nothing.expensetracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nothing.expensetracker.ui.dashboard.DashboardScreen
import com.nothing.expensetracker.ui.friends.FriendDetailScreen
import com.nothing.expensetracker.ui.friends.FriendsScreen
import com.nothing.expensetracker.ui.history.EditTransactionScreen
import com.nothing.expensetracker.ui.history.HistoryScreen
import com.nothing.expensetracker.ui.reports.ReportsScreen
import com.nothing.expensetracker.ui.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController = navController) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onEditTransaction = { expenseId ->
                        navController.navigate("edit_transaction/$expenseId")
                    }
                )
            }
            composable(Screen.Friends.route) {
                FriendsScreen(
                    onNavigateToLedger = { name ->
                        navController.navigate("friend_detail/$name")
                    }
                )
            }
            composable(Screen.Reports.route) {
                ReportsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Screen.EditTransaction.route,
                arguments = listOf(navArgument("expenseId") { type = NavType.LongType })
            ) {
                EditTransactionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFriends = { navController.navigate(Screen.Friends.route) }
                )
            }
            composable(
                route = Screen.FriendDetail.route,
                arguments = listOf(navArgument("friendName") { type = NavType.StringType })
            ) {
                FriendDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

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
import com.nothing.expensetracker.ui.settings.CategoryManagementScreen
import com.nothing.expensetracker.ui.settings.OverallBudgetScreen
import com.nothing.expensetracker.ui.settings.CategoryBudgetsScreen
import com.nothing.expensetracker.ui.auth.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AppNavigation(
    viewModel: MpinViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isAuthScreen = currentRoute?.startsWith("create_mpin") == true || 
                       currentRoute?.startsWith("confirm_mpin") == true || 
                       currentRoute?.startsWith("unlock_app") == true

    val startDestination = if (viewModel.isMpinSet()) {
        Screen.UnlockApp.route
    } else {
        Screen.CreateMpin.route
    }

    Scaffold(
        bottomBar = { 
            if (!isAuthScreen) {
                BottomNavigationBar(navController = navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.CreateMpin.route) {
                CreateMpinScreen(
                    onMpinCreated = { mpin ->
                        navController.navigate("confirm_mpin/$mpin")
                    }
                )
            }
            composable(
                route = Screen.ConfirmMpin.route,
                arguments = listOf(navArgument("mpin") { type = NavType.StringType })
            ) { backStackEntry ->
                val mpin = backStackEntry.arguments?.getString("mpin") ?: ""
                ConfirmMpinScreen(
                    originalMpin = mpin,
                    onMpinConfirmed = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.CreateMpin.route) { inclusive = true }
                        }
                    },
                    onMismatch = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.UnlockApp.route) {
                UnlockScreen(
                    onUnlocked = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.UnlockApp.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToAddTransaction = {
                        navController.navigate("edit_transaction/0")
                    },
                    onNavigateToBudget = {
                        navController.navigate(Screen.OverallBudget.route)
                    }
                )
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
                SettingsScreen(
                    onNavigateToCreateMpin = { navController.navigate(Screen.CreateMpin.route) },
                    onNavigateToChangeMpin = { navController.navigate(Screen.ChangeMpin.route) },
                    onNavigateToCategoryManagement = { navController.navigate(Screen.CategoryManagement.route) },
                    onNavigateToOverallBudget = { navController.navigate(Screen.OverallBudget.route) },
                    onNavigateToCategoryBudgets = { navController.navigate(Screen.CategoryBudgets.route) }
                )
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
            composable(Screen.ChangeMpin.route) {
                ChangeMpinScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.CategoryManagement.route) {
                CategoryManagementScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.OverallBudget.route) {
                OverallBudgetScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.CategoryBudgets.route) {
                CategoryBudgetsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

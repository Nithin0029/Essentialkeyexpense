package com.nothing.expensetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.data.local.Expense
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onEditTransaction: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val expenses by viewModel.expenses.collectAsState()
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HistoryHeader()
            HistorySearchBar()
            QuickFilterRow()
            
            if (expenses.isEmpty()) {
                EmptyHistory()
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    TransactionList(
                        expenses = expenses,
                        onExpenseClick = { expense ->
                            selectedExpense = expense
                            showBottomSheet = true
                        }
                    )
                }
            }
        }
    }

    if (showBottomSheet && selectedExpense != null) {
        TransactionDetailSheet(
            expense = selectedExpense!!,
            onDismiss = { showBottomSheet = false },
            onEdit = {
                showBottomSheet = false
                onEditTransaction(selectedExpense!!.id)
            },
            onDelete = {
                showDeleteDialog = true
            }
        )
    }

    if (showDeleteDialog && selectedExpense != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction?", color = Color.White) },
            text = { Text("This action cannot be undone.", color = Color.Gray) },
            confirmButton = {
                Button(
                    onClick = {
                        val expenseToDelete = selectedExpense!!
                        viewModel.deleteExpense(expenseToDelete)
                        showDeleteDialog = false
                        showBottomSheet = false
                        
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Transaction deleted",
                                actionLabel = "Undo"
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        )
    }
}

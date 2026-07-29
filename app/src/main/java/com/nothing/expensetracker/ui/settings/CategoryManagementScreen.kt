package com.nothing.expensetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.expensetracker.data.local.Category
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Deletion Flow State
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var showInUseDialog by remember { mutableStateOf(false) }
    var showFinalDeleteTransactionsDialog by remember { mutableStateOf(false) }
    var showMoveTransactionsDialog by remember { mutableStateOf(false) }
    var selectedReplacementCategory by remember { mutableStateOf("") }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (uiState.categories.isEmpty() && !uiState.isLoading) {
                EmptyCategoriesState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = uiState.categories,
                        key = { it.id }
                    ) { category ->
                        CategoryItem(
                            category = category,
                            onDeleteClick = { categoryToDelete = category }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            title = "Add Category",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, onResult ->
                viewModel.addCategory(name, onResult)
            }
        )
    }


    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${categoryToDelete!!.name}\"?\n\nThis action may affect existing transactions.", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = categoryToDelete!!
                        viewModel.deleteCategory(toDelete) { success, message ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Category deleted successfully.") }
                                categoryToDelete = null
                            } else if (message == "IN_USE") {
                                showInUseDialog = true
                            } else if (message != null) {
                                scope.launch { snackbarHostState.showSnackbar(message) }
                                categoryToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showInUseDialog && categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showInUseDialog = false
                categoryToDelete = null 
            },
            title = { Text("Category In Use", color = Color.White) },
            text = { Text("This category is currently used by existing transactions. Choose what you want to do.", color = Color.White) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            showInUseDialog = false
                            showMoveTransactionsDialog = true 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Move transactions to another category")
                    }
                    Button(
                        onClick = { 
                            showInUseDialog = false
                            showFinalDeleteTransactionsDialog = true 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete all associated transactions")
                    }
                    TextButton(
                        onClick = { 
                            showInUseDialog = false
                            categoryToDelete = null 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showMoveTransactionsDialog && categoryToDelete != null) {
        val otherCategories = uiState.categories.filter { it.id != categoryToDelete!!.id }
        
        AlertDialog(
            onDismissRequest = { 
                showMoveTransactionsDialog = false
                categoryToDelete = null 
            },
            title = { Text("Move Transactions", color = Color.White) },
            text = {
                Column {
                    Text("Select a category to move existing transactions to:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedReplacementCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Replacement Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            otherCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedReplacementCategory = cat.name
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedReplacementCategory.isNotBlank(),
                    onClick = {
                        val toDelete = categoryToDelete!!
                        viewModel.moveTransactionsAndDelete(toDelete, selectedReplacementCategory) { success, _ ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Transactions moved and category deleted.") }
                                showMoveTransactionsDialog = false
                                categoryToDelete = null
                                selectedReplacementCategory = ""
                            }
                        }
                    }
                ) {
                    Text("Confirm Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showMoveTransactionsDialog = false
                    categoryToDelete = null 
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showFinalDeleteTransactionsDialog && categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showFinalDeleteTransactionsDialog = false
                categoryToDelete = null 
            },
            title = { Text("Delete Transactions?", color = Color.White) },
            text = { Text("This will permanently delete all transactions under \"${categoryToDelete!!.name}\". This action cannot be undone.", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = categoryToDelete!!
                        viewModel.deleteTransactionsAndDelete(toDelete) { success, _ ->
                            if (success) {
                                scope.launch { snackbarHostState.showSnackbar("Category and transactions deleted.") }
                                showFinalDeleteTransactionsDialog = false
                                categoryToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                ) {
                    Text("Delete Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showFinalDeleteTransactionsDialog = false
                    categoryToDelete = null 
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun CategoryItem(
    category: Category,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Surface(
                        color = if (category.isSystem) Color.DarkGray else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (category.isSystem) "System" else "Custom",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (category.isSystem) Color.LightGray else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            if (category.name != "Friends") {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Locked",
                        tint = Color.DarkGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun getCategoryIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name.lowercase()) {
        "food", "snacks" -> Icons.Default.Fastfood
        "home" -> Icons.Default.Home
        "fuel" -> Icons.Default.LocalGasStation
        "travel" -> Icons.Default.DirectionsTransit
        "shopping" -> Icons.Default.ShoppingBag
        "medical" -> Icons.Default.LocalHospital
        "fitness" -> Icons.AutoMirrored.Filled.DirectionsRun
        "income" -> Icons.Default.Payments
        "friends" -> Icons.Default.Group
        "college" -> Icons.Default.School
        "entertainment" -> Icons.Default.TheaterComedy
        "books" -> Icons.Default.Book
        else -> Icons.Default.Category
    }
}

@Composable
fun EmptyCategoriesState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Category,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.DarkGray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Categories Found",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = "Pull to refresh or add a custom one.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun CategoryDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        error = null
                    },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name) { success, message ->
                        if (success) {
                            onDismiss()
                        } else {
                            error = message
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(20.dp)
    )
}

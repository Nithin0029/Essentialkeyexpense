package com.nothing.expensetracker.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
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
import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.util.getCategoryIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var subcategoryParent by remember { mutableStateOf<Category?>(null) }
    var categoryToRename by remember { mutableStateOf<Category?>(null) }
    var categoryToUnlink by remember { mutableStateOf<Category?>(null) }
    var expandedParentIds by remember { mutableStateOf(setOf<Long>()) }

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
                title = { Text("${uiState.scope.label} Categories", fontWeight = FontWeight.Bold) },
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
            CategoryScopeTabs(
                selected = uiState.scope,
                onSelect = { viewModel.setScope(it) }
            )

            if (uiState.topLevelCategories.isEmpty() && !uiState.isLoading) {
                EmptyCategoriesState(uiState.scope)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = uiState.topLevelCategories,
                        key = { it.id }
                    ) { category ->
                        val subcategories = uiState.subcategoriesOf(category.id)
                        val expanded = category.id in expandedParentIds
                        Column {
                            CategoryItem(
                                category = category,
                                scope = uiState.scope,
                                subcategoryCount = subcategories.size,
                                expanded = expanded,
                                onToggleExpand = {
                                    expandedParentIds = if (expanded) {
                                        expandedParentIds - category.id
                                    } else {
                                        expandedParentIds + category.id
                                    }
                                },
                                onAddSubcategoryClick = { subcategoryParent = category },
                                onEditClick = { categoryToRename = category },
                                onUnlinkClick = { categoryToUnlink = category },
                                onDeleteClick = { categoryToDelete = category }
                            )
                            if (expanded) {
                                Column(
                                    modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    subcategories.forEach { subcategory ->
                                        SubcategoryItem(
                                            category = subcategory,
                                            onEditClick = { categoryToRename = subcategory },
                                            onDeleteClick = { categoryToDelete = subcategory }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            title = "Add ${uiState.scope.label} Category",
            // Categories that exist on the other side can be linked in one tap instead of retyped.
            linkCandidates = uiState.linkableCandidates(),
            onLinkCandidateClick = { candidate ->
                viewModel.linkExistingCategory(candidate) { _, message ->
                    showAddDialog = false
                    message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                }
            },
            onDismiss = { showAddDialog = false },
            onConfirm = { name, onResult ->
                viewModel.addCategory(name = name) { success, message ->
                    // On success `message` is the "linked to your existing ..." note, if any.
                    if (success && message != null) {
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                    onResult(success, message)
                }
            }
        )
    }

    categoryToRename?.let { target ->
        CategoryDialog(
            title = "Rename \"${target.name}\"",
            initialName = target.name,
            confirmLabel = "Rename",
            // A linked category is a single row, so renaming it renames both sides at once.
            helperText = if (target.isLinked) {
                "\"${target.name}\" is linked across Debit and Credit — renaming updates both."
            } else null,
            onDismiss = { categoryToRename = null },
            onConfirm = { name, onResult ->
                viewModel.updateCategory(target, name) { success, message ->
                    if (success) categoryToRename = null
                    onResult(success, message)
                }
            }
        )
    }

    categoryToUnlink?.let { target ->
        val otherSide = if (uiState.scope == CategoryScope.DEBIT) "Credit" else "Debit"
        AlertDialog(
            onDismissRequest = { categoryToUnlink = null },
            title = { Text("Unlink from ${uiState.scope.label}", color = Color.White) },
            text = {
                Text(
                    "\"${target.name}\" will stop appearing in ${uiState.scope.label} transactions " +
                        "and stay a $otherSide category. Its totals will no longer be netted in Reports.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.removeFromScope(target) { _, message ->
                        categoryToUnlink = null
                        message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                    }
                }) {
                    Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToUnlink = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    subcategoryParent?.let { parent ->
        CategoryDialog(
            title = "Add Subcategory to \"${parent.name}\"",
            onDismiss = { subcategoryParent = null },
            onConfirm = { name, onResult ->
                viewModel.addCategory(name = name, parentId = parent.id) { success, message ->
                    if (success) expandedParentIds = expandedParentIds + parent.id
                    onResult(success, message)
                }
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
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Category deleted successfully.",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreCategory(toDelete)
                                    }
                                }
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

/** Switches the list between the Debit and Credit sides of the ledger. */
@Composable
fun CategoryScopeTabs(
    selected: CategoryScope,
    onSelect: (CategoryScope) -> Unit
) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = Color.Black,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        CategoryScope.entries.forEach { scope ->
            Tab(
                selected = scope == selected,
                onClick = { onSelect(scope) },
                text = {
                    Text(
                        text = scope.label,
                        fontWeight = if (scope == selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = Color.Gray
            )
        }
    }
}

@Composable
fun CategoryItem(
    category: Category,
    scope: CategoryScope,
    subcategoryCount: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddSubcategoryClick: () -> Unit,
    onEditClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isProtected = category.name in ExpenseRepository.PROTECTED_CATEGORIES
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = subcategoryCount > 0, onClick = onToggleExpand)
            ) {
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (category.isLinked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Linked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (subcategoryCount > 0) {
                            Text(
                                text = "  $subcategoryCount subcategor${if (subcategoryCount == 1) "y" else "ies"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                if (subcategoryCount > 0) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Color.Gray
                    )
                }
            }

            // Subcategories are a Debit-only concept, so the control is hidden on the Credit tab.
            if (scope == CategoryScope.DEBIT) {
                IconButton(onClick = onAddSubcategoryClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Subcategory",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (category.isLinked && !isProtected) {
                IconButton(onClick = onUnlinkClick) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Unlink from ${scope.label}",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (!isProtected) {
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

@Composable
fun SubcategoryItem(
    category: Category,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.SubdirectoryArrowRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
            IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyCategoriesState(scope: CategoryScope) {
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
            text = "No ${scope.label} Categories",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = "Tap + to add one.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String = "Save",
    helperText: String? = null,
    /** Categories on the other side of the ledger that can be linked in instead of retyped. */
    linkCandidates: List<Category> = emptyList(),
    onLinkCandidateClick: (Category) -> Unit = {},
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
                if (helperText != null) {
                    Text(
                        text = helperText,
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
                if (linkCandidates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Or link an existing category",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "It stays one category, and its two sides are netted in Reports.",
                        style = MaterialTheme.typography.labelSmall,
                        // Gray, not DarkGray: this dialog sits on a hardcoded #1E1E1E surface,
                        // against which DarkGray is barely legible.
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        linkCandidates.forEach { candidate ->
                            // Colours are set explicitly rather than inherited: this dialog
                            // hardcodes a dark surface, so the chip's default onSurface label
                            // would be dark-on-dark while the app theme is light.
                            AssistChip(
                                onClick = { onLinkCandidateClick(candidate) },
                                label = { Text(candidate.name, color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    labelColor = Color.White,
                                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
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
                Text(confirmLabel)
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

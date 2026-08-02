package com.nothing.expensetracker.ui.friends

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
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
import com.nothing.expensetracker.data.local.Friend
import com.nothing.expensetracker.ui.friends.FriendWithBalance
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateToLedger: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var friendToEdit by remember { mutableStateOf<Friend?>(null) }
    var friendToDelete by remember { mutableStateOf<Friend?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
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
                Icon(Icons.Default.Add, contentDescription = "Add Friend")
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
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search friends...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.friendsWithBalances.isEmpty() && !uiState.isLoading) {
                EmptyFriendsState(onAddClick = { showAddDialog = true })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = uiState.friendsWithBalances,
                        key = { it.friend.id }
                    ) { item ->
                        FriendItem(
                            item = item,
                            onClick = { onNavigateToLedger(item.friend.name) },
                            onEditClick = { friendToEdit = item.friend },
                            onDeleteClick = { friendToDelete = item.friend }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        FriendDialog(
            title = "Add Friend",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, onResult ->
                viewModel.addFriend(name, onResult)
            }
        )
    }

    if (friendToEdit != null) {
        FriendDialog(
            title = "Edit Friend",
            initialName = friendToEdit!!.name,
            showDelete = true,
            onDismiss = { friendToEdit = null },
            onConfirm = { name, onResult ->
                viewModel.updateFriend(friendToEdit!!, name, onResult)
            },
            onDelete = {
                friendToDelete = friendToEdit
                friendToEdit = null
            }
        )
    }

    if (friendToDelete != null) {
        var hasHistory by remember { mutableStateOf(false) }
        val friendName = friendToDelete!!.name
        
        LaunchedEffect(friendName) {
            hasHistory = viewModel.hasTransactions(friendName)
        }

        if (!hasHistory) {
            // Simple confirmation for friends with NO history
            AlertDialog(
                onDismissRequest = { friendToDelete = null },
                title = { Text("Delete Friend", color = Color.White) },
                text = { Text("Are you sure you want to delete \"$friendName\"?", color = Color.Gray) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteFriendOnly(friendToDelete!!)
                            friendToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendToDelete = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(20.dp)
            )
        } else {
            // Complex Choice Dialog for friends WITH history
            AlertDialog(
                onDismissRequest = { friendToDelete = null },
                title = { Text("Friend is used in transactions", color = Color.White) },
                text = {
                    Text(
                        text = "This friend is linked to existing transactions. Choose what you want to do.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.deleteFriendOnly(friendToDelete!!)
                                friendToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete Friend Only")
                        }
                        Button(
                            onClick = {
                                viewModel.deleteFriendAndTransactions(friendToDelete!!)
                                friendToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete Friend + All Transactions")
                        }
                        TextButton(
                            onClick = { friendToDelete = null },
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendItem(
    item: FriendWithBalance,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val friend = item.friend
    val balance = item.balance
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    Text(
                        text = friend.name.firstOrNull()?.uppercase() ?: "",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    val statusText = when {
                        balance.outstandingBalance > 0 -> "Friend owes you"
                        balance.outstandingBalance < 0 -> "You owe friend"
                        else -> "Settled"
                    }
                    val amountText = when {
                        balance.outstandingBalance > 0 -> "+ ₹${balance.outstandingBalance.toInt()}"
                        balance.outstandingBalance < 0 -> "- ₹${(-balance.outstandingBalance).toInt()}"
                        else -> "₹0"
                    }
                    val balanceColor = when {
                        balance.outstandingBalance > 0 -> Color(0xFF4CAF50) // Green
                        balance.outstandingBalance < 0 -> Color(0xFFF44336) // Red
                        else -> Color.Gray
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = amountText,
                            style = MaterialTheme.typography.bodySmall,
                            color = balanceColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Gray.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyFriendsState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.DarkGray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Friends Added",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add friends to track expenses with them.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add Friend")
        }
    }
}


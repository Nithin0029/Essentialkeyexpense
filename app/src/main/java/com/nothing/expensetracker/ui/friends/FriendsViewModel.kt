package com.nothing.expensetracker.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Friend
import com.nothing.expensetracker.data.local.FriendBalance
import com.nothing.expensetracker.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendWithBalance(
    val friend: Friend,
    val balance: FriendBalance
)

data class FriendsUiState(
    val friendsWithBalances: List<FriendWithBalance> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repository: FriendRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<FriendsUiState> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllFriends().distinctUntilChanged()
            } else {
                repository.searchFriends(query).distinctUntilChanged()
            }
        },
        repository.getFriendBalances().distinctUntilChanged()
    ) { friends, balances ->
        val balanceMap = balances.associateBy { it.friendName }
        val friendsWithBalances = friends.map { friend ->
            FriendWithBalance(
                friend = friend,
                balance = balanceMap[friend.name] ?: FriendBalance(friend.name, 0.0, 0.0, 0.0)
            )
        }
        FriendsUiState(
            friendsWithBalances = friendsWithBalances,
            searchQuery = _searchQuery.value,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FriendsUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    suspend fun hasTransactions(name: String) = repository.hasTransactions(name)

    fun addFriend(name: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onResult(false, "Name is required")
            return
        }

        viewModelScope.launch {
            val existing = repository.getFriendByName(trimmedName)
            if (existing != null) {
                onResult(false, "Friend already exists")
            } else {
                repository.insertFriend(Friend(name = trimmedName))
                onResult(true, null)
            }
        }
    }

    fun updateFriend(friend: Friend, newName: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) {
            onResult(false, "Name is required")
            return
        }

        viewModelScope.launch {
            val existing = repository.getFriendByName(trimmedName)
            if (existing != null && existing.id != friend.id) {
                onResult(false, "Another friend with this name already exists")
            } else {
                repository.updateFriend(friend.name, friend.copy(name = trimmedName, updatedAt = System.currentTimeMillis()))
                onResult(true, null)
            }
        }
    }

    fun deleteFriend(friend: Friend, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteFriend(friend)
            onResult(true, null)
        }
    }
}

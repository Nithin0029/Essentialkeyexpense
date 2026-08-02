package com.nothing.expensetracker.ui.friends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FriendDialog(
    title: String,
    initialName: String = "",
    showDelete: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, (Boolean, String?) -> Unit) -> Unit,
    onDelete: (() -> Unit)? = null
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
                    label = { Text("Friend Name") },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showDelete) {
                    Button(
                        onClick = { onDelete?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete")
                    }
                }
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = Color.White)
                }
                
                Button(
                    onClick = {
                        onConfirm(name) { success, message ->
                            if (success) {
                                onDismiss()
                            } else {
                                error = message
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = null,
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(20.dp)
    )
}

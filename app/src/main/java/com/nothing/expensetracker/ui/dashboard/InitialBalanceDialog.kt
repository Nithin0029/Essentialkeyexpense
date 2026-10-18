package com.nothing.expensetracker.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/**
 * Asks for the balances the user is holding right now, once per install.
 *
 * The app works forwards from an opening balance, but that is not a number anyone knows — it is
 * whatever an account held before the very first recorded transaction. What a person can answer
 * is what they have today, so that is what is asked for; the opening figure is solved for from it.
 *
 * Both fields are optional. Someone who skips simply starts from zero and can set the balances
 * later from Settings.
 */
@Composable
fun InitialBalanceDialog(
    onConfirm: (bankNow: Double, cashNow: Double) -> Unit,
    onSkip: () -> Unit
) {
    var bankText by remember { mutableStateOf("") }
    var cashText by remember { mutableStateOf("") }

    fun isAcceptable(value: String) = value.isEmpty() || value.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Set your balances", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    text = "Enter what you have right now. Everything you record from here on " +
                        "is added to these, so your totals stay true to your real accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = bankText,
                    onValueChange = { if (isAcceptable(it)) bankText = it },
                    label = { Text("Bank balance") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = cashText,
                    onValueChange = { if (isAcceptable(it)) cashText = it },
                    label = { Text("Cash in hand") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You can change these any time in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(bankText.toDoubleOrNull() ?: 0.0, cashText.toDoubleOrNull() ?: 0.0)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip", color = Color.Gray) }
        }
    )
}

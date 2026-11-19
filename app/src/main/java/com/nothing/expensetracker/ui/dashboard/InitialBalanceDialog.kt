package com.nothing.expensetracker.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.nothing.expensetracker.data.repository.AccountBalance

/**
 * Asks for the balances the user is holding right now, once per install.
 *
 * The app works forwards from an opening balance, but that is not a number anyone knows — it is
 * whatever an account held before the very first recorded transaction. What a person can answer
 * is what they have today, so that is what is asked for; the opening figure is solved for from it.
 *
 * A field per account, named after the account, rather than a fixed "Bank balance" and "Cash in
 * hand" pair. The pair was written against the literal names Bank and Cash, which meant that
 * after a rename — or on a restore that brought SBI and HDFC back from the spreadsheet — the
 * figures were written to accounts that did not exist, and silently thrown away.
 *
 * Every field is optional, and a field left alone is not an answer of zero: it is not written at
 * all, so a balance a restore has already recovered survives being asked about.
 */
@Composable
fun InitialBalanceDialog(
    accounts: List<AccountBalance>,
    onConfirm: (balances: Map<Long, Double>) -> Unit,
    onSkip: () -> Unit
) {
    // Keyed by account id, so nothing typed can end up against the wrong account.
    val entered = remember { mutableStateMapOf<Long, String>() }

    fun isAcceptable(value: String) = value.isEmpty() || value.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Set your balances", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Enter what you have right now. Everything you record from here on " +
                        "is added to these, so your totals stay true to your real accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))

                accounts.forEachIndexed { index, account ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = entered[account.id] ?: "",
                        onValueChange = { if (isAcceptable(it)) entered[account.id] = it },
                        label = { Text(account.name) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = if (index == accounts.lastIndex) {
                                ImeAction.Done
                            } else {
                                ImeAction.Next
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You can change these, rename them, or add another account any time " +
                        "in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    accounts.mapNotNull { account ->
                        entered[account.id]?.toDoubleOrNull()?.let { account.id to it }
                    }.toMap()
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip", color = Color.Gray) }
        }
    )
}

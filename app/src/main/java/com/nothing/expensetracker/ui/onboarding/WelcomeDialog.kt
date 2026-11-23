package com.nothing.expensetracker.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * What this app is, said once, at the only moment a person is willing to read it.
 *
 * The app asks to sign in to Google and then writes a spreadsheet into the user's Drive. Both of
 * those are reasonable things to be suspicious of, and nothing in the app explained either: a
 * fresh install opened straight onto an empty dashboard and a prompt demanding account balances,
 * which is a strange first thing to be asked by software you have just sideloaded past a
 * "Harmful app blocked" warning.
 *
 * Four points, in the order they matter to someone who has just installed it. Shown before the
 * balance prompt, not after, because the third point is what makes that prompt make sense.
 *
 * Reachable again from Settings, under About - the once-only version of this would otherwise be
 * unreadable the moment it is dismissed.
 */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Welcome",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Point(
                    heading = "Your records live in your own Google Sheet",
                    body = "Sign in from Settings and a spreadsheet called \"Expense Tracker " +
                        "Database\" is created in your Drive. Everything you record is written " +
                        "there. It is your file, in your account - nothing is sent anywhere else, " +
                        "and there is no account to create here."
                )
                Point(
                    heading = "That sheet is also your backup",
                    body = "Reinstall the app, or move to another phone, sign in with the same " +
                        "Google account, and your transactions, categories, friends and budgets " +
                        "all come back."
                )
                Point(
                    heading = "Start by saying what you have",
                    body = "Next you will be asked for the balance of each of your accounts. " +
                        "Enter what is really in each one right now, and every total after that " +
                        "is real money rather than a count from zero."
                )
                Point(
                    heading = "Then make it yours",
                    body = "Rename accounts to what you actually call them, add a second bank, a " +
                        "wallet or cash, and set monthly budgets. All of it is in Settings.",
                    last = true
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Get started") }
        }
    )
}

@Composable
private fun Point(heading: String, body: String, last: Boolean = false) {
    Text(
        text = heading,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        modifier = Modifier.fillMaxWidth()
    )
    if (!last) Spacer(modifier = Modifier.height(16.dp))
}

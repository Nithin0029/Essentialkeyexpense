package com.nothing.expensetracker.update.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nothing.expensetracker.update.model.VersionInfo

@Composable
fun UpdateDialog(
    versionInfo: VersionInfo,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!versionInfo.forceUpdate) onDismissClick() },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Update Available 🚀",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "A newer version of Essential Expense Tracker is available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VersionColumn(label = "Current", version = com.nothing.expensetracker.BuildConfig.VERSION_NAME)
                    VersionColumn(label = "Latest", version = versionInfo.versionName)
                }

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                Text(
                    text = "Release Notes:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                versionInfo.releaseNotes.forEach { note ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(text = "• ", color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Update", color = Color.Black)
            }
        },
        dismissButton = {
            if (!versionInfo.forceUpdate) {
                TextButton(onClick = onDismissClick) {
                    Text("Later", color = Color.Gray)
                }
            }
        }
    )
}

@Composable
private fun VersionColumn(label: String, version: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            text = version,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

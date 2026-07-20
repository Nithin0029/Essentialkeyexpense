package com.nothing.expensetracker.feature.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity

class QuickAddShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("QuickAddShortcut", "onCreate called")

        val selectedColor = intent.getStringExtra("SELECTED_COLOR") ?: "GREEN"
        Log.d("QuickAddShortcut", "Selected color: $selectedColor")

        // Check if "Display over other apps" permission is granted
        if (!Settings.canDrawOverlays(this)) {
            Log.d("QuickAddShortcut", "Permission not granted, requesting...")
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            finish()
            return
        }

        // Permission granted -> Start OverlayService directly
        Log.d("QuickAddShortcut", "Permission granted, starting service")
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra("SELECTED_COLOR", selectedColor)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("QuickAddShortcut", "Service start command sent")
        } catch (e: Exception) {
            Log.e("QuickAddShortcut", "Failed to start service", e)
            Toast.makeText(this, "Error starting overlay: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}

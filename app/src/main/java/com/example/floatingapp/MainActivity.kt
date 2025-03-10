package com.example.floatingapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.floatingapp.ui.theme.FloatingAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Toast.makeText(this, "MainActivity 啟動", Toast.LENGTH_SHORT).show()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "請授予懸浮窗權限", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivityForResult(intent, 1)
        } else {
            startFloatingService()
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "請啟用無障礙服務", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        setContent {
            FloatingAppTheme {
                // UI 內容可留空，功能由服務實現
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(this, "未授予懸浮窗權限，無法啟動", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        startService(intent)
        Toast.makeText(this, "正在啟動浮動服務", Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains("com.example.floatingapp/.ClickService") == true
    }
}
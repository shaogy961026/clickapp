package com.example.floatingapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // 使用自訂布局
        Log.d(TAG, "onCreate: 開始權限檢查流程")

        val statusText = findViewById<TextView>(R.id.status_text)
        val actionButton = findViewById<Button>(R.id.action_button)
        updateUI(statusText, actionButton)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: 恢復檢查權限")
        val statusText = findViewById<TextView>(R.id.status_text)
        val actionButton = findViewById<Button>(R.id.action_button)
        updateUI(statusText, actionButton)
    }

    private fun updateUI(statusText: TextView, actionButton: Button) {
        try {
            // 檢查懸浮窗權限
            if (!Settings.canDrawOverlays(this)) {
                statusText.text = "需要開啟「顯示在其他應用程式上方」權限"
                actionButton.text = "前往設置"
                actionButton.setOnClickListener {
                    Log.d(TAG, "用戶點擊前往懸浮窗設置")
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                return
            }

            // 檢查無障礙服務
            if (ClickService.instance == null) {
                statusText.text = "需要開啟「無障礙服務」以使用自動點擊功能"
                actionButton.text = "前往設置"
                actionButton.setOnClickListener {
                    Log.d(TAG, "用戶點擊前往無障礙服務設置")
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                return
            }

            // 所有權限滿足，啟動服務
            Log.d(TAG, "所有權限已滿足，啟動服務")
            statusText.text = "所有權限已開啟，正在啟動工具..."
            actionButton.isEnabled = false
            startService(Intent(this, FloatingService::class.java))
            Toast.makeText(this, "自動點擊工具已啟動", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "updateUI 異常: ${e.message}", e)
            statusText.text = "發生錯誤: ${e.message}"
            actionButton.isEnabled = false
        }
    }
}
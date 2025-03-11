package com.example.floatingapp

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.graphics.Path
import android.accessibilityservice.GestureDescription
import android.util.Log

class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
    }

    override fun onServiceConnected() {
        instance = this
        Log.d("ClickService", "無障礙服務已連接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不處理事件
    }

    override fun onInterrupt() {
        Log.d("ClickService", "無障礙服務中斷")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d("ClickService", "無障礙服務已斷開")
        return super.onUnbind(intent)
    }

    fun performClick(x: Int, y: Int) {
        try {
            // 創建點擊手勢路徑
            val clickPath = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }

            // 設定手勢描述，持續 50ms
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(clickPath, 0L, 50L))
                .build()

            // 分派手勢
            val result = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d("ClickService", "點擊成功: x=$x, y=$y, 按壓時間=50ms")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e("ClickService", "點擊取消: x=$x, y=$y")
                }
            }, null)

            if (!result) {
                Log.e("ClickService", "點擊分派失敗: x=$x, y=$y")
            }
        } catch (e: Exception) {
            Log.e("ClickService", "點擊失敗: ${e.message}")
        }
    }
}
package com.example.floatingapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.graphics.Path

class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
    }

    override fun onServiceConnected() {
        instance = this
        Log.d("ClickService", "無障礙服務已連線")
    }

    override fun onInterrupt() {
        instance = null
        Log.d("ClickService", "無障礙服務已中斷")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    fun performClick(x: Int, y: Int) {
        Log.d("ClickService", "開始執行點擊: x=$x, y=$y")
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        val gestureDescription = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 500L)) // 增加到 500ms
            .build()
        val result = dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Log.d("ClickService", "點擊手勢完成")
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Log.d("ClickService", "點擊手勢取消")
            }
        }, null)
        Log.d("ClickService", "dispatchGesture 結果: $result")
    }
}
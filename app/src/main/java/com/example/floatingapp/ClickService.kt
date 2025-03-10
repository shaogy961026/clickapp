package com.example.floatingapp

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.graphics.Path

class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    fun performClick(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat()) // 單點點擊，起點和終點相同
        }
        val gestureDescription = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 100L))
            .build()
        dispatchGesture(gestureDescription, null, null)
    }
}
package com.example.floatingapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var toolbarView: View
    private lateinit var clickPointView: ImageView
    private lateinit var playPauseButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var locationButton: ImageButton
    private var isClicking = false
    private var clickInterval = 1000L
    private var delayStartTime = 0L
    private var clickX = 500f
    private var clickY = 500f
    private val handler = Handler(Looper.getMainLooper()) // 修正棄用警告
    private val clickRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isClicking) {
                performClickAt(clickX, clickY)
                handler.postDelayed(this, clickInterval)
            }
        }
    }

    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var clickPointParams: WindowManager.LayoutParams

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "FloatingService 開始執行", Toast.LENGTH_SHORT).show()

        if (!Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(overlayIntent)
            Toast.makeText(this, "請授予懸浮窗權限以顯示工具列", Toast.LENGTH_LONG).show()
            return START_NOT_STICKY
        }

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupToolbar()
            setupClickPoint()
            setupListeners()
            Toast.makeText(this, "浮動工具列已啟動", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "啟動浮動服務失敗: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun setupToolbar() {
        toolbarView = LayoutInflater.from(this).inflate(R.layout.floating_view, null)
        playPauseButton = toolbarView.findViewById(R.id.playPauseButton)
        settingsButton = toolbarView.findViewById(R.id.settingsButton)
        locationButton = toolbarView.findViewById(R.id.locationButton)

        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        windowManager.addView(toolbarView, toolbarParams)
    }

    private fun setupClickPoint() {
        clickPointView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_add)
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        clickPointParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clickX.toInt()
            y = clickY.toInt()
        }

        windowManager.addView(clickPointView, clickPointParams)
    }

    private fun setupListeners() {
        clickPointView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = clickPointParams.x
                        initialY = clickPointParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        clickPointParams.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        clickPointParams.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        clickX = clickPointParams.x.toFloat()
                        clickY = clickPointParams.y.toFloat()
                        windowManager.updateViewLayout(clickPointView, clickPointParams)
                        return true
                    }
                }
                return false
            }
        })

        val toolbarLayout = toolbarView.findViewById<LinearLayout>(R.id.toolbarLayout)
        toolbarLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = toolbarParams.x
                        initialY = toolbarParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        toolbarParams.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        toolbarParams.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        windowManager.updateViewLayout(toolbarView, toolbarParams)
                        return true
                    }
                }
                return false
            }
        })

        playPauseButton.setOnClickListener {
            isClicking = !isClicking
            if (isClicking) {
                handler.postDelayed(clickRunnable, delayStartTime)
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                handler.removeCallbacks(clickRunnable)
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        locationButton.setOnClickListener {
            val displayMetrics = resources.displayMetrics
            val maxX = displayMetrics.widthPixels.toFloat() - clickPointView.width
            val maxY = displayMetrics.heightPixels.toFloat() - clickPointView.height

            clickX = maxX.coerceAtMost(2956f)
            clickY = maxY.coerceAtMost(1256f)
            clickPointParams.x = clickX.toInt()
            clickPointParams.y = clickY.toInt()
            windowManager.updateViewLayout(clickPointView, clickPointParams)
            Toast.makeText(this, "已設為預設位置: ($clickX, $clickY)", Toast.LENGTH_SHORT).show()
        }

        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
            val clickIntervalInput = dialogView.findViewById<android.widget.EditText>(R.id.clickIntervalInput)
            val delayStartInput = dialogView.findViewById<android.widget.EditText>(R.id.delayStartInput)

            clickIntervalInput.setText(clickInterval.toString())
            delayStartInput.setText(delayStartTime.toString())

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("設置")
                .setPositiveButton("確定") { _, _ ->
                    clickInterval = clickIntervalInput.text.toString().toLongOrNull() ?: 1000L
                    delayStartTime = delayStartInput.text.toString().toLongOrNull() ?: 0L
                    Toast.makeText(this, "設置已更新", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消") { _, _ -> }
                .create()

            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "無法顯示設置對話框: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun performClickAt(x: Float, y: Float) {
        val clickService = ClickService.instance
        if (clickService != null) {
            clickService.performClick(x.toInt(), y.toInt())
        } else {
            Toast.makeText(this, "請啟用無障礙服務", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(toolbarView)
            windowManager.removeView(clickPointView)
        } catch (e: Exception) {
            // 忽略移除失敗的異常
        }
        handler.removeCallbacks(clickRunnable)
    }
}
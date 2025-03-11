package com.example.floatingapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.InputMethodManager

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var toolbarView: View? = null
    private var clickPointView: ImageView? = null
    private var settingsView: View? = null
    private lateinit var playPauseButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var locationButton: ImageButton
    private lateinit var recordButton: ImageButton
    private lateinit var closeButton: ImageButton
    private var isClicking = false
    private var clickInterval = 1000L
    private var duration = 0L
    private var stopTime = 0L
    private var commonX = 2956f
    private var commonY = 1256f
    private var clickX = 0f
    private var clickY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isClicking) {
                val currentTime = System.currentTimeMillis()
                if (duration > 0 && currentTime >= stopTime) {
                    isClicking = false
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                    handler.removeCallbacks(this)
                    Toast.makeText(this@FloatingService, "持續時間已到，停止點擊", Toast.LENGTH_SHORT).show()
                    Log.d("FloatingService", "持續時間結束，停止點擊")
                    return
                }
                val location = IntArray(2)
                clickPointView?.getLocationOnScreen(location)
                clickX = (location[0] + (clickPointView?.width ?: 50) / 2f)
                clickY = (location[1] + (clickPointView?.height ?: 50) / 2f)
                Log.d("FloatingService", "執行點擊: x=$clickX, y=$clickY")
                performClickAt(clickX, clickY)
                handler.postDelayed(this, clickInterval)
            } else {
                Log.d("FloatingService", "停止點擊")
            }
        }
    }

    private var toolbarParams: WindowManager.LayoutParams? = null
    private var clickPointParams: WindowManager.LayoutParams? = null
    private var settingsParams: WindowManager.LayoutParams? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (toolbarView == null || clickPointView == null) {
            setupToolbar()
            setupClickPoint()
            setupListeners()
            Toast.makeText(this, "浮動工具列已啟動", Toast.LENGTH_SHORT).show()
        } else {
            Log.d("FloatingService", "服務已運行，重用現有視圖")
        }
        return START_STICKY
    }

    private fun setupToolbar() {
        toolbarView = LayoutInflater.from(this).inflate(R.layout.floating_view, null)
        playPauseButton = toolbarView!!.findViewById(R.id.playPauseButton)
        settingsButton = toolbarView!!.findViewById(R.id.settingsButton)
        locationButton = toolbarView!!.findViewById(R.id.locationButton)
        recordButton = toolbarView!!.findViewById(R.id.recordButton)
        closeButton = toolbarView!!.findViewById(R.id.closeButton)

        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
            layoutParams = WindowManager.LayoutParams(50, 50)
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        var initialX = 540
        var initialY = 960
        if (initialX + 50 > screenWidth) initialX = screenWidth - 50
        if (initialY + 50 > screenHeight) initialY = screenHeight - 50
        if (initialX < 0) initialX = 0
        if (initialY < 0) initialY = 0

        clickPointParams = WindowManager.LayoutParams(
            50,
            50,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        windowManager.addView(clickPointView, clickPointParams)
        clickX = clickPointParams!!.x + 25f
        clickY = clickPointParams!!.y + 25f
    }

    private fun showSettingsWindow() {
        if (settingsView != null) {
            windowManager.removeView(settingsView)
        }

        // 定義 intervalEdit 在函數範圍
        lateinit var intervalEdit: EditText

        settingsView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFEEEEEE.toInt())

            // 點擊間隔
            addView(TextView(this@FloatingService).apply {
                text = "點擊間隔 (ms, 100-10000):"
            })
            intervalEdit = EditText(this@FloatingService).apply {
                setText(clickInterval.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(intervalEdit)

            // 持續時間
            addView(TextView(this@FloatingService).apply {
                text = "持續時間 (秒, 0 表示無限):"
            })
            val durationEdit = EditText(this@FloatingService).apply {
                setText((duration / 1000).toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(durationEdit)

            // 常用位置 X
            addView(TextView(this@FloatingService).apply {
                text = "常用位置 X:"
            })
            val xEdit = EditText(this@FloatingService).apply {
                setText(commonX.toInt().toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(xEdit)

            // 常用位置 Y
            addView(TextView(this@FloatingService).apply {
                text = "常用位置 Y:"
            })
            val yEdit = EditText(this@FloatingService).apply {
                setText(commonY.toInt().toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(yEdit)

            // 確認按鈕
            val confirmButton = Button(this@FloatingService).apply {
                text = "確認"
                setOnClickListener {
                    val interval = intervalEdit.text.toString().toLongOrNull() ?: clickInterval
                    clickInterval = interval.coerceIn(100L, 10000L)

                    val durationSec = durationEdit.text.toString().toLongOrNull() ?: (duration / 1000)
                    duration = if (durationSec > 0) durationSec * 1000 else 0

                    commonX = (xEdit.text.toString().toFloatOrNull() ?: commonX).coerceAtLeast(0f)
                    commonY = (yEdit.text.toString().toFloatOrNull() ?: commonY).coerceAtLeast(0f)

                    windowManager.removeView(settingsView)
                    settingsView = null
                    Toast.makeText(this@FloatingService, "設定已更新", Toast.LENGTH_SHORT).show()
                }
            }
            addView(confirmButton)
        }

        settingsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(settingsView, settingsParams)

        // 自動聚焦並彈出鍵盤
        intervalEdit.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(intervalEdit, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupListeners() {
        playPauseButton.setOnClickListener {
            isClicking = !isClicking
            if (isClicking) {
                if (ClickService.instance == null) {
                    Toast.makeText(this, "無障礙服務未啟用，請檢查設置", Toast.LENGTH_LONG).show()
                    isClicking = false
                    return@setOnClickListener
                }
                Log.d("FloatingService", "開始點擊")
                if (duration > 0) {
                    stopTime = System.currentTimeMillis() + duration
                }
                handler.post(clickRunnable)
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                Log.d("FloatingService", "手動停止點擊")
                handler.removeCallbacks(clickRunnable)
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        settingsButton.setOnClickListener {
            showSettingsWindow()
        }

        locationButton.setOnClickListener {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            var targetX = commonX.toInt()
            var targetY = commonY.toInt()
            if (targetX + 50 > screenWidth) targetX = screenWidth - 50
            if (targetY + 50 > screenHeight) targetY = screenHeight - 50
            if (targetX < 0) targetX = 0
            if (targetY < 0) targetY = 0

            clickX = targetX + 25f
            clickY = targetY + 25f
            clickPointParams?.x = targetX
            clickPointParams?.y = targetY
            clickPointView?.let { windowManager.updateViewLayout(it, clickPointParams) }
            Toast.makeText(this, "已移動到常用位置: x=$clickX, y=$clickY", Toast.LENGTH_SHORT).show()
        }

        recordButton.setOnClickListener {
            Toast.makeText(this, "錄製功能已移除", Toast.LENGTH_SHORT).show()
        }

        closeButton.setOnClickListener {
            Log.d("FloatingService", "關閉按鈕點擊，停止服務")
            stopSelf()
        }

        val toolbarLayout = toolbarView!!.findViewById<LinearLayout>(R.id.toolbarLayout)
        toolbarLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = toolbarParams?.x ?: 0
                        initialY = toolbarParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        toolbarParams?.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        toolbarParams?.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        toolbarView?.let { windowManager.updateViewLayout(it, toolbarParams) }
                        return true
                    }
                }
                return false
            }
        })

        clickPointView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (isClicking) {
                    Toast.makeText(this@FloatingService, "點擊進行中，無法移動", Toast.LENGTH_SHORT).show()
                    return true
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = clickPointParams?.x ?: 0
                        initialY = clickPointParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        clickPointParams?.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        clickPointParams?.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        clickPointView?.let { windowManager.updateViewLayout(it, clickPointParams) }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun performClickAt(x: Float, y: Float) {
        Log.d("FloatingService", "準備執行點擊於: x=$x, y=$y")
        if (ClickService.instance == null) {
            Log.e("FloatingService", "無障礙服務不可用")
            isClicking = false
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            handler.removeCallbacks(clickRunnable)
            Toast.makeText(this, "無障礙服務斷開，請重啟應用或設備", Toast.LENGTH_LONG).show()
            return
        }
        ClickService.instance?.performClick(x.toInt(), y.toInt())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clickRunnable)
        cleanupViews()
        Log.d("FloatingService", "服務已銷毀")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("FloatingService", "應用被滑掉，清理並停止服務")
        handler.removeCallbacks(clickRunnable)
        cleanupViews()
        stopSelf()
    }

    private fun cleanupViews() {
        try {
            toolbarView?.let { windowManager.removeView(it) }
            clickPointView?.let { windowManager.removeView(it) }
            settingsView?.let { windowManager.removeView(it) }
            toolbarView = null
            clickPointView = null
            settingsView = null
        } catch (e: Exception) {
            Log.e("FloatingService", "移除視圖失敗: ${e.message}")
        }
    }
}
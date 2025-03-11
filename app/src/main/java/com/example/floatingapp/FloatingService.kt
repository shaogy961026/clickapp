package com.example.floatingapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var toolbarView: View
    private lateinit var clickPointView: ImageView
    private lateinit var playPauseButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var locationButton: ImageButton
    private lateinit var recordButton: ImageButton
    private lateinit var closeButton: ImageButton
    private var isClicking = false
    private var isRecording = false
    private var clickInterval = 1000L
    private var clickX = 0f // 點擊座標
    private var clickY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isClicking) {
                Log.d("FloatingService", "執行點擊: x=$clickX, y=$clickY")
                performClickAt(clickX, clickY)
                handler.postDelayed(this, clickInterval)
            } else {
                Log.d("FloatingService", "停止點擊")
            }
        }
    }

    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var clickPointParams: WindowManager.LayoutParams

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        cleanupViewsIfExist()
        setupToolbar()
        setupClickPoint()
        setupListeners()
        Toast.makeText(this, "浮動工具列已啟動", Toast.LENGTH_SHORT).show()
        return START_STICKY
    }

    private fun setupToolbar() {
        toolbarView = LayoutInflater.from(this).inflate(R.layout.floating_view, null)
        playPauseButton = toolbarView.findViewById(R.id.playPauseButton)
        settingsButton = toolbarView.findViewById(R.id.settingsButton)
        locationButton = toolbarView.findViewById(R.id.locationButton)
        recordButton = toolbarView.findViewById(R.id.recordButton)
        closeButton = toolbarView.findViewById(R.id.closeButton)

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
            // 強制設置寬高，避免初始化為 0
            layoutParams = WindowManager.LayoutParams(50, 50)
        }

        clickPointParams = WindowManager.LayoutParams(
            50, // 固定寬度
            50, // 固定高度
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 540 // 初始位置
            y = 960
        }

        windowManager.addView(clickPointView, clickPointParams)
        clickX = clickPointParams.x + 25f // 中心點
        clickY = clickPointParams.y + 25f
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
                handler.post(clickRunnable)
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                Log.d("FloatingService", "停止點擊")
                handler.removeCallbacks(clickRunnable)
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        settingsButton.setOnClickListener {
            Toast.makeText(this, "設置功能尚未實作", Toast.LENGTH_SHORT).show()
        }

        locationButton.setOnClickListener {
            Toast.makeText(this, "定位功能尚未實作", Toast.LENGTH_SHORT).show()
        }

        recordButton.setOnClickListener {
            if (isClicking) {
                Toast.makeText(this, "請先停止點擊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isRecording = true
            Toast.makeText(this, "請點擊螢幕設定位置", Toast.LENGTH_SHORT).show()
            recordButton.setImageResource(android.R.drawable.ic_menu_camera)
            // 添加全屏透明覆蓋層來捕捉觸控
            val overlayView = View(this).apply {
                layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            }
            windowManager.addView(overlayView, overlayView.layoutParams)
            overlayView.setOnTouchListener { _, event ->
                if (isRecording && event.action == MotionEvent.ACTION_DOWN) {
                    clickX = event.rawX
                    clickY = event.rawY
                    clickPointParams.x = (clickX - 25f).toInt() // 中心對準
                    clickPointParams.y = (clickY - 25f).toInt()
                    windowManager.updateViewLayout(clickPointView, clickPointParams)
                    isRecording = false
                    recordButton.setImageResource(android.R.drawable.ic_menu_edit)
                    windowManager.removeView(overlayView)
                    Toast.makeText(this, "點擊位置已更新: x=$clickX, y=$clickY", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    false
                }
            }
        }

        closeButton.setOnClickListener {
            Log.d("FloatingService", "關閉按鈕點擊，停止服務")
            stopSelf()
        }

        val toolbarLayout = toolbarView.findViewById<LinearLayout>(R.id.toolbarLayout)
        toolbarLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
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
            windowManager.removeView(toolbarView)
            windowManager.removeView(clickPointView)
        } catch (e: Exception) {
            Log.e("FloatingService", "移除視圖失敗: ${e.message}")
        }
    }

    private fun cleanupViewsIfExist() {
        try {
            if (::toolbarView.isInitialized) windowManager.removeView(toolbarView)
            if (::clickPointView.isInitialized) windowManager.removeView(clickPointView)
        } catch (e: Exception) {
            Log.e("FloatingService", "清理舊視圖失敗: ${e.message}")
        }
    }
}
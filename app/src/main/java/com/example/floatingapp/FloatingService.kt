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
    private lateinit var closeButton: ImageButton
    private var isClicking = false
    private var clickInterval = 1000L
    // 固定測試座標為螢幕中間
    private var clickX = 540f // 假設螢幕寬度約 1080
    private var clickY = 960f // 假設螢幕高度約 1920
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
        }

        clickPointParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clickX.toInt()
            y = clickY.toInt()
        }

        windowManager.addView(clickPointView, clickPointParams)
    }

    private fun setupListeners() {
        // 暫時只測試 Play 按鈕，其他功能先註解
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

        // 其他按鈕先註解，避免干擾測試
        /*
        settingsButton.setOnClickListener { }
        locationButton.setOnClickListener { }
        closeButton.setOnClickListener { stopSelf() }
        */

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
    }

    private fun performClickAt(x: Float, y: Float) {
        Log.d("FloatingService", "準備執行點擊於: x=$x, y=$y")
        ClickService.instance?.performClick(x.toInt(), y.toInt())
            ?: run {
                Log.e("FloatingService", "無障礙服務不可用")
                isClicking = false
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                handler.removeCallbacks(clickRunnable)
                Toast.makeText(this, "無障礙服務斷開，點擊停止", Toast.LENGTH_LONG).show()
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clickRunnable)
        try {
            windowManager.removeView(toolbarView)
            windowManager.removeView(clickPointView)
        } catch (e: Exception) {
            Log.e("FloatingService", "銷毀失敗: ${e.message}")
        }
    }
}
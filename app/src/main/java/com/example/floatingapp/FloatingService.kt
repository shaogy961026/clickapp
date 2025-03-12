package com.example.floatingapp

import android.app.Service
import android.content.Context
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
import android.view.Surface
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
    private lateinit var timerText: TextView
    private var isClicking = false
    private var clickInterval = 1000L
    private var duration = 0L
    private var stopTime = 0L
    private var commonX = 2682f // 常用位置 X（不含狀態列）
    private var commonY = 1293f // 常用位置 Y（不含狀態列）
    private var rawCenterX = 0f // 含狀態列的中心點 X（用於點擊）
    private var rawCenterY = 0f // 含狀態列的中心點 Y（用於點擊）
    private var centerX = 0f    // 不含狀態列的中心點 X（用於顯示和儲存）
    private var centerY = 0f    // 不含狀態列的中心點 Y（用於顯示和儲存）
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isClicking) {
                val currentTime = System.currentTimeMillis()
                if (duration > 0 && currentTime >= stopTime) {
                    isClicking = false
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                    handler.removeCallbacks(this)
                    updateTimerText()
                    updateButtonStates()
                    Toast.makeText(this@FloatingService, "持續時間已到，停止點擊", Toast.LENGTH_SHORT).show()
                    Log.d("FloatingService", "持續時間結束，停止點擊")
                    return
                }
                Log.d("FloatingService", "執行點擊: x=$rawCenterX, y=$rawCenterY")
                performClickAt(rawCenterX, rawCenterY)
                handler.postDelayed(this, clickInterval)
            } else {
                Log.d("FloatingService", "停止點擊")
            }
        }
    }
    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            updateTimerText()
            if (isClicking && duration > 0) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private var toolbarParams: WindowManager.LayoutParams? = null
    private var clickPointParams: WindowManager.LayoutParams? = null
    private var settingsParams: WindowManager.LayoutParams? = null
    private val clickPointSize = 50 // clickPointView 的寬高 (dp)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        loadCommonPosition()
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
        timerText = toolbarView!!.findViewById(R.id.timerText)

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
        updateTimerText()
    }

    private fun setupClickPoint() {
        clickPointView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_add)
        }

        val clickPointWidth = dpToPx(clickPointSize)
        val clickPointHeight = dpToPx(clickPointSize)

        clickPointParams = WindowManager.LayoutParams(
            clickPointWidth,
            clickPointHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(clickPointView, clickPointParams)
        updateClickPosition(commonX + getStatusBarHeight(), commonY) // 初始化時轉換
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun showSettingsWindow() {
        if (settingsView != null) {
            windowManager.removeView(settingsView)
        }

        val wasClicking = isClicking
        if (isClicking) {
            isClicking = false
            handler.removeCallbacks(clickRunnable)
            handler.removeCallbacks(timerRunnable)
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            updateButtonStates()
        }

        lateinit var intervalEdit: EditText

        settingsView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFEEEEEE.toInt())

            addView(TextView(this@FloatingService).apply {
                text = "點擊間隔 (ms, 100-10000):"
            })
            intervalEdit = EditText(this@FloatingService).apply {
                setText(clickInterval.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(intervalEdit)

            addView(TextView(this@FloatingService).apply {
                text = "持續時間 (秒, 0 表示無限):"
            })
            val durationEdit = EditText(this@FloatingService).apply {
                setText((duration / 1000).toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(durationEdit)

            addView(TextView(this@FloatingService).apply {
                text = "常用位置 X:"
            })
            val xEdit = EditText(this@FloatingService).apply {
                setText(centerX.toInt().toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(xEdit)

            addView(TextView(this@FloatingService).apply {
                text = "常用位置 Y:"
            })
            val yEdit = EditText(this@FloatingService).apply {
                setText(centerY.toInt().toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(yEdit)

            val savePositionButton = Button(this@FloatingService).apply {
                text = "儲存常用位置"
                setOnClickListener {
                    commonX = centerX
                    commonY = centerY
                    saveCommonPosition()
                    Toast.makeText(this@FloatingService, "已儲存常用位置: x=$commonX, y=$commonY", Toast.LENGTH_SHORT).show()
                }
            }
            addView(savePositionButton)

            val confirmButton = Button(this@FloatingService).apply {
                text = "確認"
                setOnClickListener {
                    val interval = intervalEdit.text.toString().toLongOrNull() ?: clickInterval
                    clickInterval = interval.coerceIn(100L, 10000L)

                    val durationSec = durationEdit.text.toString().toLongOrNull() ?: (duration / 1000)
                    duration = if (durationSec > 0) durationSec * 1000 else 0
                    updateTimerText()

                    val newX = (xEdit.text.toString().toFloatOrNull() ?: centerX).coerceAtLeast(0f)
                    val newY = (yEdit.text.toString().toFloatOrNull() ?: centerY).coerceAtLeast(0f)
                    updateClickPosition(newX, newY)

                    windowManager.removeView(settingsView)
                    settingsView = null

                    if (wasClicking) {
                        isClicking = true
                        if (duration > 0) {
                            stopTime = System.currentTimeMillis() + duration
                            handler.post(timerRunnable)
                        }
                        handler.post(clickRunnable)
                        playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                        updateButtonStates()
                    }

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

        intervalEdit.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(intervalEdit, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateTimerText() {
        if (duration == 0L) {
            timerText.text = "剩餘：無限"
        } else if (isClicking) {
            val remainingTime = (stopTime - System.currentTimeMillis()).coerceAtLeast(0) / 1000
            timerText.text = "剩餘：$remainingTime 秒"
        } else {
            timerText.text = "剩餘：${duration / 1000} 秒"
        }
    }

    private fun updateButtonStates() {
        settingsButton.isEnabled = !isClicking
        locationButton.isEnabled = !isClicking
        recordButton.isEnabled = !isClicking
    }

    private fun updateClickPosition(rawX: Float, rawY: Float) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val clickPointWidth = dpToPx(clickPointSize).toFloat()
        val clickPointHeight = dpToPx(clickPointSize).toFloat()
        val statusBarHeight = getStatusBarHeight()

        // 儲存含狀態列的座標（用於點擊）
        rawCenterX = rawX
        rawCenterY = rawY

        // 根據螢幕方向計算不含狀態列的座標
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> { // 縱向
                centerX = rawX
                centerY = rawY - statusBarHeight
            }
            Surface.ROTATION_90 -> { // 橫向，狀態列在左側
                centerX = rawX - statusBarHeight
                centerY = rawY
            }
            Surface.ROTATION_270 -> { // 橫向，狀態列在右側
                centerX = rawX
                centerY = rawY
            }
        }

        // 計算左上角位置，使中心點精準對齊
        var targetX = centerX - clickPointWidth / 2
        var targetY = centerY - clickPointHeight / 2

        // 調整邊界
        if (targetX + clickPointWidth > screenWidth) targetX = screenWidth - clickPointWidth
        if (targetY + clickPointHeight > screenHeight) targetY = screenHeight - clickPointHeight
        if (targetX < 0) targetX = 0f
        if (targetY < 0) targetY = 0f

        // 更新中心點（不含狀態列）
        centerX = targetX + clickPointWidth / 2
        centerY = targetY + clickPointHeight / 2

        clickPointParams?.x = targetX.toInt()
        clickPointParams?.y = targetY.toInt()
        clickPointView?.let { windowManager.updateViewLayout(it, clickPointParams) }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
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
                    handler.post(timerRunnable)
                }
                handler.post(clickRunnable)
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                updateButtonStates()
            } else {
                Log.d("FloatingService", "手動停止點擊")
                handler.removeCallbacks(clickRunnable)
                handler.removeCallbacks(timerRunnable)
                updateTimerText()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                updateButtonStates()
            }
        }

        settingsButton.setOnClickListener {
            if (isClicking) {
                Toast.makeText(this, "點擊進行中，請先停止點擊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSettingsWindow()
        }

        locationButton.setOnClickListener {
            if (isClicking) {
                Toast.makeText(this, "點擊進行中，請先停止點擊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateClickPosition(commonX, commonY + getStatusBarHeight()) // 轉換為含狀態列座標
            Toast.makeText(this, "已移動到常用位置: x=$centerX, y=$centerY", Toast.LENGTH_SHORT).show()
        }

        recordButton.setOnClickListener {
            if (isClicking) {
                Toast.makeText(this, "點擊進行中，請先停止點擊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 切換為錄製中圖標
            recordButton.setImageResource(android.R.drawable.ic_menu_camera)
            Toast.makeText(this, "請點擊螢幕以錄製位置", Toast.LENGTH_SHORT).show()

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
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val newRawX = event.rawX
                    val newRawY = event.rawY
                    updateClickPosition(newRawX, newRawY)
                    windowManager.removeView(overlayView)
                    // 錄製完成後恢復原始圖標
                    recordButton.setImageResource(android.R.drawable.ic_menu_add)
                    Toast.makeText(this, "點擊位置已更新: x=$centerX, y=$centerY", Toast.LENGTH_SHORT).show()
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
    }

    private fun performClickAt(x: Float, y: Float) {
        Log.d("FloatingService", "準備執行點擊於: x=$x, y=$y")
        if (ClickService.instance == null) {
            Log.e("FloatingService", "無障礙服務不可用")
            isClicking = false
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            handler.removeCallbacks(clickRunnable)
            handler.removeCallbacks(timerRunnable)
            updateTimerText()
            updateButtonStates()
            Toast.makeText(this, "無障礙服務斷開，請重啟應用或設備", Toast.LENGTH_LONG).show()
            return
        }
        ClickService.instance?.performClick(x.toInt(), y.toInt())
    }

    private fun saveCommonPosition() {
        val prefs = getSharedPreferences("FloatingAppPrefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putFloat("commonX", centerX) // 儲存不含狀態列的座標
            putFloat("commonY", centerY)
            apply()
        }
    }

    private fun loadCommonPosition() {
        val prefs = getSharedPreferences("FloatingAppPrefs", Context.MODE_PRIVATE)
        commonX = prefs.getFloat("commonX", 2956f)
        commonY = prefs.getFloat("commonY", 1256f)
        // 初始化時，將 commonX, commonY 轉換回含狀態列座標
        val statusBarHeight = getStatusBarHeight()
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                rawCenterX = commonX
                rawCenterY = commonY + statusBarHeight
            }
            Surface.ROTATION_90 -> {
                rawCenterX = commonX + statusBarHeight
                rawCenterY = commonY
            }
            Surface.ROTATION_270 -> {
                rawCenterX = commonX
                rawCenterY = commonY + statusBarHeight
            }
        }
        centerX = commonX
        centerY = commonY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clickRunnable)
        handler.removeCallbacks(timerRunnable)
        cleanupViews()
        Log.d("FloatingService", "服務已銷毀")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("FloatingService", "應用被滑掉，清理並停止服務")
        handler.removeCallbacks(clickRunnable)
        handler.removeCallbacks(timerRunnable)
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
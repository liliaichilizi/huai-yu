package com.operit.huaiyu

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.math.hypot

class OverlayService : Service() {

    private var isViewInitialized = false
    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    private var bubbleView: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // Gesture state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var tapCount = 0
    private var tapResetRunnable: Runnable? = null
    private var isDragging = false
    private var isLongPressing = false
    private var longPressRunnable: Runnable? = null
    private var lastMoveTime = 0L
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    private val flingVelocityThreshold = 2000f
    private val dragThreshold = 15f

    companion object {
        const val ACTION_UPDATE_SIZE = "com.operit.huaiyu.ACTION_UPDATE_SIZE"
        const val EXTRA_PET_SIZE = "extra_pet_size"
        private const val CHANNEL_ID = "huaiyu_overlay_channel"
        private const val NOTIFICATION_ID = 1
        private const val LONG_PRESS_TIMEOUT = 500L
        private const val DOUBLE_TAP_TIMEOUT = 300L
    }

    private val singleTapTexts = listOf(
        "干嘛戳我", "嗯？", "摸摸~", "痒痒!", "哼",
        "又戳！", "有事？", "喵~", "轻点啦", "看什么看"
    )

    private val multiTapTexts = mapOf(
        2 to "又戳？！",
        3 to "你够了啊！",
        4 to "再戳就生气了！",
        5 to "…我生气了"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_SIZE -> {
                val sizeDp = intent.getIntExtra(EXTRA_PET_SIZE, PetPrefs.getPetSize(this))
                updatePetSize(sizeDp)
            }
            else -> {
                if (!isViewInitialized) {
                    notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                    setupOverlayView()
                    createNotificationChannel()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    isViewInitialized = true
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "淮鱼桌宠", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("淮鱼桌宠")
            .setContentText("桌宠正在陪着你")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupOverlayView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            setBackgroundColor(0x00000000)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet/pet.html")
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val petSize = dp(PetPrefs.getPetSize(this))
        layoutParams = WindowManager.LayoutParams(
            petSize, petSize, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        setupTouchHandler()
        windowManager.addView(webView, layoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandler() {
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isLongPressing = false
                    lastMoveTime = System.currentTimeMillis()
                    lastMoveX = event.rawX
                    lastMoveY = event.rawY
                    velocityX = 0f
                    velocityY = 0f

                    longPressRunnable = Runnable {
                        if (!isDragging) {
                            isLongPressing = true
                            onLongPress()
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val distance = hypot(dx, dy)

                    if (distance > dragThreshold) {
                        isDragging = true
                        longPressRunnable?.let { handler.removeCallbacks(it) }

                        val now = System.currentTimeMillis()
                        val dt = (now - lastMoveTime).coerceAtLeast(1)
                        velocityX = (event.rawX - lastMoveX) / dt * 1000f
                        velocityY = (event.rawY - lastMoveY) / dt * 1000f
                        lastMoveTime = now
                        lastMoveX = event.rawX
                        lastMoveY = event.rawY

                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(webView, layoutParams)
                        updateBubblePosition()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    if (isDragging) {
                        val speed = hypot(velocityX, velocityY)
                        if (speed > flingVelocityThreshold) {
                            onFling()
                        }
                    } else if (!isLongPressing) {
                        onTap()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun onTap() {
        tapCount++
        tapResetRunnable?.let { handler.removeCallbacks(it) }

        tapResetRunnable = Runnable {
            handleTapResult(tapCount)
            tapCount = 0
        }
        handler.postDelayed(tapResetRunnable!!, DOUBLE_TAP_TIMEOUT)
    }

    private fun handleTapResult(count: Int) {
        when {
            count == 1 -> {
                showBubble(singleTapTexts.random())
                triggerAnimation("poked", 600)
            }
            count == 2 -> {
                showBubble(multiTapTexts[2] ?: "又戳？！")
                triggerAnimation("doubletap", 700)
            }
            count >= 5 -> {
                showBubble(multiTapTexts[5] ?: "…我生气了")
                triggerAnimation("angry", 800)
            }
            count >= 3 -> {
                showBubble(multiTapTexts[count.coerceAtMost(4)] ?: "你够了啊！")
                triggerAnimation("angry", 700)
            }
        }
    }

    private fun onLongPress() {
        showBubble("…别看我")
        triggerAnimation("hideface", 2000)
    }

    private fun onFling() {
        triggerAnimation("flung", 400)
        showBubble("啊——！")

        handler.postDelayed({
            layoutParams.x = -layoutParams.width * 2
            windowManager.updateViewLayout(webView, layoutParams)
            removeBubble()

            handler.postDelayed({
                comeBack()
            }, 600)
        }, 400)
    }

    private fun comeBack() {
        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val targetX = (screenW - layoutParams.width) / 2
        val targetY = screenH / 3

        layoutParams.x = targetX
        layoutParams.y = targetY
        windowManager.updateViewLayout(webView, layoutParams)

        triggerAnimation("comeback", 700)
        showBubble("…我回来了")
    }

    private fun triggerAnimation(state: String, durationMs: Int) {
        webView.evaluateJavascript("triggerState('$state', $durationMs)", null)
    }

    private fun showBubble(text: String) {
        removeBubble()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val bubbleW = dp(140)
        val bubbleH = dp(50)

        bubbleView = BubbleView(this, text)
        bubbleParams = WindowManager.LayoutParams(
            bubbleW, bubbleH, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = layoutParams.x + (layoutParams.width - bubbleW) / 2
            y = layoutParams.y - bubbleH
        }

        windowManager.addView(bubbleView, bubbleParams)
        handler.postDelayed({ removeBubble() }, 3000)
    }

    private fun updateBubblePosition() {
        bubbleView?.let {
            bubbleParams?.let { params ->
                val bubbleW = dp(140)
                params.x = layoutParams.x + (layoutParams.width - bubbleW) / 2
                params.y = layoutParams.y - dp(50)
                try {
                    windowManager.updateViewLayout(it, params)
                } catch (_: Exception) {}
            }
        }
    }

    private fun removeBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            bubbleView = null
            bubbleParams = null
        }
    }

    private fun updatePetSize(sizeDp: Int) {
        if (!isViewInitialized) return
        val newSize = dp(sizeDp)
        layoutParams.width = newSize
        layoutParams.height = newSize
        windowManager.updateViewLayout(webView, layoutParams)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeBubble()
        if (isViewInitialized) {
            webView.destroy()
            windowManager.removeView(webView)
        }
        isViewInitialized = false
    }
}

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
import android.os.FileObserver
import android.os.Environment
import java.io.File
import java.util.Timer
import java.util.TimerTask
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.BatteryManager

@Suppress("DEPRECATION")
class OverlayService : Service() {

    private var isViewInitialized = false
    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    private var screenshotObserver: FileObserver? = null
    private var appCheckTimer: Timer? = null
    private var lastForegroundApp: String? = null
    private var notificationReceiver: BroadcastReceiver? = null

    private var bubbleView: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // Loneliness progression system
    private var lonelinessTimer: Timer? = null
    private var lonelinessLevel = 0  // 0=normal, 1=peek, 2=bubble, 3=fidget, 4=drowsy, 5=asleep
    private var lastInteractionTime = System.currentTimeMillis()
    private val lonelinessInterval = 30_000L  // 30s per level

    // Battery awareness
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastBatteryLevel = -1
    private var lastChargingState = false

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

    // AI message receiver
    private var aiMessageReceiver: BroadcastReceiver? = null

    companion object {
        const val ACTION_UPDATE_SIZE = "com.operit.huaiyu.ACTION_UPDATE_SIZE"
        const val ACTION_UPDATE_IMAGE = "com.operit.huaiyu.ACTION_UPDATE_IMAGE"
        const val ACTION_AI_MESSAGE = "com.operit.huaiyu.AI_MESSAGE"
        const val EXTRA_PET_SIZE = "extra_pet_size"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_AI_TEXT = "text"
        const val EXTRA_AI_STYLE = "style"
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

    // Loneliness progression texts
    private val lonelinessTexts = mapOf(
        1 to listOf("…", "嗯？", "偷偷看你一眼"),
        2 to listOf("无聊…", "吹个泡泡~", "…好闲"),
        3 to listOf("东西搬来搬去…", "收拾收拾", "找点事做"),
        4 to listOf("好困…", "眼皮好重", "打了个哈欠~"),
        5 to listOf("zzZ", "睡着了…", "…")
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_SIZE -> {
                val sizeDp = intent.getIntExtra(EXTRA_PET_SIZE, PetPrefs.getPetSize(this))
                updatePetSize(sizeDp)
            }
            ACTION_UPDATE_IMAGE -> {
                val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
                updatePetImage(imagePath)
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
            settings.domStorageEnabled = true
            settings.allowFileAccess = true // <-- THE FIX
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
        startScreenshotObserver()
        startAppObserver()
        startNotificationReceiver()
        startLonelinessTimer()
        startBatteryReceiver()
        startAiMessageReceiver()
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

                    // Reset loneliness on any interaction
                    resetLoneliness()

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

    private fun startScreenshotObserver() {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
        val screenshotDir = File(path)
        if (!screenshotDir.exists()) screenshotDir.mkdirs()

        screenshotObserver = object : FileObserver(path, CREATE) {
            override fun onEvent(event: Int, file: String?) {
                if (event == CREATE && file != null) {
                    handler.post {
                        showBubble("刚截了图，需要看看吗？")
                        triggerAnimation("poked", 1000)
                    }
                }
            }
        }
        screenshotObserver?.startWatching()
    }

    private fun startAppObserver() {
        appCheckTimer = Timer()
        appCheckTimer?.schedule(object : TimerTask() {
            override fun run() {
                val foregroundApp = getForegroundApp()
                if (foregroundApp != null && foregroundApp != lastForegroundApp) {
                    lastForegroundApp = foregroundApp
                    handler.post { onAppSwitched(foregroundApp) }
                }
            }
        }, 0, 3000) // Check every 3 seconds
    }

    private fun getForegroundApp(): String? {
        var currentApp: String? = null
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)
        if (appList != null && appList.isNotEmpty()) {
            val sortedList = appList.sortedByDescending { it.lastTimeUsed }
            if (sortedList.isNotEmpty()) {
                currentApp = sortedList[0].packageName
            }
        }
        return currentApp
    }

    private fun onAppSwitched(packageName: String) {
        val reaction = when (packageName) {
            "com.tencent.mm" -> "在和谁聊天呀？"
            "com.bilibili.app.in" -> "B站！今天看点啥？"
            "com.ss.android.ugc.aweme" -> "刷抖音停不下来了？"
            "com.netease.cloudmusic" -> "这首歌我也喜欢~"
            "com.autonavi.minimap", "com.baidu.BaiduMap" -> "要出门吗？注意安全哦。"
            "com.android.launcher", "com.miui.home", "com.huawei.android.launcher" -> "回主屏幕休息一下~"
            else -> null // No reaction for other apps
        }
        if (reaction != null) {
            showBubble(reaction)
        }
    }

    private fun stopAppObserver() {
        appCheckTimer?.cancel()
        appCheckTimer = null
    }

    private fun startNotificationReceiver() {
        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == NotificationListener.ACTION_NOTIFICATION_POSTED) {
                    val packageName = intent.getStringExtra(NotificationListener.EXTRA_PACKAGE_NAME)
                    val title = intent.getStringExtra(NotificationListener.EXTRA_TITLE)
                    val text = intent.getStringExtra(NotificationListener.EXTRA_TEXT)
                    
                    val message = when (packageName) {
                        "com.tencent.mm" -> "收到一条微信: ${title}"
                        "com.tencent.mobileqq" -> "QQ消息: ${title}"
                        else -> "收到新消息: ${title}"
                    }
                    val bubbleStyle = when (packageName) {
                        "com.tencent.mm", "com.tencent.mobileqq" -> BubbleStyle.LOVE
                        else -> BubbleStyle.SYSTEM
                    }
                    showBubble(message, bubbleStyle)
                }
            }
        }
        val filter = IntentFilter(NotificationListener.ACTION_NOTIFICATION_POSTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
    }

    private fun stopNotificationReceiver() {
        notificationReceiver?.let { unregisterReceiver(it) }
        notificationReceiver = null
    }

    // ===== Loneliness Progression System =====
    private fun startLonelinessTimer() {
        lonelinessTimer = Timer()
        lonelinessTimer?.schedule(object : TimerTask() {
            override fun run() {
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                val newLevel = (elapsed / lonelinessInterval).toInt().coerceIn(0, 5)
                if (newLevel > lonelinessLevel) {
                    lonelinessLevel = newLevel
                    handler.post { onLonelinessLevelChanged(newLevel) }
                }
            }
        }, lonelinessInterval, lonelinessInterval)
    }

    private fun onLonelinessLevelChanged(level: Int) {
        val texts = lonelinessTexts[level] ?: return
        val text = texts.random()
        val style = when (level) {
            4, 5 -> BubbleStyle.SLEEPY
            else -> BubbleStyle.NORMAL
        }
        showBubble(text, style)

        // Trigger matching animation
        val anim = when (level) {
            1 -> "poked"      // peek
            2 -> "doubletap"  // playful
            3 -> "poked"      // fidget
            4 -> "hideface"   // drowsy
            5 -> "hideface"   // asleep
            else -> null
        }
        anim?.let { triggerAnimation(it, 1500) }
    }

    private fun resetLoneliness() {
        lastInteractionTime = System.currentTimeMillis()
        if (lonelinessLevel > 0) {
            lonelinessLevel = 0
            triggerAnimation("comeback", 500)
        }
    }

    private fun stopLonelinessTimer() {
        lonelinessTimer?.cancel()
        lonelinessTimer = null
    }

    // ===== Battery Awareness =====
    private fun startBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percent = (level * 100) / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

                // Charging state changed
                if (isCharging != lastChargingState) {
                    lastChargingState = isCharging
                    if (isCharging) {
                        handler.post {
                            showBubble("充上电了~ 舒服", BubbleStyle.SYSTEM)
                            triggerAnimation("doubletap", 800)
                        }
                    } else {
                        handler.post {
                            showBubble("拔掉充电器了", BubbleStyle.SYSTEM)
                        }
                    }
                }

                // Low battery warnings
                if (!isCharging && percent != lastBatteryLevel) {
                    when {
                        percent <= 10 && lastBatteryLevel > 10 -> handler.post {
                            showBubble("只剩${percent}%了！要没电了！", BubbleStyle.ALERT)
                            triggerAnimation("angry", 800)
                        }
                        percent <= 20 && lastBatteryLevel > 20 -> handler.post {
                            showBubble("电量${percent}%…有点低了", BubbleStyle.ALERT)
                        }
                    }
                }
                lastBatteryLevel = percent
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun stopBatteryReceiver() {
        batteryReceiver?.let { unregisterReceiver(it) }
        batteryReceiver = null
    }

    // ===== AI Message Receiver =====
    private fun startAiMessageReceiver() {
        aiMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_AI_MESSAGE) {
                    val text = intent.getStringExtra(EXTRA_AI_TEXT) ?: return
                    val styleName = intent.getStringExtra(EXTRA_AI_STYLE) ?: "NORMAL"
                    val style = try {
                        BubbleStyle.valueOf(styleName.uppercase())
                    } catch (_: Exception) {
                        BubbleStyle.NORMAL
                    }
                    handler.post {
                        showBubble(text, style)
                        triggerAnimation("poked", 800)
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_AI_MESSAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(aiMessageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(aiMessageReceiver, filter)
        }
    }

    private fun stopAiMessageReceiver() {
        aiMessageReceiver?.let { unregisterReceiver(it) }
        aiMessageReceiver = null
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

    private fun showBubble(text: String, style: BubbleStyle = BubbleStyle.NORMAL) {
        removeBubble()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val bubbleW = dp(140)
        val bubbleH = dp(50)

        bubbleView = BubbleView(this, text, style)
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

    private fun updatePetImage(imagePath: String?) {
        val script = if (imagePath != null) {
            "setCustomImage('file://$imagePath')"
        } else {
            "resetToDefault()"
        }
        webView.evaluateJavascript(script, null)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotObserver?.stopWatching()
        stopAppObserver()
        stopNotificationReceiver()
        stopLonelinessTimer()
        stopBatteryReceiver()
        stopAiMessageReceiver()
        removeBubble()
        if (isViewInitialized) {
            webView.destroy()
            windowManager.removeView(webView)
        }
        isViewInitialized = false
    }
}

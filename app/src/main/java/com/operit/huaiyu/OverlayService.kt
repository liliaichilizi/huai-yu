package com.operit.huaiyu
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class OverlayService : Service() {
    private var isViewInitialized = false
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var gestureDetector: GestureDetector
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private var isPetVisible = true
    private var bubbleView: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    companion object {
        const val ACTION_UPDATE_SIZE = "com.operit.huaiyu.ACTION_UPDATE_SIZE"
        const val ACTION_TOGGLE_VISIBILITY = "com.operit.huaiyu.ACTION_TOGGLE_VISIBILITY"
        const val EXTRA_PET_SIZE = "extra_pet_size"
        private const val CHANNEL_ID = "huaiyu_overlay_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_SIZE -> {
                val sizeDp = intent.getIntExtra(EXTRA_PET_SIZE, PetPrefs.getPetSize(this))
                updatePetSize(sizeDp)
            }
            ACTION_TOGGLE_VISIBILITY -> {
                if (isViewInitialized) {
                    togglePetVisibility()
                }
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
        val toggleIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TOGGLE_VISIBILITY
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionText = if (isPetVisible) "隐藏" else "显示"
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("淮鱼桌宠")
            .setContentText("桌宠正在陪着你")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, actionText, pendingIntent)
            .build()
    }

    private fun togglePetVisibility() {
        isPetVisible = !isPetVisible
        overlayView.alpha = if (isPetVisible) 1.0f else 0.0f
        overlayView.isClickable = isPetVisible
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
        if (isPetVisible) {
            showBubble("我回来啦")
        }
    }

    private fun showBubble(text: String) {
        // Remove existing bubble if any
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

        // Auto remove after 3 seconds
        handler.postDelayed({ removeBubble() }, 3000)
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

    private fun setupOverlayView() {
        overlayView = if (PetPrefs.useCustomImage(this) && PetPrefs.getCustomImageUri(this) != null) {
            createCustomImageView(PetPrefs.getCustomImageUri(this)!!)
        } else {
            PetView(this)
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

        setupGestureDetection()
        windowManager.addView(overlayView, layoutParams)
    }

    private fun setupGestureDetection() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onDown(e: MotionEvent): Boolean {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = e.rawX
                initialTouchY = e.rawY
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                layoutParams.x = initialX + (e2.rawX - initialTouchX).toInt()
                layoutParams.y = initialY + (e2.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(overlayView, layoutParams)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                showBubble("你戳我干嘛")
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                togglePetVisibility()
            }
        })

        overlayView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun updatePetSize(sizeDp: Int) {
        if (!isViewInitialized) return
        val newSize = dp(sizeDp)
        layoutParams.width = newSize
        layoutParams.height = newSize
        windowManager.updateViewLayout(overlayView, layoutParams)
    }

    private fun createCustomImageView(uriString: String): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                val uri = Uri.parse(uriString)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    setImageBitmap(BitmapFactory.decodeStream(inputStream))
                }
            } catch (e: Exception) {
                setBackgroundColor(0x00000000)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeBubble()
        if (isViewInitialized) {
            windowManager.removeView(overlayView)
        }
        isViewInitialized = false
    }
}

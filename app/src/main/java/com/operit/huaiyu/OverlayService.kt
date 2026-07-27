package com.operit.huaiyu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast

class OverlayService : Service() {

    private var isViewInitialized = false
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var gestureDetector: GestureDetector

    private var isPetVisible = true

    companion object {
        const val ACTION_UPDATE_SIZE = "com.operit.huaiyu.ACTION_UPDATE_SIZE"
        const val EXTRA_PET_SIZE = "extra_pet_size"
        private const val CHANNEL_ID = "huaiyu_overlay_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_SIZE) {
            val sizeDp = intent.getIntExtra(EXTRA_PET_SIZE, PetPrefs.getPetSize(this))
            updatePetSize(sizeDp)
        } else if (!isViewInitialized) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupOverlayView()
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            isViewInitialized = true
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "淮鱼桌宠", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 单击：摸摸头，啥也不干
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 双击：弹个俏皮话
                Toast.makeText(this@OverlayService, "你戳我干嘛", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // 长按：切换显示/隐藏
                isPetVisible = !isPetVisible
                overlayView.alpha = if (isPetVisible) 1.0f else 0.0f
                val message = if (isPetVisible) "我回来啦" else "我先藏起来咯"
                Toast.makeText(this@OverlayService, message, Toast.LENGTH_SHORT).show()
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
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                // Fallback to a transparent view if image loading fails
                setBackgroundColor(0x00000000)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isViewInitialized) {
            windowManager.removeView(overlayView)
        }
        isViewInitialized = false
    }
}

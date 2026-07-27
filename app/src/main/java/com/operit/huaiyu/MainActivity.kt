package com.operit.huaiyu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        startButton.setOnClickListener {
            if (checkOverlayPermission()) {
                startOverlayService()
            } else {
                requestOverlayPermission()
            }
        }

        stopButton.setOnClickListener {
            stopOverlayService()
        }

        updateStatus()
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (checkOverlayPermission()) {
                startOverlayService()
            } else {
                Toast.makeText(this, "未授予悬浮窗权限 桌宠无法显示", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "桌宠已启动", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "桌宠已关闭", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun updateStatus() {
        val hasPermission = checkOverlayPermission()
        statusText.text = if (hasPermission) {
            "悬浮窗权限：已授予\n点击下方按钮启动桌宠"
        } else {
            "悬浮窗权限：未授予\n点击启动后会跳转到权限设置"
        }
    }
}

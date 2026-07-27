package com.operit.huaiyu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var previewImage: ImageView
    private lateinit var pickImageButton: Button
    private lateinit var resetImageButton: Button
    private lateinit var imageHint: TextView

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 持久化URI权限
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            PetPrefs.setCustomImageUri(this, it.toString())
            PetPrefs.setUseCustomImage(this, true)
            previewImage.setImageURI(it)
            imageHint.text = "已选择自定义图片 重新启动桌宠生效"
            Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        sizeLabel = findViewById(R.id.sizeLabel)
        sizeSeekBar = findViewById(R.id.sizeSeekBar)
        previewImage = findViewById(R.id.previewImage)
        pickImageButton = findViewById(R.id.pickImageButton)
        resetImageButton = findViewById(R.id.resetImageButton)
        imageHint = findViewById(R.id.imageHint)

        setupButtons()
        setupSizeSeekBar()
        setupImagePicker()
        loadCurrentSettings()
        updateStatus()
    }

    private fun setupButtons() {
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
    }

    private fun setupSizeSeekBar() {
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress.coerceIn(50, 300)
                sizeLabel.text = "大小：${size}dp"
                if (fromUser) {
                    PetPrefs.setPetSize(this@MainActivity, size)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                imageHint.text = "大小已保存 重新启动桌宠生效"
            }
        })
    }

    private fun setupImagePicker() {
        pickImageButton.setOnClickListener {
            imagePickerLauncher.launch(arrayOf("image/*"))
        }
        resetImageButton.setOnClickListener {
            PetPrefs.setUseCustomImage(this, false)
            PetPrefs.setCustomImageUri(this, null)
            previewImage.setImageDrawable(null)
            previewImage.setBackgroundColor(0xFFE8E8E8.toInt())
            imageHint.text = "已恢复默认小猫 重新启动桌宠生效"
            Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCurrentSettings() {
        val size = PetPrefs.getPetSize(this)
        sizeSeekBar.progress = size
        sizeLabel.text = "大小：${size}dp"

        if (PetPrefs.useCustomImage(this)) {
            val uriStr = PetPrefs.getCustomImageUri(this)
            if (uriStr != null) {
                try {
                    previewImage.setImageURI(Uri.parse(uriStr))
                } catch (e: Exception) {
                    previewImage.setImageDrawable(null)
                }
            }
            imageHint.text = "当前使用自定义图片"
        } else {
            imageHint.text = "当前使用默认小猫"
        }
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

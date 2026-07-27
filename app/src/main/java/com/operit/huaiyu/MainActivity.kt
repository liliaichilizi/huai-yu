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
import java.io.File
import java.io.FileOutputStream
import android.app.AppOpsManager
import android.content.Context

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
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // 1. Copy image to internal storage
            val internalPath = copyUriToInternalStorage(it, "custom_pet.png")
            if (internalPath != null) {
                // 2. Save the new path
                PetPrefs.setCustomImageUri(this, internalPath)
                PetPrefs.setUseCustomImage(this, true)
                previewImage.setImageURI(Uri.fromFile(File(internalPath)))
                imageHint.text = "自定义外观已生效"
                
                // 3. Notify service immediately
                updatePetImage(internalPath)
                Toast.makeText(this, "外观已更新", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show()
            }
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

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupButtons() {
        startButton.setOnClickListener {
            when {
                !checkOverlayPermission() -> requestOverlayPermission()
                !hasUsageStatsPermission() -> requestUsageStatsPermission()
                else -> startOverlayService()
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
                    // 发送广播或Intent通知Service实时更新大小
                    val intent = Intent(this@MainActivity, OverlayService::class.java)
                    intent.action = OverlayService.ACTION_UPDATE_SIZE
                    intent.putExtra(OverlayService.EXTRA_PET_SIZE, size)
                    startService(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                imageHint.text = "大小已实时更新"
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
            imageHint.text = "已恢复默认小猫"
            updatePetImage(null) // Pass null to reset
            Toast.makeText(this, "已恢复默认外观", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePetImage(imagePath: String?) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_IMAGE
            putExtra(OverlayService.EXTRA_IMAGE_PATH, imagePath)
        }
        startService(intent)
    }

    private fun copyUriToInternalStorage(uri: Uri, fileName: String): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadCurrentSettings() {
        val size = PetPrefs.getPetSize(this)
        sizeSeekBar.progress = size
        sizeLabel.text = "大小：${size}dp"
        if (PetPrefs.useCustomImage(this)) {
            val path = PetPrefs.getCustomImageUri(this)
            if (path != null) {
                try {
                    previewImage.setImageURI(Uri.fromFile(File(path)))
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
        val hasOverlay = checkOverlayPermission()
        val hasUsageStats = hasUsageStatsPermission()

        var status = "悬浮窗权限: ${if(hasOverlay) "✅" else "❌"}\n"
        status += "应用使用情况权限: ${if(hasUsageStats) "✅" else "❌"}\n\n"

        status += when {
            !hasOverlay -> "请先授予悬浮窗权限"
            !hasUsageStats -> "请授予应用使用情况权限，以便桌宠与您互动"
            else -> "一切就绪，可以启动桌宠了"
        }
        statusText.text = status
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请找到“淮鱼”并开启权限", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开权限设置页面", Toast.LENGTH_SHORT).show()
        }
    }
}

package com.file.whatsapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.file.whatsapp.R
import com.file.whatsapp.core.WhatsAppPathHelper
import com.file.whatsapp.core.TransferForegroundService
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var radioGroupType: RadioGroup
    private lateinit var radioRegular: RadioButton
    private lateinit var btnCheckPermissions: Button
    private lateinit var btnStartTransfer: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var txtStatus: TextView
    private lateinit var txtPathInfo: TextView

    private val STORAGE_PERMISSION_CODE = 1001

    private var transferMode: String = "WIFI"
    private var transferRole: String = "SENDER"
    private var targetIp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transferMode = intent.getStringExtra("TRANSFER_MODE") ?: "WIFI"
        transferRole = intent.getStringExtra("TRANSFER_ROLE") ?: "SENDER"
        targetIp = intent.getStringExtra("TARGET_IP") ?: ""

        initViews()
        setupListeners()
    }

    private fun initViews() {
        radioGroupType = findViewById(R.id.radioGroupType)
        radioRegular = findViewById(R.id.radioRegular)
        btnCheckPermissions = findViewById(R.id.btnCheckPermissions)
        btnStartTransfer = findViewById(R.id.btnStartTransfer)
        progressBar = findViewById(R.id.progressBar)
        txtStatus = findViewById(R.id.txtStatus)
        txtPathInfo = findViewById(R.id.txtPathInfo)
        
        radioRegular.isChecked = true
        
        btnStartTransfer.text = if (transferRole == "SENDER") "بدء الإرسال فائق السرعة ($transferMode)" else "تشغيل خادم الاستقبال السريع"
    }

    private fun setupListeners() {
        btnCheckPermissions.setOnClickListener {
            requestStoragePermissions()
        }

        btnStartTransfer.setOnClickListener {
            if (checkStoragePermissions()) {
                executeMaxPerformanceService()
            } else {
                Toast.makeText(this, "Permissions are required first.", Toast.LENGTH_SHORT).show()
                requestStoragePermissions()
            }
        }
    }

    private fun getSelectedWhatsAppType(): WhatsAppPathHelper.WhatsAppType {
        return if (radioGroupType.checkedRadioButtonId == R.id.radioBusiness) {
            WhatsAppPathHelper.WhatsAppType.BUSINESS
        } else {
            WhatsAppPathHelper.WhatsAppType.REGULAR
        }
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    private fun executeMaxPerformanceService() {
        val selectedType = getSelectedWhatsAppType()
        val targetInfo = WhatsAppPathHelper.resolveStoragePath(selectedType)

        txtPathInfo.text = "Resolved Path: ${targetInfo.sourceDir.absolutePath} (${targetInfo.pathDescription})"

        if (!targetInfo.sourceDir.exists()) {
            txtStatus.text = "Error: Target directory does not exist on device."
            return
        }

        val ipToUse = if (targetIp.isNotEmpty()) targetIp else "192.168.4.1"

        val serviceIntent = Intent(this, TransferForegroundService::class.java).apply {
            putExtra(TransferForegroundService.EXTRA_MODE, transferRole)
            putExtra("TRANSFER_MODE", transferMode)
            putExtra(TransferForegroundService.EXTRA_IP, ipToUse)
            putExtra(TransferForegroundService.EXTRA_SOURCE_PATH, targetInfo.sourceDir.absolutePath)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        txtStatus.text = "الخدمة تعمل في الخلفية عبر ($transferMode). راقب شريط الإشعارات للحالة."
        Toast.makeText(this, "تم بدء خدمة النقل الخلفية.", Toast.LENGTH_SHORT).show()
    }
}

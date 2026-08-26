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
import androidx.lifecycle.lifecycleScope
import com.file.whatsapp.R
import com.file.whatsapp.core.WhatsAppPathHelper
import com.file.whatsapp.core.TransferForegroundService
import com.file.whatsapp.core.NetworkTransferEngine
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var radioGroupType: RadioGroup
    private lateinit var radioRegular: RadioButton
    private lateinit var btnCheckPermissions: Button
    private lateinit var btnStartTransfer: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var txtStatus: TextView
    private lateinit var txtPathInfo: TextView

    private val networkTransferEngine = NetworkTransferEngine()
    private val STORAGE_PERMISSION_CODE = 1001

    private var transferMode: String = "USB"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transferMode = intent.getStringExtra("TRANSFER_MODE") ?: "USB"

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
        
        if (transferMode == "WIFI") {
            btnStartTransfer.text = "Start Wi-Fi Socket Transfer"
        } else {
            btnStartTransfer.text = "Start Background Service Transfer"
        }
    }

    private fun setupListeners() {
        btnCheckPermissions.setOnClickListener {
            requestStoragePermissions()
        }

        btnStartTransfer.setOnClickListener {
            if (checkStoragePermissions()) {
                executeTransferRouting()
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

    private fun executeTransferRouting() {
        val selectedType = getSelectedWhatsAppType()
        val targetInfo = WhatsAppPathHelper.resolveStoragePath(selectedType)

        txtPathInfo.text = "Resolved Path: ${targetInfo.sourceDir.absolutePath} (${targetInfo.pathDescription})"

        if (!targetInfo.sourceDir.exists()) {
            txtStatus.text = "Error: Target directory does not exist on device."
            return
        }

        if (transferMode == "WIFI") {
            executeWifiTransfer(targetInfo.sourceDir)
        } else {
            executeBackgroundServiceTransfer(targetInfo.sourceDir, selectedType)
        }
    }

    private fun executeBackgroundServiceTransfer(sourceDir: File, selectedType: WhatsAppPathHelper.WhatsAppType) {
        val destinationDir = File(getExternalFilesDir(null), "TransferOutput_${selectedType.folderName}")

        val serviceIntent = Intent(this, TransferForegroundService::class.java).apply {
            putExtra(TransferForegroundService.EXTRA_SOURCE_PATH, sourceDir.absolutePath)
            putExtra(TransferForegroundService.EXTRA_DEST_PATH, destinationDir.absolutePath)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        txtStatus.text = "Transfer running in background service. Check notifications."
        Toast.makeText(this, "بدء عملية النقل في الخلفية بنجاح.", Toast.LENGTH_SHORT).show()
    }

    private fun executeWifiTransfer(sourceDir: File) {
        btnStartTransfer.isEnabled = false
        progressBar.progress = 0
        txtStatus.text = "Initializing Socket Server / Client..."

        lifecycleScope.launch {
            try {
                val targetIp = "192.168.4.1"
                
                networkTransferEngine.connectAndSend(targetIp, sourceDir) { fileName, percent ->
                    runOnUiThread {
                        progressBar.progress = percent
                        txtStatus.text = "Sending over Wi-Fi: $fileName ($percent%)"
                    }
                }

                btnStartTransfer.isEnabled = true
                txtStatus.text = "Wi-Fi Transfer Completed Successfully."
            } catch (e: Exception) {
                btnStartTransfer.isEnabled = true
                txtStatus.text = "Wi-Fi Transfer Error: ${e.localizedMessage}"
            }
        }
    }
}

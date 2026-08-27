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
import com.file.whatsapp.core.ConnectionManager
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
    private var transferRole: String = "SENDER"
    private var targetIp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transferMode = intent.getStringExtra("TRANSFER_MODE") ?: "USB"
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
        
        btnStartTransfer.text = if (transferMode == "WIFI") {
            if (transferRole == "SENDER") "Start Wi-Fi Sender" else "Start Wi-Fi Receiver Server"
        } else {
            "Verify USB & Start Background Transfer"
        }
    }

    private fun setupListeners() {
        btnCheckPermissions.setOnClickListener {
            requestStoragePermissions()
        }

        btnStartTransfer.setOnClickListener {
            if (checkStoragePermissions()) {
                verifyConnectionAndExecuteRouting()
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

    private fun verifyConnectionAndExecuteRouting() {
        val selectedType = getSelectedWhatsAppType()
        val targetInfo = WhatsAppPathHelper.resolveStoragePath(selectedType)

        txtPathInfo.text = "Resolved Path: ${targetInfo.sourceDir.absolutePath} (${targetInfo.pathDescription})"

        if (!targetInfo.sourceDir.exists()) {
            txtStatus.text = "Error: Target directory does not exist on device."
            return
        }

        btnStartTransfer.isEnabled = false
        txtStatus.text = "Verifying physical/network link between devices..."

        lifecycleScope.launch {
            val isLinkHealthy = if (transferMode == "USB") {
                ConnectionManager.verifyUsbConnection(this@MainActivity)
            } else {
                if (transferRole == "SENDER") {
                    val ipToTest = if (targetIp.isNotEmpty()) targetIp else "192.168.4.1"
                    ConnectionManager.testSocketConnection(ipToTest, NetworkTransferEngine.PORT)
                } else {
                    true 
                }
            }

            if (!isLinkHealthy && transferMode == "USB") {
                btnStartTransfer.isEnabled = true
                txtStatus.text = "USB Connection Verification Failed."
                Toast.makeText(this@MainActivity, "فشل التحقق من اتصال الـ USB.", Toast.LENGTH_LONG).show()
                return@launch
            }

            txtStatus.text = "Link Verified Successfully. Executing Transfer..."

            if (transferMode == "WIFI") {
                executeWifiTransfer(targetInfo.sourceDir, destinationDir = File(getExternalFilesDir(null), "TransferOutput_${selectedType.folderName}"))
            } else {
                executeBackgroundServiceTransfer(targetInfo.sourceDir, selectedType)
            }
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

        btnStartTransfer.isEnabled = true
        txtStatus.text = "Transfer running in background service. Check notifications."
        Toast.makeText(this, "بدء عملية النقل في الخلفية بنجاح.", Toast.LENGTH_SHORT).show()
    }

    private fun executeWifiTransfer(sourceDir: File, destinationDir: File) {
        progressBar.progress = 0

        lifecycleScope.launch {
            try {
                if (transferRole == "RECEIVER") {
                    txtStatus.text = "Server listening on port ${NetworkTransferEngine.PORT}..."
                    networkTransferEngine.startServerAndReceive(destinationDir) { message ->
                        runOnUiThread {
                            txtStatus.text = message
                        }
                    }
                    txtStatus.text = "Wi-Fi Receiver Completed Successfully."
                } else {
                    val ipToUse = if (targetIp.isNotEmpty()) targetIp else "192.168.4.1"
                    txtStatus.text = "Connecting to $ipToUse..."
                    
                    networkTransferEngine.connectAndSend(ipToUse, sourceDir) { fileName, percent ->
                        runOnUiThread {
                            progressBar.progress = percent
                            txtStatus.text = "Wi-Fi Streaming: $fileName ($percent%)"
                        }
                    }
                    txtStatus.text = "Wi-Fi Sender Completed Successfully."
                }
            } catch (e: Exception) {
                txtStatus.text = "Wi-Fi Transfer Error: ${e.localizedMessage}"
            } finally {
                btnStartTransfer.isEnabled = true
            }
        }
    }
}

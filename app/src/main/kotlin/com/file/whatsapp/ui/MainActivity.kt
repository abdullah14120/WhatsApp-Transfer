
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
import com.file.whatsapp.core.TransferEngine
import kotlinx.coroutines.flow.collectLatest
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

    private val transferEngine = TransferEngine()
    private val STORAGE_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }

    private fun setupListeners() {
        btnCheckPermissions.setOnClickListener {
            requestStoragePermissions()
        }

        btnStartTransfer.setOnClickListener {
            if (checkStoragePermissions()) {
                executeTransfer()
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

    private fun executeTransfer() {
        val selectedType = getSelectedWhatsAppType()
        val targetInfo = WhatsAppPathHelper.resolveStoragePath(selectedType)

        txtPathInfo.text = "Resolved Path: ${targetInfo.sourceDir.absolutePath} (${targetInfo.pathDescription})"

        if (!targetInfo.sourceDir.exists()) {
            txtStatus.text = "Error: Target directory does not exist on device."
            return
        }

        // مسار الوجهة الخارجي المؤقت (مثال: مجلد التخزين العام للنقل)
        val destinationDir = File(getExternalFilesDir(null), "TransferOutput_${selectedType.folderName}")

        btnStartTransfer.isEnabled = false
        progressBar.progress = 0

        lifecycleScope.launch {
            transferEngine.transferFolder(targetInfo.sourceDir, destinationDir).collectLatest { progress ->
                progressBar.progress = progress.percentage
                if (progress.isCompleted) {
                    btnStartTransfer.isEnabled = true
                    txtStatus.text = "Transfer Completed Successfully to: ${destinationDir.absolutePath}"
                } else if (progress.errorMessage != null) {
                    txtStatus.text = "Error in ${progress.currentFileName}: ${progress.errorMessage}"
                } else {
                    txtStatus.text = "Copying: ${progress.currentFileName} (${progress.percentage}%)"
                }
            }
        }
    }
}

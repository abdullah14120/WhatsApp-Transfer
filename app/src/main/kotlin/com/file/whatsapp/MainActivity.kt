package com.file.whatsapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.file.whatsapp.core.PathResolver
import com.file.whatsapp.model.TransferRole
import com.file.whatsapp.model.WhatsAppPackage
import com.file.whatsapp.service.TransferForegroundService
import com.file.whatsapp.ui.TransferScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!fineLocation && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "يرجى منح أذونات الموقع وشبكة الواي فاي للاتصال المباشر", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStorageAndNetworkPermissions()

        setContent {
            var role by remember { mutableStateOf(TransferRole.SENDER) }
            var selectedPackage by remember { mutableStateOf(WhatsAppPackage.STANDARD) }
            var targetIp by remember { mutableStateOf("192.168.49.1") }

            val transferStats by TransferForegroundService.transferState.collectAsState()
            val isRunning by TransferForegroundService.isRunning.collectAsState()

            val sourcePath = remember(selectedPackage) {
                PathResolver.resolveSourceDirectory(selectedPackage).absolutePath
            }
            val targetPath = remember(selectedPackage) {
                PathResolver.resolveTargetDirectory(selectedPackage).absolutePath
            }

            TransferScreen(
                currentRole = role,
                onRoleChange = { role = it },
                selectedPkg = selectedPackage,
                onPackageChange = { selectedPackage = it },
                targetIp = targetIp,
                onIpChange = { targetIp = it },
                isRunning = isRunning,
                stats = transferStats,
                detectedSourcePath = sourcePath,
                detectedTargetPath = targetPath,
                onStartTransfer = {
                    if (!checkStoragePermission()) {
                        requestStorageAndNetworkPermissions()
                        return@TransferScreen
                    }
                    startTransferService(role, selectedPackage, targetIp)
                }
            )
        }
    }

    private fun startTransferService(role: TransferRole, pkg: WhatsAppPackage, targetIp: String) {
        val intent = Intent(this, TransferForegroundService::class.java).apply {
            action = TransferForegroundService.ACTION_START_TRANSFER
            putExtra(TransferForegroundService.EXTRA_ROLE, role)
            putExtra(TransferForegroundService.EXTRA_PACKAGE, pkg)
            putExtra(TransferForegroundService.EXTRA_TARGET_IP, targetIp)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestStorageAndNetworkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:\$packageName")
                }
                startActivity(intent)
            }
        }

        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }
}

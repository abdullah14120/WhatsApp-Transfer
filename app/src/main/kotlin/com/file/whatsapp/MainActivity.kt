package com.file.whatsapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.file.whatsapp.core.PathResolver
import com.file.whatsapp.model.TransferRole
import com.file.whatsapp.model.WhatsAppPackage
import com.file.whatsapp.service.TransferForegroundService
import com.file.whatsapp.ui.TransferScreen
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private var hasStoragePermission by mutableStateOf(false)
    private var showPermissionDialog by mutableStateOf(false)

    // مسجل طلب إذن الوصول لكل الملفات لنظام Android 11 فما فوق
    private val allFilesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndRefreshPermissions()
    }

    // مسجل طلب الصلاحيات العادية (Nearby Devices، الإشعارات، والموقع للأنظمة الأقدم)
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!fineLocation && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "يرجى منح أذونات الموقع وشبكة الواي فاي للاتصال المباشر", Toast.LENGTH_LONG).show()
        }
        checkAndRefreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRefreshPermissions()
        if (!hasStoragePermission) {
            requestAllFilesAccess()
        }
        requestSystemRuntimePermissions()

        setContent {
            var role by remember { mutableStateOf(TransferRole.SENDER) }
            var selectedPackage by remember { mutableStateOf(WhatsAppPackage.STANDARD) }
            
            // قراءة عنوان البوابة (Gateway IP) تلقائياً عند الإقلاع
            var targetIp by remember { 
                mutableStateOf(detectNetworkGatewayIp() ?: "192.168.49.1") 
            }

            val transferStats by TransferForegroundService.transferState.collectAsState()
            val isRunning by TransferForegroundService.isRunning.collectAsState()

            val sourcePath = remember(selectedPackage) {
                PathResolver.resolveSourceDirectory(selectedPackage).absolutePath
            }
            val targetPath = remember(selectedPackage) {
                PathResolver.resolveTargetDirectory(selectedPackage).absolutePath
            }

            // تنبيه يظهر في حال محاولة النقل دون تفعيل الصلاحية
            if (showPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionDialog = false },
                    title = { Text("مطلوب إذن الوصول لجميع الملفات") },
                    text = { 
                        Text("يحتاج التطبيق إلى صلاحية الوصول لكافة الملفات لنقل مجلدات الواتساب واستبدالها بنجاح دون توقف أو تلف.") 
                    },
                    confirmButton = {
                        Button(onClick = {
                            showPermissionDialog = false
                            requestAllFilesAccess()
                        }) {
                            Text("منح الإذن الآن")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            TransferScreen(
                currentRole = role,
                onRoleChange = { newRole ->
                    role = newRole
                    // تحديث عنوان الآي بي تلقائياً حسب البوابة عند التبديل
                    if (newRole == TransferRole.SENDER) {
                        targetIp = detectNetworkGatewayIp() ?: "192.168.49.1"
                    }
                },
                selectedPkg = selectedPackage,
                onPackageChange = { selectedPackage = it },
                targetIp = targetIp,
                onIpChange = { targetIp = it },
                isRunning = isRunning,
                stats = transferStats,
                detectedSourcePath = sourcePath,
                detectedTargetPath = targetPath,
                onStartTransfer = {
                    if (!hasStoragePermission) {
                        showPermissionDialog = true
                        return@TransferScreen
                    }
                    startTransferService(role, selectedPackage, targetIp)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRefreshPermissions()
    }

    private fun checkAndRefreshPermissions() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    allFilesPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    allFilesPermissionLauncher.launch(fallbackIntent)
                }
            }
        }
    }

    private fun requestSystemRuntimePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        runtimePermissionLauncher.launch(permissions.toTypedArray())
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

    /**
     * استخراج عنوان البوابة (Gateway IPv4 / Router IP) تلقائياً:
     * عند اتصال المرسل بنقطة اتصال (Hotspot) المستلم، يكون عنوان المستلم هو الـ Gateway.
     */
    private fun detectNetworkGatewayIp(): String? {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork
                val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    for (route in linkProperties.routes) {
                        if (route.isDefaultRoute && route.gateway is Inet4Address) {
                            val gateway = route.gateway?.hostAddress
                            if (!gateway.isNullOrEmpty() && gateway != "0.0.0.0") {
                                return gateway
                            }
                        }
                    }
                }
            }

            // للأجهزة القديمة كحل بديل عبر DHCP
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                val ip = dhcpInfo.gateway
                return (ip and 0xFF).toString() + "." +
                        (ip shr 8 and 0xFF) + "." +
                        (ip shr 16 and 0xFF) + "." +
                        (ip shr 24 and 0xFF)
            }
        } catch (_: Exception) {}

        return null
    }
}

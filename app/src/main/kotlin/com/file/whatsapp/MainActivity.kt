package com.file.whatsapp

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.PowerManager
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
import com.file.whatsapp.model.TransferState
import com.file.whatsapp.model.WhatsAppPackage
import com.file.whatsapp.service.TransferForegroundService
import com.file.whatsapp.ui.ActiveTransferScreen
import com.file.whatsapp.ui.TransferScreen
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
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

    // مسجل نتيجة طلب استثناء تحسين البطارية (Doze Mode Exemption)
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // تم التعامل مع خروج المستخدم من شاشة البطارية
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
        
        // تفعيل استثناء Doze Mode لضمان عدم إيقاف النقل نهائياً عند إطفاء الشاشة
        requestIgnoreBatteryOptimizations()

        setContent {
            var role by remember { mutableStateOf(TransferRole.SENDER) }
            var selectedPackage by remember { mutableStateOf(WhatsAppPackage.STANDARD) }

            var targetIp by remember {
                mutableStateOf(detectNetworkGatewayIp() ?: "192.168.49.1")
            }

            val currentDeviceIp = remember {
                detectLocalDeviceIp() ?: "غير متصل بالواي فاي"
            }

            // الاكتشاف التلقائي لعنوان IP بين الجهازين عبر UDP Broadcast
            DisposableEffect(role) {
                val discoveryScope = CoroutineScope(Dispatchers.IO)
                val discoveryJob = if (role == TransferRole.RECEIVER) {
                    startBroadcastingPresence(discoveryScope)
                } else {
                    listenForReceiverSignal(discoveryScope) { detectedIp ->
                        targetIp = detectedIp
                        Toast.makeText(this@MainActivity, "تم اكتشاف الجهاز المستلم: $detectedIp", Toast.LENGTH_SHORT).show()
                    }
                }

                onDispose {
                    discoveryJob.cancel()
                    discoveryScope.cancel()
                }
            }

            val transferStats by TransferForegroundService.transferState.collectAsState()
            val isRunning = transferStats.state == TransferState.RUNNING || 
                            transferStats.state == TransferState.CONNECTING ||
                            transferStats.state == TransferState.CONNECTED ||
                            transferStats.state == TransferState.PAUSED || 
                            transferStats.state == TransferState.RECONNECTING

            val sourcePath = remember(selectedPackage) {
                PathResolver.resolveSourceDirectory(selectedPackage).absolutePath
            }
            val targetPath = remember(selectedPackage) {
                PathResolver.resolveTargetDirectory(selectedPackage).absolutePath
            }

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

            // التبديل إلى الواجهة المنفصلة التفاعلية فور بدء النقل
            if (isRunning || transferStats.state == TransferState.COMPLETED) {
                ActiveTransferScreen(
                    role = role,
                    stats = transferStats,
                    onPause = {
                        sendServiceAction(TransferForegroundService.ACTION_PAUSE)
                    },
                    onResume = {
                        sendServiceAction(TransferForegroundService.ACTION_RESUME)
                    },
                    onCancel = {
                        sendServiceAction(TransferForegroundService.ACTION_CANCEL)
                    },
                    onDone = {
                        sendServiceAction(TransferForegroundService.ACTION_CANCEL)
                    }
                )
            } else {
                TransferScreen(
                    currentRole = role,
                    onRoleChange = { newRole ->
                        role = newRole
                        if (newRole == TransferRole.SENDER) {
                            targetIp = detectNetworkGatewayIp() ?: "192.168.49.1"
                        }
                    },
                    selectedPkg = selectedPackage,
                    onPackageChange = { selectedPackage = it },
                    targetIp = targetIp,
                    onIpChange = { targetIp = it },
                    currentDeviceIp = currentDeviceIp,
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
                    },
                    onPauseTransfer = {
                        sendServiceAction(TransferForegroundService.ACTION_PAUSE)
                    },
                    onResumeTransfer = {
                        sendServiceAction(TransferForegroundService.ACTION_RESUME)
                    },
                    onCancelTransfer = {
                        sendServiceAction(TransferForegroundService.ACTION_CANCEL)
                    }
                )
            }
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

    /**
     * استثناء التطبيق من وضع السكون وتحسين البطارية (Doze Mode Exemption):
     * يمنع النظام من إيقاف أو تجميد عمل الـ Sockets ونقل الشبكة أثناء قفل الشاشة أو وضع السكون.
     */
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    batteryOptimizationLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        batteryOptimizationLauncher.launch(fallbackIntent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun startTransferService(role: TransferRole, pkg: WhatsAppPackage, targetIp: String) {
        val intent = Intent(this, TransferForegroundService::class.java).apply {
            action = TransferForegroundService.ACTION_START
            putExtra(TransferForegroundService.EXTRA_ROLE, role)
            putExtra(TransferForegroundService.EXTRA_PACKAGE, pkg)
            putExtra(TransferForegroundService.EXTRA_TARGET_IP, targetIp)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, TransferForegroundService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

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

    private fun detectLocalDeviceIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip != null && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ==========================================
    // محرك الاكتشاف التلقائي المدمج عبر UDP
    // ==========================================
    private fun startBroadcastingPresence(scope: CoroutineScope): Job {
        return scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                val signal = "WA_RECEIVER_DISCOVERY_PING".toByteArray()
                val packet = DatagramPacket(
                    signal,
                    signal.size,
                    InetAddress.getByName("255.255.255.255"),
                    8888
                )
                while (isActive) {
                    socket.send(packet)
                    delay(1200)
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }
    }

    private fun listenForReceiverSignal(scope: CoroutineScope, onFound: (String) -> Unit): Job {
        return scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(8888).apply {
                    reuseAddress = true
                    soTimeout = 3000
                }
                val buffer = ByteArray(512)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        if (message == "WA_RECEIVER_DISCOVERY_PING") {
                            val detectedIp = packet.address.hostAddress
                            if (!detectedIp.isNullOrEmpty()) {
                                withContext(Dispatchers.Main) {
                                    onFound(detectedIp)
                                }
                                break
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }
    }
}

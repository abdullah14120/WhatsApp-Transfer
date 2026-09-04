package com.file.whatsapp.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.file.whatsapp.MainActivity
import com.file.whatsapp.WhatsAppTransferApp
import com.file.whatsapp.core.PathResolver
import com.file.whatsapp.engine.ReceiverEngine
import com.file.whatsapp.engine.SenderEngine
import com.file.whatsapp.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransferForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_CANCEL = "ACTION_CANCEL"

        const val EXTRA_ROLE = "EXTRA_ROLE"
        const val EXTRA_PACKAGE = "EXTRA_PACKAGE"
        const val EXTRA_TARGET_IP = "EXTRA_TARGET_IP"
        const val NOTIFICATION_ID = 9001

        private val _transferState = MutableStateFlow(TransferStats())
        val transferState = _transferState.asStateFlow()

        var dynamicIpProvider: () -> String = { "192.168.49.1" }
    }

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WhatsAppTransfer::WakeLockUltra"
        ).apply {
            setReferenceCounted(false)
            acquire(48 * 60 * 60 * 1000L)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "WhatsAppTransfer::WifiLock")
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WhatsAppTransfer::WifiLock")
        }.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val role = intent.getSerializableExtra(EXTRA_ROLE) as? TransferRole ?: TransferRole.RECEIVER
                val pkg = intent.getSerializableExtra(EXTRA_PACKAGE) as? WhatsAppPackage ?: WhatsAppPackage.STANDARD
                val targetIp = intent.getStringExtra(EXTRA_TARGET_IP) ?: "192.168.49.1"
                
                dynamicIpProvider = { targetIp }

                startForegroundNotification("جاري المزامنة وبدء النقل...")
                serviceScope.launch {
                    executeTransferProcess(role, pkg)
                }
            }
            ACTION_PAUSE -> {
                SenderEngine.isPaused = true
                ReceiverEngine.isPaused = true
                _transferState.value = _transferState.value.copy(state = TransferState.PAUSED)
                updateNotification("تم الإيقاف مؤقتاً")
            }
            ACTION_RESUME -> {
                SenderEngine.isPaused = false
                ReceiverEngine.isPaused = false
                _transferState.value = _transferState.value.copy(state = TransferState.RUNNING)
                updateNotification("جاري استئناف النقل...")
            }
            ACTION_CANCEL -> {
                SenderEngine.isCancelled = true
                ReceiverEngine.isCancelled = true
                _transferState.value = TransferStats(state = TransferState.IDLE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun executeTransferProcess(role: TransferRole, pkg: WhatsAppPackage) {
        try {
            if (role == TransferRole.SENDER) {
                val sourceDir = PathResolver.resolveSourceDirectory(pkg)
                SenderEngine.sendDirectory(
                    sourceDir = sourceDir,
                    targetIpProvider = dynamicIpProvider,
                    onProgress = { stats ->
                        _transferState.value = stats
                        if (stats.state == TransferState.RUNNING) {
                            updateNotification(
                                "نقل: ${stats.filesTransferred}/${stats.totalFiles} (${stats.speedBytesPerSec / (1024 * 1024)} MB/s)"
                            )
                        } else if (stats.state == TransferState.RECONNECTING) {
                            updateNotification("جاري إعادة الاتصال تلقائياً...")
                        }
                    }
                )
            } else {
                val destinationDir = PathResolver.resolveTargetDirectory(pkg)
                ReceiverEngine.receiveDirectory(
                    targetDirectory = destinationDir,
                    onProgress = { stats ->
                        _transferState.value = stats
                        if (stats.state == TransferState.RUNNING) {
                            updateNotification(
                                "استقبال: ${stats.filesTransferred}/${stats.totalFiles} (${stats.speedBytesPerSec / (1024 * 1024)} MB/s)"
                            )
                        } else if (stats.state == TransferState.RECONNECTING) {
                            updateNotification("في انتظار الاتصال...")
                        }
                    }
                )
            }
            _transferState.value = _transferState.value.copy(state = TransferState.COMPLETED)
            updateNotification("اكتمل نقل جميع بيانات واتساب بنجاح!")
        } catch (e: Exception) {
            _transferState.value = _transferState.value.copy(
                state = TransferState.ERROR,
                errorMessage = e.localizedMessage ?: "حدث خطأ غير متوقع"
            )
            updateNotification("خطأ: ${e.message}")
        }
    }

    private fun startForegroundNotification(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, WhatsAppTransferApp.CHANNEL_ID)
        .setContentTitle("WhatsApp Turbo Migration")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

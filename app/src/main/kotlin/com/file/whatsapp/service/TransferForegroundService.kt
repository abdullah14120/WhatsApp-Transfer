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
                updateStatusNotification("تم الإيقاف مؤقتاً")
            }
            ACTION_RESUME -> {
                SenderEngine.isPaused = false
                ReceiverEngine.isPaused = false
                _transferState.value = _transferState.value.copy(state = TransferState.RUNNING)
                updateStatusNotification("جاري استئناف النقل...")
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
                // عزل وتأمين مجلد الواتساب بتغيير اسمه فوراً لمنع حذفه عند تسجيل الخروج
                val sourceDir = PathResolver.secureSourceDirectory(pkg)
                
                SenderEngine.sendDirectory(
                    sourceDir = sourceDir,
                    targetIpProvider = dynamicIpProvider,
                    onProgress = { stats ->
                        _transferState.value = stats
                        handleProgressNotification(role, stats)
                    }
                )
            } else {
                val destinationDir = PathResolver.resolveTargetDirectory(pkg)
                ReceiverEngine.receiveDirectory(
                    targetDirectory = destinationDir,
                    onProgress = { stats ->
                        _transferState.value = stats
                        handleProgressNotification(role, stats)
                    }
                )
            }
            _transferState.value = _transferState.value.copy(state = TransferState.COMPLETED)
            showCompletionNotification("اكتمل نقل وتثبيت جميع بيانات واتساب بنجاح!")
        } catch (e: Exception) {
            _transferState.value = _transferState.value.copy(
                state = TransferState.ERROR,
                errorMessage = e.localizedMessage ?: "حدث خطأ غير متوقع"
            )
            updateStatusNotification("خطأ أثناء النقل: ${e.message}")
        }
    }

    private fun handleProgressNotification(role: TransferRole, stats: TransferStats) {
        val rolePrefix = if (role == TransferRole.SENDER) "إرسال" else "استقبال"
        when (stats.state) {
            TransferState.RUNNING -> {
                val percent = if (stats.totalBytes > 0) ((stats.bytesTransferred * 100) / stats.totalBytes).toInt() else 0
                val speedMb = stats.speedBytesPerSec / (1024 * 1024)
                val statusText = "$rolePrefix: ${stats.filesTransferred}/${stats.totalFiles} ملف ($speedMb MB/s)"
                updateLiveProgressNotification(100, percent, statusText)
            }
            TransferState.CONNECTING -> {
                updateIndeterminateProgressNotification("جاري فحص الاتصال والمصافحة...")
            }
            TransferState.CONNECTED -> {
                updateIndeterminateProgressNotification("تم الاتصال بنجاح! جاري التجهيز...")
            }
            TransferState.RECONNECTING -> {
                updateIndeterminateProgressNotification("جاري إعادة الاتصال التلقائي بالشبكة...")
            }
            TransferState.PAUSED -> {
                updateStatusNotification("النقل متوقف مؤقتاً")
            }
            else -> {}
        }
    }

    private fun startForegroundNotification(initialText: String) {
        val notification = buildProgressNotification(0, 0, initialText, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateLiveProgressNotification(max: Int, progress: Int, contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildProgressNotification(max, progress, contentText, false))
    }

    private fun updateIndeterminateProgressNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildProgressNotification(0, 0, contentText, true))
    }

    private fun updateStatusNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildBaseNotification(contentText).build())
    }

    private fun showCompletionNotification(contentText: String) {
        val notification = buildBaseNotification(contentText)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(max: Int, progress: Int, contentText: String, indeterminate: Boolean): Notification {
        return buildBaseNotification(contentText)
            .setProgress(max, progress, indeterminate)
            .build()
    }

    private fun buildBaseNotification(contentText: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, WhatsAppTransferApp.CHANNEL_ID)
            .setContentTitle("ناقل واتساب السريع الذكي")
            .setContentText(contentText)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

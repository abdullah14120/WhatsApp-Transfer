package com.file.whatsapp.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class TransferForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wakeLock: PowerManager.WakeLock
    private val networkEngine = NetworkTransferEngine()

    companion object {
        const val CHANNEL_ID = "HighSpeedTransferChannel"
        const val NOTIFICATION_ID = 7777
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_IP = "EXTRA_IP"
        const val EXTRA_SOURCE_PATH = "EXTRA_SOURCE_PATH"
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhatsAppTransfer::MaxPerformanceWakeLock")
        wakeLock.acquire(8 * 60 * 60 * 1000L)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: "SENDER"
        val ip = intent?.getStringExtra(EXTRA_IP) ?: "192.168.4.1"
        val sourcePath = intent?.getStringExtra(EXTRA_SOURCE_PATH)

        startForeground(NOTIFICATION_ID, createNotification("جاري تهيئة قناة الاتصال والربط...", 0))

        serviceScope.launch {
            try {
                val destinationDir = File(getExternalFilesDir(null), "WhatsAppTransfer_HighSpeed")
                if (mode == "RECEIVER") {
                    updateNotification("في انتظار اتصال المرسل على البورت ${NetworkTransferEngine.PORT}...", 0)
                    networkEngine.startServerAndReceive(destinationDir) { name, percent ->
                        updateNotification("استقبال: $name ($percent%)", percent)
                    }
                } else if (sourcePath != null) {
                    val sourceFile = File(sourcePath)
                    updateNotification("جاري الاتصال بالمستقبل عبر ($ip)...", 0)
                    networkEngine.connectAndSend(ip, sourceFile) { name, percent ->
                        updateNotification("إرسال: $name ($percent%)", percent)
                    }
                }
                updateNotification("اكتمل النقل بنجاح تام وبأقصى سرعة!", 100)
                delay(3000)
            } catch (e: Exception) {
                updateNotification("فشل الاتصال أو النقل: ${e.localizedMessage}", 0)
                delay(6000)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "High Speed Transfer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("منظومة النقل فائق السرعة")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, progress > 0)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content, progress))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

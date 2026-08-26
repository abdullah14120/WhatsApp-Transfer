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
import com.file.whatsapp.R
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

class TransferForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit.let var wakeLock: PowerManager.WakeLock
    private val transferEngine = TransferEngine()

    companion object {
        const val CHANNEL_ID = "WhatsAppTransferChannel"
        const val NOTIFICATION_ID = 1337
        const val EXTRA_SOURCE_PATH = "EXTRA_SOURCE_PATH"
        const val EXTRA_DEST_PATH = "EXTRA_DEST_PATH"
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhatsAppTransfer::Wakelock")
        wakeLock.acquire(4 * 60 * 60 * 1000L) // حماية لمدة أقصاها 4 ساعات
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sourcePath = intent?.getStringExtra(EXTRA_SOURCE_PATH) ?: return START_NOT_STICKY
        val destPath = intent?.getStringExtra(EXTRA_DEST_PATH) ?: return START_NOT_STICKY

        val sourceFile = File(sourcePath)
        val destFile = File(destPath)

        startForeground(NOTIFICATION_ID, createNotification("جاري تجهيز نقل الملفات...", 0))

        serviceScope.launch {
            try {
                transferEngine.transferFolderWithVerification(sourceFile, destFile) { progress ->
                    val notification = createNotification("جاري نقل: ${progress.currentFileName} (${progress.percentage}%)", progress.percentage)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
                updateNotificationCompletion("تم النقل بنجاح وبدون تلف ملفات!")
            } catch (e: Exception) {
                updateNotificationCompletion("فشل النقل: ${e.localizedMessage}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY // ضمان إعادة التشغيل لو قتل النظام الخدمة قسرياً
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "WhatsApp Transfer Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("نقل بيانات واتساب في الخلفية")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            
        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        }
        return builder.build()
    }

    private fun updateNotificationCompletion(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("اكتملت العملية")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(false)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock.isHeld) wakeLock.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

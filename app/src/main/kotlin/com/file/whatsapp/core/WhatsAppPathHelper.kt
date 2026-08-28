package com.file.whatsapp.core

import android.os.Build
import android.os.Environment
import java.io.File

object WhatsAppPathHelper {

    enum class WhatsAppType(val packageName: String, val folderName: String) {
        REGULAR("com.whatsapp", "WhatsApp"),
        BUSINESS("com.whatsapp.w4b", "WhatsApp Business")
    }

    data class TargetInfo(
        val sourceDir: File,
        val isScopedStorage: Boolean,
        val pathDescription: String
    )

    fun resolveStoragePath(type: WhatsAppType): TargetInfo {
        val externalStorage = Environment.getExternalStorageDirectory()
        
        val mediaDir = File(externalStorage, "Android/media/${type.packageName}/${type.folderName}")
        val legacyDir = File(externalStorage, type.folderName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when {
                mediaDir.exists() && mediaDir.isDirectory -> {
                    TargetInfo(mediaDir, true, "Android/media (Scoped Storage)")
                }
                legacyDir.exists() && legacyDir.isDirectory -> {
                    TargetInfo(legacyDir, false, "Internal Storage Root (Legacy)")
                }
                else -> {
                    TargetInfo(mediaDir, true, "Android/media (Default)")
                }
            }
        } else {
            TargetInfo(if (legacyDir.exists()) legacyDir else mediaDir, false, "Internal Storage Root")
        }
    }
}

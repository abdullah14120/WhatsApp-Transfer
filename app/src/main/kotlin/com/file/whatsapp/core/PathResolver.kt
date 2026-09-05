package com.file.whatsapp.core

import android.os.Build
import android.os.Environment
import com.file.whatsapp.model.WhatsAppPackage
import java.io.File

object PathResolver {

    /**
     * تأمين المجلد فوراً عن طريق تغيير اسمه لمنع واتساب من حذفه عند تسجيل الخروج
     */
    fun secureSourceDirectory(pkg: WhatsAppPackage): File {
        val originalDir = resolveSourceDirectory(pkg)
        
        // إذا كان المجلد الأصلي موجوداً، نحميه بتغيير اسمه
        if (originalDir.exists()) {
            val safeDir = File(originalDir.parentFile, "${originalDir.name}_safe_backup")
            
            // إذا كان المجلد الآمن غير موجود مسبقاً، نقوم بإعادة التسمية فوراً
            if (!safeDir.exists()) {
                val success = originalDir.renameTo(safeDir)
                if (success) return safeDir
            } else {
                return safeDir
            }
        }
        
        // في حال كان المجلد قد تم تأمينه مسبقاً
        val alreadySafeDir = File(originalDir.parentFile, "${originalDir.name}_safe_backup")
        if (alreadySafeDir.exists()) {
            return alreadySafeDir
        }

        return originalDir
    }

    fun resolveSourceDirectory(pkg: WhatsAppPackage): File {
        val root = Environment.getExternalStorageDirectory()
        val modernPath = File(root, "Android/media/${pkg.packageName}")
        val legacyPath = File(root, pkg.legacyFolderName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (modernPath.exists()) modernPath else legacyPath
        } else {
            if (legacyPath.exists()) legacyPath else modernPath
        }
    }

    fun resolveTargetDirectory(pkg: WhatsAppPackage): File {
        val root = Environment.getExternalStorageDirectory()
        // في الجهاز الجديد نضعه دائماً بالاسم الرسمي الصحيح
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File(root, "Android/media/${pkg.packageName}")
        } else {
            File(root, pkg.legacyFolderName)
        }
    }

    private fun hasActualData(dir: File): Boolean {
        return dir.exists() && dir.isDirectory && (dir.list()?.isNotEmpty() == true)
    }
}

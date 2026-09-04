package com.file.whatsapp.core

import android.os.Build
import android.os.Environment
import com.file.whatsapp.model.WhatsAppPackage
import java.io.File

object PathResolver {

    /**
     * الذكاء في كشف مسار مجلد الواتساب للجهاز المرسل
     * يفحص مسار Scoped Storage الجديد (Android 11+) أولاً،
     * وإذا لم يجد به محتويات يعود تلقائياً لمسار Legacy القديم.
     */
    fun resolveSourceDirectory(pkg: WhatsAppPackage): File {
        val root = Environment.getExternalStorageDirectory()
        val modernPath = File(root, "Android/media/${pkg.packageName}")
        val legacyPath = File(root, pkg.legacyFolderName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasActualData(modernPath)) {
                modernPath
            } else if (hasActualData(legacyPath)) {
                legacyPath
            } else {
                modernPath // افتراضي في حال كان فارغاً
            }
        } else {
            if (hasActualData(legacyPath)) legacyPath else modernPath
        }
    }

    /**
     * المسار الصحيح لوضع البيانات في الجهاز الجديد:
     * إذا كان أندرويد 11 فما فوق (API 30+) يذهب مباشرة لـ Android/media/{pkg}
     * وإذا كان أندرويد قديم يوضع في مسار الجذر المعتاد.
     */
    fun resolveTargetDirectory(pkg: WhatsAppPackage): File {
        val root = Environment.getExternalStorageDirectory()
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

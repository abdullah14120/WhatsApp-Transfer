package com.file.whatsapp.model

enum class TransferRole {
    SENDER,
    RECEIVER
}

enum class WhatsAppPackage(val packageName: String, val displayName: String, val legacyFolderName: String) {
    STANDARD("com.whatsapp", "WhatsApp (الرسمي)", "WhatsApp"),
    BUSINESS("com.whatsapp.w4b", "WhatsApp Business (الأعمال)", "WhatsApp Business")
}

data class TransferStats(
    val currentFileName: String = "",
    val filesTransferred: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

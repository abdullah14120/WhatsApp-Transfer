package com.file.whatsapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.file.whatsapp.model.TransferRole
import com.file.whatsapp.model.TransferStats
import com.file.whatsapp.model.WhatsAppPackage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    currentRole: TransferRole,
    onRoleChange: (TransferRole) -> Unit,
    selectedPkg: WhatsAppPackage,
    onPackageChange: (WhatsAppPackage) -> Unit,
    targetIp: String,
    onIpChange: (String) -> Unit,
    isRunning: Boolean,
    stats: TransferStats,
    detectedSourcePath: String,
    detectedTargetPath: String,
    onStartTransfer: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ناقل واتساب السريع الذكي", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // تحديد دور الجهاز
            Text("اختر وظيفة هذا الجهاز", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning,
                    onClick = { onRoleChange(TransferRole.SENDER) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentRole == TransferRole.SENDER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (currentRole == TransferRole.SENDER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("المرسل (القديم)")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning,
                    onClick = { onRoleChange(TransferRole.RECEIVER) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentRole == TransferRole.RECEIVER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (currentRole == TransferRole.RECEIVER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("المستلم (الجديد)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // اختيار نوع الحزمة
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("نوع نسخة واتساب", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPkg == WhatsAppPackage.STANDARD,
                            onClick = { if (!isRunning) onPackageChange(WhatsAppPackage.STANDARD) },
                            label = { Text("WhatsApp الأساسي") },
                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) }
                        )
                        FilterChip(
                            selected = selectedPkg == WhatsAppPackage.BUSINESS,
                            onClick = { if (!isRunning) onPackageChange(WhatsAppPackage.BUSINESS) },
                            label = { Text("WhatsApp Business") },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // بطاقة الكشف التلقائي عن المسار
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الكشف الذكي عن المسار المتوافق", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val pathInfo = if (currentRole == TransferRole.SENDER) {
                        "مسار القراءة التلقائي:\n$detectedSourcePath"
                    } else {
                        "مسار الحفظ والاستبدال:\n$detectedTargetPath"
                    }

                    Text(
                        text = pathInfo,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // حقل إدخال الآي بي للمرسل
            if (currentRole == TransferRole.SENDER) {
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = onIpChange,
                    label = { Text("عنوان IP للمستلم (Wi-Fi Direct / Hotspot)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // لوحة متابعة النقل في الوقت الفعلي
            AnimatedVisibility(visible = isRunning || stats.isCompleted || stats.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (stats.isCompleted) "اكتمل النقل بنجاح!" else if (stats.errorMessage != null) "حدث خطأ" else "جاري النقل بسرعة فائقة...",
                            fontWeight = FontWeight.Bold,
                            color = if (stats.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val progress = if (stats.totalBytes > 0) (stats.bytesTransferred.toFloat() / stats.totalBytes.toFloat()) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الملفات: ${stats.filesTransferred} / ${stats.totalFiles}", fontSize = 12.sp)
                            Text("السرعة: ${stats.speedBytesPerSec / (1024 * 1024)} MB/s", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (stats.currentFileName.isNotEmpty()) {
                            Text("الملف الحالي: ${stats.currentFileName}", fontSize = 11.sp, maxLines = 1)
                        }

                        stats.errorMessage?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartTransfer,
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isRunning) "العملية جارية بالخلفية..." else if (currentRole == TransferRole.SENDER) "بدء إرسال البيانات مباشرة" else "بدء استقبال وحفظ البيانات",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

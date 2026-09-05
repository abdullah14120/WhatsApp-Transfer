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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.file.whatsapp.model.TransferRole
import com.file.whatsapp.model.TransferState
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
    currentDeviceIp: String,
    isRunning: Boolean,
    stats: TransferStats,
    detectedSourcePath: String,
    detectedTargetPath: String,
    onStartTransfer: () -> Unit,
    onPauseTransfer: () -> Unit,
    onResumeTransfer: () -> Unit,
    onCancelTransfer: () -> Unit
) {
    // التحقق من صحة عنوان IP المدخل/المكتشف
    val isReceiverIpValid = remember(targetIp) {
        val trimmed = targetIp.trim()
        val ipv4Regex = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$")
        trimmed.isNotEmpty() && 
        trimmed != "غير متصل بالواي فاي" && 
        trimmed != "0.0.0.0" && 
        trimmed != "127.0.0.1" &&
        ipv4Regex.matches(trimmed)
    }

    // السماح بالبدء فقط إذا كان المستلم جاهزاً أو كان الجهاز هو المستلم نفسه
    val canStartTransfer = if (currentRole == TransferRole.SENDER) {
        !isRunning && isReceiverIpValid
    } else {
        !isRunning && currentDeviceIp != "غير متصل بالواي فاي"
    }

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

            // أزرار تحديد الدور
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

            // اختيار نوع النسخة
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

            // بطاقة كشف المسار المتوافق
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

            // إعدادات الـ IP التفاعلية الذكية
            if (currentRole == TransferRole.SENDER) {
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = onIpChange,
                    label = { Text("عنوان IP للمستلم") },
                    supportingText = { 
                        Text(
                            if (isReceiverIpValid) "تم التعرف على عنوان المستلم جاهز للاتصال" 
                            else "بانتظار ظهور الجهاز المستلم على الشبكة أو كتابة IP يدوياً"
                        ) 
                    },
                    isError = !isReceiverIpValid && targetIp.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    singleLine = true
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("عنوان IP لهذا الجهاز (المستلم):", fontSize = 12.sp)
                            Text(
                                text = currentDeviceIp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // لوحة متابعة الاتصال والنقل
            AnimatedVisibility(visible = isRunning || stats.state == TransferState.COMPLETED || stats.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val headerStatusText = when (stats.state) {
                            TransferState.CONNECTING -> "جاري فحص الاتصال والمصافحة..."
                            TransferState.CONNECTED -> "تم التحقق من الاتصال بنجاح!"
                            TransferState.RUNNING -> "جاري النقل بسرعة فائقة..."
                            TransferState.PAUSED -> "تم الإيقاف مؤقتاً"
                            TransferState.RECONNECTING -> "جاري استعادة الاتصال تلقائياً..."
                            TransferState.COMPLETED -> "اكتمل النقل بنجاح!"
                            TransferState.ERROR -> "فشل في الاتصال"
                            else -> "في الانتظار..."
                        }

                        Text(
                            text = headerStatusText,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.state == TransferState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val progress = if (stats.totalBytes > 0) (stats.bytesTransferred.toFloat() / stats.totalBytes.toFloat()) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الملفات: ${stats.filesTransferred} / ${stats.totalFiles}", fontSize = 12.sp)
                            Text("السرعة: ${stats.speedBytesPerSec / (1024 * 1024)} MB/s", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (stats.currentFileName.isNotEmpty()) {
                            Text("الحالة: ${stats.currentFileName}", fontSize = 12.sp, maxLines = 2)
                        }

                        stats.errorMessage?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }

                        // أزرار التحكم أثناء النقل
                        if (isRunning) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (stats.state == TransferState.PAUSED) {
                                    Button(
                                        onClick = onResumeTransfer,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("استئناف")
                                    }
                                } else {
                                    Button(
                                        onClick = onPauseTransfer,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إيقاف مؤقت")
                                    }
                                }

                                OutlinedButton(
                                    onClick = onCancelTransfer,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إلغاء")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // زر البدء الذكي
            Button(
                onClick = onStartTransfer,
                enabled = canStartTransfer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                val buttonLabel = when {
                    isRunning -> "العملية جارية بالخلفية..."
                    currentRole == TransferRole.SENDER && !isReceiverIpValid -> "بانتظار العثور على الجهاز المستلم..."
                    currentRole == TransferRole.SENDER -> "بدء فحص الاتصال والإرسال"
                    currentDeviceIp == "غير متصل بالواي فاي" -> "يرجى الاتصال بالواي فاي أولاً"
                    else -> "بدء استقبال وحفظ البيانات"
                }

                Text(
                    text = buttonLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

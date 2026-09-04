package com.file.whatsapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTransferScreen(
    role: TransferRole,
    stats: TransferStats,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val progressRatio = if (stats.totalBytes > 0L) {
        (stats.bytesTransferred.toFloat() / stats.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(targetValue = progressRatio, label = "Progress")
    val percentage = (progressRatio * 100).toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (role == TransferRole.SENDER) "جاري إرسال البيانات..." else "جاري استلام البيانات...",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // مؤشر التقدم الرئيسي الرقمي والنسبي
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$percentage%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val statusLabel = when (stats.state) {
                        TransferState.CONNECTING -> "جاري مزامنة المنفذ والاتصال..."
                        TransferState.CONNECTED -> "تم الاتصال! جاري دفق البيانات"
                        TransferState.PAUSED -> "العملية متوقفة مؤقتاً"
                        TransferState.RECONNECTING -> "انقطع الاتصال، جاري إعادة المحاولة..."
                        TransferState.COMPLETED -> "تم نقل جميع البيانات بنجاح!"
                        TransferState.ERROR -> "فشل في عملية النقل"
                        else -> "جاري المعالجة..."
                    }

                    Text(
                        text = statusLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (stats.state == TransferState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // لوحة المقاييس الفورية (السرعة، الحجم، عدد الملفات)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem("السرعة الحالية", "${stats.speedBytesPerSec / (1024 * 1024)} MB/s")
                        MetricItem("الملفات المنقولة", "${stats.filesTransferred} / ${stats.totalFiles}")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    val transferredMb = stats.bytesTransferred / (1024 * 1024)
                    val totalMb = stats.totalBytes / (1024 * 1024)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem("الحجم المنجز", "$transferredMb MB / $totalMb MB")
                        MetricItem("حالة النمط", if (role == TransferRole.SENDER) "جهاز مرسل" else "جهاز مستلم")
                    }

                    if (stats.currentFileName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "الملف الجاري: ${stats.currentFileName}",
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // أزرار التحكم السفلية
            if (stats.state == TransferState.COMPLETED) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تم بنجاح - العودة للرئيسية", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (stats.state == TransferState.PAUSED) {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استئناف")
                        }
                    } else {
                        Button(
                            onClick = onPause,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إيقاف مؤقت")
                        }
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إلغاء النقل")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

package com.file.whatsapp.core

import android.content.Context
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

object ConnectionManager {

    const val DEFAULT_PORT = 9988

    enum class Role { SENDER, RECEIVER }
    enum class Mode { WIFI, USB }

    // 1. التحقق من توصيل الكيبل الفيزيائي (USB OTG / MTP Connection State)
    fun verifyUsbConnection(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        // التحقق من وجود أجهزة متصلة أو حالة الملحقات الفعالة
        val deviceList = usbManager.deviceList
        return deviceList.isNotEmpty()
    }

    // 2. اختبار الاتصال بشبكة Sockets المحلية (مرسل إلى مستقبل عبر Wi-Fi / IP)
    suspend fun testSocketConnection(ip: String, port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3000)
                socket.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    // 3. تشغيل خادم المستقبل المحلي عبر Wi-Fi
    suspend fun startReceiverServer(port: Int = DEFAULT_PORT, onClientConnected: (Socket) -> Unit) = withContext(Dispatchers.IO) {
        ServerSocket(port).use { serverSocket ->
            val clientSocket = serverSocket.accept()
            onClientConnected(clientSocket)
        }
    }
}

package com.file.whatsapp.core

import android.content.Context
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

object ConnectionManager {

    const val DEFAULT_PORT = 8888

    enum class Role { SENDER, RECEIVER }
    enum class Mode { WIFI, USB }

    suspend fun verifyUsbConnection(context: Context): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return@withContext usbManager.deviceList.isNotEmpty()
    }

    suspend fun testSocketConnection(ip: String, port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 2000)
                socket.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun startReceiverServer(port: Int = DEFAULT_PORT, onClientConnected: (Socket) -> Unit) = withContext(Dispatchers.IO) {
        ServerSocket(port).use { serverSocket ->
            serverSocket.soTimeout = 0
            val clientSocket = serverSocket.accept()
            onClientConnected(clientSocket)
        }
    }
}

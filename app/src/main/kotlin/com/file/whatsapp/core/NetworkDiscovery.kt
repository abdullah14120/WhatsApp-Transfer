package com.file.whatsapp.core

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object NetworkDiscovery {

    private const val BROADCAST_PORT = 8888
    private const val DISCOVERY_SIGNAL = "WA_RECEIVER_DISCOVERY_PING"

    /**
     * يُشغله المستلم ليبث وجوده وعنوانه في الشبكة المحلية
     */
    fun startBroadcasting(scope: CoroutineScope): Job {
        return scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                val buffer = DISCOVERY_SIGNAL.toByteArray()
                val packet = DatagramPacket(
                    buffer,
                    buffer.size,
                    InetAddress.getByName("255.255.255.255"),
                    BROADCAST_PORT
                )

                while (isActive) {
                    socket.send(packet)
                    delay(1200) // إرسال إشارة كل ثانية تقريباً
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * يُشغله المرسل لالتقاط عنوان المستلم فوراً
     */
    fun listenForReceiver(scope: CoroutineScope, onReceiverFound: (String) -> Unit): Job {
        return scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(BROADCAST_PORT).apply {
                    reuseAddress = true
                    soTimeout = 3000
                }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        if (message == DISCOVERY_SIGNAL) {
                            val receiverIp = packet.address.hostAddress
                            if (!receiverIp.isNullOrEmpty()) {
                                withContext(Dispatchers.Main) {
                                    onReceiverFound(receiverIp)
                                }
                                break // التوقف فور العثور عليه
                            }
                        }
                    } catch (_: Exception) {
                        // انتهاء مهلة الانتظار وإعادة المحاولة
                    }
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }
    }
}

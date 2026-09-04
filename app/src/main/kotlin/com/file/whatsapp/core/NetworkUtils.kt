package com.file.whatsapp.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * استخراج عنوان البوابة (Gateway IP):
     * عندما يكون المستلم فاتح نقطة اتصال (Hotspot) والمرسل متصل به،
     * يكون عنوان المستلم هو Gateway IP بالنسبة للمرسل (عادة 192.168.43.1 أو 192.168.49.1).
     */
    fun getGatewayIp(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val linkProperties: LinkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

            for (route in linkProperties.routes) {
                if (route.isDefaultRoute && route.gateway is Inet4Address) {
                    val ip = route.gateway?.hostAddress
                    if (!ip.isNullOrEmpty() && ip != "0.0.0.0") {
                        return ip
                    }
                }
            }
        }

        // للأجهزة القديمة أو كخيار بديل عبر DhcpInfo
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val dhcpInfo = wifiManager?.dhcpInfo
        if (dhcpInfo != null && dhcpInfo.gateway != 0) {
            return formatIpAddress(dhcpInfo.gateway)
        }

        return null
    }

    /**
     * استخراج عنوان الـ IPv4 الفعلي للجهاز نفسه على شبكة الواي فاي الحالية
     */
    fun getDeviceIpv4(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        // استبعاد عناوين الربط المحلي التلقائي غير الصالحة
                        if (host != null && !host.startsWith("127.") && !host.startsWith("169.254.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun formatIpAddress(ipAddress: Int): String {
        return (ipAddress and 0xFF).toString() + "." +
                (ipAddress shr 8 and 0xFF) + "." +
                (ipAddress shr 16 and 0xFF) + "." +
                (ipAddress shr 24 and 0xFF)
    }
}

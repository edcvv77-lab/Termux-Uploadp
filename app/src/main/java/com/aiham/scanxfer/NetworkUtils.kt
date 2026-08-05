package com.aiham.scanxfer

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpv4Address(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            for (address in networkInterface.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    val host = address.hostAddress ?: continue
                    if (host != "0.0.0.0") return host
                }
            }
        }
        return "127.0.0.1"
    }
}

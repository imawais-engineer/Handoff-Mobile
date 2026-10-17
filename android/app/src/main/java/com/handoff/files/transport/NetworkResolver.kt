package com.handoff.files.transport

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkResolver {

    /**
     * Finds the active local IPv4 address on Wi-Fi, hotspot (AP), or USB tethering interfaces.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()

            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val name = iface.name.lowercase()

                val addresses = iface.inetAddresses
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress ?: continue
                        // Prioritize wlan0, ap0, swlan, p2p
                        if (name.contains("wlan") || name.contains("ap") || name.contains("p2p")) {
                            return host
                        }
                        candidates.add(host)
                    }
                }
            }
            return candidates.firstOrNull()
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Validates peer destination IP address:
     * - Must be valid IPv4
     * - Cannot be loopback (127.x.x.x) or wildcard (0.0.0.0) or multicast (224-239.x.x.x)
     * - Port must be within range 1024..65535
     */
    fun validatePeerEndpoint(host: String, port: Int): Boolean {
        if (port !in 1024..65535) return false
        return try {
            val addr = InetAddress.getByName(host)
            addr is Inet4Address &&
                    !addr.isLoopbackAddress &&
                    !addr.isAnyLocalAddress &&
                    !addr.isMulticastAddress
        } catch (_: Exception) {
            false
        }
    }
}

package com.diyf1wheel.net

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** Listens for the receiver's LAN broadcast beacon so the user does not have to type the IP. */
object Discovery {
    data class Found(val ip: String, val port: Int, val name: String)

    /** Blocks for up to [timeoutMs]; call from a background thread. */
    fun find(context: Context, timeoutMs: Long = 3500): List<Found> {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wm?.createMulticastLock("diyf1wheel-discovery")?.apply { setReferenceCounted(false); acquire() }
        val found = LinkedHashMap<String, Found>()
        try {
            DatagramSocket(null).use { s ->
                s.reuseAddress = true
                s.broadcast = true
                s.bind(InetSocketAddress(Protocol.BEACON_PORT))
                s.soTimeout = 300
                val buf = ByteArray(256)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(pkt)
                    } catch (_: SocketTimeoutException) {
                        if (found.isNotEmpty()) break
                        continue
                    }
                    val b = Protocol.parseBeacon(buf, pkt.length) ?: continue
                    val ip = pkt.address.hostAddress ?: continue
                    found[ip] = Found(ip, b.port, b.name)
                }
            }
        } finally {
            runCatching { lock?.release() }
        }
        return found.values.toList()
    }
}

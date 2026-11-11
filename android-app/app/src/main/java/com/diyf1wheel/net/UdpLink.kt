package com.diyf1wheel.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.locks.LockSupport

/**
 * Sends the controller state to the Windows receiver at a fixed rate over UDP
 * and listens for its ACKs (used for connection status and round-trip time).
 *
 * Every STATE packet carries the complete current state, so a lost packet is
 * simply superseded by the next one a few milliseconds later. Gear shifts are
 * counters, so they survive packet loss without being duplicated.
 */
class UdpLink(
    private val context: Context,
    private val stateProvider: (phoneMs: Long, rttMs: Float?) -> Protocol.State,
) {
    enum class Status { STOPPED, CONNECTING, CONNECTED, ERROR }

    @Volatile var status = Status.STOPPED; private set
    @Volatile var error: String? = null; private set
    @Volatile var rttMs: Float? = null; private set
    @Volatile var controllerOk = false; private set
    @Volatile var pcRxHz = 0; private set
    @Volatile var pcDropped = 0L; private set
    @Volatile var sentPackets = 0L; private set
    @Volatile var reconnects = 0; private set

    @Volatile private var running = false
    private var sender: Thread? = null
    private var receiver: Thread? = null
    private var socket: DatagramSocket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    @Volatile private var lastAckMs = 0L

    private var host = ""
    private var port = Protocol.DEFAULT_PORT
    private var rateHz = 200
    private var signer: Protocol.Signer? = null
    private val session = SecureRandom().nextInt()
    private var seq = 0

    fun start(host: String, port: Int, pin: String, rateHz: Int) {
        stop()
        this.host = host
        this.port = port
        this.rateHz = rateHz.coerceIn(50, 250)
        this.signer = Protocol.Signer(Protocol.deriveKey(pin))
        running = true
        status = Status.CONNECTING
        error = null
        rttMs = null
        lastAckMs = 0L
        controllerOk = false
        acquireWifiLock()
        sender = Thread({ sendLoop() }, "udp-send").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        if (!running) return
        running = false
        sender?.join(500)
        sender = null
        receiver?.join(500)
        receiver = null
        releaseWifiLock()
        status = Status.STOPPED
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun openSocket(): Pair<DatagramSocket, InetAddress> {
        val addr = InetAddress.getByName(host)
        val s = DatagramSocket()
        s.soTimeout = 250
        s.trafficClass = 0xB8 // DSCP EF (low latency) - honoured by some routers
        socket = s
        val sig = signer!!
        receiver = Thread({ receiveLoop(s, sig) }, "udp-recv").apply { start() }
        return Pair(s, addr)
    }

    private fun sendLoop() {
        val sig = signer!!
        var sock: DatagramSocket? = null
        var addr: InetAddress? = null
        val periodNs = 1_000_000_000L / rateHz
        var next = System.nanoTime()
        var lastHello = 0L
        while (running) {
            try {
                if (sock == null) {
                    val (s, a) = openSocket()
                    sock = s; addr = a
                    lastHello = 0L
                }
                val t = now()
                val connected = t - lastAckMs < 1000
                status = if (connected) Status.CONNECTED else Status.CONNECTING
                error = null
                if (!connected && t - lastHello > 500) {
                    send(sock, addr!!, sig, Protocol.T_HELLO, Protocol.packHello(Build.MODEL ?: "phone"))
                    lastHello = t
                }
                val state = stateProvider(t, rttMs)
                send(sock, addr!!, sig, Protocol.T_STATE, Protocol.packState(state))
                sentPackets++
            } catch (e: Exception) {
                // Network changed / Wi-Fi dropped / unresolved host: back off and retry.
                status = Status.ERROR
                error = e.message ?: e.javaClass.simpleName
                sock?.close(); sock = null
                receiver?.join(300)
                reconnects++
                sleepMs(1000)
                next = System.nanoTime()
                continue
            }
            next += periodNs
            val wait = next - System.nanoTime()
            if (wait > 0) LockSupport.parkNanos(wait) else next = System.nanoTime()
        }
        // Tell the PC we are leaving so it neutralises immediately (best effort).
        sock?.let { s -> repeat(3) { runCatching { send(s, addr!!, sig, Protocol.T_BYE, ByteArray(0)) } } }
        sock?.close()
        socket = null
    }

    @Synchronized
    private fun send(s: DatagramSocket, addr: InetAddress, sig: Protocol.Signer, type: Int, payload: ByteArray) {
        seq++
        val data = Protocol.encode(sig, type, session, seq, payload)
        s.send(DatagramPacket(data, data.size, addr, port))
    }

    private fun receiveLoop(s: DatagramSocket, sig: Protocol.Signer) {
        val buf = ByteArray(512)
        val pkt = DatagramPacket(buf, buf.size)
        var rttAvg: Float? = null
        while (running && !s.isClosed) {
            try {
                pkt.length = buf.size
                s.receive(pkt)
                val (type, payload) = Protocol.decode(sig, buf, pkt.length) ?: continue
                if (type != Protocol.T_ACK) continue
                val ack = Protocol.unpackAck(payload) ?: continue
                val t = now()
                lastAckMs = t
                controllerOk = ack.controllerOk
                pcRxHz = ack.rxHz
                pcDropped = ack.dropped
                if (ack.echoPhoneMs != 0L) {
                    val rtt = ((t and 0xFFFFFFFFL) - ack.echoPhoneMs).toFloat()
                    if (rtt in 0f..5000f) {
                        rttAvg = rttAvg?.let { it + (rtt - it) * 0.2f } ?: rtt
                        rttMs = rttAvg
                    }
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun acquireWifiLock() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                   else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(mode, "diyf1wheel").apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseWifiLock() {
        runCatching { wifiLock?.release() }
        wifiLock = null
    }

    private fun sleepMs(ms: Long) {
        val end = now() + ms
        while (running && now() < end) Thread.sleep(50)
    }
}

package com.diyf1wheel.net

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Wire protocol v1. Must stay byte-for-byte identical to
 * windows-receiver/f1wheel/protocol.py (see docs/ARCHITECTURE.md).
 */
object Protocol {
    const val VERSION = 1
    const val DEFAULT_PORT = 47800
    const val BEACON_PORT = 47801
    const val TAG_LEN = 8
    const val HEADER_SIZE = 12
    const val STATE_SIZE = 25
    const val ACK_SIZE = 11

    const val T_HELLO = 1
    const val T_STATE = 2
    const val T_BYE = 3
    const val T_ACK = 16

    const val F_ACTIVE = 0x01
    const val F_SENSOR_OK = 0x02
    const val F_CALIBRATED = 0x04

    const val RTT_UNKNOWN = 0xFFFF

    /**
     * Held-button bits; order matches protocol.BUTTONS in Python.
     * Bits 0-7 are racing functions (re-mappable in the receiver). Bits 8-17 are the
     * fixed gamepad buttons of PAD mode (menus): they always map to the Xbox button of
     * the same name, so the game's own menu prompts (A = select, B = back) stay true.
     */
    enum class Button(val bit: Int, val label: String, val pad: Boolean = false) {
        DRS(1 shl 0, "DRS"),
        ERS(1 shl 1, "ERS"),
        CLUTCH(1 shl 2, "CLUTCH"),
        PAUSE(1 shl 3, "PAUSE"),
        LOOK_BACK(1 shl 4, "LOOK BACK"),
        FLASHBACK(1 shl 5, "FLASHBACK"),
        PIT_LIMITER(1 shl 6, "PIT"),
        MFD(1 shl 7, "MFD"),
        PAD_A(1 shl 8, "A", true),
        PAD_B(1 shl 9, "B", true),
        PAD_X(1 shl 10, "X", true),
        PAD_Y(1 shl 11, "Y", true),
        DPAD_UP(1 shl 12, "▲", true),
        DPAD_DOWN(1 shl 13, "▼", true),
        DPAD_LEFT(1 shl 14, "◀", true),
        DPAD_RIGHT(1 shl 15, "▶", true),
        START(1 shl 16, "START", true),
        BACK(1 shl 17, "BACK", true);

        companion object {
            /** The racing functions the setup screen lets you put on the driving screen. */
            val RACING = entries.filter { !it.pad }
        }
    }

    fun deriveKey(pin: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("F1WHEEL-PAIR-v1:${pin.trim()}".toByteArray(Charsets.UTF_8))

    /** HMAC signer. [Mac] is not thread-safe and the sender and receiver threads share one. */
    class Signer(key: ByteArray) {
        private val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        @Synchronized
        fun tag(data: ByteArray, len: Int): ByteArray {
            mac.update(data, 0, len)
            return mac.doFinal()
        }
    }

    data class State(
        val phoneMs: Long,
        val steer: Float,
        val throttle: Float,
        val brake: Float,
        val buttons: Int,
        val gearUp: Int,
        val gearDown: Int,
        val rawAngleDeg: Float,
        val sensorHz: Int,
        val rttMs: Float?,
        val flags: Int,
    )

    data class Ack(val echoPhoneMs: Long, val controllerOk: Boolean, val rxHz: Int, val dropped: Long)

    private fun header(buf: ByteBuffer, type: Int, session: Int, seq: Int) {
        buf.put('F'.code.toByte()).put('1'.code.toByte()).put(VERSION.toByte()).put(type.toByte())
        buf.putInt(session).putInt(seq)
    }

    private fun clamp(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v

    fun encode(signer: Signer, type: Int, session: Int, seq: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size + TAG_LEN)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        header(buf, type, session, seq)
        buf.put(payload)
        val bodyLen = HEADER_SIZE + payload.size
        System.arraycopy(signer.tag(out, bodyLen), 0, out, bodyLen, TAG_LEN)
        return out
    }

    fun packState(s: State): ByteArray {
        val buf = ByteBuffer.allocate(STATE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt((s.phoneMs and 0xFFFFFFFFL).toInt())
        buf.putShort(Math.round(clamp(s.steer, -1f, 1f) * 32767f).toShort())
        buf.putShort(Math.round(clamp(s.throttle, 0f, 1f) * 65535f).toShort())
        buf.putShort(Math.round(clamp(s.brake, 0f, 1f) * 65535f).toShort())
        buf.putInt(s.buttons)
        buf.putShort((s.gearUp and 0xFFFF).toShort())
        buf.putShort((s.gearDown and 0xFFFF).toShort())
        buf.putShort(clamp(s.rawAngleDeg * 100f, -32767f, 32767f).toInt().toShort())
        buf.putShort(s.sensorHz.coerceIn(0, 65535).toShort())
        val rtt = s.rttMs?.let { clamp(it * 10f, 0f, (RTT_UNKNOWN - 1).toFloat()).toInt() } ?: RTT_UNKNOWN
        buf.putShort(rtt.toShort())
        buf.put(s.flags.toByte())
        return buf.array()
    }

    fun packHello(name: String): ByteArray {
        var b = name.toByteArray(Charsets.UTF_8)
        if (b.size > 32) b = b.copyOf(32)
        return byteArrayOf(b.size.toByte()) + b
    }

    /** Returns (type, payload) when the datagram is well-formed and correctly signed. */
    fun decode(signer: Signer, data: ByteArray, len: Int): Pair<Int, ByteArray>? {
        if (len < HEADER_SIZE + TAG_LEN || len > 512) return null
        val bodyLen = len - TAG_LEN
        val expect = signer.tag(data, bodyLen)
        var diff = 0
        for (i in 0 until TAG_LEN) diff = diff or (expect[i].toInt() xor data[bodyLen + i].toInt())
        if (diff != 0) return null
        if (data[0] != 'F'.code.toByte() || data[1] != '1'.code.toByte() || data[2].toInt() != VERSION) return null
        return Pair(data[3].toInt() and 0xFF, data.copyOfRange(HEADER_SIZE, bodyLen))
    }

    fun unpackAck(p: ByteArray): Ack? {
        if (p.size != ACK_SIZE) return null
        val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        val echo = b.int.toLong() and 0xFFFFFFFFL
        val ok = b.get().toInt() != 0
        val hz = b.short.toInt() and 0xFFFF
        val dropped = b.int.toLong() and 0xFFFFFFFFL
        return Ack(echo, ok, hz, dropped)
    }

    /** Discovery beacon: "F1WB" ver(u8) port(u16) nameLen(u8) name. Not authenticated. */
    data class Beacon(val port: Int, val name: String)

    fun parseBeacon(data: ByteArray, len: Int): Beacon? {
        if (len < 8 || data[0] != 'F'.code.toByte() || data[1] != '1'.code.toByte() ||
            data[2] != 'W'.code.toByte() || data[3] != 'B'.code.toByte() || data[4].toInt() != VERSION) return null
        val port = (data[5].toInt() and 0xFF) or ((data[6].toInt() and 0xFF) shl 8)
        val n = minOf(data[7].toInt() and 0xFF, len - 8, 32)
        return Beacon(port, String(data, 8, n, Charsets.UTF_8))
    }
}

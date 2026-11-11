package com.diyf1wheel

import com.diyf1wheel.net.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Golden vectors were produced by windows-receiver/f1wheel/protocol.py - both sides must match. */
class ProtocolTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private val signer = Protocol.Signer(Protocol.deriveKey("123456"))

    @Test fun stateMatchesPython() {
        val st = Protocol.State(123456789, -0.5f, 1f, 0.25f, 0b101, 7, 3, -45.5f, 400, 8.5f, 7)
        val bytes = Protocol.encode(signer, Protocol.T_STATE, 0x01020304, 5, Protocol.packState(st))
        assertArrayEquals(hex("46310102040302010500000015cd5b0701c0ffff004005000000070003003aee90015500071c26547622ba04e3"), bytes)
    }

    @Test fun decodesPythonAck() {
        val data = hex("46310110040302016300000040e2010001c6000400000028008fb63811c4dc")
        val (type, payload) = Protocol.decode(signer, data, data.size)!!
        assertEquals(Protocol.T_ACK, type)
        val ack = Protocol.unpackAck(payload)!!
        assertEquals(123456L, ack.echoPhoneMs)
        assertEquals(true, ack.controllerOk)
        assertEquals(198, ack.rxHz)
        assertEquals(4L, ack.dropped)
    }

    @Test fun rejectsWrongPin() {
        val data = hex("46310110040302016300000040e2010001c6000400000028008fb63811c4dc")
        assertNull(Protocol.decode(Protocol.Signer(Protocol.deriveKey("000000")), data, data.size))
    }

    @Test fun rejectsTampered() {
        val data = hex("46310110040302016300000040e2010001c6000400000028008fb63811c4dc")
        data[13] = (data[13].toInt() xor 1).toByte()
        assertNull(Protocol.decode(signer, data, data.size))
    }

    @Test fun parsesBeacon() {
        val b = hex("4631574201b8ba064c4150544f50")
        val beacon = Protocol.parseBeacon(b, b.size)
        assertNotNull(beacon)
        assertEquals(47800, beacon!!.port)
        assertEquals("LAPTOP", beacon.name)
    }

    @Test fun sizes() {
        assertEquals(Protocol.STATE_SIZE, Protocol.packState(Protocol.State(0, 0f, 0f, 0f, 0, 0, 0, 0f, 0, null, 0)).size)
    }
}

package com.example.c001apk.compose.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CdidClientTest {

    private val timestamp = 1_700_000_000L

    @Test
    fun encodeFieldMatchesReference() {
        assertEquals("4o-p9i2Qfj", CdidClient.encodeField("iPhone18,3", timestamp))
        assertEquals("CDB", CdidClient.encodeField("ABC", timestamp))
        assertEquals("", CdidClient.encodeField("", timestamp))
    }

    @Test
    fun encodeUuidMatchesReference() {
        val (encoded, checksum) = CdidClient.encodeUuid(
            "A1B2C3D4-E5F6-4789-8ABC-DEF012345678",
            timestamp,
        )
        assertEquals(":8978655.473G261FG.F5EE.4DDC3BC92.B", encoded)
        assertEquals("d79c625", checksum)
    }

    @Test
    fun aesMatchesReference() {
        assertEquals(
            "d07a6c9df48edb486d4d97fed380e89b",
            CdidClient.aesEncrypt("hello".toByteArray()).hex(),
        )
    }

    @Test
    fun aesRoundTrip() {
        val plain = """{"err":"0","rid":"D2JWPUVVa+nT7zkKGnfpAYIytASAD1efdYZOmSL0/UTnI285"}"""
            .toByteArray()
        assertArrayEquals(plain, CdidClient.aesDecrypt(CdidClient.aesEncrypt(plain)))
    }

    private fun ByteArray.hex(): String = joinToString("") {
        "%02x".format(it.toInt() and 0xFF)
    }
}

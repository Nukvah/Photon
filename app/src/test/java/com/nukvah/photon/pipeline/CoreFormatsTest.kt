package com.nukvah.photon.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreFormatsTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test
    fun recognisesWhatRustReads() {
        assertTrue(CoreFormats.canRead(bytes(0xFF, 0xD8, 0xFF, 0xE1)))
        assertTrue(CoreFormats.canRead(bytes(0x89) + ascii("PNG\r\n")))
        assertTrue(CoreFormats.canRead(ascii("RIFF") + bytes(1, 2, 3, 4) + ascii("WEBPVP8 ")))
    }

    @Test
    fun leavesTheRestToAndroid() {
        // AVIF and HEIC are ISO-BMFF: size, then "ftyp".
        assertFalse(CoreFormats.canRead(bytes(0, 0, 0, 0x20) + ascii("ftypavif")))
        assertFalse(CoreFormats.canRead(bytes(0, 0, 0, 0x18) + ascii("ftypheic")))
        assertFalse(CoreFormats.canRead(ascii("RIFF") + bytes(1, 2, 3, 4) + ascii("WAVE")))
        assertFalse(CoreFormats.canRead(ascii("GIF89a")))
        assertFalse(CoreFormats.canRead(bytes(0xFF, 0xD8)))
        assertFalse(CoreFormats.canRead(ByteArray(0)))
    }
}

package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.image.BlurHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurHashTest {

    /** The canonical 4x3-component reference hash from the BlurHash spec. */
    private val referenceHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

    @Test
    fun `known hash decodes to the reference corner pixels`() {
        val pixels = BlurHash.decode(referenceHash, 4, 4)!!

        assertEquals(16, pixels.size)
        assertEquals(0xFF87A4B1.toInt(), pixels.first())
        assertEquals(0xFF998F86.toInt(), pixels.last())
    }

    @Test
    fun `decoding at the 32x32 placeholder size is stable and fully opaque`() {
        val pixels = BlurHash.decode(referenceHash, 32, 32)!!

        assertEquals(32 * 32, pixels.size)
        assertEquals(0xFF87A4B1.toInt(), pixels.first())
        assertEquals(0xFF858E93.toInt(), pixels.last())
        assertTrue(pixels.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun `punch scales the AC components without touching the DC average`() {
        val flat = BlurHash.decode(referenceHash, 4, 4, punch = 0f)!!

        // With no AC contribution every pixel collapses to the DC colour.
        assertEquals(1, flat.toSet().size)
        assertTrue(flat.first() != BlurHash.decode(referenceHash, 4, 4)!!.last())
    }

    @Test
    fun `malformed hashes return null instead of throwing`() {
        assertNull(BlurHash.decode(null, 32, 32))
        assertNull(BlurHash.decode("", 32, 32))
        assertNull(BlurHash.decode("LEH", 32, 32))
        // Right prefix, truncated body: length no longer matches the size flag.
        assertNull(BlurHash.decode(referenceHash.dropLast(1), 32, 32))
        assertNull(BlurHash.decode(referenceHash + "0", 32, 32))
        // Non-base83 characters anywhere in the string.
        assertNull(BlurHash.decode(referenceHash.replaceRange(10, 11, "\\"), 32, 32))
        assertNull(BlurHash.decode("\\EHV6nWB2yk8pyo0adR*.7kCMdnj", 32, 32))
    }

    @Test
    fun `non positive dimensions return null`() {
        assertNull(BlurHash.decode(referenceHash, 0, 32))
        assertNull(BlurHash.decode(referenceHash, 32, 0))
        assertNull(BlurHash.decode(referenceHash, -1, -1))
    }
}

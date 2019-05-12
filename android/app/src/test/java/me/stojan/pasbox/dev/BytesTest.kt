package me.stojan.pasbox.dev

import org.junit.Assert.assertEquals
import org.junit.Test

class BytesTest {
  @Test
  fun long_bigEndian() {
    ByteArray(8).also { 0x1122334455667788L.bigEndian(it) }
      .let { bytes ->
        assertEquals(0x11.toByte(), bytes[0])
        assertEquals(0x22.toByte(), bytes[1])
        assertEquals(0x33.toByte(), bytes[2])
        assertEquals(0x44.toByte(), bytes[3])
        assertEquals(0x55.toByte(), bytes[4])
        assertEquals(0x66.toByte(), bytes[5])
        assertEquals(0x77.toByte(), bytes[6])
        assertEquals(0x88.toByte(), bytes[7])
      }
  }

  @Test
  fun int_bigEndian() {
    ByteArray(4).also { 0x11223344.bigEndian(it) }
      .let { bytes ->
        assertEquals(0x11.toByte(), bytes[0])
        assertEquals(0x22.toByte(), bytes[1])
        assertEquals(0x33.toByte(), bytes[2])
        assertEquals(0x44.toByte(), bytes[3])
      }
  }

  @Test
  fun bytearray_fillRepeat() {
    String(ByteArray(5).also { it.fillRepeat(byteArrayOf('1'.toByte(), '2'.toByte())) })
      .let { value ->
        assertEquals("12121", value)
      }
  }
}
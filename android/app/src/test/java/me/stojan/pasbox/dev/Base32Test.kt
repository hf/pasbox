package me.stojan.pasbox.dev

import org.junit.Assert.assertEquals
import org.junit.Test

class Base32Test {
  @Test
  fun decode_blockOfOnes() {
    val bytes = charArrayOf('7', '7', '7', '7', '7', '7', '7', '7').decodeBase32()

    var expectedBytes = byteArrayOf(
      0b1111_1111.toByte(),
      0b1111_1111.toByte(),
      0b1111_1111.toByte(),
      0b1111_1111.toByte(),
      0b1111_1111.toByte()
    )

    assertEquals(expectedBytes.size, bytes.size)

    for (i in 0 until expectedBytes.size) {
      assertEquals("$i", expectedBytes[i], bytes[i])
    }
  }

  @Test
  fun decode_block() {
    val bytes = charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H').decodeBase32()

    var expectedBytes = byteArrayOf(
      0b0_00000_000.toByte(),
      0b0_01_00010_0.toByte(),
      0b0_0011_0010.toByte(),
      0b0_0_00101_00.toByte(),
      0b0_110_00111.toByte()
    )

    assertEquals(expectedBytes.size, bytes.size)

    for (i in 0 until expectedBytes.size) {
      assertEquals("$i", expectedBytes[i], bytes[i])
    }
  }

  @Test
  fun decode_incompleteBlock() {
    val bytes = charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G').decodeBase32()

    var expectedBytes = byteArrayOf(
      0b0_00000_000.toByte(),
      0b0_01_00010_0.toByte(),
      0b0_0011_0010.toByte(),
      0b0_0_00101_00.toByte(),
      0b0_110_00000.toByte()
    )

    assertEquals(expectedBytes.size, bytes.size)

    for (i in 0 until expectedBytes.size) {
      assertEquals("$i", expectedBytes[i], bytes[i])
    }
  }

  @Test
  fun decode_incompleteBlockWithPadding() {
    val bytes = charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', '=').decodeBase32()

    var expectedBytes = byteArrayOf(
      0b0_00000_000.toByte(),
      0b0_01_00010_0.toByte(),
      0b0_0011_0010.toByte(),
      0b0_0_00101_00.toByte(),
      0b0_110_00000.toByte()
    )

    assertEquals(expectedBytes.size, bytes.size)

    for (i in 0 until expectedBytes.size) {
      assertEquals("$i", expectedBytes[i], bytes[i])
    }
  }

  @Test
  fun decode_incompleteBlockHeterogenous() {
    val bytes = charArrayOf('A', 'B', 'c', ' ', 'D', '?', 'E', 'f', 'G', ':', '=', '!', '!').decodeBase32()

    var expectedBytes = byteArrayOf(
      0b0_00000_000.toByte(),
      0b0_01_00010_0.toByte(),
      0b0_0011_0010.toByte(),
      0b0_0_00101_00.toByte(),
      0b0_110_00000.toByte()
    )

    assertEquals(expectedBytes.size, bytes.size)

    for (i in 0 until expectedBytes.size) {
      assertEquals("$i", expectedBytes[i], bytes[i])
    }
  }

  @Test
  fun decode_sampleValue() {
    val bytes = "HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ".decodeBase32()

    val expectedBytes = byteArrayOf(
      61,
      198.toByte(),
      202.toByte(),
      164.toByte(),
      130.toByte(),
      74,
      109,
      40,
      135.toByte(),
      103,
      178.toByte(),
      51,
      30,
      32,
      180.toByte(),
      49,
      102,
      203.toByte(),
      133.toByte(),
      217.toByte()
    )

    assertEquals(expectedBytes.size, bytes.size)

    for (i in 0 until expectedBytes.size) {
      assertEquals("$i", expectedBytes[i], bytes[i])
    }
  }
}
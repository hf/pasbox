package me.stojan.pasbox.dev

import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.*

open class CharArraySequence(val chars: CharArray, val offset: Int, override val length: Int) : CharSequence {

  fun bytes(charset: Charset = Charsets.UTF_8): ByteArray = charset.encode(CharBuffer.wrap(chars)).array()

  override fun get(index: Int): Char = chars[offset + index]

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    CharArraySequence(chars, offset + startIndex, endIndex - startIndex)

  override fun toString(): String {
    return String.format(
      Locale.US,
      "CharArraySequence@%x(chars = %x, offset = %d, length = %d)",
      System.identityHashCode(this) and 0xFF,
      System.identityHashCode(chars) and 0xFF,
      offset,
      length
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CharArraySequence

    if (!chars.contentEquals(other.chars)) return false
    if (offset != other.offset) return false
    if (length != other.length) return false

    return true
  }

  override fun hashCode(): Int {
    var result = chars.contentHashCode()
    result = 31 * result + offset
    result = 31 * result + length
    return result
  }
}
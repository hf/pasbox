package me.stojan.pasbox.password

import me.stojan.pasbox.dev.CharArraySequence
import java.util.*

class Password(chars: CharArray) : CharArraySequence(chars, 0, chars.size), AutoCloseable {

  constructor(charSequence: CharSequence) : this(CharArray(charSequence.length).also {
    for (i in 0 until charSequence.length) {
      it[i] = charSequence[i]
    }
  })

  override fun close() {
    Arrays.fill(chars, 'P')
  }

  override fun toString(): String {
    return String.format(
      Locale.US, "PasswordSecret@%x(chars = %x, offset = %d, length = %d, closed = %s",
      System.identityHashCode(this) and 0xFF,
      System.identityHashCode(chars) and 0xFF,
      offset,
      length,
      if (length == chars.count { 'P' == it }) {
        "true"
      } else {
        "false"
      }
    )
  }

}
/*
 * Copyright (C) 2019
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package me.stojan.pasbox.dev

import com.google.protobuf.ByteString
import com.google.protobuf.asByteString
import me.stojan.pasbox.BuildConfig
import java.io.IOException
import java.io.OutputStream
import java.util.*

inline fun Long.bigEndian(bytes: ByteArray, offset: Int = 0): Int {
  if (bytes.size < offset + 8) {
    throw RuntimeException("Not enough size to write long in ByteArray at position $offset")
  }

  bytes[0 + offset] = ((this ushr (7 * 8)) and 0xFF).toByte()
  bytes[1 + offset] = ((this ushr (6 * 8)) and 0xFF).toByte()
  bytes[2 + offset] = ((this ushr (5 * 8)) and 0xFF).toByte()
  bytes[3 + offset] = ((this ushr (4 * 8)) and 0xFF).toByte()
  bytes[4 + offset] = ((this ushr (3 * 8)) and 0xFF).toByte()
  bytes[5 + offset] = ((this ushr (2 * 8)) and 0xFF).toByte()
  bytes[6 + offset] = ((this ushr (1 * 8)) and 0xFF).toByte()
  bytes[7 + offset] = ((this ushr (0 * 8)) and 0xFF).toByte()

  return 8
}

inline fun Int.bigEndian(bytes: ByteArray, offset: Int = 0): Int {
  if (bytes.size < offset + 4) {
    throw RuntimeException("Not enough size to write long in ByteArray at position $offset")
  }

  bytes[0 + offset] = ((this ushr (3 * 8)) and 0xFF).toByte()
  bytes[1 + offset] = ((this ushr (2 * 8)) and 0xFF).toByte()
  bytes[2 + offset] = ((this ushr (1 * 8)) and 0xFF).toByte()
  bytes[3 + offset] = ((this ushr (0 * 8)) and 0xFF).toByte()

  return 4
}

inline fun ByteArray.fillRepeat(bytes: ByteArray) {
  var i = 0
  while (i < size) {
    System.arraycopy(bytes, 0, this, i, Math.min(bytes.size, size - i))
    i += bytes.size
  }
}

inline fun <R> ByteArray.use(fn: (ByteArray) -> R): R {
  try {
    return fn(this)
  } finally {
    Arrays.fill(this, 0)
  }
}

object ByteArray16 {
  private val local = ThreadLocal<ByteArray>()

  fun __get(): ByteArray = local.get().let { bytes ->
    if (null == bytes) {
      val newArray = ByteArray(16)
      local.set(newArray)
      newArray
    } else {
      bytes
    }
  }

  inline fun <R> use(fn: (ByteArray) -> R): R = __get().use(fn)
}

class FixedSizeByteArrayOutputStream(size: Int) : OutputStream() {
  private val buf = ByteArray(size)
  private var pos = 0
  private var closed = false

  override fun write(b: Int) {
    if (closed) {
      throw IOException("Stream is closed")
    }

    if (pos + 1 > buf.size) {
      throw IOException("Not enough space")
    }

    buf[pos] = b.toByte()
    pos += 1
  }

  override fun write(b: ByteArray) {
    this.write(b, 0, b.size)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    if (closed) {
      throw IOException("Stream is closed")
    }

    if (pos + len > buf.size) {
      throw IOException("Not enough space")
    }

    System.arraycopy(b, off, buf, pos, len)
    pos += len
  }

  override fun close() {
    super.close()

    if (!closed) {
      closed = true

      if (BuildConfig.DEBUG) {
        if (pos != buf.size) {
          Log.w(this) {
            text("Closing position not equal to buffer size")
            param("pos", pos)
            param("size", buf.size)
          }
        }
      }
    }
  }

  fun toByteString(): ByteString =
    if (pos == buf.size) {
      buf.asByteString()
    } else {
      buf.asByteString(0, pos)
    }
}



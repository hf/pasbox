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

import android.util.SparseArray
import com.google.protobuf.ByteString
import com.google.protobuf.peek
import me.stojan.pasbox.BuildConfig
import java.io.OutputStream
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream

inline fun Cipher.updateAAD(value: Int) {
  ByteArray16.use { aead ->
    updateAAD(aead, 0, value.bigEndian(aead))
  }
}

inline fun Cipher.updateAAD(value: Long) {
  ByteArray16.use { aead ->
    updateAAD(aead, 0, value.bigEndian(aead))
  }
}

fun Cipher.update(byteString: ByteString) =
  byteString.peek { bytes, offset, length ->
    update(bytes, offset, length)
  }

fun Cipher.updateAAD(byteString: ByteString) =
  byteString.peek { bytes, offset, length ->
    updateAAD(bytes, offset, length)
  }

fun Cipher.doFinal(byteString: ByteString) =
  byteString.peek { bytes, offset, length ->
    doFinal(bytes, offset, length)
  }

class CleaningCipherOutputStream(os: OutputStream?, c: Cipher?) : CipherOutputStream(os, c) {
  private val buffers = SparseArray<ByteArray>(15)

  override fun write(b: ByteArray) {
    checkClean(b, 0, b.size)

    super.write(b)

    buffers.put(System.identityHashCode(b), b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    checkClean(b, off, len)

    super.write(b, off, len)

    buffers.put(System.identityHashCode(b), b)
  }

  override fun close() {
    super.close()

    for (i in 0 until buffers.size()) {
      buffers.valueAt(i)?.let { Arrays.fill(it, 0xCC.toByte()) }
    }

    buffers.clear()
  }

  private inline fun checkClean(bytes: ByteArray, off: Int, len: Int) {
    if (BuildConfig.DEBUG) {
      var ccCount = 0

      for (i in off + 1 until len) {
        if (0xCC.toByte() == bytes[i] && 0xCC.toByte() == bytes[i - 1]) {
          ccCount += 1
        }
      }

      if (ccCount > len / 4) {
        Log.w(this) {
          text("Byte array looks like it was cleaned before being encrypted!")
        }
      }

      if (ccCount > len / 2) {
        throw RuntimeException("Byte array looks like it was cleaned before being encrypted!")
      }
    }
  }

}

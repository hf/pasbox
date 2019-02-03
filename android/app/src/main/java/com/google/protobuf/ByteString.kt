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

package com.google.protobuf

val LiteralByteStringClass = Class.forName("com.google.protobuf.ByteString\$LiteralByteString")
val LiteralByteStringBytes = LiteralByteStringClass.getDeclaredField("bytes").apply {
  isAccessible = true
}

val BoundedByteStringClass = Class.forName("com.google.protobuf.ByteString\$BoundedByteString")
val BoundedByteStringOffset = BoundedByteStringClass.getDeclaredField("bytesOffset")
  .apply {
    isAccessible = true
  }
val BoundedByteStringLength = BoundedByteStringClass.getDeclaredField("bytesLength")
  .apply {
    isAccessible = true
  }

fun ByteArray.asByteString() = ByteString.wrap(this)

fun ByteString.asByteArray(): ByteArray = peek { bytes, _, _ -> bytes }

inline fun <R> ByteString.peek(fn: (ByteArray, Int, Int) -> R): R =
  if (LiteralByteStringClass == this.javaClass) {
    val bytes = LiteralByteStringBytes.get(this) as ByteArray

    fn(bytes, 0, bytes.size)
  } else if (BoundedByteStringClass == this.javaClass) {
    val offset = BoundedByteStringOffset.getInt(this)
    val length = BoundedByteStringLength.getInt(this)
    val bytes = LiteralByteStringBytes.get(this) as ByteArray

    fn(bytes, offset, length)
  } else {
    val bytes = this.toByteArray()

    fn(bytes, 0, bytes.size)
  }

package me.stojan.pasbox.dev

inline fun Char.toBase32(): Int =
  when {
    this in 'A'..'Z' -> (this - 'A')
    this in '2'..'7' -> 26 + (this - '2')
    this in 'a'..'z' -> (this - 'a')
    else -> -1
  }

fun CharArray.decodeBase32(): ByteArray =
  count { it.toBase32() > -1 }
    .let { base32size ->
      ByteArray((5 * base32size) ceilDiv 8)
        .also { bytes ->
          var byte = 0
          var bits = 0
          var total = 0

          for (i in 0 until this.size + (base32size % 8)) {
            val b32 = if (i >= this.size) {
              0
            } else {
              this[i].toBase32()
            }
            if (b32 < 0) continue
            if (total >= bytes.size) break

            bits += 5
            byte = (byte shl 5) or b32

            if (bits >= 8) {
              bytes[total++] = ((byte ushr (bits - 8)) and 0xFF).toByte()
              bits -= 8
            }
          }
        }
    }


inline fun String.decodeBase32(): ByteArray = toCharArray().use { it.decodeBase32() }

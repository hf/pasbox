package me.stojan.pasbox.totp

import me.stojan.pasbox.dev.bigEndian
import me.stojan.pasbox.dev.fillRepeat
import me.stojan.pasbox.dev.use
import java.security.Key
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TOTP private constructor(algorithm: String) {
  companion object {
    const val HMAC_SHA1 = "HmacSHA1"
    const val HMAC_SHA256 = "HmacSHA256"
    const val HMAC_SHA512 = "HmacSHA512"

    fun getInstance(type: String = HMAC_SHA1): TOTP =
      when (type) {
        HMAC_SHA1, HMAC_SHA256, HMAC_SHA512 -> TOTP(type)
        else -> throw RuntimeException("Unknown type $type")
      }

    fun keyFor(type: String, bytes: ByteArray): Key =
      when (type) {
        HMAC_SHA1 -> ByteArray(20).also { it.fillRepeat(bytes) }
        HMAC_SHA256 -> ByteArray(32).also { it.fillRepeat(bytes) }
        HMAC_SHA512 -> ByteArray(64).also { it.fillRepeat(bytes) }
        else -> throw RuntimeException("Unknown algorithm $type")
      }.use { key ->
        SecretKeySpec(key, type)
      }
  }

  private val mac: Mac = Mac.getInstance(algorithm)
  private var step: Long = 30 * 1000
  private var digits: Int = 8

  fun init(key: Key, stepMs: Long = 30 * 1000, digits: Int = 8) {
    if (stepMs <= 0) {
      throw RuntimeException("step must be >= 0")
    }

    if (digits <= 0) {
      throw RuntimeException("digits must be >= 0")
    }

    this.step = stepMs
    this.digits = digits

    mac.init(key)
  }

  fun init(bytes: ByteArray, stepMs: Long = 30 * 1000, digits: Int = 8) {
    init(
      keyFor(mac.algorithm, bytes),
      stepMs,
      digits
    )
  }

  fun now() = atTime(System.currentTimeMillis())

  fun atTime(timeMs: Long): CharArray = mac.doFinal(
    ByteArray(8).also {
      (timeMs / step).bigEndian(it)
    }
  ).let { hash ->
    (hash.last().toInt() and 0xF).let { offset ->
      0 or
        ((hash[0 + offset].toInt() and 0x7F) shl (3 * 8)) or
        ((hash[1 + offset].toInt() and 0xFF) shl (2 * 8)) or
        ((hash[2 + offset].toInt() and 0xFF) shl (1 * 8)) or
        ((hash[3 + offset].toInt() and 0xFF) shl (0 * 8))
    }.let { value ->
      CharArray(digits)
        .also {
          Arrays.fill(it, '0')

          var totp = value

          for (i in (it.size - 1) downTo 0) {
            if (totp <= 0) break
            it[i] = ('0'.toInt() + totp % 10).toChar()
            totp /= 10
          }
        }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TOTP

    if (mac != other.mac) return false
    if (step != other.step) return false
    if (digits != other.digits) return false

    return true
  }

  override fun hashCode(): Int {
    var result = mac.hashCode()
    result = 31 * result + step.hashCode()
    result = 31 * result + digits
    return result
  }


  override fun toString(): String {
    return String.format(
      Locale.US,
      "TOTP@%x(type = %s, digits = %d, step = %d)",
      System.identityHashCode(this) and 0xFF,
      digits,
      step
    )
  }
}


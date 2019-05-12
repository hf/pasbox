package me.stojan.pasbox.totp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test vectors available at: https://tools.ietf.org/html/rfc6238#page-15
 */
class TOTPTest {
  companion object {
    const val SECRET = "12345678901234567890"
  }

  @Test
  fun rfc6238_sha1_59() {
    String(TOTP.getInstance(TOTP.HMAC_SHA1).run {
      init(SECRET.toByteArray())
      atTime(59L * 1000L)
    }).let { assertEquals("94287082", it) }
  }

  @Test
  fun rfc6238_sha1_1111111111() {
    String(TOTP.getInstance(TOTP.HMAC_SHA1).run {
      init(SECRET.toByteArray())
      atTime(1111111111L * 1000L)
    }).let { assertEquals("14050471", it) }
  }

  @Test
  fun rfc6238_sha1_20000000000() {
    String(TOTP.getInstance(TOTP.HMAC_SHA1).run {
      init(SECRET.toByteArray())
      atTime(20000000000L * 1000)
    }).let {
      assertEquals("65353130", it)
    }
  }

  @Test
  fun rfc6238_sha256_59() {
    String(TOTP.getInstance(TOTP.HMAC_SHA256).run {
      init(SECRET.toByteArray())
      atTime(59 * 1000)
    }).let { assertEquals("46119246", it) }
  }

  @Test
  fun rfc6238_sha256_1111111111() {
    String(TOTP.getInstance(TOTP.HMAC_SHA256).run {
      init(SECRET.toByteArray())
      atTime(1111111111L * 1000L)
    }).let { assertEquals("67062674", it) }
  }

  @Test
  fun rfc6238_sha256_20000000000() {
    String(TOTP.getInstance(TOTP.HMAC_SHA256).run {
      init(SECRET.toByteArray())
      atTime(20000000000L * 1000)
    }).let {
      assertEquals("77737706", it)
    }
  }

  @Test
  fun rfc6238_sha512_59() {
    String(TOTP.getInstance(TOTP.HMAC_SHA512).run {
      init(SECRET.toByteArray())
      atTime(59 * 1000)
    }).let { assertEquals("90693936", it) }
  }

  @Test
  fun rfc6238_sha512_1111111111() {
    String(TOTP.getInstance(TOTP.HMAC_SHA512).run {
      init(SECRET.toByteArray())
      atTime(1111111111L * 1000)
    }).let { assertEquals("99943326", it) }
  }

  @Test
  fun rfc6238_sha512_20000000000() {
    String(TOTP.getInstance(TOTP.HMAC_SHA512).run {
      init(SECRET.toByteArray())
      atTime(20000000000L * 1000)
    }).let { assertEquals("47863826", it) }
  }
}
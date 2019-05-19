package me.stojan.pasbox.random

import com.google.protobuf.ByteString
import com.google.protobuf.asByteString
import java.security.SecureRandom

object RandomPadding {
  private val random = SecureRandom()

  fun new(): ByteArray =
    ByteArray((512 + random.nextInt(192)) / 8)
      .also {
        random.nextInt()
        random.nextBytes(it)
      }

  inline fun newBS(): ByteString = new().asByteString()

}
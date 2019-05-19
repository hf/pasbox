package me.stojan.pasbox.random

import com.google.protobuf.ByteString
import com.google.protobuf.asByteString
import java.security.SecureRandom

object UUID {
  private val random = SecureRandom()

  fun new(): ByteArray = ByteArray(16)
    .also {
      random.nextBytes(it)
    }

  inline fun newBS(): ByteString = new().asByteString()
}
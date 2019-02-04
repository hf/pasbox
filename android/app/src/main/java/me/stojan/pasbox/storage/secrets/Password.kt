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

package me.stojan.pasbox.storage.secrets

import com.google.protobuf.asByteString
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.signature.DeviceSignature
import me.stojan.pasbox.storage.SecretPrivate
import me.stojan.pasbox.storage.SecretPublic
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

object Password {
  fun create(title: String, password: String): Single<Pair<SecretPublic, SecretPrivate>> =
    Single.fromCallable {
      workerThreadOnly {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString().toByteArray().asByteString()

        val private = SecretPrivate.newBuilder()
          .setRandomPadding(
            SecureRandom().run {
              ByteArray(256 + nextInt(192))
                .also {
                  nextInt(192) // skip the next int
                  nextBytes(it)
                }
            }.asByteString()
          )
          .setId(id)
          .setCreatedAt(now)
          .setPassword(
            SecretPrivate.Password.newBuilder()
              .setPassword(password)
              .build()
          )
          .build()

        val privateBytes = private.toByteArray()

        val public = SecretPublic.newBuilder()
          .setId(id)
          .setCreatedAt(now)
          .setModifiedAt(now)
          .setHidden(false)
          .setSha256(
            MessageDigest.getInstance("SHA-256")
              .run {
                update(privateBytes)
                digest()
              }.asByteString()
          )
          .setSecp256R1Sha256(
            DeviceSignature.createBlocking()
              .apply {
                sign(privateBytes)
              }.signature().asByteString()
          )
          .setPassword(
            SecretPublic.Password.newBuilder()
              .setTitle(title)
              .build()
          )
          .build()

        Arrays.fill(privateBytes, 0)

        Pair(public, private)
      }
    }.subscribeOn(Schedulers.io())

}
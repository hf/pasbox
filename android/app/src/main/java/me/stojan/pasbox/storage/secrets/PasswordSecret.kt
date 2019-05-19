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
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.password.Password
import me.stojan.pasbox.random.RandomPadding
import me.stojan.pasbox.random.UUID
import me.stojan.pasbox.storage.SecretPrivate
import me.stojan.pasbox.storage.SecretPublic

object PasswordSecret {
  fun create(
    title: String,
    website: String?,
    user: String?,
    password: Password
  ): Single<Pair<SecretPublic, SecretPrivate>> =
    Single.fromCallable {
      workerThreadOnly {
        val now = System.currentTimeMillis()
        val id = UUID.newBS()

        val private =
          password.use {
            SecretPrivate.newBuilder()
              .setRandomPadding(RandomPadding.newBS())
              .setId(id)
              .setCreatedAt(now)
              .setPassword(
                SecretPrivate.Password.newBuilder()
                  .setPasswordBytes(it.bytes().asByteString())
                  .build()
              )
              .build()
          }

        val public = SecretPublic.newBuilder()
          .setId(id)
          .setCreatedAt(now)
          .setModifiedAt(now)
          .setHidden(false)
          .setPassword(
            SecretPublic.Password.newBuilder()
              .setTitle(title)
              .setWebsite(website)
              .setUser(user)
              .build()
          )
          .build()

        Pair(public, private)
      }
    }.cache()

}
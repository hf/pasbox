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

package me.stojan.pasbox.signature

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.security.*
import java.security.spec.ECGenParameterSpec

class DeviceSignature {
  companion object {
    fun create(): Single<DeviceSignature> =
      Single.fromCallable { createBlocking() }.subscribeOn(
        Schedulers.io()
      )

    fun createBlocking(): DeviceSignature = DeviceSignature().also { it.loadOrGenerate() }
  }

  private var public: PublicKey? = null
  private var signature: Signature? = null

  val publicKey: PublicKey
    get() {
      loadOrGenerate()

      return public!!
    }

  fun sign(bytes: ByteArray) {
    loadOrGenerate()

    signature!!.update(bytes)
  }

  fun signature(): ByteArray {
    loadOrGenerate()

    return signature!!.let { signature ->
      this.signature = null
      signature.sign()
    }
  }

  private fun loadOrGenerate() {
    if (null == signature) {
      KeyStore.getInstance("AndroidKeyStore")!!.run {
        load(null)

        getEntry("device-signature", null).let { entry ->
          if (null == entry) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
              .apply {
                initialize(
                  KeyGenParameterSpec.Builder(
                    "device-signature",
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                  )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .build()
                )
              }
              .generateKeyPair()
          } else {
            if (entry !is KeyStore.PrivateKeyEntry) {
              throw Error("Entry is not a PrivateKeyEntry")
            }

            KeyPair(entry.certificate.publicKey, entry.privateKey)
          }
        }
      }.let { key ->
        if (key is KeyPair) {
          public = key.public
          signature = Signature.getInstance("SHA256withECDSA")
            .apply {
              initSign(key.private)
            }
        } else {
          throw Error("Key is not a KeyPair")
        }
      }
    }
  }

}
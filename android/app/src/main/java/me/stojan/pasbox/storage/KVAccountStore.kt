package me.stojan.pasbox.storage

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.google.protobuf.asByteArray
import com.google.protobuf.asByteString
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import me.stojan.pasbox.dev.doFinal
import me.stojan.pasbox.dev.doFinalBS
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.hkdf.HKDF
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KVAccountStore(private val kvstore: KVStore) : AccountStore {

  override fun new(): Completable =
    kvstore.put(Single.fromCallable {
      workerThreadOnly {
        ArrayList<Pair<Int, ByteArray>>(10)
          .also { result ->
            SecureRandom()
              .let { random ->
                val hkdf1024 = ByteArray(1024).apply { random.nextBytes(this) }

                val authHmacSha256 = HKDF.derive512(hkdf1024, HKDF.USER_AUTHENTICATON_HMAC_SHA256)
                val openerHkdfSha256 = HKDF.derive512(hkdf1024, HKDF.USER_OPENER_HKDF_SHA256)

                val curve25519ecdh = HKDF.derive256(hkdf1024, HKDF.USER_ECDH_CURVE25519)
                val curve25519ecdsa = HKDF.derive256(hkdf1024, HKDF.USER_ECDSA_CURVE25519)

                val recoveryKey = KeyGenerator.getInstance("AES")
                  .run {
                    init(256, random)
                    generateKey()
                  }

                val secp256r1ecdh = generateEC("prime256v1", random)
                val secp256r1ecdsa = generateEC("prime256v1", random)
                val secp521r1ecdh = generateEC("secp521r1", random)
                val secp521r1ecdsa = generateEC("secp521r1", random)

                KeyStore.getInstance("AndroidKeyStore")!!
                  .run {
                    load(null)

                    setEntry(
                      "user-account-recovery-aes-gcm",
                      KeyStore.SecretKeyEntry(recoveryKey),
                      KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setUserAuthenticationRequired(true)
                        .build()
                    )

                    setEntry(
                      "user-authentication-hmac-sha256",
                      KeyStore.SecretKeyEntry(
                        SecretKeySpec(authHmacSha256, "HmacSHA256")
                      ),
                      KeyProtection.Builder(KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setUserAuthenticationRequired(true)
                        .build()
                    )

                    setEntry(
                      "user-opener-hkdf-sha256",
                      KeyStore.SecretKeyEntry(
                        SecretKeySpec(openerHkdfSha256, "HmacSHA256")
                      ),
                      KeyProtection.Builder(KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setUserAuthenticationRequired(true)
                        .build()
                    )

                    result.add(
                      Pair(KV.ACCOUNT_RECOVERY, Cipher.getInstance("AES/GCM/NoPadding")
                        .run {
                          init(Cipher.ENCRYPT_MODE, recoveryKey)

                          AccountRecoveryContainer.newBuilder()
                            .setAesGcmNopad96(iv.asByteString())
                            .setContent(
                              doFinalBS(
                                AccountRecovery.newBuilder()
                                  .setKeys(
                                    MasterKeys.newBuilder()
                                      .setHkdf1024(hkdf1024.asByteString())
                                      .setSecp256R1Ecdh(secp256r1ecdh.asByteString())
                                      .setSecp256R1Ecdsa(secp256r1ecdsa.asByteString())
                                      .setSecp521R1Ecdh(secp521r1ecdh.asByteString())
                                      .setSecp521R1Ecdsa(secp521r1ecdsa.asByteString())
                                      .build()
                                  )
                                  .build()
                                  .toByteString()
                              )
                            )
                            .build()
                            .toByteArray()
                        })
                    )

                    SecretKeySpec(HKDF.derive256(openerHkdfSha256, HKDF.USER_OPENER), "AES")
                      .let { opener ->
                        result.add(
                          Pair(KV.MASTER_KEY_ECDH_CURVE25519, Cipher.getInstance("AES/GCM/NoPadding")
                            .run {
                              init(Cipher.ENCRYPT_MODE, opener)

                              OpenerContainer.newBuilder()
                                .setAesGcmNopad96(iv.asByteString())
                                .setContent(
                                  doFinalBS(
                                    Opener.newBuilder()
                                      .setCurve25519Ecdh(curve25519ecdh.asByteString())
                                      .build()
                                      .toByteString()
                                  )
                                )
                                .build()
                                .toByteArray()
                            })
                        )

                        result.add(
                          Pair(KV.MASTER_KEY_ECDSA_CURVE25519, Cipher.getInstance("AES/GCM/NoPadding")
                            .run {
                              init(Cipher.ENCRYPT_MODE, opener)

                              OpenerContainer.newBuilder()
                                .setAesGcmNopad96(iv.asByteString())
                                .setContent(
                                  doFinalBS(
                                    Opener.newBuilder()
                                      .setCurve25519Ecdsa(curve25519ecdsa.asByteString())
                                      .build()
                                      .toByteString()
                                  )
                                )
                                .build()
                                .toByteArray()
                            })
                        )

                        result.add(
                          Pair(KV.MASTER_KEY_ECDH_SECP256R1, Cipher.getInstance("AES/GCM/NoPadding")
                            .run {
                              init(Cipher.ENCRYPT_MODE, opener)

                              OpenerContainer.newBuilder()
                                .setAesGcmNopad96(iv.asByteString())
                                .setContent(
                                  doFinalBS(
                                    Opener.newBuilder()
                                      .setSecp256R1Ecdh(secp256r1ecdh.asByteString())
                                      .build()
                                      .toByteString()
                                  )
                                )
                                .build()
                                .toByteArray()
                            })
                        )

                        result.add(
                          Pair(KV.MASTER_KEY_ECDSA_SCEP256R1, Cipher.getInstance("AES/GCM/NoPadding")
                            .run {
                              init(Cipher.ENCRYPT_MODE, opener)

                              OpenerContainer.newBuilder()
                                .setAesGcmNopad96(iv.asByteString())
                                .setContent(
                                  doFinalBS(
                                    Opener.newBuilder()
                                      .setSecp256R1Ecdsa(secp256r1ecdsa.asByteString())
                                      .build()
                                      .toByteString()
                                  )
                                )
                                .build()
                                .toByteArray()
                            })
                        )

                        result.add(
                          Pair(KV.MASTER_KEY_ECDH_SECP521R1, Cipher.getInstance("AES/GCM/NoPadding")
                            .run {
                              init(Cipher.ENCRYPT_MODE, opener)

                              OpenerContainer.newBuilder()
                                .setAesGcmNopad96(iv.asByteString())
                                .setContent(
                                  doFinalBS(
                                    Opener.newBuilder()
                                      .setSecp521R1Ecdh(secp521r1ecdh.asByteString())
                                      .build()
                                      .toByteString()
                                  )
                                )
                                .build()
                                .toByteArray()
                            })
                        )

                        result.add(
                          Pair(KV.MASTER_KEY_ECDSA_SECP521R1, Cipher.getInstance("AES/GCM/NoPadding")
                            .run {
                              init(Cipher.ENCRYPT_MODE, opener)

                              OpenerContainer.newBuilder()
                                .setAesGcmNopad96(iv.asByteString())
                                .setContent(
                                  doFinalBS(
                                    Opener.newBuilder()
                                      .setSecp521R1Ecdsa(secp521r1ecdsa.asByteString())
                                      .build()
                                      .toByteString()
                                  )
                                )
                                .build()
                                .toByteArray()
                            })
                        )

                      }
                  }
              }
          }
      }
    })

  fun recovery(): Maybe<AccountRecovery> =
    kvstore.get(KV.ACCOUNT_RECOVERY)
      .map { bytes ->
        workerThreadOnly {
          AccountRecoveryContainer.parseFrom(bytes)
            .let { container ->

              KeyStore.getInstance("AndroidKeyStore")!!
                .run {
                  load(null)

                  getKey("user-account-recovery-aes-gcm", null)!!
                    .let { recoveryKey ->
                      Cipher.getInstance("AES/GCM/NoPadding")
                        .run {
                          init(
                            Cipher.DECRYPT_MODE,
                            recoveryKey,
                            GCMParameterSpec(128, container.aesGcmNopad96.asByteArray())
                          )

                          AccountRecovery.parseFrom(doFinal(container.content))
                        }
                    }
                }
            }
        }
      }

  fun update(argon2: KDFArgon2, password: String, key: ByteArray) =
    recovery()
      .flatMapCompletable { recovery ->
        kvstore.put(KV.ACCOUNT_RECOVERY, Single.fromCallable {
          workerThreadOnly {
            AccountRecovery.newBuilder(recovery)
              .setKdfArgon2(argon2)
              .setPasswordArgon2(password)
              .build()
              .let { accountRecovery ->
                KeyStore.getInstance("AndroidKeyStore")!!
                  .run {
                    load(null)

                    // TODO: Regenerate user-account-recovery-aes-gcm and set its validity end in 30 days or so.

                    getKey("user-account-recovery-aes-gcm", null)!!
                      .let { recoveryKey ->
                        Cipher.getInstance("AES/GCM/NoPadding")
                          .run {
                            init(Cipher.ENCRYPT_MODE, recoveryKey)

                            AccountRecoveryContainer.newBuilder()
                              .setAesGcmNopad96(iv.asByteString())
                              .setContent(doFinalBS(accountRecovery.toByteString()))
                              .build()
                              .toByteArray()
                          }
                      }
                  }
              }
          }
        }).andThen(kvstore.put(KV.ACCOUNT, Single.fromCallable {
          workerThreadOnly {
            Cipher.getInstance("AES/GCM/NoPadding")
              .run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

                MasterKeysContainer.newBuilder()
                  .setAesGcmNopad96(iv.asByteString())
                  .setContent(doFinalBS(recovery.keys.toByteString()))
                  .build()
                  .toByteString()
              }.let { keys ->
                Account.newBuilder()
                  .setArgon2(argon2)
                  .setKeys(keys)
                  .build()
                  .toByteArray()
              }
          }
        }))
      }

  private fun generateEC(curve: String, random: SecureRandom) =
    KeyPairGenerator.getInstance("EC")
      .run {
        initialize(ECGenParameterSpec(curve), random)
        (generateKeyPair().private as ECPrivateKey).s.toByteArray()
      }
}
package me.stojan.pasbox.storage

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.google.protobuf.asByteString
import io.reactivex.Completable
import io.reactivex.Single
import me.stojan.pasbox.dev.encipher
import me.stojan.pasbox.dev.use
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.hkdf.HKDF
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

class KVAccountStore(private val kvstore: KVStore) : AccountStore {

  override fun new(): Completable =
    kvstore.put(Single.fromCallable {
      workerThreadOnly {
        ArrayList<Pair<Int, ByteArray>>(10)
          .also { result ->
            SecureRandom()
              .let { random ->
                ByteArray(2048 / 8).use { hkdf2048 ->
                  HKDF.derive512(hkdf2048, HKDF.USER_AUTHENTICATON_HMAC_SHA256).use { authHmacSha256 ->
                    HKDF.derive512(hkdf2048, HKDF.USER_OPENER_HKDF_SHA256).use { openerHkdfSha256 ->
                      HKDF.derive256(hkdf2048, HKDF.USER_ECDH_CURVE25519).use { curve25519ecdh ->
                        HKDF.derive256(hkdf2048, HKDF.USER_ECDSA_CURVE25519).use { curve25519ecdsa ->

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
                                        AccountRecovery.newBuilder()
                                          .setKeys(
                                            MasterKeys.newBuilder()
                                              .setHkdf2048(hkdf2048.asByteString())
                                              .setSecp256R1Ecdh(secp256r1ecdh.asByteString())
                                              .setSecp256R1Ecdsa(secp256r1ecdsa.asByteString())
                                              .setSecp521R1Ecdh(secp521r1ecdh.asByteString())
                                              .setSecp521R1Ecdsa(secp521r1ecdsa.asByteString())
                                              .build()
                                          )
                                          .build()
                                          .encipher(this)
                                      )
                                      .build()
                                      .toByteArray()
                                  })
                              )

                              SecretKeySpec(HKDF.derive256(openerHkdfSha256, HKDF.USER_MASTER_KEY_OPENER), "AES")
                                .let { opener ->
                                  result.add(
                                    Pair(KV.MASTER_KEY_ECDH_CURVE25519, Cipher.getInstance("AES/GCM/NoPadding")
                                      .run {
                                        init(Cipher.ENCRYPT_MODE, opener)

                                        OpenerContainer.newBuilder()
                                          .setAesGcmNopad96(iv.asByteString())
                                          .setContent(
                                            Opener.newBuilder()
                                              .setCurve25519Ecdh(curve25519ecdh.asByteString())
                                              .build()
                                              .encipher(this)
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
                                            Opener.newBuilder()
                                              .setCurve25519Ecdsa(curve25519ecdsa.asByteString())
                                              .build()
                                              .encipher(this)
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
                                            Opener.newBuilder()
                                              .setSecp256R1Ecdh(secp256r1ecdh.asByteString())
                                              .build()
                                              .encipher(this)
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
                                            Opener.newBuilder()
                                              .setSecp256R1Ecdsa(secp256r1ecdsa.asByteString())
                                              .build()
                                              .encipher(this)
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
                                            Opener.newBuilder()
                                              .setSecp521R1Ecdh(secp521r1ecdh.asByteString())
                                              .build()
                                              .encipher(this)
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
                                            Opener.newBuilder()
                                              .setSecp521R1Ecdsa(secp521r1ecdsa.asByteString())
                                              .build()
                                              .encipher(this)
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
                  }
                }
              }
          }
      }
    })

  private fun generateEC(curve: String, random: SecureRandom) =
    KeyPairGenerator.getInstance("EC")
      .run {
        initialize(ECGenParameterSpec(curve), random)
        (generateKeyPair().private as ECPrivateKey).s.toByteArray()
      }
}
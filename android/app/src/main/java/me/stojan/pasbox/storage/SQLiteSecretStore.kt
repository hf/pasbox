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

package me.stojan.pasbox.storage

import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.protobuf.asByteArray
import com.google.protobuf.asByteString
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import me.stojan.pasbox.dev.*
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class SQLiteSecretStore(db: Single<SQLiteDatabase>) : SecretStore {

  private val changes = PublishSubject.create<Pair<SecretPublic, Secret>>()
  override val modifications = changes

  private val db: Single<Pair<SQLiteDatabase, Key>> = db.map { db ->
    workerThreadOnly {
      db.execSQL("CREATE TABLE IF NOT EXISTS secrets (id INTEGER PRIMARY KEY, uuid BLOB NOT NULL UNIQUE, value BLOB NOT NULL, created_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP, modified_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP);")
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_secrets_modified_at_created_at ON secrets (modified_at DESC, created_at DESC);")

      Pair(db, KeyStore.getInstance("AndroidKeyStore")
        .run {
          load(null)

          val key = getKey("secrets-binder-aead", null)

          if (null == key) {
            KeyGenerator.getInstance("AES", "AndroidKeyStore")
              .apply {
                init(
                  KeyGenParameterSpec.Builder(
                    "secrets-binder-aead",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                  )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                )
              }
              .generateKey()
          } else {
            key
          }
        })
    }
  }.cache()

  private inner class AESGCMNoPad96Save(key: Key, override val data: Pair<SecretPublic, SecretPrivate>) :
    SecretStore.Save() {
    override val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
      .apply {
        init(Cipher.ENCRYPT_MODE, key)
      }

    override fun execute(): Single<Secret> =
      Single.fromCallable {
        workerThreadOnly {
          val public = data.first.toByteArray()
          val private = data.second.toByteArray()

          cipher.updateAAD(data.second.id.asByteArray()) // only set the private ID here
          cipher.updateAAD(public)

          Secret.newBuilder()
            .setAesGcmNopad96(cipher.iv.asByteString())
            .setId(data.first.id) // cryptographically makes sure that the public and private IDs are the same
            .setPublic(public.asByteString())
            .setPrivate(cipher.doFinal(private).asByteString())
            .build()
        }
      }.flatMap { secret ->
        db.map { (db, key) ->
          secret.toByteArray().use { secretBytes ->
            db.compileStatement("INSERT OR REPLACE INTO secrets (uuid, value, modified_at) VALUES (?, ?, ?);")
              .apply {
                val uuid = secret.id.asByteArray()

                bindBlob(1, uuid)
                bindLong(3, System.currentTimeMillis() / 1000)
                Cipher.getInstance("AES/GCM/NoPadding")
                  .apply {
                    init(Cipher.ENCRYPT_MODE, key)

                    updateAAD(uuid)
                    bindBlob(
                      2,
                      SQLiteSecret.newBuilder()
                        .setAesGcmNopad96(this.iv.asByteString())
                        .setAeadId(true)
                        .setSecret(doFinal(secretBytes).asByteString())
                        .build()
                        .toByteArray()
                    )
                  }
              }
              .executeInsert()
          }

          changes.onNext(Pair(data.first, secret))
        }.flatMap { Single.just(secret) }
      }
        .subscribeOn(Schedulers.io())
  }

  override fun save(data: Single<Pair<SecretPublic, SecretPrivate>>): Single<SecretStore.Save> =
    data.map { data ->
      workerThreadOnly {
        KeyStore.getInstance("AndroidKeyStore")
          .run {
            load(null)

            AESGCMNoPad96Save(getKey("secrets-user-aead", null).let { key ->
              if (null == key) {
                Log.v(this@SQLiteSecretStore) { text("Generating new key") }

                KeyGenerator.getInstance("AES", "AndroidKeyStore")
                  .apply {
                    init(
                      KeyGenParameterSpec.Builder(
                        "secrets-user-aead",
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                      )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationValidityDurationSeconds(5)
                        .build()
                    )
                  }
                  .generateKey()
              } else {
                Log.v(this@SQLiteSecretStore) { text("Loading key") }
                key
              }
            }, data)
          }
      }
    }

  override fun page(query: SecretStore.Query): Single<SecretStore.Page> =
    db.map { (db, key) ->
      workerThreadOnly {
        val total = db.compileStatement("SELECT COUNT(*) FROM secrets;")
          .simpleQueryForLong()

        db.query {
          select("id", "uuid", "value", "created_at", "modified_at")
          from("secrets")
          orderBy("modified_at DESC")
          limit(query.count, query.offset)
        }.use { cursor ->
          if (cursor.moveToFirst()) {
            val uuidIndex = cursor.getColumnIndexOrThrow("uuid")
            val valueIndex = cursor.getColumnIndexOrThrow("value")

            SecretStore.Page(total, query.offset, ArrayList<Pair<SecretPublic, Secret>>(cursor.count)
              .apply {
                do {
                  val secret = Secret.parseFrom(
                    SQLiteSecret.parseFrom(cursor.getBlob(valueIndex))
                      .let { container ->
                        Cipher.getInstance("AES/GCM/NoPadding")
                          .run {
                            init(
                              Cipher.DECRYPT_MODE,
                              key,
                              GCMParameterSpec(128, container.aesGcmNopad96.asByteArray())
                            )

                            if (container.aeadId) {
                              updateAAD(cursor.getBlob(uuidIndex))
                            }

                            doFinal(container.secret)
                          }
                      })

                  val public = SecretPublic.parseFrom(secret.public)

                  add(Pair(public, secret))
                } while (cursor.moveToNext())
              })
          } else {
            SecretStore.Page(0, query.offset, ArrayList())
          }
        }
      }
    }.subscribeOn(Schedulers.io())
}
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

  private val db: Single<Pair<SQLiteDatabase, Key>> =
    db.map { db ->
      workerThreadOnly {
        db.execSQL("CREATE TABLE IF NOT EXISTS secrets (id INTEGER PRIMARY KEY, uuid BLOB NOT NULL UNIQUE, value BLOB NOT NULL, created_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP, modified_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_secrets_modified_at_created_at ON secrets (modified_at DESC, created_at DESC);")

        KeyStore.getInstance("AndroidKeyStore")!!.run {
          load(null)

          getKey("secrets-binder-aes-gcm", null).let { key ->
            key ?: KeyGenerator.getInstance("AES", "AndroidKeyStore")
              .run {
                init(
                  KeyGenParameterSpec.Builder(
                    "secrets-binder-aes-gcm",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                  )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                )
                generateKey()
              }
          }
        }.let { key ->
          Pair(db, key)
        }
      }
    }.cache()

  override fun open(data: Single<Pair<SecretPublic, Secret>>) =
    data
      .subscribeOn(Schedulers.io())
      .map { (public, secret) ->
        workerThreadOnly {
          KeyStore.getInstance("AndroidKeyStore")
            .run {
              load(null)

              getKey("user-secrets-aead", null)!!.let { key ->
                Cipher.getInstance("AES/GCM/NoPadding").run {
                  init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, secret.aesGcmNopad96.asByteArray()))
                  updateAAD(secret.id)
                  updateAAD(secret.public)
                  doFinal(secret.private)
                }.let {
                  SecretPrivate.parseFrom(it)
                }
              }
            }.let { private ->
              Pair(public, private)
            }
        }
      }

  override fun save(data: Single<Pair<SecretPublic, SecretPrivate>>) =
    data.flatMap { data ->
      val public = data.first
      val private = data.second

      db.map { (db, dbKey) ->
        workerThreadOnly {
          val insert = db.compileStatement("INSERT INTO secrets (uuid, value) VALUES (?, ?);")

          KeyStore.getInstance("AndroidKeyStore")
            .run {
              load(null)

              getKey("user-secrets-aead", null).let { key ->
                key ?: KeyGenerator.getInstance("AES", "AndroidKeyStore")
                  .run {
                    init(
                      KeyGenParameterSpec.Builder(
                        "user-secrets-aead",
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                      )
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationValidityDurationSeconds(5)
                        .build()
                    )
                    generateKey()
                  }
              }.let { key ->
                val privateBS = private.toByteString()
                val publicBS = public.toByteString()

                Cipher.getInstance("AES/GCM/NoPadding").run {
                  init(Cipher.ENCRYPT_MODE, key)

                  updateAAD(private.id)
                  updateAAD(publicBS)

                  Secret.newBuilder()
                    .setAesGcmNopad96(iv.asByteString())
                    .setId(public.id)
                    .setPublic(publicBS)
                    .setPrivate(doFinalBS(privateBS))
                    .build()
                }
              }.let { secret ->
                Cipher.getInstance("AES/GCM/NoPadding").run {
                  init(Cipher.ENCRYPT_MODE, dbKey)

                  updateAAD(private.id)

                  SecretContainer.newBuilder()
                    .setAesGcmNopad96(iv.asByteString())
                    .setSecret(doFinal(secret.toByteArray()).asByteString())
                    .build()
                    .toByteArray()
                    .let { container ->
                      insert.run {
                        clearBindings()
                        bindBlob(1, public.id.asByteArray())
                        bindBlob(2, container)
                        executeInsert()
                      }

                      changes.onNext(Pair(public, secret))
                    }
                }
              }
            }

          data
        }
      }
    }

  override fun page(query: SecretStore.Query) =
    db.map { (db, dbKey) ->
      workerThreadOnly {
        val count = db.compileStatement("SELECT COUNT(*) FROM secrets;")

        db.transaction {
          count.simpleQueryForLong().let { total ->
            db.query {
              select("uuid", "value")
              from("secrets")
              orderBy("modified_at DESC")
              limit(query.count, query.offset)
            }.use { cursor ->
              if (cursor.moveToFirst()) {
                val uuidIndex = cursor.getColumnIndexOrThrow("uuid")
                val valueIndex = cursor.getColumnIndexOrThrow("value")

                ArrayList<Pair<ByteArray, ByteArray>>(cursor.count).apply {
                  do {
                    add(Pair(cursor.getBlob(uuidIndex), cursor.getBlob(valueIndex)))
                  } while (cursor.moveToNext())
                }
              } else {
                ArrayList()
              }
            }.let { Pair(total, it) }
          }
        }.let { (total, list) ->
          list
            .map { (uuid, value) ->
              val container = SecretContainer.parseFrom(value)
              Cipher.getInstance("AES/GCM/NoPadding")
                .run {
                  init(Cipher.DECRYPT_MODE, dbKey, GCMParameterSpec(128, container.aesGcmNopad96.asByteArray()))
                  updateAAD(uuid)
                  doFinal(container.secret)
                }
                .let { Secret.parseFrom(it) }
                .let { secret ->
                  Pair(SecretPublic.parseFrom(secret.public), secret)
                }
            }
            .let { SecretStore.Page(total, query.offset, it) }
        }
      }
    }.subscribeOn(Schedulers.io())

}
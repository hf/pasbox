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
import com.google.protobuf.ByteString
import com.google.protobuf.asByteArray
import com.google.protobuf.asByteString
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import me.stojan.pasbox.dev.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class SQLiteKVStore(db: Single<SQLiteDatabase>) : KVStore {

  private val db = db.map { db ->
    workerThreadOnly {
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS kvstore (id INTEGER PRIMARY KEY, value BLOB DEFAULT NULL, created_at INTEGER DEFAULT CURRENT_TIMESTAMP, modified_at INTEGER DEFAULT CURRENT_TIMESTAMP);"
      )

      Pair(db, KeyStore.getInstance("AndroidKeyStore")!!.run {
        load(null)

        getKey("kvstore-binder-aead", null).let { key ->
          if (null == key) {
            Log.v(this@SQLiteKVStore) { text("Generating AES256GCM key") }
            KeyGenerator.getInstance("AES", "AndroidKeyStore")
              .apply {
                init(
                  KeyGenParameterSpec.Builder(
                    "kvstore-binder-aead",
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
            Log.v(this@SQLiteKVStore) { text("Loading AES256GCM key") }
            key
          }
        }
      })
    }
  }.cache()

  private val changes = PublishSubject.create<Pair<Int, ByteArray?>>()

  override val modifications: Observable<Pair<Int, ByteArray?>>
    get() = changes.observeOn(Schedulers.io())

  override fun warmup(): Disposable = db.subscribe({ (_, error) ->
    Log.v(this@SQLiteKVStore) { text("Warmed up.") }
  }, { error ->
    Log.e(this@SQLiteKVStore) { text("Failed to warm up."); error(error); }
  })

  override fun put(values: Single<List<Pair<Int, ByteArray>>>): Completable =
    values.flatMapCompletable { values ->
      db.map { (db, dbKey) ->
        workerThreadOnly {
          val insert = db.compileStatement("INSERT OR REPLACE INTO kvstore (id, value, modified_at) VALUES (?, ?, ?);")

          values.map {
            Triple(it.first, Cipher.getInstance("AES/GCM/NoPadding").run {
              init(Cipher.ENCRYPT_MODE, dbKey)

              updateAAD(it.first)

              KVContainer.newBuilder()
                .setAesGcmNopad96(iv.asByteString())
                .setValue(
                  ByteArray16.use { padding ->
                    KVPadding.newBuilder()
                      .setPadding(padding.asByteString())
                      .setValue(ByteString.copyFrom(it.second)) // enciphering will clear the it.second array
                      .build()
                      .encipher(this)
                  }
                )
                .build()
                .toByteArray()
            }, it.second)
          }.let { ready ->
            val now = System.currentTimeMillis() / 1000

            db.transaction {
              ready.forEach {
                insert.clearBindings()
                insert.bindLong(1, it.first.toLong())
                insert.bindBlob(2, it.second)
                insert.bindLong(3, now)
                insert.executeInsert()
              }
            }

            ready.forEach {
              changes.onNext(Pair(it.first, it.third))
            }

          }
        }
      }.ignoreElement()
    }

  override fun put(key: Int, value: Single<ByteArray>): Completable = put(value.map { value ->
    listOf(Pair(key, value))
  })

  override fun get(key: Int): Maybe<ByteArray> =
    db.flatMapMaybe { (db, dbKey) ->
      Maybe.fromCallable<ByteArray> {
        workerThreadOnly {
          db.query {
            select("id", "value")
            from("kvstore")
            where("id = ?")
            args(key)
            limit(1)
          }.use { cursor ->
            if (!cursor.moveToFirst()) {
              null
            } else {
              val valueIndex = cursor.getColumnIndexOrThrow("value")

              if (cursor.isNull(valueIndex)) {
                null
              } else {
                val value = cursor.getBlob(valueIndex)

                KVContainer.parseFrom(value.asByteString())
                  .let { container ->
                    Cipher.getInstance("AES/GCM/NoPadding").run {
                      init(Cipher.DECRYPT_MODE, dbKey, GCMParameterSpec(128, container.aesGcmNopad96.asByteArray()))
                      updateAAD(key)
                      doFinal(container.value)
                    }
                  }
                  .let { KVPadding.parseFrom(it) }
                  .let { it.value.asByteArray() }
              }
            }
          }
        }
      }
    }.subscribeOn(Schedulers.io())

  override fun watch(key: Int, nulls: Boolean, get: Boolean): Observable<Pair<Int, ByteArray?>> =
    if (get) {
      this.get(key)
        .map { Pair<Int, ByteArray?>(key, it) }
        .switchIfEmpty(Maybe.just(Pair(key, null)))
        .toObservable()
    } else {
      Observable.empty()
    }
      .mergeWith(modifications)
      .filter { workerThreadOnly { key == it.first && (null != it.second || nulls) } }

  override fun del(key: Int): Completable =
    db.map { (db, _) ->
      workerThreadOnly {
        db.compileStatement("UPDATE kvstore SET value = NULL, modified_at = CURRENT_TIMESTAMP WHERE id = ?;")
          .apply {
            bindLong(1, key.toLong())
            executeUpdateDelete()
          }

        changes.onNext(Pair(key, null))
      }
    }.ignoreElement()
}
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
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.query
import me.stojan.pasbox.dev.workerThreadOnly
import java.security.KeyStore
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.Mac

class SQLiteKVStore(db: Single<SQLiteDatabase>) : KVStore {

  private val db = db.map { db ->
    workerThreadOnly {
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS kvstore (id INTEGER PRIMARY KEY, value BLOB DEFAULT NULL, mac BLOB DEFAULT NULL, created_at INTEGER DEFAULT CURRENT_TIMESTAMP, modified_at INTEGER DEFAULT CURRENT_TIMESTAMP);"
      )

      val macKey = KeyStore.getInstance("AndroidKeyStore")!!.run {
        load(null)

        getKey("kvstore-mac", null).let { key ->
          if (null == key) {
            Log.v(this@SQLiteKVStore) { text("Generating HmacSha256 key") }
            KeyGenerator.getInstance("HmacSha256", "AndroidKeyStore")
              .apply {
                init(
                  KeyGenParameterSpec.Builder("kvstore-mac", KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
                )
              }
              .generateKey()
          } else {
            Log.v(this@SQLiteKVStore) { text("Loading HmacSha256 key") }
            key
          }
        }
      }

      Pair(db, macKey)
    }
  }.cache()

  private val changes = PublishSubject.create<Pair<Int, ByteArray?>>()

  override val modifications: Observable<Pair<Int, ByteArray?>>
    get() = changes

  override fun warmup(): Disposable = db.subscribe({ (_, error) ->
    Log.v(this@SQLiteKVStore) { text("Warmed up.") }
  }, { error ->
    Log.e(this@SQLiteKVStore) { text("Failed to warm up."); error(error); }
  })

  override fun put(key: Int, value: Single<ByteArray>): Completable =
    value.flatMapCompletable { bytes ->
      db.flatMapCompletable { (db, macKey) ->
        Completable.fromCallable {
          workerThreadOnly {
            db.compileStatement("INSERT OR REPLACE INTO kvstore (id, value, mac, modified_at) VALUES (?, ?, ?, ?);")
              .apply {
                bindLong(1, key.toLong())
                bindBlob(2, bytes)
                bindBlob(3, Mac.getInstance("HmacSha256").apply {
                  init(macKey)
                  Log.v(this@SQLiteKVStore) { text("Computing HMAC with"); param("provider", provider.name) }
                }.doFinal(bytes))
                bindLong(4, System.currentTimeMillis() / 1000)
              }.executeInsert()

            changes.onNext(Pair(key, bytes))
          }
        }
      }.subscribeOn(Schedulers.io())
    }

  override fun get(key: Int): Maybe<ByteArray> =
    db.flatMapMaybe { (db, macKey) ->
      Maybe.fromCallable<ByteArray> {
        workerThreadOnly {
          db.query { select("id", "value", "mac"); from("kvstore"); where("id = ?"); args(key); limit(1) }
            .use { cursor ->
              if (!cursor.moveToFirst()) {
                null
              } else {
                val valueIndex = cursor.getColumnIndexOrThrow("value")
                val macIndex = cursor.getColumnIndexOrThrow("mac")

                if (cursor.isNull(valueIndex) != cursor.isNull(macIndex)) {
                  throw Error("Database has been tampered at key=$key")
                } else {
                  if (cursor.isNull(valueIndex)) {
                    null
                  } else {
                    val value = cursor.getBlob(valueIndex)
                    val mac = cursor.getBlob(macIndex)

                    val computedMac = Mac.getInstance("HmacSha256")
                      .apply {
                        init(macKey)
                        Log.v(this@SQLiteKVStore) { text("Verifying HMAC with"); param("provider", provider.name) }
                      }
                      .doFinal(value)

                    if (!Arrays.equals(mac, computedMac)) {
                      throw Error("Database has been tampered at key=$key")
                    }

                    value
                  }
                }
              }
            }
            .also {
              changes.onNext(Pair(key, it))
            }
        }
      }
    }.subscribeOn(Schedulers.io())

  override fun del(key: Int): Completable =
    db.flatMapCompletable { (db, _) ->
      Completable.fromCallable {
        workerThreadOnly {
          db.compileStatement("UPDATE kvstore SET value = NULL, mac = NULL, modified_at = CURRENT_TIMESTAMP WHERE id = ?;")
            .apply {
              bindLong(1, key.toLong())
            }
            .executeUpdateDelete()

          changes.onNext(Pair(key, null))
        }
      }
    }.subscribeOn(Schedulers.io())
}
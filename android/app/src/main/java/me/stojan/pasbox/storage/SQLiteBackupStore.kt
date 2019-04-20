package me.stojan.pasbox.storage

import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.protobuf.asByteArray
import com.google.protobuf.asByteString
import io.reactivex.Completable
import io.reactivex.Single
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.query
import me.stojan.pasbox.dev.transaction
import me.stojan.pasbox.dev.workerThreadOnly
import org.whispersystems.curve25519.Curve25519
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SQLiteBackupStore(db: Single<SQLiteDatabase>) : BackupStore {

  private val db = db.map { db ->
    workerThreadOnly {
      db.execSQL("CREATE TABLE IF NOT EXISTS restore_keys (id INTEGER PRIMARY KEY, value BLOB NOT NULL);")
      db.execSQL("CREATE TABLE IF NOT EXISTS backup_keys (id INTEGER PRIMARY KEY, value BLOB NOT NULL);")
      db.execSQL("CREATE TABLE IF NOT EXISTS backup_entries (id INTEGER PRIMARY KEY, value BLOB NOT NULL);")

      Pair(db, KeyStore.getInstance("AndroidKeyStore")!!
        .run {
          load(null)

          getKey("backups-binder-aes-gcm", null).let { key ->
            if (null == key) {
              Log.v(this@SQLiteBackupStore) { text("Generating AES256GCM key") }
              KeyGenerator.getInstance("AES", "AndroidKeyStore")
                .apply {
                  init(
                    KeyGenParameterSpec.Builder(
                      "backups-binder-aes-gcm",
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
              Log.v(this@SQLiteBackupStore) { text("Loading AES256GCM key") }
              key
            }
          }
        })
    }
  }.cache()

  override fun setupNew(): Completable =
    db.map { (db, dbKey) ->
      workerThreadOnly {
        val insert = db.compileStatement("INSERT INTO restore_keys (value) VALUES (?);")

        KeyStore.getInstance("AndroidKeyStore").run {
          load(null)

          getKey("user-restore-aes-gcm", null)!!.let { authenticationKey ->
            Curve25519.getInstance(Curve25519.BEST).run {
              (0 until 10).map {
                generateKeyPair()
              }.map {
                RestoreKey.newBuilder()
                  .setCurve25519(it.privateKey.asByteString())
                  .setBackup(
                    BackupKey.newBuilder()
                      .setCurve25519(it.publicKey.asByteString())
                      .build()
                  )
                  .build()
              }.map {
                Cipher.getInstance("AES/GCM/NoPadding").run {
                  init(Cipher.ENCRYPT_MODE, authenticationKey)
                  RestoreKeyContainer.newBuilder()
                    .setAesGcmNopad96(iv.asByteString())
                    .setContent(doFinal(it.toByteArray()).asByteString())
                    .build()
                    .toByteArray()
                }
              }.map {
                Cipher.getInstance("AES/GCM/NoPadding").run {
                  init(Cipher.ENCRYPT_MODE, dbKey)
                  RestoreKeyContainer.newBuilder()
                    .setAesGcmNopad96(iv.asByteString())
                    .setContent(doFinal(it).asByteString())
                    .build()
                    .toByteArray()
                }
              }.let { restoreKeys ->

                db.transaction {
                  restoreKeys.forEach {
                    insert.apply {
                      clearBindings()
                      bindBlob(1, it)
                      executeInsert()
                    }
                  }
                }

              }
            }
          }
        }
      }
    }.ignoreElement()

  override fun backup(data: Single<Pair<SecretPublic, SecretPrivate>>): Completable =
    data.flatMap { secret ->
      db.map { (db, dbKey) ->
        workerThreadOnly {
          val insert = db.compileStatement("INSERT INTO backup_entries (value) VALUES (?);")

          KeyStore.getInstance("AndroidKeyStore").run {
            load(null)

            getKey("user-authentication-hmac256", null).let { authentication ->
              db.transaction {
                db.query {
                  from("backup_keys")
                  select("value")
                }.use { cursor ->
                  ArrayList<ByteArray>(cursor.count).apply {
                    val valueIndex = cursor.getColumnIndexOrThrow("value")

                    if (cursor.moveToFirst()) {
                      do {
                        add(cursor.getBlob(valueIndex))
                      } while (cursor.moveToNext())
                    }
                  }
                }.asSequence()
                  .map {
                    BackupKeyContainer.parseFrom(it)
                  }.map { container ->
                    Cipher.getInstance("AES/GCM/NoPadding")
                      .run {
                        init(Cipher.DECRYPT_MODE, dbKey)
                        doFinal(container.content.asByteArray())
                      }
                  }.map {
                    BackupKey.parseFrom(it)
                      .curve25519.asByteArray()
                  }.map { key ->
                    Curve25519.getInstance(Curve25519.BEST)
                      .let { curve ->
                        val ephemeral = curve.generateKeyPair()

                        Triple(key, ephemeral.publicKey, curve.calculateAgreement(ephemeral.privateKey, key))
                      }
                  }.map { (restoreKey, entryKey, agreement) ->
                    Pair(
                      agreement, Backup.Key.newBuilder()
                        .setCurve25519Hmac256(
                          Backup.Key.Curve25519.newBuilder()
                            .setEntryPub(entryKey.asByteString())
                            .setRestorePub(restoreKey.asByteString())
                            .build()
                        )
                        .build()
                    )
                  }.map { (agreement, backupKey) ->
                    backupKey.toByteString().let { backupKeyBS ->
                      Cipher.getInstance("AES/GCM/NoPadding")
                        .run {
                          init(
                            Cipher.ENCRYPT_MODE,
                            SecretKeySpec(
                              Mac.getInstance("HmacSHA256")
                                .run {
                                  init(authentication)
                                  doFinal(agreement)
                                },
                              "AES"
                            )
                          )

                          updateAAD(secret.second.id.asByteArray()) // makes sure the private secret is equal to the public
                          updateAAD(backupKeyBS.asByteArray())

                          Backup.newBuilder()
                            .setAesGcmNopad96(iv.asByteString())
                            .setId(secret.first.id)
                            .setKey(backupKeyBS)
                            .setContent(
                              doFinal(
                                BackupContent.newBuilder()
                                  .setId(secret.first.id)
                                  .setPublic(secret.first)
                                  .setPrivate(secret.second)
                                  .build()
                                  .toByteArray()
                              ).asByteString()
                            )
                            .build()
                            .toByteString()
                        }
                    }
                  }.map { entry ->
                    Cipher.getInstance("AES/GCM/NoPadding")
                      .run {
                        init(Cipher.ENCRYPT_MODE, dbKey)
                        doFinal(
                          BackupContainer.newBuilder()
                            .setAesGcmNopad96(iv.asByteString())
                            .setContent(entry)
                            .build()
                            .toByteArray()
                        )
                      }
                  }.toList().forEach { entry ->
                    insert.apply {
                      clearBindings()
                      bindBlob(1, entry)
                      executeInsert()
                    }
                  }
              }
            }
          }

        }
      }
    }.ignoreElement()
}
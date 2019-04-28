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
import dagger.Module
import dagger.Provides
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.dev.workerThreadOnly
import javax.inject.Singleton

@Module
class AppStorageModule(val app: App) {

  @Provides
  @Singleton
  fun provideSQLiteDatabase(): Single<SQLiteDatabase> =
    Single.fromCallable {
      workerThreadOnly {
        app.isRestricted
        SQLiteDatabase.openDatabase(
          app.getDatabasePath("pasbox.sqlite3").absolutePath, null, 0 or
            SQLiteDatabase.OPEN_READWRITE or
            SQLiteDatabase.CREATE_IF_NECESSARY or
            SQLiteDatabase.NO_LOCALIZED_COLLATORS or
            SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
        )
      }
    }
      .subscribeOn(Schedulers.io())
      .cache()

  @Provides
  @Singleton
  fun provideSQLiteKVStore(db: Single<SQLiteDatabase>): KVStore = SQLiteKVStore(db)

  @Provides
  @Singleton
  fun provideSQLiteSecretStore(db: Single<SQLiteDatabase>): SecretStore = SQLiteSecretStore(db)

  @Provides
  @Singleton
  fun provideBackupStore(db: Single<SQLiteDatabase>): BackupStore = SQLiteBackupStore(db)
}
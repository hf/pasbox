package me.stojan.pasbox.storage

import io.reactivex.Completable
import io.reactivex.Single

interface BackupStore {
  fun setupNew(): Completable
  fun backup(data: Single<Pair<SecretPublic, SecretPrivate>>): Completable
}
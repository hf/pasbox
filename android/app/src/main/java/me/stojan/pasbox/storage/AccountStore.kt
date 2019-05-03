package me.stojan.pasbox.storage

import io.reactivex.Completable
import io.reactivex.Maybe

interface AccountStore {

  fun new(): Completable

  fun accountRecovery(): Maybe<AccountRecovery>
  fun secure(
    accountRecovery: AccountRecovery,
    kdf: KDFArgon2,
    hash: ByteArray,
    password: ByteArray? = null
  ): Completable
}
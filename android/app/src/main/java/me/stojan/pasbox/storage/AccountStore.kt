package me.stojan.pasbox.storage

import io.reactivex.Completable

interface AccountStore {

  fun new(): Completable

}
package me.stojan.pasbox.dev

import android.os.PowerManager

inline fun <R> PowerManager.WakeLock.use(fn: () -> R) =
  try {
    acquire()
    fn()
  } finally {
    release()
  }
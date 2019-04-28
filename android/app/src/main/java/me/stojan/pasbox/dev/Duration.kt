package me.stojan.pasbox.dev

import android.os.SystemClock

inline fun duration(fn: () -> Unit): Long {
  val start = SystemClock.elapsedRealtime()

  fn()
  return SystemClock.elapsedRealtime() - start
}

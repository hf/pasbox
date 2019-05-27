package me.stojan.pasbox.dev

import android.content.res.TypedArray

inline fun <R> TypedArray.use(fn: TypedArray.() -> R): R {
  try {
    return fn(this)
  } finally {
    recycle()
  }
}
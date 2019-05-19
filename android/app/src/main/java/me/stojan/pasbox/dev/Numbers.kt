package me.stojan.pasbox.dev

inline infix fun Int.ceilDiv(div: Int) =
  this / div + if (this % div > 0) {
    1
  } else {
    0
  }

inline infix fun Long.ceilDiv(div: Long) =
  this / div + if (this % div > 0) {
    1
  } else {
    0
  }

package me.stojan.pasbox.password

interface PasswordGeneratorParams {
  val length: Int
  val alphabet: CharArray

  fun verify(password: CharArray): Boolean
}

class ASCIIPasswordGeneratorParams(
  override val length: Int,
  val multicase: Boolean = true,
  val digits: Boolean = true,
  val symbols: Boolean = true
) : PasswordGeneratorParams {
  companion object {
    internal const val UPCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    internal const val LOCASE = "abcdefghijklmnopqrstuvwxyz"
    internal const val DIGITS = "0123456789"
    internal const val SYMBOLS = "!@#$%^&*()-_=+[]{}:?<>~"
  }

  override val alphabet: CharArray
    get() {
      var size = LOCASE.length

      if (multicase) size += UPCASE.length
      if (digits) size += DIGITS.length
      if (symbols) size += SYMBOLS.length

      return CharArray(size)
        .also {
          var i = 0

          for (j in 0 until LOCASE.length) {
            it[i] = LOCASE[j]
            i += 1
          }

          if (multicase) {
            for (j in 0 until UPCASE.length) {
              it[i] = UPCASE[j]
              i += 1
            }
          }

          if (digits) {
            for (j in 0 until DIGITS.length) {
              it[i] = DIGITS[j]
              i += 1
            }
          }

          if (symbols) {
            for (j in 0 until SYMBOLS.length) {
              it[i] = SYMBOLS[j]
              i += 1
            }
          }
        }
    }

  override fun verify(password: CharArray): Boolean =
    password.count { it.isLowerCase() }
      .let { lowcaseCount ->
        password.count { it.isUpperCase() }
          .let { upcaseCount ->
            password.count { it.isDigit() }
              .let { digitCount ->
                val symbolCount = length - lowcaseCount - upcaseCount - digitCount

                var isValid = lowcaseCount > 0

                if (multicase) isValid = upcaseCount > 0 && isValid
                if (digits) isValid = digitCount > 0 && isValid
                if (symbols) isValid = symbolCount > 0 && isValid

                isValid
              }
          }
      }
}


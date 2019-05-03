package me.stojan.pasbox.password

import java.security.SecureRandom

class PasswordGenerator {
  companion object {
    fun getInstance() = PasswordGenerator()
  }

  internal constructor()

  fun generate(
    params: PasswordGeneratorParams,
    random: SecureRandom = SecureRandom()
  ): Password =
    params.alphabet.let { alphabet ->
      Password(
        CharArray(params.length)
          .also {
            for (k in 0 until 256) {
              for (i in 0 until params.length) {
                it[i] = alphabet[random.nextInt(alphabet.size)]
              }

              if (params.verify(it)) {
                return@also
              }
            }

            throw RuntimeException("Unable to generate a valid password after 255 attempts")
          })
    }

}
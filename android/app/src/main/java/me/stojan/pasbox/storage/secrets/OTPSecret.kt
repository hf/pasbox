package me.stojan.pasbox.storage.secrets

import android.net.Uri
import com.google.protobuf.asByteString
import io.reactivex.Maybe
import io.reactivex.Single
import me.stojan.pasbox.dev.decodeBase32
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.random.RandomPadding
import me.stojan.pasbox.random.UUID
import me.stojan.pasbox.storage.SecretPrivate
import me.stojan.pasbox.storage.SecretPublic

object OTPSecret {

  fun parse(uri: String?): Maybe<Uri> = Maybe.fromCallable {
    if (null == uri) {
      null
    } else {
      try {
        Uri.parse(uri)
          .let { parsed ->
            val isValid = "otpauth" == parsed.scheme &&
              "totp" == parsed.authority &&
              (parsed.lastPathSegment ?: "").isNotEmpty() &&
              (parsed.getQueryParameter("secret") ?: "").length >= 20

            if (isValid) {
              parsed
            } else {
              null
            }
          }
      } catch (e: Throwable) {
        null
      }
    }
  }

  fun create(parsed: Uri) = Single.fromCallable {
    workerThreadOnly {
      val type = parsed.authority
      val label = (parsed.lastPathSegment ?: "").split(":")
      val issuer = parsed.getQueryParameter("issuer") ?: label[0]
      val secret = parsed.getQueryParameter("secret")!!
      val digits = parsed.getQueryParameter("digits") ?: "6"
      val algorithm = parsed.getQueryParameter("algorithm")?.toUpperCase() ?: "SHA1"
      val hotpCounter = parsed.getQueryParameter("counter") ?: "0"
      val totpPeriod = parsed.getQueryParameter("period") ?: "30"

      val now = System.currentTimeMillis()
      val uuid = UUID.newBS()

      val private = SecretPrivate.newBuilder()
        .setRandomPadding(RandomPadding.newBS())
        .setCreatedAt(now)
        .setId(uuid)
        .setOtp(
          SecretPrivate.OTP.newBuilder()
            .setUri(parsed.toString())
            .setDigits(Integer.parseInt(digits))
            .apply {
              secret.decodeBase32().asByteString()
                .let { decodedSecret ->
                  when (algorithm) {
                    "SHA1" -> secretSha1 = decodedSecret
                    "SHA256" -> secretSha256 = decodedSecret
                    "SHA512" -> secretSha512 = decodedSecret
                    else -> throw RuntimeException("Unknown algorithm $algorithm")
                  }
                }

              when (type) {
                "totp" -> period = Integer.parseInt(totpPeriod)
                "hotp" -> initial = Integer.parseInt(hotpCounter)
                else -> throw RuntimeException("Unknown OTP type $type")
              }
            }
            .build()
        )
        .build()

      val public = SecretPublic.newBuilder()
        .setId(uuid)
        .setCreatedAt(now)
        .setModifiedAt(now)
        .setOtp(
          SecretPublic.OTP.newBuilder()
            .setIssuer(issuer)
            .setAccount(
              if (label.size > 1) {
                label[1]
              } else {
                label[0]
              }
            )
            .build()
        )
        .build()

      Pair(public, private)
    }
  }

}
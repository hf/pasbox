package me.stojan.pasbox.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.protobuf.asByteArray
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.R
import me.stojan.pasbox.storage.Secret
import me.stojan.pasbox.storage.SecretPrivate
import me.stojan.pasbox.storage.SecretPublic
import me.stojan.pasbox.totp.TOTP

class UISecretOTP @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : UISecret(context, attrs, defStyleAttr), KeyguardButton.Callbacks {

  private lateinit var content: ViewGroup
  private lateinit var closed: ViewGroup
  private lateinit var toOpen: ViewGroup
  private lateinit var opened: ViewGroup

  private lateinit var title: TextView
  private lateinit var open: KeyguardButton
  private lateinit var otp: OTPView

  override fun onFinishInflate() {
    super.onFinishInflate()

    content = findViewById(R.id.content)
    closed = findViewById(R.id.closed)
    toOpen = findViewById(R.id.to_open)
    opened = findViewById(R.id.opened)

    title = findViewById(R.id.title)
    open = findViewById(R.id.open)
    otp = findViewById(R.id.otp)
    open.requestCode = RequestCodes.UI_OPEN_OTP_KEYGUARD
    open.callbacks = this
  }

  override fun onBind(value: Pair<SecretPublic, Secret>) {
    super.onBind(value)

    opened.visibility = View.GONE
    toOpen.visibility = View.GONE
    closed.visibility = View.VISIBLE

    title.text = value.first.otp.title

    content.setOnClickListener {
      beginDelayedTransition()

      when (closed.visibility) {
        View.VISIBLE -> {
          closed.visibility = View.GONE
          opened.visibility = View.GONE
          toOpen.visibility = View.VISIBLE
          open.transition = 0
        }

        else -> {
          closed.visibility = View.VISIBLE
          toOpen.visibility = View.GONE
          opened.visibility = View.GONE
          otp.totp = null
        }
      }
    }
  }

  override fun onSuccess(button: KeyguardButton) {
    super.onSuccess(button)

    disposeOnRecycle(
      App.Components.Storage.secrets().open(Single.just(value))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({ (_, private) ->
          val totp = TOTP.getInstance(
            when (private.otp.secretCase) {
              SecretPrivate.OTP.SecretCase.SECRET_SHA1 -> TOTP.HMAC_SHA1
              SecretPrivate.OTP.SecretCase.SECRET_SHA256 -> TOTP.HMAC_SHA256
              SecretPrivate.OTP.SecretCase.SECRET_SHA512 -> TOTP.HMAC_SHA512
              else -> throw RuntimeException("Unknown OTP secret type ${private.otp.secretCase}")
            }
          ).apply {
            init(
              when (private.otp.secretCase) {
                SecretPrivate.OTP.SecretCase.SECRET_SHA1 -> private.otp.secretSha1
                SecretPrivate.OTP.SecretCase.SECRET_SHA256 -> private.otp.secretSha256
                SecretPrivate.OTP.SecretCase.SECRET_SHA512 -> private.otp.secretSha512
                else -> throw RuntimeException("Unknown OTP secret type ${private.otp.secretCase}")
              }.asByteArray(),
              stepMs = private.otp.period * 1000L,
              digits = private.otp.digits
            )
          }

          beginDelayedTransition()

          closed.visibility = View.GONE
          toOpen.visibility = View.GONE
          opened.visibility = View.VISIBLE

          otp.totp = totp
        }, {

        })
    )

  }
}
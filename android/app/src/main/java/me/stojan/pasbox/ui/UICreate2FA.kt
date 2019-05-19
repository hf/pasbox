package me.stojan.pasbox.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import me.stojan.pasbox.R
import me.stojan.pasbox.barcode.BarcodeScanner
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.decodeBase32
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.storage.secrets.OTPSecret
import me.stojan.pasbox.totp.TOTP

class UICreate2FA @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
  private val activity: UIActivity get() = context as UIActivity

  private lateinit var scanContainer: FrameLayout
  private lateinit var scan: TextureView
  private lateinit var barcodeScanner: BarcodeScanner

  private lateinit var valueLayout: ViewGroup
  private lateinit var title: TextInputEditText
  private lateinit var secret: TextInputEditText
  private lateinit var otp: TextInputEditText

  override fun onFinishInflate() {
    super.onFinishInflate()

    scanContainer = findViewById(R.id.scan_container)

    valueLayout = findViewById(R.id.value_layout)
    title = valueLayout.findViewById(R.id.title)
    secret = valueLayout.findViewById(R.id.secret)
    otp = valueLayout.findViewById(R.id.otp)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    when (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)) {
      PackageManager.PERMISSION_DENIED -> {
        activity.disposeOnDestroy(activity.permissions
          .filter { RequestCodes.UI_CREATE_2FA_REQUEST_CAMERA_PERMISSION == it.first }
          .firstElement()
          .subscribe {
            when (it.third[0]) {
              PackageManager.PERMISSION_GRANTED -> scanBarcode()
            }
          }
        )

        activity.requestPermissions(
          arrayOf(Manifest.permission.CAMERA),
          RequestCodes.UI_CREATE_2FA_REQUEST_CAMERA_PERMISSION
        )
      }

      else -> scanBarcode()
    }
  }

  private fun scanBarcode() {
    context.getSystemService(CameraManager::class.java)
      .let { cameraManager ->
        scanContainer.removeAllViews()
        scan = TextureView(context)
        scanContainer.addView(scan)
        barcodeScanner = BarcodeScanner(cameraManager.cameraIdList.first(), scan)

        activity.disposeOnDestroy(barcodeScanner.results
          .flatMapMaybe { OTPSecret.parse(it.rawValue) }
          .doOnEach {
            Log.v(this@UICreate2FA) {
              text("URI")
              param("uri", it.value)
            }
          }
          .firstElement()
          .subscribe { uri ->
            mainThreadOnly {
              Log.v(this@UICreate2FA) {
                text("OTP URI detected")
                param("uri", uri)

                scanContainer.removeAllViews()
                setupSecret(uri)

                post {
                  TransitionManager.beginDelayedTransition(parent as ViewGroup)

                  scanContainer.visibility = View.GONE
                  valueLayout.visibility = View.VISIBLE
                }
              }
            }
          })
      }
  }

  private fun setupSecret(uri: Uri) {
    val label = uri.lastPathSegment!!
    val secret = uri.getQueryParameter("secret")!!
    val digits = Integer.parseInt(uri.getQueryParameter("digits") ?: "6")
    val algorithm = uri.getQueryParameter("algorithm")

    this.title.setText(label.replace(':', ' '))
    this.secret.setText(secret)

    this.otp.setText(
      String(
        TOTP.getInstance(
          when (algorithm) {
            "SHA256" -> TOTP.HMAC_SHA256
            "SHA512" -> TOTP.HMAC_SHA512
            else -> TOTP.HMAC_SHA1
          }
        ).run {
          init(secret.decodeBase32(), digits = digits)
          now()
        }
      )
    )
  }

}
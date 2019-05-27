package me.stojan.pasbox.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
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

class UICreateOTP @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),
  ChildOf<UICreateSecret>,
  ImplicitSceneRoot,
  KeyguardButton.Callbacks {

  override val parentView: UICreateSecret by ChildOf.Auto()
  override val sceneRoot: ViewGroup? by ImplicitSceneRoot.Auto

  private val activity: UIActivity get() = context as UIActivity

  private lateinit var scanContainer: FrameLayout
  private lateinit var scan: TextureView
  private lateinit var barcodeScanner: BarcodeScanner

  private lateinit var valueLayout: ViewGroup
  private lateinit var title: TextInputEditText
  private lateinit var secret: TextInputEditText
  private lateinit var otp: TextInputEditText
  private lateinit var save: KeyguardButton

  private var uri: Uri? = null

  override fun onFinishInflate() {
    super.onFinishInflate()

    scanContainer = findViewById(R.id.scan_container)

    valueLayout = findViewById(R.id.value_layout)
    title = valueLayout.findViewById(R.id.title)
    secret = valueLayout.findViewById(R.id.secret)
    otp = valueLayout.findViewById(R.id.otp)
    save = valueLayout.findViewById(R.id.save)
    save.requestCode = RequestCodes.UI_CREATE_2FA_PASSWORD_KEYGUARD
    save.callbacks = this
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    when (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)) {
      PackageManager.PERMISSION_DENIED -> {
        parentView.disposeOnRecycle(
          activity.permissions
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
        barcodeScanner = BarcodeScanner(
          cameraManager.cameraIdList.firstOrNull {
            CameraCharacteristics.LENS_FACING_BACK ==
              cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING)
          } ?: cameraManager.cameraIdList.first(), scan)

        parentView.disposeOnRecycle(
          barcodeScanner.results
            .flatMapMaybe { OTPSecret.parse(it.rawValue) }
            .doOnEach {
              Log.v(this@UICreateOTP) {
                text("URI")
                param("uri", it.value)
              }
            }
            .firstElement()
            .subscribe { uri ->
              mainThreadOnly {
                Log.v(this@UICreateOTP) {
                  text("OTP URI detected")
                  param("uri", uri)

                  scanContainer.removeAllViews()
                  setupSecret(uri)

                  post {
                    beginDelayedTransition()

                    scanContainer.visibility = View.GONE
                    valueLayout.visibility = View.VISIBLE
                  }
                }
              }
            })
      }
  }

  private fun setupSecret(uri: Uri) {
    this.uri = uri

    val label = uri.lastPathSegment!!
    val secret = uri.getQueryParameter("secret")!!
    val digits = Integer.parseInt(uri.getQueryParameter("digits") ?: "6")
    val algorithm = uri.getQueryParameter("algorithm")
    val period = Integer.parseInt(uri.getQueryParameter("period") ?: "30")

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
          init(secret.decodeBase32(), digits = digits, stepMs = period * 1000L)
          now()
        }
      )
    )
  }

  override fun onSuccess(button: KeyguardButton) {
    super.onSuccess(button)

    parentView.disposeOnRecycle(
      parentView.save(OTPSecret.create(uri!!))
        .subscribe({
          Log.v(this@UICreateOTP) {
            text("OTP data saved")
          }

          mainThreadOnly {
            parentView.onDone?.invoke()
          }
        }, {
          Log.e(this@UICreateOTP) {
            text("OTP data failed to save")
            error(it)
          }

          mainThreadOnly {

          }
        })
    )
  }
}
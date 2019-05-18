package me.stojan.pasbox.ui

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.AttributeSet
import android.view.TextureView
import android.widget.LinearLayout
import me.stojan.pasbox.R
import me.stojan.pasbox.barcode.BarcodeScanner
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly

class UICreate2FA @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
  private val activity: UIActivity get() = context as UIActivity

  private lateinit var scan: TextureView
  private lateinit var barcodeScanner: BarcodeScanner

  override fun onFinishInflate() {
    super.onFinishInflate()

    context.getSystemService(CameraManager::class.java)
      .let { cameraManager ->
        scan = findViewById(R.id.scan)
        barcodeScanner = BarcodeScanner(cameraManager.cameraIdList.first(), scan)

        activity.disposeOnDestroy(barcodeScanner.results.subscribe {
          mainThreadOnly {
            Log.v(this@UICreate2FA) {
              text("Barcode detected")
              param("barcode", it.rawValue)
            }
          }
        })
      }
  }

}
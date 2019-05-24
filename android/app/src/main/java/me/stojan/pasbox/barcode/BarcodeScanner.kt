package me.stojan.pasbox.barcode

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.ConditionVariable
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeScanner(val cameraId: String, val textureView: TextureView) :
  TextureView.SurfaceTextureListener,
  ImageReader.OnImageAvailableListener {

  init {
    textureView.surfaceTextureListener = this
  }

  private val context get() = textureView.context

  private val resultsSubject = PublishSubject.create<FirebaseVisionBarcode>()
  val results: Observable<FirebaseVisionBarcode> = resultsSubject

  private lateinit var handler: Handler
  private val handlerSet = ConditionVariable(false)

  private val inProgress = AtomicBoolean(false)

  private val detector = FirebaseVision.getInstance()
    .visionBarcodeDetector

  private var camera: CameraDevice? = null
  private var cameraSession: CameraCaptureSession? = null
  private var imageReader: ImageReader? = null
  private var surfaceTexture: SurfaceTexture? = null

  private var rotation: Int = -1

  private val thread = Thread({
    Looper.prepare()
    handler = Handler(Looper.myLooper())
    handlerSet.open()
    Looper.loop()
  }, "barcode-scanner-$cameraId")

  private fun start(): Handler {
    Log.v(this) {
      text("Starting")
    }

    thread.start()
    handlerSet.block()

    Log.v(this) {
      text("Started")
    }

    return handler
  }

  private fun quit() {
    handler.post {
      Log.v(this) {
        text("Quitting")
      }

      if (Thread.currentThread() != thread) {
        throw RuntimeException("Unknown thread ${Thread.currentThread()}")
      }

      cameraSession?.apply {
        abortCaptures()
        close()
      }
      cameraSession = null

      camera?.apply { close() }
      camera = null

      surfaceTexture?.apply { release() }
      surfaceTexture = null

      imageReader?.apply { close() }
      imageReader = null

      Looper.myLooper()!!.quitSafely()

      inProgress.set(false)
      handlerSet.close()

      Log.v(this) {
        text("Quit")
      }
    }
  }

  fun processImage(image: Image, rotation: Int) {
    if (!inProgress.getAndSet(true)) {
      if (Thread.currentThread() != thread) {
        throw RuntimeException("Unknown thread ${Thread.currentThread()}")
      }

      detector.detectInImage(
        FirebaseVisionImage.fromMediaImage(image, rotation)
      )
        .addOnSuccessListener {
          it.forEach {
            Log.v(this@BarcodeScanner) {
              text("Detected barcode")
              param("barcode.rawValue", it.rawValue)
            }
          }

          it.forEach {
            resultsSubject.onNext(it)
          }

          inProgress.set(false)
        }
        .addOnFailureListener {
          inProgress.set(false)
        }
    }
  }

  @SuppressLint("MissingPermission")
  override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
    Log.v(this) {
      text("Surface available")
      param("width", width)
      param("height", height)
    }

    mainThreadOnly {
      this.surfaceTexture = surfaceTexture

      start()

      context.getSystemService(CameraManager::class.java)
        .let { cameraManager ->
          val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

          val streamMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
          val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
          val outputSizes = streamMap.getOutputSizes(SurfaceTexture::class.java)

          rotation = when (sensorOrientation) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw RuntimeException("Unknown sensor orientation $sensorOrientation")
          }

          Log.v(this) {
            text("Camera info")
            param("id", cameraId)
            param("outputSizes", outputSizes)
            param("sensorOrientation", sensorOrientation)
          }

          outputSizes.also {
            it.sortBy { it.height }
          }
            .firstOrNull { it.width >= width && it.height >= height }
            .let { minSize ->
              Log.v(this) {
                text("Output")
                param("size", minSize)
              }

              surfaceTexture.setDefaultBufferSize(minSize!!.width, minSize.height)

              textureView.setTransform(Matrix()
                .also {
                  it.setScale(1f, minSize.width.toFloat() / minSize.height.toFloat())
                })

              imageReader?.apply { close() }
              imageReader = ImageReader.newInstance(minSize.width, minSize.height, ImageFormat.YUV_420_888, 2)
            }

          val surface = Surface(surfaceTexture)

          cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
              Log.v(this@BarcodeScanner) {
                text("Opened camera")
              }

              this@BarcodeScanner.camera = camera

              camera.createCaptureSession(
                arrayListOf(surface, imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {

                  override fun onConfigured(session: CameraCaptureSession) {
                    this@BarcodeScanner.cameraSession = session

                    imageReader!!.setOnImageAvailableListener(this@BarcodeScanner, handler)

                    session.setRepeatingRequest(camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                      .apply {
                        addTarget(surface)
                        addTarget(imageReader!!.surface)
                      }
                      .build(),
                      null,
                      null)
                  }

                  override fun onConfigureFailed(session: CameraCaptureSession) {
                    this@BarcodeScanner.cameraSession = null
                  }

                },
                handler
              )
            }

            override fun onDisconnected(camera: CameraDevice) {
              Log.v(this@BarcodeScanner) {
                text("Disconnected from camera")
              }
              this@BarcodeScanner.camera = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
              Log.e(this@BarcodeScanner) {
                text("Error on camera")
                param("error", error)
              }
              this@BarcodeScanner.camera = null
            }

          }, handler)
        }
    }
  }

  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
    Log.v(this) {
      text("Surface size changed")
      param("width", width)
      param("height", height)
    }
  }

  override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
  }

  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
    Log.v(this) {
      text("Surface destroyed")
    }

    quit()
    return false
  }

  override fun onImageAvailable(reader: ImageReader) {
    reader.acquireLatestImage()?.use {
      processImage(it, rotation)
    }
  }
}
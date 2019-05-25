package me.stojan.pasbox.barcode

import android.annotation.SuppressLint
import android.app.Activity
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
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import me.stojan.pasbox.App
import me.stojan.pasbox.LifecycleCallback
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeScanner(var cameraId: String, val textureView: TextureView) :
  TextureView.SurfaceTextureListener,
  ImageReader.OnImageAvailableListener,
  OnSuccessListener<MutableList<FirebaseVisionBarcode>>,
  OnFailureListener,
  LifecycleCallback {

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
    .getVisionBarcodeDetector(
      FirebaseVisionBarcodeDetectorOptions.Builder()
        .setBarcodeFormats(
          FirebaseVisionBarcode.FORMAT_QR_CODE,
          FirebaseVisionBarcode.FORMAT_AZTEC,
          FirebaseVisionBarcode.FORMAT_DATA_MATRIX
        )
        .build()
    )

  private var camera: CameraDevice? = null
  private var cameraSession: CameraCaptureSession? = null
  private var imageReader: ImageReader? = null
  private var surfaceTexture: SurfaceTexture? = null
  private var surface: Surface? = null
  private var surfaceWidth: Int = 0
  private var surfaceHeight: Int = 0

  private var rotation: Int = -1

  private val thread = Thread({
    Looper.prepare()
    handler = Handler(Looper.myLooper())
    handlerSet.open()
    Looper.loop()
  }, "barcode-scanner-$cameraId")

  private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
      Log.v(this@BarcodeScanner) {
        text("Opened camera")
      }

      this@BarcodeScanner.camera = camera

      camera.createCaptureSession(
        arrayListOf(surface, imageReader!!.surface),
        cameraCaptureSessionStateCallback,
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

  }

  private val cameraCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
    override fun onConfigured(session: CameraCaptureSession) {
      cameraSession = session

      Log.v(this@BarcodeScanner) {
        text("Capture session is configured")
        param("session", session)
      }

      imageReader!!.setOnImageAvailableListener(this@BarcodeScanner, handler)

      session.setRepeatingRequest(camera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        .apply {
          addTarget(surface!!)
          addTarget(imageReader!!.surface)
        }
        .build(),
        null,
        null)
    }

    override fun onConfigureFailed(session: CameraCaptureSession) {
      cameraSession = null

      Log.e(this@BarcodeScanner) {
        text("Capture session configuration failed")
      }
    }

  }

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

      surface?.apply { release() }
      surface = null

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

  @SuppressLint("MissingPermission")
  override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
    Log.v(this) {
      text("Surface available")
      param("width", width)
      param("height", height)
    }

    mainThreadOnly {
      this.surfaceWidth = width
      this.surfaceHeight = height
      this.surfaceTexture = surfaceTexture
      this.surface = Surface(surfaceTexture)

      App.INSTANCE.addLifecycle(textureView.context as Activity, this)

      start()
      openCamera()
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

    App.INSTANCE.removeLifecycle(textureView.context as Activity, this)

    quit()
    return false
  }

  fun switchCamera(cameraId: String) {
    this.cameraId = cameraId

    if (null != cameraSession) {
      openCamera()
    }
  }

  @SuppressLint("MissingPermission")
  private fun openCamera() {
    cameraSession?.apply {
      abortCaptures()
      close()
    }
    cameraSession = null

    camera?.apply { close() }
    camera = null

    imageReader?.apply { close() }
    imageReader = null

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

        Log.v(this@BarcodeScanner) {
          text("Camera info")
          param("id", cameraId)
          param("outputSizes", outputSizes)
          param("sensorOrientation", sensorOrientation)
        }

        outputSizes.apply {
          sortBy { Math.min(it.width, it.height) }
        }
          .firstOrNull { it.width >= surfaceWidth && it.height >= surfaceHeight }!!
          .let { minSize ->
            Log.v(this@BarcodeScanner) {
              text("Camera output size")
              param("size", minSize)
            }

            surfaceTexture!!.setDefaultBufferSize(minSize.width, minSize.height)

            textureView.setTransform(Matrix()
              .also {
                it.setScale(1f, 1f)
                it.postScale(1f, minSize.width.toFloat() / minSize.height.toFloat())
              })

            imageReader = ImageReader.newInstance(minSize.width, minSize.height, ImageFormat.YUV_420_888, 2)
          }

        cameraManager.openCamera(cameraId, cameraDeviceStateCallback, handler)
      }
  }

  override fun onImageAvailable(reader: ImageReader) {
    reader.acquireLatestImage()?.use {
      processImage(it, rotation)
    }
  }

  private fun processImage(image: Image, rotation: Int) {
    if (!inProgress.getAndSet(true)) {
      if (Thread.currentThread() != thread) {
        throw RuntimeException("Unknown thread ${Thread.currentThread()}")
      }

      detector.detectInImage(
        FirebaseVisionImage.fromMediaImage(image, rotation)
      )
        .addOnSuccessListener(this)
        .addOnFailureListener(this)
    }
  }

  override fun onSuccess(result: MutableList<FirebaseVisionBarcode>?) {
    try {
      Log.v(this) {
        text("Detected")
        param("barcodes", result)
      }

      mainThreadOnly {
        result?.forEach { resultsSubject.onNext(it) }
      }
    } finally {
      inProgress.set(false)
    }
  }

  override fun onFailure(exception: Exception) {
    try {
      Log.e(this) {
        text("Failed to detect barcodes")
        error(exception)
      }
    } finally {
      inProgress.set(false)
    }
  }

  override fun onResume() {
    if (null != surfaceTexture) {
      openCamera()
    }
  }

  override fun onPause() {
    cameraSession?.apply {
      abortCaptures()
      close()
    }

    cameraSession = null

    camera?.apply {
      close()
    }
    camera = null

    imageReader?.apply {
      close()
    }
    imageReader = null
  }
}
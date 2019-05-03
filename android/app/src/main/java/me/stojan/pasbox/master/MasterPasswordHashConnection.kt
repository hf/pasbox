package me.stojan.pasbox.master

import android.app.ActivityManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.google.protobuf.asByteString
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.storage.KDFArgon2
import java.security.SecureRandom
import java.util.*

open class MasterPasswordHashConnection(protected val password: ByteArray, hashSize: Int) :
  ServiceConnection {
  private val disposables = CompositeDisposable()

  protected val salt = ByteArray((2 * hashSize) / 8)
  protected val hash = ByteArray(hashSize / 8)

  private val params = IntArray(5)

  private var _service: IMasterPasswordHashService? = null
  protected val service: IMasterPasswordHashService get() = _service!!

  final override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    mainThreadOnly {
      _service = IMasterPasswordHashService.Stub.asInterface(service)

      Log.v(this) {
        text("Service connected")
        param("name", name)
        param("binder", service)
      }

      onConnected()
    }
  }

  final override fun onServiceDisconnected(name: ComponentName?) {
    disposables.dispose()
    Arrays.fill(password, 0xCC.toByte())

    mainThreadOnly {
      _service = null
      disposables.clear()

      Log.v(this) {
        text("Service disconnected")
        param("name", name)
      }

      onDisconnected()
    }
  }

  protected fun memory(memoryInfo: ActivityManager.MemoryInfo) {
    // this number will be negotiated down by the implementer as well, so this value is the absolute recommended maximum
    params[1] = Math.max(
      // at a minimum take up 33% of the total device memory, the service will negotiate down to 128MB
      (memoryInfo.totalMem / 3L) / 1024L,
      // try to take up all available memory except for the threshold and take up 20% of the used memory
      (memoryInfo.availMem - memoryInfo.threshold + (memoryInfo.totalMem - memoryInfo.availMem) / 5L) / 1024L
    ).toInt()

    Runtime.getRuntime().availableProcessors()
      .let { availableProcessors ->
        // most mobile devices report 8 threads, but desktops rarely have more than 2, 4
        // so at least 1, at most 4
        params[2] = Math.max(1, Math.min(4, availableProcessors / 2))
      }
  }

  protected fun measure(duration: Int, memoryPercent: Int): Completable = Completable.fromRunnable {
    workerThreadOnly {
      checkCleanPassword()

      try {
        SecureRandom().nextBytes(salt)

        for (i in 0..5) {
          params[1] = ((params[1].toLong() * memoryPercent.toLong()) / 100L).toInt()

          val result = service.measure(duration, hash.size, salt, password, params)

          if (IMasterPasswordHashService.OK == result) {
            return@fromRunnable
          } else if (IMasterPasswordHashService.ERROR_FAILURE == result) {
            throw RuntimeException("Unable to measure hashing parameters")
          }
        }

        throw RuntimeException("Unable to measure hashing parameters after 5 attempts")
      } catch (e: Throwable) {
        Arrays.fill(password, 0xCC.toByte())
        throw e
      }
    }
  }.subscribeOn(Schedulers.io())

  protected fun hash(): Completable = Completable.fromRunnable {
    workerThreadOnly {
      checkCleanPassword()

      try {
        val result = service.hash(hash, salt, password, params)

        if (IMasterPasswordHashService.OK == result) {
          return@fromRunnable
        }
      } catch (e: Throwable) {
        Arrays.fill(hash, 0xCC.toByte())
        Arrays.fill(password, 0xCC.toByte())

        throw e
      }
    }
  }.subscribeOn(Schedulers.io())

  protected fun params(): KDFArgon2 = KDFArgon2.newBuilder()
    .setTCost(params[0])
    .setMCost(params[1])
    .setParallelism(params[2])
    .setSalt(salt.asByteString())
    .setType(params[3])
    .setVersion(params[4])
    .build()

  open fun onConnected() {

  }

  open fun onDisconnected() {

  }

  protected fun checkCleanPassword() {
    if (password.count { 0xCC.toByte() == it } >= password.size / 2) {
      throw RuntimeException("Password has been cleaned")
    }
  }

  protected fun disposeOnDisconnect(disposable: Disposable): Disposable = disposable.also { disposables.add(it) }
}
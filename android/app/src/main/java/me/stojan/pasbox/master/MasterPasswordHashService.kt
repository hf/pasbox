package me.stojan.pasbox.master

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import me.stojan.pasbox.argon2.Argon2
import me.stojan.pasbox.argon2.Argon2Exception
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.duration
import me.stojan.pasbox.dev.use
import me.stojan.pasbox.dev.workerThreadOnly
import java.util.*

class MasterPasswordHashService : Service() {

  companion object {
    const val MIN_RAM_KB = 128 * 1024
    const val RAM_KB_STEP_DOWN = 8 * 1024
  }

  inner class Binder : IMasterPasswordHashService.Stub() {

    override fun measure(duration: Int, hashSize: Int, salt: ByteArray, password: ByteArray, params: IntArray): Int =
      workerThreadOnly {
        this@MasterPasswordHashService.getSystemService(PowerManager::class.java)
          .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pasbox:hashing-measure")
          .use {
            val hash = ByteArray(hashSize)

            try {
              Log.v(this) {
                text("IMasterPassswordHashService#measure()")
              }

              this@MasterPasswordHashService.measure(duration, salt, password, hash, params)

              IMasterPasswordHashService.OK
            } catch (e: Argon2Exception) {
              Log.e(this@MasterPasswordHashService) {
                text("(retryable) Failure to measure hashing params")
                error(e)
              }

              IMasterPasswordHashService.ERROR_RETRYABLE
            } catch (e: Throwable) {
              Log.e(this@MasterPasswordHashService) {
                text("Failure to measure hashing params")
                error(e)
              }

              IMasterPasswordHashService.ERROR_FAILURE
            } finally {
              Arrays.fill(hash, 0)
            }
          }
      }

    override fun hash(hash: ByteArray, salt: ByteArray, password: ByteArray, params: IntArray): Int =
      workerThreadOnly {
        this@MasterPasswordHashService.getSystemService(PowerManager::class.java)
          .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pasbox:hashing-hash")
          .use {
            try {
              Log.v(this) {
                text("IMasterPasswordHashService#hash()")
              }

              this@MasterPasswordHashService.hash(
                salt,
                password,
                hash,
                params
              )

              IMasterPasswordHashService.OK
            } catch (e: Argon2Exception) {
              Log.e(this@MasterPasswordHashService) {
                text("(retryable) Failure to hash password")
                error(e)
              }

              IMasterPasswordHashService.ERROR_RETRYABLE
            } catch (e: Throwable) {
              Log.e(this@MasterPasswordHashService) {
                text("Failure to hash password")
                error(e)
              }

              IMasterPasswordHashService.ERROR_FAILURE
            }
          }
      }
  }

  override fun onBind(intent: Intent): IBinder {
    Log.v(this) {
      text("onBind")
      param("intent", intent)
    }

    return Binder()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    Log.v(this) {
      text("onUnbind")
      param("intent", intent)
    }

    return super.onUnbind(intent)
  }

  override fun onCreate() {
    super.onCreate()

    Log.v(this) {
      text("onCreate")
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    Log.v(this) {
      text("onDestroy")
    }
  }

  private fun measure(duration: Int, salt: ByteArray, password: ByteArray, hash: ByteArray, params: IntArray) {
    workerThreadOnly {
      Argon2.getInstance(Argon2.BEST)
        .run {
          params[3] = type()
          params[4] = version()

          params[1] = Math.max(params[1], MIN_RAM_KB)

          var negotiationPasses = 0
          while (true) {
            try {
              val timed = duration {
                hash(1, params[1], 1, password, salt, hash)
              }

              params[0] = Math.ceil(((duration.toDouble() / timed.toDouble()) / 4.0)).toInt()
              break
            } catch (e: Argon2Exception) {
              if ((-15 == e.code() || -22 == e.code()) && params[1] >= MIN_RAM_KB + RAM_KB_STEP_DOWN) {
                params[1] -= RAM_KB_STEP_DOWN
                negotiationPasses += 1
              } else {
                throw RuntimeException("Unable to negotiate minimum RAM requirements", e)
              }
            }
          }

          Log.v(this@MasterPasswordHashService) {
            text("Memory measurements")
            param("memory", params[1])
            param("negotiationPasses", negotiationPasses)
          }

          for (i in (3..9 step 2)) { // 3 6 9
            val timed = duration {
              hash(i, params[1], params[2], password, salt, hash)
            }

            if (timed > duration) {
              params[0] = i
              break
            } else {
              val durationPerUnit = timed.toDouble() / i.toDouble()
              val durationUnits = duration.toDouble() / durationPerUnit

              params[0] += Math.ceil((durationUnits / 4)).toInt()
            }
          }

          Log.v(this@MasterPasswordHashService) {
            text("Time measurements")
            param("cost", params[0])
          }
        }
    }
  }

  private fun hash(salt: ByteArray, password: ByteArray, hash: ByteArray, params: IntArray) =
    workerThreadOnly {
      params[1] = Math.max(params[1], MIN_RAM_KB)
      params[0] = Math.max(params[0], Math.round(Math.log(params[1].toDouble()) / Math.log(2.0)).toInt())

      Argon2.getInstance(Argon2.BEST)
        .run {
          Log.v(this@MasterPasswordHashService) {
            text("Hashing")
            param("tCost", params[0])
            param("mCost", params[1])
            param("parallelism", params[2])
            param("salt.size", salt.size)
            param("password.size", password.size)
            param("hash.size", hash.size)
          }

          duration {
            hash(
              params[0],
              params[1],
              params[2],
              password,
              salt,
              hash
            )
          }.let { duration ->
            Log.v(this@MasterPasswordHashService) {
              text("Hashing done")
              param("duration", duration)
            }
          }
        }
    }

}

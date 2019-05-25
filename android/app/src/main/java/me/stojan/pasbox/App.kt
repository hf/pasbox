/*
 * Copyright (C) 2019
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package me.stojan.pasbox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.safetynet.SafetyNetAttestationJobScheduled
import me.stojan.pasbox.storage.KV
import java.lang.ref.WeakReference
import java.util.*

interface LifecycleCallback {
  fun onResume()
  fun onPause()
}

class App : Application(), HasComponents {
  companion object {
    internal lateinit var INSTANCE: App

    val Current: App get() = INSTANCE
    val Components: AppComponents get() = INSTANCE.components
  }

  private val disposables = CompositeDisposable()
  private lateinit var _components: AppComponents

  private val lifecycleMap = WeakHashMap<Activity, ArrayList<WeakReference<LifecycleCallback>>>()
  private val callbacks = object : ActivityLifecycleCallbacks {
    override fun onActivityPaused(activity: Activity) {
      Log.v(this@App) {
        text("Paused")
        param("activity", activity)
      }

      mainThreadOnly {
        lifecycleMap[activity]?.apply {
          removeAll { null == it.get() }
          forEach {
            it.get()?.onPause()
          }
        }
      }
    }

    override fun onActivityResumed(activity: Activity?) {
      Log.v(this@App) {
        text("Resumed")
        param("activity", activity)
      }

      mainThreadOnly {
        lifecycleMap[activity]?.apply {
          removeAll { null == it.get() }
          forEach {
            it.get()?.onResume()
          }
        }
      }
    }

    override fun onActivityStarted(activity: Activity?) {
    }

    override fun onActivityDestroyed(activity: Activity?) {
      mainThreadOnly {
        lifecycleMap[activity] = null
      }
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity?) {
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
    }

  }

  override val components: AppComponents get() = _components

  override fun onCreate() {
    super.onCreate()
    Log.v(this@App) { text("Created") }

    INSTANCE = this
    _components = RuntimeAppComponents(this)

    startup()
  }

  private fun startup() {
    registerActivityLifecycleCallbacks(callbacks)

    disposables.add(
      components.Storage.kvstore().watch(KV.DEVICE_ID, nulls = false, get = false).observeOn(
        AndroidSchedulers.mainThread()
      ).subscribe {
        Log.v(this@App) { text("Device ID was updated, scheduling SafetyNet attestation") }
        Jobs.schedule(this@App, SafetyNetAttestationJobScheduled.info)
      })
  }

  fun addLifecycle(forActivity: Activity, callback: LifecycleCallback) {
    mainThreadOnly {
      lifecycleMap[forActivity] = (lifecycleMap[forActivity] ?: ArrayList(2))
        .apply {
          removeAll { null == it.get() || callback == it.get() }
          add(WeakReference(callback))
        }
    }
  }

  fun removeLifecycle(forActivity: Activity, callback: LifecycleCallback) {
    mainThreadOnly {
      lifecycleMap[forActivity]?.removeAll { null == it.get() || callback == it.get() }
    }
  }
}
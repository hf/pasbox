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

import android.app.Application
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.safetynet.SafetyNetAttestationJobASAP
import me.stojan.pasbox.safetynet.SafetyNetAttestationJobScheduled
import me.stojan.pasbox.storage.KV

class App : Application(), HasComponents {
  companion object {
    internal lateinit var INSTANCE: App

    val Current: App get() = INSTANCE
    val Components: AppComponents get() = INSTANCE.components
  }

  private val disposables = CompositeDisposable()
  private lateinit var _components: AppComponents

  override val components: AppComponents get() = _components

  override fun onCreate() {
    super.onCreate()
    INSTANCE = this
    _components = RuntimeAppComponents(this)

    Log.v(this@App) { text("Created") }

    warmup()
    startup()
  }

  private fun warmup() {
    disposables.add(components.Storage.kvstore().warmup())
  }

  private fun startup() {
    Jobs.schedule(this@App, SafetyNetAttestationJobScheduled.info)

    disposables.add(components.Storage.kvstore().warmup())
    disposables.add(
      components.Storage.kvstore().watch(KV.DEVICE_ID, nulls = false, get = false).observeOn(
        AndroidSchedulers.mainThread()
      ).subscribe {
        Log.v(this@App) { text("Device ID was updated, scheduling SafetyNet attestation") }
        Jobs.schedule(this@App, SafetyNetAttestationJobASAP.info)
      })
  }
}
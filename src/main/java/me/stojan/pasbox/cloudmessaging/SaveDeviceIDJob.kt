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

package me.stojan.pasbox.cloudmessaging

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.jobs.Job
import me.stojan.pasbox.jobs.JobService
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.storage.KV

object SaveDeviceIDJob : Job {
  const val TOKEN = "token"

  override val id: Int = Jobs.SAVE_DEVICE_ID

  override fun run(context: Context, params: JobParameters): Completable =
    App.Components.Storage.kvstore().put(KV.DEVICE_ID,
      App.Components.Storage.kvstore().get(KV.DEVICE_ID)
        .map { bytes ->
          workerThreadOnly {
            DeviceID.parseFrom(bytes)
          }
        }
        .switchIfEmpty(Single.just(DeviceID.newBuilder().build()))
        .map { deviceId ->
          workerThreadOnly {
            DeviceID.newBuilder(deviceId)
              .setCurrentId(params.extras.getString(TOKEN))
              .addPreviousIds(deviceId.currentId)
              .build()
              .toByteArray()
          }
        }).subscribeOn(Schedulers.newThread())


  fun now(token: String): JobInfo = JobInfo.Builder(id, JobService.ComponentName)
    .setOverrideDeadline(1000)
    .setExtras(PersistableBundle(1).apply {
      putString(TOKEN, token)
    })
    .setBackoffCriteria(500, JobInfo.BACKOFF_POLICY_LINEAR)
    .apply {
      if (Build.VERSION.SDK_INT >= 28) {
        setImportantWhileForeground(true)
      }
    }
    .build()

}
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

package me.stojan.pasbox.jobs

import android.app.job.JobParameters
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly

class JobService : android.app.job.JobService() {

  companion object {
    val ComponentName = android.content.ComponentName("me.stojan.pasbox", JobService::class.java.name)
  }

  private val runningJobs = HashMap<JobParameters, Disposable>()

  override fun onStopJob(params: JobParameters): Boolean =
    runningJobs[params].let { runningJob ->
      if (null == runningJob) {
        false
      } else {
        runningJobs.remove(params)

        if (runningJob.isDisposed) {
          false
        } else {
          runningJob.dispose()
          true
        }
      }
    }

  override fun onStartJob(params: JobParameters): Boolean =
    JobRegistry.findForId(params.jobId).let { job ->
      runningJobs[params] = job.run(this@JobService, params)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnDispose {
          jobFinished(params, false)
          Log.v(this@JobService) { text("Job was disposed"); param("job", job) }
        }
        .subscribe({
          jobFinished(params, false)
          Log.v(this@JobService) { text("Job finished successfully"); param("job", job); }
          mainThreadOnly {
            runningJobs.remove(params)
          }
        }, { error ->
          jobFinished(params, false)
          Log.e(this@JobService) { text("Job failed with error"); param("job", job); error(error) }
          mainThreadOnly {
            runningJobs.remove(params)
          }
        })

      Log.v(this@JobService) { text("Job running"); param("job", job); }

      true
    }

}
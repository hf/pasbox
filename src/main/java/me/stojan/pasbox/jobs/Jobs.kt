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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import me.stojan.pasbox.BuildConfig
import me.stojan.pasbox.dev.Log

object Jobs {
  const val SAVE_DEVICE_ID = 1024
  const val SAFETY_NET_ATTESTATION_ID = 2048
  const val SAFETY_NET_ATTESTATION_PERIODIC_ID = 2049

  fun schedule(context: Context, job: JobInfo) {
    context.getSystemService(JobScheduler::class.java).also { scheduler ->
      if (BuildConfig.DEBUG) {
        scheduler.allPendingJobs.find { it.id == job.id }
          ?.let { existingJob ->
            Log.w(this@Jobs) { text("Job already info"); param("job", job) }
          }
      }

      scheduler.schedule(job).let { result ->
        if (JobScheduler.RESULT_FAILURE == result) {
          throw RuntimeException("Unable to schedule Job with id=${job.id}")
        }
      }
    }
  }

}
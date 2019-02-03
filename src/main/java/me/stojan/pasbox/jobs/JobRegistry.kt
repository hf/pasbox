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
import android.content.Context
import android.util.SparseArray
import io.reactivex.Completable
import me.stojan.pasbox.cloudmessaging.SaveDeviceIDJob
import me.stojan.pasbox.safetynet.SafetyNetAttestationJobASAP
import me.stojan.pasbox.safetynet.SafetyNetAttestationJobScheduled

data class DynamicJob(override val id: Int) : Job {
  override fun run(context: Context, params: JobParameters): Completable = synchronized(Jobs.dynamic) {
    Jobs.dynamic[id - Jobs.DYNAMIC_JOBS_FROM]!!
  }
}

object JobRegistry {
  private val map = SparseArray<Job>()

  init {
    // register all jobs here
    register(SaveDeviceIDJob)
    register(SafetyNetAttestationJobASAP)
    register(SafetyNetAttestationJobScheduled)
  }

  private fun register(job: Job) {
    if (null != map.get(job.id)) {
      throw Error("Job with id=${job.id} is already registered")
    }

    map.put(job.id, job)
  }

  fun findForId(id: Int): Job {
    if (id >= Jobs.DYNAMIC_JOBS_FROM) {
      return DynamicJob(id)
    }

    val job = map.get(id)

    if (null == job) {
      throw Error("Job with id=$id not registered")
    }

    return job
  }
}
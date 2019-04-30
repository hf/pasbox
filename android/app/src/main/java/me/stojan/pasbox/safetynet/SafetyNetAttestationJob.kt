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

package me.stojan.pasbox.safetynet

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.content.Context
import android.os.Build
import android.util.Base64
import com.google.android.gms.safetynet.SafetyNet
import com.google.protobuf.asByteArray
import com.google.protobuf.asByteString
import com.squareup.moshi.JsonAdapter
import io.reactivex.Single
import me.stojan.pasbox.APIKeys
import me.stojan.pasbox.App
import me.stojan.pasbox.BuildConfig
import me.stojan.pasbox.cloudmessaging.DeviceID
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.toMaybe
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.jobs.Job
import me.stojan.pasbox.jobs.JobService
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.signature.DeviceSignature
import me.stojan.pasbox.storage.KV

abstract class SafetyNetAttestationJob : Job {

  override fun run(context: Context, params: JobParameters) =
    App.Components.Storage.kvstore().get(KV.DEVICE_ID)
      .map { bytes ->
        workerThreadOnly { DeviceID.parseFrom(bytes) }
      }
      .map { deviceId ->
        workerThreadOnly { DeviceSignature().apply { sign(deviceId.currentIdBytes.asByteArray()) }.signature() }
      }
      .flatMap { attestation ->
        workerThreadOnly { SafetyNet.getClient(context).attest(attestation, APIKeys.SAFETY_NET).toMaybe() }
      }
      .map { result ->
        workerThreadOnly {
          Log.v(this@SafetyNetAttestationJob) { text("SafetyNet attestation is ready "); value(result.jwsResult) }

          parseAttestation(result.jwsResult, App.Components.JSON.moshi().adapter(Attestation::class.java))
        }
      }
      .flatMapCompletable { App.Components.Storage.kvstore().put(KV.SAFETY_NET_ATTESTATION, Single.just(it)) }


  private fun parseAttestation(jws: String, adapter: JsonAdapter<Attestation>) =
    jws.let {
      val attestation = jws.split('.').map { part -> Base64.decode(part, Base64.URL_SAFE) }
        .let { parts ->
          adapter.fromJson(String(parts[1]))!!
        }

      Log.v(this@SafetyNetAttestationJob) { text("Attestation is parsed"); param("attestation", attestation) }

      SafetyNetAttestation.newBuilder()
        .setNonce(attestation.nonce)
        .setApkDigestSha256(Base64.decode(attestation.apkDigestSha256, Base64.URL_SAFE).asByteString())
        .setApkPackageName(attestation.apkPackageName)
        .setApkCertificateDigestSha256(attestation.apkCertificateDigestSha256?.let { it[0]?.asByteString() })
        .setCtsProfileMatch(attestation.ctsProfileMatch)
        .setBasicIntegrity(attestation.basicIntegrity)
        .setAdvice(attestation.advice)
        .build()
        .toByteArray()
    }
}

object SafetyNetAttestationJobASAP : SafetyNetAttestationJob() {
  override val id: Int = Jobs.SAFETY_NET_ATTESTATION_ID

  val info: JobInfo
    get() = JobInfo.Builder(id, JobService.ComponentName)
      .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
      .apply {
        if (Build.VERSION.SDK_INT >= 28) {
          setImportantWhileForeground(true)
        }
      }
      .build()
}

object SafetyNetAttestationJobScheduled : SafetyNetAttestationJob() {
  override val id: Int = Jobs.SAFETY_NET_ATTESTATION_PERIODIC_ID

  val info: JobInfo
    get() = JobInfo.Builder(id, JobService.ComponentName)
      .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
      .setPersisted(true)
      .setPeriodic(
        60L * 60L * 1000L * (if (BuildConfig.DEBUG) {
          1L
        } else {
          2L * 24L
        })
      )
      .build()
}
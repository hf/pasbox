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

package me.stojan.pasbox.ui

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.protobuf.toByteString
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.AppActivity
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.dev.toMaybe
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.safetynet.SafetyNetAttestation
import me.stojan.pasbox.storage.KV

class UIActivity(val app: App = App.Current) : AppActivity() {

  private lateinit var layoutManager: LinearLayoutManager
  private lateinit var adapter: UIRecyclerAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    layoutManager = LinearLayoutManager(this)
    adapter = UIRecyclerAdapter(this)

    recycler.layoutManager = layoutManager
    recycler.adapter = adapter
  }

  override fun onResume() {
    super.onResume()

    observeGooglePlayServices()
    observeKeyguard()
    observeSafetyNet()

    activateFAB()
  }

  private fun observeGooglePlayServices() {
    GoogleApiAvailability.getInstance().let { googleApi ->
      googleApi.isGooglePlayServicesAvailable(this)
        .let { result ->
          adapter.dismissTop(R.layout.card_old_google_play_services)
          adapter.dismissTop(R.layout.card_updating_google_play_services)
          adapter.dismissTop(R.layout.card_missing_google_play_services)

          when (result) {
            ConnectionResult.SERVICE_DISABLED, ConnectionResult.SERVICE_INVALID, ConnectionResult.SERVICE_MISSING -> {
              adapter.presentTop(R.layout.card_missing_google_play_services) {
                val button: Button = it.findViewById(R.id.resolve_error)

                if (googleApi.isUserResolvableError(result)) {
                  button.visibility = View.VISIBLE
                  button.setOnClickListener {
                    it.isEnabled = false

                    disposeOnPause(GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this@UIActivity)
                      .toMaybe().subscribe { observeGooglePlayServices() })
                  }
                } else {
                  button.visibility = View.GONE
                }
              }
            }

            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
              adapter.presentTop(R.layout.card_old_google_play_services) {
                val button: Button = it.findViewById(R.id.resolve_error)

                if (googleApi.isUserResolvableError(result)) {
                  button.visibility = View.VISIBLE
                  button.setOnClickListener {
                    it.isEnabled = false
                    disposeOnPause(GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this@UIActivity)
                      .toMaybe().subscribe { observeGooglePlayServices() })
                  }
                } else {
                  button.visibility = View.GONE
                }
              }
            }

            ConnectionResult.SERVICE_UPDATING -> {
              adapter.presentTop(R.layout.card_updating_google_play_services)
            }

            else -> {
            }
          }
        }
    }
  }

  private fun observeSafetyNet() {
    disposeOnPause(App.Components.Storage.kvstore().watch(KV.SAFETY_NET_ATTESTATION, nulls = false)
      .map { (_, bytes) ->
        workerThreadOnly {
          SafetyNetAttestation.parseFrom(bytes!!.toByteString())
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { attestation ->
        mainThreadOnly {
          if (!attestation.ctsProfileMatch) {
            Log.v(this@UIActivity) { text("Insecure device detected") }
            adapter.presentTopImportant(R.layout.card_insecure_device)
          } else {
            adapter.dismissTop(R.layout.card_insecure_device)
          }
        }
      }
    )
  }

  private fun observeKeyguard() {
    getSystemService(KeyguardManager::class.java).let { keyguard ->
      if (!keyguard.isDeviceSecure) {
        adapter.presentTop(R.layout.card_setup_keyguard) {
          val button: Button = it.findViewById(R.id.resolve_error)
          button.setOnClickListener { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
        }
      } else {
        adapter.dismissTop(R.layout.card_setup_keyguard)

        getSystemService(FingerprintManager::class.java).let { fingerprints ->
          if (fingerprints.isHardwareDetected) {
            if (!fingerprints.hasEnrolledFingerprints()) {
              adapter.presentTop(R.layout.card_setup_fingerprint) {
                val button: Button = it.findViewById(R.id.resolve_error)
                button.setOnClickListener {
                  startActivity(
                    Intent(
                      if (Build.VERSION.SDK_INT >= 28) {
                        Settings.ACTION_FINGERPRINT_ENROLL
                      } else {
                        Settings.ACTION_SECURITY_SETTINGS
                      }
                    )
                  )
                }
              }
            } else {
              adapter.dismissTop(R.layout.card_setup_fingerprint)
            }
          } else {
            adapter.dismissTop(R.layout.card_setup_fingerprint)
          }
        }
      }
    }
  }

  fun activateFAB() {

  }
}
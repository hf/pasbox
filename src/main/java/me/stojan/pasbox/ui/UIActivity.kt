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

import android.os.Bundle
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
    observeSafetyNet()
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
            adapter.presentTop(R.layout.card_insecure_device)
          } else {
            adapter.dismissTop(R.layout.card_insecure_device)
          }
        }
      }
    )
  }
}
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
import com.google.protobuf.asByteString
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.AppActivity
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.dev.toMaybe
import me.stojan.pasbox.dev.workerThreadOnly
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.safetynet.SafetyNetAttestation
import me.stojan.pasbox.storage.KV
import me.stojan.pasbox.storage.SecretStore

class UIActivity(val app: App = App.Current) : AppActivity() {

  private lateinit var layoutManager: LinearLayoutManager
  private lateinit var adapter: UIRecyclerAdapter

  private var coldStart = true
  private var inAction = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    layoutManager = LinearLayoutManager(this)
    recycler.layoutManager = layoutManager
    recycler.setRecycledViewPool(SmartRecycledViewPool())

    adapter = UIRecyclerAdapter(this)
    adapter.mount(recycler)

    coldStart = true
  }

  override fun onResume() {
    super.onResume()

    observeGooglePlayServices()
    observeKeyguard()
    observeSafetyNet()
    observeSecrets()
    observeAccountCreation()

    activateFAB()

    coldStart = false
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
              adapter.presentTop(UIRecyclerAdapter.Top.simple(R.layout.card_missing_google_play_services) {
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
              })
            }

            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
              adapter.presentTop(UIRecyclerAdapter.Top.simple(R.layout.card_old_google_play_services) {
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
              })
            }

            ConnectionResult.SERVICE_UPDATING -> {
              adapter.presentTop(UIRecyclerAdapter.Top.simple(R.layout.card_updating_google_play_services))
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
          SafetyNetAttestation.parseFrom(bytes!!.asByteString())
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { attestation ->
        mainThreadOnly {
          if (!attestation.ctsProfileMatch) {
            adapter.presentTopImportant(UIRecyclerAdapter.Top.simple(R.layout.card_insecure_device))
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
        adapter.presentTop(UIRecyclerAdapter.Top.simple(R.layout.card_setup_keyguard) {
          val button: Button = it.findViewById(R.id.resolve_error)
          button.setOnClickListener { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
        })
      } else {
        adapter.dismissTop(R.layout.card_setup_keyguard)

        if (Build.VERSION.SDK_INT < 28) {
          getSystemService(FingerprintManager::class.java).let { fingerprints ->
            if (fingerprints.isHardwareDetected) {
              if (!fingerprints.hasEnrolledFingerprints()) {
                adapter.presentTop(UIRecyclerAdapter.Top.simple(R.layout.card_setup_fingerprint) {
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
                })
              } else {
                adapter.dismissTop(R.layout.card_setup_fingerprint)
              }
            } else {
              adapter.dismissTop(R.layout.card_setup_fingerprint)
            }
          }
        } else {
          // TODO: Use BiometricPrompt
        }
      }
    }
  }

  private fun activateFAB() {
    if (inAction) {
      return
    }

    inAction = true
    floatingAction.show()
    floatingAction.setOnClickListener {
      floatingAction.hide()

      content.fullScroll(View.FOCUS_UP)
      adapter.presentTopImportant(object : UIRecyclerAdapter.Top {
        override val layout: Int = R.layout.card_create_secret
        override val swipable: Boolean = true

        override fun onBound(view: View) {
          (view as UICreateSecret).onDone = {
            adapter.dismissTop(layout)
            floatingAction.show()
            inAction = false
          }
        }

        override fun onSwiped(view: View, direction: Int) {
          adapter.dismissTop(layout)
          floatingAction.show()
          inAction = false
        }
      })
    }

  }

  private fun observeSecrets() {
    if (coldStart) {
      disposeOnPause(App.Components.Storage.secrets().page(SecretStore.Query(0, 100))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { page ->
          if (page.count > 0) {
            adapter.update(page)
          }
        })
    }

    disposeOnPause(App.Components.Storage.secrets().modifications
      .flatMapSingle {
        Log.v(this@UIActivity) { text("Modification detected") }
        App.Components.Storage.secrets().page(
          SecretStore.Query(
            0,
            100
          )
        )
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { page ->
        if (page.count > 0) {
          adapter.update(page)
        }

      }
    )
  }

  private fun observeAccountCreation() {
    disposeOnPause(
      App.Components.Storage.kvstore().watch(
        intArrayOf(KV.ACCOUNT_RECOVERY, KV.ACCOUNT), nulls = true, get = true
      )
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {
          mainThreadOnly {
            when (it.first) {
              KV.ACCOUNT_RECOVERY -> {
                val accountRecovery = null != it.second

                if (accountRecovery) {
                  adapter.dismissTop(R.layout.card_account_new_setup)
                } else {
                  adapter.presentTopImportant(UIRecyclerAdapter.Top.simple(R.layout.card_account_new_setup))

                  Jobs.schedule(this@UIActivity, App.Components.Storage.account().new()) {
                    if (Build.VERSION.SDK_INT >= 28) {
                      setImportantWhileForeground(true)
                    } else {
                      setMinimumLatency(0)
                    }
                    setOverrideDeadline(0)
                  }
                }
              }

              KV.ACCOUNT -> {
                val account = null != it.second

                if (account) {
                  adapter.dismissTop(R.layout.card_account_new_setup)
                  adapter.dismissTop(R.layout.card_account_recovery_setup)
                } else {
                  adapter.presentTop(SetupMasterPasswordTop())
                }
              }
            }

            null
          }
        }
    )
  }

  inner class SetupMasterPasswordTop : UIRecyclerAdapter.Top {
    private lateinit var view: UISetupMasterPassword

    override val layout: Int = R.layout.card_account_recovery_setup
    override val swipable: Boolean
      get() = view.swipable

    override fun onBound(view: View) {
      (view as UISetupMasterPassword).let {
        this.view = it

        it.onDone = {
          adapter.dismissTop(layout)
        }
      }
    }

    override fun onSwiped(view: View, direction: Int) {
      adapter.dismissTop(layout)
      adapter.presentTop(SetupMasterPasswordTop())
    }

  }
}
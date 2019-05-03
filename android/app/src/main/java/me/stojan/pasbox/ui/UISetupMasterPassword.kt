package me.stojan.pasbox.ui

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.master.MasterPasswordHashConnection
import me.stojan.pasbox.master.MasterPasswordHashService
import me.stojan.pasbox.password.ASCIIPasswordGeneratorParams
import me.stojan.pasbox.password.PasswordGenerator
import me.stojan.pasbox.storage.AccountRecovery

class UISetupMasterPassword @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

  inner class HashingConnection(password: ByteArray) : MasterPasswordHashConnection(password, 512) {
    private var memoryInfo = ActivityManager.MemoryInfo()

    override fun onConnected() {
      super.onConnected()

      context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
      memory(memoryInfo)

      progressText.text = "Measuring device performance, this may take up to a minute..."

      activity.disposeOnPause(
        disposeOnDisconnect(
          measure(securityDuration, memoryPercent)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnDispose {
              mainThreadOnly {
                progressBar.removeCallbacks(progressBarUpdate)

                accountRecovery = null
                adviseLayout.visibility = View.GONE
                setupLayout.visibility = View.VISIBLE
                progressLayout.visibility = View.GONE
              }
            }
            .subscribe({
              Log.v(this@HashingConnection) {
                text("Measurement done and sucessful")

                beginHashing()
              }
            }, { e ->
              Log.e(this@HashingConnection) {
                text("Measurement failed")
                error(e)
              }
            })
        )
      )
    }

    private fun beginHashing() {
      progressBar.isIndeterminate = false
      progressBar.max = securityDuration
      progressBar.post(progressBarUpdate)

      progressText.text = "Securing your master password... your device may get hot and other apps may run slow..."

      activity.disposeOnPause(
        disposeOnDisconnect(
          hash()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnDispose {
              progressBar.removeCallbacks(progressBarUpdate)

              accountRecovery = null
              adviseLayout.visibility = View.GONE
              setupLayout.visibility = View.VISIBLE
              progressLayout.visibility = View.GONE
            }
            .subscribe({
              Log.v(this@HashingConnection) {
                text("Hashing completed successfully")
              }

              progressBar.progress = progressBar.max

              Jobs.schedule(
                activity,
                App.Components.Storage.account().secure(accountRecovery!!, params(), hash, password)
              ) {
                setMinimumLatency(0)
              }
            }, { error ->
              Log.e(this@HashingConnection) {
                text("Hashing failed")
                error(error)
              }
            })
        )
      )
    }

    private fun retry() {
      startSetup()
    }

  }

  lateinit var memoryInfo: ActivityManager.MemoryInfo

  lateinit var adviseLayout: View
  lateinit var setupLayout: View
  lateinit var progressLayout: View

  lateinit var setup: MaterialButton
  lateinit var password: TextInputEditText
  lateinit var length: ChipGroup
  lateinit var security: ChipGroup
  lateinit var securityLow: Chip
  lateinit var securityMedium: Chip
  lateinit var securityHigh: Chip
  lateinit var securityExtreme: Chip

  lateinit var start: TextView
  lateinit var explanation: TextView

  lateinit var progressText: TextView
  lateinit var progressBar: ProgressBar

  private val activity: UIActivity get() = context as UIActivity

  private var memoryPercent: Int = 85
  private val securityDuration: Int get() = security.findViewById<View>(security.checkedChipId).tag!! as Int

  private var accountRecovery: AccountRecovery? = null

  private val progressBarUpdate: Runnable = object : Runnable {
    override fun run() {
      if (progressBar.progress + 250 < progressBar.max) {
        progressBar.progress = progressBar.progress + 250
        progressBar.postDelayed(this, 250)
      } else {
        progressBar.progress = progressBar.max
        progressText.text = "Finishing up..."
        progressBar.isIndeterminate = true
      }
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    adviseLayout = findViewById(R.id.advise_layout)
    setupLayout = findViewById(R.id.setup_layout)

    setup = findViewById(R.id.setup)
    setup.setOnClickListener {
      onSetup()
    }

    password = findViewById(R.id.master_password)
    length = findViewById(R.id.length)
    length.check(R.id.length_normal)
    length.setOnCheckedChangeListener { _, _ ->
      generatePassword()
      updateSecurity()
    }

    security = findViewById(R.id.security)
    security.check(R.id.security_medium)
    security.setOnCheckedChangeListener { _, _ ->
      updateSecurity()
    }

    securityLow = security.findViewById(R.id.security_low)
    securityMedium = security.findViewById(R.id.security_medium)
    securityHigh = security.findViewById(R.id.security_high)
    securityExtreme = security.findViewById(R.id.security_extreme)

    start = findViewById(R.id.start)
    start.setOnClickListener {
      startSetup()
    }

    explanation = findViewById(R.id.explanation)

    progressLayout = findViewById(R.id.progress_layout)
    progressBar = findViewById(R.id.progress)
    progressText = findViewById(R.id.progress_text)
  }

  private fun onSetup() {
    TransitionManager.beginDelayedTransition(parent as ViewGroup)

    adviseLayout.visibility = View.GONE
    setupLayout.visibility = View.VISIBLE

    memoryInfo = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java)
      .getMemoryInfo(memoryInfo)

    generatePassword()
    updateSecurity()
  }

  private fun generatePassword() {
    if (-1 == length.checkedChipId) {
      length.check(R.id.length_normal)
      return
    }

    val length = when (length.checkedChipId) {
      -1, R.id.length_short -> 10
      R.id.length_normal -> 16
      R.id.length_long -> 32
      else -> 0
    }

    if (length > 0) {
      password.setText(PasswordGenerator.getInstance()
        .run {
          generate(ASCIIPasswordGeneratorParams(length))
        })
    }
  }

  private fun updateSecurity() {
    if (-1 == security.checkedChipId) {
      security.check(R.id.security_medium)
      return
    }

    securityLow.isCheckable = true
    securityMedium.isCheckable = true
    securityHigh.isCheckable = true
    securityExtreme.isCheckable = true

    when (length.checkedChipId) {
      R.id.length_short, R.id.length_custom -> {
        if (securityLow.isChecked) {
          security.check(R.id.security_medium)
        }

        securityLow.isCheckable = false
      }
    }

    when (memoryInfo.totalMem) {
      in 0 until (1024L * 1024L * 1024L) * 2L -> {
        securityLow.text = "2:00"
        securityLow.tag = (2 * 60) * 1000

        securityMedium.text = "3:30"
        securityMedium.tag = (3 * 60 + 30) * 1000

        securityHigh.text = "5:30"
        securityHigh.tag = (5 * 60 + 30) * 1000

        securityExtreme.text = "10:00"
        securityExtreme.tag = (10 * 60) * 1000
      }

      else -> {
        securityLow.text = "1:30"
        securityLow.tag = (1 * 60 + 30) * 1000

        securityMedium.text = "3:00"
        securityMedium.tag = (3 * 60) * 1000

        securityHigh.text = "5:00"
        securityHigh.tag = (5 * 60) * 1000

        securityExtreme.text = "10:00"
        securityExtreme.tag = (10 * 60) * 1000
      }
    }

    if (-1 != security.checkedChipId) {
      explanation.text = resources.getString(
        R.string.setup_master_password_explanation,
        security.findViewById<Chip>(security.checkedChipId).text,
        if (memoryInfo.totalMem / (1024L * 1024L * 1024L) >= 1) {
          memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        } else {
          memoryInfo.totalMem / (1024.0 * 1024.0)
        },
        if (memoryInfo.totalMem / (1024L * 1024L * 1024L) >= 1) {
          "GB"
        } else {
          "MB"
        }
      )
    }
  }

  private fun startSetup() {
    if (null == accountRecovery) {
      context.getSystemService(KeyguardManager::class.java)
        .let { keyguardManager ->

          activity.startActivityForResult(
            keyguardManager.createConfirmDeviceCredentialIntent(
              resources.getString(R.string.setup_master_password_keyguard_title),
              resources.getString(R.string.setup_master_password_keyguard_description)
            ), RequestCodes.UI_SETUP_MASTER_PASSWORD_KEYGUARD
          )

          activity.disposeOnDestroy(activity.results.filter { RequestCodes.UI_SETUP_MASTER_PASSWORD_KEYGUARD == it.first }
            .take(1)
            .subscribe { (_, resultCode, _) ->

              TransitionManager.beginDelayedTransition(parent as ViewGroup)

              setupLayout.visibility = View.GONE
              progressLayout.visibility = View.VISIBLE
              progressBar.isIndeterminate = true

              activity.disposeOnPause(App.Components.Storage.account().accountRecovery()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose {
                  mainThreadOnly {
                    accountRecovery = null

                    setupLayout.visibility = View.VISIBLE
                    progressLayout.visibility = View.GONE
                  }
                }
                .subscribe {
                  accountRecovery = it

                  activity.unbindOnPause(
                    Intent(activity, MasterPasswordHashService::class.java),
                    Context.BIND_AUTO_CREATE,
                    HashingConnection(ByteArray(16))
                  )
                })
            })
        }
    } else {
      TransitionManager.beginDelayedTransition(parent as ViewGroup)

      setupLayout.visibility = View.GONE
      progressLayout.visibility = View.VISIBLE
      progressBar.isIndeterminate = true

      activity.unbindOnPause(
        Intent(activity, MasterPasswordHashService::class.java),
        Context.BIND_AUTO_CREATE,
        HashingConnection(ByteArray(16))
      )
    }
  }
}
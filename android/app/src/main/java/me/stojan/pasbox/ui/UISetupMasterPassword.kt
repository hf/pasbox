package me.stojan.pasbox.ui

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
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
) : UITop(context, attrs, defStyleAttr) {

  private inner class HashingConnection(password: ByteArray) : MasterPasswordHashConnection(password, 512) {
    private var memoryInfo = ActivityManager.MemoryInfo()

    override fun onConnected() {
      super.onConnected()

      context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
      memory(memoryInfo)

      progressText.transition = 0

      this@UISetupMasterPassword.keepScreenOn = true

      activity.disposeOnPause(
        disposeOnDisconnect(
          measure(securityDuration, memoryPercent)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnDispose {
              mainThreadOnly {
                progressBar.removeCallbacks(progressBarUpdate)

                this@UISetupMasterPassword.keepScreenOn = false

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

              mainThreadOnly {
                this@UISetupMasterPassword.keepScreenOn = false
              }
            })
        )
      )
    }

    private fun beginHashing() {
      progressBar.isIndeterminate = false
      progressBar.max = securityDuration
      progressBar.post(progressBarUpdate)

      progressText.transition = 2

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

              mainThreadOnly {
                this@UISetupMasterPassword.keepScreenOn = false

                onDone?.invoke()
              }
            }, { error ->
              Log.e(this@HashingConnection) {
                text("Hashing failed")
                error(error)
              }

              mainThreadOnly {
                this@UISetupMasterPassword.keepScreenOn = false
              }
            })
        )
      )
    }

    private fun retry() {
      startSetup()
    }

  }

  val swipable: Boolean get() = View.VISIBLE == setupLayout.visibility

  private lateinit var memoryInfo: ActivityManager.MemoryInfo

  private lateinit var adviseLayout: View
  private lateinit var setupLayout: View
  private lateinit var progressLayout: View

  private lateinit var setup: MaterialButton
  private lateinit var password: TextInputEditText
  private lateinit var length: ChipGroup
  private lateinit var security: ChipGroup
  private lateinit var securityLow: Chip
  private lateinit var securityMedium: Chip
  private lateinit var securityHigh: Chip
  private lateinit var securityExtreme: Chip

  private lateinit var start: TextView
  private lateinit var explanation: TextView

  private lateinit var progressText: StateTextView
  private lateinit var progressBar: ProgressBar

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
        progressText.transition = 5
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

    progressText.transitions = intArrayOf(
      /* 0: */ 3000, 1, R.string.setup_master_password_measuring_performance_1,
      /* 1: */ 2000, 0, R.string.setup_master_password_measuring_performance_2,
      /* 2: */ 3500, 3, R.string.setup_master_password_securing_1,
      /* 3: */ 2500, 4, R.string.setup_master_password_securing_2,
      /* 4: */ 2500, 2, R.string.setup_master_password_securing_3,
      /* 5: */ 0, -1, R.string.setup_master_password_finishing_up
    )
  }

  private fun onSetup() {
    beginDelayedTransition()

    adviseLayout.visibility = View.GONE
    setupLayout.visibility = View.VISIBLE
    progressLayout.visibility = View.GONE

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

              beginDelayedTransition()

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
      beginDelayedTransition()

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
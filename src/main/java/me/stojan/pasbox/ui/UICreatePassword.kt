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

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.storage.Password
import java.security.SecureRandom

class UICreatePassword @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  companion object {
    const val UPCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOCASE = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "012346789"
    const val SPECIALS = "!@#$%^&*()-_=+[]{}:?<>~"
  }

  val activity: UIActivity get() = context as UIActivity

  var onDone: ((UICreatePassword) -> Unit)? = null

  lateinit var title: TextInputEditText
  lateinit var password: TextInputEditText

  lateinit var features: ChipGroup
  lateinit var featureMulticase: Chip
  lateinit var featureDigits: Chip
  lateinit var featureSpecials: Chip

  lateinit var size: ChipGroup

  lateinit var save: TextView

  override fun onFinishInflate() {
    super.onFinishInflate()

    title = findViewById(R.id.title)
    password = findViewById(R.id.password)

    features = findViewById(R.id.features)
    featureMulticase = findViewById(R.id.feature_multicase)
    featureDigits = findViewById(R.id.feature_digits)
    featureSpecials = findViewById(R.id.feature_specials)

    size = findViewById(R.id.size)

    save = findViewById(R.id.save)
    save.setOnClickListener {
      beginSave()
    }

    featureMulticase.setOnCheckedChangeListener { _, _ ->
      generatePassword()
    }

    featureDigits.setOnCheckedChangeListener { _, _ ->
      generatePassword()
    }

    featureSpecials.setOnCheckedChangeListener { _, _ ->
      generatePassword()
    }

    size.setOnCheckedChangeListener { _, id ->
      if (-1 == size.checkedChipId) {
        size.check(R.id.size_normal)
      } else {
        generatePassword()
      }
    }

    generatePassword()
  }

  fun generatePassword() {
    password.setText(String(StringBuilder(UPCASE.length + LOCASE.length + DIGITS.length + SPECIALS.length)
      .apply {
        append(LOCASE)

        if (featureMulticase.isChecked) {
          append(UPCASE)
        }

        if (featureDigits.isChecked) {
          append(DIGITS)
        }

        if (featureSpecials.isChecked) {
          append(SPECIALS)
        }
      }
      .let { chars ->
        SecureRandom().run {
          CharArray(
            when (size.checkedChipId) {
              R.id.size_short -> 8
              -1, R.id.size_normal -> 16
              R.id.size_huge -> 32
              else -> throw RuntimeException("Unknown checked id=${size.checkedChipId}")
            }
          ) { chars[nextInt(chars.length)] }
        }
      })
    )
  }

  fun beginSave() {
    save.setOnClickListener(null)

    context.getSystemService(FingerprintManager::class.java)
      .let { fingerprintManager ->
        context.getSystemService(KeyguardManager::class.java)
          .let { keyguardManager ->

            activity.startActivityForResult(
              keyguardManager.createConfirmDeviceCredentialIntent(
                resources.getString(R.string.password_keyguard_title),
                resources.getString(R.string.password_keyguard_description)
              ), RequestCodes.UI_CREATE_PASSWORD_KEYGUARD
            )

            activity.disposeOnDestroy(activity.results.filter { RequestCodes.UI_CREATE_PASSWORD_KEYGUARD == it.first }
              .take(1)
              .subscribe { (_, resultCode, _) ->
                mainThreadOnly {
                  if (Activity.RESULT_OK == resultCode) {
                    save.setText(R.string.password_saving)

                    App.Components.Storage.secrets()
                      .save(Password.create(title.text.toString(), password.text.toString()))
                      .observeOn(AndroidSchedulers.mainThread())
                      .subscribe { saveOp ->
                        // put save.cipher into fingerprint manager
                        Jobs.schedule(activity, saveOp.execute().toCompletable()) {
                          if (Build.VERSION.SDK_INT >= 28) {
                            setImportantWhileForeground(true)
                          } else {
                            setMinimumLatency(0)
                          }
                          setOverrideDeadline(0)
                        }.second.observeOn(AndroidSchedulers.mainThread())
                          .subscribe({
                            save.setText(R.string.password_saved)
                            onDone?.invoke(this@UICreatePassword)
                          }, {
                            save.setText(R.string.password_save_failed)
                            save.setOnClickListener { beginSave() }
                          })

                      }
                  } else {
                    save.setText(R.string.password_saving_failed_keyguard)
                    save.setOnClickListener { beginSave() }
                  }

                }
              })
          }

      }
  }

}
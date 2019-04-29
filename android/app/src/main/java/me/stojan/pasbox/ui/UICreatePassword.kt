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
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.jobs.Jobs
import me.stojan.pasbox.storage.secrets.Password
import java.security.SecureRandom

class UICreatePassword @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  companion object {
    const val UPCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOCASE = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    const val SPECIALS = "!@#$%^&*()-_=+[]{}:?<>~"
  }

  private val activity: UIActivity get() = context as UIActivity

  var onDone: ((UICreatePassword) -> Unit)? = null

  lateinit var titleLayout: TextInputLayout
  lateinit var title: TextInputEditText

  lateinit var websiteLayout: TextInputLayout
  lateinit var website: TextInputEditText

  lateinit var usernameLayout: TextInputLayout
  lateinit var username: TextInputEditText

  lateinit var password: TextInputEditText
  var passwordTextWatcher: TextWatcher? = null

  lateinit var features: ChipGroup
  lateinit var featureMulticase: Chip
  lateinit var featureDigits: Chip
  lateinit var featureSpecials: Chip

  lateinit var size: ChipGroup

  lateinit var save: TextView

  private val canSave: Boolean get() = title.length() > 0 && password.length() > 0

  override fun onFinishInflate() {
    super.onFinishInflate()

    title = findViewById(R.id.title)
    title.apply {
      requestFocus()
      setOnEditorActionListener { _, actionId, _ ->
        if (EditorInfo.IME_ACTION_DONE == actionId) {
          if (canSave) {
            beginSave()
            true
          } else {
            false
          }
        } else {
          false
        }
      }

      addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
          while (s.isNotEmpty() && s[0].isWhitespace()) {
            s.delete(0, 1)
          }

          Regex("([a-z-_]+(\\.[a-z-_]{2,})+)", RegexOption.IGNORE_CASE).find(s)
            .let { matchResult ->
              if (null == matchResult) {
                website.text = null
                username.text = null
                websiteLayout.visibility = View.GONE
                usernameLayout.visibility = View.GONE
              } else {
                website.setText(matchResult.value)
                websiteLayout.visibility = View.VISIBLE

                if (matchResult.range.first > 0) {
                  username.setText(s.subSequence(0, matchResult.range.first).trim())
                  usernameLayout.visibility = View.VISIBLE
                } else if ((matchResult.range.endInclusive + 1) < s.length) {
                  username.setText(s.subSequence((matchResult.range.endInclusive + 1), s.length).trim())
                  usernameLayout.visibility = View.VISIBLE
                } else {
                  username.text = null
                  usernameLayout.visibility = View.GONE
                }
              }
            }

          updateSave()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }
      })
    }

    password = findViewById(R.id.password)
    password.apply {
      setOnEditorActionListener { _, actionId, _ ->
        if (EditorInfo.IME_ACTION_DONE == actionId) {
          if (canSave) {
            beginSave()
            true
          } else {
            false
          }
        } else {
          false
        }
      }
    }

    websiteLayout = findViewById(R.id.website_layout)
    website = findViewById(R.id.website)

    usernameLayout = findViewById(R.id.username_layout)
    username = findViewById(R.id.username)

    features = findViewById(R.id.features)
    featureMulticase = findViewById(R.id.feature_multicase)
    featureDigits = findViewById(R.id.feature_digits)
    featureSpecials = findViewById(R.id.feature_specials)

    size = findViewById(R.id.size)

    save = findViewById(R.id.save)
    save.setOnClickListener {
      if (canSave) {
        beginSave()
      }
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

    post {
      generatePassword()
      updateSave()
    }
  }

  private fun generatePassword() {
    val multicase = featureMulticase.isChecked
    val digits = featureDigits.isChecked
    val specials = featureDigits.isChecked
    val length = when (size.checkedChipId) {
      R.id.size_short -> 8
      -1, R.id.size_normal -> 16
      R.id.size_huge -> 32
      else -> throw RuntimeException("Unknown checked id=${size.checkedChipId}")
    }

    if (null != passwordTextWatcher) {
      password.removeTextChangedListener(passwordTextWatcher)
    }

    password.setText(String(StringBuilder(UPCASE.length + LOCASE.length + DIGITS.length + SPECIALS.length)
      .apply {
        append(LOCASE)

        if (multicase) {
          append(UPCASE)
        }

        if (digits) {
          append(DIGITS)
        }

        if (specials) {
          append(SPECIALS)
        }
      }
      .let { chars ->
        SecureRandom().run {
          CharArray(length) { chars[nextInt(chars.length)] }
        }
      })
    )

    passwordTextWatcher = object : TextWatcher {
      override fun afterTextChanged(s: Editable) {
        while (s.isNotEmpty() && s[0].isWhitespace()) {
          s.delete(0, 1)
        }

        updateSave()
      }

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
      }

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
      }
    }

    password.addTextChangedListener(passwordTextWatcher)
  }

  private fun updateSave() {
    if (title.length() > 0 && password.length() > 0) {
      save.setText(R.string.password_touch_to_save)
    } else if (title.length() <= 0) {
      save.setText(R.string.password_enter_title_to_save)
    } else if (password.length() <= 0) {
      save.setText(R.string.password_enter_password_to_save)
    } else {
      save.text = null
    }
  }

  private fun beginSave() {
    save.isEnabled = false
    title.isEnabled = false
    password.isEnabled = false

    context.getSystemService(KeyguardManager::class.java)
      .let { keyguardManager ->

        activity.startActivityForResult(
          keyguardManager.createConfirmDeviceCredentialIntent(
            resources.getString(R.string.create_password_keyguard_title),
            resources.getString(R.string.create_password_keyguard_description)
          ), RequestCodes.UI_CREATE_PASSWORD_KEYGUARD
        )

        activity.disposeOnDestroy(activity.results.filter { RequestCodes.UI_CREATE_PASSWORD_KEYGUARD == it.first }
          .take(1)
          .subscribe { (_, resultCode, _) ->
            mainThreadOnly {
              if (Activity.RESULT_OK == resultCode) {
                save.setText(R.string.password_saving)

                val passwordData =
                  Password.create(
                    title.text.toString(),
                    website.text?.toString(),
                    username.text?.toString(),
                    password.text.toString()
                  )

                activity.disposeOnDestroy(
                  Jobs.schedule(
                    activity, App.Components.Storage.secrets()
                      .save(passwordData).ignoreElement()
                  ) {
                    if (Build.VERSION.SDK_INT >= 28) {
                      setImportantWhileForeground(true)
                    } else {
                      setMinimumLatency(0)
                    }
                    setOverrideDeadline(0)
                  }.second.observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                      mainThreadOnly {
                        Log.v(this@UICreatePassword) { text("Password saved") }
                        save.setText(R.string.password_saved)
                        onDone?.invoke(this@UICreatePassword)
                      }
                    }, {
                      mainThreadOnly {
                        Log.v(this@UICreatePassword) { text("Password failed to save"); error(it) }
                        save.isEnabled = true
                        title.isEnabled = true
                        password.isEnabled = true
                        save.setText(R.string.password_save_failed)
                      }
                    }),

                  Jobs.schedule(
                    activity, App.Components.Storage.backups()
                      .backup(passwordData)
                  ) {
                    if (Build.VERSION.SDK_INT >= 28) {
                      setImportantWhileForeground(true)
                    } else {
                      setMinimumLatency(0)
                    }
                    setOverrideDeadline(0)
                  }.second.observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                      mainThreadOnly {
                        Log.v(this@UICreatePassword) { text("Backuped password") }
                      }
                    }, {
                      mainThreadOnly {
                        Log.v(this@UICreatePassword) { text("Password backup failed"); error(it) }
                      }
                    })
                )
              } else {
                save.isEnabled = true
                title.isEnabled = true
                password.isEnabled = true
                save.setText(R.string.password_saving_failed_keyguard)
              }

            }
          })
      }
  }

}
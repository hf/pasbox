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

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.password.ASCIIPasswordGeneratorParams
import me.stojan.pasbox.password.Password
import me.stojan.pasbox.password.PasswordGenerator
import me.stojan.pasbox.storage.secrets.PasswordSecret

class UICreatePassword @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),
  ChildOf<UICreateSecret>,
  ImplicitSceneRoot,
  KeyguardButton.Callbacks {

  override val parentView: UICreateSecret by ChildOf.Auto()
  override val sceneRoot: ViewGroup? by ImplicitSceneRoot.Auto

  private val activity: UIActivity get() = context as UIActivity

  lateinit var titleLayout: TextInputLayout
  lateinit var title: TextInputEditText

  lateinit var websiteLayout: TextInputLayout
  lateinit var website: TextInputEditText

  lateinit var usernameLayout: TextInputLayout
  lateinit var username: TextInputEditText

  lateinit var password: TextInputEditText

  lateinit var features: ChipGroup
  lateinit var featureMulticase: Chip
  lateinit var featureDigits: Chip
  lateinit var featureSpecials: Chip

  lateinit var size: ChipGroup

  lateinit var save: KeyguardButton

  private val canSave: Boolean get() = title.length() > 0 && password.length() > 0

  override fun onFinishInflate() {
    super.onFinishInflate()

    title = findViewById(R.id.title)
    title.apply {
      requestFocus()
      setOnEditorActionListener { _, actionId, _ ->
        if (EditorInfo.IME_ACTION_DONE == actionId) {
          if (canSave) {
            save.performClick()
            true
          } else {
            false
          }
        } else {
          false
        }
      }

    }

    password = findViewById(R.id.password)
    password.apply {
      setOnEditorActionListener { _, actionId, _ ->
        if (EditorInfo.IME_ACTION_DONE == actionId) {
          if (canSave) {
            save.performClick()
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
    save.requestCode = RequestCodes.UI_CREATE_PASSWORD_KEYGUARD
    save.callbacks = this

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
    }
  }

  private fun generatePassword() {
    val multicase = featureMulticase.isChecked
    val digits = featureDigits.isChecked
    val specials = featureDigits.isChecked
    val length = when (size.checkedChipId) {
      R.id.size_short -> 8
      -1, R.id.size_normal -> 16
      R.id.size_huge -> 22
      else -> throw RuntimeException("Unknown checked id=${size.checkedChipId}")
    }

    password.setText(
      PasswordGenerator.getInstance()
        .run {
          generate(ASCIIPasswordGeneratorParams(length, multicase, digits, specials))
        })
  }

  override fun onSuccess(button: KeyguardButton) {
    super.onSuccess(button)

    parentView.disposeOnRecycle(
      parentView.save(
      PasswordSecret.create(
        title.text.toString(),
        website.text?.toString(),
        username.text?.toString(),
        Password(password.text!!)
      )
    )
      .subscribe({
        Log.v(this@UICreatePassword) { text("PasswordSecret saved") }

        mainThreadOnly {
          parentView.onDone?.invoke()
        }
      }, {
        Log.v(this@UICreatePassword) { text("PasswordSecret failed to save"); error(it) }

        mainThreadOnly {
          save.isEnabled = true
          title.isEnabled = true
          password.isEnabled = true
        }
      })
    )
  }
}
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
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.storage.Secret
import me.stojan.pasbox.storage.SecretPublic

class UISecretPassword @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : UISecret(context, attrs, defStyleAttr),
  KeyguardButton.Callbacks {

  lateinit var title: TextView
  lateinit var open: KeyguardButton
  lateinit var opened: ViewGroup
  lateinit var password: TextInputEditText

  override fun onFinishInflate() {
    super.onFinishInflate()

    title = findViewById(R.id.title)
    open = findViewById(R.id.open)
    open.requestCode = RequestCodes.UI_OPEN_PASSWORD_KEYGUARD
    open.callbacks = this
    opened = findViewById(R.id.opened)
    password = opened.findViewById(R.id.password)
  }

  override fun onBind(value: Pair<SecretPublic, Secret>) {
    super.onBind(value)

    title.text = public.password.title
    password.text = null

    open.visibility = View.GONE
    open.transition = 0

    opened.visibility = View.GONE

    setOnClickListener {
      beginDelayedTransition()

      when (open.visibility) {
        View.VISIBLE -> {
          open.visibility = View.GONE
          opened.visibility = View.GONE
          password.text = null
        }
        else -> open.visibility = View.VISIBLE
      }
    }
  }

  override fun onRecycle() {
    super.onRecycle()

    password.text = null
  }

  override fun onSuccess(button: KeyguardButton) {
    super.onSuccess(button)

    disposeOnRecycle(App.Components.Storage.secrets()
      .open(Single.just(Pair(public, secret)))
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (pub, pri) ->
        mainThreadOnly {
          beginDelayedTransition()

          opened.visibility = View.VISIBLE
          password.setText(pri.password.password)
        }
      })
  }

}
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
import com.google.android.material.textfield.TextInputLayout
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

  private lateinit var opened: ViewGroup
  private lateinit var toOpen: ViewGroup
  private lateinit var closed: ViewGroup

  lateinit var title: TextView
  lateinit var open: KeyguardButton
  lateinit var password: TextInputLayout

  override fun onFinishInflate() {
    super.onFinishInflate()

    opened = findViewById(R.id.opened)
    toOpen = findViewById(R.id.to_open)
    closed = findViewById(R.id.closed)

    title = findViewById(R.id.title)
    password = findViewById(R.id.password)
    open = findViewById(R.id.open)
    open.requestCode = RequestCodes.UI_OPEN_PASSWORD_KEYGUARD
    open.callbacks = this
  }

  override fun onBind(value: Pair<SecretPublic, Secret>) {
    super.onBind(value)

    opened.visibility = View.GONE
    toOpen.visibility = View.GONE
    closed.visibility = View.VISIBLE

    title.text = public.password.title
    password.editText?.text = null

    open.transition = 0

    setOnClickListener {
      beginDelayedTransition()

      when (closed.visibility) {
        View.VISIBLE -> {
          closed.visibility = View.GONE
          opened.visibility = View.GONE
          toOpen.visibility = View.VISIBLE

          open.transition = 0
        }

        else -> {
          opened.visibility = View.GONE
          toOpen.visibility = View.GONE
          closed.visibility = View.VISIBLE

          password.editText?.text = null
        }
      }
    }
  }

  override fun onRecycle() {
    super.onRecycle()

    password.editText?.text = null
  }

  override fun onSuccess(button: KeyguardButton) {
    super.onSuccess(button)

    disposeOnRecycle(App.Components.Storage.secrets()
      .open(Single.just(Pair(public, secret)))
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (_, private) ->
        mainThreadOnly {
          beginDelayedTransition()

          closed.visibility = View.GONE
          toOpen.visibility = View.GONE
          opened.visibility = View.VISIBLE

          password.editText?.setText(private.password.password)
        }
      })
  }

}
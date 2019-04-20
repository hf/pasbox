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
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import me.stojan.pasbox.App
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.mainThreadOnly

class UISecretPassword @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : UISecret(context, attrs, defStyleAttr) {

  lateinit var title: TextView
  lateinit var open: TextView
  lateinit var opened: ViewGroup
  lateinit var password: TextInputEditText

  private var remaining = 30

  private val _countdown: Runnable get() = countdown
  private val countdown: Runnable = Runnable {
    mainThreadOnly {
      TransitionManager.beginDelayedTransition(parent as ViewGroup)
      open.text = resources.getString(R.string.password_will_close, remaining)
      remaining = Math.max(0, remaining - 1)

      if (remaining <= 0) {
        password.text = null
        opened.visibility = View.GONE
        open.setOnTouchListener(null)
        open.setText(R.string.password_touch_to_open)
        open.visibility = View.GONE
      } else {
        postDelayed(_countdown, 1000)
      }
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    title = findViewById(R.id.title)
    open = findViewById(R.id.open)
    opened = findViewById(R.id.opened)
    password = opened.findViewById(R.id.password)
  }

  override fun onBind() {
    super.onBind()

    title.text = public.password.title
    open.setText(R.string.password_touch_to_open)
    open.setOnClickListener { onOpen() }

    setOnClickListener { onClick() }
  }

  override fun onRecycled() {
    super.onRecycled()

    removeCallbacks(_countdown)
  }

  private fun onClick() {
    TransitionManager.beginDelayedTransition(parent as ViewGroup)

    when (open.visibility) {
      View.VISIBLE -> {
        removeCallbacks(countdown)
        open.setOnTouchListener(null)
        open.visibility = View.GONE
        open.setText(R.string.password_touch_to_open)
        password.text = null
        opened.visibility = View.GONE
      }

      else -> {
        open.visibility = View.VISIBLE
      }
    }
  }

  private fun onOpen() {
    context.getSystemService(KeyguardManager::class.java).also { keyguardManager ->
      activity.startActivityForResult(
        keyguardManager.createConfirmDeviceCredentialIntent(
          resources.getString(R.string.open_password_keyguard_title),
          resources.getString(R.string.open_password_keyguard_description)
        ), RequestCodes.UI_OPEN_PASSWORD_KEYGUARD
      )

      disposeOnRecycle(activity.results.filter { RequestCodes.UI_OPEN_PASSWORD_KEYGUARD == it.first }
        .take(1)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { (_, resultCode, _) ->
          if (Activity.RESULT_OK == resultCode) {
            open.setText(R.string.password_opening)

            disposeOnRecycle(App.Components.Storage.secrets()
              .open(Single.just(Pair(public, secret)))
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe { (public, private) ->

                if (!recycled) {
                  TransitionManager.beginDelayedTransition(parent as ViewGroup)
                  opened.visibility = View.VISIBLE
                  password.setText(private.password.password)
                  autoclose()
                }
              })
          } else {
            open.setText(R.string.password_opening_failed_keyguard)
          }
        }
      )
    }
  }

  private fun autoclose() {
    mainThreadOnly {
      remaining = 30
      countdown.run()

      open.setOnTouchListener { _, event ->
        when (event.action) {
          MotionEvent.ACTION_DOWN -> {
            removeCallbacks(countdown)
            true
          }

          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            countdown.run()
            true
          }

          else -> false
        }
      }
    }
  }
}
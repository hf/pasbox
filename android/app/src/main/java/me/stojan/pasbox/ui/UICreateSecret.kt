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
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import me.stojan.pasbox.R

class UICreateSecret @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : UITop(context, attrs, defStyleAttr) {

  private val activity: UIActivity get() = context as UIActivity
  private val layoutInflater: LayoutInflater get() = activity.layoutInflater

  lateinit var content: ViewGroup
  lateinit var picker: ViewGroup
  lateinit var pickPassword: View
  lateinit var pickOTP: View
  lateinit var pickOther: View

  override fun onFinishInflate() {
    super.onFinishInflate()

    content = findViewById(R.id.content)
    picker = findViewById(R.id.picker)
    pickPassword = picker.findViewById(R.id.password)
    pickPassword.setOnClickListener(pick(R.layout.card_create_secret_password))

    pickOTP = picker.findViewById(R.id.otp)
    pickOTP.setOnClickListener(pick(R.layout.card_create_secret_otp))

    pickOther = picker.findViewById(R.id.other)
  }

  private inline fun pick(layout: Int): (View) -> Unit = { view ->
    TransitionManager.beginDelayedTransition(
      this.parent as ViewGroup, TransitionSet()
        .addTransition(Fade())
        .addTransition(ChangeBounds())
    )

    picker.visibility = View.GONE
    layoutInflater.inflate(layout, content, true)
      .also {
        it.findViewById<UICreatePassword?>(R.id.card_create_secret_password)
          ?.let {
            it.onDone = {
              this@UICreateSecret.onDone?.invoke()
            }
          }
      }
  }

}
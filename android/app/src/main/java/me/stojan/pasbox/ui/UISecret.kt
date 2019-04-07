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
import com.google.android.material.card.MaterialCardView
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import me.stojan.pasbox.storage.Secret
import me.stojan.pasbox.storage.SecretPublic

abstract class UISecret @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {
  protected val activity: UIActivity get() = context as UIActivity

  protected lateinit var public: SecretPublic
  protected lateinit var secret: Secret

  protected val recycled: Boolean get() = _recycled

  private var _recycled = true
  private val disposables = CompositeDisposable()

  fun bind(values: Pair<SecretPublic, Secret>) {
    _recycled = false
    disposables.clear()

    this.public = values.first
    this.secret = values.second

    onBind()
  }

  fun recycle() {
    _recycled = true

    onRecycled()

    disposables.clear()
  }

  open fun onBind() {
  }

  open fun onRecycled() {
  }

  protected fun disposeOnRecycle(vararg disposables: Disposable) {
    if (_recycled) {
      throw RuntimeException("UISecret is already recycled, can't subscribe when recycled")
    }

    this.disposables.addAll(*disposables)
  }

}
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

package me.stojan.pasbox

import androidx.appcompat.app.AppCompatActivity
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

abstract class AppActivity : AppCompatActivity() {

  private val pauseDisposables = CompositeDisposable()
  private val stopDisposables = CompositeDisposable()

  private var started = false
  private var resumed = false

  fun disposeOnPause(vararg disposables: Disposable) {
    if (!resumed) {
      throw Error("Activity is not resumed")
    }

    pauseDisposables.addAll(*disposables)
  }

  fun disposeOnStop(vararg disposables: Disposable) {
    if (!started) {
      throw Error("Activity is not started")
    }

    stopDisposables.addAll(*disposables)
  }

  override fun onStart() {
    super.onStart()
    started = true
    stopDisposables.clear()
  }

  override fun onResume() {
    super.onResume()
    resumed = true
    pauseDisposables.clear()
  }

  override fun onPause() {
    super.onPause()
    pauseDisposables.clear()
  }

  override fun onStop() {
    super.onStop()
    stopDisposables.dispose()
  }

}
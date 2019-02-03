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

package me.stojan.pasbox.dev

import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.reactivex.Maybe

fun <T> Task<T?>.toMaybe(): Maybe<T> =
  Maybe.create { emitter ->
    if (Looper.getMainLooper().isCurrentThread) {
      var cancelled = false
      this.addOnSuccessListener { value ->
        if (!cancelled) {
          emitter.onSuccess(value!!)
        }
      }

      this.addOnFailureListener { error ->
        if (!cancelled) {
          emitter.onError(error)
        }
      }

      this.addOnCanceledListener {
        cancelled = true
        emitter.onError(Error("Task was cancelled"))
      }
    } else {
      emitter.onSuccess(Tasks.await(this)!!)
    }
  }


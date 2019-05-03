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

import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import me.stojan.pasbox.dev.Log

abstract class AppActivity : AppCompatActivity() {

  private val pendingResults = ArrayList<Triple<Int, Int, Intent?>>(2)

  private val pauseDisposables = CompositeDisposable()
  private val stopDisposables = CompositeDisposable()
  private val destroyDisposables = CompositeDisposable()

  private val pauseUnbind = ArrayList<ServiceConnection>(3)

  private var started = false
  private var resumed = false

  private var activityResults = PublishSubject.create<Triple<Int, Int, Intent?>>()
  val results: Observable<Triple<Int, Int, Intent?>> = activityResults

  private lateinit var _navigationDrawer: DrawerLayout
  private lateinit var _floatingAction: FloatingActionButton
  private lateinit var _content: NestedScrollView
  private lateinit var _recycler: RecyclerView

  val navigationDrawer: DrawerLayout get() = _navigationDrawer
  val floatingAction: FloatingActionButton get() = _floatingAction
  val content: NestedScrollView get() = _content
  val recycler: RecyclerView get() = _recycler

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    destroyDisposables.clear()

    setContentView(R.layout.ui_main)

    setSupportActionBar(findViewById(R.id.toolbar))

    _navigationDrawer = findViewById(R.id.navigation_drawer)
    _floatingAction = findViewById(R.id.floating_action)
    _content = findViewById(R.id.content)
    _recycler = findViewById(R.id.recycler)
  }

  override fun unbindService(conn: ServiceConnection) {
    pauseUnbind.remove(conn)
    super.unbindService(conn)
  }

  fun unbindOnPause(intent: Intent, flags: Int, serviceConnection: ServiceConnection) {
    bindService(intent, serviceConnection, flags)
    pauseUnbind.add(serviceConnection)
  }

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

  fun disposeOnDestroy(vararg disposables: Disposable) {
    if (isDestroyed) {
      throw Error("Activity is already destroyed")
    }

    destroyDisposables.addAll(*disposables)
  }

  override fun onStart() {
    super.onStart()
    started = true
    stopDisposables.clear()

    Log.v(this) { text("onStart") }
  }

  override fun onResume() {
    super.onResume()
    resumed = true
    pauseDisposables.clear()

    Log.v(this) { text("onResume") }
  }

  override fun onPostResume() {
    super.onPostResume()

    if (pendingResults.isNotEmpty()) {
      pendingResults.forEach { result -> activityResults.onNext(result) }
      pendingResults.clear()
    }
  }

  override fun onPause() {
    super.onPause()
    resumed = false
    pauseDisposables.clear()

    pauseUnbind.forEach {
      super.unbindService(it)
    }
    pauseUnbind.clear()

    Log.v(this) { text("onPause") }
  }

  override fun onStop() {
    super.onStop()
    started = false
    stopDisposables.dispose()

    Log.v(this) { text("onStop") }
  }

  override fun onDestroy() {
    super.onDestroy()

    Log.v(this) { text("onDestroy") }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    Log.v(this) {
      text("Activity result")
      param("requestCode", requestCode)
      param("resultCode", resultCode)
      param("data", data)
    }

    if (resumed) {
      activityResults.onNext(Triple(requestCode, resultCode, data))
    } else {
      pendingResults.add(Triple(requestCode, resultCode, data))
    }
  }

}
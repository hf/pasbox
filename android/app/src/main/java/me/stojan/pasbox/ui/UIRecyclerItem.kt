package me.stojan.pasbox.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import me.stojan.pasbox.dev.mainThreadOnly

abstract class UIRecyclerItem<T> @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  var onDone: (() -> Unit)? = null

  private var _recycled = false

  protected val recycled get() = _recycled
  protected val disposables = CompositeDisposable()

  fun bind(value: T) {
    _recycled = false
    onBind(value)
  }

  fun recycle() {
    onRecycle()
    _recycled = false
  }

  fun disposeOnDetach(vararg disposables: Disposable) {
    mainThreadOnly {
      if (!isAttachedToWindow) {
        throw RuntimeException("View ${this::class.java.simpleName} is not attached to window")
      }

      this.disposables.addAll(*disposables)
    }
  }

  open fun onBind(value: T) {

  }

  open fun onRecycle() {

  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    disposables.clear()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    disposables.dispose()
  }

}
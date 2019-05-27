package me.stojan.pasbox.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import me.stojan.pasbox.dev.mainThreadOnly

abstract class UIRecyclerItem<T> @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ImplicitSceneRoot {

  override val sceneRoot: ViewGroup? by ImplicitSceneRoot.Auto

  var onDone: (() -> Unit)? = null

  private var _recycled = false

  protected val recycled get() = _recycled
  private val disposables = CompositeDisposable()

  fun bind(value: T) {
    _recycled = false
    onBind(value)
  }

  fun recycle() {
    onRecycle()
    disposables.dispose()
    _recycled = true
  }

  fun disposeOnRecycle(vararg disposables: Disposable) {
    mainThreadOnly {
      if (_recycled) {
        throw RuntimeException("View ${this::class.java.simpleName} is recycled")
      }

      this.disposables.addAll(*disposables)
    }
  }

  open fun onBind(value: T) {

  }

  open fun onRecycle() {

  }
}
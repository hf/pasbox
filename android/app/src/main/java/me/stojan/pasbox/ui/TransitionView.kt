package me.stojan.pasbox.ui

import android.content.Context
import android.transition.Transition
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

open class TransitionView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private var child = 0

  var transitionResources = 0

  private var _transition = 0
  var transition: Int
    get() = _transition
    set(value) {
      _transition = value
      removeCallbacks(callback)

      if (transitions.isNotEmpty() && childCount > 0) {
        var currentChild = getChildAt(child)
        child = (child + 1) % childCount

        var nextChild = getChildAt(child)

        TransitionManager.beginDelayedTransition(
          if (null != parent) {
            parent as ViewGroup
          } else {
            this
          },
          apply?.invoke(transition, nextChild, currentChild)
        )

        val delay = transitions[delayIndex(value)]
        val jump = transitions[jumpIndex(value)]

        if (delay > 0 && jump > -1) {
          postDelayed(callback, delay.toLong())
        }
      }
    }

  private val callback = object : Runnable {
    override fun run() {
      val jump = transitions[jumpIndex(transition)]
      val delay = transitions[delayIndex(transition)]

      if (delay > 0 && jump > -1) {
        transition = jump
      }
    }
  }

  private var _transitions: IntArray = intArrayOf()
  var transitions: IntArray
    get() = _transitions
    set(value) {
      _transitions = value
      transition = 0
    }

  private var _apply: ((state: Int, new: View, old: View) -> Transition?)? = null
  var apply: ((state: Int, new: View, old: View) -> Transition?)?
    get() = _apply
    set(value) {
      _apply = value
    }

  override fun onFinishInflate() {
    super.onFinishInflate()

    if (childCount < 1) {
      throw RuntimeException("TransitionView must have at least one child")
    }

    val firstChild = getChildAt(0)

    firstChild.visibility = View.VISIBLE

    for (i in 1 until childCount) {
      val child = getChildAt(i)
      if (!firstChild.javaClass.isInstance(child)) {
        throw RuntimeException("Child at $i is not an instance of ${firstChild.javaClass}")
      }

      child.visibility = View.GONE
    }

    transition = 0
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    transition = transition
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    removeCallbacks(callback)
  }

  fun resource(transition: Int, res: Int): Int = transitions[resourceIndex(transition, res)]

  protected inline fun delayIndex(state: Int) = (transitionResources + 2) * state + 0
  protected inline fun jumpIndex(state: Int) = (transitionResources + 2) * state + 1
  protected inline fun resourceIndex(state: Int, res: Int) = (transitionResources + 2) * state + (2 + res)
}
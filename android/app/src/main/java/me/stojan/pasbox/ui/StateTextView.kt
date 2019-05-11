package me.stojan.pasbox.ui

import android.content.Context
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView

class StateTextView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

  private val callback = object : Runnable {
    override fun run() {
      if (null != states && stateDelay(state) > 0 && stateNext(state) > -1) {
        state = states!![stateNext(state)]
      }
    }
  }

  private var _states: IntArray? = null
  var states: IntArray?
    get() = _states
    set(value) {
      TransitionManager.beginDelayedTransition(parent as ViewGroup)

      removeCallbacks(callback)
      text = null

      value?.let {
        if (it.size % 3 != 0) {
          throw RuntimeException("State array must be multiple of 3")
        }

        for (i in 0 until it.size / 3) {
          val stateNext = it[stateNext(i)]

          if (stateNext > -1) {
            if (stateNext >= it.size / 3) {
              throw RuntimeException("State at $i has next state index that does not exist")
            }
          }
        }
      }

      _states = value
    }

  private var _state: Int = -1
  var state: Int
    get() = _state
    set(value) {
      TransitionManager.beginDelayedTransition(parent as ViewGroup)

      removeCallbacks(callback)

      _state = value

      if (value > -1 && null != states) {
        setText(states!![stateRes(value)])

        if (stateDelay(value) > 0 && stateNext(value) > -1) {
          postDelayed(callback, states!![stateDelay(value)].toLong())
        }
      }
    }

  private inline fun stateRes(state: Int) = 3 * state + 0
  private inline fun stateDelay(state: Int) = 3 * state + 1
  private inline fun stateNext(state: Int) = 3 * state + 2

}



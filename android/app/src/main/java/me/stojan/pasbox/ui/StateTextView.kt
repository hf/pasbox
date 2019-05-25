package me.stojan.pasbox.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView

class StateTextView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TransitionView(context, attrs, defStyleAttr) {

  init {
    transitionResources = 1
    apply = { transition, new, old ->
      (new as TextView).setText(resource(transition, 0))

      if (new !== old) {
        new.visibility = View.VISIBLE
        old.visibility = View.GONE
      }

      null
    }
  }

}



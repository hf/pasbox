package me.stojan.pasbox.ui

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.View
import android.widget.TextView

open class StateTextView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TransitionView(context, attrs, defStyleAttr) {

  init {
    transitionResources = 1

    prepare = { _, new, old ->
      if (new !== old) {
        TransitionSet()
          .addTransition(
            Fade(Fade.MODE_OUT)
              .setDuration(150)
              .addTarget(old)
          )
          .addTransition(
            Fade(Fade.MODE_IN)
              .setStartDelay(100)
              .setDuration(150)
              .addTarget(new)
          )
          .addTransition(
            ChangeBounds()
              .addTarget(this)
              .setStartDelay(100)
              .setDuration(150)
          )
      } else {
        null
      }
    }

    apply = { transition, new, old ->
      (new as TextView).setText(resource(transition, 0))

      if (new !== old) {
        new.visibility = View.VISIBLE
        old.visibility = View.GONE
      }
    }
  }

}



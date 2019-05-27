package me.stojan.pasbox.ui

import android.transition.Transition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import me.stojan.pasbox.R
import kotlin.reflect.KProperty

interface ImplicitSceneRoot {
  object Auto {
    operator fun getValue(thisRef: View, property: KProperty<*>): ViewGroup? {
      var parent = thisRef.parent

      while (null != parent) {
        if (null != (parent as ViewGroup).getTag(R.id.implicit_scene_root)) {
          return parent
        }

        parent = parent.parent
      }

      return null
    }
  }

  val sceneRoot: ViewGroup?
}

inline fun ImplicitSceneRoot.beginDelayedTransition(transition: Transition? = null) {
  val sceneRoot = this.sceneRoot

  when {
    null != sceneRoot -> TransitionManager.beginDelayedTransition(sceneRoot, transition)
    this is ViewGroup -> TransitionManager.beginDelayedTransition(this, transition)
    else -> TransitionManager.beginDelayedTransition((this as View).parent as ViewGroup, transition)
  }
}


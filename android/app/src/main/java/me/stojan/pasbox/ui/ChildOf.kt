package me.stojan.pasbox.ui

import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface ChildOf<V : ViewGroup> {
  class Auto<V : ViewGroup> {
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any, property: KProperty<*>): V {
      var parent = (thisRef as View).parent

      while (null != parent) {
        if ((property.returnType.classifier as KClass<V>).isInstance(parent)) {
          return parent as V
        }

        parent = parent.parent
      }

      throw RuntimeException("View $thisRef does not have a parent")
    }
  }

  val parentView: V
}
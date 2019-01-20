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

import me.stojan.pasbox.BuildConfig

object Log {

  class Entry {
    internal val builder = StringBuilder()

    lateinit var tag: String
    var exception: Throwable? = null

    fun text(text: String) {
      builder.append(text)
    }

    fun <V : Any?> param(name: String, value: V) {
      if (!builder[builder.length - 1].isWhitespace()) {
        builder.append(' ')
      }

      builder.append(name)
      builder.append("=")
      value(value)
    }

    fun value(value: Any?) {
      when (value) {
        null -> builder.append("NULL")
        is String -> builder.append('"').append(value).append('"')
        is Boolean -> builder.append(value)
        is Int -> builder.append(value).append('i')
        is Long -> builder.append(value).append('l')
        is Float -> builder.append(value).append('f')
        is Double -> builder.append(value).append('d')
        is Short -> builder.append(value).append('s')
        is Byte -> builder.append("0x").append(value.toString(16))
        is Array<*> -> {
          builder.append("[ ")
          value.forEach { item ->
            value(item)
            builder.append(", ")
          }
          builder.append(" ]")
        }
        is List<*> -> {
          builder.append("( ")
          value.forEach { item ->
            value(item)
            builder.append(", ")
          }
          builder.append(" )")
        }
        is Map<*, *> -> {
          builder.append("{ ")
          value.entries.forEach { entry ->
            value(entry.key)
            builder.append(": ")
            value(entry.value)
          }
          builder.append(" }")
        }
        is Set<*> -> {
          builder.append("< ")
          value.forEach { item ->
            value(item)
            builder.append(", ")
          }
          builder.append(" >")
        }
        else -> builder.append(value.javaClass.simpleName)
          .append('@')
          .append((0xFF and System.identityHashCode(value)).toString(16))
          .append("<{ ")
          .append(value.toString())
          .append(" }>")
      }
    }

    override fun toString() = builder.toString()
  }

  inline fun <T : Any> formatTag(tag: T) = when (tag) {
    is String -> tag
    is Class<*> -> tag.simpleName
    else -> "${tag.javaClass.simpleName}@${(0xFFFF and System.identityHashCode(tag)).toString(16)}"
  }

  inline fun <T : Any> v(tag: T, stmt: Log.Entry.() -> Unit) {
    if (BuildConfig.DEBUG) {
      val entry = Log.Entry()
      entry.tag = formatTag(tag)
      stmt(entry)
      android.util.Log.v(entry.tag, entry.toString(), entry.exception)
    }
  }

  inline fun <T : Any> d(tag: T, stmt: Log.Entry.() -> Unit) {
    if (BuildConfig.DEBUG) {
      val entry = Log.Entry()
      entry.tag = formatTag(tag)
      stmt(entry)
      android.util.Log.d(entry.tag, entry.toString(), entry.exception)
    }
  }

  inline fun <T : Any> i(tag: T, stmt: Log.Entry.() -> Unit) {
    if (BuildConfig.DEBUG) {
      val entry = Log.Entry()
      entry.tag = formatTag(tag)
      stmt(entry)
      android.util.Log.i(entry.tag, entry.toString(), entry.exception)
    }
  }

  inline fun <T : Any> w(tag: T, stmt: Log.Entry.() -> Unit) {
    if (BuildConfig.DEBUG) {
      val entry = Log.Entry()
      entry.tag = formatTag(tag)
      stmt(entry)
      android.util.Log.w(entry.tag, entry.toString(), entry.exception)
    }
  }

  inline fun <T : Any> e(tag: T, stmt: Log.Entry.() -> Unit) {
    if (BuildConfig.DEBUG) {
      val entry = Log.Entry()
      entry.tag = formatTag(tag)
      stmt(entry)
      android.util.Log.e(entry.tag, entry.toString(), entry.exception)
    }
  }
}

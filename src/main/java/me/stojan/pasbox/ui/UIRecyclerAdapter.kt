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

package me.stojan.pasbox.ui

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.stojan.pasbox.dev.mainThreadOnly


class UIRecyclerAdapter(val activity: UIActivity) : RecyclerView.Adapter<UIRecyclerAdapter.UIViewHolder>() {

  abstract class UIViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

  class TopHolder(itemView: View, val bindFn: ((View) -> Unit)?) : UIViewHolder(itemView) {
    fun bind() {
      this.bindFn?.let { it(itemView) }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UIViewHolder =
    topviews.indexOfFirst { viewType == it.first }
      .let { index ->
        if (index > -1) {
          TopHolder(activity.layoutInflater.inflate(viewType, parent, false), topviews[index].second)
        } else {
          throw Error("Unknown type")
        }
      }

  override fun onBindViewHolder(holder: UIViewHolder, position: Int) {
    if (position < topviews.size) {
      (holder as TopHolder).bind()
    }
  }

  private val topviews = ArrayList<Pair<Int, ((View) -> Unit)?>>(5)

  override fun getItemViewType(position: Int): Int =
    if (position < topviews.size) {
      topviews[position].first
    } else {
      0
    }

  override fun getItemCount(): Int = 0 + topviews.size

  fun presentTop(layout: Int, bind: ((View) -> Unit)? = null) {
    mainThreadOnly {
      topviews.indexOfFirst { layout == it.first }
        .let { index ->
          if (index < 0) {
            topviews.add(Pair(layout, bind))
            notifyItemInserted(topviews.size - 1)
          }
        }
    }
  }

  fun presentTopImportant(layout: Int, bind: ((View) -> Unit)? = null) {
    mainThreadOnly {
      topviews.indexOfFirst { layout == it.first }
        .let { index ->
          if (index < 0) {
            topviews.add(0, Pair(layout, bind))
            notifyItemInserted(0)
          } else {
            topviews.removeAt(index)
            topviews.add(0, Pair(layout, bind))
            notifyItemMoved(index, 0)
          }
        }
    }
  }

  fun dismissTop(layout: Int) {
    mainThreadOnly {
      topviews.indexOfFirst { layout == it.first }
        .let { index ->
          if (index > -1) {
            topviews.removeAt(index)
            notifyItemRemoved(index)
          }
        }
    }
  }
}
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.Log
import me.stojan.pasbox.dev.mainThreadOnly
import me.stojan.pasbox.storage.Secret
import me.stojan.pasbox.storage.SecretPublic
import me.stojan.pasbox.storage.SecretStore

class UIRecyclerAdapter(val activity: UIActivity) : RecyclerView.Adapter<UIRecyclerAdapter.UIViewHolder>() {

  abstract class UIViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    open val swipeFlags: Int = 0

    open fun onSwiped(direction: Int) {

    }

    open fun onRecycled() {

    }
  }

  interface Top {
    companion object {
      fun simple(layout: Int, bindFn: ((View) -> Unit)? = null) = object : Top {
        override val layout: Int = layout
        override val swipable: Boolean = false

        override fun onBound(view: View) {
          if (null != bindFn) {
            bindFn(view)
          }
        }

        override fun onSwiped(view: View, direction: Int) {
        }

      }
    }

    val layout: Int
    val swipable: Boolean

    fun onBound(view: View)
    fun onSwiped(view: View, direction: Int)
  }

  class TopHolder(itemView: View) : UIViewHolder(itemView) {
    var top: Top? = null

    override val swipeFlags: Int
      get() = top.let {
        if (null == it || !it.swipable) {
          0
        } else {
          ItemTouchHelper.START or ItemTouchHelper.END
        }
      }

    fun bind(top: Top) {
      this.top = top
      this.top?.onBound(itemView)
    }

    override fun onSwiped(direction: Int) {
      super.onSwiped(direction)
      this.top?.onSwiped(itemView, direction)
    }
  }

  class PagedHolder(itemView: View) : UIViewHolder(itemView) {

    fun bind(pair: Pair<SecretPublic, Secret>) {
      (itemView as UISecret).bind(pair)
    }

    override fun onRecycled() {
      super.onRecycled()
      (itemView as UISecret).recycle()
    }

  }

  private val touchHelper =
    ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START or ItemTouchHelper.END) {
      override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
      ): Boolean = false

      override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        when (viewHolder) {
          is UIViewHolder -> viewHolder.onSwiped(direction)
        }
      }

      override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
        when (viewHolder) {
          is UIViewHolder -> ItemTouchHelper.Callback.makeFlag(
            ItemTouchHelper.ACTION_STATE_SWIPE,
            viewHolder.swipeFlags
          )
          else -> 0
        }
    })

  fun <R : RecyclerView> mount(recyclerView: R) {
    recyclerView.adapter = this
    touchHelper.attachToRecyclerView(recyclerView)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UIViewHolder =
    topviews.indexOfFirst { it.layout == viewType }
      .let { index ->
        if (index > -1) {
          TopHolder(activity.layoutInflater.inflate(topviews[index].layout, parent, false))
        } else {
          PagedHolder(activity.layoutInflater.inflate(viewType, parent, false))
        }
      }

  override fun onBindViewHolder(holder: UIViewHolder, position: Int) {
    if (position < topviews.size) {
      (holder as TopHolder).bind(topviews[position])
    } else {
      (holder as PagedHolder).bind(paged[position - topviews.size])
    }
  }

  override fun onViewRecycled(holder: UIViewHolder) {
    super.onViewRecycled(holder)

    Log.v(this) {
      text("Recycling")
      param("holder", holder)
    }

    holder.onRecycled()
  }

  override fun onFailedToRecycleView(holder: UIViewHolder): Boolean {
    val defaultResult = super.onFailedToRecycleView(holder)

    Log.v(this) {
      text("Failed to recycle")
      param("holder", holder)
    }

    holder.onRecycled()

    return defaultResult
  }

  private var important = 0
  private val topviews = ArrayList<Top>(5)
  private val paged = ArrayList<Pair<SecretPublic, Secret>>(100)

  override fun getItemViewType(position: Int): Int =
    if (position < topviews.size) {
      topviews[position].layout
    } else {
      when (paged[position - topviews.size].first.infoCase) {
        SecretPublic.InfoCase.PASSWORD -> R.layout.card_secret_password
        SecretPublic.InfoCase.OTP -> R.layout.card_secret_otp
        else -> throw RuntimeException("Unknown info at position=$position")
      }
    }

  override fun getItemCount(): Int = 0 + topviews.size + paged.size

  fun presentTop(top: Top) =
    mainThreadOnly {
      topviews.indexOfFirst { top.layout == it.layout }
        .let { index ->
          if (index < 0) {
            topviews.add(important, top)
            notifyItemInserted(important)
            notifyItemChanged(important)
          } else {
            topviews.removeAt(index)
            if (index < important) {
              important -= 1
            }
            topviews.add(important, top)
            notifyItemMoved(index, important)
          }
        }
    }

  fun presentTopImportant(top: Top) =
    mainThreadOnly {
      topviews.indexOfFirst { top.layout == it.layout }
        .let { index ->
          if (index < 0) {
            topviews.add(0, top)
            notifyItemInserted(0)
            notifyItemChanged(0)
            important += 1
          } else {
            topviews.removeAt(index)
            topviews.add(0, top)
            notifyItemMoved(index, 0)

            if (index >= important) {
              important += 1
            }
          }
        }
    }

  fun dismissTop(layout: Int) {
    mainThreadOnly {
      topviews.indexOfFirst { layout == it.layout }
        .let { index ->
          if (index > -1) {
            topviews.removeAt(index)
            notifyItemRemoved(index)

            if (index < important) {
              important -= 1
            }
          }
        }
    }
  }

  fun update(page: SecretStore.Page) {
    mainThreadOnly {
      val pagedFrom = topviews.size

      if (page.results.size < paged.size) {
        val removed = paged.size - page.results.size
        notifyItemRangeRemoved(pagedFrom + page.results.size, removed)
        notifyItemRangeChanged(pagedFrom, page.results.size)
      } else if (page.results.size > paged.size) {
        val new = page.results.size - paged.size
        notifyItemRangeInserted(pagedFrom, new)
        notifyItemRangeChanged(pagedFrom + new, paged.size)
      }

      paged.clear()
      paged.addAll(page.results)
    }
  }
}
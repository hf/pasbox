package me.stojan.pasbox.ui

import androidx.recyclerview.widget.RecyclerView

class SmartRecycledViewPool : RecyclerView.RecycledViewPool() {

  override fun putRecycledView(scrap: RecyclerView.ViewHolder?) {
    if (scrap !is UIRecyclerAdapter.TopHolder) {
      super.putRecycledView(scrap)
    }
  }

}

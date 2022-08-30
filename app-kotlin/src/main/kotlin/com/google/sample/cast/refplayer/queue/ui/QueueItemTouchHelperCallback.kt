/*
 * Copyright 2022 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.sample.cast.refplayer.queue.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import android.graphics.Canvas

/**
 * An implementation of the [androidx.recyclerview.widget.ItemTouchHelper.Callback].
 */
class QueueItemTouchHelperCallback constructor(private val mAdapter: ItemTouchHelperAdapter) :
    ItemTouchHelper.Callback() {
    override fun isLongPressDragEnabled(): Boolean {
        return true
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return true
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags: Int = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags: Int = ItemTouchHelper.END
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView, source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (source.getItemViewType() != target.getItemViewType()) {
            return false
        }
        mAdapter.onItemMove(source.getAdapterPosition(), target.getAdapterPosition())
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
        mAdapter.onItemDismiss(viewHolder.getAdapterPosition())
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (viewHolder is QueueListAdapter.QueueItemViewHolder) {
                ViewCompat.setTranslationX(viewHolder.mContainer, dX)
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder is QueueListAdapter.ItemTouchHelperViewHolder) {
                viewHolder.onItemSelected()
            }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is QueueListAdapter.ItemTouchHelperViewHolder) {
            viewHolder.onItemClear()
        }
    }

    /**
     * An interface to listen for a move or dismissal event from an
     * [ItemTouchHelper.Callback].
     */
    open interface ItemTouchHelperAdapter {
        /**
         * Called when an item has been dragged far enough to trigger a move. This is called every
         * time an item is shifted, and **not** at the end of a "drop" event.
         *
         * @param fromPosition Original position of the item before move.
         * @param toPosition   Target position of the item after move.
         */
        fun onItemMove(fromPosition: Int, toPosition: Int): Boolean

        /**
         * Called when an item has been dismissed by a swipe.
         *
         * @param position The position of the swiped item.
         */
        fun onItemDismiss(position: Int)
    }
}
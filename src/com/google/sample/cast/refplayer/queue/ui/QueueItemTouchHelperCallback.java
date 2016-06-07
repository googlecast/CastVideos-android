/*
 * Copyright (C) 2016 Google Inc. All Rights Reserved.
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
package com.google.sample.cast.refplayer.queue.ui;

import android.graphics.Canvas;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

/**
 * An implementation of the {@link android.support.v7.widget.helper.ItemTouchHelper.Callback}.
 */
public class QueueItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private final ItemTouchHelperAdapter mAdapter;

    public QueueItemTouchHelperCallback(ItemTouchHelperAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        final int swipeFlags = ItemTouchHelper.END;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source,
            RecyclerView.ViewHolder target) {
        if (source.getItemViewType() != target.getItemViewType()) {
            return false;
        }

        mAdapter.onItemMove(source.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {
        mAdapter.onItemDismiss(viewHolder.getAdapterPosition());
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (viewHolder instanceof QueueListAdapter.QueueItemViewHolder) {
                QueueListAdapter.QueueItemViewHolder queueHolder
                        = (QueueListAdapter.QueueItemViewHolder) viewHolder;
                ViewCompat.setTranslationX(queueHolder.mContainer, dX);
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder instanceof QueueListAdapter.ItemTouchHelperViewHolder) {
                QueueListAdapter.ItemTouchHelperViewHolder itemViewHolder
                        = (QueueListAdapter.ItemTouchHelperViewHolder) viewHolder;
                itemViewHolder.onItemSelected();
            }
        }

        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        if (viewHolder instanceof QueueListAdapter.ItemTouchHelperViewHolder) {
            QueueListAdapter.ItemTouchHelperViewHolder itemViewHolder
                    = (QueueListAdapter.ItemTouchHelperViewHolder) viewHolder;
            itemViewHolder.onItemClear();
        }
    }

    /**
     * An interface to listen for a move or dismissal event from an
     * {@link ItemTouchHelper.Callback}.
     */
    public interface ItemTouchHelperAdapter {

        /**
         * Called when an item has been dragged far enough to trigger a move. This is called every
         * time an item is shifted, and <strong>not</strong> at the end of a "drop" event.
         *
         * @param fromPosition Original position of the item before move.
         * @param toPosition   Target position of the item after move.
         */
        boolean onItemMove(int fromPosition, int toPosition);


        /**
         * Called when an item has been dismissed by a swipe.
         *
         * @param position The position of the swiped item.
         */
        void onItemDismiss(int position);
    }
}

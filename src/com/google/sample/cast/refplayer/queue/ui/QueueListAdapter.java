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

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.queue.QueueDataProvider;

import com.androidquery.AQuery;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An adapter to show the list of queue items.
 */
public class QueueListAdapter extends RecyclerView.Adapter<QueueListAdapter.QueueItemViewHolder>
        implements QueueItemTouchHelperCallback.ItemTouchHelperAdapter {

    private static final String TAG = "QueueListAdapter";
    private static final int IMAGE_THUMBNAIL_WIDTH = 64;
    private final QueueDataProvider mProvider;
    private static final int PLAY_RESOURCE = R.drawable.ic_play_arrow_grey600_48dp;
    private static final int PAUSE_RESOURCE = R.drawable.ic_pause_grey600_48dp;
    private static final int DRAG_HANDLER_DARK_RESOURCE = R.drawable.ic_drag_updown_grey_24dp;
    private static final int DRAG_HANDLER_LIGHT_RESOURCE = R.drawable.ic_drag_updown_white_24dp;
    private final Context mAppContext;
    private final OnStartDragListener mDragStartListener;
    private View.OnClickListener mItemViewOnClickListener;
    private static final float ASPECT_RATIO = 1f;
    private EventListener mEventListener;

    public QueueListAdapter(Context context, OnStartDragListener dragStartListener) {
        mAppContext = context.getApplicationContext();
        mDragStartListener = dragStartListener;
        mProvider = QueueDataProvider.getInstance(context);
        mProvider.setOnQueueDataChangedListener(new QueueDataProvider.OnQueueDataChangedListener() {
            @Override
            public void onQueueDataChanged() {
                notifyDataSetChanged();
            }
        });
        mItemViewOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view.getTag(R.string.queue_tag_item) != null) {
                    MediaQueueItem item = (MediaQueueItem) view.getTag(R.string.queue_tag_item);
                    Log.d(TAG, String.valueOf(item.getItemId()));
                }
                onItemViewClick(view);
            }
        };
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return (long) mProvider.getItem(position).getItemId();
    }

    private void onItemViewClick(View view) {
        if (mEventListener != null) {
            mEventListener.onItemViewClicked(view);
        }
    }

    @Override
    public void onItemDismiss(int position) {
        mProvider.removeFromQueue(position);
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return false;
        }
        mProvider.moveItem(fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    @Override
    public QueueItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View view = inflater.inflate(R.layout.queue_row, parent, false);
        return new QueueItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final QueueItemViewHolder holder, int position) {
        Log.d(TAG, "[upcoming] onBindViewHolder() for position: " + position);
        final MediaQueueItem item = mProvider.getItem(position);
        holder.mContainer.setTag(R.string.queue_tag_item, item);
        holder.mPlayPause.setTag(R.string.queue_tag_item, item);
        holder.mPlayUpcoming.setTag(R.string.queue_tag_item, item);
        holder.mStopUpcoming.setTag(R.string.queue_tag_item, item);

        // Set listeners
        holder.mContainer.setOnClickListener(mItemViewOnClickListener);
        holder.mPlayPause.setOnClickListener(mItemViewOnClickListener);
        holder.mPlayUpcoming.setOnClickListener(mItemViewOnClickListener);
        holder.mStopUpcoming.setOnClickListener(mItemViewOnClickListener);

        MediaInfo info = item.getMedia();
        MediaMetadata metaData = info.getMetadata();
        holder.mTitleView.setText(metaData.getString(MediaMetadata.KEY_TITLE));
        holder.mDescriptionView.setText(metaData.getString(MediaMetadata.KEY_SUBTITLE));
        AQuery aq = new AQuery(holder.itemView);
        if (!metaData.getImages().isEmpty()) {
            aq.id(holder.mImageView).width(IMAGE_THUMBNAIL_WIDTH)
                    .image(metaData.getImages().get(0).getUrl().toString(), true, true, 0,
                            R.drawable.default_video, null, 0, ASPECT_RATIO);
        }

        holder.mDragHandle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                    mDragStartListener.onStartDrag(holder);
                }
                return false;
            }
        });

        if (item == mProvider.getCurrentItem()) {
            holder.updateControlsStatus(QueueItemViewHolder.CURRENT);
            updatePlayPauseButtonImageResource(holder.mPlayPause);
        } else if (item == mProvider.getUpcomingItem()) {
            holder.updateControlsStatus(QueueItemViewHolder.UPCOMING);
        } else {
            holder.updateControlsStatus(QueueItemViewHolder.NONE);
            holder.mPlayPause.setVisibility(View.GONE);
        }

    }

    private void updatePlayPauseButtonImageResource(ImageButton button) {
        CastSession castSession = CastContext.getSharedInstance(mAppContext)
                .getSessionManager().getCurrentCastSession();
        RemoteMediaClient remoteMediaClient =
                (castSession == null) ? null : castSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            button.setVisibility(View.GONE);
            return;
        }
        int status = remoteMediaClient.getPlayerState();
        switch (status) {
            case MediaStatus.PLAYER_STATE_PLAYING:
                button.setImageResource(PAUSE_RESOURCE);
                break;
            case MediaStatus.PLAYER_STATE_PAUSED:
                button.setImageResource(PLAY_RESOURCE);
                break;
            default:
                button.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return QueueDataProvider.getInstance(mAppContext).getCount();
    }

    /* package */ static class QueueItemViewHolder extends RecyclerView.ViewHolder implements
            ItemTouchHelperViewHolder {

        private Context mContext;
        private final ImageButton mPlayPause;
        private View mControls;
        private View mUpcomingControls;
        private ImageButton mPlayUpcoming;
        private ImageButton mStopUpcoming;
        public ImageView mImageView;
        public ViewGroup mContainer;
        public ImageView mDragHandle;
        public TextView mTitleView;
        public TextView mDescriptionView;

        @Override
        public void onItemSelected() {
            // no-op
        }

        @Override
        public void onItemClear() {
            itemView.setBackgroundColor(0);
        }

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({CURRENT, UPCOMING, NONE})
        private @interface ControlStatus {

        }

        private static final int CURRENT = 0;
        private static final int UPCOMING = 1;
        private static final int NONE = 2;

        public QueueItemViewHolder(View itemView) {
            super(itemView);
            mContext = itemView.getContext();
            mContainer = (ViewGroup) itemView.findViewById(R.id.container);
            mDragHandle = (ImageView) itemView.findViewById(R.id.drag_handle);
            mTitleView = (TextView) itemView.findViewById(R.id.textView1);
            mDescriptionView = (TextView) itemView.findViewById(R.id.textView2);
            mImageView = (ImageView) itemView.findViewById(R.id.imageView1);
            mPlayPause = (ImageButton) itemView.findViewById(R.id.play_pause);
            mControls = itemView.findViewById(R.id.controls);
            mUpcomingControls = itemView.findViewById(R.id.controls_upcoming);
            mPlayUpcoming = (ImageButton) itemView.findViewById(R.id.play_upcoming);
            mStopUpcoming = (ImageButton) itemView.findViewById(R.id.stop_upcoming);
        }

        private void updateControlsStatus(@ControlStatus int status) {
            int bgResId = R.drawable.bg_item_normal_state;
            mTitleView.setTextAppearance(mContext, R.style.Base_TextAppearance_AppCompat_Subhead);
            mDescriptionView.setTextAppearance(mContext,
                    R.style.Base_TextAppearance_AppCompat_Caption);
            switch (status) {
                case CURRENT:
                    bgResId = R.drawable.bg_item_normal_state;
                    mControls.setVisibility(View.VISIBLE);
                    mPlayPause.setVisibility(View.VISIBLE);
                    mUpcomingControls.setVisibility(View.GONE);
                    mDragHandle.setImageResource(DRAG_HANDLER_DARK_RESOURCE);
                    break;
                case UPCOMING:
                    mControls.setVisibility(View.VISIBLE);
                    mPlayPause.setVisibility(View.GONE);
                    mUpcomingControls.setVisibility(View.VISIBLE);
                    mDragHandle.setImageResource(DRAG_HANDLER_LIGHT_RESOURCE);
                    bgResId = R.drawable.bg_item_upcoming_state;
                    mTitleView.setTextAppearance(mContext,
                            R.style.TextAppearance_AppCompat_Small_Inverse);
                    mTitleView.setTextAppearance(mTitleView.getContext(),
                            R.style.Base_TextAppearance_AppCompat_Subhead_Inverse);
                    mDescriptionView.setTextAppearance(mContext,
                            R.style.Base_TextAppearance_AppCompat_Caption);
                    break;
                default:
                    mControls.setVisibility(View.GONE);
                    mPlayPause.setVisibility(View.GONE);
                    mUpcomingControls.setVisibility(View.GONE);
                    mDragHandle.setImageResource(DRAG_HANDLER_DARK_RESOURCE);
                    break;
            }
            mContainer.setBackgroundResource(bgResId);
        }
    }

    public void setEventListener(EventListener eventListener) {
        mEventListener = eventListener;
    }

    /**
     * Interface for catching clicks on the ViewHolder items
     */
    public interface EventListener {

        void onItemViewClicked(View view);
    }

    /**
     * Interface to notify an item ViewHolder of relevant callbacks from {@link
     * android.support.v7.widget.helper.ItemTouchHelper.Callback}.
     */
    public interface ItemTouchHelperViewHolder {

        /**
         * Called when the {@link ItemTouchHelper} first registers an item as being moved or
         * swiped.
         * Implementations should update the item view to indicate it's active state.
         */
        void onItemSelected();


        /**
         * Called when the {@link ItemTouchHelper} has completed the move or swipe, and the active
         * item state should be cleared.
         */
        void onItemClear();
    }

    /**
     * Listener for manual initiation of a drag.
     */
    public interface OnStartDragListener {

        /**
         * Called when a view is requesting a start of a drag.
         */
        void onStartDrag(RecyclerView.ViewHolder viewHolder);

    }

}

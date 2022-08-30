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

package com.google.sample.cast.refplayer.queue.ui;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.view.MotionEventCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.MediaQueueRecyclerViewAdapter;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.queue.QueueDataProvider;
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * An adapter to show the list of queue items.
 */
public class QueueListAdapter extends MediaQueueRecyclerViewAdapter<QueueListAdapter.QueueItemViewHolder> implements QueueItemTouchHelperCallback.ItemTouchHelperAdapter {
    private static final String TAG = "QueueListAdapter";
    private static final int PLAY_RESOURCE = R.drawable.ic_play_arrow_grey600_48dp;
    private static final int PAUSE_RESOURCE = R.drawable.ic_pause_grey600_48dp;
    private static final int DRAG_HANDLER_DARK_RESOURCE = R.drawable.ic_drag_updown_grey_24dp;
    private static final int DRAG_HANDLER_LIGHT_RESOURCE = R.drawable.ic_drag_updown_white_24dp;
    private final Context mAppContext;
    private final QueueDataProvider mProvider;
    private final QueueListAdapter.OnStartDragListener mDragStartListener;
    private View.OnClickListener mItemViewOnClickListener;
    private QueueListAdapter.EventListener mEventListener;
    private ImageLoader mImageLoader;
    private final ListAdapterMediaQueueCallback myMediaQueueCallback = new ListAdapterMediaQueueCallback();
    private Executor localExecutor = Executors.newSingleThreadExecutor();

    public QueueListAdapter(@NonNull MediaQueue mediaQueue, @NonNull Context context, QueueListAdapter.OnStartDragListener dragStartListener) {
        super(mediaQueue);
        mAppContext = context.getApplicationContext();
        mProvider = QueueDataProvider.getInstance(context);
        mDragStartListener = dragStartListener;
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
        getMediaQueue().registerCallback(myMediaQueueCallback);
        setHasStableIds(false);
    }

    private void onItemViewClick(View view) {
        if (mEventListener != null) {
            mEventListener.onItemViewClicked(view);
        }
    }

    @NonNull
    @Override
    public QueueItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View view = inflater.inflate(R.layout.queue_row, parent, false);
        return new QueueListAdapter.QueueItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueItemViewHolder holder, int position) {
        Log.d(TAG, "[upcoming] onBindViewHolder() for position: " + position);
        holder.setIsRecyclable(false);
        MediaQueueItem item = this.getItem(position);
        if (item == null) {
            holder.updateControlsStatus(QueueListAdapter.QueueItemViewHolder.NONE);
        } else if (mProvider.isCurrentItem(item)) {
            holder.updateControlsStatus(QueueListAdapter.QueueItemViewHolder.CURRENT);
            updatePlayPauseButtonImageResource(holder.mPlayPause);
        } else if (mProvider.isUpcomingItem(item)) {
            holder.updateControlsStatus(QueueListAdapter.QueueItemViewHolder.UPCOMING);
        } else {
            holder.updateControlsStatus(QueueListAdapter.QueueItemViewHolder.NONE);
        }

        holder.mContainer.setTag(R.string.queue_tag_item, item);
        holder.mPlayPause.setTag(R.string.queue_tag_item, item);
        holder.mPlayUpcoming.setTag(R.string.queue_tag_item, item);
        holder.mStopUpcoming.setTag(R.string.queue_tag_item, item);

        // Set listeners
        holder.mContainer.setOnClickListener(mItemViewOnClickListener);
        holder.mPlayPause.setOnClickListener(mItemViewOnClickListener);
        holder.mPlayUpcoming.setOnClickListener(mItemViewOnClickListener);
        holder.mStopUpcoming.setOnClickListener(mItemViewOnClickListener);

        MediaInfo info = item != null ? item.getMedia() : null;
        String imageUrl = null;
        if (info != null && info.getMetadata() != null) {
            MediaMetadata metaData = info.getMetadata();
            holder.mTitleView.setText(metaData.getString(MediaMetadata.KEY_TITLE));
            holder.mDescriptionView.setText(metaData.getString(MediaMetadata.KEY_SUBTITLE));
            List<WebImage> images = metaData.getImages();
            if (images != null && !images.isEmpty()) {
                imageUrl = images.get(0).getUrl().toString();
           }
        } else {
            holder.mTitleView.setText(null);
            holder.mDescriptionView.setText(null);
        }

        if (imageUrl != null) {
            mImageLoader = CustomVolleyRequest.getInstance(mAppContext).getImageLoader();
            ImageLoader.ImageListener imageListener = new ImageLoader.ImageListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    holder.mProgressLoading.setVisibility(View.GONE);
                    holder.mImageView.setErrorImageResId(R.drawable.ic_action_alerts_and_states_warning);
                }
                @Override
                public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                    if (response.getBitmap() != null) {
                        holder.mProgressLoading.setVisibility(View.GONE);
                        holder.mImageView.setImageBitmap(response.getBitmap());
                    }
                }
            };
            holder.mImageView.setImageUrl(mImageLoader.get(imageUrl, imageListener).getRequestUrl(), mImageLoader);
        } else {
            holder.mProgressLoading.postDelayed( new Runnable() {
                @Override
                public void run() {
                    holder.mProgressLoading.setVisibility(View.GONE);
                    holder.mImageView.setDefaultImageResId(R.drawable.cast_album_art_placeholder);
                    holder.mImageView.setImageResource(R.drawable.cast_album_art_placeholder);
                }
            },3000);
        }

        holder.mDragHandle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                    mDragStartListener.onStartDrag(holder);
                } else if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_BUTTON_RELEASE) {
                    view.clearFocus();
                    view.clearAnimation();
                    holder.setIsRecyclable(false);
                    return true;
                }
                return false;
            }
        });
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
    public void dispose() {
        super.dispose();
        //unregister callback
        MediaQueue queue = getMediaQueue();
        if (queue != null) {
            queue.unregisterCallback(myMediaQueueCallback);
        }
    }

    public void setEventListener(QueueListAdapter.EventListener eventListener) {
        mEventListener = eventListener;
    }

    private void updatePlayPauseButtonImageResource(ImageButton button) {
        CastSession castSession = CastContext.getSharedInstance(mAppContext,localExecutor)
                .getResult()
                .getSessionManager()
                .getCurrentCastSession();
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

    /**
     * Handles ListAdapter notification upon MediaQueue data changes.
     * */
    class ListAdapterMediaQueueCallback extends MediaQueue.Callback{
        @Override
        public void itemsInsertedInRange(int start, int end) {
            notifyItemRangeInserted(start, end);
        }

        @Override
        public void itemsReloaded() {
            notifyDataSetChanged();
        }

        @Override
        public void itemsRemovedAtIndexes(@NonNull int[] ints) {
            for (int i : ints) {
                notifyItemRemoved(i);
            }
        }

        @Override
        public void itemsReorderedAtIndexes(@NonNull List<Integer> list, int i) {
            notifyDataSetChanged();
        }

        @Override
        public void itemsUpdatedAtIndexes(@NonNull int[] ints) {
            for (int i : ints) {
                notifyItemChanged(i);
            }
        }

        @Override
        public void mediaQueueChanged() {
            notifyDataSetChanged();
        }
    }

    static class QueueItemViewHolder extends RecyclerView.ViewHolder implements
            QueueListAdapter.ItemTouchHelperViewHolder {

        private Context mContext;
        private final ImageButton mPlayPause;
        private View mControls;
        private View mUpcomingControls;
        private ImageButton mPlayUpcoming;
        private ImageButton mStopUpcoming;
        public NetworkImageView mImageView;
        public ViewGroup mContainer;
        public ImageView mDragHandle;
        public TextView mTitleView;
        public TextView mDescriptionView;
        public ProgressBar mProgressLoading;

        @Override
        public void onItemSelected() {
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
            mImageView = (NetworkImageView) itemView.findViewById(R.id.imageView1);
            mPlayPause = (ImageButton) itemView.findViewById(R.id.play_pause);
            mControls = itemView.findViewById(R.id.controls);
            mUpcomingControls = itemView.findViewById(R.id.controls_upcoming);
            mPlayUpcoming = (ImageButton) itemView.findViewById(R.id.play_upcoming);
            mStopUpcoming = (ImageButton) itemView.findViewById(R.id.stop_upcoming);
            mProgressLoading = (ProgressBar)itemView.findViewById(R.id.item_progress);
        }

        private void updateControlsStatus(@QueueListAdapter.QueueItemViewHolder.ControlStatus int status) {
            int bgResId = R.drawable.bg_item_normal_state;
            mTitleView.setTextAppearance(mContext, R.style.Base_TextAppearance_AppCompat_Subhead);
            mDescriptionView.setTextAppearance(mContext,
                    R.style.Base_TextAppearance_AppCompat_Caption);
            Log.d(TAG,"updateControlsStatus for status = "+status);
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
            super.itemView.requestLayout();
        }
    }
    /**
     * Interface for catching clicks on the ViewHolder items
     */
    public interface EventListener {

        void onItemViewClicked(View view);
    }

    /**
     * Interface to notify an item ViewHolder of relevant callbacks from {@link
     * androidx.recyclerview.widget.ItemTouchHelper.Callback}.
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

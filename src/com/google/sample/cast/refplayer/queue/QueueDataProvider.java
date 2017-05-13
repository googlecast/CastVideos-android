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

package com.google.sample.cast.refplayer.queue;

import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import android.content.Context;
import android.util.Log;
import android.view.View;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A singleton to manage the queue. Upon instantiation, it syncs up its own copy of the queue with
 * the one that the VideoCastManager holds. After that point, it maintains an up-to-date version of
 * the queue. UI elements get their data from this class. A boolean field, {@code mDetachedQueue}
 * is used to manage whether this changes to the queue coming from the cast framework should be
 * reflected here or not; when in "detached" mode, it means that its own copy of the queue is not
 * kept up to date with the one that the cast framework has. This is needed to preserve the queue
 * when the media session ends.
 */
public class QueueDataProvider {

    private static final String TAG = "QueueDataProvider";
    public static final int INVALID = -1;
    private final Context mAppContext;
    private final List<MediaQueueItem> mQueue = new CopyOnWriteArrayList<>();
    private static QueueDataProvider mInstance;
    // Locks modification to the remove queue.
    private final Object mLock = new Object();
    private final SessionManagerListener<CastSession> mSessionManagerListener =
            new MySessionManagerListener();
    private final RemoteMediaClient.Listener mRemoteMediaClientListener =
            new MyRemoteMediaClientListener();
    private int mRepeatMode;
    private boolean mShuffle;
    private MediaQueueItem mCurrentIem;
    private MediaQueueItem mUpcomingItem;
    private OnQueueDataChangedListener mListener;
    private boolean mDetachedQueue = true;

    private QueueDataProvider(Context context) {
        mAppContext = context.getApplicationContext();
        mRepeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
        mShuffle = false;
        mCurrentIem = null;
        CastContext.getSharedInstance(mAppContext).getSessionManager().addSessionManagerListener(
                mSessionManagerListener, CastSession.class);
        syncWithRemoteQueue();
    }

    public void onUpcomingStopClicked(View view, MediaQueueItem upcomingItem) {
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }
        // need to truncate the queue on the remote device so that we can complete the playback of
        // the current item but not go any further. Alternatively, one could just stop the playback
        // here, if that was acceptable.
        int position = getPositionByItemId(upcomingItem.getItemId());
        int[] itemIds = new int[getCount() - position];
        for (int i = 0; i < itemIds.length; i++) {
            itemIds[i] = mQueue.get(i + position).getItemId();
        }
        remoteMediaClient.queueRemoveItems(itemIds, null);
    }

    public void onUpcomingPlayClicked(View view, MediaQueueItem upcomingItem) {
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }
        remoteMediaClient.queueJumpToItem(upcomingItem.getItemId(), null);
    }

    public boolean isQueueDetached() {
        return mDetachedQueue;
    }

    public int getPositionByItemId(int itemId) {
        if (mQueue.isEmpty()) {
            return INVALID;
        }
        for (int i = 0; i < mQueue.size(); i++) {
            if (mQueue.get(i).getItemId() == itemId) {
                return i;
            }
        }
        return INVALID;
    }

    public static synchronized QueueDataProvider getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new QueueDataProvider(context);
        }
        return mInstance;
    }

    public void removeFromQueue(int position) {
        synchronized (mLock) {
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient == null) {
                return;
            }
            remoteMediaClient.queueRemoveItem(mQueue.get(position).getItemId(), null);
        }
    }

    public void removeAll() {
        synchronized (mLock) {
            if (mQueue.isEmpty()) {
                return;
            }
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient == null) {
                return;
            }
            int[] itemIds = new int[mQueue.size()];
            for (int i = 0; i < mQueue.size(); i++) {
                itemIds[i] = mQueue.get(i).getItemId();
            }
            remoteMediaClient.queueRemoveItems(itemIds, null);
            mQueue.clear();
        }
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }
        int itemId = mQueue.get(fromPosition).getItemId();

        remoteMediaClient.queueMoveItemToNewIndex(itemId, toPosition, null);
        final MediaQueueItem item = mQueue.remove(fromPosition);
        mQueue.add(toPosition, item);
    }

    public int getCount() {
        return mQueue.size();
    }

    public MediaQueueItem getItem(int position) {
        return mQueue.get(position);
    }

    public void clearQueue() {
        mQueue.clear();
        mDetachedQueue = true;
        mCurrentIem = null;
    }

    public int getRepeatMode() {
        return mRepeatMode;
    }

    public boolean isShuffleOn() {
        return mShuffle;
    }

    public MediaQueueItem getCurrentItem() {
        return mCurrentIem;
    }

    public int getCurrentItemId() {
        return mCurrentIem.getItemId();
    }

    public MediaQueueItem getUpcomingItem() {
        Log.d(TAG, "[upcoming] getUpcomingItem() returning " + mUpcomingItem);
        return mUpcomingItem;
    }

    public void setOnQueueDataChangedListener(OnQueueDataChangedListener listener) {
        mListener = listener;
    }

    public List<MediaQueueItem> getItems() {
        return mQueue;
    }

    /**
     * Listener notifies the data of the queue has changed.
     */
    public interface OnQueueDataChangedListener {

        void onQueueDataChanged();
    }

    private void syncWithRemoteQueue() {
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        if (remoteMediaClient != null) {
            remoteMediaClient.addListener(mRemoteMediaClientListener);
            MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
            if (mediaStatus != null) {
                List<MediaQueueItem> items = mediaStatus.getQueueItems();
                if (items != null && !items.isEmpty()) {
                    mQueue.clear();
                    mQueue.addAll(items);
                    mRepeatMode = mediaStatus.getQueueRepeatMode();
                    mCurrentIem = mediaStatus.getQueueItemById(mediaStatus.getCurrentItemId());
                    mDetachedQueue = false;
                    mUpcomingItem = mediaStatus.getQueueItemById(mediaStatus.getPreloadedItemId());
                }
            }
        }
    }

    private class MySessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            syncWithRemoteQueue();
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            syncWithRemoteQueue();
        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            clearQueue();
            if (mListener != null) {
                mListener.onQueueDataChanged();
            }
        }

        @Override
        public void onSessionStarting(CastSession session) {
        }

        @Override
        public void onSessionStartFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionEnding(CastSession session) {
        }

        @Override
        public void onSessionResuming(CastSession session, String sessionId) {
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionSuspended(CastSession session, int reason) {
        }
    }

    private class MyRemoteMediaClientListener implements RemoteMediaClient.Listener {

        @Override
        public void onPreloadStatusUpdated() {
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient == null) {
                return;
            }
            MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
            if (mediaStatus == null) {
                return;
            }
            mUpcomingItem = mediaStatus.getQueueItemById(mediaStatus.getPreloadedItemId());
            Log.d(TAG, "onRemoteMediaPreloadStatusUpdated() with item=" + mUpcomingItem);
            if (mListener != null) {
                mListener.onQueueDataChanged();
            }
        }

        @Override
        public void onQueueStatusUpdated() {
            updateMediaQueue();
            if (mListener != null) {
                mListener.onQueueDataChanged();
            }
            Log.d(TAG, "Queue was updated");
        }

        @Override
        public void onStatusUpdated() {
            updateMediaQueue();
            if (mListener != null) {
                mListener.onQueueDataChanged();
            }
        }

        @Override
        public void onMetadataUpdated() {
        }

        @Override
        public void onSendingRemoteMediaRequest() {
        }

        @Override
        public void onAdBreakStatusUpdated() {
        }

        private void updateMediaQueue() {
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            MediaStatus mediaStatus;
            List<MediaQueueItem> queueItems = null;
            if (remoteMediaClient != null) {
                mediaStatus = remoteMediaClient.getMediaStatus();
                if (mediaStatus != null) {
                    queueItems = mediaStatus.getQueueItems();
                    mRepeatMode = mediaStatus.getQueueRepeatMode();
                    mCurrentIem = mediaStatus.getQueueItemById(mediaStatus.getCurrentItemId());
                }
            }
            mQueue.clear();
            if (queueItems == null) {
                Log.d(TAG, "Queue is cleared");
            } else {
                Log.d(TAG, "Queue is updated with a list of size: " + queueItems.size());
                if (queueItems.size() > 0) {
                    mQueue.addAll(queueItems);
                    mDetachedQueue = false;
                } else {
                    mDetachedQueue = true;
                }
            }
        }
    }

    private RemoteMediaClient getRemoteMediaClient() {
        CastSession castSession = CastContext.getSharedInstance(mAppContext).getSessionManager()
                .getCurrentCastSession();
        if (castSession == null || !castSession.isConnected()) {
            Log.w(TAG, "Trying to get a RemoteMediaClient when no CastSession is started.");
            return null;
        }
        return castSession.getRemoteMediaClient();
    }
}

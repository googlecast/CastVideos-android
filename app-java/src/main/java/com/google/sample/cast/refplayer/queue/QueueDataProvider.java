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

package com.google.sample.cast.refplayer.queue;

import android.content.Context;
import android.util.Log;
import android.view.View;

import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
    private static QueueDataProvider mInstance;
    // Locks modification to the remove queue.
    private final Object mLock = new Object();
    private final SessionManagerListener<CastSession> mSessionManagerListener =
            new MySessionManagerListener();
    private final RemoteMediaClient.Callback mRemoteMediaClientCallback =
            new MyRemoteMediaClientCallback();
    private MediaQueueItem mCurrentItem;
    private MediaQueueItem mUpcomingItem;
    private OnQueueDataChangedListener mListener;
    private boolean mDetachedQueue = true;
    private Executor localExecutor = Executors.newSingleThreadExecutor();

    private QueueDataProvider(Context context) {
        mAppContext = context.getApplicationContext();
        mCurrentItem = null;
        CastContext.getSharedInstance(mAppContext,localExecutor)
                .getResult()
                .getSessionManager()
                .addSessionManagerListener(mSessionManagerListener, CastSession.class);
        MediaQueue queue = getMediaQueue();
        if (queue != null) {
            queue.setCacheCapacity(30);
        }
        registerQueueCallbackAndUpdateQueue();
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
            itemIds[i] = getMediaQueue().getItemAtIndex(i + position).getItemId();
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

    public MediaQueue getMediaQueue() {
        MediaQueue queue = getRemoteMediaClient() == null ?
                 null :
                 getRemoteMediaClient().getMediaQueue();
        return queue;
    }

    public int getPositionByItemId(int itemId) {
        if (getMediaQueue() == null || getMediaQueue().getItemIds() == null) {
            return INVALID;
        }
        int[] ids = getMediaQueue().getItemIds();
        for (int i = 0 ; i < ids.length; i++) {
            if (ids[i] == itemId) {
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
            MediaQueue queue = getMediaQueue();
            if (queue == null) {
                return;
            }
            MediaQueueItem item = queue.getItemAtIndex(position);
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (item != null && remoteMediaClient != null) {
                getRemoteMediaClient().queueRemoveItem(item.getItemId(), null);
            }
        }
    }

    public void removeAll() {
        synchronized (mLock) {
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient == null) {
                return;
            }
            MediaQueue queue = getMediaQueue();
            if (queue == null) {
                return;
            }
            int[] ids  = queue.getItemIds();
            if (ids != null && ids.length > 0) {
                remoteMediaClient.queueRemoveItems(ids, null);
            }
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
        MediaQueue queue = getMediaQueue();
        if (queue == null) {
            return;
        }
        int itemId = queue.itemIdAtIndex(fromPosition);
        remoteMediaClient.queueMoveItemToNewIndex(itemId, toPosition, null);
    }

    public int getCount() {
        MediaQueue queue = getMediaQueue();
        if (queue == null) {
            return 0;
        }
        return queue.getItemCount();
    }

    public MediaQueueItem getItem(int position) {
        MediaQueue queue = getMediaQueue();
        if (queue == null) {
            return null;
        }
        return queue.getItemAtIndex(position,true);
    }

    public void destroyQueue() {
        removeAll();
        mDetachedQueue = true;
        mCurrentItem = null;
        mUpcomingItem = null;
    }

    public boolean isCurrentItem(MediaQueueItem item) {
        if (item != null
                && mCurrentItem != null
                && (item == mCurrentItem
                || item.getItemId() == mCurrentItem.getItemId())) {
            return true;
        }

        return false;
    }

    public int getCurrentItemId() {
        return mCurrentItem == null ? -1 : mCurrentItem.getItemId();
    }

    public MediaQueueItem getUpcomingItem() {
        return mUpcomingItem;
    }

    public boolean isUpcomingItem(MediaQueueItem item) {
        if (item != null
                && mUpcomingItem != null
                && (item == mUpcomingItem
                    || item.getItemId() == mUpcomingItem.getItemId())) {
            return true;
        }

        return false;
    }

    public void setOnQueueDataChangedListener(OnQueueDataChangedListener listener) {
        mListener = listener;
    }

    /**
     * Listener notifies the data of the queue has changed.
     */
    public interface OnQueueDataChangedListener {

        void onQueueDataChanged();
    }

    private void registerQueueCallbackAndUpdateQueue() {
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        if (remoteMediaClient != null) {
            remoteMediaClient.registerCallback(mRemoteMediaClientCallback);
            updateMediaQueue();
        }
    }

    private void updateMediaQueue() {
        Log.d(TAG, "updateMediaQueue ");
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        mDetachedQueue = true;
        if (remoteMediaClient != null) {
            mCurrentItem = remoteMediaClient.getCurrentItem();
            mUpcomingItem = remoteMediaClient.getPreloadedItem();
            mDetachedQueue = false;
            Log.d(TAG, "updateMediaQueue() with mCurrentItem=" + mCurrentItem);
            Log.d(TAG, "updateMediaQueue() with mUpcomingItem=" + mUpcomingItem);
        }
    }

    private RemoteMediaClient getRemoteMediaClient() {
        CastSession castSession = CastContext.getSharedInstance(mAppContext,localExecutor)
                .getResult()
                .getSessionManager()
                .getCurrentCastSession();
        if (castSession == null || !castSession.isConnected()) {
            Log.w(TAG, "Trying to get a RemoteMediaClient when no CastSession is started.");
            return null;
        }
        return castSession.getRemoteMediaClient();
    }

    private class MySessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            registerQueueCallbackAndUpdateQueue();
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            registerQueueCallbackAndUpdateQueue();
        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            destroyQueue();
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

    private class MyRemoteMediaClientCallback extends RemoteMediaClient.Callback {
        
        @Override
        public void onPreloadStatusUpdated() {
            updateMediaQueue();
            if (mListener != null) {
                mListener.onQueueDataChanged();
            }
            Log.d(TAG, "onPreloadStatusUpdated Queue was updated");
        }

        @Override
        public void onQueueStatusUpdated() {
            updateMediaQueue();
            if (mListener != null) {
                mListener.onQueueDataChanged();
            }
            Log.d(TAG, "onQueueStatusUpdated Queue was updated");
        }

        @Override
        public void onStatusUpdated() {
            updateMediaQueue();
            if (mListener != null) {
                mListener.onQueueDataChanged();
            }
            Log.d(TAG, "onStatusUpdated Queue was updated");
        }
    }
}

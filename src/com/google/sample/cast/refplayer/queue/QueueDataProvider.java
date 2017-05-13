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
 * the queue. UI elements get their data from this class. A boolean field, {@code detachedQueue}
 * is used to manage whether this changes to the queue coming from the cast framework should be
 * reflected here or not; when in "detached" mode, it means that its own copy of the queue is not
 * kept up to date with the one that the cast framework has. This is needed to preserve the queue
 * when the media session ends.
 */
public class QueueDataProvider {

    private static final String TAG = "QueueDataProvider";
    public static final int INVALID = -1;
    private final Context appContext;
    private final List<MediaQueueItem> queue = new CopyOnWriteArrayList<>();
    private static QueueDataProvider instance;
    // Locks modification to the remove queue.
    private final Object lock = new Object();
    private final SessionManagerListener<CastSession> sessionManagerListener =
            new MySessionManagerListener();
    private final RemoteMediaClient.Listener remoteMediaClientListener =
            new MyRemoteMediaClientListener();
    private int repeatMode;
    private boolean shuffle;
    private MediaQueueItem currentIem;
    private MediaQueueItem upcomingItem;
    private OnQueueDataChangedListener listener;
    private boolean detachedQueue = true;

    private QueueDataProvider(Context context) {
        appContext = context.getApplicationContext();
        repeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
        shuffle = false;
        currentIem = null;
        CastContext.getSharedInstance(appContext).getSessionManager().addSessionManagerListener(
                sessionManagerListener, CastSession.class);
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
            itemIds[i] = queue.get(i + position).getItemId();
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
        return detachedQueue;
    }

    public int getPositionByItemId(int itemId) {
        if (queue.isEmpty()) {
            return INVALID;
        }
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getItemId() == itemId) {
                return i;
            }
        }
        return INVALID;
    }

    public static synchronized QueueDataProvider getInstance(Context context) {
        if (instance == null) {
            instance = new QueueDataProvider(context);
        }
        return instance;
    }

    public void removeFromQueue(int position) {
        synchronized (lock) {
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient == null) {
                return;
            }
            remoteMediaClient.queueRemoveItem(queue.get(position).getItemId(), null);
        }
    }

    public void removeAll() {
        synchronized (lock) {
            if (queue.isEmpty()) {
                return;
            }
            RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient == null) {
                return;
            }
            int[] itemIds = new int[queue.size()];
            for (int i = 0; i < queue.size(); i++) {
                itemIds[i] = queue.get(i).getItemId();
            }
            remoteMediaClient.queueRemoveItems(itemIds, null);
            queue.clear();
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
        int itemId = queue.get(fromPosition).getItemId();

        remoteMediaClient.queueMoveItemToNewIndex(itemId, toPosition, null);
        final MediaQueueItem item = queue.remove(fromPosition);
        queue.add(toPosition, item);
    }

    public int getCount() {
        return queue.size();
    }

    public MediaQueueItem getItem(int position) {
        return queue.get(position);
    }

    public void clearQueue() {
        queue.clear();
        detachedQueue = true;
        currentIem = null;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public boolean isShuffleOn() {
        return shuffle;
    }

    public MediaQueueItem getCurrentItem() {
        return currentIem;
    }

    public int getCurrentItemId() {
        return currentIem.getItemId();
    }

    public MediaQueueItem getUpcomingItem() {
        Log.d(TAG, "[upcoming] getUpcomingItem() returning " + upcomingItem);
        return upcomingItem;
    }

    public void setOnQueueDataChangedListener(OnQueueDataChangedListener listener) {
        this.listener = listener;
    }

    public List<MediaQueueItem> getItems() {
        return queue;
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
            remoteMediaClient.addListener(remoteMediaClientListener);
            MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
            if (mediaStatus != null) {
                List<MediaQueueItem> items = mediaStatus.getQueueItems();
                if (items != null && !items.isEmpty()) {
                    queue.clear();
                    queue.addAll(items);
                    repeatMode = mediaStatus.getQueueRepeatMode();
                    currentIem = mediaStatus.getQueueItemById(mediaStatus.getCurrentItemId());
                    detachedQueue = false;
                    upcomingItem = mediaStatus.getQueueItemById(mediaStatus.getPreloadedItemId());
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
            if (listener != null) {
                listener.onQueueDataChanged();
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
            upcomingItem = mediaStatus.getQueueItemById(mediaStatus.getPreloadedItemId());
            Log.d(TAG, "onRemoteMediaPreloadStatusUpdated() with item=" + upcomingItem);
            if (listener != null) {
                listener.onQueueDataChanged();
            }
        }

        @Override
        public void onQueueStatusUpdated() {
            updateMediaQueue();
            if (listener != null) {
                listener.onQueueDataChanged();
            }
            Log.d(TAG, "Queue was updated");
        }

        @Override
        public void onStatusUpdated() {
            updateMediaQueue();
            if (listener != null) {
                listener.onQueueDataChanged();
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
                    repeatMode = mediaStatus.getQueueRepeatMode();
                    currentIem = mediaStatus.getQueueItemById(mediaStatus.getCurrentItemId());
                }
            }
            queue.clear();
            if (queueItems == null) {
                Log.d(TAG, "Queue is cleared");
            } else {
                Log.d(TAG, "Queue is updated with a list of size: " + queueItems.size());
                if (queueItems.size() > 0) {
                    queue.addAll(queueItems);
                    detachedQueue = false;
                } else {
                    detachedQueue = true;
                }
            }
        }
    }

    private RemoteMediaClient getRemoteMediaClient() {
        CastSession castSession = CastContext.getSharedInstance(appContext).getSessionManager()
                .getCurrentCastSession();
        if (castSession == null || !castSession.isConnected()) {
            Log.w(TAG, "Trying to get a RemoteMediaClient when no CastSession is started.");
            return null;
        }
        return castSession.getRemoteMediaClient();
    }
}

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
package com.google.sample.cast.refplayer.queue

import android.util.Log
import android.content.Context
import android.view.View
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.framework.media.MediaQueue
import com.google.android.gms.cast.MediaQueueItem
import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.jvm.Synchronized

/**
 * A singleton to manage the queue. Upon instantiation, it syncs up its own copy of the queue with
 * the one that the VideoCastManager holds. After that point, it maintains an up-to-date version of
 * the queue. UI elements get their data from this class. A boolean field, `mDetachedQueue`
 * is used to manage whether this changes to the queue coming from the cast framework should be
 * reflected here or not; when in "detached" mode, it means that its own copy of the queue is not
 * kept up to date with the one that the cast framework has. This is needed to preserve the queue
 * when the media session ends.
 */
class QueueDataProvider private constructor(context: Context?) {
    private val mAppContext: Context
    private val  castExecutor: Executor = Executors.newSingleThreadExecutor();
    // Locks modification to the remove queue.
    private val mLock: Any = Any()
    private val mSessionManagerListener: SessionManagerListener<CastSession> =
        MySessionManagerListener()
    private val mRemoteMediaClientCallback: RemoteMediaClient.Callback =
        MyRemoteMediaClientCallback()
    private var mCurrentItem: MediaQueueItem?
    var upcomingItem: MediaQueueItem? = null
        private set
    private var mListener: OnQueueDataChangedListener? = null
    var isQueueDetached: Boolean = true
        private set

    init {
        mAppContext = context!!.getApplicationContext()
        mCurrentItem = null
        CastContext.getSharedInstance(
            mAppContext,
            castExecutor
        ).result?.sessionManager?.addSessionManagerListener(
            mSessionManagerListener, CastSession::class.java
        )
        mediaQueue?.setCacheCapacity(30)
        registerQueueCallbackAndUpdateQueue()
    }

    fun onUpcomingStopClicked(view: View?, upcomingItem: MediaQueueItem) {
        val remoteMediaClient: RemoteMediaClient = remoteMediaClient ?: return
        // need to truncate the queue on the remote device so that we can complete the playback of
        // the current item but not go any further. Alternatively, one could just stop the playback
        // here, if that was acceptable.
        val position: Int = getPositionByItemId(upcomingItem.itemId)
        val itemIds: IntArray = IntArray(count - position)
        for ( i in itemIds.indices) {
            itemIds[i]  = mediaQueue!!.getItemAtIndex(i + position)!!.itemId
        }
        remoteMediaClient.queueRemoveItems(itemIds, JSONObject() )
    }

    fun onUpcomingPlayClicked(view: View?, upcomingItem: MediaQueueItem) {
        val remoteMediaClient: RemoteMediaClient = remoteMediaClient ?: return
        remoteMediaClient.queueJumpToItem(upcomingItem.itemId, JSONObject() )
    }

    val mediaQueue: MediaQueue?
        get() {
            val queue: MediaQueue? =
                if (remoteMediaClient == null) null else remoteMediaClient!!.getMediaQueue()
            return queue
        }

    fun getPositionByItemId(itemId: Int): Int {
        if (mediaQueue == null || mediaQueue?.itemIds == null) {
            return INVALID
        }
        val ids: IntArray = mediaQueue!!.itemIds
        for (i in ids.indices) {
            if (ids.get(i) == itemId) {
                return i
            }
        }
        return INVALID
    }

    fun removeFromQueue(position: Int) {
        synchronized(mLock) {
            val queue: MediaQueue = mediaQueue ?: return
            val item: MediaQueueItem? = queue.getItemAtIndex(position)
            val remoteMediaClient: RemoteMediaClient? = remoteMediaClient
            if (item != null && remoteMediaClient != null) {
                remoteMediaClient.queueRemoveItem(item.itemId, JSONObject())
            }
        }
    }

    fun removeAll() {
        synchronized(mLock) {
            val remoteMediaClient: RemoteMediaClient = remoteMediaClient ?: return
            val queue: MediaQueue = mediaQueue ?: return
            val ids: IntArray = queue.itemIds
            if (ids.isNotEmpty()) {
                remoteMediaClient.queueRemoveItems(ids, JSONObject())
            }
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) {
            return
        }
        val remoteMediaClient: RemoteMediaClient = remoteMediaClient ?: return
        val queue: MediaQueue = mediaQueue ?: return
        val itemId: Int = queue.itemIdAtIndex(fromPosition)
        remoteMediaClient.queueMoveItemToNewIndex(itemId, toPosition, JSONObject()  )
    }

    val count: Int
        get() {
            val queue: MediaQueue = mediaQueue ?: return 0
            return queue.itemCount
        }

    fun getItem(position: Int): MediaQueueItem? {
        val queue: MediaQueue = mediaQueue ?: return null
        return queue.getItemAtIndex(position, true)
    }

    fun destroyQueue() {
        removeAll()
        isQueueDetached = true
        mCurrentItem = null
        upcomingItem = null
    }

    fun isCurrentItem(item: MediaQueueItem?): Boolean {
        if ((item != null
                    ) && (mCurrentItem != null
                    ) && ((item === mCurrentItem
                    || item.itemId == mCurrentItem!!.itemId))
        ) {
            return true
        }
        return false
    }

    val currentItemId: Int
        get() {
            return if (mCurrentItem == null) -1 else mCurrentItem!!.itemId
        }

    fun isUpcomingItem(item: MediaQueueItem?): Boolean {
        if ((item != null
                    ) && (upcomingItem != null
                    ) && ((item === upcomingItem
                    || item.itemId == upcomingItem!!.itemId))
        ) {
            return true
        }
        return false
    }

    fun setOnQueueDataChangedListener(listener: OnQueueDataChangedListener?) {
        mListener = listener
    }

    /**
     * Listener notifies the data of the queue has changed.
     */
    open interface OnQueueDataChangedListener {
        fun onQueueDataChanged()
    }

    private fun registerQueueCallbackAndUpdateQueue() {
        val remoteMediaClient: RemoteMediaClient? = remoteMediaClient
        if (remoteMediaClient != null) {
            remoteMediaClient.registerCallback(mRemoteMediaClientCallback)
            updateMediaQueue()
        }
    }

    private fun updateMediaQueue() {
        Log.d(TAG, "updateMediaQueue ")
        val remoteMediaClient: RemoteMediaClient? = remoteMediaClient
        isQueueDetached = true
        if (remoteMediaClient != null) {
            mCurrentItem = remoteMediaClient.currentItem
            upcomingItem = remoteMediaClient.preloadedItem
            isQueueDetached = false
            Log.d(TAG, "updateMediaQueue() with mCurrentItem=" + mCurrentItem)
            Log.d(TAG, "updateMediaQueue() with mUpcomingItem=" + upcomingItem)
        }
    }

    private val remoteMediaClient: RemoteMediaClient?
        private get() {
            val castSession: CastSession? =
                CastContext.getSharedInstance(mAppContext,castExecutor)
                    .result
                    .sessionManager
                    .currentCastSession
            if (castSession == null || !castSession.isConnected) {
                Log.w(TAG, "Trying to get a RemoteMediaClient when no CastSession is started.")
                return null
            }
            return castSession.remoteMediaClient
        }

    private inner class MySessionManagerListener constructor() :
        SessionManagerListener<CastSession> {
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            registerQueueCallbackAndUpdateQueue()
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            registerQueueCallbackAndUpdateQueue()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            destroyQueue()
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private inner class MyRemoteMediaClientCallback constructor() : RemoteMediaClient.Callback() {
        override fun onPreloadStatusUpdated() {
            updateMediaQueue()
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
            Log.d(TAG, "onPreloadStatusUpdated Queue was updated")
        }

        override fun onQueueStatusUpdated() {
            updateMediaQueue()
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
            Log.d(TAG, "onQueueStatusUpdated Queue was updated")
        }

        override fun onStatusUpdated() {
            updateMediaQueue()
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
            Log.d(TAG, "onStatusUpdated Queue was updated")
        }
    }

    companion object {
        private val TAG: String = "QueueDataProvider"
        val INVALID: Int = -1
        private var mInstance: QueueDataProvider? = null
        @Synchronized
        fun getInstance(context: Context?): QueueDataProvider? {
            if (mInstance == null) {
                mInstance = QueueDataProvider(context)
            }
            return mInstance
        }
    }
}
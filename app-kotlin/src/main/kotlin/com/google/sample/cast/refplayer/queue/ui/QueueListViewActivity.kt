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

import android.util.Log
import android.view.View
import com.google.sample.cast.refplayer.R
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.SessionManagerListener
import android.os.Bundle
import android.content.Intent
import android.view.Menu
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import android.view.KeyEvent
import androidx.appcompat.widget.Toolbar
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import com.google.sample.cast.refplayer.settings.CastPreference
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * An activity to show the queue list
 */
class QueueListViewActivity : AppCompatActivity() {
    private val mRemoteMediaClientCallback: RemoteMediaClient.Callback =
        MyRemoteMediaClientCallback()
    private val mSessionManagerListener: SessionManagerListener<CastSession> =
        MySessionManagerListener()
    private var mCastContext: CastContext? = null
    private val  castExecutor: Executor = Executors.newSingleThreadExecutor();
    private var mRemoteMediaClient: RemoteMediaClient? = null
    private var mEmptyView: View? = null

    private inner class MySessionManagerListener : SessionManagerListener<CastSession> {
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient!!.unregisterCallback(mRemoteMediaClientCallback)
            }
            mRemoteMediaClient = null
            mEmptyView!!.visibility = View.VISIBLE
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            mRemoteMediaClient = remoteMediaClient
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient!!.registerCallback(mRemoteMediaClientCallback)
            }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            mRemoteMediaClient = remoteMediaClient
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient!!.registerCallback(mRemoteMediaClientCallback)
            }
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient!!.unregisterCallback(mRemoteMediaClientCallback)
            }
            mRemoteMediaClient = null
        }
    }

    private inner class MyRemoteMediaClientCallback : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            updateMediaQueue()
        }

        override fun onQueueStatusUpdated() {
            updateMediaQueue()
        }

        private fun updateMediaQueue() {
            val mediaStatus = mRemoteMediaClient!!.mediaStatus
            val queueItems = mediaStatus?.queueItems
            if (queueItems == null || queueItems.isEmpty()) {
                mEmptyView!!.visibility = View.VISIBLE
            } else {
                mEmptyView!!.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.queue_activity)
        Log.d(TAG, "onCreate() was called")
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.container, QueueListViewFragment(), FRAGMENT_LIST_VIEW)
                .commit()
        }
        setupActionBar()
        mEmptyView = findViewById(R.id.empty)
        mCastContext = CastContext.getSharedInstance(this,castExecutor).result
    }

    private fun setupActionBar() {
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        toolbar.setTitle(R.string.queue_list)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    override fun onPause() {
        if (mRemoteMediaClient != null) {
            mRemoteMediaClient!!.unregisterCallback(mRemoteMediaClientCallback)
        }
        mCastContext!!.sessionManager.removeSessionManagerListener(
            mSessionManagerListener, CastSession::class.java
        )
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.queue_menu, menu)
        CastButtonFactory.setUpMediaRouteButton(
            applicationContext, menu,
            R.id.media_route_menu_item
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(
                Intent(
                    this@QueueListViewActivity,
                    CastPreference::class.java
                )
            )
            R.id.action_clear_queue -> QueueDataProvider.Companion.getInstance(
                applicationContext
            )!!.removeAll()
            android.R.id.home -> finish()
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return (mCastContext!!.onDispatchVolumeKeyEventBeforeJellyBean(event)
                || super.dispatchKeyEvent(event))
    }

    override fun onResume() {
        mCastContext!!.sessionManager.addSessionManagerListener(
            mSessionManagerListener, CastSession::class.java
        )
        if (mRemoteMediaClient == null) {
            mRemoteMediaClient = remoteMediaClient
        }
        if (mRemoteMediaClient != null) {
            mRemoteMediaClient!!.registerCallback(mRemoteMediaClientCallback)
            val mediaStatus = mRemoteMediaClient!!.mediaStatus
            val queueItems = mediaStatus?.queueItems
            if (queueItems != null && queueItems.isNotEmpty()) {
                mEmptyView!!.visibility = View.GONE
            }
        }
        super.onResume()
    }

    private val remoteMediaClient: RemoteMediaClient?
        private get() {
            val castSession = mCastContext!!.sessionManager.currentCastSession
            return if (castSession != null && castSession.isConnected) castSession.remoteMediaClient else null
        }

    companion object {
        private const val FRAGMENT_LIST_VIEW = "list view"
        private const val TAG = "QueueListViewActivity"
    }
}
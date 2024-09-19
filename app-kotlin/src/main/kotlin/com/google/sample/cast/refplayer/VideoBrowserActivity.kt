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
package com.google.sample.cast.refplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.IntroductoryOverlay
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity
import com.google.sample.cast.refplayer.settings.CastPreference
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The main activity that displays the list of videos.
 */
class VideoBrowserActivity : AppCompatActivity() {
    private var mCastContext: CastContext? = null
    private val mSessionManagerListener: SessionManagerListener<CastSession> = MySessionManagerListener()
    private var mCastSession: CastSession? = null
    private var mediaRouteMenuItem: MenuItem? = null
    private var mQueueMenuItem: MenuItem? = null
    private var mToolbar: Toolbar? = null
    private var mIntroductoryOverlay: IntroductoryOverlay? = null
    private var mCastStateListener: CastStateListener? = null
    private val  castExecutor: Executor = Executors.newSingleThreadExecutor();
    private var hasValidCastContextOnResume = false

    private inner class MySessionManagerListener : SessionManagerListener<CastSession> {
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (session === mCastSession) {
                mCastSession = null
            }
            invalidateOptionsMenu()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            mCastSession = session
            invalidateOptionsMenu()
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            mCastSession = session
            invalidateOptionsMenu()
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_browser)
        setupActionBar()
        mCastStateListener = CastStateListener { newState ->
            if (newState != CastState.NO_DEVICES_AVAILABLE) {
                showIntroductoryOverlay()
            }
        }
        val castContextTask = CastContext.getSharedInstance(this, castExecutor)
        castContextTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Task completed successfully
                onCastContextInitialized(task.result!!)
            } else {
                // Task failed with an exception
                Log.e(
                    TAG,
                    "fail to initialize CastContext"
                )
            }
        }
    }

    @Synchronized
    private fun onCastContextInitialized(castContext: CastContext) {
        Log.i(TAG, "CastContext is initialized and used for onCreate")
        mCastContext = castContext
        mCastContext!!.sessionManager.addSessionManagerListener(
            mSessionManagerListener, CastSession::class.java
        )
        mCastContext!!.addSessionTransferCallback(
            CastSessionTransferCallback(applicationContext)
        )
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !hasValidCastContextOnResume) {
            // If the activity's lifecycle is equal to or greater than the onResumed state but the
            // CastContext was not initialized when the onResume() is called, we need to execute the
            // method "onResumeWithCastContext" which was skipped previously in the onResume().
            onResumeWithCastContext(castContext)
        }
    }

    @Synchronized
    private fun onResumeWithCastContext(castContext: CastContext) {
        castContext!!.addCastStateListener(mCastStateListener!!)
        intentToJoin()
        if (mCastSession == null) {
            mCastSession = castContext!!.sessionManager.currentCastSession
        }
        if (mQueueMenuItem != null) {
            mQueueMenuItem!!.isVisible = mCastSession != null && mCastSession!!.isConnected
        }
        hasValidCastContextOnResume = true
    }

    private fun setupActionBar() {
        mToolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(mToolbar)
    }

    private fun intentToJoin() {
        val intent = intent
        val intentToJoinUri = Uri.parse("https://castvideos.com/cast/join")
        Log.i(TAG, "URI passed: $intentToJoinUri")
        if (intent.data != null && intent.data == intentToJoinUri) {
            mCastContext!!.sessionManager.startSession(intent)
            Log.i(TAG, "Uri Joined: $intentToJoinUri")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.browse, menu)
        mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(
            applicationContext, menu,
            R.id.media_route_menu_item
        )
        mQueueMenuItem = menu.findItem(R.id.action_show_queue)
        showIntroductoryOverlay()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_show_queue).isVisible =
            mCastSession != null && mCastSession!!.isConnected
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val intent: Intent
        if (item.itemId == R.id.action_settings) {
            intent = Intent(this@VideoBrowserActivity, CastPreference::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.action_show_queue) {
            intent = Intent(this@VideoBrowserActivity, QueueListViewActivity::class.java)
            startActivity(intent)
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return (mCastContext!!.onDispatchVolumeKeyEventBeforeJellyBean(event)
                || super.dispatchKeyEvent(event))
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() is called")
        if (mCastContext != null) {
            onResumeWithCastContext(mCastContext!!)
        }
    }

    override fun onPause() {
        mCastContext!!.removeCastStateListener(mCastStateListener!!)
        mCastContext!!.sessionManager.removeSessionManagerListener(
            mSessionManagerListener, CastSession::class.java
        )
        super.onPause()
    }

    private fun showIntroductoryOverlay() {
        if (mIntroductoryOverlay != null) {
            mIntroductoryOverlay!!.remove()
        }
        if (mediaRouteMenuItem != null && mediaRouteMenuItem!!.isVisible) {
            Handler(Looper.getMainLooper()).post {
                mIntroductoryOverlay = IntroductoryOverlay.Builder(
                    this@VideoBrowserActivity, mediaRouteMenuItem!!
                )
                    .setTitleText(getString(R.string.introducing_cast))
                    .setOverlayColor(R.color.primary)
                    .setSingleTime()
                    .setOnOverlayDismissedListener { mIntroductoryOverlay = null }
                    .build()
                mIntroductoryOverlay!!.show()
            }
        }
    }

    companion object {
        private const val TAG = "VideoBrowserActivity"
    }
}
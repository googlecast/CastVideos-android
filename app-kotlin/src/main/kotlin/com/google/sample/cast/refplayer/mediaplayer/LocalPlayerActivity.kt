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
package com.google.sample.cast.refplayer.mediaplayer

import android.util.Log
import android.view.View
import com.google.sample.cast.refplayer.R
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import com.android.volley.toolbox.NetworkImageView
import android.widget.TextView
import com.android.volley.toolbox.ImageLoader
import android.widget.ImageView
import com.google.android.gms.cast.framework.SessionManagerListener
import android.os.Bundle
import android.widget.ImageButton
import android.content.Intent
import androidx.core.app.ActivityCompat
import android.view.Menu
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.appcompat.app.AppCompatActivity
import android.widget.VideoView
import android.widget.SeekBar
import android.widget.ProgressBar
import java.util.Timer
import android.view.MenuItem
import com.google.android.gms.cast.framework.media.MediaUtils
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import android.view.KeyEvent
import java.util.TimerTask
import android.media.MediaPlayer
import android.widget.SeekBar.OnSeekBarChangeListener
import android.annotation.SuppressLint
import android.view.WindowManager
import android.os.Build
import android.widget.RelativeLayout
import android.text.method.ScrollingMovementMethod
import androidx.core.view.ViewCompat
import android.content.res.Configuration
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.cast.*
import com.google.sample.cast.refplayer.browser.VideoProvider
import com.google.sample.cast.refplayer.expandedcontrols.ExpandedControlsActivity
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity
import com.google.sample.cast.refplayer.settings.CastPreference
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest
import com.google.sample.cast.refplayer.utils.Utils
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Activity for the local media player.
 */
class LocalPlayerActivity : AppCompatActivity() {
    private var mVideoView: VideoView? = null
    private var mTitleView: TextView? = null
    private var mDescriptionView: TextView? = null
    private var mStartText: TextView? = null
    private var mEndText: TextView? = null
    private var mSeekbar: SeekBar? = null
    private var mPlayPause: ImageView? = null
    private var mLoading: ProgressBar? = null
    private var mControllers: View? = null
    private var mContainer: View? = null
    private var mCoverArt: NetworkImageView? = null
    private var mSeekbarTimer: Timer? = null
    private var mControllersTimer: Timer? = null
    private var mLocation: PlaybackLocation? = null
    private var mPlaybackState: PlaybackState? = null
    private val mHandler = Handler(Looper.getMainLooper())
    private val mAspectRatio = 72f / 128
    private var mSelectedMedia: MediaInfo? = null
    private var mControllersVisible = false
    private var mDuration = 0
    private var mAuthorView: TextView? = null
    private var mPlayCircle: ImageButton? = null
    private var mCastContext: CastContext? = null
    private var mCastSession: CastSession? = null
    private var mSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var mQueueMenuItem: MenuItem? = null
    private var mImageLoader: ImageLoader? = null
    private val  localExecutor: Executor = Executors.newSingleThreadExecutor();


    /**
     * indicates whether we are doing a local or a remote playback
     */
    enum class PlaybackLocation {
        LOCAL, REMOTE
    }

    /**
     * List of various states that we can be in
     */
    enum class PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)
        loadViews()
        setupControlsCallbacks()
        setupCastListener()
        mCastContext = CastContext.getSharedInstance(this,localExecutor).result
        mCastSession = mCastContext!!.sessionManager.currentCastSession
        // see what we need to play and where
        val bundle = intent.extras
        if (bundle != null) {
            mSelectedMedia = intent.getParcelableExtra("media")
            setupActionBar()
            val shouldStartPlayback = bundle.getBoolean("shouldStart")
            val startPosition = bundle.getInt("startPosition", 0)
            mVideoView!!.setVideoURI(Uri.parse(mSelectedMedia!!.contentId))
            Log.d(TAG, "Setting url of the VideoView to: " + mSelectedMedia!!.contentId)
            if (shouldStartPlayback) {
                // this will be the case only if we are coming from the
                // CastControllerActivity by disconnecting from a device
                mPlaybackState = PlaybackState.PLAYING
                updatePlaybackLocation(PlaybackLocation.LOCAL)
                updatePlayButton(mPlaybackState)
                if (startPosition > 0) {
                    mVideoView!!.seekTo(startPosition)
                }
                mVideoView!!.start()
                startControllersTimer()
            } else {
                // we should load the video but pause it
                // and show the album art.
                if (mCastSession != null && mCastSession!!.isConnected) {
                    updatePlaybackLocation(PlaybackLocation.REMOTE)
                } else {
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }
                mPlaybackState = PlaybackState.IDLE
                updatePlayButton(mPlaybackState)
            }
        }
        if (mTitleView != null) {
            updateMetadata(true)
        }
    }

    private fun setupCastListener() {
        mSessionManagerListener = object : SessionManagerListener<CastSession> {
            override fun onSessionEnded(session: CastSession, error: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                onApplicationConnected(session)
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                onApplicationConnected(session)
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
            private fun onApplicationConnected(castSession: CastSession) {
                mCastSession = castSession
                if (null != mSelectedMedia) {
                    if (mPlaybackState == PlaybackState.PLAYING) {
                        mVideoView!!.pause()
                        loadRemoteMedia(mSeekbar!!.progress, true)
                        return
                    } else {
                        mPlaybackState = PlaybackState.IDLE
                        updatePlaybackLocation(PlaybackLocation.REMOTE)
                    }
                }
                updatePlayButton(mPlaybackState)
                invalidateOptionsMenu()
            }

            private fun onApplicationDisconnected() {
                updatePlaybackLocation(PlaybackLocation.LOCAL)
                mPlaybackState = PlaybackState.IDLE
                mLocation = PlaybackLocation.LOCAL
                updatePlayButton(mPlaybackState)
                invalidateOptionsMenu()
            }
        }
    }

    private fun updatePlaybackLocation(location: PlaybackLocation) {
        mLocation = location
        if (location == PlaybackLocation.LOCAL) {
            if (mPlaybackState == PlaybackState.PLAYING
                || mPlaybackState == PlaybackState.BUFFERING
            ) {
                setCoverArtStatus(null)
                startControllersTimer()
            } else {
                stopControllersTimer()
                setCoverArtStatus(MediaUtils.getImageUrl(mSelectedMedia, 0))
            }
        } else {
            stopControllersTimer()
            setCoverArtStatus(MediaUtils.getImageUrl(mSelectedMedia, 0))
            updateControllersVisibility(false)
        }
    }

    private fun play(position: Int) {
        startControllersTimer()
        when (mLocation) {
            PlaybackLocation.LOCAL -> {
                mVideoView!!.seekTo(position)
                mVideoView!!.start()
            }
            PlaybackLocation.REMOTE -> {
                mPlaybackState = PlaybackState.BUFFERING
                updatePlayButton(mPlaybackState)
                mCastSession!!.remoteMediaClient!!.seek(MediaSeekOptions
                    .Builder()
                    .setPosition(position.toLong())
                    .build())
            }
            else -> {}
        }
        restartTrickplayTimer()
    }

    private fun togglePlayback() {
        stopControllersTimer()
        when (mPlaybackState) {
            PlaybackState.PAUSED -> when (mLocation) {
                PlaybackLocation.LOCAL -> {
                    mVideoView!!.start()
                    Log.d(TAG, "Playing locally...")
                    mPlaybackState = PlaybackState.PLAYING
                    startControllersTimer()
                    restartTrickplayTimer()
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }
                PlaybackLocation.REMOTE -> {
                    loadRemoteMedia(0, true)
                    finish()
                }
                else -> {}
            }
            PlaybackState.PLAYING -> {
                mPlaybackState = PlaybackState.PAUSED
                mVideoView!!.pause()
            }
            PlaybackState.IDLE -> when (mLocation) {
                PlaybackLocation.LOCAL -> {
                    mVideoView!!.setVideoURI(Uri.parse(mSelectedMedia!!.contentId))
                    mVideoView!!.seekTo(0)
                    mVideoView!!.start()
                    mPlaybackState = PlaybackState.PLAYING
                    restartTrickplayTimer()
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }
                PlaybackLocation.REMOTE -> if (mCastSession != null && mCastSession!!.isConnected) {
                    Utils.showQueuePopup(this, mPlayCircle, mSelectedMedia)
                }
                else -> {}
            }
            else -> {}
        }
        updatePlayButton(mPlaybackState)
    }

    private fun loadRemoteMedia(position: Int, autoPlay: Boolean) {
        if (mCastSession == null) {
            return
        }
        val remoteMediaClient = mCastSession!!.remoteMediaClient ?: return
        remoteMediaClient.registerCallback(object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val intent = Intent(this@LocalPlayerActivity, ExpandedControlsActivity::class.java)
                startActivity(intent)
                remoteMediaClient.unregisterCallback(this)
            }
        })
        remoteMediaClient.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(mSelectedMedia)
                .setAutoplay(autoPlay)
                .setCurrentTime(position.toLong()).build()
        )
    }

    private fun setCoverArtStatus(url: String?) {
        if (url != null) {
            mImageLoader = CustomVolleyRequest.Companion.getInstance(this.applicationContext)?.imageLoader
            mImageLoader!![url, ImageLoader.getImageListener(mCoverArt, 0, 0)]
            mCoverArt!!.setImageUrl(url, mImageLoader)
            mCoverArt!!.visibility = View.VISIBLE
            mVideoView!!.visibility = View.INVISIBLE
        } else {
            mCoverArt!!.visibility = View.GONE
            mVideoView!!.visibility = View.VISIBLE
        }
    }

    private fun stopTrickplayTimer() {
        Log.d(TAG, "Stopped TrickPlay Timer")
        if (mSeekbarTimer != null) {
            mSeekbarTimer!!.cancel()
        }
    }

    private fun restartTrickplayTimer() {
        stopTrickplayTimer()
        mSeekbarTimer = Timer()
        mSeekbarTimer!!.scheduleAtFixedRate(UpdateSeekbarTask(), 100, 1000)
        Log.d(TAG, "Restarted TrickPlay Timer")
    }

    private fun stopControllersTimer() {
        if (mControllersTimer != null) {
            mControllersTimer!!.cancel()
        }
    }

    private fun startControllersTimer() {
        if (mControllersTimer != null) {
            mControllersTimer!!.cancel()
        }
        if (mLocation == PlaybackLocation.REMOTE) {
            return
        }
        mControllersTimer = Timer()
        mControllersTimer!!.schedule(HideControllersTask(), 5000)
    }

    // should be called from the main thread
    private fun updateControllersVisibility(show: Boolean) {
        if (show) {
            supportActionBar!!.show()
            mControllers!!.visibility = View.VISIBLE
        } else {
            if (!Utils.isOrientationPortrait(this)) {
                supportActionBar!!.hide()
            }
            mControllers!!.visibility = View.INVISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() was called")
        if (mLocation == PlaybackLocation.LOCAL) {
            if (mSeekbarTimer != null) {
                mSeekbarTimer!!.cancel()
                mSeekbarTimer = null
            }
            if (mControllersTimer != null) {
                mControllersTimer!!.cancel()
            }
            // since we are playing locally, we need to stop the playback of
            // video (if user is not watching, pause it!)
            mVideoView!!.pause()
            mPlaybackState = PlaybackState.PAUSED
            updatePlayButton(PlaybackState.PAUSED)
        }
        mCastContext!!.sessionManager.removeSessionManagerListener(
            mSessionManagerListener!!, CastSession::class.java
        )
    }

    override fun onStop() {
        Log.d(TAG, "onStop() was called")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() is called")
        stopControllersTimer()
        stopTrickplayTimer()
        super.onDestroy()
    }

    override fun onStart() {
        Log.d(TAG, "onStart was called")
        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume() was called")
        mCastContext!!.sessionManager.addSessionManagerListener(
            mSessionManagerListener!!, CastSession::class.java
        )
        if (mCastSession != null && mCastSession!!.isConnected) {
            updatePlaybackLocation(PlaybackLocation.REMOTE)
        } else {
            updatePlaybackLocation(PlaybackLocation.LOCAL)
        }
        if (mQueueMenuItem != null) {
            mQueueMenuItem!!.isVisible = mCastSession != null && mCastSession!!.isConnected
        }
        super.onResume()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return (mCastContext!!.onDispatchVolumeKeyEventBeforeJellyBean(event)
                || super.dispatchKeyEvent(event))
    }

    private inner class HideControllersTask : TimerTask() {
        override fun run() {
            mHandler.post {
                updateControllersVisibility(false)
                mControllersVisible = false
            }
        }
    }

    private inner class UpdateSeekbarTask : TimerTask() {
        override fun run() {
            mHandler.post {
                if (mLocation == PlaybackLocation.LOCAL) {
                    val currentPos = mVideoView!!.currentPosition
                    updateSeekbar(currentPos, mDuration)
                }
            }
        }
    }

    private fun setupControlsCallbacks() {
        mVideoView!!.setOnErrorListener { mp, what, extra ->
            Log.e(
                TAG, "OnErrorListener.onError(): VideoView encountered an "
                        + "error, what: " + what + ", extra: " + extra
            )
            val msg: String
            msg = if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                getString(R.string.video_error_media_load_timeout)
            } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                getString(R.string.video_error_server_unaccessible)
            } else {
                getString(R.string.video_error_unknown_error)
            }
            Utils.showErrorDialog(this@LocalPlayerActivity, msg)
            mVideoView!!.stopPlayback()
            mPlaybackState = PlaybackState.IDLE
            updatePlayButton(mPlaybackState)
            true
        }
        mVideoView!!.setOnPreparedListener { mp ->
            Log.d(TAG, "onPrepared is reached")
            mDuration = mp.duration
            mEndText!!.text = Utils.formatMillis(mDuration)
            mSeekbar!!.max = mDuration
            restartTrickplayTimer()
        }
        mVideoView!!.setOnCompletionListener {
            stopTrickplayTimer()
            Log.d(TAG, "setOnCompletionListener()")
            mPlaybackState = PlaybackState.IDLE
            updatePlayButton(mPlaybackState)
        }
        mVideoView!!.setOnTouchListener { v, event ->
            if (!mControllersVisible) {
                updateControllersVisibility(true)
            }
            startControllersTimer()
            false
        }
        mSeekbar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (mPlaybackState == PlaybackState.PLAYING) {
                    play(seekBar.progress)
                } else if (mPlaybackState != PlaybackState.IDLE) {
                    mVideoView!!.seekTo(seekBar.progress)
                }
                startControllersTimer()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopTrickplayTimer()
                mVideoView!!.pause()
                stopControllersTimer()
            }

            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                mStartText!!.text = Utils.formatMillis(progress)
            }
        })
        mPlayPause!!.setOnClickListener {
            if (mLocation == PlaybackLocation.LOCAL) {
                togglePlayback()
            }
        }
    }

    private fun updateSeekbar(position: Int, duration: Int) {
        mSeekbar!!.progress = position
        mSeekbar!!.max = duration
        mStartText!!.text = Utils.formatMillis(position)
        mEndText!!.text = Utils.formatMillis(duration)
    }

    private fun updatePlayButton(state: PlaybackState?) {
        Log.d(TAG, "Controls: PlayBackState: $state")
        val isConnected = (mCastSession != null
                && (mCastSession!!.isConnected || mCastSession!!.isConnecting))
        mControllers!!.visibility = if (isConnected) View.GONE else View.VISIBLE
        mPlayCircle!!.visibility = if (isConnected) View.GONE else View.VISIBLE
        when (state) {
            PlaybackState.PLAYING -> {
                mLoading!!.visibility = View.INVISIBLE
                mPlayPause!!.visibility = View.VISIBLE
                mPlayPause!!.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_av_pause_dark)
                )
                mPlayCircle!!.visibility = if (isConnected) View.VISIBLE else View.GONE
            }
            PlaybackState.IDLE -> {
                mPlayCircle!!.visibility = View.VISIBLE
                mControllers!!.visibility = View.GONE
                mCoverArt!!.visibility = View.VISIBLE
                mVideoView!!.visibility = View.INVISIBLE
            }
            PlaybackState.PAUSED -> {
                mLoading!!.visibility = View.INVISIBLE
                mPlayPause!!.visibility = View.VISIBLE
                mPlayPause!!.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_av_play_dark)
                )
                mPlayCircle!!.visibility = if (isConnected) View.VISIBLE else View.GONE
            }
            PlaybackState.BUFFERING -> {
                mPlayPause!!.visibility = View.INVISIBLE
                mLoading!!.visibility = View.VISIBLE
            }
            else -> {}
        }
    }

    @SuppressLint("NewApi")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        supportActionBar!!.show()
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
            }
            updateMetadata(false)
            mContainer!!.setBackgroundColor(resources.getColor(R.color.black))
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN
            )
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            updateMetadata(true)
            mContainer!!.setBackgroundColor(resources.getColor(R.color.white))
        }
    }

    private fun updateMetadata(visible: Boolean) {
        val displaySize: Point?
        if (!visible) {
            mDescriptionView!!.visibility = View.GONE
            mTitleView!!.visibility = View.GONE
            mAuthorView!!.visibility = View.GONE
            displaySize = Utils.getDisplaySize(this)
            val lp = RelativeLayout.LayoutParams(
                displaySize.x,
                displaySize.y + supportActionBar!!.height
            )
            lp.addRule(RelativeLayout.CENTER_IN_PARENT)
            mVideoView!!.layoutParams = lp
            mVideoView!!.invalidate()
        } else {
            val mm = mSelectedMedia!!.metadata
            mDescriptionView!!.text = mSelectedMedia!!.customData!!.optString(
                VideoProvider.Companion.KEY_DESCRIPTION
            )
            mTitleView!!.text = mm!!.getString(MediaMetadata.KEY_TITLE)
            mAuthorView!!.text = mm.getString(MediaMetadata.KEY_SUBTITLE)
            mDescriptionView!!.visibility = View.VISIBLE
            mTitleView!!.visibility = View.VISIBLE
            mAuthorView!!.visibility = View.VISIBLE
            displaySize = Utils.getDisplaySize(this)
            val lp =
                RelativeLayout.LayoutParams(displaySize.x, (displaySize.x * mAspectRatio).toInt())
            lp.addRule(RelativeLayout.BELOW, R.id.toolbar)
            mVideoView!!.layoutParams = lp
            mVideoView!!.invalidate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.player, menu)
        CastButtonFactory.setUpMediaRouteButton(
            applicationContext, menu,
            R.id.media_route_menu_item
        )
        mQueueMenuItem = menu.findItem(R.id.action_show_queue)
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
            intent = Intent(this@LocalPlayerActivity, CastPreference::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.action_show_queue) {
            intent = Intent(this@LocalPlayerActivity, QueueListViewActivity::class.java)
            startActivity(intent)
        } else if (item.itemId == android.R.id.home) {
            ActivityCompat.finishAfterTransition(this)
        }
        return true
    }

    private fun setupActionBar() {
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        toolbar.title =
            mSelectedMedia!!.metadata!!.getString(MediaMetadata.KEY_TITLE)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadViews() {
        mVideoView = findViewById<View>(R.id.videoView1) as VideoView
        mTitleView = findViewById<View>(R.id.titleTextView) as TextView
        mDescriptionView = findViewById<View>(R.id.descriptionTextView) as TextView
        mDescriptionView!!.movementMethod = ScrollingMovementMethod()
        mAuthorView = findViewById<View>(R.id.authorTextView) as TextView
        mStartText = findViewById<View>(R.id.startText) as TextView
        mStartText!!.text = Utils.formatMillis(0)
        mEndText = findViewById<View>(R.id.endText) as TextView
        mSeekbar = findViewById<View>(R.id.seekBar1) as SeekBar
        mPlayPause = findViewById<View>(R.id.playPauseImageView) as ImageView
        mLoading = findViewById<View>(R.id.progressBar1) as ProgressBar
        mControllers = findViewById(R.id.controllers)
        mContainer = findViewById(R.id.container)
        mCoverArt = findViewById<View>(R.id.coverArtView) as NetworkImageView
        ViewCompat.setTransitionName(mCoverArt!!, getString(R.string.transition_image))
        mPlayCircle = findViewById<View>(R.id.play_circle) as ImageButton
        mPlayCircle!!.setOnClickListener { togglePlayback() }
    }

    companion object {
        private const val TAG = "LocalPlayerActivity"
    }
}
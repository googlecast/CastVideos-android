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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.MediaUtils
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.browser.VideoProvider
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity
import com.google.sample.cast.refplayer.settings.CastPreference
import com.google.sample.cast.refplayer.utils.AsyncBitmap
import com.google.sample.cast.refplayer.utils.Utils

/**
 * Activity for the local media player.
 */
class LocalPlayerActivity : AppCompatActivity() {
    private var coverArtUrl: String? = null
    private var titleView: TextView? = null
    private var descriptionView: TextView? = null
    private var container: View? = null
    private var coverArt: ImageView? = null
    private var selectedMedia: MediaInfo? = null
    private var authorView: TextView? = null
    private var playCircle: ImageButton? = null
    private var startPosition: Long = 0
    private var playbackAdapter: PlaybackAdapter? = null
    private var shouldStart = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)
        setTitle(R.string.app_name)
        Log.d(TAG, "onCreate() was called")

        // For Android T+ devices, the local video playback posts a media notification when the app is
        // in foreground.
        playbackAdapter = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) VideoPlaybackAdapter(
            this,
            PlaybackAdapterCallback()
        ) else VideoPlaybackAdapterWithoutAudioFocus(this, PlaybackAdapterCallback())
        refreshViewFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent() was called")
        refreshViewFromIntent(intent)
    }

    private fun refreshViewFromIntent(intent: Intent) {
        val bundle = intent.extras
        if (bundle != null) {
            val mediaInfo = intent.getParcelableExtra<MediaInfo>("media") ?: return
            // Obtain the media info and other information from extra bundle.
            selectedMedia = mediaInfo
            loadViews()
            setupActionBar()
            startPosition = bundle.getLong("startPosition", 0)

            // For video apps, the playback will be resumed in the onResumed() if shouldStart is true.
            shouldStart = bundle.getBoolean("shouldStart")
        }
        updateMetadata(true)
    }

    /** Sets cover art status.  */
    private fun setCoverArtStatus() {
        val isHidden = playbackAdapter!!.isPlaybackLocal && playbackAdapter!!.isPlaying
        coverArt!!.visibility = if (isHidden) View.GONE else View.VISIBLE
    }

    override fun onPause() {
        Log.d(TAG, "onPause() was called")
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop() was called")
        if (playbackAdapter != null && playbackAdapter!!.isPlaying) {
            Log.d(TAG, "pause the playing video that will be resumed later")
            startPosition = playbackAdapter!!.currentPosition
            shouldStart = true
            playbackAdapter!!.pauseAndStopMediaNotification()
        }
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() is called")
        playbackAdapter!!.destroy()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart() was called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() was called")
        playbackAdapter!!.updatePlaybackState()
        if (playbackAdapter!!.isPlaybackLocal && shouldStart) {
            Log.d(
                TAG,
                "start playing media from position = $startPosition"
            )
            playbackAdapter!!.play(selectedMedia!!, startPosition)
            shouldStart = false
        }
    }

    private fun updatePlayButton() {
        Log.d(
            TAG,
            "updatePlaybackButton with isPlaybackLocal = "
              + playbackAdapter!!.isPlaybackLocal
              + ", isActive = "
              + playbackAdapter!!.isActive
        )
        // The playCircle is always shown except for local active playback.
        playCircle!!.visibility =
            if (playbackAdapter!!.isPlaybackLocal && playbackAdapter!!.isActive) View.GONE else View.VISIBLE

        // The controller is only shown and used for local playback.
        playbackAdapter!!.updateControllersVisibility(playbackAdapter!!.isPlaybackLocal)
        setCoverArtStatus()
    }

    @SuppressLint("NewApi")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        supportActionBar!!.show()
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            window
                .setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
            }
            updateMetadata(false)
            container!!.setBackgroundColor(getResources().getColor(R.color.black))
        } else {
            window
                .setFlags(
                    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN
                )
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            updateMetadata(true)
            container!!.setBackgroundColor(getResources().getColor(R.color.white))
        }
    }

    private fun updateMetadata(visible: Boolean) {
        if (selectedMedia == null) {
            return
        }
        val metadata = selectedMedia!!.metadata ?: return
        if (titleView != null) {
            titleView!!.text = metadata.getString(MediaMetadata.KEY_TITLE)
            titleView!!.visibility = if (visible) View.VISIBLE else View.GONE
        }
        val customData = selectedMedia!!.customData
        if (descriptionView != null && customData != null) {
            // Description is never set in tests.
            descriptionView!!.text = customData.optString(VideoProvider.KEY_DESCRIPTION)
            descriptionView!!.visibility = if (visible) View.VISIBLE else View.GONE
        }
        if (authorView != null) {
            authorView!!.text = metadata.getString(MediaMetadata.KEY_SUBTITLE)
            authorView!!.visibility = if (visible) View.VISIBLE else View.GONE
        }
        val displaySize = Utils.getDisplaySize(this)
        playbackAdapter!!.updateViewForOrientation(displaySize, supportActionBar!!)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.player, menu)
        CastButtonFactory.setUpMediaRouteButton(
            applicationContext, menu, R.id.media_route_menu_item
        )
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasSession: Boolean = !playbackAdapter!!.isPlaybackLocal
        menu.findItem(R.id.action_show_queue).setVisible(hasSession)
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
        val metadata = selectedMedia!!.metadata ?: return
        val title =
            metadata.getString(MediaMetadata.KEY_TITLE) ?: return
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        toolbar.setTitle(title)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadViews() {
        titleView = findViewById<View>(R.id.titleTextView) as TextView
        descriptionView = findViewById<View>(R.id.descriptionTextView) as TextView
        descriptionView!!.movementMethod = ScrollingMovementMethod()
        authorView = findViewById<View>(R.id.authorTextView) as TextView
        container = findViewById(R.id.container)
        coverArt = findViewById<View>(R.id.coverArtView) as ImageView
        val url = MediaUtils.getImageUrl(selectedMedia, 0)
        if (url != null && !TextUtils.equals(coverArtUrl, url)) {
            // Set the coverArt to null before fetching in case the fetching failed and we never update
            // the image.
            coverArt!!.setImageBitmap(null)
            val coverArtAsyncBitmap = AsyncBitmap()
            coverArtAsyncBitmap.setCallback(
                object : AsyncBitmap.Callback {
                    override fun onBitmapLoaded(bitmap: Bitmap?) {
                        coverArt!!.setImageBitmap(bitmap)
                    }
                })
            coverArtAsyncBitmap.loadBitmap(Uri.parse(url))
            coverArtUrl = url
        }
        ViewCompat.setTransitionName(coverArt!!, getString(R.string.transition_image))
        playCircle = findViewById<View>(R.id.play_circle) as ImageButton
        playCircle!!.setOnClickListener {
            if (playbackAdapter!!.isPlaybackLocal) {
                playbackAdapter!!.play(selectedMedia!!, 0)
                shouldStart = false
            } else {
                Utils.showQueuePopup(
                    this@LocalPlayerActivity,
                    playCircle,
                    selectedMedia
                )
            }
        }
    }

    /**
     * A class to transfer the playback state of [PlaybackAdapter] to [ ] and update the UI of [LocalPlayerActivity].
     */
    private inner class PlaybackAdapterCallback : PlaybackAdapter.Callback {
        override fun onMediaInfoUpdated(playingMedia: MediaInfo?) {
            if (playingMedia == null) {
                return
            }
            if (selectedMedia == null) {
                // When the previous LocalPlayerActivity has been destroyed, clicking on the local playback
                // notification will launch a new LocalPlaybackActivity.
                Log.d(TAG, "update the selectedMedia with the playing media")
                selectedMedia = playingMedia
                updateMetadata(true)
                return
            }
            val playingMetadata = playingMedia.metadata
            val selectedMetadata = selectedMedia!!.metadata
            if (playingMetadata == null || selectedMetadata == null) {
                return
            }
            val playingMediaTitle = playingMetadata.getString(MediaMetadata.KEY_TITLE)
            val selectedMediaTitle = selectedMetadata.getString(MediaMetadata.KEY_TITLE)
            if (TextUtils.equals(selectedMediaTitle, playingMediaTitle)) {
                Log.d(TAG, "update the seekbar for the playing media")
                playbackAdapter!!.setupSeekbar()
            } else {
                Log.d(TAG, "stop the playing media playback")
                playbackAdapter!!.stop()
            }
        }

        override fun onPlaybackStateChanged() {
            updatePlayButton()
        }

        override fun onPlaybackLocationChanged() {
            updatePlayButton()
            invalidateOptionsMenu()
        }

        override fun onDestroy() {
            Log.d(TAG, "destroy LocalPlayerActivity")
            finish()
        }
    }

    companion object {
        private const val TAG = "LocalPlayerActivity"
    }
}

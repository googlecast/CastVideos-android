/*
 * Copyright 2024 Google LLC. All Rights Reserved.
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

import android.app.Activity
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.VideoView
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.utils.Utils

/** A player to host the video local playback.  */
class VideoMediaPlayer(private val activity: Activity) :
  LocalMediaPlayer(activity, "video") {
  /** Callback to provide state update from [VideoMediaPlayer].  */
  interface Callback {
    /** Called when the media is loaded.  */
    fun onMediaLoaded()

    /** Called when the local playback state is changed.  */
    fun onPlaybackStateChanged(state: PlaybackStateCompat?)

    /** Called when switching from local playback to a remote playback.  */
    fun onPlaybackLocationChanged()
  }

  private var videoView: VideoView? = null

  init {
    initializeVideoView()
  }

  override fun onPlay(mediaId: String?, bundle: Bundle?): Boolean {
    val mediaInfo = mediaInfo
    if (TextUtils.isEmpty(mediaInfo!!.contentId) && !TextUtils.isEmpty(
        mediaInfo.entity
      )
    ) {
      videoView!!.setVideoPath(mediaInfo.entity)
    } else {
      videoView!!.setVideoURI(Uri.parse(mediaInfo.contentId))
    }
    return true
  }

  override fun onPlay(): Boolean {
    videoView!!.requestFocus()
    videoView!!.start()
    return true
  }

  override fun onPause(): Boolean {
    videoView!!.pause()
    return true
  }

  override fun onSeekTo(position: Int): Boolean {
    videoView!!.seekTo(position)
    return true
  }

  override fun onStop(): Boolean {
    videoView!!.stopPlayback()
    return true
  }

  public override fun onDestroy(): Boolean {
    return true
  }

  override val duration: Long
    get() = if (isActive) videoView!!.duration.toLong() else 0L
  override val currentPosition: Long
    protected get() = if (isActive) videoView!!.currentPosition.toLong() else 0L

  private fun initializeVideoView() {
    videoView = activity.findViewById<View>(R.id.videoView1) as VideoView
    videoView!!.setOnPreparedListener {
      logDebug("onPrepared")
      callback!!.onMediaLoaded()
      videoView!!.seekTo(startPosition.toInt())
      play()
    }
    videoView!!.setOnCompletionListener {
      logDebug("onCompletion is reached")
      stop()
    }
    videoView!!.setOnTouchListener { v, event ->
      notifyPlaybackStateChanged()
      false
    }
    videoView!!.setOnErrorListener { mediaPlayer, what, extra ->
      Log.e(TAG, "OnErrorListener.onError(): VideoView encountered an error, what: " + what
          + ", extra: " + extra)
      val message: String = if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
        activity.getString(R.string.video_error_media_load_timeout)
      } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
        activity.getString(R.string.video_error_server_unaccessible)
      } else {
        activity.getString(R.string.video_error_unknown_error)
      }
      Utils.showErrorDialog(activity, message)
      stop()
      true
    }
  }

  companion object {
    private const val TAG = "VideoMediaPlayer"
  }
}

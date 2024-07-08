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
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.VideoView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.framework.CastSession
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.utils.Utils

/** A class to handle the video playback that can not play in the background.  */
class VideoPlaybackAdapterWithoutAudioFocus(
  activity: Activity?, playbackAdapterCallback: Callback?,
) :
  PlaybackAdapter(
    activity!!,
    playbackAdapterCallback!!, "videoWoAudioFocus"
  ) {
  private val remotePlaybackHelper: RemotePlaybackHelper
  private var videoView: VideoView? = null
  private var startPosition: Long = 0

  init {
    initializeVideoView()
    remotePlaybackHelper = RemotePlaybackHelper(activity, RemotePlaybackHelperCallback())
    remotePlaybackHelper.registerSessionManagerListener()
  }

  override fun onPlay(mediaInfo: MediaInfo?, position: Long): Boolean {
    loadMediaInfo(mediaInfo!!)
    startPosition = position
    return true
  }

  private fun play(position: Long) {
    videoView!!.seekTo(position.toInt())
    videoView!!.requestFocus()
    videoView!!.start()
    playbackState = PlaybackStateCompat.STATE_PLAYING
    updatePlaybackState()
  }

  override fun onPause(): Boolean {
    videoView!!.pause()
    playbackState = PlaybackStateCompat.STATE_PAUSED
    updatePlaybackState()
    return true
  }

  override fun onPauseAndStopMediaNotification(): Boolean {
    return onPause()
  }

  override fun onPlay(): Boolean {
    play(currentPosition)
    return true
  }

  override fun onSeekTo(position: Int): Boolean {
    videoView!!.seekTo(position)
    updatePlaybackState()
    return true
  }

  override fun onStop(): Boolean {
    videoView!!.stopPlayback()
    playbackState = PlaybackStateCompat.STATE_STOPPED
    updatePlaybackState()
    return true
  }

  override fun onDestroy(): Boolean {
    remotePlaybackHelper.unregisterSessionManagerListener()
    return true
  }

  override fun onUpdatePlaybackState(): Boolean {
    if (isActive) {
      updateControllers()
    } else {
      updateControllersVisibility(false)
    }
    return true
  }

  override var playbackLocation: PlaybackLocation
    get() = if (remotePlaybackHelper.isRemotePlayback) PlaybackLocation.REMOTE else PlaybackLocation.LOCAL
    set(playbackLocation) {
      super.playbackLocation = playbackLocation
    }
  override val mediaDuration: Long
    get() = if (isActive) videoView!!.duration.toLong() else 0L
  override val currentPosition: Long
    get() = if (isActive) videoView!!.currentPosition.toLong() else 0L

  private fun initializeVideoView() {
    videoView = activity.findViewById<View>(R.id.videoView1) as VideoView
    videoView!!.setOnPreparedListener {
      setupSeekbar()
      play(startPosition)
    }
    videoView!!.setOnCompletionListener {
      logDebug("onCompletion is reached")
      stop()
    }
    videoView!!.setOnTouchListener { v, event ->
      updateControllers()
      false
    }
    videoView!!.setOnErrorListener { mediaPlayer, what, extra ->
      Log.e(
        TAG,
        "OnErrorListener.onError(): VideoView encountered an "
          + "error, what: "
          + what
          + ", extra: "
          + extra
      )
      val message: String
      message = if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
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

  private fun loadMediaInfo(mediaInfo: MediaInfo) {
    this.mediaInfo = mediaInfo
    remotePlaybackHelper.setMedia(mediaInfo)
    if (TextUtils.isEmpty(mediaInfo.contentId) && !TextUtils.isEmpty(mediaInfo.entity)) {
      videoView!!.setVideoPath(mediaInfo.entity)
    } else {
      videoView!!.setVideoURI(Uri.parse(mediaInfo.contentId))
    }
  }

  /**
   * A class to transfer the cast session state update from [RemotePlaybackHelper] to [ ].
   */
  private inner class RemotePlaybackHelperCallback : RemotePlaybackHelper.Callback {
    override fun onApplicationConnected(castSession: CastSession?) {
      logDebug("Cast session is connected to an application on the receiver")
      if (isActive) {
        remotePlaybackHelper.loadRemoteMedia(currentPosition, isPlaying)
        playbackState = PlaybackStateCompat.STATE_NONE
        playbackAdapterCallback.onDestroy()
      } else {
        playbackState = PlaybackStateCompat.STATE_NONE
        playbackAdapterCallback.onPlaybackLocationChanged()
      }
    }

    override fun onApplicationDisconnected() {
      logDebug("Cast session is disconnected from the receiver")
      if (!isActive) {
        playbackState = PlaybackStateCompat.STATE_NONE
        playbackAdapterCallback.onPlaybackLocationChanged()
      }
    }
  }
}

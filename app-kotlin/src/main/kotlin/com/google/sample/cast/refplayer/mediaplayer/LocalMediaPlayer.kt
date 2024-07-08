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

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.framework.CastSession
import com.google.sample.cast.refplayer.mediaplayer.PlaybackAdapter
import com.google.sample.cast.refplayer.mediaplayer.RemotePlaybackHelper
import java.util.Locale

/** An interface for the local media player.  */
abstract class LocalMediaPlayer(context: Context?, private val messagePrefix: String) {
  /** Callback to provide state update from [LocalMediaPlayer].  */
  interface Callback {
    /** Called when the media is loaded.  */
    fun onMediaLoaded()

    /** Called when the local playback state is changed.  */
    fun onPlaybackStateChanged(state: PlaybackStateCompat?)

    /** Called when switching from local playback to a remote playback.  */
    fun onPlaybackLocationChanged()
  }

  protected var callback: Callback? = null
  var mediaInfo: MediaInfo? = null
    protected set
  protected var startPosition: Long = 0

  @PlaybackStateCompat.State
  protected var playbackState: Int
  private val remotePlaybackHelper: RemotePlaybackHelper?

  init {
    remotePlaybackHelper = RemotePlaybackHelper(context, RemotePlaybackHelperCallback())
    remotePlaybackHelper.registerSessionManagerListener()
    playbackState = PlaybackStateCompat.STATE_NONE
  }

  fun addCallback(callback: Callback?) {
    this.callback = callback
  }

  protected abstract fun onPlay(mediaId: String?, bundle: Bundle?): Boolean
  protected abstract fun onPlay(): Boolean
  protected abstract fun onPause(): Boolean
  protected abstract fun onSeekTo(position: Int): Boolean
  protected abstract fun onStop(): Boolean
  protected abstract fun onDestroy(): Boolean
  abstract val duration: Long
  protected abstract val currentPosition: Long

  fun play(mediaId: String?, bundle: Bundle) {
    mediaInfo = bundle.getParcelable(KEY_MEDIA_INFO)
    if (mediaInfo == null) {
      return
    }
    remotePlaybackHelper!!.setMedia(mediaInfo)
    startPosition = bundle.getLong(KEY_START_POSITION)
    if (onPlay(mediaId, bundle)) {
      logDebug("play media from position " + startPosition + " when isActive = " + isActive)
    }
  }

  fun play() {
    if (onPlay()) {
      logDebug("play playback")
      playbackState = PlaybackStateCompat.STATE_PLAYING
      notifyPlaybackStateChanged()
    }
  }

  fun pause() {
    if (onPause()) {
      logDebug("pause playback")
      playbackState = PlaybackStateCompat.STATE_PAUSED
      notifyPlaybackStateChanged()
    }
  }

  fun seekTo(position: Int) {
    if (onSeekTo(position)) {
      logDebug("seek playback to $position")
      notifyPlaybackStateChanged()
    }
  }

  fun stop() {
    if (onStop()) {
      logDebug("stop playback")
      playbackState = PlaybackStateCompat.STATE_STOPPED
      notifyPlaybackStateChanged()
    }
  }

  fun destroy() {
    if (onDestroy()) {
      if (remotePlaybackHelper != null) {
        remotePlaybackHelper.unregisterSessionManagerListener()
      }
    }
  }

  fun setVolume(volume: Float) {}
  fun getRemotePlaybackHelper(): RemotePlaybackHelper? {
    return remotePlaybackHelper
  }

  protected val isActive: Boolean
    protected get() = (playbackState == PlaybackStateCompat.STATE_PLAYING
      || playbackState == PlaybackStateCompat.STATE_PAUSED)
  protected val isPlaying: Boolean
    protected get() {
      return playbackState == PlaybackStateCompat.STATE_PLAYING
    }

  fun notifyPlaybackStateChanged() {
    val stateBuilder: PlaybackStateCompat.Builder = PlaybackStateCompat.Builder()
    val actions: Long = getAvailableActions(playbackState)
    if (actions > 0) {
      stateBuilder.setActions(actions)
    }
    stateBuilder.setState(playbackState, currentPosition, 1.0f)
    callback!!.onPlaybackStateChanged(stateBuilder.build())
  }

  @PlaybackStateCompat.Actions
  private fun getAvailableActions(@PlaybackStateCompat.State state: Int): Long {
    var actions: Long = 0L
    when (state) {
      PlaybackStateCompat.STATE_STOPPED -> actions =
        actions or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

      PlaybackStateCompat.STATE_PLAYING -> actions = actions or (PlaybackStateCompat.ACTION_STOP
        or PlaybackStateCompat.ACTION_PLAY_PAUSE
        or PlaybackStateCompat.ACTION_PAUSE
        or PlaybackStateCompat.ACTION_SEEK_TO)

      PlaybackStateCompat.STATE_PAUSED -> actions = actions or (PlaybackStateCompat.ACTION_STOP
        or PlaybackStateCompat.ACTION_PLAY_PAUSE
        or PlaybackStateCompat.ACTION_PLAY
        or PlaybackStateCompat.ACTION_SEEK_TO)

      else -> {}
    }
    return actions
  }

  val playbackLocation: PlaybackAdapter.PlaybackLocation
    get() {
      return if (remotePlaybackHelper!!.isRemotePlayback) PlaybackAdapter.PlaybackLocation.REMOTE else PlaybackAdapter.PlaybackLocation.LOCAL
    }

  protected fun logDebug(message: String?, vararg args: Any?) {
    val formattedMessage: String = String.format(
      "[%s] %s",
      messagePrefix,
      if ((args.size == 0)) message else String.format(
        Locale.ROOT,
        (message)!!, *args
      )
    )
    Log.d(TAG, formattedMessage)
  }

  /** A class to transfer the cast session state update from [RemotePlaybackHelper].  */
  private inner class RemotePlaybackHelperCallback() : RemotePlaybackHelper.Callback {
    override fun onApplicationConnected(castSession: CastSession?) {
      logDebug("Cast session is connected to an application on the receiver")
      if (isActive && mediaInfo != null) {
        remotePlaybackHelper!!.loadRemoteMedia(currentPosition, isPlaying)
        callback!!.onPlaybackLocationChanged()
        stop()
      } else {
        playbackState = PlaybackStateCompat.STATE_NONE
        callback!!.onPlaybackLocationChanged()
      }
    }

    override fun onApplicationDisconnected() {
      logDebug("Cast session is disconnected from the receiver")
      if (isActive) {
        return
      }
      playbackState = PlaybackStateCompat.STATE_NONE
      callback!!.onPlaybackLocationChanged()
    }
  }

  companion object {
    protected val TAG: String = "LocalMediaPlayer"
    val KEY_MEDIA_INFO: String = "com.google.sample.cast.refplayer.mediaplayer.MEDIA_INFO"
    val KEY_START_POSITION: String = "com.google.sample.cast.refplayer.mediaplayer.START_POSITION"
  }
}

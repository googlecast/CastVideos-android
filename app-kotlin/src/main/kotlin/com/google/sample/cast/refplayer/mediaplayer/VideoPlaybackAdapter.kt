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
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import com.google.android.gms.cast.MediaInfo
import com.google.sample.cast.refplayer.notification.MediaSessionProxy

/**
 * A class to handle the video playback that can not play in the background, but it posts a media
 * notification for the local video playback when the app is in the foreground.
 */
class VideoPlaybackAdapter(activity: Activity?, playbackAdapterCallback: Callback?) :
  PlaybackAdapter(
    activity!!,
    playbackAdapterCallback!!, "video"
  ) {
  private val mediaSessionProxy: MediaSessionProxy
  private var mediaController: MediaControllerCompat? = null
  private val localMediaPlayer: VideoMediaPlayer

  init {
    localMediaPlayer = VideoMediaPlayer(activity!!)
    mediaSessionProxy = MediaSessionProxy(activity, localMediaPlayer)
    val token: MediaSessionCompat.Token? = mediaSessionProxy!!.mediaSessionToken
    if (token != null) {
      mediaController = MediaControllerCompat(activity, token)
      MediaControllerCompat.setMediaController(activity, mediaController)
      mediaController!!.registerCallback(mediaControllerCallback)

      // If the video local player is not active, then check if it is casting to remote device or not.
      @PlaybackStateCompat.State val playbackState = mediaController!!.playbackState.state
      val isActive = (playbackState == PlaybackStateCompat.STATE_PLAYING
        || playbackState == PlaybackStateCompat.STATE_PAUSED)
      if (!isActive) {
        mediaController!!.sendCommand(
          MediaSessionProxy.ACTION_UPDATE_PLAYBACK_LOCATION, Bundle(), null
        )
      }
    }
  }

  override fun onPlay(mediaInfo: MediaInfo?, position: Long): Boolean {
    this.mediaInfo = mediaInfo
    val hasEntity = TextUtils.isEmpty(mediaInfo!!.contentId) && !TextUtils.isEmpty(mediaInfo.entity)
    val contentUrl = (if (hasEntity) mediaInfo.entity else mediaInfo.contentId) ?: return false
    val bundle = Bundle()
    bundle.putParcelable(LocalMediaPlayer.KEY_MEDIA_INFO, mediaInfo)
    bundle.putLong(LocalMediaPlayer.KEY_START_POSITION, position)
    mediaController!!.transportControls.playFromMediaId(contentUrl, bundle)
    return true
  }

  override fun onPause(): Boolean {
    mediaController!!.transportControls.pause()
    return true
  }

  override fun onPauseAndStopMediaNotification(): Boolean {
    mediaController!!.transportControls.pause()
    mediaSessionProxy.setStopMediaNotificationAfterPaused(true)
    return true
  }

  override fun onPlay(): Boolean {
    mediaController!!.transportControls.play()
    return true
  }

  override fun onSeekTo(position: Int): Boolean {
    mediaController!!.transportControls.seekTo(position.toLong())
    return true
  }

  override fun onStop(): Boolean {
    mediaController!!.transportControls.stop()
    return true
  }

  override fun onDestroy(): Boolean {
    if (mediaController != null && mediaControllerCallback != null) {
      mediaController!!.unregisterCallback(mediaControllerCallback)
    }
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

  override val mediaDuration: Long
    get() = mediaController?.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
  override val currentPosition: Long
    get() = mediaController?.playbackState?.position ?: 0L
}

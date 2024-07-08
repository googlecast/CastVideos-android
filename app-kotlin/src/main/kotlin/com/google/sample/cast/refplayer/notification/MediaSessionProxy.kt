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
package com.google.sample.cast.refplayer.notification

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import androidx.media.utils.MediaConstants
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.mediaplayer.LocalMediaPlayer
import com.google.sample.cast.refplayer.utils.AsyncBitmap

/** A proxy of Media Session.  */
class MediaSessionProxy(private val context: Context, localMediaPlayer: LocalMediaPlayer) {
  private val localNotificationProxy: LocalNotificationProxy
  private val audioManager: AudioManager?
  private var audioFocusHelper: AudioFocusHelper?
  private var localMediaPlayer: LocalMediaPlayer?
  var mediaSession: MediaSessionCompat?
    private set
  private val metadataBuilder: MediaMetadataCompat.Builder
  private var playbackStateBuilder: PlaybackStateCompat.Builder
  private var stopCustomAction: PlaybackStateCompat.CustomAction? = null
    private get() {
      if (field == null) {
        field = PlaybackStateCompat.CustomAction.Builder(
          ACTION_STOP,
          context
            .resources
            .getString(R.string.local_playback_notification_stop_action_title),
          R.drawable.ic_av_stop
        )
          .build()
      }
      return field
    }
  private var stopMediaNotificationAfterPaused: Boolean? = null

  init {
    localNotificationProxy = LocalNotificationProxy(
      context
    )
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioFocusHelper = AudioFocusHelper()
    this.localMediaPlayer = localMediaPlayer
    localMediaPlayer.addCallback(LocalMediaPlayerCallback())

    // Create a MediaSessionCompat for the local media playback.
    val mediaSessionCompat = MediaSessionCompat(context, "CastVideoSample")
    mediaSessionCompat.setFlags(
      MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
    )
    mediaSessionCompat.setCallback(MediaSessionCallback())
    playbackStateBuilder =
      PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
    mediaSessionCompat.setPlaybackState(playbackStateBuilder.build())
    metadataBuilder = MediaMetadataCompat.Builder()
    mediaSessionCompat.setMetadata(metadataBuilder.build())
    mediaSession = mediaSessionCompat
  }

  val mediaSessionToken: MediaSessionCompat.Token?
    get() = if ((mediaSession != null)) mediaSession!!.sessionToken else null

  fun destroy() {
    Log.d(TAG, "destroy")
    if (audioFocusHelper != null) {
      audioFocusHelper!!.abandonAudioFocus()
      audioFocusHelper = null
    }
    stopLocalNotification()
    if (localMediaPlayer != null) {
      localMediaPlayer!!.destroy()
      localMediaPlayer = null
    }
    if (mediaSession != null) {
      mediaSession!!.release()
      mediaSession = null
    }
  }

  private fun notifyPlaybackLocation() {
    if (localMediaPlayer == null || mediaSession == null) {
      return
    }
    val location: com.google.sample.cast.refplayer.mediaplayer.PlaybackAdapter.PlaybackLocation =
      localMediaPlayer!!.playbackLocation
    Log.d(
      TAG,
      "notify the playback location: $location"
    )
    val bundle = Bundle()
    bundle.putSerializable(EVENT_KEY_LOCATION, location)
    mediaSession!!.sendSessionEvent(EVENT_ON_TRANSFER_LOCATION, bundle)
  }

  // It is safe to catch newly defined exception types ForegroundServiceStartNotAllowedException on
  // older Android versions that do not define that exception type.
  private fun startLocalNotification() {
    Log.d(TAG, "start media notification for local playback")
    localNotificationProxy.updateNotification(mediaSession)
  }

  private fun stopLocalNotification() {
    Log.d(TAG, "stop media notification for local playback")
    localNotificationProxy.cancelNotification()
  }

  fun setStopMediaNotificationAfterPaused(stopMediaNotificationAfterPaused: Boolean) {
    this.stopMediaNotificationAfterPaused = stopMediaNotificationAfterPaused
  }

  /** The MediaSession callbacks will transport the controls to [LocalMediaPlayer].  */
  inner class MediaSessionCallback() : MediaSessionCompat.Callback() {
    override fun onPrepare() {
      Log.d(TAG, "onPrepare")
      if (mediaSession != null && !mediaSession!!.isActive) {
        mediaSession!!.setActive(true)
        mediaSession!!.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
      }
    }

    override fun onPlayFromMediaId(mediaId: String, bundle: Bundle) {
      if (audioFocusHelper == null || !audioFocusHelper!!.requestAudioFocus()) {
        return
      }
      Log.d(
        TAG,
        "play mediaId = $mediaId"
      )
      if (localMediaPlayer != null) {
        localMediaPlayer!!.play(mediaId, bundle)
      }
    }

    override fun onPlay() {
      if (audioFocusHelper == null || !audioFocusHelper!!.requestAudioFocus()) {
        return
      }
      Log.d(TAG, "onPlay")
      if (localMediaPlayer != null) {
        localMediaPlayer!!.play()
      }
    }

    override fun onPause() {
      Log.d(TAG, "onPause")
      if (localMediaPlayer != null) {
        localMediaPlayer!!.pause()
      }
      if (audioFocusHelper != null) {
        audioFocusHelper!!.abandonAudioFocus()
      }
    }

    override fun onSeekTo(position: Long) {
      Log.d(TAG, "onSeekTo")
      if (localMediaPlayer != null) {
        localMediaPlayer!!.seekTo(position.toInt())
      }
    }

    override fun onStop() {
      Log.d(TAG, "onStop")
      if (localMediaPlayer != null) {
        localMediaPlayer!!.stop()
      }
      if (audioFocusHelper != null) {
        audioFocusHelper!!.abandonAudioFocus()
      }
    }

    override fun onCommand(command: String, extra: Bundle, resultReceiver: ResultReceiver?) {
      Log.d(TAG, "onCommand with $command")
      val bundle = Bundle()
      when (command) {
        ACTION_UPDATE_PLAYBACK_LOCATION -> notifyPlaybackLocation()
        ACTION_UPDATE_MEDIA_INFO -> if (mediaSession != null && localMediaPlayer != null) {
          bundle.putParcelable(EVENT_KEY_MEDIA_INFO, localMediaPlayer!!.mediaInfo)
          mediaSession!!.sendSessionEvent(EVENT_ON_UPDATE_MEDIA_INFO, bundle)
        }

        ACTION_UPDATE_PLAYBACK_STATE -> if (localMediaPlayer != null) {
          localMediaPlayer!!.notifyPlaybackStateChanged()
        }

        else -> {}
      }
    }

    override fun onCustomAction(action: String, extras: Bundle) {
      Log.d(
        TAG,
        "onCustomAction with action = $action"
      )
      if (TextUtils.equals(action, ACTION_STOP)) {
        onStop()
      }
    }
  }

  /** A callback class to transport the playback states of [LocalMediaPlayer].  */
  private inner class LocalMediaPlayerCallback() :
    com.google.sample.cast.refplayer.mediaplayer.LocalMediaPlayer.Callback {
    private var thumbnailImage: ThumbnailImage? = null
    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
      if (state == null) {
        return
      }
      val mediaSessionCompat = mediaSession ?: return
      Log.d(
        TAG,
        "onPlaybackStateChanged with state: $state"
      )
      playbackStateBuilder = PlaybackStateCompat.Builder(state)
      playbackStateBuilder.addCustomAction(stopCustomAction)
      mediaSessionCompat.setPlaybackState(playbackStateBuilder.build())
      val bundle = Bundle()
      bundle.putBoolean(MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV, true)
      bundle.putBoolean(MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT, true)
      mediaSessionCompat.setExtras(bundle)
      if ((state.state == PlaybackStateCompat.STATE_PLAYING
          || (stopMediaNotificationAfterPaused == null
          && state.state == PlaybackStateCompat.STATE_PAUSED))
      ) {
        if (!mediaSessionCompat.isActive) {
          mediaSessionCompat.setActive(true)
        }
        startLocalNotification()
      } else {
        mediaSessionCompat.setActive(false)
        stopLocalNotification()
        stopMediaNotificationAfterPaused = null
      }
    }

    override fun onMediaLoaded() {
      if (localMediaPlayer == null) {
        return
      }
      val duration: Long = localMediaPlayer!!.duration
      val mediaInfo: MediaInfo = localMediaPlayer!!.mediaInfo ?: return
      val metadata = mediaInfo.metadata ?: return
      Log.d(
        TAG,
        "onMediaLoaded with duration = $duration"
      )
      val title = metadata.getString(MediaMetadata.KEY_TITLE)
      val subtitle = metadata.getString(MediaMetadata.KEY_SUBTITLE)
      if (title != null) {
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
      }
      if (subtitle != null) {
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
      }
      metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
      val webImage = if (metadata.hasImages()) metadata.images[0] else null
      val imageUrl = webImage?.url
      if (((imageUrl != null
          ) && (thumbnailImage != null
          ) && (imageUrl == thumbnailImage!!.imageUrl) && (thumbnailImage!!.bitmap != null))
      ) {
        Log.d(TAG, "reuse the existing bitmap")
        metadataBuilder.putBitmap(
          MediaMetadataCompat.METADATA_KEY_ART,
          thumbnailImage!!.bitmap
        )
      } else if (imageUrl != null) {
        thumbnailImage = ThumbnailImage(imageUrl)
      }
      if (mediaSession != null) {
        mediaSession!!.setMetadata(metadataBuilder.build())
      }
    }

    override fun onPlaybackLocationChanged() {
      notifyPlaybackLocation()
    }
  }

  private inner class AudioFocusHelper() : OnAudioFocusChangeListener {
    private var audioFocusRequest: AudioFocusRequest? = null
    fun requestAudioFocus(): Boolean {
      if (audioManager == null) {
        return false
      }
      val result: Int
      if (VERSION.SDK_INT >= VERSION_CODES.O) {
        // The AudioFocusRequest.Builder() API is only available on Android O+ devices.
        val attributes = AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(attributes)
          .setOnAudioFocusChangeListener(this)
          .build()
        result = audioManager.requestAudioFocus(audioFocusRequest!!)
      } else {
        result = audioManager
          .requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
      }
      return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonAudioFocus(): Boolean {
      if (audioManager == null) {
        return true
      }
      var result = AudioManager.AUDIOFOCUS_REQUEST_FAILED
      if (VERSION.SDK_INT >= VERSION_CODES.O) {
        if (audioFocusRequest != null) {
          result = audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        }
      } else {
        result = audioManager.abandonAudioFocus(this)
      }
      return result == AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }

    override fun onAudioFocusChange(focusChange: Int) {
      val localMediaPlayer: LocalMediaPlayer? = localMediaPlayer
      if (localMediaPlayer == null) {
        Log.d(TAG, "null localMediaPlayer when changing audio focus")
        return
      }
      when (focusChange) {
        AudioManager.AUDIOFOCUS_GAIN -> {
          Log.d(TAG, "gain audio focus")
          localMediaPlayer.setVolume(1.0f)
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          Log.d(TAG, "loss audio focus but can duck")
          localMediaPlayer.setVolume(0.5f)
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> {
          // Fall through.
          Log.d(TAG, "loss audio focus so pause")
          localMediaPlayer.pause()
        }

        else -> Log.d(TAG, "ignore unsupported audio focus change")
      }
    }
  }

  private inner class ThumbnailImage(val imageUrl: Uri) {
    private val asyncBitmap: AsyncBitmap
    var bitmap: Bitmap? = null

    init {
      asyncBitmap = AsyncBitmap()
      asyncBitmap.setCallback(
        object : AsyncBitmap.Callback {
          override fun onBitmapLoaded(bitmap: Bitmap?) {
            Log.d(TAG, "bitmap is loaded")
            this@ThumbnailImage.bitmap = bitmap
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            if (mediaSession != null) {
              mediaSession!!.setMetadata(metadataBuilder.build())
            }
          }
        })
      asyncBitmap.loadBitmap(imageUrl)
    }
  }

  companion object {
    private val TAG = "MediaSessionProxy"
    val EVENT_ON_TRANSFER_LOCATION =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.onTransferLocation"
    val EVENT_KEY_LOCATION =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.keyLocation"
    val ACTION_UPDATE_PLAYBACK_LOCATION =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionUpdatePlaybackLocation"
    val EVENT_ON_UPDATE_MEDIA_INFO =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.onUpdateMediaInfo"
    val EVENT_KEY_MEDIA_INFO =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.keyMediaInfo"
    val ACTION_UPDATE_MEDIA_INFO =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionUpdateMediaInfo"
    val ACTION_UPDATE_PLAYBACK_STATE =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionUpdatePlaybackState"
    private val ACTION_STOP =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionStop"
  }
}

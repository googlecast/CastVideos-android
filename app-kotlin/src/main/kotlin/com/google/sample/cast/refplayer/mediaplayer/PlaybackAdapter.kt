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
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import com.google.android.gms.cast.MediaInfo
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.notification.MediaSessionProxy
import com.google.sample.cast.refplayer.utils.Utils
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

/** An abstract class to handle the local playback.  */
abstract class PlaybackAdapter protected constructor(
  protected val activity: Activity,
  protected val playbackAdapterCallback: Callback,
  private val messagePrefix: String,
) {
  /**
   * Callback to provide state update from [PlaybackAdapter] to [LocalPlayerActivity].
   */
  interface Callback {
    /** Called when the MediaInfo is updated.  */
    fun onMediaInfoUpdated(mediaInfo: MediaInfo?)

    /** Called when the local playback state is changed.  */
    fun onPlaybackStateChanged()

    /** Called when switching the playback location between local and remote.  */
    fun onPlaybackLocationChanged()

    /** Called to end the [LocalPlayerActivity].  */
    fun onDestroy()
  }

  /** indicates whether we are doing a local or a remote playback  */
  enum class PlaybackLocation {
    LOCAL,
    REMOTE
  }

  protected val handler: Handler = Handler()
  protected val mediaControllerCallback: MediaControllerCompat.Callback
  var mediaInfo: MediaInfo? = null
    protected set

  @PlaybackStateCompat.State
  protected var playbackState: Int
  open var playbackLocation: PlaybackLocation = PlaybackLocation.LOCAL
    protected set
  private var playerContainer: View? = null
  private var startText: TextView? = null
  private var endText: TextView? = null
  private var seekbar: SeekBar? = null
  private var playPause: ImageView? = null
  private var controllers: View? = null
  private var actionBar: ActionBar? = null
  private var controllersTimer: Timer? = null
  private var seekbarTimer: Timer? = null

  init {
    mediaControllerCallback = MediaControllerCallback()
    playbackState = PlaybackStateCompat.STATE_NONE
    playbackLocation = PlaybackLocation.LOCAL
    initializePlayerAndController()
  }

  protected abstract val mediaDuration: Long

  /** Gets the current position of the media playback.  */
  abstract val currentPosition: Long

  /** Plays the given {@link MediaInfo} starting from the given position. */
  protected abstract fun onPlay(mediaInfo: MediaInfo?, position: Long): Boolean
  protected abstract fun onPlay(): Boolean
  protected abstract fun onPause(): Boolean
  protected abstract fun onPauseAndStopMediaNotification(): Boolean
  protected abstract fun onSeekTo(position: Int): Boolean
  protected abstract fun onStop(): Boolean
  protected abstract fun onDestroy(): Boolean
  protected abstract fun onUpdatePlaybackState(): Boolean
  fun play(mediaInfo: MediaInfo, position: Long) {
    val hasEntity: Boolean =
      TextUtils.isEmpty(mediaInfo.contentId) && !TextUtils.isEmpty(mediaInfo.entity)
    val contentUrl: String? = if (hasEntity) mediaInfo.entity else mediaInfo.contentId
    if (contentUrl == null) {
      return
    }
    if (onPlay(mediaInfo, position)) {
      logDebug("play mediaInfo with position = %d", position)
      restartTrickplayTimer()
    }
  }

  /** Stops the player. The player can be restart if needed.  */
  fun stop() {
    if (onStop()) {
      logDebug("stop")
      stopTrickplayTimer()
      stopControllersTimer()
    }
  }

  /** Destroys the player. The player can not be restart.  */
  fun destroy() {
    if (onDestroy()) {
      logDebug("destroy")
      stopTrickplayTimer()
      stopControllersTimer()
    }
  }

  /** Pauses the playback of the media.  */
  fun pause() {
    if (onPause()) {
      logDebug("pause")
      stopTrickplayTimer()
    }
  }

  /** Pauses the playback of the media and stop media notification.  */
  fun pauseAndStopMediaNotification() {
    if (onPauseAndStopMediaNotification()) {
      logDebug("pauseAndStopMediaNotification")
      stopTrickplayTimer()
    }
  }

  /** Plays the playback of the media.  */
  fun play() {
    if (onPlay()) {
      logDebug("play")
      restartTrickplayTimer()
    }
  }

  protected fun seekTo(position: Int) {
    if (onSeekTo(position)) {
      logDebug("seekTo %d", position)
    }
  }

  protected fun destroySession() {
    playbackAdapterCallback.onDestroy()
  }

  fun updatePlaybackState() {
    if (onUpdatePlaybackState()) {
      logDebug("update playback state to $playbackState")
      updatePlayPauseImageView()
      playbackAdapterCallback.onPlaybackStateChanged()
    }
  }

  val isPlaybackLocal: Boolean
    /** Returns `true` if the player has a local playback.  */
    get() = playbackLocation == PlaybackLocation.LOCAL
  val isActive: Boolean
    /** Returns `true` if the player is in the playing or the paused state.  */
    get() = (playbackState == PlaybackStateCompat.STATE_PLAYING
      || playbackState == PlaybackStateCompat.STATE_PAUSED)
  val isPlaying: Boolean
    /** Returns `true` if the player is in the playing state.  */
    get() {
      return playbackState == PlaybackStateCompat.STATE_PLAYING
    }

  /** Updates the view of the player and the controllers.  */
  fun updateViewForOrientation(displaySize: Point, actionBar: ActionBar) {
    this.actionBar = actionBar
    val orientationPortrait: Boolean = Utils.isOrientationPortrait(
      activity
    )
    updateSupportActionBarVisibility(orientationPortrait)
    val layoutParams: RelativeLayout.LayoutParams
    if (orientationPortrait) {
      layoutParams =
        RelativeLayout.LayoutParams(displaySize.x, (displaySize.x * ASPECT_RATIO).toInt())
      layoutParams.addRule(RelativeLayout.BELOW, R.id.toolbar)
    } else {
      layoutParams = RelativeLayout.LayoutParams(displaySize.x, displaySize.y + actionBar.height)
      layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
    }
    playerContainer!!.layoutParams = layoutParams
  }

  /** Setups the seekbar with the duration of the media.  */
  fun setupSeekbar() {
    val duration: Int = mediaDuration.toInt()
    logDebug("setup seekbar with duration = $duration")
    endText!!.text = Utils.formatMillis(duration)
    seekbar!!.max = duration
    restartTrickplayTimer()
  }

  private fun updatePlayPauseImageView() {
    playPause!!.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
    if (isActive) {
      val playPauseResourceId: Int =
        if (isPlaying) R.drawable.ic_av_pause_dark else R.drawable.ic_av_play_dark
      playPause!!.setImageDrawable(activity.resources.getDrawable(playPauseResourceId))
    }
  }

  protected fun updateControllers() {
    updateControllersVisibility(true)
    startControllersTimer()
  }

  // should be called from the main thread
  fun updateControllersVisibility(toShow: Boolean) {
    controllers!!.visibility = if (toShow) View.VISIBLE else View.INVISIBLE
    updateSupportActionBarVisibility(toShow)
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

  private fun initializePlayerAndController() {
    val activity: Activity = activity
    playerContainer = activity.findViewById(R.id.player_container) as View
    startText = activity.findViewById<View>(R.id.startText) as TextView
    endText = activity.findViewById<View>(R.id.endText) as TextView
    seekbar = activity.findViewById<View>(R.id.seekBar1) as SeekBar
    seekbar!!.setOnSeekBarChangeListener(
      object : OnSeekBarChangeListener {
        override fun onStopTrackingTouch(seekBar: SeekBar) {
          seekTo(seekBar.progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
          startText!!.text = Utils.formatMillis(progress)
        }
      })
    playPause = activity.findViewById<View>(R.id.playPauseImageView) as ImageView
    playPause!!.setOnClickListener(
      object : View.OnClickListener {
        override fun onClick(v: View) {
          if (isPlaying) {
            pause()
          } else {
            play()
          }
        }
      })
    controllers = activity.findViewById(R.id.controllers)
  }

  private fun stopControllersTimer() {
    if (controllersTimer != null) {
      controllersTimer!!.cancel()
      controllersTimer = null
    }
  }

  private fun startControllersTimer() {
    stopControllersTimer()
    controllersTimer = Timer()
    controllersTimer!!.schedule(HideControllersTask(), 5000)
  }

  private fun stopTrickplayTimer() {
    if (seekbarTimer != null) {
      seekbarTimer!!.cancel()
      seekbarTimer = null
    }
  }

  protected fun restartTrickplayTimer() {
    stopTrickplayTimer()
    seekbarTimer = Timer()
    seekbarTimer!!.scheduleAtFixedRate(UpdateSeekbarTask(), 100, 1000)
  }

  private fun updateSeekbar() {
    val duration: Int = mediaDuration.toInt()
    val position: Int = currentPosition.toInt()
    seekbar!!.progress = position
    seekbar!!.max = duration
    startText!!.text = Utils.formatMillis(position)
    endText!!.text = Utils.formatMillis(duration)
  }

  private fun updateSupportActionBarVisibility(toShow: Boolean) {
    if (actionBar == null) {
      return
    }
    if (toShow) {
      actionBar!!.show()
    } else if (!Utils.isOrientationPortrait(activity)) {
      actionBar!!.hide()
    }
  }

  /** A class to transfer the playback state from MediaSession.  */
  private inner class MediaControllerCallback() : MediaControllerCompat.Callback() {
    override fun onPlaybackStateChanged(playbackStateCompat: PlaybackStateCompat?) {
      logDebug("onPlaybackStateChanged with playbackStateCompat = $playbackStateCompat")
      if (playbackStateCompat == null) {
        return
      }
      playbackState = playbackStateCompat.state
      updatePlaybackState()
    }

    override fun onMetadataChanged(metadata: MediaMetadataCompat) {
      logDebug("onMetadataChanged")
      setupSeekbar()
    }

    /** Receives the customized event from the [MediaSessionProxy].  */
    override fun onSessionEvent(event: String, bundle: Bundle) {
      logDebug("onSessionEvent with event = $event")
      when (event) {
        MediaSessionProxy.EVENT_ON_TRANSFER_LOCATION -> {
          val playbackLocationLocal: PlaybackLocation? =
            bundle.get(MediaSessionProxy.EVENT_KEY_LOCATION) as PlaybackLocation?
          if (playbackLocationLocal != null) {
            playbackLocation = playbackLocationLocal
            logDebug("update playbackLocation to $playbackLocation")
            playbackState = PlaybackStateCompat.STATE_NONE
            playbackAdapterCallback.onPlaybackLocationChanged()
          }
        }

        MediaSessionProxy.EVENT_ON_UPDATE_MEDIA_INFO -> {
          val playingMediaInfo: MediaInfo? =
            bundle.getParcelable(MediaSessionProxy.EVENT_KEY_MEDIA_INFO)
          if (playingMediaInfo != null) {
            playbackAdapterCallback.onMediaInfoUpdated(playingMediaInfo)
          }
        }

        else -> {}
      }
    }

    override fun onSessionDestroyed() {
      logDebug("onSessionDestroyed")
      destroySession()
    }
  }

  private inner class HideControllersTask() : TimerTask() {
    override fun run() {
      handler.post(
        object : Runnable {
          override fun run() {
            updateControllersVisibility(false)
          }
        })
    }
  }

  private inner class UpdateSeekbarTask() : TimerTask() {
    override fun run() {
      handler.post(
        object : Runnable {
          override fun run() {
            updateSeekbar()
          }
        })
    }
  }

  companion object {
    @JvmStatic
    protected val TAG: String = "PlaybackAdapter"
    private val ASPECT_RATIO: Float = 72f / 128
  }
}

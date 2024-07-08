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

package com.google.sample.cast.refplayer.mediaplayer;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import com.google.android.gms.cast.MediaInfo;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.notification.MediaSessionProxy;
import com.google.sample.cast.refplayer.utils.Utils;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/** An abstract class to handle the local playback. */
public abstract class PlaybackAdapter {
  protected static final String TAG = "PlaybackAdapter";

  /**
   * Callback to provide state update from {@link PlaybackAdapter} to {@link LocalPlayerActivity}.
   */
  interface Callback {
    /** Called when the MediaInfo is updated. */
    void onMediaInfoUpdated(MediaInfo mediaInfo);

    /** Called when the local playback state is changed. */
    void onPlaybackStateChanged();

    /** Called when switching the playback location between local and remote. */
    void onPlaybackLocationChanged();

    /** Called to end the {@link LocalPlayerActivity}. */
    void onDestroy();
  }

  /** indicates whether we are doing a local or a remote playback */
  public enum PlaybackLocation {
    LOCAL,
    REMOTE
  }

  private static final float ASPECT_RATIO = 72f / 128;
  protected final Activity activity;
  protected final Callback playbackAdapterCallback;
  private final String messagePrefix;
  protected final Handler handler = new Handler();
  protected final MediaControllerCompat.Callback mediaControllerCallback;

  protected MediaInfo mediaInfo;
  protected @PlaybackStateCompat.State int playbackState;
  protected PlaybackLocation playbackLocation;
  private View playerContainer;
  private TextView startText;
  private TextView endText;
  private SeekBar seekbar;
  private ImageView playPause;
  private View controllers;
  private ActionBar actionBar;
  @Nullable private Timer controllersTimer;
  @Nullable private Timer seekbarTimer;

  protected PlaybackAdapter(
      Activity activity, Callback playbackAdapterCallback, String messagePrefix) {
    this.activity = activity;
    this.playbackAdapterCallback = playbackAdapterCallback;
    this.messagePrefix = messagePrefix;
    mediaControllerCallback = new MediaControllerCallback();
    playbackState = PlaybackStateCompat.STATE_NONE;
    playbackLocation = PlaybackLocation.LOCAL;
    initializePlayerAndController();
  }

  /** Gets the duration of the media playback. */
  protected abstract long getMediaDuration();

  /** Gets the current position of the media playback. */
  public abstract long getCurrentPosition();

  /** Plays the given {@link MediaInfo} starting from the given position. */
  protected abstract boolean onPlay(MediaInfo mediaInfo, long position);

  protected abstract boolean onPlay();

  protected abstract boolean onPause();

  protected abstract boolean onPauseAndStopMediaNotification();

  protected abstract boolean onSeekTo(int position);

  protected abstract boolean onStop();

  protected abstract boolean onDestroy();

  protected abstract boolean onUpdatePlaybackState();

  public void play(MediaInfo mediaInfo, long position) {
    boolean hasEntity =
        TextUtils.isEmpty(mediaInfo.getContentId()) && !TextUtils.isEmpty(mediaInfo.getEntity());
    String contentUrl = hasEntity ? mediaInfo.getEntity() : mediaInfo.getContentId();
    if (contentUrl == null) {
      return;
    }
    if (onPlay(mediaInfo, position)) {
      logDebug("play mediaInfo with position = %d", position);
      restartTrickplayTimer();
    }
  }

  /** Stops the player. The player can be restart if needed. */
  public void stop() {
    if (onStop()) {
      logDebug("stop");
      stopTrickplayTimer();
      stopControllersTimer();
    }
  }

  /** Destroys the player. The player can not be restart. */
  public void destroy() {
    if (onDestroy()) {
      logDebug("destroy");
      stopTrickplayTimer();
      stopControllersTimer();
    }
  }

  /** Pauses the playback of the media. */
  public void pause() {
    if (onPause()) {
      logDebug("pause");
      stopTrickplayTimer();
    }
  }

  /** Pauses the playback of the media and stop media notification. */
  public void pauseAndStopMediaNotification() {
    if (onPauseAndStopMediaNotification()) {
      logDebug("pauseAndStopMediaNotification");
      stopTrickplayTimer();
    }
  }

  /** Plays the playback of the media. */
  public void play() {
    if (onPlay()) {
      logDebug("play");
      restartTrickplayTimer();
    }
  }

  protected void seekTo(int position) {
    if (onSeekTo(position)) {
      logDebug("seekTo %d", position);
    }
  }

  protected void destroySession() {
    playbackAdapterCallback.onDestroy();
  }

  protected void updatePlaybackState() {
    if (onUpdatePlaybackState()) {
      logDebug("update playback state to " + playbackState);
      updatePlayPauseImageView();
      playbackAdapterCallback.onPlaybackStateChanged();
    }
  }

  public MediaInfo getPlayingMedia() {
    return mediaInfo;
  }

  public PlaybackLocation getPlaybackLocation() {
    return playbackLocation;
  }

  /** Returns {@code true} if the player has a local playback. */
  public boolean isPlaybackLocal() {
    return getPlaybackLocation() == PlaybackLocation.LOCAL;
  }

  /** Returns {@code true} if the player is in the playing or the paused state. */
  public boolean isActive() {
    return playbackState == PlaybackStateCompat.STATE_PLAYING
        || playbackState == PlaybackStateCompat.STATE_PAUSED;
  }

  /** Returns {@code true} if the player is in the playing state. */
  public boolean isPlaying() {
    return playbackState == PlaybackStateCompat.STATE_PLAYING;
  }

  /** Updates the view of the player and the controllers. */
  public void updateViewForOrientation(Point displaySize, ActionBar actionBar) {
    this.actionBar = actionBar;
    boolean orientationPortrait = Utils.isOrientationPortrait(activity);
    updateSupportActionBarVisibility(orientationPortrait);

    RelativeLayout.LayoutParams layoutParams;
    if (orientationPortrait) {
      layoutParams =
          new RelativeLayout.LayoutParams(displaySize.x, (int) (displaySize.x * ASPECT_RATIO));
      layoutParams.addRule(RelativeLayout.BELOW, R.id.toolbar);
    } else {
      layoutParams =
          new RelativeLayout.LayoutParams(displaySize.x, displaySize.y + actionBar.getHeight());
      layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    }
    playerContainer.setLayoutParams(layoutParams);
  }

  /** Setups the seekbar with the duration of the media. */
  public void setupSeekbar() {
    int duration = (int) getMediaDuration();
    logDebug("setup seekbar with duration = " + duration);
    endText.setText(Utils.formatMillis(duration));
    seekbar.setMax(duration);
    restartTrickplayTimer();
  }

  private void updatePlayPauseImageView() {
    playPause.setVisibility(isActive() ? View.VISIBLE : View.INVISIBLE);
    if (isActive()) {
      int playPauseResourceId =
          isPlaying() ? R.drawable.ic_av_pause_dark : R.drawable.ic_av_play_dark;
      playPause.setImageDrawable(activity.getResources().getDrawable(playPauseResourceId));
    }
  }

  protected void updateControllers() {
    updateControllersVisibility(true);
    startControllersTimer();
  }

  // should be called from the main thread
  public void updateControllersVisibility(boolean toShow) {
    controllers.setVisibility(toShow ? View.VISIBLE : View.INVISIBLE);
    updateSupportActionBarVisibility(toShow);
  }

  protected void logDebug(String message, Object... args) {
    String formattedMessage =
        String.format(
            "[%s] %s",
            messagePrefix,
            (args.length == 0) ? message : String.format(Locale.ROOT, message, args));
    Log.d(TAG, formattedMessage);
  }

  private void initializePlayerAndController() {
    Activity activity = this.activity;
    playerContainer = (View) activity.findViewById(R.id.player_container);

    startText = (TextView) activity.findViewById(R.id.startText);
    endText = (TextView) activity.findViewById(R.id.endText);

    seekbar = (SeekBar) activity.findViewById(R.id.seekBar1);
    seekbar.setOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            seekTo(seekBar.getProgress());
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            startText.setText(Utils.formatMillis(progress));
          }
        });

    playPause = (ImageView) activity.findViewById(R.id.playPauseImageView);
    playPause.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            if (isPlaying()) {
              pause();
            } else {
              play();
            }
          }
        });

    controllers = activity.findViewById(R.id.controllers);
  }

  private void stopControllersTimer() {
    if (controllersTimer != null) {
      controllersTimer.cancel();
      controllersTimer = null;
    }
  }

  private void startControllersTimer() {
    stopControllersTimer();
    controllersTimer = new Timer();
    controllersTimer.schedule(new HideControllersTask(), 5000);
  }

  private void stopTrickplayTimer() {
    if (seekbarTimer != null) {
      seekbarTimer.cancel();
      seekbarTimer = null;
    }
  }

  protected void restartTrickplayTimer() {
    stopTrickplayTimer();
    seekbarTimer = new Timer();
    seekbarTimer.scheduleAtFixedRate(new UpdateSeekbarTask(), 100, 1000);
  }

  private void updateSeekbar() {
    int duration = (int) getMediaDuration();
    int position = (int) getCurrentPosition();
    seekbar.setProgress(position);
    seekbar.setMax(duration);
    startText.setText(Utils.formatMillis(position));
    endText.setText(Utils.formatMillis(duration));
  }

  private void updateSupportActionBarVisibility(boolean toShow) {
    if (actionBar == null) {
      return;
    }
    if (toShow) {
      actionBar.show();
    } else if (!Utils.isOrientationPortrait(activity)) {
      actionBar.hide();
    }
  }

  /** A class to transfer the playback state from MediaSession. */
  private class MediaControllerCallback extends MediaControllerCompat.Callback {
    @Override
    public void onPlaybackStateChanged(@Nullable final PlaybackStateCompat playbackStateCompat) {
      logDebug("onPlaybackStateChanged with playbackStateCompat = " + playbackStateCompat);
      if (playbackStateCompat == null) {
        return;
      }
      playbackState = playbackStateCompat.getState();
      updatePlaybackState();
    }

    @Override
    public void onMetadataChanged(final MediaMetadataCompat metadata) {
      logDebug("onMetadataChanged");
      setupSeekbar();
    }

    /** Receives the customized event from the {@link MediaSessionProxy}. */
    @Override
    public void onSessionEvent(String event, Bundle bundle) {
      logDebug("onSessionEvent with event = " + event);
      switch (event) {
        case MediaSessionProxy.EVENT_ON_TRANSFER_LOCATION:
          PlaybackLocation playbackLocationLocal =
              (PlaybackLocation) bundle.get(MediaSessionProxy.EVENT_KEY_LOCATION);
          if (playbackLocationLocal != null) {
            playbackLocation = playbackLocationLocal;
            logDebug("update playbackLocation to " + playbackLocation);
            playbackState = PlaybackStateCompat.STATE_NONE;
            playbackAdapterCallback.onPlaybackLocationChanged();
          }
          break;
        case MediaSessionProxy.EVENT_ON_UPDATE_MEDIA_INFO:
          MediaInfo playingMediaInfo = bundle.getParcelable(MediaSessionProxy.EVENT_KEY_MEDIA_INFO);
          if (playingMediaInfo != null) {
            playbackAdapterCallback.onMediaInfoUpdated(playingMediaInfo);
          }
          break;
        default:
          // Do nothing.
      }
    }

    @Override
    public void onSessionDestroyed() {
      logDebug("onSessionDestroyed");
      destroySession();
    }
  }

  private class HideControllersTask extends TimerTask {
    @Override
    public void run() {
      handler.post(
          new Runnable() {
            @Override
            public void run() {
              updateControllersVisibility(false);
            }
          });
    }
  }

  private class UpdateSeekbarTask extends TimerTask {
    @Override
    public void run() {
      handler.post(
          new Runnable() {
            @Override
            public void run() {
              updateSeekbar();
            }
          });
    }
  }
}

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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.framework.CastSession;
import com.google.sample.cast.refplayer.mediaplayer.PlaybackAdapter.PlaybackLocation;
import java.util.Locale;

/** An interface for the local media player. */
public abstract class LocalMediaPlayer {
  protected static final String TAG = "LocalMediaPlayer";

  /** Callback to provide state update from {@link LocalMediaPlayer}. */
  public interface Callback {
    /** Called when the media is loaded. */
    void onMediaLoaded();

    /** Called when the local playback state is changed. */
    void onPlaybackStateChanged(PlaybackStateCompat state);

    /** Called when switching from local playback to a remote playback. */
    void onPlaybackLocationChanged();
  }

  public static final String KEY_MEDIA_INFO =
      "com.google.sample.cast.refplayer.mediaplayer.MEDIA_INFO";
  public static final String KEY_START_POSITION =
      "com.google.sample.cast.refplayer.mediaplayer.START_POSITION";

  private final String messagePrefix;
  protected Callback callback;
  @Nullable protected MediaInfo mediaInfo;
  protected long startPosition;
  protected @PlaybackStateCompat.State int playbackState;
  private final RemotePlaybackHelper remotePlaybackHelper;

  public LocalMediaPlayer(Context context, String messagePrefix) {
    this.messagePrefix = messagePrefix;
    remotePlaybackHelper = new RemotePlaybackHelper(context, new RemotePlaybackHelperCallback());
    remotePlaybackHelper.registerSessionManagerListener();
    playbackState = PlaybackStateCompat.STATE_NONE;
  }

  public void addCallback(Callback callback) {
    this.callback = callback;
  }

  protected abstract boolean onPlay(String mediaId, Bundle bundle);

  protected abstract boolean onPlay();

  protected abstract boolean onPause();

  protected abstract boolean onSeekTo(int position);

  protected abstract boolean onStop();

  protected abstract boolean onDestroy();

  public abstract long getDuration();

  protected abstract long getCurrentPosition();

  public void play(String mediaId, Bundle bundle) {
    mediaInfo = bundle.getParcelable(KEY_MEDIA_INFO);
    if (mediaInfo == null) {
      return;
    }
    remotePlaybackHelper.setMedia(mediaInfo);
    startPosition = bundle.getLong(KEY_START_POSITION);

    if (onPlay(mediaId, bundle)) {
      logDebug("play media from position " + startPosition + " when isActive = " + isActive());
    }
  }

  public void play() {
    if (onPlay()) {
      logDebug("play playback");
      playbackState = PlaybackStateCompat.STATE_PLAYING;
      notifyPlaybackStateChanged();
    }
  }

  public void pause() {
    if (onPause()) {
      logDebug("pause playback");
      playbackState = PlaybackStateCompat.STATE_PAUSED;
      notifyPlaybackStateChanged();
    }
  }

  public void seekTo(int position) {
    if (onSeekTo(position)) {
      logDebug("seek playback to " + position);
      notifyPlaybackStateChanged();
    }
  }

  public void stop() {
    if (onStop()) {
      logDebug("stop playback");
      playbackState = PlaybackStateCompat.STATE_STOPPED;
      notifyPlaybackStateChanged();
    }
  }

  public void destroy() {
    if (onDestroy()) {
      if (remotePlaybackHelper != null) {
        remotePlaybackHelper.unregisterSessionManagerListener();
      }
    }
  }

  public void setVolume(float volume) {}

  @Nullable
  public MediaInfo getMediaInfo() {
    return mediaInfo;
  }

  public RemotePlaybackHelper getRemotePlaybackHelper() {
    return remotePlaybackHelper;
  }

  protected boolean isActive() {
    return playbackState == PlaybackStateCompat.STATE_PLAYING
        || playbackState == PlaybackStateCompat.STATE_PAUSED;
  }

  protected boolean isPlaying() {
    return playbackState == PlaybackStateCompat.STATE_PLAYING;
  }

  public void notifyPlaybackStateChanged() {
    PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
    long actions = getAvailableActions(playbackState);
    if (actions > 0) {
      stateBuilder.setActions(actions);
    }
    stateBuilder.setState(playbackState, getCurrentPosition(), 1.0f);
    callback.onPlaybackStateChanged(stateBuilder.build());
  }

  @PlaybackStateCompat.Actions
  private long getAvailableActions(@PlaybackStateCompat.State int state) {
    long actions = 0L;
    switch (state) {
      case PlaybackStateCompat.STATE_STOPPED:
        actions |= PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
        break;
      case PlaybackStateCompat.STATE_PLAYING:
        actions |=
            PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_SEEK_TO;
        break;
      case PlaybackStateCompat.STATE_PAUSED:
        actions |=
            PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_SEEK_TO;
        break;
      default:
        // Do nothing.
    }
    return actions;
  }

  public PlaybackLocation getPlaybackLocation() {
    return remotePlaybackHelper.isRemotePlayback()
        ? PlaybackLocation.REMOTE
        : PlaybackLocation.LOCAL;
  }

  protected void logDebug(String message, Object... args) {
    String formattedMessage =
        String.format(
            "[%s] %s",
            messagePrefix,
            (args.length == 0) ? message : String.format(Locale.ROOT, message, args));
    Log.d(TAG, formattedMessage);
  }

  /** A class to transfer the cast session state update from {@link RemotePlaybackHelper}. */
  private class RemotePlaybackHelperCallback implements RemotePlaybackHelper.Callback {
    @Override
    public void onApplicationConnected(CastSession castSession) {
      logDebug("Cast session is connected to an application on the receiver");
      if (isActive() && mediaInfo != null) {
        remotePlaybackHelper.loadRemoteMedia(getCurrentPosition(), isPlaying());
        callback.onPlaybackLocationChanged();
        stop();
      } else {
        playbackState = PlaybackStateCompat.STATE_NONE;
        callback.onPlaybackLocationChanged();
      }
    }

    @Override
    public void onApplicationDisconnected() {
      logDebug("Cast session is disconnected from the receiver");
      if (isActive()) {
        return;
      }
      playbackState = PlaybackStateCompat.STATE_NONE;
      callback.onPlaybackLocationChanged();
    }
  }
}

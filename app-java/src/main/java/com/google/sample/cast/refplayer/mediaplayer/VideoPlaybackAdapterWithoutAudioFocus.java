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
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.VideoView;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.framework.CastSession;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.utils.Utils;

/** A class to handle the video playback that can not play in the background. */
public class VideoPlaybackAdapterWithoutAudioFocus extends PlaybackAdapter {

  private final RemotePlaybackHelper remotePlaybackHelper;
  private VideoView videoView;
  private long startPosition;

  public VideoPlaybackAdapterWithoutAudioFocus(
      Activity activity, PlaybackAdapter.Callback playbackAdapterCallback) {
    super(activity, playbackAdapterCallback, "videoWoAudioFocus");
    initializeVideoView();
    remotePlaybackHelper = new RemotePlaybackHelper(activity, new RemotePlaybackHelperCallback());
    remotePlaybackHelper.registerSessionManagerListener();
  }

  @Override
  public boolean onPlay(MediaInfo mediaInfo, long position) {
    loadMediaInfo(mediaInfo);
    startPosition = position;
    return true;
  }

  private void play(long position) {
    videoView.seekTo((int) position);
    videoView.requestFocus();
    videoView.start();
    playbackState = PlaybackStateCompat.STATE_PLAYING;
    updatePlaybackState();
  }

  @Override
  protected boolean onPause() {
    videoView.pause();
    playbackState = PlaybackStateCompat.STATE_PAUSED;
    updatePlaybackState();
    return true;
  }

  @Override
  protected boolean onPauseAndStopMediaNotification() {
    return onPause();
  }

  @Override
  protected boolean onPlay() {
    play(getCurrentPosition());
    return true;
  }

  @Override
  protected boolean onSeekTo(int position) {
    videoView.seekTo(position);
    updatePlaybackState();
    return true;
  }

  @Override
  protected boolean onStop() {
    videoView.stopPlayback();
    playbackState = PlaybackStateCompat.STATE_STOPPED;
    updatePlaybackState();
    return true;
  }

  @Override
  protected boolean onDestroy() {
    remotePlaybackHelper.unregisterSessionManagerListener();
    return true;
  }

  @Override
  protected boolean onUpdatePlaybackState() {
    if (isActive()) {
      updateControllers();
    } else {
      updateControllersVisibility(false);
    }
    return true;
  }

  @Override
  public PlaybackLocation getPlaybackLocation() {
    return remotePlaybackHelper.isRemotePlayback()
        ? PlaybackLocation.REMOTE
        : PlaybackLocation.LOCAL;
  }

  @Override
  public long getMediaDuration() {
    return isActive() ? videoView.getDuration() : 0L;
  }

  @Override
  public long getCurrentPosition() {
    return isActive() ? videoView.getCurrentPosition() : 0L;
  }

  private void initializeVideoView() {
    videoView = (VideoView) activity.findViewById(R.id.videoView1);

    videoView.setOnPreparedListener(
        new OnPreparedListener() {
          @Override
          public void onPrepared(MediaPlayer mediaPlayer) {
            setupSeekbar();
            play(startPosition);
          }
        });

    videoView.setOnCompletionListener(
        new OnCompletionListener() {
          @Override
          public void onCompletion(MediaPlayer mediaPlayer) {
            logDebug("onCompletion is reached");
            stop();
          }
        });

    videoView.setOnTouchListener(
        new OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            updateControllers();
            return false;
          }
        });

    videoView.setOnErrorListener(
        new OnErrorListener() {
          @Override
          public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
            Log.e(
                TAG,
                "OnErrorListener.onError(): VideoView encountered an "
                    + "error, what: "
                    + what
                    + ", extra: "
                    + extra);
            String message;
            if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
              message = activity.getString(R.string.video_error_media_load_timeout);
            } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
              message = activity.getString(R.string.video_error_server_unaccessible);
            } else {
              message = activity.getString(R.string.video_error_unknown_error);
            }
            Utils.showErrorDialog(activity, message);
            stop();
            return true;
          }
        });
  }

  private void loadMediaInfo(MediaInfo mediaInfo) {
    this.mediaInfo = mediaInfo;
    remotePlaybackHelper.setMedia(mediaInfo);
    if (TextUtils.isEmpty(mediaInfo.getContentId()) && !TextUtils.isEmpty(mediaInfo.getEntity())) {
      videoView.setVideoPath(mediaInfo.getEntity());
    } else {
      videoView.setVideoURI(Uri.parse(mediaInfo.getContentId()));
    }
  }

  /**
   * A class to transfer the cast session state update from {@link RemotePlaybackHelper} to {@link
   * VideoPlaybackAdapter}.
   */
  private class RemotePlaybackHelperCallback implements RemotePlaybackHelper.Callback {
    @Override
    public void onApplicationConnected(CastSession castSession) {
      logDebug("Cast session is connected to an application on the receiver");
      if (isActive()) {
        remotePlaybackHelper.loadRemoteMedia(getCurrentPosition(), isPlaying());
        playbackState = PlaybackStateCompat.STATE_NONE;
        playbackAdapterCallback.onDestroy();
      } else {
        playbackState = PlaybackStateCompat.STATE_NONE;
        playbackAdapterCallback.onPlaybackLocationChanged();
      }
    }

    @Override
    public void onApplicationDisconnected() {
      logDebug("Cast session is disconnected from the receiver");
      if (!isActive()) {
        playbackState = PlaybackStateCompat.STATE_NONE;
        playbackAdapterCallback.onPlaybackLocationChanged();
      }
    }
  }
}

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
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.VideoView;
import com.google.android.gms.cast.MediaInfo;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.utils.Utils;

/** A player to host the video local playback. */
public class VideoMediaPlayer extends LocalMediaPlayer {

  private static final String TAG = "VideoMediaPlayer";

  /** Callback to provide state update from {@link VideoMediaPlayer}. */
  public interface Callback {
    /** Called when the media is loaded. */
    void onMediaLoaded();

    /** Called when the local playback state is changed. */
    void onPlaybackStateChanged(PlaybackStateCompat state);

    /** Called when switching from local playback to a remote playback. */
    void onPlaybackLocationChanged();
  }

  private final Activity activity;
  private VideoView videoView;

  public VideoMediaPlayer(Activity activity) {
    super(activity, "video");
    this.activity = activity;
    initializeVideoView();
  }

  @Override
  public boolean onPlay(String mediaId, Bundle bundle) {
    MediaInfo mediaInfo = this.mediaInfo;
    if (TextUtils.isEmpty(mediaInfo.getContentId()) && !TextUtils.isEmpty(mediaInfo.getEntity())) {
      videoView.setVideoPath(mediaInfo.getEntity());
    } else {
      videoView.setVideoURI(Uri.parse(mediaInfo.getContentId()));
    }

    return true;
  }

  @Override
  protected boolean onPlay() {
    videoView.requestFocus();
    videoView.start();
    return true;
  }

  @Override
  protected boolean onPause() {
    videoView.pause();
    return true;
  }

  @Override
  protected boolean onSeekTo(int position) {
    videoView.seekTo(position);
    return true;
  }

  @Override
  protected boolean onStop() {
    videoView.stopPlayback();
    return true;
  }

  @Override
  public boolean onDestroy() {
    return true;
  }

  @Override
  public long getDuration() {
    return isActive() ? videoView.getDuration() : 0L;
  }

  @Override
  protected long getCurrentPosition() {
    return isActive() ? videoView.getCurrentPosition() : 0L;
  }

  private void initializeVideoView() {
    videoView = (VideoView) activity.findViewById(R.id.videoView1);

    videoView.setOnPreparedListener(
        new OnPreparedListener() {
          @Override
          public void onPrepared(MediaPlayer mediaPlayer) {
            logDebug("onPrepared");
            callback.onMediaLoaded();
            videoView.seekTo((int) startPosition);
            play();
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
            notifyPlaybackStateChanged();
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
}

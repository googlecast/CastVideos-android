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
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import com.google.android.gms.cast.MediaInfo;
import com.google.sample.cast.refplayer.notification.MediaSessionProxy;

/**
 * A class to handle the video playback that can not play in the background, but it posts a media
 * notification for the local video playback when the app is in the foreground.
 */
public class VideoPlaybackAdapter extends PlaybackAdapter {

  private final MediaSessionProxy mediaSessionProxy;
  private final MediaControllerCompat mediaController;
  private VideoMediaPlayer localMediaPlayer;

  public VideoPlaybackAdapter(Activity activity, PlaybackAdapter.Callback playbackAdapterCallback) {
    super(activity, playbackAdapterCallback, "video");

    localMediaPlayer = new VideoMediaPlayer(activity);
    mediaSessionProxy = new MediaSessionProxy(activity, localMediaPlayer);

    MediaSessionCompat.Token token = mediaSessionProxy.getMediaSessionToken();
    mediaController = new MediaControllerCompat(activity, token);
    MediaControllerCompat.setMediaController(activity, mediaController);
    mediaController.registerCallback(mediaControllerCallback);

    // If the video local player is not active, then check if it is casting to remote device or not.
    @PlaybackStateCompat.State int playbackState = mediaController.getPlaybackState().getState();
    boolean isActive =
        playbackState == PlaybackStateCompat.STATE_PLAYING
            || playbackState == PlaybackStateCompat.STATE_PAUSED;
    if (!isActive) {
      mediaController.sendCommand(
          MediaSessionProxy.ACTION_UPDATE_PLAYBACK_LOCATION, new Bundle(), null);
    }
  }

  @Override
  public boolean onPlay(MediaInfo mediaInfo, long position) {
    this.mediaInfo = mediaInfo;

    boolean hasEntity =
        TextUtils.isEmpty(mediaInfo.getContentId()) && !TextUtils.isEmpty(mediaInfo.getEntity());
    String contentUrl = hasEntity ? mediaInfo.getEntity() : mediaInfo.getContentId();
    if (contentUrl == null) {
      return false;
    }

    Bundle bundle = new Bundle();
    bundle.putParcelable(LocalMediaPlayer.KEY_MEDIA_INFO, mediaInfo);
    bundle.putLong(LocalMediaPlayer.KEY_START_POSITION, position);
    mediaController.getTransportControls().playFromMediaId(contentUrl, bundle);

    return true;
  }

  @Override
  protected boolean onPause() {
    mediaController.getTransportControls().pause();
    return true;
  }

  @Override
  protected boolean onPauseAndStopMediaNotification() {
    mediaController.getTransportControls().pause();
    mediaSessionProxy.setStopMediaNotificationAfterPaused(true);
    return true;
  }

  @Override
  protected boolean onPlay() {
    mediaController.getTransportControls().play();
    return true;
  }

  @Override
  protected boolean onSeekTo(int position) {
    mediaController.getTransportControls().seekTo(position);
    return true;
  }

  @Override
  protected boolean onStop() {
    mediaController.getTransportControls().stop();
    return true;
  }

  @Override
  protected boolean onDestroy() {
    if (mediaController != null && mediaControllerCallback != null) {
      mediaController.unregisterCallback(mediaControllerCallback);
    }
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
  public long getMediaDuration() {
    return (mediaController != null)
        ? mediaController.getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        : 0L;
  }

  @Override
  public long getCurrentPosition() {
    return (mediaController != null) ? mediaController.getPlaybackState().getPosition() : 0L;
  }
}

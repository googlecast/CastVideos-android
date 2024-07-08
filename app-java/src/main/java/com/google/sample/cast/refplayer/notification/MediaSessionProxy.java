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

package com.google.sample.cast.refplayer.notification;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.session.PlaybackStateCompat.CustomAction;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media.utils.MediaConstants;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.mediaplayer.LocalMediaPlayer;
import com.google.sample.cast.refplayer.mediaplayer.PlaybackAdapter.PlaybackLocation;
import com.google.sample.cast.refplayer.utils.AsyncBitmap;

/** A proxy of Media Session. */
public class MediaSessionProxy {

  private static final String TAG = "MediaSessionProxy";

  public static final String EVENT_ON_TRANSFER_LOCATION =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.onTransferLocation";
  public static final String EVENT_KEY_LOCATION =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.keyLocation";
  public static final String ACTION_UPDATE_PLAYBACK_LOCATION =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionUpdatePlaybackLocation";

  public static final String EVENT_ON_UPDATE_MEDIA_INFO =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.onUpdateMediaInfo";
  public static final String EVENT_KEY_MEDIA_INFO =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.keyMediaInfo";
  public static final String ACTION_UPDATE_MEDIA_INFO =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionUpdateMediaInfo";

  public static final String ACTION_UPDATE_PLAYBACK_STATE =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionUpdatePlaybackState";

  private static final String ACTION_STOP =
      "com.google.sample.cast.refplayer.notification.MediaSessionProxy.actionStop";

  private final Context context;
  private final LocalNotificationProxy localNotificationProxy;
  @Nullable private final AudioManager audioManager;
  @Nullable private AudioFocusHelper audioFocusHelper;
  @Nullable private LocalMediaPlayer localMediaPlayer;
  @Nullable private MediaSessionCompat mediaSessionCompat;
  private MediaMetadataCompat.Builder metadataBuilder;
  private PlaybackStateCompat.Builder playbackStateBuilder;
  @Nullable private CustomAction stopCustomAction;
  @Nullable private Boolean stopMediaNotificationAfterPaused;

  public MediaSessionProxy(Context context, LocalMediaPlayer localMediaPlayer) {
    this.context = context;
    localNotificationProxy = new LocalNotificationProxy(context);
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    audioFocusHelper = new AudioFocusHelper();
    this.localMediaPlayer = localMediaPlayer;
    localMediaPlayer.addCallback(new LocalMediaPlayerCallback());

    // Create a MediaSessionCompat for the local media playback.
    MediaSessionCompat mediaSessionCompat = new MediaSessionCompat(context, "CastVideoSample");
    mediaSessionCompat.setFlags(
        MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
            | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
    mediaSessionCompat.setCallback(new MediaSessionCallback());
    playbackStateBuilder =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID);
    mediaSessionCompat.setPlaybackState(playbackStateBuilder.build());

    metadataBuilder = new MediaMetadataCompat.Builder();
    mediaSessionCompat.setMetadata(metadataBuilder.build());
    this.mediaSessionCompat = mediaSessionCompat;
  }

  @Nullable
  public MediaSessionCompat getMediaSession() {
    return mediaSessionCompat;
  }

  @Nullable
  public MediaSessionCompat.Token getMediaSessionToken() {
    return (mediaSessionCompat != null) ? mediaSessionCompat.getSessionToken() : null;
  }

  public void destroy() {
    Log.d(TAG, "destroy");
    if (audioFocusHelper != null) {
      audioFocusHelper.abandonAudioFocus();
      audioFocusHelper = null;
    }
    stopLocalNotification();
    if (localMediaPlayer != null) {
      localMediaPlayer.destroy();
      localMediaPlayer = null;
    }
    if (mediaSessionCompat != null) {
      mediaSessionCompat.release();
      mediaSessionCompat = null;
    }
  }

  private void notifyPlaybackLocation() {
    if (localMediaPlayer == null || mediaSessionCompat == null) {
      return;
    }
    PlaybackLocation location = localMediaPlayer.getPlaybackLocation();
    Log.d(TAG, "notify the playback location: " + location);
    Bundle bundle = new Bundle();
    bundle.putSerializable(EVENT_KEY_LOCATION, location);
    mediaSessionCompat.sendSessionEvent(EVENT_ON_TRANSFER_LOCATION, bundle);
  }

  // It is safe to catch newly defined exception types ForegroundServiceStartNotAllowedException on
  // older Android versions that do not define that exception type.
  @SuppressWarnings("NewApi")
  private void startLocalNotification() {
    Log.d(TAG, "start media notification for local playback");
    localNotificationProxy.updateNotification(this.mediaSessionCompat);
  }

  private void stopLocalNotification() {
    Log.d(TAG, "stop media notification for local playback");
    localNotificationProxy.cancelNotification();
  }

  public void setStopMediaNotificationAfterPaused(boolean stopMediaNotificationAfterPaused) {
    this.stopMediaNotificationAfterPaused = stopMediaNotificationAfterPaused;
  }

  private CustomAction getStopCustomAction() {
    if (stopCustomAction == null) {
      stopCustomAction =
          new CustomAction.Builder(
              ACTION_STOP,
              context
                  .getResources()
                  .getString(R.string.local_playback_notification_stop_action_title),
              R.drawable.ic_av_stop)
              .build();
    }
    return stopCustomAction;
  }

  /** The MediaSession callbacks will transport the controls to {@link LocalMediaPlayer}. */
  public class MediaSessionCallback extends MediaSessionCompat.Callback {
    @Override
    public void onPrepare() {
      Log.d(TAG, "onPrepare");
      if (mediaSessionCompat != null && !mediaSessionCompat.isActive()) {
        mediaSessionCompat.setActive(true);
        mediaSessionCompat.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
      }
    }

    @Override
    public void onPlayFromMediaId(String mediaId, Bundle bundle) {
      if (audioFocusHelper == null || !audioFocusHelper.requestAudioFocus()) {
        return;
      }
      Log.d(TAG, "play mediaId = " + mediaId);
      if (localMediaPlayer != null) {
        localMediaPlayer.play(mediaId, bundle);
      }
    }

    @Override
    public void onPlay() {
      if (audioFocusHelper == null || !audioFocusHelper.requestAudioFocus()) {
        return;
      }
      Log.d(TAG, "onPlay");
      if (localMediaPlayer != null) {
        localMediaPlayer.play();
      }
    }

    @Override
    public void onPause() {
      Log.d(TAG, "onPause");
      if (localMediaPlayer != null) {
        localMediaPlayer.pause();
      }
      if (audioFocusHelper != null) {
        audioFocusHelper.abandonAudioFocus();
      }
    }

    @Override
    public void onSeekTo(long position) {
      Log.d(TAG, "onSeekTo");
      if (localMediaPlayer != null) {
        localMediaPlayer.seekTo((int) position);
      }
    }

    @Override
    public void onStop() {
      Log.d(TAG, "onStop");
      if (localMediaPlayer != null) {
        localMediaPlayer.stop();
      }
      if (audioFocusHelper != null) {
        audioFocusHelper.abandonAudioFocus();
      }
    }

    @Override
    public void onCommand(String command, Bundle extra, ResultReceiver resultReceiver) {
      Log.d(TAG, "onCommand with " + command);
      Bundle bundle = new Bundle();
      switch (command) {
        case ACTION_UPDATE_PLAYBACK_LOCATION:
          notifyPlaybackLocation();
          break;
        case ACTION_UPDATE_MEDIA_INFO:
          if (mediaSessionCompat != null && localMediaPlayer != null) {
            bundle.putParcelable(EVENT_KEY_MEDIA_INFO, localMediaPlayer.getMediaInfo());
            mediaSessionCompat.sendSessionEvent(EVENT_ON_UPDATE_MEDIA_INFO, bundle);
          }
          break;
        case ACTION_UPDATE_PLAYBACK_STATE:
          if (localMediaPlayer != null) {
            localMediaPlayer.notifyPlaybackStateChanged();
          }
          break;
        default:
          // Do nothing.
      }
    }

    @Override
    public void onCustomAction(String action, Bundle extras) {
      Log.d(TAG, "onCustomAction with action = " + action);
      if (TextUtils.equals(action, ACTION_STOP)) {
        onStop();
      }
    }
  }

  /** A callback class to transport the playback states of {@link LocalMediaPlayer}. */
  private class LocalMediaPlayerCallback implements LocalMediaPlayer.Callback {
    private ThumbnailImage thumbnailImage;

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
      if (state == null) {
        return;
      }
      MediaSessionCompat mediaSessionCompat = MediaSessionProxy.this.mediaSessionCompat;
      if (mediaSessionCompat == null) {
        return;
      }
      Log.d(TAG, "onPlaybackStateChanged with state: " + state);
      playbackStateBuilder = new PlaybackStateCompat.Builder(state);
      playbackStateBuilder.addCustomAction(getStopCustomAction());
      mediaSessionCompat.setPlaybackState(playbackStateBuilder.build());
      Bundle bundle = new Bundle();
      bundle.putBoolean(MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV, true);
      bundle.putBoolean(MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT, true);
      mediaSessionCompat.setExtras(bundle);

      if (state.getState() == PlaybackStateCompat.STATE_PLAYING
          || (stopMediaNotificationAfterPaused == null
          && state.getState() == PlaybackStateCompat.STATE_PAUSED)) {
        if (!mediaSessionCompat.isActive()) {
          mediaSessionCompat.setActive(true);
        }
        startLocalNotification();
      } else {
        mediaSessionCompat.setActive(false);
        stopLocalNotification();
        stopMediaNotificationAfterPaused = null;
      }
    }

    @Override
    public void onMediaLoaded() {
      if (localMediaPlayer == null) {
        return;
      }
      long duration = localMediaPlayer.getDuration();
      MediaInfo mediaInfo = localMediaPlayer.getMediaInfo();
      if (mediaInfo == null) {
        return;
      }
      MediaMetadata metadata = mediaInfo.getMetadata();
      if (metadata == null) {
        return;
      }
      Log.d(TAG, "onMediaLoaded with duration = " + duration);
      String title = metadata.getString(MediaMetadata.KEY_TITLE);
      String subtitle = metadata.getString(MediaMetadata.KEY_SUBTITLE);
      if (title != null) {
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title);
      }
      if (subtitle != null) {
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle);
      }
      metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

      WebImage webImage = metadata.hasImages() ? metadata.getImages().get(0) : null;
      Uri imageUrl = (webImage != null) ? webImage.getUrl() : null;
      if (imageUrl != null
          && thumbnailImage != null
          && imageUrl.equals(thumbnailImage.imageUrl)
          && thumbnailImage.getBitmap() != null) {
        Log.d(TAG, "reuse the existing bitmap");
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, thumbnailImage.getBitmap());
      } else if (imageUrl != null) {
        thumbnailImage = new ThumbnailImage(imageUrl);
      }
      if (mediaSessionCompat != null) {
        mediaSessionCompat.setMetadata(metadataBuilder.build());
      }
    }

    @Override
    public void onPlaybackLocationChanged() {
      notifyPlaybackLocation();
    }
  }

  private class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
    @Nullable private AudioFocusRequest audioFocusRequest;

    private boolean requestAudioFocus() {
      if (audioManager == null) {
        return false;
      }

      int result;
      if (VERSION.SDK_INT >= VERSION_CODES.O) {
        // The AudioFocusRequest.Builder() API is only available on Android O+ devices.
        AudioAttributes attributes =
            new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audioFocusRequest =
            new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this)
                .build();
        result = audioManager.requestAudioFocus(audioFocusRequest);
      } else {
        result = audioManager
                .requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
      }
      return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private boolean abandonAudioFocus() {
      if (audioManager == null) {
        return true;
      }
      int result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;

      if (VERSION.SDK_INT >= VERSION_CODES.O) {
        if (audioFocusRequest != null) {
          result = audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
      } else {
        result = audioManager.abandonAudioFocus(this);
      }
      return result == AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
      LocalMediaPlayer localMediaPlayer = MediaSessionProxy.this.localMediaPlayer;
      if (localMediaPlayer == null) {
        Log.d(TAG, "null localMediaPlayer when changing audio focus");
        return;
      }
      switch (focusChange) {
        case AudioManager.AUDIOFOCUS_GAIN:
          Log.d(TAG, "gain audio focus");
          localMediaPlayer.setVolume(1.0f);
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
          Log.d(TAG, "loss audio focus but can duck");
          localMediaPlayer.setVolume(0.5f);
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
          // Fall through.
        case AudioManager.AUDIOFOCUS_LOSS:
          // Fall through.
          Log.d(TAG, "loss audio focus so pause");
          localMediaPlayer.pause();
          break;
        default:
          Log.d(TAG, "ignore unsupported audio focus change");
      }
    }
  }

  private class ThumbnailImage {
    public final Uri imageUrl;
    private final AsyncBitmap asyncBitmap;
    private Bitmap bitmap;

    public Bitmap getBitmap() {
      return bitmap;
    }

    public ThumbnailImage(Uri imageUrl) {
      this.imageUrl = imageUrl;
      asyncBitmap = new AsyncBitmap();
      asyncBitmap.setCallback(
          new AsyncBitmap.Callback() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap) {
              Log.d(TAG, "bitmap is loaded");
              ThumbnailImage.this.bitmap = bitmap;
              metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap);
              if (mediaSessionCompat != null) {
                mediaSessionCompat.setMetadata(metadataBuilder.build());
              }
            }
          });
      asyncBitmap.loadBitmap(imageUrl);
    }
  }
}

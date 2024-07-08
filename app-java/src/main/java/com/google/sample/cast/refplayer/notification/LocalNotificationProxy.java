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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.TaskStackBuilder;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity;

/**
 * A class to manage the notification of local playback and automatically update the notification
 * with the MediaSession.
 */
public class LocalNotificationProxy {
  private static final String TAG = "LocalNotifService";

  private static final int NOTIFICATION_ID = 2;
  private static final String NOTIFICATION_TAG = "localNotification";
  private static final String NOTIFICATION_CHANNEL_ID =
      "com.google.sample.cast.refplayer.media_playback";

  private final Context context;
  @Nullable private final NotificationManager notificationManager;
  private Notification notification;

  // The notification button actions. They are initialized when they are used for the first time.
  // These action buttons can be reused when updating notification as their fields are the same.
  private Action playAction;
  private Action pauseAction;
  private Action stopAction;

  LocalNotificationProxy(Context context) {
    this.context = context;
    notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    createNotificationChannel(context);
  }

  public void updateNotification(@Nullable MediaSessionCompat mediaSession) {
    if (mediaSession == null) {
      Log.d(TAG, "skip updating notification for null mediaSession");
      return;
    }
    MediaControllerCompat controller = mediaSession.getController();
    if (controller == null) {
      Log.d(TAG, "skip updating notification for null controller");
      return;
    }
    MediaMetadataCompat metadata = controller.getMetadata();
    PlaybackStateCompat playbackState = controller.getPlaybackState();
    if (metadata == null || playbackState == null) {
      Log.d(
          TAG,
          "skip updating notification for metadata = "
              + metadata
              + ", playbackState = "
              + playbackState);
      return;
    }
    MediaSessionCompat.Token mediaSessionToken = mediaSession.getSessionToken();
    if (mediaSessionToken == null) {
      Log.d(TAG, "skip updating notification with null token");
      return;
    }

    Log.d(TAG, "updateNotification with playbackState: " + playbackState.getState());
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setStyle(
                new MediaStyle()
                    .setMediaSession(mediaSessionToken)
                    .setShowActionsInCompactView(0, 1))
            .setSmallIcon(R.drawable.ic_stat_action_notification);

    PendingIntent contentIntent = getContentIntent();
    if (contentIntent != null) {
      builder.setContentIntent(getContentIntent());
    }

    String metadataTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
    if (!TextUtils.isEmpty(metadataTitle)) {
      builder.setContentTitle(metadataTitle);
    }
    String metadataArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
    if (!TextUtils.isEmpty(metadataArtist)) {
      builder.setContentText(metadataArtist);
    }
    builder
        .addAction(
            getTogglePlaybackAction(playbackState.getState() == PlaybackStateCompat.STATE_PLAYING))
        .addAction(getStopAction());

    Bitmap bitmap = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
    if (bitmap != null) {
      builder.setLargeIcon(bitmap);
    }

    if (notificationManager != null) {
      notificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, builder.build());
    }
  }

  void cancelNotification() {
    if (notificationManager != null) {
      notificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
    }
  }

  private void createNotificationChannel(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || notificationManager == null) {
      // The notification channel is only required for Android O and above.
      return;
    }
    Log.d(TAG, "create a new notification channel");
    String channelName =
        context.getResources().getString(R.string.local_playback_notification_channel);
    NotificationChannel notificationChannel =
        new NotificationChannel(
            NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
    notificationManager.createNotificationChannel(notificationChannel);
  }

  // Returns the intent to be fired when "Toggle playback" button is tapped.
  private Action getTogglePlaybackAction(boolean isPlaying) {
    return isPlaying ? getPauseAction() : getPlayAction();
  }

  private Action getPlayAction() {
    if (playAction == null) {
      PendingIntent pendingIntent =
          MediaButtonReceiver.buildMediaButtonPendingIntent(
              context, PlaybackStateCompat.ACTION_PLAY);
      playAction =
          new Action.Builder(
              R.drawable.ic_av_play,
              context
                  .getResources()
                  .getString(R.string.local_playback_notification_play_action_title),
              pendingIntent)
              .extend(new Action.WearableExtender().setHintDisplayActionInline(true))
              .build();
    }
    return playAction;
  }

  private Action getPauseAction() {
    if (pauseAction == null) {
      PendingIntent pendingIntent =
          MediaButtonReceiver.buildMediaButtonPendingIntent(
              context, PlaybackStateCompat.ACTION_PAUSE);
      pauseAction =
          new Action.Builder(
              R.drawable.ic_av_pause,
              context
                  .getResources()
                  .getString(R.string.local_playback_notification_pause_action_title),
              pendingIntent)
              .extend(new Action.WearableExtender().setHintDisplayActionInline(true))
              .build();
    }
    return pauseAction;
  }

  private Action getStopAction() {
    if (stopAction == null) {
      PendingIntent pendingIntent =
          MediaButtonReceiver.buildMediaButtonPendingIntent(
              context, PlaybackStateCompat.ACTION_STOP);
      stopAction =
          new Action.Builder(
              R.drawable.ic_av_stop,
              context
                  .getResources()
                  .getString(R.string.local_playback_notification_stop_action_title),
              pendingIntent)
              .extend(new Action.WearableExtender().setHintDisplayActionInline(true))
              .build();
    }
    return stopAction;
  }

  @Nullable
  private PendingIntent getContentIntent() {
    Intent contentIntent = new Intent(context.getApplicationContext(), LocalPlayerActivity.class);
    TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
    stackBuilder.addNextIntentWithParentStack(contentIntent);
    return stackBuilder.getPendingIntent(
        NOTIFICATION_ID, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
}

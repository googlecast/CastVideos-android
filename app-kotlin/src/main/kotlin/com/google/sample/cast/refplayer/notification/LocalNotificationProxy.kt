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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.media.session.MediaButtonReceiver
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity

/**
 * A class to manage the notification of local playback and automatically update the notification
 * with the MediaSession.
 */
class LocalNotificationProxy internal constructor(private val context: Context) {
  private val notificationManager: NotificationManager?
  private val notification: Notification? = null

  // The notification button actions. They are initialized when they are used for the first time.
  // These action buttons can be reused when updating notification as their fields are the same.
  private var playAction: NotificationCompat.Action? = null
    private get() {
      if (field == null) {
        val pendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
          context, PlaybackStateCompat.ACTION_PLAY
        )
        field = NotificationCompat.Action.Builder(
          R.drawable.ic_av_play,
          context
            .resources
            .getString(R.string.local_playback_notification_play_action_title),
          pendingIntent
        )
          .extend(NotificationCompat.Action.WearableExtender().setHintDisplayActionInline(true))
          .build()
      }
      return field
    }
  private var pauseAction: NotificationCompat.Action? = null
    private get() {
      if (field == null) {
        val pendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
          context, PlaybackStateCompat.ACTION_PAUSE
        )
        field = NotificationCompat.Action.Builder(
          R.drawable.ic_av_pause,
          context
            .resources
            .getString(R.string.local_playback_notification_pause_action_title),
          pendingIntent
        )
          .extend(NotificationCompat.Action.WearableExtender().setHintDisplayActionInline(true))
          .build()
      }
      return field
    }
  private var stopAction: NotificationCompat.Action? = null
    private get() {
      if (field == null) {
        val pendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
          context, PlaybackStateCompat.ACTION_STOP
        )
        field = NotificationCompat.Action.Builder(
          R.drawable.ic_av_stop,
          context
            .resources
            .getString(R.string.local_playback_notification_stop_action_title),
          pendingIntent
        )
          .extend(NotificationCompat.Action.WearableExtender().setHintDisplayActionInline(true))
          .build()
      }
      return field
    }

  init {
    notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    createNotificationChannel(context)
  }

  fun updateNotification(mediaSession: MediaSessionCompat?) {
    if (mediaSession == null) {
      Log.d(TAG, "skip updating notification for null mediaSession")
      return
    }
    val controller = mediaSession.controller
    if (controller == null) {
      Log.d(TAG, "skip updating notification for null controller")
      return
    }
    val metadata = controller.metadata
    val playbackState = controller.playbackState
    if (metadata == null || playbackState == null) {
      Log.d(
        TAG,
        "skip updating notification for metadata = "
          + metadata
          + ", playbackState = "
          + playbackState
      )
      return
    }
    val mediaSessionToken = mediaSession.sessionToken
    if (mediaSessionToken == null) {
      Log.d(TAG, "skip updating notification with null token")
      return
    }
    Log.d(TAG, "updateNotification with playbackState: " + playbackState.state)
    val builder = NotificationCompat.Builder(
      context, NOTIFICATION_CHANNEL_ID
    )
      .setStyle(
        androidx.media.app.NotificationCompat.MediaStyle()
          .setMediaSession(mediaSessionToken)
          .setShowActionsInCompactView(0, 1)
      )
      .setSmallIcon(R.drawable.ic_stat_action_notification)
    val contentIntent = contentIntent
    if (contentIntent != null) {
      builder.setContentIntent(this.contentIntent)
    }
    val metadataTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
    if (!TextUtils.isEmpty(metadataTitle)) {
      builder.setContentTitle(metadataTitle)
    }
    val metadataArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
    if (!TextUtils.isEmpty(metadataArtist)) {
      builder.setContentText(metadataArtist)
    }
    builder
      .addAction(
        getTogglePlaybackAction(playbackState.state == PlaybackStateCompat.STATE_PLAYING)
      )
      .addAction(stopAction)
    val bitmap = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
    if (bitmap != null) {
      builder.setLargeIcon(bitmap)
    }
    notificationManager?.notify(NOTIFICATION_TAG, NOTIFICATION_ID, builder.build())
  }

  fun cancelNotification() {
    notificationManager?.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
  }

  private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || notificationManager == null) {
      // The notification channel is only required for Android O and above.
      return
    }
    Log.d(TAG, "create a new notification channel")
    val channelName = context.resources.getString(R.string.local_playback_notification_channel)
    val notificationChannel = NotificationChannel(
      NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW
    )
    notificationManager.createNotificationChannel(notificationChannel)
  }

  // Returns the intent to be fired when "Toggle playback" button is tapped.
  private fun getTogglePlaybackAction(isPlaying: Boolean): NotificationCompat.Action {
    return if (isPlaying) pauseAction!! else playAction!!
  }

  private val contentIntent: PendingIntent?
    private get() {
      val contentIntent = Intent(context.applicationContext, LocalPlayerActivity::class.java)
      val stackBuilder = TaskStackBuilder.create(
        context
      )
      stackBuilder.addNextIntentWithParentStack(contentIntent)
      return stackBuilder.getPendingIntent(
        NOTIFICATION_ID, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }

  companion object {
    private const val TAG = "LocalNotifService"
    private const val NOTIFICATION_ID = 2
    private const val NOTIFICATION_TAG = "localNotification"
    private const val NOTIFICATION_CHANNEL_ID = "com.google.sample.cast.refplayer.media_playback"
  }
}

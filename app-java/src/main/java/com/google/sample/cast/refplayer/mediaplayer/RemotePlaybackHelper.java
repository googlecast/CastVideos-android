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
import android.util.Log;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.sample.cast.refplayer.utils.Utils;

/** A helper class to handle the switch between the local playback and the remote playback. */
public class RemotePlaybackHelper {
  private static final String TAG = "RemotePlaybackHelper";

  /** Callback to provide cast session update from {@link RemotePlaybackHelper}. */
  public interface Callback {
    /** Called when the receiver application is connected. */
    void onApplicationConnected(CastSession castSession);

    /** Called when the receiver application is disconnected. */
    void onApplicationDisconnected();
  }

  private final Callback callback;
  private final CastContext castContext;
  private final CastSessionManagerListener sessionManagerListener;

  private MediaInfo playingMedia;

  public RemotePlaybackHelper(Context context, Callback callback) {
    this.callback = callback;
    castContext = CastContext.getSharedInstance(context);
    sessionManagerListener = new CastSessionManagerListener();
  }

  public void registerSessionManagerListener() {
    castContext
        .getSessionManager()
        .addSessionManagerListener(sessionManagerListener, CastSession.class);
  }

  public void unregisterSessionManagerListener() {
    castContext
        .getSessionManager()
        .removeSessionManagerListener(sessionManagerListener, CastSession.class);
  }

  public void setMedia(MediaInfo media) {
    playingMedia = media;
  }

  /** Returns true if there is a remote playback for a Cast session. */
  public boolean isRemotePlayback() {
    CastSession castSession = castContext.getSessionManager().getCurrentCastSession();
    return (castSession != null) && castSession.isConnected();
  }

  public void loadRemoteMedia(long position, boolean autoPlay) {
    CastSession castSession = castContext.getSessionManager().getCurrentCastSession();
    if (castSession == null) {
      return;
    }
    RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
    if (remoteMediaClient == null) {
      return;
    }

    remoteMediaClient.load(playingMedia, autoPlay, position);
  }

  private class CastSessionManagerListener implements SessionManagerListener<CastSession> {
    @Override
    public void onSessionEnded(CastSession session, int error) {
      Log.d(TAG, "onSessionEnded with error = " + error);
      callback.onApplicationDisconnected();
    }

    @Override
    public void onSessionResumed(CastSession session, boolean wasSuspended) {
      Log.d(TAG, "onSessionResumed with wasSuspended = " + wasSuspended);
      callback.onApplicationConnected(session);
    }

    @Override
    public void onSessionResumeFailed(CastSession session, int error) {
      Log.d(TAG, "onSessionResumeFailed with error = " + error);
      callback.onApplicationDisconnected();
    }

    @Override
    public void onSessionStarted(CastSession session, String sessionId) {
      Log.d(TAG, "onSessionStarted with sessionId = " + sessionId);
      callback.onApplicationConnected(session);
    }

    @Override
    public void onSessionStartFailed(CastSession session, int error) {
      Log.d(TAG, "onSessionStartFailed with error = " + error);
      callback.onApplicationDisconnected();
    }

    @Override
    public void onSessionStarting(CastSession session) {
      Log.d(TAG, "onSessionStarting");
    }

    @Override
    public void onSessionEnding(CastSession session) {
      Log.d(TAG, "onSessionEnding");
    }

    @Override
    public void onSessionResuming(CastSession session, String sessionId) {
      Log.d(TAG, "onSessionResuming with sessionId = " + sessionId);
    }

    @Override
    public void onSessionSuspended(CastSession session, int reason) {
      Log.d(TAG, "onSessionSuspended with reason = " + reason);
    }
  }
}

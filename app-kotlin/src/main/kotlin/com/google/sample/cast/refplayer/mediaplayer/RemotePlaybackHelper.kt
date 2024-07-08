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

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import java.lang.Class

/** A helper class to handle the switch between the local playback and the remote playback.  */
class RemotePlaybackHelper(context: Context?, private val callback: Callback) {
  /** Callback to provide cast session update from [RemotePlaybackHelper].  */
  interface Callback {
    /** Called when the receiver application is connected.  */
    fun onApplicationConnected(castSession: CastSession?)

    /** Called when the receiver application is disconnected.  */
    fun onApplicationDisconnected()
  }

  private val castContext: CastContext
  private var sessionManagerListener: SessionManagerListener<CastSession>? = null
  private var playingMedia: MediaInfo? = null

  init {
    castContext = CastContext.getSharedInstance(context!!)
    sessionManagerListener = CastSessionManagerListener()
  }

  fun registerSessionManagerListener() {
    castContext!!
      .sessionManager
      .addSessionManagerListener(sessionManagerListener!!, CastSession::class.java)
  }

  fun unregisterSessionManagerListener() {
    castContext!!
      .sessionManager
      .removeSessionManagerListener(sessionManagerListener!!, CastSession::class.java)
  }

  fun setMedia(media: MediaInfo?) {
    playingMedia = media
  }


  /** Returns true if there is a remote playback for a Cast session.  */
  val isRemotePlayback: Boolean
    get(){
      val castSession = castContext.sessionManager.currentCastSession
      return castSession != null && castSession.isConnected
    }

  fun loadRemoteMedia(position: Long, autoPlay: Boolean) {
    val castSession = castContext.sessionManager.currentCastSession ?: return
    val remoteMediaClient = castSession.remoteMediaClient ?: return
    remoteMediaClient.load(playingMedia!!, autoPlay, position)
  }

  private inner class CastSessionManagerListener : SessionManagerListener<CastSession> {
    override fun onSessionEnded(session: CastSession, error: Int) {
      Log.d(TAG, "onSessionEnded with error = $error")
      callback.onApplicationDisconnected()
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
      Log.d(TAG, "onSessionResumed with wasSuspended = $wasSuspended")
      callback.onApplicationConnected(session)
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
      Log.d(TAG, "onSessionResumeFailed with error = $error")
      callback.onApplicationDisconnected()
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
      Log.d(TAG, "onSessionStarted with sessionId = $sessionId")
      callback.onApplicationConnected(session)
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
      Log.d(TAG, "onSessionStartFailed with error = $error")
      callback.onApplicationDisconnected()
    }

    override fun onSessionStarting(session: CastSession) {
      Log.d(TAG, "onSessionStarting")
    }

    override fun onSessionEnding(session: CastSession) {
      Log.d(TAG, "onSessionEnding")
    }

    override fun onSessionResuming(session: CastSession, sessionId: String) {
      Log.d(TAG, "onSessionResuming with sessionId = $sessionId")
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
      Log.d(TAG, "onSessionSuspended with reason = $reason")
    }
  }

  companion object {
    private const val TAG = "RemotePlaybackHelper"
  }
}

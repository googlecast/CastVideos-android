package com.google.sample.cast.refplayer

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.cast.SessionState
import com.google.android.gms.cast.framework.SessionTransferCallback
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity

/** A class to monitor and prepare the output switching between different endpoints.  */
class CastSessionTransferCallback(private val context: Context) : SessionTransferCallback() {
  private val handler = Handler(Looper.getMainLooper())
  override fun onTransferred(@TransferType transferType: Int, sessionState: SessionState) {
    Log.d(
      TAG,
      "onTransferred with type = $transferType, sessionState = $sessionState"
    )
    if (transferType == TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) {
      // The cast-to-phone scenario.
      handler.post { resumeLocalPlayback(sessionState) }
    }
  }

  override fun onTransferFailed(
    @TransferType transferType: Int,
    @TransferFailedReason reason: Int,
  ) {
    Log.i(
      TAG,
      "onTransferFailed with type = $transferType, reason = $reason"
    )
  }

  private fun resumeLocalPlayback(sessionState: SessionState?) {
    if (sessionState == null) {
      return
    }
    val mediaLoadRequestData = sessionState.loadRequestData ?: return
    val mediaInfo = mediaLoadRequestData.mediaInfo ?: return
    val startPosition = mediaLoadRequestData.currentTime
    Log.d(
      TAG,
      "Resume local playback with startPosition = $startPosition"
    )
    val intent = Intent(context, LocalPlayerActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.putExtra("media", mediaInfo)
    intent.putExtra("shouldStart", true)
    Log.d(
      TAG,
      "video mode, put startPosition $startPosition in extra"
    )
    // For video apps, resume the local playback when the activity is resumed.
    intent.putExtra("startPosition", startPosition)
    context.startActivity(intent)
  }

  companion object {
    private const val TAG = "CastTransferCallback"
  }
}

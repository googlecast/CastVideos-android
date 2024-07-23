package com.google.sample.cast.refplayer;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.SessionState;
import com.google.android.gms.cast.framework.SessionTransferCallback;
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity;

/** A class to monitor and prepare the output switching between different endpoints. */
public class CastSessionTransferCallback extends SessionTransferCallback {
  private static final String TAG = "CastTransferCallback";

  private final Context context;
  private final Handler handler = new Handler(Looper.getMainLooper());

  public CastSessionTransferCallback(Context context) {
    this.context = context;
  }

  @Override
  public void onTransferred(@TransferType int transferType, SessionState sessionState) {
    Log.d(TAG, "onTransferred with type = " + transferType + ", sessionState = " + sessionState);
    if (transferType == SessionTransferCallback.TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) {
      // The cast-to-phone scenario.
      handler.post(() -> resumeLocalPlayback(sessionState));
    }
  }

  @Override
  public void onTransferFailed(@TransferType int transferType, @TransferFailedReason int reason) {
    Log.i(TAG, "onTransferFailed with type = " + transferType + ", reason = " + reason);
  }

  private void resumeLocalPlayback(SessionState sessionState) {
    if (sessionState == null) {
      return;
    }
    MediaLoadRequestData mediaLoadRequestData = sessionState.getLoadRequestData();
    if (mediaLoadRequestData == null) {
      return;
    }
    MediaInfo mediaInfo = mediaLoadRequestData.getMediaInfo();
    if (mediaInfo == null) {
      return;
    }
    long startPosition = mediaLoadRequestData.getCurrentTime();
    Log.d(TAG, "Resume local playback with startPosition = " + startPosition);
    Intent intent = new Intent(context, LocalPlayerActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra("media", mediaInfo);
    intent.putExtra("shouldStart", true);
    Log.d(TAG, "video mode, put startPosition " + startPosition + " in extra");
    // For video apps, resume the local playback when the activity is resumed.
    intent.putExtra("startPosition", startPosition);
    context.startActivity(intent);
  }
}

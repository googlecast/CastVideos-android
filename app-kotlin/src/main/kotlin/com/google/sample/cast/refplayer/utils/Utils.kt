/*
 * Copyright 2022 Google LLC. All Rights Reserved.
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
package com.google.sample.cast.refplayer.utils

import java.lang.Exception
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import android.content.Context
import android.view.View
import com.google.sample.cast.refplayer.R
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import android.content.Intent
import android.view.MenuItem
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import android.app.AlertDialog
import android.view.WindowManager
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import android.view.Display
import android.content.DialogInterface
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.widget.Toast
import java.util.Locale
import android.graphics.Point
import android.text.TextUtils
import androidx.appcompat.widget.PopupMenu
import com.google.sample.cast.refplayer.expandedcontrols.ExpandedControlsActivity
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A collection of utility methods, all static.
 */
object Utils {
    private const val TAG: String = "Utils"
    private const val PRELOAD_TIME_S: Int = 20
    private val  utilCastExecutor: Executor = Executors.newSingleThreadExecutor();

    /**
     * Returns the screen/display size
     *
     */
    fun getDisplaySize(context: Context): Point {
        val wm: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display: Display = wm.defaultDisplay
        val width: Int = display.width
        val height: Int = display.height
        return Point(width, height)
    }

    /**
     * Returns `true` if and only if the screen orientation is portrait.
     */
    fun isOrientationPortrait(context: Context): Boolean {
        return (context.resources.configuration.orientation
                == Configuration.ORIENTATION_PORTRAIT)
    }

    /**
     * Shows an error dialog with a given text message.
     */
    fun showErrorDialog(context: Context?, errorString: String?) {
        AlertDialog.Builder(context).setTitle(R.string.error)
            .setMessage(errorString)
            .setPositiveButton(R.string.ok, object : DialogInterface.OnClickListener {
                public override fun onClick(dialog: DialogInterface, id: Int) {
                    dialog.cancel()
                }
            })
            .create()
            .show()
    }

    /**
     * Gets the version of app.
     */
    fun getAppVersionName(context: Context): String? {
        var versionString: String? = null
        try {
            val info: PackageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0 /* basic info */
            )
            versionString = info.versionName
        } catch (e: Exception) {
            //do nothing
            Log.w(TAG,e.message,e);
        }
        return versionString
    }

    /**
     * Formats time from milliseconds to hh:mm:ss string format.
     */
    fun formatMillis(millisec: Int): String {
        var seconds: Int = (millisec / 1000)
        val hours: Int = seconds / (60 * 60)
        seconds %= (60 * 60)
        val minutes: Int = seconds / 60
        seconds %= 60
        val time: String = if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
        return time
    }

    /**
     * Show a popup to select whether the selected item should play immediately, be added to the
     * end of queue or be added to the queue right after the current item.
     */
    fun showQueuePopup(context: Context?, view: View?, mediaInfo: MediaInfo?) {
        val castSession: CastSession? =
            CastContext.getSharedInstance(context!!,utilCastExecutor).result.sessionManager.currentCastSession
        if (castSession == null || !castSession.isConnected) {
            Log.w(TAG, "showQueuePopup(): not connected to a cast device")
            return
        }
        val remoteMediaClient: RemoteMediaClient? = castSession.remoteMediaClient
        if (remoteMediaClient == null) {
            Log.w(TAG, "showQueuePopup(): null RemoteMediaClient")
            return
        }
        val provider: QueueDataProvider? = QueueDataProvider.Companion.getInstance(context)
        val popup = PopupMenu((context)!!, (view)!!)
        popup.menuInflater.inflate(
            if (provider!!.isQueueDetached || provider!!.count == 0) R.menu.detached_popup_add_to_queue else R.menu.popup_add_to_queue,
            popup.menu
        )
        val clickListener: PopupMenu.OnMenuItemClickListener =
            object : PopupMenu.OnMenuItemClickListener {
                override fun onMenuItemClick(menuItem: MenuItem): Boolean {
                    val queueItem: MediaQueueItem =
                        MediaQueueItem.Builder((mediaInfo)!!).setAutoplay(
                            true
                        ).setPreloadTime(PRELOAD_TIME_S.toDouble()).build()
                    val newItemArray: Array<MediaQueueItem> = arrayOf(queueItem)
                    var toastMessage: String? = null
                    if (provider?.count == 0) {
                        remoteMediaClient.queueLoad(
                            newItemArray, 0,
                            MediaStatus.REPEAT_MODE_REPEAT_OFF, JSONObject()
                        )
                    } else {
                        val currentId: Int = provider!!.currentItemId
                        if (menuItem.itemId == R.id.action_play_now) {
                            remoteMediaClient.queueInsertAndPlayItem(queueItem, currentId, JSONObject() )
                        } else if (menuItem.itemId == R.id.action_play_next) {
                            val currentPosition: Int = provider!!.getPositionByItemId(currentId)
                            if (currentPosition == provider!!.count - 1) {
                                //we are adding to the end of queue
                                remoteMediaClient.queueAppendItem(queueItem, JSONObject() )
                            } else {
                                val nextItem: MediaQueueItem? =
                                    provider.getItem(currentPosition + 1)
                                if (nextItem != null) {
                                    val nextItemId: Int = nextItem.itemId
                                    remoteMediaClient.queueInsertItems(
                                        newItemArray,
                                        nextItemId,
                                        JSONObject()
                                    )
                                } else {
                                    //remote queue is not ready with item; try again.
                                    return false
                                }
                            }
                            toastMessage = context.getString(
                                R.string.queue_item_added_to_play_next
                            )
                        } else if (menuItem.itemId == R.id.action_add_to_queue) {
                            remoteMediaClient.queueAppendItem(queueItem, JSONObject() )
                            toastMessage = context.getString(R.string.queue_item_added_to_queue)
                        } else {
                            return false
                        }
                    }
                    if (menuItem.getItemId() == R.id.action_play_now) {
                        val intent: Intent = Intent(context, ExpandedControlsActivity::class.java)
                        context!!.startActivity(intent)
                    }
                    if (!TextUtils.isEmpty(toastMessage)) {
                        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
            }
        popup.setOnMenuItemClickListener(clickListener)
        popup.show()
    }
}
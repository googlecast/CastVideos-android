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
package com.google.sample.cast.refplayer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

/**
 * A class that loads [android.graphics.Bitmap] instances with expected width and height
 * asynchronously.
 */
class AsyncBitmap @JvmOverloads constructor(
  private val preferredWidth: Int = 0,
  private val preferredHeight: Int = 0,
) {
  private var url: Uri? = null
  private var bitmap: Bitmap? = null
  private var callback: Callback? = null

  /** The callback interface for notifying the loaded [android.graphics.Bitmap].  */
  interface Callback {
    /** Called when `bitmap` is loaded.  */
    fun onBitmapLoaded(bitmap: Bitmap?)
  }
  /**
   * Creates an [AsyncBitmap] that loads [android.graphics.Bitmap] with expected width
   * and height.
   *
   * @param preferredWidth the preferred width of the image.
   * @param preferredHeight the preferred height of the image.
   */
  /**
   * Creates an [AsyncBitmap] that loads [android.graphics.Bitmap] with any width and
   * height.
   */
  init {
    reset()
  }

  /** Sets a [Callback] to be notified when an [android.graphics.Bitmap] is loaded.  */
  fun setCallback(callback: Callback?) {
    this.callback = callback
  }

  /**
   * Loads image pointed by `url` asynchronously. The image will be delivered as an [ ] via [Callback]. If this method is called again with the same
   * `url` and the bitmap is still loading, and the result will be delivered to the callback
   * once it is loaded. If this method is called again with a different `url`, and the
   * currently loading or loaded bitmap will be discarded, and the new `url` will start
   * loading. If this method is called with a `null` `url`, and then the currently
   * loading or loaded bitmap will be discarded.
   *
   * @param url the [android.net.Uri] to load.
   */
  fun loadBitmap(url: Uri?) {
    if (url == null) {
      // Calling loadBitmap with a null URL should reset the state of the AsyncBitmap.
      reset()
      return
    }
    if (url == this.url) {
      // Calling loadBitmap with the same URL will use the previously loaded bitmap.
      return
    }

    // Calling loadBitmap with a different URL should reset the previously loaded bitmap.
    reset()
    this.url = url
    val task: FetchBitmapTask = FetchBitmapTask(
      preferredWidth,
      preferredHeight
    )
    task.executeTask(url)
  }

  /**
   * Clears the state of this instance by discarding the loading or loaded bitmap and remove the
   * [Callback].
   */
  fun clear() {
    reset()
    callback = null
  }

  fun onPostExecute(bitmap: Bitmap?) {
    this.bitmap = bitmap
    if (callback != null) {
      callback!!.onBitmapLoaded(this.bitmap)
    }
  }

  private fun reset() {
    url = null
    bitmap = null
  }

  /**
   * An AsyncTask to fetch an image over HTTP, optionally apply subsampling while downloading, and
   * scale to the desired size after downloading.
   */
  inner class FetchBitmapTask
  /**
   * Constructs a new FetchBitmapTask that applies subsampling before downloading the image, and
   * optionally applies scaling.
   *
   * @param preferredWidth The preferred image width after subsampling and scaling.
   * @param preferredHeight The preferred image height after subsampling and scaling.
   */(private val preferredWidth: Int, private val preferredHeight: Int) :
    AsyncTask<Uri?, Void?, Bitmap?>() {
    fun executeTask(uri: Uri?): AsyncTask<Uri?, Void?, Bitmap?> {
      return executeOnExecutor(THREAD_POOL_EXECUTOR, uri)
    }

    override fun onPostExecute(bitmap: Bitmap?) {
      this@AsyncBitmap.onPostExecute(bitmap)
    }

    protected override fun doInBackground(vararg uris: Uri?): Bitmap? {
      if (uris.size != 1 || uris[0] == null) {
        return null
      }
      var bitmap: Bitmap? = null
      val url: URL
      url = try {
        URL(uris[0].toString())
      } catch (e: MalformedURLException) {
        Log.w(TAG, "Malformed URL.", e)
        return null
      }
      val options = BitmapFactory.Options()
      options.inJustDecodeBounds = false
      options.inSampleSize = 1
      if (preferredWidth > 0 && preferredHeight > 0) {
        // This is done to do appropriate resampling when the image is too large for the desired
        // target size; instead of downloading the original image and resizing that (which can run
        // into OOM exception), we find an appropriate sample size and only
        // adjust the options to download the resized version.
        val originalSize = calculateOriginalDimensions(url)
        if (originalSize.x > 0 && originalSize.y > 0) {
          options.inSampleSize = calculateSampleSize(
            originalSize.x, originalSize.y,
            preferredWidth,
            preferredHeight
          )
        }
      }
      var urlConnection: HttpURLConnection? = null
      try {
        urlConnection = url.openConnection() as HttpURLConnection
        if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
          bitmap = BitmapFactory.decodeStream(urlConnection!!.inputStream, null, options)
        }
      } catch (e: IOException) {
        Log.w(
          TAG,
          "Failed to open connection to $url", e
        )
      } finally {
        urlConnection?.disconnect()
      }
      return bitmap
    }

    /** Returns the original size of the image.  */
    private fun calculateOriginalDimensions(url: URL): Point {
      val options = BitmapFactory.Options()
      options.inJustDecodeBounds = true
      var connection: HttpURLConnection? = null
      var originalWidth = 0
      var originalHeight = 0
      try {
        connection = url.openConnection() as HttpURLConnection
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
          BitmapFactory.decodeStream(connection!!.inputStream, null, options)
          originalWidth = options.outWidth
          originalHeight = options.outHeight
        }
      } catch (e: IOException) {
        Log.w(
          TAG,
          "Failed to open connection to $url", e
        )
      } finally {
        connection?.disconnect()
      }
      return Point(originalWidth, originalHeight)
    }

    /**
     * Find the appropriate sample-size (as an inverse power of 2) to help reduce the size of
     * downloaded image.
     */
    private fun calculateSampleSize(
      origWidth: Int,
      origHeight: Int,
      reqWidth: Int,
      reqHeight: Int,
    ): Int {
      var sampleSize = 1
      while (reqWidth * sampleSize * 2 < origWidth && reqHeight * sampleSize * 2 < origHeight) {
        sampleSize *= 2
      }
      return sampleSize
    }
  }

  companion object {
    private const val TAG = "AsyncBitmap"
  }
}

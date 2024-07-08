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

package com.google.sample.cast.refplayer.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A class that loads {@link android.graphics.Bitmap} instances with expected width and height
 * asynchronously.
 */
public class AsyncBitmap {
  private static final String TAG = "AsyncBitmap";

  private final int preferredWidth;
  private final int preferredHeight;

  @Nullable private Uri url;
  @Nullable private Bitmap bitmap;
  @Nullable private Callback callback;

  /** The callback interface for notifying the loaded {@link android.graphics.Bitmap}. */
  public interface Callback {
    /** Called when {@code bitmap} is loaded. */
    void onBitmapLoaded(Bitmap bitmap);
  }

  /**
   * Creates an {@link AsyncBitmap} that loads {@link android.graphics.Bitmap} with any width and
   * height.
   */
  public AsyncBitmap() {
    this(0, 0);
  }

  /**
   * Creates an {@link AsyncBitmap} that loads {@link android.graphics.Bitmap} with expected width
   * and height.
   *
   * @param preferredWidth the preferred width of the image.
   * @param preferredHeight the preferred height of the image.
   */
  public AsyncBitmap(int preferredWidth, int preferredHeight) {
    this.preferredWidth = preferredWidth;
    this.preferredHeight = preferredHeight;
    reset();
  }

  /** Sets a {@link Callback} to be notified when an {@link android.graphics.Bitmap} is loaded. */
  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  /**
   * Loads image pointed by {@code url} asynchronously. The image will be delivered as an {@link
   * android.graphics.Bitmap} via {@link Callback}. If this method is called again with the same
   * {@code url} and the bitmap is still loading, and the result will be delivered to the callback
   * once it is loaded. If this method is called again with a different {@code url}, and the
   * currently loading or loaded bitmap will be discarded, and the new {@code url} will start
   * loading. If this method is called with a {@code null} {@code url}, and then the currently
   * loading or loaded bitmap will be discarded.
   *
   * @param url the {@link android.net.Uri} to load.
   */
  public void loadBitmap(@Nullable Uri url) {
    if (url == null) {
      // Calling loadBitmap with a null URL should reset the state of the AsyncBitmap.
      reset();
      return;
    }
    if (url.equals(this.url)) {
      // Calling loadBitmap with the same URL will use the previously loaded bitmap.
      return;
    }

    // Calling loadBitmap with a different URL should reset the previously loaded bitmap.
    reset();
    this.url = url;
    FetchBitmapTask task = new FetchBitmapTask(preferredWidth, preferredHeight);
    task.executeTask(url);
  }

  /**
   * Clears the state of this instance by discarding the loading or loaded bitmap and remove the
   * {@link Callback}.
   */
  public void clear() {
    reset();
    callback = null;
  }

  public void onPostExecute(Bitmap bitmap) {
    this.bitmap = bitmap;
    if (callback != null) {
      callback.onBitmapLoaded(this.bitmap);
    }
  }

  private void reset() {
    url = null;
    bitmap = null;
  }

  /**
   * An AsyncTask to fetch an image over HTTP, optionally apply subsampling while downloading, and
   * scale to the desired size after downloading.
   */
  public class FetchBitmapTask extends AsyncTask<Uri, Void, Bitmap> {

    private final int preferredWidth;
    private final int preferredHeight;

    /**
     * Constructs a new FetchBitmapTask that applies subsampling before downloading the image, and
     * optionally applies scaling.
     *
     * @param preferredWidth The preferred image width after subsampling and scaling.
     * @param preferredHeight The preferred image height after subsampling and scaling.
     */
    public FetchBitmapTask(int preferredWidth, int preferredHeight) {
      this.preferredWidth = preferredWidth;
      this.preferredHeight = preferredHeight;
    }

    public AsyncTask<Uri, Void, Bitmap> executeTask(Uri uri) {
      return executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
      AsyncBitmap.this.onPostExecute(bitmap);
    }

    @Override
    @Nullable
    protected Bitmap doInBackground(Uri... uris) {
      if (uris.length != 1 || uris[0] == null) {
        return null;
      }

      Bitmap bitmap = null;
      URL url;
      try {
        url = new URL(uris[0].toString());
      } catch (MalformedURLException e) {
        Log.w(TAG, "Malformed URL.", e);
        return null;
      }
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = false;
      options.inSampleSize = 1;
      if ((preferredWidth > 0) && (preferredHeight > 0)) {
        // This is done to do appropriate resampling when the image is too large for the desired
        // target size; instead of downloading the original image and resizing that (which can run
        // into OOM exception), we find an appropriate sample size and only
        // adjust the options to download the resized version.
        Point originalSize = calculateOriginalDimensions(url);
        if ((originalSize.x > 0) && (originalSize.y > 0)) {
          options.inSampleSize =
              calculateSampleSize(originalSize.x, originalSize.y, preferredWidth, preferredHeight);
        }
      }
      HttpURLConnection urlConnection = null;
      try {
        urlConnection = (HttpURLConnection) url.openConnection();
        if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          bitmap = BitmapFactory.decodeStream(urlConnection.getInputStream(), null, options);
        }
      } catch (IOException e) {
        Log.w(TAG, "Failed to open connection to " + url, e);
      } finally {
        if (urlConnection != null) {
          urlConnection.disconnect();
        }
      }
      return bitmap;
    }

    /** Returns the original size of the image. */
    private Point calculateOriginalDimensions(URL url) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      HttpURLConnection connection = null;
      int originalWidth = 0;
      int originalHeight = 0;
      try {
        connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          BitmapFactory.decodeStream(connection.getInputStream(), null, options);
          originalWidth = options.outWidth;
          originalHeight = options.outHeight;
        }
      } catch (IOException e) {
        Log.w(TAG, "Failed to open connection to " + url, e);
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
      return new Point(originalWidth, originalHeight);
    }

    /**
     * Find the appropriate sample-size (as an inverse power of 2) to help reduce the size of
     * downloaded image.
     */
    private int calculateSampleSize(int origWidth, int origHeight, int reqWidth, int reqHeight) {
      int sampleSize = 1;
      while (((reqWidth * sampleSize * 2) < origWidth)
          && ((reqHeight * sampleSize * 2) < origHeight)) {
        sampleSize *= 2;
      }
      return sampleSize;
    }
  }
}

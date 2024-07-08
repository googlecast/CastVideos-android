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

package com.google.sample.cast.refplayer.mediaplayer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.media.MediaUtils;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.browser.VideoProvider;
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity;
import com.google.sample.cast.refplayer.settings.CastPreference;
import com.google.sample.cast.refplayer.utils.AsyncBitmap;
import com.google.sample.cast.refplayer.utils.Utils;
import org.json.JSONObject;

/**
 * Activity for the local media player.
 */
public class LocalPlayerActivity extends AppCompatActivity {
    private static final String TAG = "LocalPlayerActivity";

    private String coverArtUrl;

    private TextView titleView;
    private TextView descriptionView;
    private View container;
    private ImageView coverArt;
    private MediaInfo selectedMedia;
    private TextView authorView;
    private ImageButton playCircle;
    private long startPosition;
    private PlaybackAdapter playbackAdapter;
    private boolean shouldStart;

    public LocalPlayerActivity() {}

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_activity);
        setTitle(R.string.app_name);

        Log.d(TAG, "onCreate() was called");

        // For Android T+ devices, the local video playback posts a media notification when the app is
        // in foreground.
        playbackAdapter = (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
                    ? new VideoPlaybackAdapter(this, new PlaybackAdapterCallback())
                    : new VideoPlaybackAdapterWithoutAudioFocus(this, new PlaybackAdapterCallback());

        refreshViewFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent() was called");
        refreshViewFromIntent(intent);
    }

    private void refreshViewFromIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            MediaInfo mediaInfo = intent.getParcelableExtra("media");
            if (mediaInfo == null) {
                return;
            }
            // Obtain the media info and other information from extra bundle.
            selectedMedia = mediaInfo;
            loadViews();
            setupActionBar();
            startPosition = bundle.getLong("startPosition", 0);

            // For video apps, the playback will be resumed in the onResumed() if shouldStart is true.
            shouldStart = bundle.getBoolean("shouldStart");
        }
        updateMetadata(true);
    }

    /** Sets cover art status. */
    private void setCoverArtStatus() {
        boolean isHidden =
            playbackAdapter.isPlaybackLocal() && playbackAdapter.isPlaying();
        coverArt.setVisibility(isHidden ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause() was called");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() was called");
        if (playbackAdapter != null && playbackAdapter.isPlaying()) {
            Log.d(TAG, "pause the playing video that will be resumed later");
            startPosition = playbackAdapter.getCurrentPosition();
            shouldStart = true;
            playbackAdapter.pauseAndStopMediaNotification();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() is called");
        playbackAdapter.destroy();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() was called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() was called");
        playbackAdapter.updatePlaybackState();

        if (playbackAdapter.isPlaybackLocal() && shouldStart) {
            Log.d(TAG, "start playing media from position = " + startPosition);
            playbackAdapter.play(selectedMedia, startPosition);
            shouldStart = false;
        }
    }

    private void updatePlayButton() {
        Log.d(
            TAG,
            "updatePlaybackButton with isPlaybackLocal = "
                + playbackAdapter.isPlaybackLocal()
                + ", isActive = "
                + playbackAdapter.isActive());
        // The playCircle is always shown except for local active playback.
        playCircle.setVisibility(
            (playbackAdapter.isPlaybackLocal() && playbackAdapter.isActive())
                ? View.GONE
                : View.VISIBLE);

        // The controller is only shown and used for local playback.
        playbackAdapter.updateControllersVisibility(playbackAdapter.isPlaybackLocal());

        setCoverArtStatus();
    }

    @SuppressLint("NewApi")
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getSupportActionBar().show();
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow()
                .setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
            updateMetadata(false);
            container.setBackgroundColor(getResources().getColor(R.color.black));
        } else {
            getWindow()
                .setFlags(
                    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
            updateMetadata(true);
            container.setBackgroundColor(getResources().getColor(R.color.white));
        }
    }

    private void updateMetadata(boolean visible) {
        if (selectedMedia == null) {
            return;
        }
        MediaMetadata metadata = selectedMedia.getMetadata();
        if (metadata == null) {
            return;
        }
        if (titleView != null) {
            titleView.setText(metadata.getString(MediaMetadata.KEY_TITLE));
            titleView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        JSONObject customData = selectedMedia.getCustomData();
        if (descriptionView != null && customData != null) {
            // Description is never set in tests.
            descriptionView.setText(customData.optString(VideoProvider.KEY_DESCRIPTION));
            descriptionView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (authorView != null) {
            authorView.setText(metadata.getString(MediaMetadata.KEY_SUBTITLE));
            authorView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        Point displaySize = Utils.getDisplaySize(this);
        playbackAdapter.updateViewForOrientation(displaySize, getSupportActionBar());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.player, menu);
        CastButtonFactory.setUpMediaRouteButton(
                getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean hasSession = !playbackAdapter.isPlaybackLocal();
        menu.findItem(R.id.action_show_queue).setVisible(hasSession);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        if (item.getItemId() == R.id.action_settings) {
            intent = new Intent(LocalPlayerActivity.this, CastPreference.class);
            startActivity(intent);
        } else if (item.getItemId() == R.id.action_show_queue) {
            intent = new Intent(LocalPlayerActivity.this, QueueListViewActivity.class);
            startActivity(intent);
        } else if (item.getItemId() == android.R.id.home) {
            ActivityCompat.finishAfterTransition(this);
        }
        return true;
    }

    private void setupActionBar() {
        MediaMetadata metadata = selectedMedia.getMetadata();
        if (metadata == null) {
            return;
        }
        String title = metadata.getString(MediaMetadata.KEY_TITLE);
        if (title == null) {
            return;
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void loadViews() {
        titleView = (TextView) findViewById(R.id.titleTextView);
        descriptionView = (TextView) findViewById(R.id.descriptionTextView);
        descriptionView.setMovementMethod(new ScrollingMovementMethod());
        authorView = (TextView) findViewById(R.id.authorTextView);

        container = findViewById(R.id.container);
        coverArt = (ImageView) findViewById(R.id.coverArtView);

        String url = MediaUtils.getImageUrl(selectedMedia, 0);
        if (url != null && !TextUtils.equals(coverArtUrl, url)) {
            // Set the coverArt to null before fetching in case the fetching failed and we never update
            // the image.
            coverArt.setImageBitmap(null);
            AsyncBitmap coverArtAsyncBitmap = new AsyncBitmap();
            coverArtAsyncBitmap.setCallback(
                new AsyncBitmap.Callback() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap) {
                        coverArt.setImageBitmap(bitmap);
                    }
                });
            coverArtAsyncBitmap.loadBitmap(Uri.parse(url));
            coverArtUrl = url;
        }

        ViewCompat.setTransitionName(coverArt, getString(R.string.transition_image));
        playCircle = (ImageButton) findViewById(R.id.play_circle);
        playCircle.setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (playbackAdapter.isPlaybackLocal()) {
                        playbackAdapter.play(selectedMedia, 0);
                        shouldStart = false;
                    } else {
                        Utils.showQueuePopup(LocalPlayerActivity.this, playCircle, selectedMedia);
                    }
                }
            });
    }

    /**
     * A class to transfer the playback state of {@link PlaybackAdapter} to {@link
     * LocalPlayerActivity} and update the UI of {@link LocalPlayerActivity}.
     */
    private class PlaybackAdapterCallback implements PlaybackAdapter.Callback {
        @Override
        public void onMediaInfoUpdated(MediaInfo playingMedia) {
            if (playingMedia == null) {
                return;
            }

            if (selectedMedia == null) {
                // When the previous LocalPlayerActivity has been destroyed, clicking on the local playback
                // notification will launch a new LocalPlaybackActivity.
                Log.d(TAG, "update the selectedMedia with the playing media");
                selectedMedia = playingMedia;
                updateMetadata(true);
                return;
            }

            MediaMetadata playingMetadata = playingMedia.getMetadata();
            MediaMetadata selectedMetadata = selectedMedia.getMetadata();
            if (playingMetadata == null || selectedMetadata == null) {
                return;
            }

            String playingMediaTitle = playingMetadata.getString(MediaMetadata.KEY_TITLE);
            String selectedMediaTitle = selectedMetadata.getString(MediaMetadata.KEY_TITLE);
            if (TextUtils.equals(selectedMediaTitle, playingMediaTitle)) {
                Log.d(TAG, "update the seekbar for the playing media");
                playbackAdapter.setupSeekbar();
            } else {
                Log.d(TAG, "stop the playing media playback");
                playbackAdapter.stop();
            }
        }

        @Override
        public void onPlaybackStateChanged() {
            updatePlayButton();
        }

        @Override
        public void onPlaybackLocationChanged() {
            updatePlayButton();
            LocalPlayerActivity.this.invalidateOptionsMenu();
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "destroy LocalPlayerActivity");
            LocalPlayerActivity.this.finish();
        }
    }
}

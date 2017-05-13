/*
 * Copyright (C) 2016 Google Inc. All Rights Reserved.
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

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.MediaUtils;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.browser.VideoProvider;
import com.google.sample.cast.refplayer.expandedcontrols.ExpandedControlsActivity;
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity;
import com.google.sample.cast.refplayer.settings.CastPreference;
import com.google.sample.cast.refplayer.utils.Utils;

import com.androidquery.AQuery;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity for the local media player.
 */
public class LocalPlayerActivity extends AppCompatActivity {

    private static final String TAG = "LocalPlayerActivity";
    private VideoView videoView;
    private TextView titleView;
    private TextView descriptionView;
    private TextView startText;
    private TextView endText;
    private SeekBar seekBar;
    private ImageView playPause;
    private ProgressBar loading;
    private View controllers;
    private View container;
    private ImageView coverArt;
    private Timer seekBarTimer;
    private Timer controllersTimer;
    private PlaybackLocation location;
    private PlaybackState playbackState;
    private final Handler handler = new Handler();
    private final float aspectRatio = 72f / 128;
    private AQuery androidQuery;
    private MediaInfo selectedMedia;
    private boolean controllersVisible;
    private int duration;
    private TextView authorView;
    private ImageButton playCircle;
    private CastContext castContext;
    private CastSession castSession;
    private SessionManagerListener<CastSession> sessionManagerListener;
    private MenuItem queueMenuItem;

    /**
     * indicates whether we are doing a local or a remote playback
     */
    public enum PlaybackLocation {
        LOCAL,
        REMOTE
    }

    /**
     * List of various states that we can be in
     */
    public enum PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_activity);
        androidQuery = new AQuery(this);
        loadViews();
        setupControlsCallbacks();
        setupCastListener();
        castContext = CastContext.getSharedInstance(this);
        castContext.registerLifecycleCallbacksBeforeIceCreamSandwich(this, savedInstanceState);
        castSession = castContext.getSessionManager().getCurrentCastSession();
        // see what we need to play and where
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            selectedMedia = getIntent().getParcelableExtra("media");
            setupActionBar();
            boolean shouldStartPlayback = bundle.getBoolean("shouldStart");
            int startPosition = bundle.getInt("startPosition", 0);
            videoView.setVideoURI(Uri.parse(selectedMedia.getContentId()));
            Log.d(TAG, "Setting url of the VideoView to: " + selectedMedia.getContentId());
            if (shouldStartPlayback) {
                // this will be the case only if we are coming from the
                // CastControllerActivity by disconnecting from a device
                playbackState = PlaybackState.PLAYING;
                updatePlaybackLocation(PlaybackLocation.LOCAL);
                updatePlayButton(playbackState);
                if (startPosition > 0) {
                    videoView.seekTo(startPosition);
                }
                videoView.start();
                startControllersTimer();
            } else {
                // we should load the video but pause it
                // and show the album art.
                if (castSession != null && castSession.isConnected()) {
                    updatePlaybackLocation(PlaybackLocation.REMOTE);
                } else {
                    updatePlaybackLocation(PlaybackLocation.LOCAL);
                }
                playbackState = PlaybackState.IDLE;
                updatePlayButton(playbackState);
            }
        }
        if (titleView != null) {
            updateMetadata(true);
        }
    }

    private void setupCastListener() {
        sessionManagerListener = new SessionManagerListener<CastSession>() {

            @Override
            public void onSessionEnded(CastSession session, int error) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionResumed(CastSession session, boolean wasSuspended) {
                onApplicationConnected(session);
            }

            @Override
            public void onSessionResumeFailed(CastSession session, int error) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionStarted(CastSession session, String sessionId) {
                onApplicationConnected(session);
            }

            @Override
            public void onSessionStartFailed(CastSession session, int error) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionStarting(CastSession session) {
            }

            @Override
            public void onSessionEnding(CastSession session) {
            }

            @Override
            public void onSessionResuming(CastSession session, String sessionId) {
            }

            @Override
            public void onSessionSuspended(CastSession session, int reason) {
            }

            private void onApplicationConnected(CastSession castSession) {
                LocalPlayerActivity.this.castSession = castSession;
                if (null != selectedMedia) {

                    if (playbackState == PlaybackState.PLAYING) {
                        videoView.pause();
                        loadRemoteMedia(seekBar.getProgress(), true);
                        return;
                    } else {
                        playbackState = PlaybackState.IDLE;
                        updatePlaybackLocation(PlaybackLocation.REMOTE);
                    }
                }
                updatePlayButton(playbackState);
                invalidateOptionsMenu();
            }

            private void onApplicationDisconnected() {
                updatePlaybackLocation(PlaybackLocation.LOCAL);
                playbackState = PlaybackState.IDLE;
                location = PlaybackLocation.LOCAL;
                updatePlayButton(playbackState);
                invalidateOptionsMenu();
            }
        };
    }

    private void updatePlaybackLocation(PlaybackLocation location) {
        this.location = location;
        if (location == PlaybackLocation.LOCAL) {
            if (playbackState == PlaybackState.PLAYING
                    || playbackState == PlaybackState.BUFFERING) {
                setCoverArtStatus(null);
                startControllersTimer();
            } else {
                stopControllersTimer();
                setCoverArtStatus(MediaUtils.getImageUrl(selectedMedia, 0));
            }
        } else {
            stopControllersTimer();
            setCoverArtStatus(MediaUtils.getImageUrl(selectedMedia, 0));
            updateControllersVisibility(false);
        }
    }

    private void play(int position) {
        startControllersTimer();
        switch (location) {
            case LOCAL:
                videoView.seekTo(position);
                videoView.start();
                break;
            case REMOTE:
                playbackState = PlaybackState.BUFFERING;
                updatePlayButton(playbackState);
                castSession.getRemoteMediaClient().seek(position);
                break;
            default:
                break;
        }
        restartTrickplayTimer();
    }

    private void togglePlayback() {
        stopControllersTimer();
        switch (playbackState) {
            case PAUSED:
                switch (location) {
                    case LOCAL:
                        videoView.start();
                        Log.d(TAG, "Playing locally...");
                        playbackState = PlaybackState.PLAYING;
                        startControllersTimer();
                        restartTrickplayTimer();
                        updatePlaybackLocation(PlaybackLocation.LOCAL);
                        break;
                    case REMOTE:
                        loadRemoteMedia(0, true);
                        finish();
                        break;
                    default:
                        break;
                }
                break;

            case PLAYING:
                playbackState = PlaybackState.PAUSED;
                videoView.pause();
                break;

            case IDLE:
                switch (location) {
                    case LOCAL:
                        videoView.setVideoURI(Uri.parse(selectedMedia.getContentId()));
                        videoView.seekTo(0);
                        videoView.start();
                        playbackState = PlaybackState.PLAYING;
                        restartTrickplayTimer();
                        updatePlaybackLocation(PlaybackLocation.LOCAL);
                        break;
                    case REMOTE:
                        if (castSession != null && castSession.isConnected()) {
                            Utils.showQueuePopup(this, playCircle, selectedMedia);
                        }
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
        updatePlayButton(playbackState);
    }

    private void loadRemoteMedia(int position, boolean autoPlay) {
        if (castSession == null) {
            return;
        }
        final RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }
        remoteMediaClient.addListener(new RemoteMediaClient.Listener() {
            @Override
            public void onStatusUpdated() {
                Intent intent = new Intent(LocalPlayerActivity.this, ExpandedControlsActivity.class);
                startActivity(intent);
                remoteMediaClient.removeListener(this);
            }

            @Override
            public void onMetadataUpdated() {
            }

            @Override
            public void onQueueStatusUpdated() {
            }

            @Override
            public void onPreloadStatusUpdated() {
            }

            @Override
            public void onSendingRemoteMediaRequest() {
            }

            @Override
            public void onAdBreakStatusUpdated() {
            }
        });
        remoteMediaClient.load(selectedMedia, autoPlay, position);
    }

    private void setCoverArtStatus(String url) {
        if (url != null) {
            androidQuery.id(coverArt).image(url);
            coverArt.setVisibility(View.VISIBLE);
            videoView.setVisibility(View.INVISIBLE);
        } else {
            coverArt.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
        }
    }

    private void stopTrickplayTimer() {
        Log.d(TAG, "Stopped TrickPlay Timer");
        if (seekBarTimer != null) {
            seekBarTimer.cancel();
        }
    }

    private void restartTrickplayTimer() {
        stopTrickplayTimer();
        seekBarTimer = new Timer();
        seekBarTimer.scheduleAtFixedRate(new UpdateSeekbarTask(), 100, 1000);
        Log.d(TAG, "Restarted TrickPlay Timer");
    }

    private void stopControllersTimer() {
        if (controllersTimer != null) {
            controllersTimer.cancel();
        }
    }

    private void startControllersTimer() {
        if (controllersTimer != null) {
            controllersTimer.cancel();
        }
        if (location == PlaybackLocation.REMOTE) {
            return;
        }
        controllersTimer = new Timer();
        controllersTimer.schedule(new HideControllersTask(), 5000);
    }

    // should be called from the main thread
    private void updateControllersVisibility(boolean show) {
        if (show) {
            getSupportActionBar().show();
            controllers.setVisibility(View.VISIBLE);
        } else {
            if (!Utils.isOrientationPortrait(this)) {
                getSupportActionBar().hide();
            }
            controllers.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() was called");
        if (location == PlaybackLocation.LOCAL) {

            if (seekBarTimer != null) {
                seekBarTimer.cancel();
                seekBarTimer = null;
            }
            if (controllersTimer != null) {
                controllersTimer.cancel();
            }
            // since we are playing locally, we need to stop the playback of
            // video (if user is not watching, pause it!)
            videoView.pause();
            playbackState = PlaybackState.PAUSED;
            updatePlayButton(PlaybackState.PAUSED);
        }
        castContext.getSessionManager().removeSessionManagerListener(
                sessionManagerListener, CastSession.class);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() was called");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() is called");
        stopControllersTimer();
        stopTrickplayTimer();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart was called");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume() was called");
        castContext.getSessionManager().addSessionManagerListener(
                sessionManagerListener, CastSession.class);
        if (castSession != null && castSession.isConnected()) {
            updatePlaybackLocation(PlaybackLocation.REMOTE);
        } else {
            updatePlaybackLocation(PlaybackLocation.LOCAL);
        }
        if (queueMenuItem != null) {
            queueMenuItem.setVisible(
                    (castSession != null) && castSession.isConnected());
        }
        super.onResume();
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        return castContext.onDispatchVolumeKeyEventBeforeJellyBean(event)
                || super.dispatchKeyEvent(event);
    }

    private class HideControllersTask extends TimerTask {

        @Override
        public void run() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateControllersVisibility(false);
                    controllersVisible = false;
                }
            });

        }
    }

    private class UpdateSeekbarTask extends TimerTask {

        @Override
        public void run() {
            handler.post(new Runnable() {

                @Override
                public void run() {
                    if (location == PlaybackLocation.LOCAL) {
                        int currentPos = videoView.getCurrentPosition();
                        updateSeekbar(currentPos, duration);
                    }
                }
            });
        }
    }

    private void setupControlsCallbacks() {
        videoView.setOnErrorListener(new OnErrorListener() {

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "OnErrorListener.onError(): VideoView encountered an "
                        + "error, what: " + what + ", extra: " + extra);
                String msg;
                if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                    msg = getString(R.string.video_error_media_load_timeout);
                } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    msg = getString(R.string.video_error_server_unaccessible);
                } else {
                    msg = getString(R.string.video_error_unknown_error);
                }
                Utils.showErrorDialog(LocalPlayerActivity.this, msg);
                videoView.stopPlayback();
                playbackState = PlaybackState.IDLE;
                updatePlayButton(playbackState);
                return true;
            }
        });

        videoView.setOnPreparedListener(new OnPreparedListener() {

            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "onPrepared is reached");
                duration = mp.getDuration();
                endText.setText(Utils.formatMillis(duration));
                seekBar.setMax(duration);
                restartTrickplayTimer();
            }
        });

        videoView.setOnCompletionListener(new OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                stopTrickplayTimer();
                Log.d(TAG, "setOnCompletionListener()");
                playbackState = PlaybackState.IDLE;
                updatePlayButton(playbackState);
            }
        });

        videoView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!controllersVisible) {
                    updateControllersVisibility(true);
                }
                startControllersTimer();
                return false;
            }
        });

        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (playbackState == PlaybackState.PLAYING) {
                    play(seekBar.getProgress());
                } else if (playbackState != PlaybackState.IDLE) {
                    videoView.seekTo(seekBar.getProgress());
                }
                startControllersTimer();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopTrickplayTimer();
                videoView.pause();
                stopControllersTimer();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                startText.setText(Utils.formatMillis(progress));
            }
        });

        playPause.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (location == PlaybackLocation.LOCAL) {
                    togglePlayback();
                }
            }
        });
    }

    private void updateSeekbar(int position, int duration) {
        seekBar.setProgress(position);
        seekBar.setMax(duration);
        startText.setText(Utils.formatMillis(position));
        endText.setText(Utils.formatMillis(duration));
    }

    private void updatePlayButton(PlaybackState state) {
        Log.d(TAG, "Controls: PlayBackState: " + state);
        boolean isConnected = (castSession != null)
                && (castSession.isConnected() || castSession.isConnecting());
        controllers.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        playCircle.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        switch (state) {
            case PLAYING:
                loading.setVisibility(View.INVISIBLE);
                playPause.setVisibility(View.VISIBLE);
                playPause.setImageDrawable(
                        getResources().getDrawable(R.drawable.ic_av_pause_dark));
                playCircle.setVisibility(isConnected ? View.VISIBLE : View.GONE);
                break;
            case IDLE:
                playCircle.setVisibility(View.VISIBLE);
                controllers.setVisibility(View.GONE);
                coverArt.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.INVISIBLE);
                break;
            case PAUSED:
                loading.setVisibility(View.INVISIBLE);
                playPause.setVisibility(View.VISIBLE);
                playPause.setImageDrawable(
                        getResources().getDrawable(R.drawable.ic_av_play_dark));
                playCircle.setVisibility(isConnected ? View.VISIBLE : View.GONE);
                break;
            case BUFFERING:
                playPause.setVisibility(View.INVISIBLE);
                loading.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getSupportActionBar().show();
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
            updateMetadata(false);
            container.setBackgroundColor(getResources().getColor(R.color.black));

        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
            updateMetadata(true);
            container.setBackgroundColor(getResources().getColor(R.color.white));
        }
    }

    private void updateMetadata(boolean visible) {
        Point displaySize;
        if (!visible) {
            descriptionView.setVisibility(View.GONE);
            titleView.setVisibility(View.GONE);
            authorView.setVisibility(View.GONE);
            displaySize = Utils.getDisplaySize(this);
            RelativeLayout.LayoutParams lp = new
                    RelativeLayout.LayoutParams(displaySize.x,
                    displaySize.y + getSupportActionBar().getHeight());
            lp.addRule(RelativeLayout.CENTER_IN_PARENT);
            videoView.setLayoutParams(lp);
            videoView.invalidate();
        } else {
            MediaMetadata mm = selectedMedia.getMetadata();
            descriptionView.setText(selectedMedia.getCustomData().optString(
                    VideoProvider.KEY_DESCRIPTION));
            titleView.setText(mm.getString(MediaMetadata.KEY_TITLE));
            authorView.setText(mm.getString(MediaMetadata.KEY_SUBTITLE));
            descriptionView.setVisibility(View.VISIBLE);
            titleView.setVisibility(View.VISIBLE);
            authorView.setVisibility(View.VISIBLE);
            displaySize = Utils.getDisplaySize(this);
            RelativeLayout.LayoutParams lp = new
                    RelativeLayout.LayoutParams(displaySize.x,
                    (int) (displaySize.x * aspectRatio));
            lp.addRule(RelativeLayout.BELOW, R.id.toolbar);
            videoView.setLayoutParams(lp);
            videoView.invalidate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.player, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu,
                R.id.media_route_menu_item);
        queueMenuItem = menu.findItem(R.id.action_show_queue);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_show_queue).setVisible(
                (castSession != null) && castSession.isConnected());
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
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(selectedMedia.getMetadata().getString(MediaMetadata.KEY_TITLE));
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void loadViews() {
        videoView = (VideoView) findViewById(R.id.videoView1);
        titleView = (TextView) findViewById(R.id.titleTextView);
        descriptionView = (TextView) findViewById(R.id.descriptionTextView);
        descriptionView.setMovementMethod(new ScrollingMovementMethod());
        authorView = (TextView) findViewById(R.id.authorTextView);
        startText = (TextView) findViewById(R.id.startText);
        startText.setText(Utils.formatMillis(0));
        endText = (TextView) findViewById(R.id.endText);
        seekBar = (SeekBar) findViewById(R.id.seekBar1);
        playPause = (ImageView) findViewById(R.id.playPauseImageView);
        loading = (ProgressBar) findViewById(R.id.progressBar1);
        controllers = findViewById(R.id.controllers);
        container = findViewById(R.id.container);
        coverArt = (ImageView) findViewById(R.id.coverArtView);
        ViewCompat.setTransitionName(coverArt, getString(R.string.transition_image));
        playCircle = (ImageButton) findViewById(R.id.play_circle);
        playCircle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayback();
            }
        });
    }
}

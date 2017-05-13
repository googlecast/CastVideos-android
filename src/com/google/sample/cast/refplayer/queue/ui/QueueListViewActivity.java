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

package com.google.sample.cast.refplayer.queue.ui;

import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.queue.QueueDataProvider;
import com.google.sample.cast.refplayer.settings.CastPreference;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.List;

/**
 * An activity to show the queue list
 */
public class QueueListViewActivity extends AppCompatActivity {

    private static final String FRAGMENT_LIST_VIEW = "list view";
    private static final String TAG = "QueueListViewActivity";

    private final RemoteMediaClient.Listener remoteMediaClientListener =
            new MyRemoteMediaClientListener();
    private final SessionManagerListener<CastSession> sessionManagerListener =
            new MySessionManagerListener();
    private CastContext castContext;
    private RemoteMediaClient remoteMediaClient;
    private View emptyView;

    private class MySessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionEnded(CastSession session, int error) {
            if (remoteMediaClient != null) {
                remoteMediaClient.removeListener(remoteMediaClientListener);
            }
            remoteMediaClient = null;
            emptyView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient != null) {
                remoteMediaClient.addListener(remoteMediaClientListener);
            }
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            remoteMediaClient = getRemoteMediaClient();
            if (remoteMediaClient != null) {
                remoteMediaClient.addListener(remoteMediaClientListener);
            }
        }

        @Override
        public void onSessionStarting(CastSession session) {
        }

        @Override
        public void onSessionStartFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionEnding(CastSession session) {
        }

        @Override
        public void onSessionResuming(CastSession session, String sessionId) {
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionSuspended(CastSession session, int reason) {
            if (remoteMediaClient != null) {
                remoteMediaClient.removeListener(remoteMediaClientListener);
            }
            remoteMediaClient = null;
        }
    }

    private class MyRemoteMediaClientListener implements RemoteMediaClient.Listener {

        @Override
        public void onStatusUpdated() {
            updateMediaQueue();
        }

        @Override
        public void onQueueStatusUpdated() {
            updateMediaQueue();
        }

        @Override
        public void onMetadataUpdated() {
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

        private void updateMediaQueue() {
            MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
            List<MediaQueueItem> queueItems =
                    (mediaStatus == null) ? null : mediaStatus.getQueueItems();
            if (queueItems == null || queueItems.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.queue_activity);
        Log.d(TAG, "onCreate() was called");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new QueueListViewFragment(), FRAGMENT_LIST_VIEW)
                    .commit();
        }
        setupActionBar();
        emptyView = findViewById(R.id.empty);
        castContext = CastContext.getSharedInstance(this);
        castContext.registerLifecycleCallbacksBeforeIceCreamSandwich(this, savedInstanceState);
    }


    private void setupActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.queue_list);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onPause() {
        if (remoteMediaClient != null) {
            remoteMediaClient.removeListener(remoteMediaClientListener);
        }
        castContext.getSessionManager().removeSessionManagerListener(
                sessionManagerListener, CastSession.class);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.queue_menu, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu,
                R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(QueueListViewActivity.this, CastPreference.class));
                break;
            case R.id.action_clear_queue:
                QueueDataProvider.getInstance(getApplicationContext()).removeAll();
                break;
            case android.R.id.home:
                finish();
                break;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        return castContext.onDispatchVolumeKeyEventBeforeJellyBean(event)
                || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        castContext.getSessionManager().addSessionManagerListener(
                sessionManagerListener, CastSession.class);
        if (remoteMediaClient == null) {
            remoteMediaClient = getRemoteMediaClient();
        }
        if (remoteMediaClient != null) {
            remoteMediaClient.addListener(remoteMediaClientListener);
            MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
            List<MediaQueueItem> queueItems =
                    (mediaStatus == null) ? null : mediaStatus.getQueueItems();
            if (queueItems != null && !queueItems.isEmpty()) {
                emptyView.setVisibility(View.GONE);
            }
        }
        super.onResume();
    }

    private RemoteMediaClient getRemoteMediaClient() {
        CastSession castSession = castContext.getSessionManager().getCurrentCastSession();
        return (castSession != null && castSession.isConnected())
                ? castSession.getRemoteMediaClient() : null;
    }
}

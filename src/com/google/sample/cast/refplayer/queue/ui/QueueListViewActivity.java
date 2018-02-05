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

    private final RemoteMediaClient.Listener mRemoteMediaClientListener =
            new MyRemoteMediaClientListener();
    private final SessionManagerListener<CastSession> mSessionManagerListener =
            new MySessionManagerListener();
    private CastContext mCastContext;
    private RemoteMediaClient mRemoteMediaClient;
    private View mEmptyView;

    private class MySessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionEnded(CastSession session, int error) {
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.removeListener(mRemoteMediaClientListener);
            }
            mRemoteMediaClient = null;
            mEmptyView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            mRemoteMediaClient = getRemoteMediaClient();
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.addListener(mRemoteMediaClientListener);
            }
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            mRemoteMediaClient = getRemoteMediaClient();
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.addListener(mRemoteMediaClientListener);
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
            if (mRemoteMediaClient != null) {
                mRemoteMediaClient.removeListener(mRemoteMediaClientListener);
            }
            mRemoteMediaClient = null;
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
            MediaStatus mediaStatus = mRemoteMediaClient.getMediaStatus();
            List<MediaQueueItem> queueItems =
                    (mediaStatus == null) ? null : mediaStatus.getQueueItems();
            if (queueItems == null || queueItems.isEmpty()) {
                mEmptyView.setVisibility(View.VISIBLE);
            } else {
                mEmptyView.setVisibility(View.GONE);
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
        mEmptyView = findViewById(R.id.empty);
        mCastContext = CastContext.getSharedInstance(this);
    }


    private void setupActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.queue_list);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onPause() {
        if (mRemoteMediaClient != null) {
            mRemoteMediaClient.removeListener(mRemoteMediaClientListener);
        }
        mCastContext.getSessionManager().removeSessionManagerListener(
                mSessionManagerListener, CastSession.class);
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
        return mCastContext.onDispatchVolumeKeyEventBeforeJellyBean(event)
                || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        mCastContext.getSessionManager().addSessionManagerListener(
                mSessionManagerListener, CastSession.class);
        if (mRemoteMediaClient == null) {
            mRemoteMediaClient = getRemoteMediaClient();
        }
        if (mRemoteMediaClient != null) {
            mRemoteMediaClient.addListener(mRemoteMediaClientListener);
            MediaStatus mediaStatus = mRemoteMediaClient.getMediaStatus();
            List<MediaQueueItem> queueItems =
                    (mediaStatus == null) ? null : mediaStatus.getQueueItems();
            if (queueItems != null && !queueItems.isEmpty()) {
                mEmptyView.setVisibility(View.GONE);
            }
        }
        super.onResume();
    }

    private RemoteMediaClient getRemoteMediaClient() {
        CastSession castSession = mCastContext.getSessionManager().getCurrentCastSession();
        return (castSession != null && castSession.isConnected())
                ? castSession.getRemoteMediaClient() : null;
    }
}

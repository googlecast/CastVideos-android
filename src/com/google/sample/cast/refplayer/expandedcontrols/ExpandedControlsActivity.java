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

package com.google.sample.cast.refplayer.expandedcontrols;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity;
import com.google.sample.cast.refplayer.settings.CastPreference;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class ExpandedControlsActivity extends AppCompatActivity {

    private static final String TAG = "ExpandedControlsActivity";

    private final SessionManagerListener mSessionManagerListener = new SessionManagerListenerImpl();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CastContext.getSharedInstance(this).registerLifecycleCallbacksBeforeIceCreamSandwich(this,
                savedInstanceState);
        setContentView(R.layout.cast_expanded_controls);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(
                    R.drawable.arrow_down_white);
        }
    }

    @Override
    protected void onResume() {
        CastContext.getSharedInstance(this).getSessionManager().addSessionManagerListener(
                mSessionManagerListener);
        CastSession castSession =
                CastContext.getSharedInstance(this).getSessionManager().getCurrentCastSession();
        if ((castSession == null) || (!castSession.isConnected() && !castSession.isConnecting())) {
            finish();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        CastContext.getSharedInstance(this).getSessionManager().removeSessionManagerListener(
                mSessionManagerListener);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.browse, menu);
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        Intent intent;
        if (item.getItemId() == R.id.action_settings) {
            intent = new Intent(ExpandedControlsActivity.this, CastPreference.class);
            startActivity(intent);
        } else if (item.getItemId() == R.id.action_show_queue) {
            intent = new Intent(ExpandedControlsActivity.this, QueueListViewActivity.class);
            startActivity(intent);
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersive();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setImmersive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return;
        }
        int newUiOptions = getWindow().getDecorView().getSystemUiVisibility();

        // Navigation bar hiding:  Backwards compatible to ICS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        // Status bar hiding: Backwards compatible to Jellybean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setImmersive(true);
        }
    }

    private class SessionManagerListenerImpl implements SessionManagerListener {

        @Override
        public void onSessionEnded(Session session, int error) {
            finish();
        }

        @Override
        public void onSessionStarting(Session session) {
        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
        }

        @Override
        public void onSessionStartFailed(Session session, int error) {
        }

        @Override
        public void onSessionEnding(Session session) {
        }

        @Override
        public void onSessionResuming(Session session, String sessionId) {
        }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
        }

        @Override
        public void onSessionResumeFailed(Session session, int error) {
        }

        @Override
        public void onSessionSuspended(Session session, int reason) {
        }
    }

}
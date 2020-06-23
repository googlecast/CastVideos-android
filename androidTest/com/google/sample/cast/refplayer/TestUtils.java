/*
 * Copyright 2019 Google LLC. All Rights Reserved.
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

package com.google.sample.cast.refplayer;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;

import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.json.JSONException;

import java.util.concurrent.TimeUnit;

import androidx.appcompat.widget.ButtonBarLayout;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

class TestUtils {

    private static Resources resources =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
    private static final String TARGET_DEVICE =
            resources.getString(R.string.cast_test_target_device);
    private static final long SHORT_TIMEOUT_MS =
            TimeUnit.SECONDS.toMillis(resources.getInteger(R.integer.cast_test_short_timeout));
    private static final long MAX_TIMEOUT_MS =
            TimeUnit.SECONDS.toMillis(resources.getInteger(R.integer.cast_test_max_timeout));

    private static Context mTragetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private CastContext mCastContext;
    private CastSession mCastSession;
    private SessionManager mSessionManager;
    private RemoteMediaClient mRemoteMediaClient;
    private MediaStatus mMediaStatus;
    private boolean isCastConnected;
    private int actualState;

    private boolean isCastDisconnected;
    /**
     * Connecting to Cast device
     *  - Open Cast menu dialog when tapping the Cast icon
     *  - Select target Cast device and connect
     *  - Assert the Cast state is connected
     */
    void connectToCastDevice() throws InterruptedException, UiObjectNotFoundException {
        // wait for cast button ready
        Thread.sleep(SHORT_TIMEOUT_MS);

        getCastInfo();
        if (isCastConnected) {
            disconnectFromCastDevice();
        }

        onView(isAssignableFrom(MediaRouteButton.class))
                .perform(click());

        onView(withId(R.id.action_bar_root))
                .check(matches(isDisplayed()));

        // wait for device chooser list
        Thread.sleep(SHORT_TIMEOUT_MS);

        mDevice.findObject(new UiSelector().text(TARGET_DEVICE)).click();

        assertCastStateIsConnected(MAX_TIMEOUT_MS);
    }

    /**
     * Disconnect from Cast device
     *  - Open Cast menu dialog when tapping the Cast icon
     *  - Click to stop casting
     */
    void disconnectFromCastDevice() throws InterruptedException {
        onView(isAssignableFrom(MediaRouteButton.class))
                .check(matches(isDisplayed()))
                .perform(click());

        Thread.sleep(SHORT_TIMEOUT_MS);

        onView(allOf(
                withText("Stop casting")
                , withParent(isAssignableFrom(ButtonBarLayout.class))
        )).check(matches(isDisplayed())).perform(click());
    }

    /**
     * Get Cast context and media status info
     */
    void getCastInfo() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        mCastContext = CastContext.getSharedInstance(mTragetContext);
                        mSessionManager = mCastContext.getSessionManager();
                        mCastSession = mSessionManager.getCurrentCastSession();
                        if (mCastSession != null) {
                            mRemoteMediaClient = mCastSession.getRemoteMediaClient();
                            isCastConnected = mCastSession.isConnected();
                            if (mRemoteMediaClient != null) {
                                mMediaStatus = mRemoteMediaClient.getMediaStatus();
                            }
                        }
                    }
                }
        );
    }

    /**
     * Check connection status from Cast session
     */
    private void assertCastStateIsConnected(long timeout) throws InterruptedException {
        long startTime = SystemClock.uptimeMillis();
        isCastConnected = false;

        getCastInfo();

        while (!isCastConnected && SystemClock.uptimeMillis() - startTime < timeout) {
            Thread.sleep(500);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    new Runnable() {
                        @Override
                        public void run() {
                            isCastConnected = mCastSession.isConnected();
                        }
                    }
            );
        }

        assertTrue(isCastConnected);

    }

    /**
     * Check Cast is Disconnected
     */
    protected void assertCastStateIsDisconnected(long timeout) throws Exception{
        long startTime = SystemClock.uptimeMillis();
        isCastDisconnected = false;
        getCastInfo();

        while(!isCastDisconnected && SystemClock.uptimeMillis() - startTime < timeout){
            Thread.sleep(500);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    new Runnable() {
                        @Override
                        public void run() {
                            isCastDisconnected = mCastSession.isDisconnected();
                        }
                    }
            );
        }
        assertTrue(isCastDisconnected);
    }

    /**
     * Click and play a video
     * Assert player state is playing
     */
    void playCastContent(String videoTitle) throws UiObjectNotFoundException, InterruptedException {
        mDevice.findObject(new UiSelector().text(videoTitle)).click();
        mDevice.findObject(
                new UiSelector().resourceId(resources.getResourceName(R.id.play_circle))).click();
        mDevice.findObject(new UiSelector().text("Play Now")).click();
        assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);
    }

    /**
     * Get and assert player state from remote media client
     */
    void assertPlayerState(int expectedState, long timeout) throws InterruptedException {
        long startTime = SystemClock.uptimeMillis();
        actualState = MediaStatus.PLAYER_STATE_UNKNOWN;

        getCastInfo();

        while (actualState != expectedState && SystemClock.uptimeMillis() - startTime < timeout) {
            Thread.sleep(500);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    new Runnable() {
                        @Override
                        public void run() {
                            actualState = mRemoteMediaClient.getPlayerState();
                        }
                    }
            );
        }

        assertEquals(expectedState, actualState);
    }

    /**
     * Check the Expanded Controller UI
     */
    void verifyExpandedController() {
        onView(withId(R.id.controllers))
                .check(matches(isDisplayed()));
        onView(withId(R.id.cast_seek_bar))
                .check(matches(isDisplayed()));
        onView(withId(R.id.start_text))
                .check(matches(isDisplayed()));
        onView(withId(R.id.end_text))
                .check(matches(isDisplayed()));
        onView(withId(R.id.button_play_pause_toggle))
                .check(matches(isDisplayed()));
        onView(withContentDescription("Unmute"))
                .check(matches(isDisplayed()));
        onView(withContentDescription("Closed captions unavailable"))
                .check(matches(isDisplayed()))
                .check(matches(not(isEnabled())));
    }

    /**
     * Check the Mini Controller UI
     */
    void verifyMiniController() {
        onView(withId(R.id.cast_mini_controller))
                .check(matches(isDisplayed()));
        onView(withId(R.id.icon_view))
                .check(matches(isDisplayed()));
        onView(withId(R.id.title_view))
                .check(matches(isDisplayed()));
        onView(withId(R.id.subtitle_view))
                .check(matches(isDisplayed()));
        onView(withId(R.id.button_2))   // play_pause_button
                .check(matches(isDisplayed()));
    }

    /**
     * Check if active track exists
     */
    void assertCaption(boolean enabled) {
        getCastInfo();
        if (enabled) {
            assertNotNull(mMediaStatus.getActiveTrackIds());
        } else {
            assertTrue(mMediaStatus.getActiveTrackIds() == null
                    || mMediaStatus.getActiveTrackIds().length == 0);
        }
    }

    /**
     * Assert remote media client gets correct timestamp
     */
    void assertStreamPosition(final float expectedPercent) {
        getCastInfo();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        long duration = mMediaStatus.getMediaInfo().getStreamDuration();
                        long currentTime = mRemoteMediaClient.getApproximateStreamPosition();

                        float actualPercent = (float) currentTime / duration;

                        assertEquals(expectedPercent, actualPercent, 0.05f);
                    }
                }
        );
    }

    /**
     * Verify the order of the queue items
     */
    void assertQueueOrder(String expectedItem1, String expectedItem2, String expectedItem3)
            throws JSONException {
        getCastInfo();

        assertEquals(expectedItem1,
                mMediaStatus.getQueueItem(0).getMedia().getMetadata().toJson().get("title"));
        assertEquals(expectedItem2,
                mMediaStatus.getQueueItem(1).getMedia().getMetadata().toJson().get("title"));
        assertEquals(expectedItem3,
                mMediaStatus.getQueueItem(2).getMedia().getMetadata().toJson().get("title"));
    }
}

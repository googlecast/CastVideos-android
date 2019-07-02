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

import android.content.res.Resources;

import com.google.android.gms.cast.MediaStatus;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import androidx.test.espresso.action.ViewActions;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * To test media playback functionality
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class PlaybackControlTest {

    private static Resources resources =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
    private static final String VIDEO_WITHOUT_SUBTITLES =
            resources.getString(R.string.cast_test_video_1);
    private static final String VIDEO_WITH_SUBTITLES =
            resources.getString(R.string.cast_test_video_2);
    private static final long MAX_TIMEOUT_MS =
            TimeUnit.SECONDS.toMillis(resources.getInteger(R.integer.cast_test_max_timeout));

    private UiDevice mDevice;
    private TestUtils mTestUtils = new TestUtils();

    @Rule
    public ActivityTestRule<VideoBrowserActivity> mActivityRule =
            new ActivityTestRule<>(VideoBrowserActivity.class);

    @Before
    public void initState() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    /**
     * Play content using the play/pause control and assert player status
     */
    @Test
    public void testPlayPause() throws InterruptedException, UiObjectNotFoundException {
        mTestUtils.connectToCastDevice();
        mTestUtils.playCastContent(VIDEO_WITHOUT_SUBTITLES);
        mTestUtils.verifyExpandedController();

        onView(withId(R.id.button_play_pause_toggle))
                .perform(click());
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PAUSED, MAX_TIMEOUT_MS);

        onView(withId(R.id.button_play_pause_toggle))
                .perform(click());
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);

        mTestUtils.disconnectFromCastDevice();
    }

    /**
     * Click on Closed Caption button and select language from the subtitles dialog
     * Verify the Closed Caption button is clickable
     * Verify the active track exists
     */
    @Test
    public void testCaptionControl() throws InterruptedException, UiObjectNotFoundException {
        mTestUtils.connectToCastDevice();
        mTestUtils.playCastContent(VIDEO_WITH_SUBTITLES);

        mTestUtils.assertCaption(false);

        // Click CC button and open SUBTITLES dialog
        mDevice.findObject(new UiSelector().description("Closed captions")).click();
        assertTrue(mDevice.findObject(new UiSelector().textMatches("SUBTITLES")).exists());

        // Select subtitle and close SUBTITLES dialog
        mDevice.findObject(new UiSelector().text("English Subtitle")
                .fromParent(new UiSelector().resourceId(resources.getResourceName(R.id.radio)))).click();
        mDevice.findObject(new UiSelector().text("OK")).click();
        assertFalse(mDevice.findObject(new UiSelector().textMatches("SUBTITLES")).exists());

        // Assert subtitle is activated
        mTestUtils.assertCaption(true);

        onView(isRoot()).perform(ViewActions.pressBack());

        mTestUtils.disconnectFromCastDevice();
    }


    /**
     * Scrub progress bar and verify the current duration
     */
    @Test
    public void testProgressBarControl() throws UiObjectNotFoundException, InterruptedException {
        mTestUtils.connectToCastDevice();
        mTestUtils.playCastContent(VIDEO_WITHOUT_SUBTITLES);
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);

        UiObject2 progressBar = mDevice.findObject(By.res(resources.getResourceName(R.id.cast_seek_bar)));
        progressBar.scroll(Direction.LEFT, 0.75f, 500);
        mTestUtils.assertStreamPosition(0.75f);

        mTestUtils.disconnectFromCastDevice();
    }
}

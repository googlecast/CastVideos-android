/*
 * Copyright (C) 2019 Google LLC. All Rights Reserved.
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

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * To test Queueing functionality
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class QueueingTest {
    private static Resources resources =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
    private static final String VIDEO_ITEM_1 =
            resources.getString(R.string.cast_test_video_1);
    private static final String VIDEO_ITEM_2 =
            resources.getString(R.string.cast_test_video_2);
    private static final String VIDEO_ITEM_3 =
            resources.getString(R.string.cast_test_video_3);
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
     * Test adding content to the Queue
     * Verify Next and Previous buttons are enabled
     * Verify content is played in the order it was added to queue
     */
    @Test
    public void testQueuePlayback() throws UiObjectNotFoundException, InterruptedException, JSONException {
        mTestUtils.connectToCastDevice();

        createQueue();

        // Verify expanded controller with correct video title and skip to next button is enabled
        onView(withText(VIDEO_ITEM_3))
                .check(matches(isDisplayed()));
        onView(withContentDescription("Skip to next item"))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()));

        // Verify queue items (media status) in correct order
        mDevice.pressBack();
        onView(withId(R.id.action_show_queue))
                .check(matches(isDisplayed())).perform(click());
        mTestUtils.assertQueueOrder(VIDEO_ITEM_3, VIDEO_ITEM_1, VIDEO_ITEM_2);

        // Open expanded controller and click to play next item
        mDevice.pressBack();
        onView(withId(R.id.cast_mini_controller)).perform(click());
        mDevice.findObject(new UiSelector().description("Skip to next item")).click();
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);

        // Verify expanded controller with correct video title and skip to previous button is enabled
        onView(withText(VIDEO_ITEM_1))
                .check(matches(isDisplayed()));
        onView(withContentDescription("Skip to previous item"))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()));

        mTestUtils.disconnectFromCastDevice();
    }

    /**
     * To add content and create queue
     */
    private void createQueue() throws UiObjectNotFoundException, InterruptedException {
        mDevice.findObject(new UiSelector().text(VIDEO_ITEM_1)).click();
        mDevice.findObject(
                new UiSelector().resourceId(resources.getResourceName(R.id.play_circle))).click();
        mDevice.findObject(new UiSelector().text("Add to Queue")).click();
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);

        mDevice.findObject(new UiSelector().description("Navigate up")).click();

        mDevice.findObject(new UiSelector().text(VIDEO_ITEM_2)).click();
        mDevice.findObject(
                new UiSelector().resourceId(resources.getResourceName(R.id.play_circle))).click();
        mDevice.findObject(new UiSelector().text("Add to Queue")).click();

        mDevice.findObject(new UiSelector().description("Navigate up")).click();

        mDevice.findObject(new UiSelector().text(VIDEO_ITEM_3)).click();
        mDevice.findObject(
                new UiSelector().resourceId(resources.getResourceName(R.id.play_circle))).click();
        mDevice.findObject(new UiSelector().text("Play Now")).click();
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);
    }
}

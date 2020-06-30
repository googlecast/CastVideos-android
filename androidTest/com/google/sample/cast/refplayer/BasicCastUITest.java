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

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.Toolbar;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static org.hamcrest.Matchers.allOf;

/**
 * To test basic Cast Widgets
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class BasicCastUITest {

    private static Resources resources =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
    private static final String VIDEO_TITLE =
            resources.getString(R.string.cast_test_video_1);
    private static final long SHORT_TIMEOUT_MS =
            TimeUnit.SECONDS.toMillis(resources.getInteger(R.integer.cast_test_short_timeout));
    private static final long MAX_TIMEOUT_MS =
            TimeUnit.SECONDS.toMillis(resources.getInteger(R.integer.cast_test_max_timeout));
    private static final String NOTIFICATION_TITLE = "Cast Videos Sample";

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
     * Test the cast icon is visible
     */
    @Test
    public void testCastButtonDisplay() throws InterruptedException {
        // wait for cast button
        Thread.sleep(SHORT_TIMEOUT_MS);

        onView(isAssignableFrom(MediaRouteButton.class))
                .check(matches(isDisplayed()));
    }

    /**
     * Test connecting and disconnecting to Cast device
     */
    @Test
    public void testCastConnect() throws InterruptedException, UiObjectNotFoundException {
        mTestUtils.connectToCastDevice();
        mTestUtils.disconnectFromCastDevice();
    }

    /**
     * Test casting video content to receiver app
     */
    @Test
    public void testCastingVideo() throws InterruptedException, UiObjectNotFoundException {
        mTestUtils.connectToCastDevice();
        mTestUtils.playCastContent(VIDEO_TITLE);
        mTestUtils.disconnectFromCastDevice();
    }

    /**
     * Verify the Expanded Controller is displayed
     */
    @Test
    public void testExpandedController() throws InterruptedException, UiObjectNotFoundException {
        mTestUtils.connectToCastDevice();
        mTestUtils.playCastContent(VIDEO_TITLE);
        mTestUtils.verifyExpandedController();
        mTestUtils.disconnectFromCastDevice();
    }

    /**
     * Verify the Mini Controller is displayed
     * Click the Mini Controller and verify the Expanded Controller is displayed
     */
    @Test
    public void testMiniController() throws InterruptedException, UiObjectNotFoundException {
        mTestUtils.connectToCastDevice();
        mTestUtils.playCastContent(VIDEO_TITLE);

        // click to close expanded controller
        onView(allOf(
                isAssignableFrom(AppCompatImageButton.class)
                , withParent(isAssignableFrom(Toolbar.class))
        ))
                .check(matches(isDisplayed()))
                .perform(click());

        mTestUtils.verifyMiniController();

        mDevice.pressEnter();
        onView(withId(R.id.cast_mini_controller))
                .perform(click());

        mTestUtils.verifyExpandedController();

        mTestUtils.disconnectFromCastDevice();
    }

    /**
     * Navigate away from the sender app and open the notification
     * Media control should display on notification
     * Click the notification widget and verify the Expanded Controller is displayed
     */
    @Test
    public void testNotification() throws UiObjectNotFoundException, InterruptedException {
        mTestUtils.connectToCastDevice();
        mTestUtils.playCastContent(VIDEO_TITLE);
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);

        mDevice.pressHome();
        mDevice.openNotification();
        mDevice.wait(Until.hasObject(By.text(NOTIFICATION_TITLE)),MAX_TIMEOUT_MS);

        mDevice.findObject(new UiSelector()
                .className("android.widget.ImageButton")
                .description("Pause"))
                .click();
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PAUSED, MAX_TIMEOUT_MS);

        mDevice.findObject(new UiSelector()
                .className("android.widget.ImageButton")
                .description("Play"))
                .click();
        mTestUtils.assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);

        mDevice.findObject(By.text(VIDEO_TITLE)).click();
        mTestUtils.verifyExpandedController();

        mDevice.pressBack();
        mDevice.pressEnter();
        mTestUtils.disconnectFromCastDevice();
    }
}

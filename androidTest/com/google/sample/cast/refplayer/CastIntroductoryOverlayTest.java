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
import android.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * To test Introductory Overlay UI
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class CastIntroductoryOverlayTest {
    private static Resources resources =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
    private static final long SHORT_TIMEOUT_MS =
            TimeUnit.SECONDS.toMillis(resources.getInteger(R.integer.cast_test_short_timeout));
    private Context mTragetContext;

    @Rule
    public ActivityTestRule<VideoBrowserActivity> mActivityRule =
            new ActivityTestRule<>(VideoBrowserActivity.class);

    @Before
    public void initState() {
        mTragetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * Verify overlay should be displayed the first time Cast is available
     */
    @Test
    public void testCastIntroductoryOverlay() throws InterruptedException {
        PreferenceManager.getDefaultSharedPreferences(mTragetContext).edit()
                .putBoolean("googlecast-introOverlayShown", false).apply();

        // wait for cast button
        Thread.sleep(SHORT_TIMEOUT_MS);

        onView(withId(R.id.cast_featurehighlight_view))
                .check(matches(isDisplayed()));

        onView(withId(R.id.cast_featurehighlight_help_text_header_view))
                .check(matches(withText(R.string.introducing_cast)));
    }
}

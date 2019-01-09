package com.google.sample.cast.refplayer;

import android.content.Context;
import android.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class CastIntroductoryOverlayTest {
    private Context mTragetContext;

    @Rule
    public ActivityTestRule<VideoBrowserActivity> mActivityRule =
            new ActivityTestRule<>(VideoBrowserActivity.class);

    @Before
    public void initState() {
        mTragetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testCastIntroductoryOverlay() throws InterruptedException {
        PreferenceManager.getDefaultSharedPreferences(mTragetContext).edit()
                .putBoolean("googlecast-introOverlayShown", false).apply();

        // wait for cast button
        Thread.sleep(2000);

        onView(withId(R.id.cast_featurehighlight_view))
                .check(matches(isDisplayed()));

        onView(withId(R.id.cast_featurehighlight_help_text_header_view))
                .check(matches(withText(R.string.introducing_cast)));
    }
}
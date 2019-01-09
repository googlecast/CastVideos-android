package com.google.sample.cast.refplayer;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;

import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.ButtonBarLayout;
import androidx.appcompat.widget.Toolbar;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.test.espresso.action.ViewActions;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BasicCastUITest {

    private static final String TARGET_DEVICE = "Living Room TV";
    private static final String VIDEO_WITH_SUBTITLES = "Casting To The Future";
    private static final String VIDEO_WITHOUT_SUBTITLES = "Big Buck Bunny";
    private static final long SHORT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(2);
    private static final long MAX_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);

    private Context mTragetContext;
    private CastContext mCastContext;
    private CastSession mCastSession;
    private SessionManager mSessionManager;
    private RemoteMediaClient mRemoteMediaClient;
    private MediaStatus mMediaStatus;
    private UiDevice mDevice;
    private boolean isCastConnected;
    private int actualState;

    @Rule
    public ActivityTestRule<VideoBrowserActivity> mActivityRule =
            new ActivityTestRule<>(VideoBrowserActivity.class);

    @Before
    public void initState() throws InterruptedException {
        mTragetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testCastButtonDisplay() throws InterruptedException {
        // wait for cast button
        Thread.sleep(SHORT_TIMEOUT_MS);

        onView(isAssignableFrom(MediaRouteButton.class))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testCastConnect() throws InterruptedException, UiObjectNotFoundException {
        connectToCastDevice();
        disconnectFromCastDevice();
    }

    @Test
    public void testCastingVideo() throws InterruptedException, UiObjectNotFoundException {
        connectToCastDevice();
        playCastContent(VIDEO_WITHOUT_SUBTITLES);
        disconnectFromCastDevice();
    }

    @Test
    public void testExpandedControllerWithVideo() throws InterruptedException, UiObjectNotFoundException {
        connectToCastDevice();
        playCastContent(VIDEO_WITHOUT_SUBTITLES);
        verifyExpandedController();

        onView(withId(R.id.button_play_pause_toggle))
                .perform(click());
        assertPlayerState(MediaStatus.PLAYER_STATE_PAUSED, MAX_TIMEOUT_MS);

        onView(withId(R.id.button_play_pause_toggle))
                .perform(click());
        assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);

        disconnectFromCastDevice();
    }

    private void verifyExpandedController() {
        onView(withId(R.id.expanded_controller_layout))
                .check(matches(isDisplayed()));
        onView(withId(R.id.seek_bar))
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

    @Test
    public void testMiniControllerWithVideo() throws InterruptedException, UiObjectNotFoundException {
        connectToCastDevice();
        playCastContent(VIDEO_WITHOUT_SUBTITLES);

        // click to close expanded controller
        onView(allOf(
                isAssignableFrom(AppCompatImageButton.class)
                , withParent(isAssignableFrom(Toolbar.class))
        ))
                .check(matches(isDisplayed()))
                .perform(click());

        verifyMiniControllerWithVideo();

        onView(withId(R.id.cast_mini_controller))
                .perform(click());

        verifyExpandedController();

        disconnectFromCastDevice();
    }

    private void verifyMiniControllerWithVideo() {
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

    @Test
    public void testCaptionControl() throws InterruptedException, UiObjectNotFoundException {
        connectToCastDevice();

        playCastContent(VIDEO_WITH_SUBTITLES);

        getCastInfo();
        assertNull(mMediaStatus.getActiveTrackIds());

        // Click CC button and open SUBTITLES dialog
        mDevice.findObject(new UiSelector().description("Closed captions")).click();
        assertTrue(mDevice.findObject(new UiSelector().textMatches("SUBTITLES")).exists());

        // Select subtitle and close SUBTITLES dialog
        mDevice.findObject(new UiSelector().text("English Subtitle")
                .fromParent(new UiSelector().resourceId(getResourceName(R.id.radio)))).click();
        mDevice.findObject(new UiSelector().text("OK")).click();
        assertFalse(mDevice.findObject(new UiSelector().textMatches("SUBTITLES")).exists());

        // Assert subtitle is activated
        assertNotNull(mMediaStatus.getActiveTrackIds());

        onView(isRoot()).perform(ViewActions.pressBack());

        disconnectFromCastDevice();
    }

    private String getResourceName(int resourceId) {
        Resources resources = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getResources();

        return resources.getResourceName(resourceId);
    }

    private void connectToCastDevice() throws InterruptedException, UiObjectNotFoundException {
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

    private void disconnectFromCastDevice() throws InterruptedException {
        onView(isAssignableFrom(MediaRouteButton.class))
                .check(matches(isDisplayed()))
                .perform(click());

        Thread.sleep(2000);

        onView(allOf(
                withText("Stop casting")
                , withParent(isAssignableFrom(ButtonBarLayout.class))
        )).check(matches(isDisplayed())).perform(click());
    }

    private void playCastContent(String videoTitle) throws UiObjectNotFoundException, InterruptedException {
        mDevice.findObject(new UiSelector().text(videoTitle)).click();
        mDevice.findObject(
                new UiSelector().resourceId(getResourceName(R.id.play_circle))).click();
        mDevice.findObject(new UiSelector().text("Play Now")).click();
        assertPlayerState(MediaStatus.PLAYER_STATE_PLAYING, MAX_TIMEOUT_MS);
    }

    private void getCastInfo() {
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

    private void assertPlayerState(int expectedState, long timeout) throws InterruptedException {
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
}
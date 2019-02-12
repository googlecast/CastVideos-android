# CastVideos-android  (reference Android sender app)

This Google Cast demo app shows how to cast videos from an Android device in a way that is fully compliant with the Design Checklist.

**This is a reference sender app to be used as the starting point for your Android sender app**

Here is the list of other reference apps:
* [iOS Sender: CastVideos-ios](https://github.com/googlecast/CastVideos-ios)
* [Chrome Sender: CastVideos-chrome](https://github.com/googlecast/CastVideos-chrome)
* [CAF Receiver: CastReceiver](https://github.com/googlecast/CastReceiver)

## Dependencies
* Android Volley library: https://github.com/google/volley

## Setup Instructions
* Get a Chromecast device and set it up
* [Optional] Register an application on the Developers Console (http://cast.google.com/publish). The easiest would be to use the Styled Media Receiver option there.
You will get an App ID when you finish registering your application. This project uses a published Application ID that
can be used to run the app without using your own ID but if you need to do any console debugging, you would need
to have your own ID.
* Import the project into Android Studio or use gradle to build the project.
* Compile and deploy to your Android device.
* This sample includes a published app id in the res/values/strings.xml file so the project can be built and run without a need
   to register an app id. If you want to use your own receiver (which is required if you need to debug the receiver),
    update "app_id" in that file with your own app id.

## Automated UI Testing on Cast Sender App
This Google Cast demo app also includes sample Cast test cases in [androidTest/](androidTest/com/google/sample/cast/refplayer/).
It is recommended to implement and run automated testing for your Android sender app to ensure the best Cast experience for users.

#### How to run test cases
* Update `cast_test_target_device` in [res/values/cast_test.xml] with your Chromecast device name
* Connect to a physical Android device and make sure your device is unlocked
* Follow [Espresso setup instructions](https://developer.android.com/training/testing/espresso/setup#set-up-environment) to turn off system animations under **Settings > Developer options**
* In Android Studio, click the **Sync Project with Gradle Files** button
* Go to [CastTestSuite.java](androidTest/com/google/sample/cast/refplayer/CastTestSuite.java), right click and **Run 'CastTestSuite'** or right click to run single test case (ex: [testCastingVideo()](androidTest/com/google/sample/cast/refplayer/BasicCastUITest.java))
* \[Optional\] Adjust timeout setting in [res/values/cast_test.xml] if necessary

#### UI testing frameworks
* Espresso: https://developer.android.com/training/testing/espresso/
* UI Automator: https://developer.android.com/training/testing/ui-automator
* **Note: The UI Automator framework requires Android 4.3 (API level 18) or higher.**

## Documentation
* [Google Cast Android Sender Overview](https://developers.google.com/cast/docs/android_sender/)
* [Developer Guides](https://developers.google.com/cast/docs/developers)

## References
* [Android Sender Reference](https://developers.google.com/cast/docs/reference/android/packages)
* [Design Checklist](http://developers.google.com/cast/docs/design_checklist)

## How to report bugs
* [Google Cast SDK Support](https://developers.google.com/cast/docs/support)
* For sample apps issues, please open a bug here on GitHub.

## How to make contributions?
Please read and follow the steps in the [CONTRIBUTING.md](CONTRIBUTING.md).

## License
See [LICENSE](LICENSE)

## Terms
Your use of this sample is subject to, and by using or downloading the sample files you agree to comply with, the [Google APIs Terms of Service](https://developers.google.com/terms/) and the [Google Cast SDK Additional Developer Terms of Service](https://developers.google.com/cast/docs/terms/).

## Google+
[Google Cast Developers Community on Google+](http://goo.gl/TPLDxj)

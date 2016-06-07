# CastVideos-android (reference Android sender app)

CastVideos-android application shows how to cast videos from an android device in a way that is fully compliant with the Design Checklist.

**This is a reference sender app to be used as the starting point for your Android sender app**

Here is the list of other reference apps:
* [iOS Sender: CastVideos-ios](https://github.com/googlecast/CastVideos-ios)
* [Chrome Sender: CastVideos-chrome](https://github.com/googlecast/CastVideos-chrome)
* [Receiver: Cast-Player-Sample](https://github.com/googlecast/Cast-Player-Sample)

## Dependencies
* Android Query library: https://github.com/androidquery/androidquery

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

## References and How to report bugs
* [Cast Developer Documentation](http://developers.google.com/cast/)
* [Design Checklist](http://developers.google.com/cast/docs/design_checklist)
* If you find any issues with this library, please open a bug here on GitHub
* Question are answered on [StackOverflow](http://stackoverflow.com/questions/tagged/google-cast)

## How to make contributions?
Please read and follow the steps in the CONTRIBUTING.md

## License
See LICENSE

## Terms
Your use of this sample is subject to, and by using or downloading the sample files you agree to comply with, the [Google APIs Terms of Service](https://developers.google.com/terms/) and the [Google Cast SDK Additional Developer Terms of Service](https://developers.google.com/cast/docs/terms/).

## Google+
Google Cast Developers Community on Google+ [http://goo.gl/TPLDxj](http://goo.gl/TPLDxj)
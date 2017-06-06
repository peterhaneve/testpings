# TEST Pings

TEST ping distribution application for mobile devices

## Compiling

You will need the latest version of Android Studio to build, along with a Java 8 JDK. There are two projects - one for the sample server, and one for the Android application.

## Android

This application uses Firebase Cloud Messaging (FCM) and is targeted at Android 4.1 and above (Jelly Bean).

This Android application will not compile as specified - a file named `android/app/src/main/res/values/strings-dummy.xml` needs to be created with a single string value `dummy_host`, which is used to determine the host used for the test server.

## Server

Likewise, the server application requires a config file named `fcmjava.properties` with the Google Cloud application ID to be placed in `~/.fcmjava`.

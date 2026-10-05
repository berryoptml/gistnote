# Development

## Requirements

- Android Studio with its bundled JBR (JDK 17)
- Android SDK Platform 37 and Build Tools 36.0.0
- Android 6.0 / API 23 or newer for the Android app

Open the repository in Android Studio and run the `app` configuration, or run:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is emitted at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Layout

```text
app/          Android application and widgets
macos-app/    Experimental SwiftUI desktop companion
tools/        Small local development utilities
docs/         Project documentation and screenshots
```

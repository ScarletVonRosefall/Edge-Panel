# Edge Panel (Android 9 / ColorOS 6)

Build (Android Studio): open this folder, Build > Build APK.
Build (CLI): needs JDK 17, Android SDK (platform 34), Gradle 8.7+
    gradle wrapper --gradle-version 8.7
    ./gradlew assembleRelease
    adb install -r app/build/outputs/apk/release/app-release.apk

Then: open the app > enable the Accessibility service > swipe from the right edge.

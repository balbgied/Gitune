# Gtuner

An offline Android tuner, pitch and tempo monitor, and practice recorder. Built to help musicians hear and improve their intonation, see which notes recur in a jam, and review passages without having to watch the screen while playing.

[Download the Android APK](https://github.com/balbgied/Gtuner/releases) · [Illustrated user guide](docs/USER_GUIDE.md) · [Report an issue](https://github.com/balbgied/Gtuner/issues)

![Gtuner Jam and Tuner screens with labeled controls](docs/user-guide-screens.svg)

## Use it

1. Download the APK from **Assets** on the GitHub release and open it on an Android 11 or newer device. If Android asks, allow installation from the browser or file manager you used. You can turn that permission back off afterward.
2. Open Gtuner and allow microphone access for live tuning and recording. Playback of existing files does not require it.
3. Swipe between **Jam** and **Tuner**. In Jam, tap **REC** before playing and the red timer to stop. The saved take appears below the graph: tap **Play** to listen back and add bookmarks to passages you want to practise.

Recordings stay on the device in shared `Music/`. **AAC (.m4a)** is the default; choose **WAV** in Tuner for lossless recording. The [user guide](docs/USER_GUIDE.md) covers settings, permissions, playback, and deletion. Gtuner shows detected pitches and an estimated tempo; it does not identify scales automatically and works best with one clearly audible note at a time.

## Build from source

Open the project in Android Studio with **Android SDK Platform 37** installed and use a Gradle JDK compatible with Android Gradle Plugin 9.2 (JDK 17 or newer; Android Studio’s bundled JDK works). Sync and run the `app` configuration, or build from a terminal:

```sh
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat`. The APK is built at `app/build/outputs/apk/debug/app-debug.apk`. The app uses Kotlin, Jetpack Compose and Android media APIs. Compose previews in `MainActivity.kt` use example data rather than live audio.

The [working specification](music_analyzer_android_spec_v0.1.md) and [early mockups](docs/prototype) are historical design material, not descriptions of the current interface.

## License and contact

Gtuner is distributed under the [GNU General Public License v3.0](LICENSE), without warranty. Maintained by Giedrius Balbieris (<metagi@gmail.com>).

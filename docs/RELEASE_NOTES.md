# Gitune v0.2.0

This update adds multi-note analysis, a more reliable tuner, and tempo detection that works on steady percussion. Gitune is still an offline Android 11+ preview, not a Play Store release.

- **Live Jam:** Adaptive pitch sensitivity replaces the mic sensitivity switch. The graph can plot up to three dots per frame, including dim hints from strong tonal peaks and overtones. All twelve semitone labels brighten with how often each note was detected. Tempo detection now autocorrelates onsets, so a steady drum beat or repeated claps produce a BPM.
- **Tuner:** Uses the same adaptive gate as Jam. When the periodicity check is inconclusive, a single dominant tonal peak that persists for a few frames can drive the needle.
- **File review:** Imported files use the same multi-note analysis as live Jam, so up to three dots and note-label brightness come from the recording. Re-importing the same file reuses its existing Music copy instead of creating duplicates.
- **Playlist:** Paste a plain-text setlist on the Tuner screen and show or hide it behind the Jam graph. The text and checkbox are saved on the device.
- **Display and internals:** Gitune keeps the screen awake while the app is visible. The Kotlin package and Android namespace are now `com.gitune.prototype`; `applicationId` and the preferences file stay `com.gtuner.prototype` so existing installs keep their settings and recordings.
- **Documentation:** The [illustrated user guide](https://github.com/balbgied/Gitune#screens-at-a-glance) explains every main control and the app’s permissions.

## Downloads

- **Debug APK** — the `Gitune-debug-apk` artifact attached to each [GitHub Actions build](https://github.com/balbgied/Gitune/actions/workflows/build.yml). Artifacts expire after 30 days.
- **Source code** — GitHub’s archives for the matching commit. No `v0.2.0` release tag has been published yet.

On Android, open the APK to install it. Install it over an earlier Gitune build to keep your settings, bookmarks and recordings. If your old build was signed with a different key, Android will not install the update over it. Before uninstalling an old build, back up any recordings you want to keep; uninstalling clears in-app settings and bookmarks.

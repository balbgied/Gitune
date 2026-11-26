# Gitune app context

This is a developer-facing snapshot of the current implementation. For user-facing behavior, use the [README](../README.md); for the current release, use [RELEASE_NOTES.md](RELEASE_NOTES.md). `music_analyzer_android_spec_v0.1.md` is historical design material and is intentionally not kept in sync.

## Identity and build

- Kotlin package / Android namespace: `com.gitune.prototype`.
- `applicationId` stays `com.gtuner.prototype`, and the SharedPreferences file stays `gtuner`. This is deliberate: changing either would stop installs from upgrading in place and would lose settings, bookmarks and recordings.
- `minSdk 30`, `compileSdk`/`targetSdk 37`, versionCode 3 / versionName 0.2.0.
- Gradle 9.4.1 wrapper, Android Gradle Plugin 9.2, JDK 17+. `gradle.properties` enables the build cache and configuration cache.
- UI is Jetpack Compose in a two-page `HorizontalPager`: **Jam** and **Tuner**. File review lives inside Jam. The activity sets `FLAG_KEEP_SCREEN_ON` while visible.

## Audio and analysis pipeline

- Capture: `AudioRecord`, 48 kHz mono, 20 ms frames (960 samples). DC is removed per frame.
- Pitch (`LivePitchTracker`, `PitchDetector`, `SpectralPitch`):
  - Downsample 6:1 to 8 kHz; analyse a 100 ms / 800-sample window.
  - Adaptive gate tracks recent AC RMS (attack 0.10, release 0.04) and applies a threshold of 2.5% of that level, clamped to 0.00012–0.002. It gates analysis only; it never changes mic gain or the recorded signal.
  - YIN periodicity search covers 36–1150 Hz.
  - A 2048-point FFT over a Hann window finds strong tonal peaks (36–1150 Hz), keeping up to three with a 6 dB prominence rule and excluding peaks within 50 cents of the YIN result.
  - If YIN is inconclusive, a single peak at least 4× the next peak can drive the tuner after repeating within 50 cents for three frames.
  - Each frame produces `PitchPoint`s: dim `estimated` hints plus a solid tuner point. Note counting (`PitchStats.countNotes`, 10 s window) counts each pitch class at most once per frame.
- Tempo (`TempoDetector`):
  - Consumes full-band 20 ms RMS blocks (same path live and in file review).
  - Keeps 6.4 s of onsets (positive RMS rises over a slow follower) and uses mean-subtracted Pearson autocorrelation across lags 14–60, roughly 50–214 BPM.
  - Minimum correlation 0.20; half/double-time peaks are canonicalised toward 80–155 BPM when a neighbouring period is nearly as strong; three consistent analyses confirm a reading; it clears about 1.5 s after attacks stop.
- Live audio (`LiveAudio`) feeds both pitch and tempo from the mic, keeps 12 s of pitch points and 30 s of tempo points, and publishes a `LiveReadout` at about 25 Hz.
- File review (`ReviewAudio`) decodes with `MediaExtractor`/`MediaCodec`, downsamples to 8 kHz for pitch, computes tempo from full-rate 20 ms blocks, and uses the same `LivePitchTracker`, so recordings can show up to three dots and note-label brightness too.

## Recordings and files

- Takes are written to shared `Music/` through MediaStore as AAC (default) or WAV; maximum length is 3 hours.
- Picking a file through **File** copies it into `Music/` so bookmarks have a stable key. The copy is keyed by **file name + size**, and an existing Music copy with the same name is reused before creating a new one, which prevents `(1)`, `(2)` duplicates.
- The original picker URI is stored separately under `importSource:<copyUri>`; the delete dialog uses it to offer deleting both the original and the Music copy. Old `reviewCopy:<sourceUri>` entries are still recognised for upgrade compatibility.

## Preferences (file `gtuner`)

| Key | Type | Default | Purpose |
| --- | --- | --- | --- |
| `a4` | Int | 440 | Tuning reference in Hz, 415–466. |
| `recordingFormat` | String | `AAC` | Format for new takes (`AAC` or `WAV`). |
| `thickPitchLine` | Boolean | true | 12 dp vs 4 dp graph dots. |
| `playlist` | String | empty | Plain-text setlist, one song per line. |
| `showPlaylist` | Boolean | true | Show/hide the setlist backdrop behind the Jam graph. |
| `bookmarks:<copyUri>` | String | empty | Comma-separated positions (seconds), at most four per file. |
| `reviewCopy:<name>:<size>` | String | – | URI of the reusable Music copy for an imported file. |
| `importSource:<copyUri>` | String | – | Original picker URI, used only by the delete confirmation. |
| `highPitchSensitivity` | Boolean | – | Legacy key from older builds; no longer read or written. |

## Tests and tooling

- 19 JVM unit tests: `LivePitchTrackerTest` (9), `TempoDetectorTest` (7), `PitchStatsTest` (3).
- `./gradlew test assembleDebug lintDebug` is the standard verification command.
- Local audio samples under `docs/*.m4a`, `*.mp3`, `*.wav`, `*.flac`, `*.aac` are gitignored so they cannot be committed by accident.
- `.github/workflows/build.yml` runs on pushes to `main`, PRs and manual dispatch, builds the debug APK and uploads the `Gitune-debug-apk` Actions artifact (30-day retention).
- `.github/workflows/release.yml` runs on `v*` tags, builds the APK and publishes a GitHub Release with `Gitune-<tag>.apk` and its SHA-256 checksum, using the repository's built-in `GITHUB_TOKEN`.

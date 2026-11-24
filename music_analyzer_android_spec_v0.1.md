# Music Analyzer Android — Working Product Specification

- **Version:** 0.23 (review draft)
- **Updated:** 2026-11-23
- **Status:** Requirements and hypotheses for discussion; not an implementation commitment
- **Target:** Native Android, Kotlin and Jetpack Compose

**Minimum OS:** Android 11 (API 30). Physical acceptance devices: Xiaomi Mi Note 10 Pro and Samsung Galaxy Tab A, both running Android 11, plus Samsung Galaxy S23 Ultra. Record the exact Tab A model number and S23 Ultra Android version in test results.

> This is a reviewable source draft, not a second delivery-status tracker. Once agreed, changes belong in the project PRD, UX specification, architecture decisions, story files, and `sprint-status.yaml` as appropriate.

## 1. Product intent

A fast, offline music companion for two situations:

1. **Jam session:** Open the app and understand the evolving musical environment: tempo and changes in tempo, and active and prominent pitch classes.
2. **Individual practice:** Watch pitch accuracy while playing, explicitly record a performance, and later replay it with a time-synchronized pitch display and bookmarks to study recurring mistakes.

The signature visual is a **PitchLab-inspired scrolling pitch spectrogram** with 12 colored chromatic guide lines and one vivid pitch trace on a dark background. A compact, **liveBPM-inspired tempo history** sits above it in Jam. The plot must be useful at arm's length and on a dark stage.

### Principles

- Immediate, low-friction use. No sign-in, network, or setup wizard for core features.
- Honest measurements: show confidence/uncertainty rather than fabricated stable readings.
- Capture audio into short-lived buffers for live analysis; **save raw audio only after an explicit Record action**.
- Prefer faithful, readable musical feedback over decorative animation.
- Visual inspiration is not a license to copy another app's artwork or interface assets.

## 2. Scope and terminology

### In scope for the intended first usable product

- Live **Jam**, **Tuner**, and **Recordings/Review** views.
- Microphone-based tempo estimation with a history graph and direction indicator.
- Scrolling 12-semitone pitch-class display with one trace for the loudest resolved pitch and proportional offsets for sharp and flat pitches.
- Prominent pitch classes highlighted by brightness over a configurable trailing window in seconds, independent of BPM.
- Single-note instrument tuner with note name, Hz, cents deviation, and user-defined A4 reference (default **440 Hz**). V1 must support standard four-string bass tuning (**E1, A1, D2, G2**), including low E1 at approximately **41.2 Hz**.
- Button-triggered audio recording; local recording list; playback and seek; synchronized pitch display; one-tap time bookmarks.
- Import and analyze local **MP3, FLAC, and WAV** files.
- Offline-first operation, no required account.

### Explicitly out of scope for now

- Video recording / posture and ergonomics review.
- Cloud processing, music recognition by title, social sharing, accounts, collaboration.
- Reliable identification of **which player** in a polyphonic jam is out of tune.
- Full polyphonic transcription, chord charts, sheet-music comparison, or automatic mistake grading.
- Key/scale detection and labeling, and modulation detection or modulation-triggered resets, for the initial release.
- Tonic, dominant and subdominant discovery or harmonic-role highlighting.
- Automatic A4 discovery, tuning-reference suggestions or automatic calibration from recorded or imported audio. A4 is set manually in v1.

### Important musical distinction

The display has exactly **12 lanes: the twelve semitones of one chromatic octave, C–B**. Input pitches from different octaves fold into the same pitch-class lanes: A2, A3 and A4 all share the A centerline. In Jam and Review, a single scrolling line follows the loudest resolved pitch, positioned proportionally above or below its nearest equal-tempered semitone using the selected A4 reference. This measures deviation from the nearest note, not whether the intended note was played. The plot does not identify the musician or reveal the octave. The **monophonic Tuner** also reports numerical cents deviation.

## 3. Use cases and user journeys

### UC-01 — Arriving at a jam

1. User launches the app into **Jam** (proposed default).
2. Microphone permission is requested if not yet granted.
3. The 12-lane display starts scrolling once signal is detected. The tempo area initially reads `Listening…` until a reliable beat can be estimated.
4. A BPM number and a small history trace appear if tempo is reliable. Up to four prominent notes appear in large, bright type over the tempo graph, regardless of whether BPM can be estimated.
5. Highlights indicate note prominence only; no harmonic-role colors, key/scale labels or modulation events are shown. Weak or ambiguous pitch evidence is indicated rather than presented as confident notes.
6. The musician can switch to **Tuner** or press **Record** without leaving the analysis workflow.

### UC-02 — Practicing and reviewing

1. User opens **Tuner** or **Jam** and observes pitch while playing.
2. User taps the **red-outlined REC button** at the top right. Once recording starts, the button fills red and shows the elapsed time in white.
3. Only microphone samples accepted after recording starts are written to the file; live visualizations continue.
   Switching between Jam and Tuner does not interrupt recording. Both views share the header and recording controls; other views do not show them.
4. User taps the timer button to stop. It returns to the outlined `REC` appearance while the audio saves, then the saved file appears in **Recordings**.
5. User reopens the recording, plays/pauses/seeks, and sees pitch traces synchronized to the **recording timeline**, not a live microphone input. When playing the recording there is a sound in speakers/phones.
6. User taps **Add bookmark** to save the current playback position immediately. Tap a bookmark marker to jump to it; press and hold the marker to delete it.
7. User may select an MP3, FLAC or WAV, copy it into the phone's shared music storage, and inspect it with the same playback visualization.

## 4. UX specification — draft

### 4.1 Navigation

- **Jam** — default landing view, full ensemble listening.
- **Tuner** — clear single-note tuning view.
- **Recordings** — recordings list, import, playback and analysis.

Swipe horizontally between **Jam → Tuner → Review/Recordings**. Do not reserve screen space for tabs, bottom navigation, or page-switch buttons. Provide equivalent screen-switch actions for accessibility, and let seek bars and other horizontal controls handle their own gestures. There is no standalone Settings screen in the current layout.

The **same header appears only in Jam and Tuner**, with the tempo area and recording control/status. Navigating to Recordings/Review does not by itself end a recording; return to Jam or Tuner to stop it. Play and Import are blocked during recording.

Leaving Review pauses file playback. Keep the selected file and its last playback position for the current app session; return to Review paused. Before Jam or Tuner starts live microphone analysis, pause any active file playback. Outside Jam and Tuner, keep the microphone off unless an explicit recording is still active.

While any app screen is visible in the foreground, keep the display awake: inactivity must not cause it to sleep or lock. Manual locking remains available and stops an active recording.

For v1, stop and save when the app is no longer visible (e.g. Home or switching to another full-screen app). In-app navigation and transient overlays that neither hide the app nor interrupt microphone access do not stop recording. Outside Jam and Tuner, show a small recording status indicator without adding the recording header or controls. On return, show why a recording stopped and whether it was saved.

Rotation, split-screen resizing and screen recreation do not themselves end or restart Recording or Saving. Preserve the same file, elapsed time and recording state when capture remains uninterrupted. Verify rotation during both Recording and Saving on the Galaxy Tab A.

### 4.2 Jam screen hierarchy

```text
┌──────────────────────────────────────────────────────┐
│ 124         ~E~~~~~A~~~~~~G~~~~~~D~          [REC]    │  notes over tempo history
├──────────────────────────────────────────────────────┤
│ B   ───────────────────────────────────────────────  │
│ A#  ───────────────────────────────────────────────  │
│ A   ──────────────━━━━━━━━━━━━────────────────────  │  one pitch trace
│ G#  ───────────────────────────────────────────────  │
│ G   ══════════════════════════════════════════════  │
│ F#  ───────────────────────────────────────────────  │
│ F   ───────────────────────────────────────────────  │
│ E   ──────────────────────────────────────────────  │
│ D#  ───────────────────────────────────────────────  │
│ D   ──────────────────────────────────────────────  │
│ C#  ───────────────────────────────────────────────  │
│ C   ──────────────────────────────────────────────  │
└──────────────────────────────────────────────────────┘
```

The diagram shows **information hierarchy**, not exact spacing or trace shape. Design portrait-first without locking orientation. Jam has no visible screen name, static captions, or bottom controls. Hide the system status bar in Jam and place the header flush with the top usable edge, keeping controls clear of display cutouts. Restore the status bar on other screens. The grid fills the remaining content area. Preserve usable recording controls and make the note names easy to read from standing height with the phone on the floor.

**Tempo header**

- Proposed graph height is about **40 dp**, replacing the source brief's literal 40 px with density-independent Android units; verify against physical phones.
- BPM number on a **black background**; text **red** when speeding up, **yellow** when stable within **±3 BPM** (configurable), **green** when slowing down.
- Tempo history uses a **black background with a light-colored curve and latest dot**, visible in all trend states. Trend is communicated by the BPM number and accompanying symbols, not by changing the dot color.
- The comparison baseline is a **smoothed recent tempo**, *not* an arbitrary fixed target; exact comparison window is TBD.
- Jam and Tuner share one live tempo session. Show the last **30 seconds** of tempo estimates, not individual beats. Leave gaps when tempo is uncertain and show `—`/`Listening…` rather than holding an old BPM. Clear the current reading, graph history and trend baseline when live capture restarts; switching between Jam and Tuner without restarting capture retains them.
- Show the BPM number without a visible `Tempo` label or a trailing `≈` or equals sign. Expose the trend as accessible text independently of the color.
- In Jam, overlay up to four bright note names on the tempo graph, in prominence order over the configured trailing window. Scale them to use as much of the header height as fits without clipping or overlapping. Keep the history curve visible behind the names. No visible `Top notes` label or percentages.

**Pitch spectrogram**

- The grid fills the space below the header in Jam. Use large, bold lane labels that scale with lane height without clipping. Default labels: **C, C♯, D, D♯, E, F, F♯, G, G♯, A, A♯, B**. The flats preference changes labels only, not trace position.
- One horizontal lane per pitch class; newest observations enter at the **right** and history scrolls left (proposed convention).
- Draw **one thick, continuous scrolling line** through consecutive reliable estimates. Connect abrupt note jumps, including a change in the dominant pitch, with a short, smooth curve rather than a dotted or broken stroke. The connector is visual only: do not treat the intermediate positions as measured pitches or count them toward prominence. Keep genuine gaps for missing or unreliable audio and keep the B/C wrap rule.
- In Jam, use one brighter style for all qualifying lane labels. Show rank by the order of `Top notes`, not by brightness tiers. When a note is no longer prominent, restore its normal brightness and remove it from `Top notes`. Do not recolor historical traces or assign harmonic-role colors. Review shows the pitch trace without prominence highlights in the initial release.
- In Jam and Review, use a dark background and thin, muted colored guide lines for the 12 labeled semitone centerlines. Give each pitch class a consistent accent, shared by its guide and label. Draw the single scrolling trace above the guides, thicker and brighter, with a thin contrasting outline so it stays visible when exactly in tune. Each segment takes the accent of its nearest pitch class. A color change by itself does not break a continuous bend.
- Use an original palette, not copied PitchLab artwork. Color identifies pitch class, not tuning status or harmonic role. Keep the labels and vertical offsets readable without relying on hue; Jam prominence still uses the existing brighter label style and ordered `Top notes`, without changing historical trace colors.
- Choose the nearest equal-tempered semitone. Plot zero cents on its centerline, positive offsets above, and negative offsets below, with 100 cents equal to one semitone spacing. For example, A at +25 cents is one-quarter of the spacing toward A♯; A at −25 cents is one-quarter toward G♯. Fold input octaves onto the same grid. The Tuner's in-tune tolerance does not snap the line to its center. Handle B/C wrapping without drawing a line through intervening lanes.
- The plot keeps scrolling through silence or unreliable input, but leaves a gap rather than drawing an invented pitch or holding the previous one. Clearly indicate clipping and low-confidence states.
- At exactly halfway between two equal-tempered semitones, choose the higher semitone for labels and prominence (−50 cents relative to that note). Place each vertical-range boundary half a semitone beyond its outer centerline (C at the bottom, B at the top). At the B/C boundary, split a continuous bend at one edge and resume it at the opposite edge without drawing through intervening lanes.

**Record affordance**

- Use a single recording button **top right in Jam and Tuner**. When idle, show a red outline and red `REC` text with no red fill. Tap to start. While recording, use a solid red fill and red border and show only the elapsed time in white, formatted `HH:MM:SS` with two digits per field (e.g. `00:01:23`). Tap the same button to stop and return it to the outlined `REC` appearance as soon as capture stops; keep it disabled until saving finishes. Do not show a separate Stop button or extra text on the recording button. Keep the button size stable and provide accessible action labels for starting and stopping. Switching Jam ↔ Tuner preserves the take and elapsed timer.
- **Stop recording when an incoming call or another event interrupts the live session.** This includes screen lock, loss of app visibility, and Android interrupting microphone access (such as permission revocation or OS-reported silencing). Ordinary acoustic silence is not an interruption. In split screen, continue while the app remains visible and capture is uninterrupted, even if the other pane has focus. Finalize and retain captured audio where possible and show why the take stopped. A new take always requires another tap on `REC`.
- When the interruption ends, automatically restart **live analysis only** as soon as Jam or Tuner is visible and microphone access is available. Refresh the tuner reading, pitch trace, and tempo graph from new audio; do not carry stale readings across the interruption. If microphone access is still unavailable, show why and wait for it to return. Do not restart a recording or file playback automatically.
- Show the active microphone input in Jam and Tuner. On an input-route change, continue only if capture remains uninterrupted and the recording format stays unchanged; otherwise stop and save as above. Test the built-in microphone for v1 and explicitly list any tested external routes supported at release.
- A single recording has a **maximum duration of 3 hours**. At the limit, stop and finalize the file and explain why recording stopped; do not silently start another file.
- Recording follows `Starting → Recording → Saving → Saved / Partial / Failed`. If startup fails before capture, report `Failed`. Prevent a second start while starting or saving; never resume a stopped recording automatically.
- Do not save pre-roll. The file begins with the first accepted sample after entering `Recording`. Derive elapsed time and the three-hour cap from audio frames written at the file sample rate. If the recording path loses frames, stop and attempt to retain the playable portion rather than silently joining audio across the gap. If only analysis falls behind, keep recording and leave a gap in the affected plot.
- Recording writes audio independently of A4. Live analysis uses the current global A4 even during a take; changing it does not alter recorded samples or start a new file.

### 4.3 Tuner view

- Retain the same shared header and recording controls as Jam; live recording continues uninterrupted when entering or leaving Tuner.
- No visible screen title, static captions, or demo controls. Use a large cents scale with a visibly larger, bold **0** and a longer, thicker vertical center mark, distinct from the moving pitch indicator.
- Place the manual global A4 setting at the bottom of Tuner. Show the current frequency above a horizontal slider that defaults to 440 Hz and adjusts in 1 Hz steps. The reference is shared by Jam and Review.
- Large current note and octave (e.g. `A3`), measured frequency (`220.3 Hz`), and signed cents offset from equal temperament under the selected A4 reference.
- Clear **flat / in tune / sharp** sign or wording alongside color. The default in-tune tolerance is ±5 cents, inclusive. Changing it affects Tuner status only, not trace position.
- Stable presentation of recognized note, with reasonable smoothing and fast response when the user changes notes.
- Silence, noise, clipping, missing permission, and ambiguous multipitch signal must not display a confident held note indefinitely.

### 4.4 Recordings and review

- Keep the Review playback screen free of a screen title, static captions, and an A4 readout. Maximize the plot by placing a single control row below it and the timeline: **File → filename → Play/Pause icon → Add bookmark icon**. The filename takes the remaining width before Play/Pause and truncates with an ellipsis, without wrapping. Use compact icons with accessible action labels and usable touch targets.
- Recording list: name, date, duration when available, basic file type and bookmark count. Add new recordings only after saving or classification as partial. Show retained incomplete files after restart, labeled **Partial — playable** or **Partial — cannot play**; disable Play for the latter. User can rename and delete with confirmation.
- Deleting a recording or imported item deletes the app's stored file and its bookmarks after confirmation. Never delete the original of an imported copy. If deleting the stored file fails, report the error and keep its entry and bookmarks.
- Playback: Play/Pause, seek timeline, elapsed/total time, speed `1×` initially (other speeds optional later), and a synchronized 12-lane pitch plot with the same proportional cents offsets as Jam. At the end of a file, the next Play starts from the beginning.
- **Add bookmark** saves the current position immediately while playing or paused. Tap a timeline bookmark marker to seek there without changing Play/Pause state. Press and hold the marker to delete it immediately without also seeking. No additional bookmark buttons, labels, confirmation, or Undo in v1.
- Group markers whose touch targets overlap on the full-file timeline. Tapping a group reveals individually selectable timestamps; the same tap-to-seek and long-press-to-delete gestures apply to individual markers. Expose equivalent actions to TalkBack without adding visible buttons.
- The pitch graph represents **decoded file audio**, not microphone input. Put the player's current position at the plot's right edge and show the preceding configured history window; freeze scrolling on pause. On seek, move the window to the new position, show only results already available for that window, and leave unanalyzed portions blank until played and a note is recognized. Discard in-flight results from before the seek. Timestamp pitch points by source audio, not delivery time.
- Block Play and Import during `Starting`, `Recording`, and `Saving`. During recording, tell the user to stop the recording first; during starting or saving, tell them to wait for the transition to finish. Do not start playback, open the picker, or stop recording automatically. Block Record during an active import copy; the user can wait or cancel the copy and then retry.
- Pause playback if the app goes out of view, loses audio focus, or headphones disconnect. Preserve position and require explicit Play to resume; do not automatically continue on the speaker.
- **File** opens the native Android document picker for supported MP3, FLAC, and WAV files. Request the user's primary shared `Music/` folder as the initial location; if the picker cannot open it, allow its normal fallback. Users may browse to supported files elsewhere. In v1, import **copies the selection into shared `Music/`** through MediaStore. This is a local transfer, not a server upload. Allow one import at a time with visible progress and Cancel. Never overwrite an existing file; on a name collision append ` (2)`, ` (3)`, and so on before the extension until the name is unique. Content-based duplicate detection is out of scope for v1. Explain unsupported, corrupted, or oversized imports without crashing.
- If a cloud-backed source cannot be read offline, ask the user to download it locally and retry. Once the copy is complete, playback and analysis must work in airplane mode.
- Make completed, playable imports available immediately. Analyze progressively during playback and bound the analysis cache; never stall playback waiting for the plot.
- Review uses the current global A4 set in Tuner for every recording and import without showing it on the Review screen. No per-file or session override, saved recording reference, or reset-to-original control. When global A4 changes, recalculate note labels, cents offsets, trace positions and colors from measured frequency, including while paused, without moving bookmarks or changing audio. Do not infer or suggest an A4 value from playback.

### 4.5 Preferences

Only A4 gets an on-screen control in the current prototype. Other preference requirements below remain in scope; their UI placement is deferred without adding a Settings swipe page now.

| Setting | Initial value / intent | Scope |
|---|---|---|
| Concert pitch A4 | **440 Hz**; **415–466 Hz** in **1 Hz** steps (provisional) | One global reference for Jam, Tuner and all Review plots, set at the bottom of Tuner |
| BPM stable band | **±3 BPM**; configurable **±1 to ±10 BPM** in **1 BPM** steps (provisional) | Tempo trend/number coloring |
| Tuner in-tune cents tolerance | **±5 cents**, configurable **±1 to ±15 cents** | Tuner status only; does not move the trace |
| Note naming | **Sharps** by default; flats available | Labels only; does not move the trace |
| Pitch-history visible duration | **8 s**, configurable **3–20 s** | Scrolling display only; independent of the prominence window |
| Prominence window | **10 s**, configurable **1–60 s** (provisional) | Jam lane-label brightness and `Top notes`, independent of tempo and visible plot duration |

Validate configurable values before applying them. Reject invalid values with an explanation and retain the last valid setting. Persist global settings.

When global A4 changes, remap retained live frequencies to the new reference for trace positions, trace colors and prominence together. If live history cannot be remapped, clear the live trace and prominence together. This is a calibration change, not prominence recoloring. Apply it even during an active take.

## 5. Signal analysis semantics

### 5.1 Common audio pipeline

```text
Microphone → PCM capture → timestamped ring buffer ┬→ dominant-pitch analysis and prominence
                                                   ├→ onset / beat / BPM analysis
                                                   └→ WAV writer, only while recording

Imported file → decode PCM → same analysis interfaces → timestamped results
                                                └→ synchronized audio playback
```

- Begin with a native `AudioRecord`-based capture path, **mono PCM16**, preferably **48 kHz if supported**; runtime negotiation and fallback must be explicit. Sample rate and channel conversions must be testable.
- Start with four threads for the live audio and graph path: a dedicated microphone capture thread, a separate WAV writer active during recording and saving, one worker for pitch, BPM, prominence and graph preparation, and Android's existing UI thread. This count excludes Android, media-library and unrelated app threads. Give capture the highest priority in this path using Android-supported audio thread priority. Prioritize recording writes and live analysis over noncritical work. Run BPM updates less frequently than pitch and reuse the analysis worker for decoded audio in Review. Keep capture and disk writes separate; split the analysis worker only if profiling on the reference devices shows a need. **No microphone reads, file writes, or heavy DSP on the UI thread**.
- Use separate bounded, preallocated capture-to-writer and capture-to-analysis queues, sized in milliseconds of audio and tuned on the reference devices. Capture offers buffers without waiting. A published buffer is read-only and cannot be reused until its last consumer releases it; dropping a queued buffer also releases it. A full writer queue triggers a safe stop and an attempt to finalize the playable portion. Never silently skip recorded samples.
- Under contention, defer noncritical work first. In the analysis worker, handle fresh pitch data first and bound or defer BPM work so one update cannot block pitch processing for a prolonged period. Dropped analysis frames leave timestamped gaps; skipped redraws do not erase analyzed history.
- Thread priorities are best-effort. Android scheduling, thermal limits, and system load can still delay work; the app must degrade as described above rather than promise immunity to throttling.
- All analysis results carry a **source-audio timestamp**, confidence/quality, and `no reliable estimate` state.
- Rendering runs at its own cadence. Smooth the visual connections between consecutive reliable estimates, including dominant-pitch jumps, without altering measured points. Never connect across missing estimates, capture gaps, or opposite plot edges.
- Use a dedicated Jetpack Compose `Canvas` for the pitch plot in Jam and Review. Read rapidly changing trace and scroll state only during drawing, not composition or layout. Cache guide geometry and label layout until their inputs change. Publish prepared graph data through a single latest-value slot; a snapshot must remain stable while Compose draws it. Draw at display cadence rather than queueing redraws. Plot updates must not trigger recomposition or layout of the surrounding screen. Keep BPM, recording timer and controls on independent state updates.

### 5.2 Pitch and 12-note activity

- **Tuner (predominantly monophonic):** evaluate autocorrelation/YIN-family or comparable pitch estimators against real instrument fixtures; output frequency, note, cents and confidence. Do not assume an algorithm until benchmarked.
- Validate standard four-string bass through the built-in microphone on the reference devices using real plucked notes, in tune and deliberately detuned. If the fundamental is weak, use available harmonic evidence to resolve the actual note where reliable; do not confidently report an octave-up overtone as the note. Measure low-register response separately from higher notes. Five-string low B0 (approximately 30.9 Hz) is not a v1 requirement unless separately validated and added to scope.
- **Jam and Review:** output at most one pitch per timestamp. Rank reliable candidates by estimated strength of the pitched sound, not the size of an individual spectral peak. Do not draw quieter simultaneous pitches or average their frequencies into the selected pitch.
- For near ties between simultaneous candidates, keep the current pitch only while it remains reliably detected; with no reliable incumbent or clear winner, leave a gap. A competing pitch must lead by a minimum strength margin for a minimum duration before replacing a still-reliable incumbent. The margin and duration are benchmark parameters. Never keep a stale frequency after the current pitch becomes unreliable.
- Retain frequency, nearest pitch class, cents offset, and confidence for the selected pitch. Test overtone rejection, percussion, room reverberation and dominance changes with real instruments as well as synthetic tones.
- An exact list of *sounding notes* or isolated musician intonation is **not** promised in dense mixes.

### 5.3 Tempo

- Derive an onset/beat-strength envelope and estimate periodicity, with a confidence value and a stabilization stage.
- Handle **half-time/double-time ambiguity** (e.g. 60 vs 120 BPM); do not flip between aliases without clear evidence.
- Compute trend relative to a smoothed baseline of prior reliable estimates; indicate speedup, stable (within configured band), or slowdown only after the baseline is established. Leave gaps during uncertainty. Rebuild the baseline after a confirmed half/double-time correction rather than showing the correction as a tempo trend.
- Show `tempo uncertain` or `no steady beat` when beat definition is weak, rather than displaying frozen false precision.
- User may later need tap-tempo fallback; **not included unless explicitly prioritized**.

### 5.4 Prominence highlights

- Rank each nearest equal-tempered pitch class by the **total duration it was the reliably detected dominant pitch** within the preceding X seconds of source time. Fold octaves together; never count two pitches at the same instant. Do not credit silence, unreliable or missing frames. At startup, use the available history. Retain up to 60 seconds of observations during the live session so enlarging X can use existing history immediately.
- Highlight up to four notes with positive detected duration, breaking ties by most recently heard. Use the same ranking for Jam lane-label brightness and `Top notes`, independent of BPM. Remove highlights as notes drop out of the set. When no notes qualify, clear `Top notes` and restore normal brightness.

### 5.5 Recording and playback format

- **Proposed recording format: uncompressed PCM WAV** (PCM16, mono at captured sample rate). This generally avoids CPU-intensive encoding and preserves analysis input, but uses substantially more storage than compressed formats.
- Store recordings and import copies directly in the device's shared **Music folder (`Music/`)** through MediaStore. Recording filenames must be unique, readable and timestamped.
- On a failure the app can handle, attempt to finalize the WAV header and close the open file before exiting or ending the session. Leave the partial file in place, even if finalization fails. Report the failure and distinguish a playable partial recording from an unfinalized file when possible. An abrupt process kill or power loss may prevent cleanup; do not guarantee recovery in that case.
- Write to a pending MediaStore item and persist its URI in an unfinished library entry as soon as it is created. Finalize and close the file before publishing it, then mark the library entry saved. On restart, reconcile interrupted writes and publication without duplicating entries. Recover valid completed recordings, label retained partial recordings, and clean up abandoned import copies without touching their originals. Test interruption before and after finalization, publication, and the library update, including the gap between item creation and persisting its URI.
- Enforce the **3-hour recording limit**. Mono PCM16 at 48 kHz needs approximately **1.04 GB** for three hours. Before starting, require space for a short recording plus a safety reserve, not the full three hours. Monitor space while recording and stop before using the reserve needed for finalization. Supported capture-rate fallbacks must remain compatible with the chosen WAV container and duration limit.
- **Input:** MP3, FLAC and WAV via file picker, copied to shared music storage before being treated as an imported library item; preserve the source file. Clean up failed or canceled import copies without touching the original or showing a successful entry. Use Android-supported decoding. Investigate **Media3/ExoPlayer** for playback and an explicit decoded-PCM feed for visualization (Media3 alone is not assumed to provide analysis frames automatically).
- Store bookmarks/metadata locally (e.g. Room database), referenced to media URI and media timestamps. If a previously saved file is no longer accessible at its stored URI, show **Missing**, disable Play, and retain its bookmarks until the user removes the entry. A move that preserves access through the URI does not make the file missing.
- Import has a separate provisional **2 GB per-file** limit, not a three-hour duration limit. Check declared size when available and enforce the limit while copying even if the size is unknown. Keep the storage reserve; on failure or cancellation remove only the incomplete copy.
- Start the tested import matrix with mono and stereo MP3, FLAC and PCM WAV. Validate the actual encoding, channel count and sample rate, not just the extension; explain unsupported combinations. Preserve source channels for playback. Analyze stereo without relying solely on averaging left and right; test both a pitch present in only one channel and opposite-phase copies of a pitch on the two channels.
- Seeking, playback and analysis share the player's media timeline. Analyze decoded file audio, never sound picked up by the mic. The plot works through headphones as well as the built-in speaker, without requiring speaker output or mic permission. Cache early results but do not draw them before their source timestamps reach playback. The provisional steady-playback lag gate is **≤150 ms at the 95th percentile**, measured against audible output on the active route. Test the built-in speaker and headphone playback, record the headphone route used, and measure voiced-region coverage and first trace after a seek separately. Test pause, resume and repeated seeks near the end of a three-hour file.
- Tag decoded audio and analysis results with the current playback session and seek generation as well as their media timestamps. Discard in-flight results from older generations. Use the playback position, not decoder progress, to reveal points. If analysis falls behind, keep playing and leave the missing region blank until valid results arrive. Prototype the decoded-PCM handoff and output-route alignment early.

## 6. Application architecture

Start with one Android app Gradle module. Treat the boundaries below as Kotlin packages, not separate Gradle modules.

Use one app-scoped audio session controller, independent of screen recreation. Serialize commands and events such as Record, Stop, lock, navigation, mic interruption and writer failure. Compose observes the controller's state and does not own capture or finalization. Stop is idempotent: competing stop signals must not finalize the same file twice. Tag asynchronous callbacks with a session ID and ignore callbacks from an old session. The controller must not block the UI or capture thread on file I/O and does not require another dedicated worker thread.

| Package | Responsibility |
|---|---|
| `audio-capture` | Permission-aware mic input, timestamps, routing, interruption handling |
| `audio-file` | WAV recording, import, decode, playback coordination, MediaStore |
| `dsp-pitch` | Dominant-pitch selection, nearest-semitone mapping, cents and confidence |
| `dsp-tempo` | Onsets, BPM history, confidence and tempo trend |
| `analysis-core` | Shared timestamped frames, windows, state and cache formats |
| `data` | Settings and recording/bookmark metadata (DataStore / Room candidates) |
| `ui` | Jetpack Compose screens with an independently updating Canvas for the Jam and Review pitch plot |

Keep DSP as pure Kotlin/JVM-testable code where practical; use native optimizations only where profiling justifies complexity. Media decoding/playback APIs are replaceable behind interfaces. Avoid hard-coding algorithm constants in UI logic.

## 7. Quality, privacy and runtime states

- Request `RECORD_AUDIO` at the point of first live capture; explain refusal/retry. App remains useful for imported-file review if permission is denied.
- No server processing or uploads. No default storage of microphone buffers; only explicit recording persists audio.
- Completed recordings and import copies in shared music storage are visible to other apps with appropriate access. Exclude analysis caches and recording/bookmark metadata from Android cloud backup. Shared files may still be backed up by the user or another app.
- Support: `permission missing`, `no signal`, `weak/noisy`, `clipped`, `tempo unknown`, `pitch uncertain`, `audio interrupted`, `storage full`, `unsupported file`, `recording active`.
- Mic usage is visible. Keep the screen awake while the app is visible in the foreground. Stop and finalize the take on screen lock, session interruption, or loss of app visibility. Automatically restore live analysis in visible Jam or Tuner once microphone access returns, but never restart the take. Background recording is out of scope for v1.
- Display stays readable at arm's length and in dark environments; symbols/text accompany any essential color status. Expose the current note, tuning status, BPM trend and recording state as accessible text without announcing every analysis frame.
- Separate signal-processing rate from rendering rate to avoid jitter. Benchmark sustained CPU, battery, heat, recorder queue depth, dropped audio and graph frames, and graph latency on the named reference devices, including under CPU, storage and thermal load.
- Retain file duration, source type and analysis algorithm version where relevant. Tuning calibration is one user setting, not per-file metadata.

## 8. Verification and acceptance criteria

Use deterministic **generated and prerecorded PCM fixtures**. All numeric tolerances, DSP constants and confidence thresholds must be named, documented and tested. No performance or device-compatibility claim without physical evidence.

Before release acceptance, specify the supported pitch range and name the reference phones. Score both detection coverage (fraction of eligible voiced time with a valid estimate) and cents error for each labeled voiced fixture; withholding estimates cannot pass on accuracy alone. Measure live response, dominance switching, first trace after seek and steady-playback lag separately. Tempo fixtures must include steady beats, half/double-time ambiguity and material without a steady beat where withholding BPM is correct.

Provisional clean sustained-note fixture gates: cover **E1–C6 in Tuner** and **E2–C6 in Jam and Review** at A4 = 440 Hz, achieve **≥95% detection coverage** per fixture, and keep **95th-percentile absolute pitch error ≤3 cents** on valid estimates. Validate on named reference devices before making release claims. Set separate gates for real bass notes, real-instrument mixtures and post-seek startup; do not apply the clean-tone gates to dense mixes by default.

Provisional timing gates for benchmarking on the named devices: first reliable live pitch within **250 ms** of a clean supported note onset; switch within **350 ms** after a challenger establishes a sustained, clearly measurable lead; first trace within **350 ms** of continuous voiced playback after a seek; acquire reliable tempo within **12 s** and within **±2 BPM** on steady, unambiguous click fixtures from **60–180 BPM**. Include real instrument recordings and complete a full three-hour capture and review test on the baseline devices. Confirm or revise these targets from measurements, not assumptions.

Measure live pitch latency from the actual audio onset to the visible result, not from DSP start. Include short plucked notes as well as sustained tones; report them separately.

Benchmark low bass notes separately, particularly E1. Use the provisional **250 ms** live response target for clean notes at E2 and above; set the lower-register response gate from device measurements before release acceptance. Bass tuning remains a v1 requirement even if it needs a different latency target.

| Story | Observable acceptance criteria | Verification level |
|---|---|---|
| JAM-01 Live capture | Starts with permission; stops cleanly; denied and interrupted states visible; no default file saved | Integration + emulator + physical device |
| JAM-06 Interruption recovery | An incoming call that interrupts the live session or another interruption stops the take. Once Jam or Tuner is visible and the mic is available, fresh tuner and graph readings return without user action, stale readings are cleared, and recording remains off until `REC` is tapped. | Integration + physical device |
| JAM-02 Scrolling pitch and intonation | One bold trace on 12 large labeled lanes; A2/A3/A4 share a centerline; ±25 cents shifts a quarter semitone. Near ties retain a reliable incumbent and a sustained clear lead switches within the deadline. Connect reliable jumps smoothly without inventing observations; leave gaps for unreliable frames and verify B/C wrapping and clean-fixture coverage. | Unit + integration + physical + musician validation |
| UX-01 Screen layout and navigation | Swipe between Jam, Tuner and Review without bottom navigation. Verify floor-distance note legibility and full-height header notes in Jam, the enlarged zero and bottom A4 controls in Tuner, and Review's compact bottom control row with an ellipsized long filename. File requests Music as its starting location and can browse elsewhere. | Integration + physical + musician validation |
| JAM-05 Pitch plot legibility | Check a trace exactly on its guide and offsets of ±5 and ±25 cents on a narrow phone at stage brightness and in grayscale. Verify the trace stays distinct from every guide, the offsets remain proportional, and a slow B/C bend wraps without a line through the center. Use Tuner for precision if very small offsets are hard to read visually. | Physical + musician validation |
| JAM-03 Prominent notes | Rank by reliably detected duration, not frame count; changing X changes expiry; former prominent notes lose their highlight without recoloring past traces; works without BPM | Unit + integration |
| JAM-04 Tempo history | Known-tempo fixtures exercise trends and beat aliases; verify 30-second history, gaps for uncertain intervals, retention across Jam/Tuner switches, and reset after live capture restarts | Unit + integration + musician validation |
| TUN-01 Tuner | Known-frequency, detuned, silent and harmonic-rich notes produce correct note/Hz/cents or uncertainty under chosen A4. Verify real plucked E1, A1, D2 and G2 through the built-in mic, including low E1 with a weak fundamental: score detection coverage and cents error, check for octave-up errors, and measure low-register response separately. | Unit + physical device + musician validation |
| REC-01 Recording | The outlined `REC` button becomes a solid red `HH:MM:SS` timer while recording; tapping it stops capture and restores the outlined button, disabled until saving finishes. No pre-roll; elapsed time and the three-hour cap follow written frames; recording gaps stop capture, analysis gaps do not; changing A4 does not alter the file; rotation and resizing preserve Recording and Saving; lock, mic interruption or loss of visibility stops capture without auto-resume; space limits and partial-file handling are verified. | Integration + physical device |
| REC-02 Playback/import | Verify recording/import guards, pause without auto-resume, original preservation, stereo fixtures and offline playback after copying. On speaker and headphones, the plot works without mic permission, does not reveal future pitches, and discards stale results after seeks. Measure lag, first trace after seek and voiced coverage separately. | Integration + physical device |
| REC-03 Bookmarks and file lifecycle | Add saves immediately; tap seeks without changing state; long-press deletes without seeking, confirmation or Undo; overlapping markers are individually selectable and TalkBack actions work. Retained partials and Missing entries retain their specified behavior; deletion preserves import originals. | Unit + integration + physical device |
| SET-02 Global A4 | Changing A4 at the bottom of Tuner updates Jam, Tuner and Review, including a paused plot and live analysis during a take. Audio and bookmark timestamps do not change. No per-file or session override is offered. | Unit + integration |
| SET-01 Settings | A4, ±BPM band, prominence window and other agreed settings persist; relevant downstream results recompute correctly | Unit + integration |
| QLT-01 Responsiveness | No heavy DSP or blocking audio I/O on the UI thread. Verify that trace-only updates do not trigger recomposition or layout of the surrounding screen. Under load, verify noncritical work yields first, graph backlog is bounded, and graph overload does not lose recorded audio. Inject a recorder overrun and verify a safe stop with partial-file handling. Measure graph latency, CPU, battery and heat against agreed targets. | Profiler/benchmark + physical device |
| QLT-02 Session and file recovery | Competing stop signals finalize only once; stale callbacks and seek results are ignored. Inject failures around MediaStore creation, file finalization, publication and library updates; restart without duplicate entries or lost originals. | Unit + integration + physical device |

**Targets still to agree:** time-to-first-use, performance on real-instrument fixtures, BPM error by genre, and sustained CPU/battery use. Validate provisional pitch, tempo, and playback gates on the reference devices. Maximum single recording duration is **3 hours**. Do not substitute unmeasured promises.

## 9. Build order / Codex handoff plan

**First prototype:** microphone → bounded queues → WAV writer and Jetpack Compose pitch trace. Force analysis to fall behind and rotate the device mid-take. Verify that the plot can show analysis gaps while the resulting WAV remains continuous. Separately validate the Review PCM handoff and sync on speaker and headphones before committing to the playback implementation.

1. **Foundation:** app shell, permission/audio states, deterministic PCM fixture tooling, source timestamps, reusable analysis and view-state types.
2. **Monophonic Tuner:** A4 calibration, note/Hz/cents/confidence; measured on real instruments.
3. **Jam pitch canvas:** scrolling 12 lanes, rolling prominence, visual smoothing.
4. **Recorder/reviewer:** WAV writing, MediaStore folder, list, file import, playback, synchronized re-analysis and bookmarks.
5. **Tempo:** onset detection, stabilized BPM, history plot and trend classification.
6. **Hardening:** device benchmarks, UI legibility, interruptions, accessibility and extended musician sessions.

These are **implementation increments**. Tempo and prominent-note highlights remain intended initial-release features; harmonic-role, key/scale and modulation detection do not.

### Recommended repo documentation layout (when implementation starts)

```text
docs/
  prd.md                 # Product requirements and approved scope
  ux-spec.md             # Screens, interaction and visual behavior
  architecture.md        # Data pipeline, interfaces, ADR links
  research/              # DSP experiments, fixtures, benchmarks
stories/                 # Story-specific acceptance, verification, delivery status
sprint-status.yaml       # Only delivery status summary
```

Changes to product scope update the PRD; UI changes update UX; engineering decisions update architecture; delivery progress belongs in stories and `sprint-status.yaml`. This draft can be split into those files after review, rather than coexist as a competing master plan.

## 10. Decisions required at next review

1. **Display settings:** Validate the prominence window and plot-history defaults and ranges on a phone, and verify that the single highlight style is readable at arm's length.
2. **Intonation:** Set supported range, cents accuracy, confidence thresholds, strength margin and switch delay for Jam and Review. Validate the midpoint rule and B/C wrapping on the single trace.
3. **Phone ergonomics:** Validate the portrait-first layout, including header density, note labels and recording controls on narrow screens and in landscape. Prototype the original pitch palette and verify that the colored guides stay subdued, the trace is distinguishable in every hue, and note identity and tuning offsets remain readable in grayscale.
4. **Storage and formats:** Set the free-space reserve and recovery limits after abrupt process loss, validate the provisional import cap, and complete the tested encoding and sample-rate matrix.
5. **Playback visualization:** Set cache bounds and real-instrument voiced-region coverage targets; validate the provisional first-trace-after-seek target.
6. **Audio lifecycle:** Validate loss of visibility, transient overlays, lock, microphone interruption and screen-awake behavior on reference devices.

## 11. External references and interpretation

- **Compose prototype references:** [Jam](docs/prototype/jam.png), [Tuner](docs/prototype/tuner.png), and [Review](docs/prototype/review.png). These are layout references. Live pitch is simulated, and the prototype plays selected files directly without importing a copy or analyzing their pitch. The v1 import and analysis requirements above still apply.

- [PitchLab Guitar Tuner (PRO) — feature description](https://pitchlab-guitar-tuner-pro.soft112.com/): reference for Pitch Spectrogram, chromatic tuner, stage tuner and multi-view concepts. It explicitly distinguishes a pitch spectrogram from its chord matrix and strobe views. **Visual inspiration only**.
- [PitchLab spectrogram example screenshot](https://www.mobiles24.com/static/previews/downloads/default/294/P-615348-PE7JcW9zBI-2.jpg): reference for chromatic scrolling traces. Screenshots sometimes show **octave-labeled** lanes; do not assume they match this draft's 12 pitch classes exactly.
- [liveBPM — Beat Detector (Daniel Bach), Google Play](https://play.google.com/store/apps/details?id=com.DanielBach.liveBPM): tempo history curve and BPM estimation reference; emphasizes that clear rhythmic impulses improve reliability.
- [liveBPM — Softonic page supplied in brief](https://livebpm-beat-detector.en.softonic.com/android): same Daniel Bach app, tempo graph and zoom modes.
- [Android `AudioRecord` documentation](https://developer.android.com/reference/android/media/AudioRecord): PCM capture and timestamp APIs.
- [Android Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats): MP3/WAV/FLAC playback support and codec/device caveats.
- [Android shared-media storage / MediaStore](https://developer.android.com/training/data-storage/shared/media): shared audio storage in `Music/`.
- [Android microphone foreground-service rules](https://developer.android.com/develop/background-work/services/fgs/service-types): background-capture restrictions when considering future behavior.

**Reference caveat:** The user brief mentions `pic1` and an attached pitch screenshot, but those image files were not present in the supplied message. The ASCII layout and PitchLab picture interpretation need visual confirmation during UX review.

---

*End of v0.23 review draft. Open points are intentionally marked rather than silently resolved.*

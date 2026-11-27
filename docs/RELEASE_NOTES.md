# Gitune v0.2.1

A small follow-up to v0.2.0. Gitune is still an offline Android 11+ preview, not a Play Store release.

- **File review:** Picking the same recording again reuses its existing Music copy instead of creating `(1)`, `(2)` duplicates. The delete confirmation still recognises the original file for older imports.
- **Jam:** The playlist backdrop is brighter and easier to read while remaining behind the pitch graph.
- **Documentation:** `docs/APP_CONTEXT.md` records the current architecture, preference keys, tests, and CI/release setup for maintainers.

The v0.2.0 features are all included: adaptive pitch sensitivity, multi-note Jam and file review with all twelve semitone brightness labels, a tuner fallback for a sustained single peak, tempo detection that works on steady percussion, the playlist setlist, and keep-screen-on.

## Downloads

- `Gitune-v0.2.1.apk` — installable Android preview APK (debug-signed).
- `Gitune-v0.2.1.apk.sha256` — SHA-256 checksum for the APK.
- **Source code** — GitHub’s archives for the matching `v0.2.1` tag. See the repository README for build instructions and the GPLv3 license.

On Android, open the APK to install it. Install it over an earlier Gitune build to keep your settings, bookmarks and recordings. If your old build was signed with a different key, Android will not install the update over it. Before uninstalling an old build, back up any recordings you want to keep; uninstalling clears in-app settings and bookmarks.

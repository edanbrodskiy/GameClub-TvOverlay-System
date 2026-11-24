# MVP Session Overlay

An Android TV app for MVP Lisboa with a full-screen TIME IS UP overlay over HDMI,
a local session timer, and warnings in the top-right corner.

**Version:** `0.2.1-compact` · **Package:** `pt.mvplisboa.sessionoverlay`.
Displaying the overlay over a PS5 on HDMI 4 and restoring the underlying picture
after hiding it have been confirmed on a TCL Smart TV Pro running Android 12.
See the [test results](docs/TEST_RESULTS.md) for details.

## Download

[Release v0.2.1-compact](https://github.com/edanbrodskiy/GameClub-TvOverlay-System/releases/tag/v0.2.1-compact)
includes a ready-to-install APK, the complete source archive, and `SHA256SUMS` to
verify your downloads. Installing the APK requires only ADB; building the app and
installing Android Studio are unnecessary.
Follow the [installation instructions](docs/INSTALLATION.md#установка-из-github-release).

This is a standalone MVP for sideloading on Android TV. The APK is signed with a
debug key and includes permission-protected ADB controls. Payment and ggLeap
integrations are not yet implemented.

## Quick start

Building requires JDK 17, Android SDK Platform 34, and Build Tools 34.0.0.
Use ADB to communicate with the TV. The test and packaging scripts require
Python 3.10+ with no additional dependencies.

Open **this folder** in Android Studio or run:

```bash
./scripts/build.sh
python3 scripts/package.py
```

The build script uses the Gradle Wrapper to build the debug APK, run Android Lint,
and execute 11 timer logic tests. On macOS, it automatically selects an installed
JDK 17. Set the SDK path through `ANDROID_HOME` or `sdk.dir` in a local
`local.properties` file. The default macOS SDK location is detected automatically.

Packaging produces:

- `dist/mvp-session-overlay-0.2.1-compact-debug.apk` — installable APK.
- `dist/mvp-session-overlay-0.2.1-compact-source.zip` — source code without SDKs, caches, or local paths.
- `dist/SHA256SUMS` — file checksums.

## Installation and controls

Commands target `192.168.10.53:5555` by default. To use another device, set
`export MVP_ADB_SERIAL=IP:PORT`.

```bash
./scripts/tv.sh connect
./scripts/tv.sh install
./scripts/tv.sh permission
./scripts/tv.sh activate
./scripts/tv.sh status
```

`activate` opens the setup screen, waits for confirmation that the service is
running in the foreground, and returns to the previous screen. This is required
on the tested TCL firmware. Granting overlay permission through AppOps is a
diagnostic method; you can also grant permission through the app's setup screen.
See the [installation guide](docs/INSTALLATION.md) for detailed setup instructions.

```bash
./scripts/tv.sh demo          # Four warnings, the final screen, then auto-hide after 53 seconds.
./scripts/tv.sh timer 1200    # Start a local 20-minute session.
./scripts/tv.sh extend 300    # Add 5 minutes and hide the session-ended screen.
./scripts/tv.sh notice 900    # Preview a warning: 900 / 300 / 60 / 30 seconds.
./scripts/tv.sh show
./scripts/tv.sh hide          # Hide all windows and cancel the test timer or demo.
```

Warnings appear with 15 minutes, 5 minutes, 1 minute, and 30 seconds remaining,
and stay on screen for 10 seconds. The cards are 20% smaller than in the previous
version, with approximately 25% background transparency and fully opaque text.
On the tested TCL, each card measures 499×265 px.

## Project structure

```text
app/src/main/       App code, resources, timer, and overlay windows
app/src/debug/      Permission-protected ADB controls, debug APK only
app/src/test/       JVM tests for timer logic
gradle/wrapper/     Gradle Wrapper 8.7 with SHA-256 verification
scripts/           Build, packaging, ADB, and test scripts
docs/              Installation, architecture, test results, and original guide
dist/              Generated APK, source archive, and checksums
```

## Documentation and status

The linked documentation is currently in Russian.

- [TCL installation and troubleshooting](docs/INSTALLATION.md).
- [Architecture and development](docs/DEVELOPMENT.md).
- [Automated and device testing](docs/TESTING.md).
- [Test results](docs/TEST_RESULTS.md).
- [Changelog](CHANGELOG.md).
- [Original technical guide](docs/reference/MVP_Lisboa_TCL_Overlay_Development_Guide.md).

A standalone Android prototype is implemented. A REST API on port 5002, API token
authentication, a boot receiver, server-side session state, and ggLeap integration
are planned next steps. The local timer is not connected to payments. Restarting
the process resets the session and does not restore the session-ended overlay.
Holding the overlay for 30 minutes and reboot scenarios have not yet been tested.

`minSdk 26`, `targetSdk 32`, `compileSdk 34`. The target SDK was selected for the
local TCL running Android 12. The target SDK and foreground service handling need
to be updated before production distribution.

# Build Melo from source

These instructions are for contributors and people who want to build Melo themselves. Most users should install the signed APK from [GitHub Releases](https://github.com/aarush-io/melo/releases/latest).

## Requirements

- JDK 21
- Android SDK Platform 37 and Build Tools 36.0.0 or newer

No third-party archive is needed. The APK contains no native Spotify runtime and runs on every Android ABI.

## Windows

Clone the repository and configure the Android SDK using `ANDROID_HOME` or an ignored `local.properties` file.

Build and package the signed release:

~~~powershell
.\gradlew.bat :app:assembleRelease -PmeloUpdateRepository=aarush-io/melo
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-release.ps1
~~~

The APK, SHA-256 checksum, and signing-certificate report are written to the ignored `dist` folder. Create the Melo signing key once with `scripts/create-signing-key.ps1` and back up the ignored `.signing` directory securely. Never commit the signing key or its properties file.

## macOS and Linux

Use `./gradlew` with the same Gradle property and packaging script.

## Development build

~~~powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
~~~

## Playback implementation notes

Spotify supplies metadata only: catalogue, library and playlists. Every track, Spotify ones included, plays from YouTube Music (`ytm://` addresses read by `YouTubeDataSource`) or from a local file. The protocol notes distinguish static checks, tests with doubles, and validation against live services. A moving playhead or a populated buffer is not proof of audible playback; check decoded audio after seeks and across track changes.

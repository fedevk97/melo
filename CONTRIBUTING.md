# Contributing to Melo

Thanks for considering a contribution.

See the [build-from-source guide](docs/BUILDING.md) for the Android SDK requirements and release packaging steps.

## Ways to help

- Report bugs with clear steps to reproduce.
- Test the app on other Android devices and versions.
- Improve the protocol notes in [`protocol/`](protocol/), with the evidence level stated (static, tested with doubles, or live).
- Improve the build, the CI or the documentation.
- Fix bugs in playback, offline storage, the library or the interface.

## Before opening an issue

Include:

- the Melo version or commit;
- your Android version and device model;
- whether you installed a release, a CI artifact or your own build;
- steps to reproduce;
- relevant `adb logcat` output, **with tokens, usernames and email addresses removed**.

## Pull requests

1. Keep the change focused and explain why it is needed.
2. Run `./gradlew :app:testDebugUnitTest :app:lintDebug`.
3. For audio changes, check the decoded signal, not only the player state: after a seek and across a track transition.
4. Write code, comments and documentation in English. New user-facing strings go in `res/values/` (English) and `res/values-fr/` (French).

## What will not be accepted

- Anything that bypasses Premium restrictions, shares accounts or extracts decrypted audio.
- A switch to the public Web API, App Remote or the installed Spotify app as a dependency.

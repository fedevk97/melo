# FAQ

## Do I need a Spotify account?

No. Without an account, Melo plays the YouTube Music catalog and the audio files on your phone, and Spotify tracks of imported playlists play from their closest YouTube Music match. With an account, it also shows your Spotify library and playlists. Spotify supplies the catalogue and your library; every Spotify track plays from its YouTube Music match, with a Free or a Premium account alike. If something fails with your account, Settings → Diagnostic report gives a text to attach to a bug report.

## Do I need the official Spotify app installed?

No. Melo does not use the installed app, and it contains no part of it.

## Where do I get the APK?

From the [latest release](https://github.com/aarush-io/melo/releases/latest). Each release lists the SHA-256 of the APK and a signing-certificate report. You can also build it yourself from the source: see [Build from source](BUILDING.md). A build you made yourself is signed with a different key, so uninstall the other one first (this erases the session and downloads).

## How do application updates work?

Melo checks GitHub at startup. A newer release opens a dialog with release notes and a download button. Android confirms the installation. If you dismiss the dialog, the same release is offered again at the first startup at least 24 hours later. Settings → Check for updates checks immediately.

## Does it work on x86 emulators or 32-bit phones?

Yes. Melo runs on every Android ABI: 64-bit and 32-bit ARM, x86 and x86_64.

## How do I sign in?

Enter the email address of your Spotify account, then the code Spotify emails you. If your Spotify account is linked to Google, "Sign in with Google" opens Google's page instead. Signing in with a password is also available. The session is kept encrypted on the device and restored at each launch. Settings → Spotify account → Sign out erases it.

## Why no podcasts, audiobooks, videos or Canvas?

Melo is for music only. Those items are filtered out of search, pages and the library.

## Are my changes synced with the official app?

Yes. Saved tracks, albums, followed artists and playlists use the same services as the official app. Pins are the exception: they stay on the device.

## Can I use my YouTube Music playlists?

Yes. Connect a Google account in Settings → Accounts: your YouTube Music playlists appear in the library and stay synced with the account, and new playlists can be created there. The YouTube Music catalog still plays without an account.

## Can I move a playlist from Spotify to YouTube Music?

Yes. On the playlist's page, ⋮ → Convert to…, then choose Spotify, YouTube Music or a multi-service playlist. Melo looks up each track on the destination, highlights the matches it is not sure of, and lets you change or leave out any track before creating the playlist.

## How does offline playback work?

Download an album, a playlist or a track from its page. Recently played and upcoming tracks are also kept in a cache you can size in Settings → Cached storage. Audio stays in the app's private storage. The offline mode switch stops all network access from the app.

## Can I use Melo to save MP3 files or skip Premium?

No. Melo never writes decrypted audio and does not unlock anything your account does not have.

## Where is the protocol documentation?

In [`protocol/`](../protocol/). It can be read without the code.

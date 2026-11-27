# Melo Privacy Policy

Last updated: November 20, 2026

Melo uses basic app-usage analytics to understand how the app is used and help improve it.

## Usage analytics

Analytics is on by default. When enabled, Firebase Analytics helps us estimate app sessions and daily or monthly active installations. Firebase automatically receives app-lifecycle and engagement signals, a per-install app-instance identifier, app and Android version details, and basic device information. Google Analytics may also infer a broad region, such as a country, from network information.

Separately from analytics, Melo tells `api.melo.aarushsagar.com` when you are using it, for a public count of how many people are listening right now. While Melo is on screen or playing music, including in the background, it sends a random installation ID and the platform (Android) every five minutes, and a closing signal once you have left the app and the music has stopped. Installation IDs are HMAC-hashed on the server, and only the hash, the platform and the time of the last signal are kept. They are deleted when that closing signal arrives, or 90 seconds after a missed signal. Public stats contain counts only. Nothing about you or your music is sent, so this runs whether analytics is on or off.

Melo does not send music account details, Spotify cookies, track or artist names, track IDs, searches, playlists, queue contents, or listening history. Playback time is an estimate based on player-reported progress and does not prove that audio was audible. Automatic screen-view reporting, Android Advertising ID collection, and ad-personalization signals are disabled.

You can switch analytics off anytime in **Settings → Privacy**. This stops future analytics collection by Melo; the live-count signal above is not affected. Data already sent to Firebase remains subject to Firebase/Google Analytics retention.

Google processes Analytics data to provide its reporting service. See [Google’s privacy information](https://policies.google.com/privacy) and [Firebase’s Android data disclosure](https://firebase.google.com/docs/android/play-data-disclosure).

## Music services and on-device data

When you connect a music service, search its catalog, or play content, that service receives the requests needed to provide the feature. Its own privacy policy applies. Melo stores app settings, caches, and local downloads on your device; these are not sent to Firebase Analytics.

Questions or corrections? [Open an issue in the Melo repository](https://github.com/aarush-io/melo/issues).

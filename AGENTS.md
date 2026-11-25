# Working in this repository

- Work on the current branch. Do not create branches or worktrees unless asked.
- Melo carries no Spotify native runtime and runs on every Android ABI. Spotify is the metadata source (catalogue, library, playlists); every Spotify track plays through its YouTube Music match. Do not reintroduce the Orbit engine, the Spotify HLS path or Widevine.
- The client uses the native Android protocols. Do not substitute Spotify for Developers OAuth, the Web API, App Remote, a playback WebView or a dependency on the installed Spotify app.
- The Android client constants in network messages are those of the analysed package. They are not a developer app registration.
- A moving position, the PLAYING state or a full buffer does not prove there is sound. New tests must check the decoded signal after seeks and across track transitions.
- Keep three things apart: contracts checked statically, tests with doubles, and successes against the real services. Playlist writes use Playlist4 over HTTP (`playlist/v2/.../changes`); call them working only after a run against a real account (`SpotifyPlaylistWriteTest`).
- Shuffle reorders the queue inside `SpotifyPlayer`. Media3's own shuffle mode stays off.
- YouTube Music tracks use `ytm://track/` addresses and Spotify tracks use `ytm://match/` addresses (resolved by `TrackMatcher`), both read by `YouTubeDataSource` (progressive, byte ranges). `MeloMediaSourceFactory` knows only this path, local files and, as the one addition, add-on streams (`addon://` rows, and YouTube-queued tracks when Direct Source Stream is on; plain files, DASH or HLS, read through `AddonMediaSource`). Add-on streams never get a DRM session: encrypted answers are refused, and a failing add-on stream falls back to the YouTube path at the same position.
- Everything in the repository is written in English. The app ships English (default) and French strings.

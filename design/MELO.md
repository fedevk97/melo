# Melo visual system

Melo is a native music player for deliberate listening. The UI should give music room and make the next playback action obvious. Nothing OS is a reference for restraint and dotted typography, not a source of copied assets.

## Tokens

| Role | Dark | Light |
| --- | --- | --- |
| Background | `#000000` | `#F7F6F2` |
| Surface | `#0D0D0F` | `#F7F6F2` |
| Raised surface | `#191919` | `#ECEBE7` |
| Main content | `#FAFAFA` | `#171717` |
| Secondary content | `#9A9AA0` | `#5C5C58` |
| Subtle divider | `#2C2C2E` | `#CBCBC7` |

Use semantic Material color roles rather than ad hoc screen colors. Meaningful control outlines use the stronger `outline` role. Primary and secondary text exceed WCAG AA normal-text contrast on their corresponding base surfaces. Error states use explicit messages as well as their surface treatment.

## App mark

The app mark is the M: two soft peaks, the second overlapping the first, in a mint-to-green gradient. `melo-mark.svg` is the vector master and `melo-logo.png` the 512 px export on Melo's black. The launcher icon comes in Mint, Ice Blue, Amethyst, Emerald and Monochrome; the splash screen raises the M in the colour of the icon it was opened from. The notification icon is the plain white M, and the lyrics share card and the app's headers use the same shape, tinted to fit. Album artwork stays in full colour.

## Typography

- Doto: the identity only. Bundled and licensed in `Doto-OFL.txt`.
- SF Pro Display: regular, medium, semibold, bold and heavy weights shared across app typography.
- Platform monospace: small editorial labels.
- Display: 44/50 sp; headline: 28/34; title: 22/28; body: 16/24; supporting text: 14/21.
- No stretched text, remote font dependency, or all-caps body copy.

## Layout and interaction

Use a 4/8 dp spacing rhythm with 16 dp page gutters and 28 dp between sections. Artwork has 8–10 dp corners; discovery cards have 12 dp corners. Primary actions can be circular. Android controls keep at least 48 dp targets.

Home combines Speed Dial, Mixes, artist pages, Top Picks, Recents, and mood discovery in Melo's neutral visual system. Speed Dial starts with pinned items, then reads from YouTube Music, Spotify, or listening history; its tile count, labels, release tags, and item order are configurable. Mixes and artists are separately browsable, with artist pins and order persisted on device. The main Home feed uses YouTube Music's personalized shelves, including Listen again and Quick Picks, plus release and Explore supplements. Shelves continue as the listener scrolls and the first page stays visible from the local cache while it refreshes. The Explore tab loads YouTube Music's mood and genre categories on demand; opening a category shows its playlist shelves. YouTube Music history and personalized mixes are read only when a Google account is connected. A compact, two-column Recents feed combines Melo's local history, Spotify's native recently-played contexts, and signed-in YouTube Music history. It respects the Recent shelf visibility and item count in Settings → Home and opens the full listening history. Configurable library shelves follow the discovery feed, and empty shelves stay hidden. The header always offers useful routes. Rendering options live under Appearance → Advanced. Motion follows Android’s Remove animations preference. Artist photos and song, album, and playlist covers retain their original colours. The interface around the artwork uses the neutral theme tokens.

Navigation has five stable tabs: Home, Explore, Search, Library, and Podcasts, inside a rounded dock. The active tab uses its filled icon, selected capsule, readable label when expanded, and selected semantics. Playback has fixed skip targets and a round play/pause button. Home artwork uses optional press feedback, respecting Android's Remove animations preference.

Light and system appearance remain available in settings. Optional album-derived player palettes are inherited advanced customization; the new-install default uses the global monochrome palette.

## Review boundaries

The logo and banner are identity assets, not app screenshots. Live playback must be verified on ARM64 with decoded PCM past the lead-in, after seeks, and across track changes before claiming an end-to-end playback test.

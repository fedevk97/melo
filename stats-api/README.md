# Melo live API

A Cloudflare Worker that counts how many Melo apps are open right now. It runs on the Workers Free plan: one SQLite-backed Durable Object holds the count, so there is no database to sign up for and no monthly command quota to run out of.

## What it counts

An app is counted while it is in the foreground or playing music, so listening with the screen off counts. While it is counted, the app sends `{"id", "platform", "open": true}` every five minutes (the server sets the interval in its reply); once it has left the foreground and the music has stopped, it sends `"open": false` and drops out of the count at once. An app that stops pinging without saying so, because it crashed or lost its connection, drops out 90 seconds after its next ping was due.

The count is split by platform: `android`, `pc` and `browser`. Melo ships on Android; the other two are accepted for clients to come.

Install IDs are random UUIDs made by the app. They are HMAC-hashed with `HASH_SECRET` before they are kept, and only the hash, the platform and the time of the last ping are stored. Nothing about the account, the music or the device is sent. A hash is deleted as soon as it stops counting.

## Endpoints

- `POST /api/presence` takes a ping and replies `{"intervalSec": 300}`. Errors: `invalid_json` (400), `invalid_id`, `invalid_platform` or `invalid_open` (422), `payload_too_large` (413), `origin_not_allowed` (403) and `rate_limited` (429). Releases before 1.3.1 send `playbackActive` in place of `open`; it is read the same way.
- `GET /api/stats/live` returns `{"online", "android", "pc", "browser", "updatedAt"}`, cached for 15 seconds. `GET /api/stats` is the same.
- `GET /api/stats/live/badge.svg` is the README badge. It is sent `no-cache`, so GitHub's image proxy shows the current number.

Counts are worked out every 15 seconds. Each address may send 20 pings a minute. Requests with an `Origin` header are refused unless they say `"platform": "browser"` and the origin is listed in `ALLOWED_ORIGINS`, so no web page can have its visitors post made-up installs.

## Settings

The `[vars]` in `wrangler.toml`:

- `PRESENCE_INTERVAL_SEC` (300): how often an open app pings. Apps follow the value in each reply, held between 60 and 3600.
- `PRESENCE_GRACE_SEC` (90): how long past a missed ping an app still counts.
- `PRESENCE_MAX_ENTRIES` (200000): the most installs counted at once.
- `PRESENCE_PINGS_PER_MINUTE` (20) and `PRESENCE_RATE_LIMIT_MAX_IPS` (100000): the rate limit.
- `ALLOWED_ORIGINS` (empty): comma-separated origins of a web player.

`HASH_SECRET` is a secret, not a var. Never commit it or put it in the app.

## Deploying

```sh
npm install
npx wrangler login
npx wrangler deploy
node -e "process.stdout.write(require('crypto').randomBytes(32).toString('hex'))" | npx wrangler secret put HASH_SECRET
```

`wrangler deploy` prints the Worker's `workers.dev` address. The app sends pings to `https://api.melo.aarushsagar.com/api/presence` by default; to use another address, build with `-PmeloStatsEndpoint=https://…/api/presence`.

It is deployed at `https://melo-live.melo-live.workers.dev`. `api.melo.aarushsagar.com` stays on Vercel, where this directory is also deployed: `vercel.json` there has no functions or build, only a rewrite that forwards every request to the Worker, so released apps keep their address. If the Worker moves, change the rewrite's destination.

The Worker reads the app's address from `X-Forwarded-For` when the request comes through Vercel, so the rate limit still applies to each app rather than to Vercel.

## Developing

- `npm test` runs the counting, rate-limit, parsing and badge tests.
- `npm run typecheck` checks the types.
- `npm run dev` serves the Worker at `http://127.0.0.1:8787` with local storage.

## Firebase Analytics

Detailed playback summaries (play starts, elapsed time while the player reports playing, buffering duration, and playback errors grouped by catalog source) go to Firebase Analytics, not to this API. Elapsed player time is an estimate; it cannot prove that audio was audible to a person. Search and Radio usage are not collected. The **Share usage analytics** setting controls Firebase collection; the live count runs either way, as it carries nothing about the listener.

To display summed durations in Google Analytics reports, register the event parameters `playing_ms` and `buffering_ms` as event-scoped custom metrics for `melo_player_playing_time` and `melo_buffering`. Firebase's Events report shows event counts by default; parameter reports need custom definitions and may take time to populate.

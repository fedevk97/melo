import { DurableObject } from "cloudflare:workers";
import { badge, type Counts, parsePing, type Platform, RateLimiter, Tracker } from "./tracker.ts";

export interface Env {
  LIVE: DurableObjectNamespace<LiveCounter>;
  /** How often an open app pings, in seconds. Sent back with every reply, so clients follow it. */
  PRESENCE_INTERVAL_SEC?: string;
  /** How long past a missed ping an install still counts. */
  PRESENCE_GRACE_SEC?: string;
  PRESENCE_MAX_ENTRIES?: string;
  PRESENCE_PINGS_PER_MINUTE?: string;
  PRESENCE_RATE_LIMIT_MAX_IPS?: string;
  /** Comma-separated origins a web player may post from. Empty: only the apps are accepted. */
  ALLOWED_ORIGINS?: string;
  /** Keys install IDs before they are stored. Set with `wrangler secret put HASH_SECRET`. */
  HASH_SECRET?: string;
}

const SWEEP_MS = 15_000;
const MAX_BODY_BYTES = 4096;

function number(value: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(value ?? "", 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function intervalSec(env: Env): number {
  return number(env.PRESENCE_INTERVAL_SEC, 300);
}

const PLATFORM_CODES: Record<Platform, number> = { android: 1, pc: 2, browser: 3 };
const CODE_PLATFORMS: Record<number, Platform> = { 1: "android", 2: "pc", 3: "browser" };

/**
 * The live count, in one object so every ping lands in the same table. Installs are held in
 * memory and mirrored to the object's own SQLite storage, so an evicted or restarted object
 * picks up where it left off instead of starting again from zero.
 */
export class LiveCounter extends DurableObject<Env> {
  private readonly tracker: Tracker;
  private readonly limiter: RateLimiter;
  private sweptAt = 0;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    const windowMs = (intervalSec(env) + number(env.PRESENCE_GRACE_SEC, 90)) * 1000;
    this.tracker = new Tracker(windowMs, number(env.PRESENCE_MAX_ENTRIES, 200_000));
    this.limiter = new RateLimiter(60_000, number(env.PRESENCE_PINGS_PER_MINUTE, 20), number(env.PRESENCE_RATE_LIMIT_MAX_IPS, 100_000));
    ctx.blockConcurrencyWhile(async () => {
      const sql = this.ctx.storage.sql;
      sql.exec("CREATE TABLE IF NOT EXISTS installs (id TEXT PRIMARY KEY, platform INTEGER NOT NULL, last INTEGER NOT NULL)");
      for (const row of sql.exec<{ id: string; platform: number; last: number }>("SELECT id, platform, last FROM installs")) {
        const platform = CODE_PLATFORMS[row.platform];
        if (platform) this.tracker.ping(row.id, platform, row.last);
      }
      this.sweep(Date.now(), true);
    });
  }

  /** Records one ping. Returns false when the address has sent too many this minute. */
  async ping(key: string, platform: Platform, open: boolean, ip: string): Promise<boolean> {
    const now = Date.now();
    if (!this.limiter.allow(ip, now)) return false;
    const sql = this.ctx.storage.sql;
    if (open) {
      if (this.tracker.ping(key, platform, now)) {
        sql.exec(
          "INSERT INTO installs (id, platform, last) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET platform = excluded.platform, last = excluded.last",
          key, PLATFORM_CODES[platform], now,
        );
      }
    } else if (this.tracker.close(key)) {
      sql.exec("DELETE FROM installs WHERE id = ?", key);
      // A close is meant to show at once: recount now rather than at the next pass.
      this.sweep(now, true);
    }
    this.sweep(now, false);
    return true;
  }

  async counts(): Promise<Counts> {
    this.sweep(Date.now(), false);
    return this.tracker.current();
  }

  private sweep(now: number, force: boolean) {
    if (!force && now - this.sweptAt < SWEEP_MS) return;
    this.sweptAt = now;
    const dropped = this.tracker.sweep(now);
    for (const key of dropped) this.ctx.storage.sql.exec("DELETE FROM installs WHERE id = ?", key);
  }
}

function json(data: unknown, status = 200, cache = "no-store"): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": cache,
      "Access-Control-Allow-Origin": "*",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function error(status: number, code: string, message: string): Response {
  return json({ error: code, message }, status);
}

/** The caller's address. Behind the Vercel forward, the first hop it records is the app's. */
function clientIp(request: Request): string {
  if (request.headers.has("x-vercel-id")) {
    const forwarded = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
    if (forwarded) return forwarded;
  }
  return request.headers.get("cf-connecting-ip") ?? "unknown";
}

async function hashId(id: string, env: Env): Promise<string> {
  const data = new TextEncoder().encode(id);
  const digest = env.HASH_SECRET
    ? await crypto.subtle.sign("HMAC", await crypto.subtle.importKey("raw", new TextEncoder().encode(env.HASH_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]), data)
    : await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function counter(env: Env): DurableObjectStub<LiveCounter> {
  return env.LIVE.get(env.LIVE.idFromName("live"));
}

async function handlePresence(request: Request, env: Env): Promise<Response> {
  const raw = await request.text();
  if (raw.length > MAX_BODY_BYTES) return error(413, "payload_too_large", "The body is too large.");
  let value: unknown;
  try { value = JSON.parse(raw); } catch { return error(400, "invalid_json", "The body is not JSON."); }
  const ping = parsePing(value);
  if ("error" in ping) {
    const messages: Record<string, string> = {
      invalid_payload: "The body must be an object.",
      invalid_id: "id must be a UUID.",
      invalid_platform: "platform must be android, pc or browser.",
      invalid_open: "open must be true or false.",
    };
    return error(422, ping.error, messages[ping.error] ?? "Invalid ping.");
  }
  // The apps send no Origin; only a web player does, and only from a named origin. Refusing every
  // other browser stops any page from having its visitors post made-up installs.
  const origin = request.headers.get("origin");
  const allowed = (env.ALLOWED_ORIGINS ?? "").split(",").map((entry) => entry.trim()).filter(Boolean);
  if ((ping.platform === "browser") !== (origin !== null) || (origin !== null && !allowed.includes(origin))) {
    return error(403, "origin_not_allowed", "Presence is only accepted from the apps and the web player.");
  }
  const accepted = await counter(env).ping(await hashId(ping.id, env), ping.platform, ping.open, clientIp(request));
  if (!accepted) return error(429, "rate_limited", "Too many presence pings from this address.");
  return json({ intervalSec: intervalSec(env) });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    // HEAD is answered as GET; the runtime drops the body. Link checkers and image proxies send it.
    const method = request.method === "HEAD" ? "GET" : request.method;
    const route = `${method} ${pathname.replace(/\/+$/, "") || "/"}`;
    switch (route) {
      case "GET /":
        return json({ name: "Melo live", endpoints: ["POST /api/presence", "GET /api/stats/live", "GET /api/stats/live/badge.svg"] });
      case "GET /healthz":
        return json({ ok: true });
      case "POST /api/presence":
        return handlePresence(request, env);
      // Public on purpose: a README badge or another site may read how many apps are open.
      case "GET /api/stats":
      case "GET /api/stats/live":
        return json(await counter(env).counts(), 200, "public, max-age=15");
      case "GET /api/stats/live/badge.svg":
        // no-cache is what GitHub's image proxy honours, so every README view shows the current number.
        return new Response(badge((await counter(env).counts()).online), {
          headers: {
            "Content-Type": "image/svg+xml; charset=utf-8",
            "Cache-Control": "no-cache, no-store, must-revalidate, max-age=0",
            "Access-Control-Allow-Origin": "*",
            "X-Content-Type-Options": "nosniff",
          },
        });
      case "OPTIONS /api/presence":
        return new Response(null, {
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": request.headers.get("origin") ?? "*",
            "Access-Control-Allow-Methods": "POST",
            "Access-Control-Allow-Headers": "Content-Type",
          },
        });
      default:
        return error(404, "not_found", "No such endpoint.");
    }
  },
} satisfies ExportedHandler<Env>;

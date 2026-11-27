/** Where an open app runs. Melo ships on Android; the others are accepted for clients to come. */
export type Platform = "android" | "pc" | "browser";

export const PLATFORMS: readonly Platform[] = ["android", "pc", "browser"];

export type Counts = {
  online: number;
  android: number;
  pc: number;
  browser: number;
  updatedAt: number;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isInstallId(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

export function parsePlatform(value: unknown): Platform | null {
  return PLATFORMS.includes(value as Platform) ? (value as Platform) : null;
}

export type Ping = { id: string; platform: Platform; open: boolean };

/**
 * Reads `{"id", "platform", "open"}`. Releases before 1.3.1 send `playbackActive` instead of
 * `open`, only while music plays; it is read the same way so they keep counting.
 */
export function parsePing(value: unknown): Ping | { error: string } {
  if (!value || typeof value !== "object" || Array.isArray(value)) return { error: "invalid_payload" };
  const body = value as Record<string, unknown>;
  if (!isInstallId(body.id)) return { error: "invalid_id" };
  const platform = parsePlatform(body.platform);
  if (!platform) return { error: "invalid_platform" };
  const open = typeof body.open === "boolean" ? body.open
    : typeof body.playbackActive === "boolean" ? body.playbackActive : null;
  if (open === null) return { error: "invalid_open" };
  return { id: body.id.toLowerCase(), platform, open };
}

type Entry = { lastMs: number; platform: Platform };

/**
 * The installs that pinged within the window. Counts are worked out by [sweep], at most every
 * few seconds, so reading them never walks the whole table.
 */
export class Tracker {
  private readonly seen = new Map<string, Entry>();
  private counts: Counts = { online: 0, android: 0, pc: 0, browser: 0, updatedAt: 0 };

  private readonly windowMs: number;
  private readonly maxEntries: number;

  constructor(windowMs: number, maxEntries: number) {
    this.windowMs = windowMs;
    this.maxEntries = maxEntries;
  }

  get size(): number {
    return this.seen.size;
  }

  has(key: string): boolean {
    return this.seen.has(key);
  }

  /** False when the table is full and [key] is new: it is not counted. */
  ping(key: string, platform: Platform, nowMs: number): boolean {
    if (!this.seen.has(key) && this.maxEntries > 0 && this.seen.size >= this.maxEntries) return false;
    this.seen.set(key, { lastMs: nowMs, platform });
    return true;
  }

  close(key: string): boolean {
    return this.seen.delete(key);
  }

  /** Drops the installs that went quiet and recounts. Returns the keys it dropped. */
  sweep(nowMs: number): string[] {
    const cutoff = nowMs - this.windowMs;
    const dropped: string[] = [];
    const next: Counts = { online: 0, android: 0, pc: 0, browser: 0, updatedAt: nowMs };
    for (const [key, entry] of this.seen) {
      if (entry.lastMs < cutoff) {
        this.seen.delete(key);
        dropped.push(key);
        continue;
      }
      next[entry.platform]++;
    }
    next.online = next.android + next.pc + next.browser;
    this.counts = next;
    return dropped;
  }

  current(): Counts {
    return { ...this.counts };
  }
}

/** A fixed window per address: [limit] pings a minute, over at most [maxKeys] addresses. */
export class RateLimiter {
  private readonly entries = new Map<string, { started: number; count: number }>();
  private swept = 0;

  private readonly windowMs: number;
  private readonly limit: number;
  private readonly maxKeys: number;

  constructor(windowMs: number, limit: number, maxKeys: number) {
    this.windowMs = windowMs;
    this.limit = limit;
    this.maxKeys = maxKeys;
  }

  allow(ip: string, nowMs: number): boolean {
    if (this.limit <= 0) return true;
    // Expired entries also reset on lookup, so a full pass is needed about once a second, or
    // when the table is full and needs the room.
    if (nowMs - this.swept >= 1000 || (this.maxKeys > 0 && this.entries.size >= this.maxKeys)) {
      for (const [key, entry] of this.entries) if (nowMs - entry.started >= this.windowMs) this.entries.delete(key);
      this.swept = nowMs;
    }
    const entry = this.entries.get(ip);
    if (!entry || nowMs - entry.started >= this.windowMs) {
      if (!entry && this.maxKeys > 0 && this.entries.size >= this.maxKeys) return false;
      this.entries.set(ip, { started: nowMs, count: 1 });
      return true;
    }
    if (entry.count >= this.limit) return false;
    entry.count++;
    return true;
  }
}

/**
 * The README badge, drawn here rather than through shields.io: its cache, stacked on GitHub's
 * image proxy, would hold the number back by minutes. The shape follows shields' for-the-badge
 * style: a fixed label, bold Verdana digits at 8.25 px each and 12 px of padding a side.
 */
export function badge(value: number): string {
  const text = String(value);
  const label = 124.25;
  const valueWidth = 8.25 * text.length + 24;
  const width = label + valueWidth;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="28" role="img" aria-label="LISTENING NOW: ${text}">`
    + `<title>LISTENING NOW: ${text}</title>`
    + `<g shape-rendering="crispEdges"><rect width="${label}" height="28" fill="#0b0b0c"/>`
    + `<rect x="${label}" width="${valueWidth}" height="28" fill="#8fe3bc"/></g>`
    + `<g text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="100">`
    + `<text fill="#fff" transform="scale(.1)" x="621.25" y="175" textLength="1002.5">LISTENING NOW</text>`
    + `<text fill="#0b0b0c" transform="scale(.1)" x="${(label + valueWidth / 2) * 10}" y="175" textLength="${82.5 * text.length}" font-weight="bold">${text}</text>`
    + `</g></svg>`;
}

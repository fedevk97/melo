import assert from "node:assert/strict";
import { test } from "node:test";
import { badge, parsePing, RateLimiter, Tracker } from "./tracker.ts";

const ID = "3f2c1d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

test("counts installs per platform and drops the quiet ones", () => {
  const tracker = new Tracker(390_000, 0);
  tracker.ping("a", "android", 0);
  tracker.ping("b", "android", 100_000);
  tracker.ping("c", "pc", 100_000);
  assert.deepEqual(tracker.sweep(200_000), []);
  assert.deepEqual(tracker.current(), { online: 3, android: 2, pc: 1, browser: 0, updatedAt: 200_000 });
  // "a" last pinged at 0: past the window at 400 s, the others are not.
  assert.deepEqual(tracker.sweep(400_000), ["a"]);
  assert.equal(tracker.current().online, 2);
});

test("a ping keeps an install counted and a close removes it", () => {
  const tracker = new Tracker(390_000, 0);
  tracker.ping("a", "android", 0);
  tracker.ping("a", "android", 300_000);
  tracker.sweep(600_000);
  assert.equal(tracker.current().online, 1);
  assert.equal(tracker.close("a"), true);
  assert.equal(tracker.close("a"), false);
  tracker.sweep(600_001);
  assert.equal(tracker.current().online, 0);
});

test("a full table refuses new installs but still refreshes known ones", () => {
  const tracker = new Tracker(390_000, 2);
  assert.equal(tracker.ping("a", "android", 0), true);
  assert.equal(tracker.ping("b", "android", 0), true);
  assert.equal(tracker.ping("c", "android", 0), false);
  assert.equal(tracker.ping("a", "android", 10), true);
  assert.equal(tracker.size, 2);
});

test("rate limiter allows a set number of pings per address each minute", () => {
  const limiter = new RateLimiter(60_000, 3, 0);
  assert.equal(limiter.allow("1.1.1.1", 0), true);
  assert.equal(limiter.allow("1.1.1.1", 1), true);
  assert.equal(limiter.allow("1.1.1.1", 2), true);
  assert.equal(limiter.allow("1.1.1.1", 3), false);
  assert.equal(limiter.allow("2.2.2.2", 3), true);
  assert.equal(limiter.allow("1.1.1.1", 60_000), true);
});

test("rate limiter refuses new addresses when its table is full", () => {
  const limiter = new RateLimiter(60_000, 5, 1);
  assert.equal(limiter.allow("1.1.1.1", 0), true);
  assert.equal(limiter.allow("2.2.2.2", 10), false);
  assert.equal(limiter.allow("2.2.2.2", 60_000), true);
});

test("parses pings, including the older playbackActive form", () => {
  assert.deepEqual(parsePing({ id: ID, platform: "android", open: true }), { id: ID, platform: "android", open: true });
  assert.deepEqual(parsePing({ id: ID.toUpperCase(), platform: "android", playbackActive: false }), { id: ID, platform: "android", open: false });
  assert.deepEqual(parsePing({ id: "nope", platform: "android", open: true }), { error: "invalid_id" });
  assert.deepEqual(parsePing({ id: ID, platform: "ios", open: true }), { error: "invalid_platform" });
  assert.deepEqual(parsePing({ id: ID, platform: "android" }), { error: "invalid_open" });
  assert.deepEqual(parsePing([]), { error: "invalid_payload" });
});

test("badge shows the number and grows with it", () => {
  const one = badge(7);
  const many = badge(1234);
  assert.match(one, /LISTENING NOW: 7/);
  assert.match(many, />1234</);
  const width = (svg: string) => Number(/width="([\d.]+)"/.exec(svg)![1]);
  assert.equal(width(one), 124.25 + 8.25 + 24);
  assert.ok(width(many) > width(one));
});

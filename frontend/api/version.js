/**
 * Vercel serverless function - returns the current plugin version.
 *
 * Resolution order (same as status.js / check-update.js):
 *   1. Local plugin.yml  (present when the repo is deployed)
 *   2. Modrinth API      (fallback when running on Vercel without local files)
 *   3. LATEST_VERSION / PLUGIN_VERSION env var
 *
 * Unlike /api/status and /api/check-update, this endpoint is browser-facing:
 * it never redirects and always returns JSON so the frontend pages can call it
 * directly to display a live version badge without any manual updates.
 *
 * Response shape:
 *   { version: "1.6.2", source: "plugin.yml" | "modrinth" | "env", checkedAt: "…" }
 */

const fs   = require("fs");
const path = require("path");
const yaml = require("js-yaml");

// ── Constants ────────────────────────────────────────────────────────────────
const MODRINTH_API  = "https://api.modrinth.com/v2/project/fake-player-plugin-(fpp)/version?limit=1";
const MODRINTH_PAGE = "https://modrinth.com/plugin/fake-player-plugin-(fpp)";
const CACHE_TTL_MS  = 5 * 60 * 1000;   // 5-minute in-process cache

// ── In-process cache ─────────────────────────────────────────────────────────
let _cache   = null;
let _cacheAt = 0;

// ── Read local plugin.yml ────────────────────────────────────────────────────
function readLocalVersion() {
  try {
    const p = path.join(process.cwd(), "src", "main", "resources", "plugin.yml");
    if (fs.existsSync(p)) {
      const obj = yaml.load(fs.readFileSync(p, "utf8"));
      if (obj && obj.version) return { version: String(obj.version), source: "plugin.yml" };
    }
  } catch (_) {}
  return null;
}

// ── Fetch from Modrinth ──────────────────────────────────────────────────────
async function fetchFromModrinth() {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);
  try {
    const res = await fetch(MODRINTH_API, {
      signal: controller.signal,
      headers: {
        "Accept":     "application/json",
        "User-Agent": "FakePlayerPlugin-VersionEndpoint (https://fake-player-plugin.vercel.app)",
      },
    });
    clearTimeout(timer);
    if (!res.ok) return null;
    const arr = await res.json();
    if (!Array.isArray(arr) || arr.length === 0) return null;
    const version = arr[0].version_number || null;
    if (!version) return null;
    return { version, source: "modrinth" };
  } catch (_) {
    clearTimeout(timer);
    return null;
  }
}

// ── Handler ───────────────────────────────────────────────────────────────────
module.exports = async (req, res) => {
  res.setHeader("Content-Type", "application/json");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Cache-Control", "public, max-age=300, stale-while-revalidate=60");

  if (req.method === "OPTIONS") return res.status(200).end();

  // Serve from cache if still fresh
  const now = Date.now();
  if (_cache && (now - _cacheAt) < CACHE_TTL_MS) {
    return res.status(200).json({ ..._cache, cached: true, checkedAt: new Date(_cacheAt).toISOString() });
  }

  // 1. Local plugin.yml
  const local = readLocalVersion();
  if (local) {
    _cache   = local;
    _cacheAt = now;
    return res.status(200).json({ ...local, checkedAt: new Date().toISOString() });
  }

  // 2. Modrinth
  const modrinth = await fetchFromModrinth();
  if (modrinth) {
    _cache   = modrinth;
    _cacheAt = now;
    return res.status(200).json({ ...modrinth, checkedAt: new Date().toISOString() });
  }

  // 3. Env-var fallback
  const envVersion = process.env.LATEST_VERSION || process.env.PLUGIN_VERSION || null;
  if (envVersion) {
    const result = { version: envVersion, source: "env" };
    _cache   = result;
    _cacheAt = now;
    return res.status(200).json({ ...result, checkedAt: new Date().toISOString() });
  }

  // Nothing available
  return res.status(503).json({
    version: null,
    source: null,
    error: "Version unavailable",
    checkedAt: new Date().toISOString(),
  });
};


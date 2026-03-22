/**
 * Vercel serverless function — mirrors the Java UpdateChecker logic exactly.
 *
 * Priority (same as UpdateChecker.java):
 *   1. Modrinth API  — https://api.modrinth.com/v2/project/fake-player-plugin-(fpp)/version?limit=1
 *   2. LATEST_VERSION / PLUGIN_VERSION env var (set on Vercel dashboard when a release ships)
 *
 * Response keys are intentionally compatible with the Java extractVersion() parser so the
 * plugin's Vercel fallback path works: "remoteVersion", "version", "version_number", "downloadUrl".
 */

const fs   = require("fs");
const path = require("path");
const yaml = require("js-yaml");

// ── Constants (mirrors UpdateChecker.java) ────────────────────────────────────
const MODRINTH_API  = "https://api.modrinth.com/v2/project/fake-player-plugin-(fpp)/version?limit=1";
const MODRINTH_PAGE = "https://modrinth.com/plugin/fake-player-plugin-(fpp)";
const CACHE_TTL_MS  = 5 * 60 * 1000;

// ── In-process cache ──────────────────────────────────────────────────────────
let _modrinthCache   = null;
let _modrinthCacheAt = 0;

// ── Semantic version comparison (mirrors compareVersions() in Java) ────────────
function compareVersions(a, b) {
  const stripV = v => (v && v.startsWith("v") ? v.slice(1) : v || "");
  const parts  = v => stripV(v).split(".").map(p => parseInt(p, 10) || 0);
  const pa = parts(a), pb = parts(b);
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const diff = (pa[i] || 0) - (pb[i] || 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

// ── Local version resolution (plugin.yml → env var) ───────────────────────────
function readLocalVersion() {
  try {
    const p = path.join(process.cwd(), "src", "main", "resources", "plugin.yml");
    if (fs.existsSync(p)) {
      const obj = yaml.load(fs.readFileSync(p, "utf8"));
      if (obj && obj.version) return String(obj.version);
    }
  } catch (_) {}
  return process.env.LATEST_VERSION || process.env.PLUGIN_VERSION || null;
}

// ── Modrinth fetch (primary, matches Java UpdateChecker) ─────────────────────
async function fetchFromModrinth(localVersion) {
  const now = Date.now();
  if (_modrinthCache && (now - _modrinthCacheAt) < CACHE_TTL_MS) return _modrinthCache;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);
  try {
    const res = await fetch(MODRINTH_API, {
      signal: controller.signal,
      headers: {
        "Accept":     "application/json",
        "User-Agent": `FakePlayerPlugin-UpdateChecker/${localVersion || "0.0.0"} (${MODRINTH_PAGE})`,
      },
    });
    clearTimeout(timer);
    if (!res.ok) return { error: `HTTP ${res.status} from Modrinth` };
    const arr = await res.json();
    if (!Array.isArray(arr) || arr.length === 0) return { error: "empty version array from Modrinth" };
    const first = arr[0];
    const latestVersion = first.version_number || null;
    if (!latestVersion) return { error: "no version_number in Modrinth response" };
    const downloadUrl = (first.files && first.files[0] && first.files[0].url) || MODRINTH_PAGE;
    const result = { latestVersion, downloadUrl, source: "modrinth" };
    _modrinthCache   = result;
    _modrinthCacheAt = Date.now();
    return result;
  } catch (e) {
    clearTimeout(timer);
    return { error: String(e) };
  }
}

// ── Handler ───────────────────────────────────────────────────────────────────
module.exports = async (req, res) => {
  res.setHeader("Content-Type", "application/json");

  const localVersion = readLocalVersion();
  const modrinth = await fetchFromModrinth(localVersion);

  if (modrinth.error) {
    return res.status(502).json({
      error:           modrinth.error,
      localVersion,
      latestVersion:   null,
      remoteVersion:   null,   // Java extractVersion() compat
      version:         null,   // Java extractVersion() compat
      updateAvailable: null,
      checkedAt:       new Date().toISOString(),
    });
  }

  const { latestVersion, downloadUrl, source } = modrinth;
  const updateAvailable = (localVersion && latestVersion)
    ? compareVersions(latestVersion, localVersion) > 0
    : null;

  res.status(200).json({
    localVersion,
    latestVersion,
    remoteVersion:   latestVersion,   // Java extractVersion() compat key
    version:         latestVersion,   // Java extractVersion() compat key
    version_number:  latestVersion,   // Modrinth-style compat key
    updateAvailable,
    downloadUrl,
    source,
    checkedAt: new Date().toISOString(),
  });
};

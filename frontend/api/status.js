/**
 * Vercel serverless function — returns plugin status / version info.
 *
 * Resolution order:
 *   1. Local plugin.yml  — present when the repo is deployed as-is
 *   2. Modrinth API      — used when running on Vercel without local files
 *   3. LATEST_VERSION / PLUGIN_VERSION env var — manual override for pinned releases
 *
 * The response shape is intentionally minimal and compatible with the Java
 * extractVersion() / extractDownloadUrl() parsers used in UpdateChecker.java.
 */

const fs   = require("fs");
const path = require("path");
const yaml = require("js-yaml");

// ── Constants ─────────────────────────────────────────────────────────────────
const MODRINTH_API  = "https://api.modrinth.com/v2/project/fake-player-plugin-(fpp)/version?limit=1";
const MODRINTH_PAGE = "https://modrinth.com/plugin/fake-player-plugin-(fpp)";

// ── Helpers ───────────────────────────────────────────────────────────────────
function safeReadYaml(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null;
    return yaml.load(fs.readFileSync(filePath, "utf8"));
  } catch (e) {
    return null;
  }
}

function toSafeString(v) {
  if (v == null) return null;
  if (typeof v === "string" || typeof v === "number" || typeof v === "boolean") return String(v);
  return null;
}

function mapToMinimal(obj, overrideVersion) {
  const fallbackUrl = process.env.PLUGIN_DOWNLOAD_URL || MODRINTH_PAGE;
  if (!obj || typeof obj !== "object") {
    return { name: null, version: overrideVersion || null, downloadUrl: fallbackUrl, notes: null };
  }
  return {
    name:        toSafeString(obj.name || obj.displayName || obj.title) || null,
    version:     overrideVersion || toSafeString(obj.version || obj.tag_name || obj.version_number) || null,
    downloadUrl: toSafeString(obj.downloadUrl || obj.download_url || obj.website) || fallbackUrl,
    notes:       toSafeString(obj.description || obj.notes || obj.body || obj.summary) || null,
  };
}

// ── Modrinth fetch (mirrors Java UpdateChecker primary path) ──────────────────
async function fetchFromModrinth() {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);
  try {
    const r = await fetch(MODRINTH_API, {
      signal: controller.signal,
      headers: {
        "Accept":     "application/json",
        "User-Agent": "FakePlayerPlugin-StatusEndpoint (https://modrinth.com/plugin/fake-player-plugin-(fpp))",
      },
    });
    clearTimeout(timer);
    if (!r.ok) return null;
    const arr = await r.json();
    if (!Array.isArray(arr) || arr.length === 0) return null;
    const first = arr[0];
    const version = first.version_number || null;
    const downloadUrl = (first.files && first.files[0] && first.files[0].url) || MODRINTH_PAGE;
    return { version, downloadUrl, name: "Fake Player Plugin (FPP)", notes: null };
  } catch (e) {
    clearTimeout(timer);
    return null;
  }
}

// ── Handler ───────────────────────────────────────────────────────────────────
module.exports = async (req, res) => {
  res.setHeader("Content-Type", "application/json");

  // Pinned version always wins (set LATEST_VERSION on Vercel when publishing a release)
  const pinnedVersion = process.env.LATEST_VERSION || process.env.PLUGIN_VERSION || null;

  // 1. Local plugin.yml (present in dev or repo-deployed Vercel)
  const pluginYmlPath = path.join(process.cwd(), "src", "main", "resources", "plugin.yml");
  if (fs.existsSync(pluginYmlPath)) {
    const raw = safeReadYaml(pluginYmlPath) || {};
    const src = (raw.plugin && typeof raw.plugin === "object") ? raw.plugin : raw;
    return res.status(200).json(mapToMinimal(src, pinnedVersion || toSafeString(src.version)));
  }

  // 2. Modrinth API (Vercel deployment without local files)
  const modrinth = await fetchFromModrinth();
  if (modrinth) {
    return res.status(200).json(mapToMinimal(modrinth, pinnedVersion || modrinth.version));
  }

  // 3. Env-var only fallback
  if (pinnedVersion) {
    return res.status(200).json({
      name: "Fake Player Plugin (FPP)", version: pinnedVersion,
      downloadUrl: MODRINTH_PAGE, notes: null,
    });
  }

  return res.status(200).json({
    name: null, version: null,
    downloadUrl: MODRINTH_PAGE, notes: null,
    error: "No plugin info available. Set LATEST_VERSION in Vercel environment variables.",
  });
};

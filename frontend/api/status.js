const fs = require("fs");
const path = require("path");
const yaml = require("js-yaml");

// Vercel Serverless function: returns plugin status JSON.
// Behavior: try reading local plugin resource files (if the repo is deployed as-is).
// If not found (typical on Vercel), and PLUGIN_API_URL env var is set, proxy to that remote API.

async function fetchCandidate(url) {
  try {
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), 5000);
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(id);
    if (!res.ok) return { error: `HTTP ${res.status}` };
    const contentType = res.headers.get("content-type") || "";
    if (contentType.includes("application/json")) return await res.json();
    // try to parse text as JSON
    const text = await res.text();
    try {
      return JSON.parse(text);
    } catch (e) {
      return { error: "invalid-json" };
    }
  } catch (e) {
    return { error: String(e) };
  }
}

function safeReadYaml(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null;
    const content = fs.readFileSync(filePath, "utf8");
    return yaml.load(content);
  } catch (e) {
    return { error: String(e) };
  }
}
module.exports = async (req, res) => {
  res.setHeader("Content-Type", "application/json");
  const projectRoot = path.resolve(process.cwd());

  const pluginYml = path.join(
    projectRoot,
    "src",
    "main",
    "resources",
    "plugin.yml"
  );

  // Map a plugin object (from YAML or remote JSON) to a minimal public shape
  // Convert a value to a safe string or null; arrays/objects become null to avoid leaking large content.
  function toSafeString(v) {
    if (v === undefined || v === null) return null;
    if (typeof v === "string") return v;
    if (typeof v === "number" || typeof v === "boolean") return String(v);
    // Avoid returning arrays or objects
    return null;
  }

  function mapToMinimal(obj) {
    const fallback = {
      name: null,
      version: null,
      downloadUrl: process.env.PLUGIN_DOWNLOAD_URL || "https://api.fpp.com/",
      notes: null,
    };
    if (!obj || typeof obj !== "object") return fallback;
    const name = toSafeString(obj.name || obj.displayName || obj.title) || null;
    const version =
      toSafeString(
        obj.version || obj.tag_name || obj.latest || obj.version_number
      ) || null;
    const downloadUrl =
      toSafeString(obj.downloadUrl || obj.download_url || obj.website) ||
      process.env.PLUGIN_DOWNLOAD_URL ||
      "https://api.fpp.com/";
    const notes =
      toSafeString(obj.description || obj.notes || obj.body || obj.summary) ||
      null;
    return { name, version, downloadUrl, notes };
  }

  // Some remote APIs return wrapper objects. Try to find the nested object that contains version/name
  function findBestObject(result) {
    if (!result || typeof result !== "object") return null;
    // If result already looks like a plugin info object, use it
    if (result.version || result.tag_name || result.name || result.latest)
      return result;
    // Common wrapper keys
    const keys = [
      "plugin",
      "data",
      "release",
      "manifest",
      "result",
      "package",
      "body",
    ];
    for (const k of keys) {
      if (result[k] && typeof result[k] === "object") {
        const candidate = result[k];
        if (
          candidate.version ||
          candidate.tag_name ||
          candidate.name ||
          candidate.latest
        )
          return candidate;
      }
    }
    // Search for any nested object with a version/tag_name/name field
    for (const k of Object.keys(result)) {
      const v = result[k];
      if (v && typeof v === "object") {
        if (v.version || v.tag_name || v.name || v.latest) return v;
      }
    }
    // No obvious candidate — return null to let mapToMinimal handle fallback
    return null;
  }

  // If local plugin.yml exists, return concise info (only minimal fields)
  if (fs.existsSync(pluginYml)) {
    const raw = safeReadYaml(pluginYml) || {};
    // Some YAML layouts embed the real plugin info under a wrapper key — find it.
    const bestLocal =
      findBestObject(raw) ||
      (raw.plugin && typeof raw.plugin === "object" ? raw.plugin : raw);
    return res.status(200).json(mapToMinimal(bestLocal));
  }

  // No local files — proxy to configured PLUGIN_API_URL
  const pluginApi =
    process.env.PLUGIN_API_URL || process.env.NEXT_PUBLIC_PLUGIN_API_URL;
  if (!pluginApi) {
    return res.status(200).json({
      name: null,
      version: null,
      downloadUrl: null,
      notes: null,
      error: "No plugin info available. Set PLUGIN_API_URL in environment.",
    });
  }

  const candidates = [
    pluginApi,
    pluginApi.endsWith("/")
      ? pluginApi + "api/status"
      : pluginApi + "/api/status",
    pluginApi.endsWith("/")
      ? pluginApi + "api/check-update"
      : pluginApi + "/api/check-update",
    pluginApi.endsWith("/") ? pluginApi + "status" : pluginApi + "/status",
    pluginApi.endsWith("/") ? pluginApi + "latest" : pluginApi + "/latest",
  ];

  for (const c of candidates) {
    const result = await fetchCandidate(c);
    if (result && !result.error) {
      // Normalize possible wrapper objects
      const best =
        findBestObject(result) ||
        (typeof result === "object"
          ? result
          : { version: String(result).trim() });
      return res.status(200).json(mapToMinimal(best));
    }
  }

  return res.status(502).json({
    name: null,
    version: null,
    downloadUrl: null,
    notes: null,
    error: "Could not retrieve plugin info from PLUGIN_API_URL",
  });
};

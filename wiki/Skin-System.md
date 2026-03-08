# Skin System

FPP supports three skin modes, configurable in `config.yml`:

```yaml
fake-player:
  skin:
    mode: auto          # auto | fetch | disabled
    clear-cache-on-reload: true
```

---

## Modes

### `auto` *(Recommended)*

FPP calls `Mannequin.setProfile(botName)` on the Mannequin entity.  
Paper and the Minecraft client then resolve the correct Mojang skin automatically — the same way a real player's skin loads.

**Pros:**
- Zero HTTP requests from the plugin
- Instantly correct skins — no delays
- No caching needed

**Requirements:**
- Online-mode server (`online-mode=true` in `server.properties`)
- The bot name must match an existing Mojang account for a skin to appear
  - If the bot name doesn't match any account, the default Steve/Alex skin is used

**How it works:**  
When a player's client receives the tab-list entry for the bot, it requests the skin from Mojang's session servers just like any other player. The Mannequin's profile is pre-populated with the bot's name, so the client knows which skin to fetch.

---

### `fetch`

FPP fetches the skin texture value and signature from the **Mojang API** in the background (async) and injects it directly into the Mannequin's `GameProfile`.

**Pros:**
- Works on offline-mode servers
- Skin is embedded in the packet — no client-side lookup needed

**Cons:**
- One HTTP request per unique bot name (Mojang API: `api.mojang.com`)
- Rate-limited by Mojang (600 requests per 10 minutes per IP)
- Adds a short delay (typically < 1 second) before the skin appears

**Cache:**  
Fetched skin data is cached in memory for the session. Running `/fpp reload` clears the cache if `clear-cache-on-reload: true`.

---

### `disabled`

No skin is applied. All bots display the default Steve or Alex skin (determined by UUID, as per vanilla Minecraft).

Use this mode if:
- You don't care about skins
- You're running an offline-mode server and don't want Mojang API calls
- You're hitting Mojang rate limits with `fetch` mode

---

## Skin Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Bot has Steve/Alex skin | Bot name doesn't match a Mojang account | Use real player names, or switch to `fetch` mode |
| Skin takes a few seconds to appear | Normal in `fetch` mode | Expected — HTTP request takes time |
| All bots have the same skin | `fetch` mode rate-limited or cache issue | Use `auto` mode, or run `/fpp reload` to clear cache |
| No skin despite `mode: auto` | Offline-mode server | Switch to `fetch` or `disabled` mode |
| `attachSkin failed` in console | Reflection API changed in new Paper version | Switch to `auto` mode — it uses the native Paper API |

---

## Online vs Offline Mode

| Server Mode | Recommended Skin Mode |
|------------|----------------------|
| Online mode (`online-mode=true`) | `auto` |
| Offline mode (`online-mode=false`) | `fetch` or `disabled` |

---

## Config Reference

```yaml
fake-player:
  skin:
    # Skin resolution mode: auto | fetch | disabled
    mode: auto

    # (fetch mode only) Clear the in-memory skin cache on /fpp reload
    clear-cache-on-reload: true
```


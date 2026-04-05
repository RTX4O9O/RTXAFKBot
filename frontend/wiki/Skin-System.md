# Skin System

FPP ships a fully reworked skin pipeline with three modes and multiple
skin sources — from automatic Mojang resolution to custom PNG files on disk.

---

## Quick Setup

```yaml
# config.yml
skin:
  mode: auto          # auto | custom | off
  clear-cache-on-reload: true
```

Run `/fpp reload` to apply changes without restarting.

---

## Modes

### `auto` *(Recommended — online-mode servers)*

FPP resolves the skin from the Mojang API using the bot's name and injects it directly
into the bot's game profile — exactly like a real player joining.

| Feature | Detail |
|---------|--------|
| Skin accuracy | Matches the Minecraft account of that name |
| Works offline | ❌ Requires `online-mode=true` for best results |
| Delay | Async fetch — no tick blocking |

> **Tip:** If a bot name doesn't match any Mojang account the client falls back
> to the default Steve / Alex skin. This is expected behaviour.

#### Guaranteed Skin (auto mode)

When `skin.guaranteed-skin: true`, the plugin will attempt to fetch a skin even for bots whose name doesn't match a Mojang account (generated names, user bots, etc.).

**Default (since v1.5.4):** `guaranteed-skin: false` — bots with no matching Mojang account will display the default Steve or Alex skin. This avoids unnecessary Mojang API calls and is the recommended setting for most servers.

Set `guaranteed-skin: true` only if you want every bot to have a custom-fetched skin regardless of whether their name is a real Minecraft account.

**Config example:**

```yaml
skin:
  mode: auto
  guaranteed-skin: false   # false = Steve/Alex fallback for non-Mojang names (default)
```

### `custom` *(Full control — works online & offline)*

FPP runs a **5-step resolution pipeline** to find the best skin for each bot:

```
1. Exact-name override  (skin.custom.by-name)
2. File named <botname>.png  (plugins/FakePlayerPlugin/skins/)
3. Random PNG file from the skins/ folder
4. Random entry from skin.custom.pool  (player names or URLs)
5. Mojang API fallback  (fetched by bot's own Minecraft name)
```

The first step that finds a valid skin wins. All Mojang fetches are cached
in-memory for the session and can be cleared with `/fpp reload`.

#### Config pool

Add Minecraft player names to the pool:

```yaml
skin:
  mode: custom
  pool:
    - Notch
    - Technoblade
    - Dream
```

Bots without an exact-name match randomly pick from this list.

#### Per-bot name overrides

Force a specific bot name to always use a particular player's skin:

```yaml
skin:
  mode: custom
  overrides:
    Herobrine: Notch          # bot "Herobrine" gets Notch's skin
    CoolBot: Technoblade      # bot "CoolBot" gets Technoblade's skin
```

Keys are matched **case-insensitively** against the bot's internal Minecraft name.

#### Skin folder

Place standard Minecraft PNG skin files in:

```
plugins/FakePlayerPlugin/skins/
```

**Naming rules:**

| File name | Behaviour |
|-----------|-----------|
| `anything.png` | Added to the random pool — any bot without a better match can use it |
| `<botname>.png` | Used **exclusively** for the bot named `<botname>` (exact, case-insensitive) |

Supported formats: `64×64` (modern slim/wide) and `64×32` (legacy classic) PNG files.

> **Note:** Folder skins have **no RSA signature**. They display correctly on Paper
> servers but may produce a `"profile not signed"` debug message — this is harmless.

The folder is scanned on startup and on `/fpp reload`.

---

### `off`

No skin is applied. Bots display the default Steve or Alex appearance
(determined by UUID per vanilla Minecraft rules).

---

## Full Config Reference

```yaml
skin:
  # Skin mode: auto | custom | off
  mode: auto

  # When false (default since v1.5.4): bots with no Mojang account use Steve/Alex.
  # When true: attempt a skin fetch even for generated/non-Mojang bot names.
  guaranteed-skin: false

  # Clear resolved skin cache on /fpp reload
  clear-cache-on-reload: true

  # ── Custom mode ──────────────────────────────────────────────────────────
  # Per-bot skin override: bot-name: minecraft-username
  overrides: {}
  #  Herobrine: Notch
  #  CoolBot: https://textures.minecraft.net/texture/<hash>

  # Random skin pool — list of Minecraft usernames
  pool: []
  #  - Notch
  #  - Technoblade
  #  - https://textures.minecraft.net/texture/<hash>

  # ── Skin folder ──────────────────────────────────────────────────────────
  # Scan plugins/FakePlayerPlugin/skins/ for PNG files
  use-skin-folder: true
  # Place .png files in: plugins/FakePlayerPlugin/skins/
  # <botname>.png → exact match for that bot
  # anything.png  → random pool fallback
```

---

## How Skins Are Resolved (custom mode flow)

```
Bot "Herobrine" spawns
       │
       ▼
[1] by-name override?  ──YES──► Use "Notch" skin (fetched via Mojang API)
       │NO
       ▼
[2] skins/Herobrine.png exists?  ──YES──► Use that file
       │NO
       ▼
[3] Any PNG in skins/ folder?  ──YES──► Pick random file
       │NO
       ▼
[4] Any entries in pool?  ──YES──► Pick random entry
       │NO
       ▼
[5] Fetch "Herobrine" from Mojang API  ──OK──► Use fetched skin
                                        │FAIL
                                        ▼
                                 Fall back to auto mode
                                 (Mannequin.setProfile(name))
```

---

## Rate Limiting

When `mode: custom` is active and the plugin fetches skins from Mojang:

- Requests are queued with a **200 ms gap** between each call
- Results are **cached per session** — each name is fetched at most once
- Mojang's documented limit is ~600 requests / 10 minutes per IP
- `/fpp reload` clears the cache (if `clear-cache-on-reload: true`)

---

## Choosing a Mode

| Scenario | Recommended mode |
|----------|-----------------|
| Online-mode server, bot names match Mojang accounts | `auto` |
| Online-mode server, custom/random bot names | `custom` with pool |
| Offline-mode server | `custom` with pool or folder |
| You have a set of specific skin PNGs you want to use | `custom` with skins folder |
| You want a specific bot to always wear a specific skin | `custom` + `by-name` |
| Performance-critical, skins don't matter | `off` |

---

← [Database](Database.md) · [Bot Behaviour](Bot-Behaviour.md) →

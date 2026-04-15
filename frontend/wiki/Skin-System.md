# 🎨 Skin System

FPP supports three skin modes, a built-in fallback pool of 1000 real Minecraft accounts, DB-backed caching, and NameTag plugin skin integration.

---

## Quick Setup

```yaml
skin:
  mode: player
  guaranteed-skin: true
  clear-cache-on-reload: true
```

Run `/fpp reload` after changing skin settings.

---

## Modes

### `player` (recommended, default)

FPP tries to fetch the Mojang skin matching the bot's name.

- If the bot's name matches a real Minecraft account, that account's skin is used
- If the name doesn't match (or Mojang returns no skin), the **built-in 1000-player fallback pool** provides a random real skin
- DB-backed skin cache avoids repeated Mojang API lookups (7-day TTL)

> **Legacy alias:** `auto` — works the same as `player`

Best for:
- all server types (online and offline mode)
- bot names that match real Minecraft accounts
- bots with generated names that still need real-looking skins

With `guaranteed-skin: true` (default for new installs and enforced by v58→v59 migration):
- bots whose names don't match a real Mojang account get a random skin from the built-in 1000-player pool
- every bot always has a real-looking skin

### `random` (advanced)

FPP resolves skins from a multi-step custom pipeline.

> **Legacy alias:** `custom` — works the same as `random`

Current resolution order:
1. `skin.overrides`
2. exact `<botname>.png` in `plugins/FakePlayerPlugin/skins/`
3. random PNG from the `skins/` folder
4. random entry from `skin.pool`
5. fallback to normal Mojang name lookup behavior

### `none`

Bots use vanilla Steve/Alex appearance only.

> **Legacy alias:** `off` — works the same as `none`

---

## SkinManager (v1.6.4+)

The `SkinManager` class centralises all skin resolution, caching, and application:

- **Resolution priority:** NameTag skin (if NameTag plugin detected) → DB cache → Mojang API → fallback pool
- **DB skin cache:** resolved skins are cached in the `fpp_skin_cache` database table (7-day TTL, auto-cleanup)
- **1000-player fallback pool:** hardcoded list of real Minecraft accounts with varied skins — no config needed
- **NameTag skin priority:** when the [NameTag](https://lode.gg) plugin is installed and assigns a skin to a bot, that skin takes precedence over all other modes
- **Public API:** `plugin.getSkinManager()` — `resolveEffectiveSkin`, `applySkinByPlayerName`, `applySkinFromProfile`, `applyNameTagSkin`, `resetToDefaultSkin`, `preloadSkin`, `clearCache`

---

## Current Config Keys

```yaml
skin:
  mode: player
  guaranteed-skin: true
  clear-cache-on-reload: true
  overrides: {}
  pool: []
  use-skin-folder: true
```

| Key | Description |
|-----|-------------|
| `mode` | `player`, `random`, or `none` (legacy: `auto`, `custom`, `off`) |
| `guaranteed-skin` | When `true`, bots that can't resolve their name always use a fallback skin from the built-in pool |
| `clear-cache-on-reload` | Clears cached skin results on `/fpp reload` |
| `overrides` | Map of `bot-name -> minecraft-username` (random mode only) |
| `pool` | List of fallback usernames for random assignment (random mode only) |
| `use-skin-folder` | Enable scanning of `plugins/FakePlayerPlugin/skins/` (random mode only) |

---

## `overrides`

Force a specific bot name to always use a specific Minecraft account skin.

```yaml
skin:
  mode: custom
  overrides:
    Herobrine: Notch
    BuilderBot: Technoblade
```

This uses the current `overrides` key name.

---

## `pool`

Provide a list of fallback usernames for random skin selection.

```yaml
skin:
  mode: custom
  pool:
    - Notch
    - Technoblade
    - Dream
```

Bots without a stronger match can randomly use one of these.

---

## Skin Folder

When `use-skin-folder: true`, FPP scans:

```text
plugins/FakePlayerPlugin/skins/
```

### File naming behavior

| File name | Behavior |
|-----------|----------|
| `<botname>.png` | Exact per-bot match |
| `anything-else.png` | Random pool candidate |

Supported formats:
- `64x64`
- `64x32`

On first run, FPP also generates a helpful:

```text
plugins/FakePlayerPlugin/skins/README.txt
```

---

## Current Defaults

### `guaranteed-skin`

Default is now:

```yaml
guaranteed-skin: true
```

Bots whose names don't match a real Mojang account get a random skin from the built-in 1000-player fallback pool. Every bot always has a real-looking skin.

> **Migration note:** existing installs that had `guaranteed-skin: false` are upgraded to `true` by config migration v58→v59.

---

## Removed Older Fallback Keys

Older versions had extra fallback keys:
- `skin.fallback-pool`
- `skin.fallback-name`

These were removed in config migration v59→v60. The 1000-player fallback pool is now hardcoded inside `SkinManager` — no configuration needed.

---

## DB Skin Cache (v1.6.4+)

Resolved skins are cached in the `fpp_skin_cache` database table:

| Column | Description |
|--------|-------------|
| `skin_name` | Minecraft username (primary key) |
| `texture_value` | Base64 skin texture |
| `texture_signature` | Mojang signature |
| `source` | Origin label (e.g. `mojang:PlayerName`) |
| `cached_at` | Timestamp; entries expire after 7 days |

Cache entries are auto-cleaned on startup. This avoids repeated Mojang API calls for the same bot names.

---

## Notes

- skins are applied to both the in-world bot body and the packet/tab-facing profile path
- folder PNG skins may not have a Mojang signature; they still work fine for most Paper setups
- `/fpp reload` can refresh skin state and clear caches if configured

---

## Recommended Mode by Use Case

| Scenario | Recommended mode |
|----------|------------------|
| All servers (online or offline mode) | `player` (default) |
| Generated names, but you want custom-looking bots | `random` + `pool` |
| Exact skin control for specific names | `random` + `overrides` |
| Fully offline-mode / local-control setup | `random` + `skins/` folder |
| Appearance does not matter | `none` |

---

## Example Configs

### Simple online-mode setup

```yaml
skin:
  mode: player
  guaranteed-skin: true
```

### Controlled custom setup

```yaml
skin:
  mode: random
  guaranteed-skin: true
  overrides:
    TraderBot: Notch
  pool:
    - Dream
    - Technoblade
  use-skin-folder: true
```

---

See also:
- [Configuration](Configuration.md)
- [Bot-Behaviour](Bot-Behaviour.md)

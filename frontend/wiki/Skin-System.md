# 🎨 Skin System

FPP supports three skin modes and several ways to resolve a bot's appearance.

---

## Quick Setup

```yaml
skin:
  mode: auto
  guaranteed-skin: false
  clear-cache-on-reload: true
```

Run `/fpp reload` after changing skin settings.

---

## Modes

### `auto`

FPP tries to fetch the Mojang skin matching the bot's name.

Best for:
- online-mode servers
- bot names that match real Minecraft accounts

Important default behavior:

```yaml
skin:
  mode: auto
  guaranteed-skin: false
```

With `guaranteed-skin: false`:
- bots with non-Mojang/generated names simply use Steve/Alex
- this avoids unnecessary API calls

### `custom`

FPP resolves skins from a multi-step custom pipeline.

Current resolution order:
1. `skin.overrides`
2. exact `<botname>.png` in `plugins/FakePlayerPlugin/skins/`
3. random PNG from the `skins/` folder
4. random entry from `skin.pool`
5. fallback to normal Mojang name lookup behavior

### `off`

Bots use vanilla Steve/Alex appearance only.

---

## Current Config Keys

```yaml
skin:
  mode: auto
  guaranteed-skin: false
  clear-cache-on-reload: true
  overrides: {}
  pool: []
  use-skin-folder: true
```

| Key | Description |
|-----|-------------|
| `mode` | `auto`, `custom`, or `off` |
| `guaranteed-skin` | Try harder to fetch skins for generated names |
| `clear-cache-on-reload` | Clears cached skin results on `/fpp reload` |
| `overrides` | Map of `bot-name -> minecraft-username` |
| `pool` | List of fallback usernames for random assignment |
| `use-skin-folder` | Enable scanning of `plugins/FakePlayerPlugin/skins/` |

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
guaranteed-skin: false
```

That means generated / fake names normally display Steve/Alex unless custom skin sources are available.

This is the intended behavior in the current plugin line.

---

## Removed Older Fallback Keys

Older versions had extra fallback keys such as:
- `skin.fallback-pool`
- `skin.fallback-name`

Those are no longer part of the current config structure.

Use `pool`, `overrides`, and the `skins/` folder instead.

---

## Notes

- skins are applied to both the in-world bot body and the packet/tab-facing profile path
- folder PNG skins may not have a Mojang signature; they still work fine for most Paper setups
- `/fpp reload` can refresh skin state and clear caches if configured

---

## Recommended Mode by Use Case

| Scenario | Recommended mode |
|----------|------------------|
| Online-mode server, real account-style names | `auto` |
| Generated names, but you want custom-looking bots | `custom` + `pool` |
| Exact skin control for specific names | `custom` + `overrides` |
| Fully offline-mode / local-control setup | `custom` + `skins/` folder |
| Appearance does not matter | `off` |

---

## Example Configs

### Simple online-mode setup

```yaml
skin:
  mode: auto
  guaranteed-skin: false
```

### Controlled custom setup

```yaml
skin:
  mode: custom
  guaranteed-skin: false
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

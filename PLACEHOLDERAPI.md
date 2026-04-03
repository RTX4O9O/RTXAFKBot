# PlaceholderAPI — Fake Player Plugin (FPP)

All placeholders use the identifier `fpp` and require [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) to be installed.  
FPP auto-registers the expansion on startup when PlaceholderAPI is detected — **no `/papi ecloud` download needed**.

> **Version:** 1.5.4+ · **Placeholders:** 26+ · **Auto-register:** Yes

---

## Quick Reference

| Category | Count | Description |
|----------|-------|-------------|
| [Server-Wide](#server-wide-placeholders) | 8 | Bot counts, names, version — same value for all players |
| [Config State](#config-state-placeholders) | 9 | Live config.yml values — update immediately after `/fpp reload` |
| [Network / Proxy](#network--proxy-placeholders) | 3 | NETWORK mode state and server identity |
| [Per-World](#per-world-placeholders) | 3 dynamic | Bot/player counts scoped to a specific world |
| [Player-Relative](#player-relative-placeholders) | 3 | Values specific to the requesting player |

---

## Server-Wide Placeholders

These return the same value regardless of which player requests them.

| Placeholder | Return type | Description |
|-------------|-------------|-------------|
| `%fpp_count%` | `integer` | Number of fake player bots currently active (all worlds). In NETWORK mode includes remote bots from other proxy servers |
| `%fpp_max%` | `integer` / `∞` | Global bot cap (`limits.max-bots`). Returns `∞` when the cap is `0` (unlimited) |
| `%fpp_real%` | `integer` | Number of **real** (non-bot) players currently online |
| `%fpp_total%` | `integer` | Real players + fake bots combined — useful for "server population" displays |
| `%fpp_online%` | `integer` | Alias for `%fpp_total%` — real players + bots combined (use whichever reads more naturally) |
| `%fpp_frozen%` | `integer` | Number of bots currently frozen via `/fpp freeze` |
| `%fpp_names%` | `string` | Comma-separated list of all active bot display names, e.g. `Steve, Alex, Notch` |
| `%fpp_version%` | `string` | Running plugin version, e.g. `1.5.4` |

### `%fpp_real%` vs `%fpp_total%` vs `%fpp_online%`

`%fpp_real%` uses `Bukkit.getOnlinePlayers().size()`, which only counts **real** Bukkit `Player` objects.  
Fake bots are **not** Bukkit players — they are NMS ServerPlayer entities not accessible via the Bukkit API.

```
%fpp_total%  =  %fpp_real%  +  %fpp_count%
%fpp_online% =  %fpp_real%  +  %fpp_count%   (identical — use whichever reads more naturally)
```

### `%fpp_max%` and unlimited servers

When `limits.max-bots: 0` (no cap), `%fpp_max%` returns the literal string `∞`.  
If you need a numeric check in another plugin, set an explicit cap and compare against `%fpp_count%`.

---

## Config State Placeholders

These reflect the current `config.yml` values and update immediately after `/fpp reload`.

| Placeholder | Values | Config key | Description |
|-------------|--------|------------|-------------|
| `%fpp_chat%` | `on` / `off` | `fake-chat.enabled` | Is the fake chat system enabled? |
| `%fpp_swap%` | `on` / `off` | `swap.enabled` | Is the bot swap/rotation system enabled? |
| `%fpp_body%` | `on` / `off` | `body.enabled` | Are bots spawning physical NMS entities? |
| `%fpp_pushable%` | `on` / `off` | `body.pushable` | Can players push bot bodies? |
| `%fpp_damageable%` | `on` / `off` | `body.damageable` | Can bot bodies take damage? |
| `%fpp_tab%` | `on` / `off` | `tab-list.enabled` | Are bots visible in the tab list? |
| `%fpp_skin%` | `auto` / `custom` / `off` | `skin.mode` | Which skin mode is active? |
| `%fpp_max_health%` | `number` | `combat.max-health` | Bot max HP (default: `20.0`) |
| `%fpp_persistence%` | `on` / `off` | `persistence.enabled` | Are bots saved and restored on server restart? |

**Usage example — status board:**
```
&7Chat:    &f%fpp_chat%
&7Swap:    &f%fpp_swap%
&7Bodies:  &f%fpp_body%  &8(&7push: &f%fpp_pushable%&8)
&7Tab:     &f%fpp_tab%
&7Skin:    &f%fpp_skin%
&7Persist: &f%fpp_persistence%
```

---

## Network / Proxy Placeholders

Useful when running FPP in NETWORK mode across a Velocity or BungeeCord proxy network.  
These placeholders reflect server-specific network configuration values.

| Placeholder | Values / type | Config key | Description |
|-------------|---------------|------------|-------------|
| `%fpp_network%` | `on` / `off` | `database.mode` | `on` when `database.enabled: true` and `database.mode: NETWORK`; `off` otherwise |
| `%fpp_server_id%` | `string` | `database.server-id` | The unique server identifier configured for this backend |
| `%fpp_spawn_cooldown%` | `integer` | `spawn-cooldown` | Configured spawn cooldown in seconds (`0` = off) |

**Usage example — network status bar:**
```
&7Network: &f%fpp_network%  &8│  &7Server: &f%fpp_server_id%  &8│  &7Cooldown: &f%fpp_spawn_cooldown%s
```

---

## Per-World Placeholders

Append any world name to `count_`, `real_`, or `total_` to scope the count to that specific world.  
World names are **case-insensitive**. Replace spaces in world names with underscores.

| Placeholder | Return type | Description |
|-------------|-------------|-------------|
| `%fpp_count_<world>%` | `integer` | Bots whose current position is in `<world>` |
| `%fpp_real_<world>%` | `integer` | Real (non-bot) players currently in `<world>` |
| `%fpp_total_<world>%` | `integer` | Bots + real players in `<world>` combined |

### World Name Examples

| Placeholder | What it returns |
|-------------|----------------|
| `%fpp_count_world%` | Bots in the overworld |
| `%fpp_real_world_nether%` | Real players in the nether |
| `%fpp_total_world_the_end%` | Everyone (bots + players) in the end |
| `%fpp_count_skyblock%` | Bots in a world named `skyblock` |
| `%fpp_total_my_custom_world%` | Total in world named `my custom world` |

> **Bot world resolution:** FPP checks the live NMS ServerPlayer body position first.  
> For bodyless bots (spawned without `body.enabled: true`) it falls back to the bot's last recorded spawn location.  
> Bots with no resolvable world are excluded from all per-world counts.

---

## Player-Relative Placeholders

These return values specific to the player requesting the placeholder.  
They require an **online** player context — they fall back gracefully when the context player is offline or `null`.

| Placeholder | Return type | Description |
|-------------|-------------|-------------|
| `%fpp_user_count%` | `integer` | Number of bots currently spawned **by this player** |
| `%fpp_user_max%` | `integer` | This player's personal bot limit (see resolution below) |
| `%fpp_user_names%` | `string` | Comma-separated display names of bots **owned by this player**. Empty string when the player has no bots |

### `%fpp_user_max%` Resolution Order

1. Highest `fpp.bot.<num>` permission node the player has (scans `fpp.bot.1` → `fpp.bot.100`)
2. Falls back to `limits.user-bot-limit` from `config.yml` if no personal node is found

### Using Player-Relative Placeholders in FPP's Tab Format

FPP calls `PlaceholderAPI.setPlaceholders(null, display)` (server-wide context) when building display names,
so `%fpp_user_*%` placeholders will return their fallback values (`0` / `""`) in that context.
Use player-relative placeholders only in external plugins where a real player context exists.

```yaml
# This works — server-wide PAPI placeholders only
bot-name:
  tab-list-format: '{prefix}{bot_name}{suffix} <gray>(%fpp_count% bots)'

# This will return 0 — user_count has no player context in this slot
bot-name:
  tab-list-format: '{bot_name} (%fpp_user_count%)'  # don't do this
```

---

## Usage Examples

### Scoreboard / Hologram — Server Population

```
&7Real players:  &f%fpp_real%
&7Fake bots:     &f%fpp_count%
&8────────────────────
&7Total online:  &a%fpp_total%
```

### TAB Plugin Header — Live Stats

```yaml
header:
  - "<gray>Players: <white>%fpp_real% <dark_gray>│ <gray>Bots: <white>%fpp_count% <dark_gray>│ <gray>Total: <white>%fpp_total%"
  - "<gray>Status: <white>Chat %fpp_chat% <dark_gray>│ <gray>Swap %fpp_swap% <dark_gray>│ <gray>Skin %fpp_skin%"
```

### Show a Player's Own Bot Stats

```
&7Your bots: &f%fpp_user_count%&7/&f%fpp_user_max%
&7Names: &f%fpp_user_names%
```

### Dynamic Join Message (via another plugin)

```
Welcome! There are %fpp_real% players and %fpp_count% bots online.
```

### Per-World Scoreboard

```
&8─ Overworld ─────────────
  &7Players: &f%fpp_real_world%
  &7Bots:    &f%fpp_count_world%
  &7Total:   &a%fpp_total_world%

&8─ Nether ────────────────
  &7Players: &f%fpp_real_world_nether%
  &7Bots:    &f%fpp_count_world_nether%

&8─ End ──────────────────
  &7Total:   &f%fpp_total_world_the_end%
```

### Server Status Panel

```
&6&lServer Status
&7 Bots online:  &b%fpp_count%&7/&b%fpp_max%
&7 Real players: &a%fpp_real%
&7 Total:        &e%fpp_total%
&7 Fake chat:    %fpp_chat%  &7Swap: %fpp_swap%
&7 Network mode: %fpp_network%  &7Server: %fpp_server_id%
```

### Network / Proxy Status Board

```
&7Network: %fpp_network%  &8│  &7Server: %fpp_server_id%
&7Cooldown: %fpp_spawn_cooldown%s  &8│  &7Persist: %fpp_persistence%
```

### Conditional Display (via plugin that supports conditions)

```yaml
# Show different message based on bot count
condition: "%fpp_count% > 0"
true:  "&a%fpp_count% fake players are online"
false: "&cNo fake players online"
```

### Bot Names in MOTD

```yaml
motd: |
  &6Welcome to MyServer!
  &7Online: &f%fpp_real% real players + &b%fpp_count% bots
  &8Bots: &7%fpp_names%
```

---

## Integration Guide

### CMI / DecentHolograms — Bot Count Hologram

```
{fpp_count} / {fpp_max}
{fpp_real} Real Players
{fpp_count} Bots Active
```

### FeatherBoard / AnimatedScoreboard — Cycle Display

```yaml
lines:
  - '&7Bots: &b%fpp_count%'
  - '&7Players: &a%fpp_real%'
  - '&7Total: &e%fpp_total%'
  - '&7Network: &f%fpp_network%'
```

### LuckPerms + FPP in Scoreboard

When bots are assigned LP groups via `/fpp rank`, you can combine LP placeholders with FPP ones:

```
&7Bots: &f%fpp_count%
&7Skin mode: &f%fpp_skin%
```

### EssentialsX Chat Format

```yaml
format: '<{DISPLAYNAME}>&r {MESSAGE}'
# Combined with FPP tab-list-format for bot names:
# bot-name.tab-list-format: '{prefix}{bot_name}{suffix}'
```

---

## Technical Notes

### Update Frequency

| Placeholder type | Update trigger |
|-----------------|----------------|
| Count (`%fpp_count%`, `%fpp_frozen%`) | Immediately when a bot is spawned or despawned |
| Config state (`%fpp_chat%`, `%fpp_persistence%`, etc.) | Immediately after `/fpp reload` |
| Network state (`%fpp_network%`, `%fpp_server_id%`) | On startup and `/fpp reload` |
| Per-world counts | Immediately when a bot moves worlds or is spawned/despawned |
| Player-relative | Every time the placeholder is requested (live) |
| `%fpp_names%` | Immediately when any bot is spawned, despawned, or renamed |

### Performance

All placeholders are O(1) or O(n) where n = active bot count (typically < 100).  
No disk I/O or async operations are performed during placeholder resolution.  
Suitable for high-frequency requests (scoreboards refreshing every 100ms, tab lists, etc.).

### Thread Safety

All placeholder requests are handled on Bukkit's main thread via PlaceholderAPI's standard expansion mechanism.  
FPP's internal bot registry uses a thread-safe data structure — no race conditions during concurrent spawn operations.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| All `%fpp_*%` return unparsed (e.g. `%fpp_count%`) | PlaceholderAPI is not installed or FPP failed to register — check console on startup for `[FPP] PlaceholderAPI expansion registered` |
| `%fpp_user_count%` always returns `0` | The context player is offline or null — this placeholder requires an online player |
| `%fpp_max%` shows `∞` but you set a number | Make sure you saved the config and ran `/fpp reload` |
| Per-world placeholder always returns `0` | Check world name spelling — it's case-insensitive but must match exactly (use underscores for spaces) |
| Values are stale after `/fpp reload` | Config-state placeholders update instantly; count placeholders reflect live state and never need reload |
| `%fpp_names%` shows raw UUIDs | Bot display names were not yet resolved at the time of the request — try again after a tick or two |
| `%fpp_user_names%` is empty despite having bots | The requesting player's UUID doesn't match the spawner UUID — bots spawned by console won't appear here |
| `%fpp_pushable%` or `%fpp_damageable%` shows wrong value | Run `/fpp reload` to re-sync config state placeholders after editing config.yml |
| `%fpp_network%` always `off` | Only `on` when `database.enabled: true` **and** `database.mode: NETWORK` |
| `%fpp_server_id%` shows `default` | Set `database.server-id` in config.yml to a unique value per server |
| `%fpp_spawn_cooldown%` shows `0` | `spawn-cooldown: 0` means disabled — set a positive integer to enable |
| `%fpp_persistence%` shows `off` unexpectedly | Check `persistence.enabled` in config.yml and run `/fpp reload` |
| `%fpp_skin%` shows unexpected value | Only `auto`, `custom`, or `off` are valid — check `skin.mode` in config.yml |

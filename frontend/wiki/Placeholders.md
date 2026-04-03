# PlaceholderAPI Integration

FPP registers a full **PlaceholderAPI** expansion (`%fpp_…%`) that is available to any plugin that supports PAPI — TAB, Scoreboard plugins, chat formatters, holograms, etc.

**Requirement:** [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) must be installed.  
No configuration is needed — the expansion registers automatically when PAPI is detected on startup.

> **Version:** 1.5.4+ · **Total placeholders:** 26+ · **Auto-register:** Yes

---

## Server-Wide Placeholders

These are player-independent and return the same value regardless of who requests them.

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_count%` | Number | Active bot count (all worlds). Also supports per-world: `%fpp_count_<world>%` — see [Per-World Placeholders](#per-world-placeholders) |
| `%fpp_max%` | Number / `∞` | Global max-bots cap (`limits.max-bots`; `∞` when 0) |
| `%fpp_real%` | Number | Real (non-bot) online player count |
| **`%fpp_total%`** | **Number** | **Real players + bots combined** |
| `%fpp_online%` | Number | Alias for `%fpp_total%` |
| `%fpp_frozen%` | Number | Number of currently frozen bots |
| `%fpp_names%` | String | Comma-separated list of active bot display names |
| `%fpp_chat%` | `on` / `off` | Whether fake-chat is enabled |
| `%fpp_swap%` | `on` / `off` | Whether bot-swap/rotation is enabled |
| `%fpp_skin%` | `auto` / `custom` / `off` | Current skin mode (`skin.mode` in config) |
| `%fpp_body%` | `on` / `off` | Whether physical bot bodies are spawned |
| `%fpp_pushable%` | `on` / `off` | Whether bot bodies are pushable |
| `%fpp_damageable%` | `on` / `off` | Whether bot bodies can take damage |
| `%fpp_tab%` | `on` / `off` | Whether bots appear in the tab list |
| `%fpp_max_health%` | Number | Configured bot max-health (`combat.max-health`) |
| `%fpp_persistence%` | `on` / `off` | Whether bots are saved and restored on restart |
| `%fpp_version%` | String | FPP plugin version |

---

## Network / Proxy Placeholders

Useful when running FPP in NETWORK mode across a Velocity or BungeeCord proxy.

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_network%` | `on` / `off` | `on` when `database.mode: NETWORK`; `off` in LOCAL mode |
| `%fpp_server_id%` | String | Value of `database.server-id` for this server |
| `%fpp_spawn_cooldown%` | Number | Configured spawn cooldown in seconds (`0` = disabled) |

---

## Per-World Placeholders

Dynamic variants of the three count placeholders. Replace `<world>` with the actual world name (case-insensitive).

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_count_<world>%` | Number | Active bot count in the specified world |
| `%fpp_real_<world>%` | Number | Real (non-bot) player count in the specified world |
| `%fpp_total_<world>%` | Number | Real players + bots combined in the specified world |

**Examples:**
```
%fpp_count_overworld%   → 2   (bots in the Overworld)
%fpp_real_nether%       → 1   (real players in the Nether)
%fpp_total_end%         → 3   (everyone in The End)
```

> **Note:** Bot world detection uses the live NMS ServerPlayer body position.  
> Bodyless bots fall back to their last recorded spawn location.

---

## Player-Relative Placeholders

These return values specific to the requesting player. When used in a context with no player (e.g. a console command), they return the global default.

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_user_count%` | Number | Number of bots currently owned by this player |
| `%fpp_user_max%` | Number | This player's personal bot limit (respects `fpp.bot.<num>` permission; falls back to `limits.user-bot-limit`) |
| `%fpp_user_names%` | String | Comma-separated display names of bots owned by this player |

---

## Combined Player Count — `%fpp_total%`

`%fpp_total%` returns **real online players + active bots** as a single number.  
This is the "combined prefix" placeholder — ideal for showing a server's apparent population in:

- Tab-list headers via TAB plugin  
- Scoreboard sidebars  
- Server MOTD / status boards  
- Holographic displays (HolographicDisplays, DecentHolograms, etc.)

**Example — TAB plugin header:**
```yaml
header:
  - "<gold>Online: <white>%fpp_total% <gray>(%fpp_real% real · %fpp_count% bots)"
```

**Example — Scoreboard line:**
```
Players: %fpp_total% (%fpp_count% bots)
```

---

## Using PAPI Placeholders Inside FPP Config

FPP itself expands PlaceholderAPI tokens in these config strings:

| Location | Config key | Notes |
|----------|-----------|-------|
| Bot tab-list / nametag display | `bot-name.tab-list-format` | Expanded with server-wide context |
| Fake-chat line format | `fake-chat.chat-format` | Expanded with server-wide context |

**Example — show combined count in every bot's nametag:**
```yaml
bot-name:
  tab-list-format: '{prefix}{bot_name}{suffix} <dark_gray>[%fpp_total%]'
```

**Example — show bot count in chat messages:**
```yaml
fake-chat:
  chat-format: "&7{bot_name}: {message} &8[%fpp_count% bots online]"
```

> **Note:** Player-relative placeholders (`%fpp_user_count%` etc.) used inside `tab-list-format` or `chat-format` are resolved with a **null** (server-wide) player context, so they return "0" / the global default.

## Quick Examples

```
%fpp_total%             → 27   (5 real players + 22 bots)
%fpp_count%             → 22
%fpp_real%              → 5
%fpp_skin%              → auto
%fpp_network%           → on   (NETWORK mode active)
%fpp_server_id%         → survival
%fpp_spawn_cooldown%    → 30   (30 second cooldown)
%fpp_user_count%        → 3    (this player owns 3 bots)
%fpp_user_max%          → 5    (this player can own up to 5)
%fpp_names%             → "Notch, Dream, Technoblade"
```


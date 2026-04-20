# 📊 PlaceholderAPI Integration

FPP registers a full `%fpp_*%` PlaceholderAPI expansion for scoreboard plugins, TAB headers, holograms, chat formats, and other PAPI-aware plugins.

Requirement: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)

> **Current line:** v1.6.6.1  
> **Placeholder count:** 29+  
> Registered automatically when PlaceholderAPI is present.

---

## Server-Wide Placeholders

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_count%` | Number | Total bot count. Includes remote bots in `NETWORK` mode |
| `%fpp_local_count%` | Number | Bots running on this server only |
| `%fpp_network_count%` | Number | Bots running on other backend servers |
| `%fpp_max%` | Number / `∞` | Global bot cap from `limits.max-bots` |
| `%fpp_real%` | Number | Real online players only |
| `%fpp_total%` | Number | Real players + bots combined |
| `%fpp_online%` | Number | Alias for `%fpp_total%` |
| `%fpp_frozen%` | Number | Frozen bot count |
| `%fpp_names%` | String | Comma-separated bot display names (local + remote in `NETWORK` mode) |
| `%fpp_network_names%` | String | Comma-separated remote bot display names only |
| `%fpp_chat%` | `on` / `off` | Fake-chat state |
| `%fpp_swap%` | `on` / `off` | Swap state |
| `%fpp_skin%` | `player` / `random` / `none` | Current skin mode (`auto`, `custom`, `off` are legacy aliases still accepted) |
| `%fpp_body%` | `on` / `off` | Physical body state |
| `%fpp_pushable%` | `on` / `off` | Whether bodies are pushable |
| `%fpp_damageable%` | `on` / `off` | Whether bodies can be damaged |
| `%fpp_tab%` | `on` / `off` | Tab-list visibility state |
| `%fpp_max_health%` | Number | `combat.max-health` |
| `%fpp_persistence%` | `on` / `off` | Persistence state |
| `%fpp_version%` | String | Plugin version |

---

## Network / Proxy Placeholders

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_network%` | `on` / `off` | `on` when `database.mode: NETWORK` |
| `%fpp_server_id%` | String | Current `database.server-id` |
| `%fpp_spawn_cooldown%` | Number | Configured `spawn-cooldown` in seconds |

### Notes

- `%fpp_count%` includes remote bots in `NETWORK` mode
- `%fpp_local_count%` lets you show only this backend's bots
- `%fpp_network_count%` lets you show only other backends' bots

---

## Per-World Placeholders

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_count_<world>%` | Number | Bots in the specified world |
| `%fpp_real_<world>%` | Number | Real players in the specified world |
| `%fpp_total_<world>%` | Number | Total players + bots in the specified world |

Examples:

```text
%fpp_count_world%
%fpp_real_world_nether%
%fpp_total_world_the_end%
```

World names are case-insensitive.

---

## Player-Relative Placeholders

| Placeholder | Returns | Description |
|-------------|---------|-------------|
| `%fpp_user_count%` | Number | Bots owned by the player |
| `%fpp_user_max%` | Number | Player's personal bot cap |
| `%fpp_user_names%` | String | Comma-separated owned bot names |

### Important note for `%fpp_user_max%`

This value is resolved from:
- the highest `fpp.spawn.limit.<N>` node the player has
- otherwise `limits.user-bot-limit`

Older docs that referenced `fpp.bot.<num>` are outdated.

---

## How `%fpp_real%` and `%fpp_total%` Work

FPP bots go through the normal server player pipeline, so they appear in Bukkit's online-player list.

That means:
- `%fpp_real%` = online player count minus local bots
- `%fpp_total%` = real players + bots

This is why these placeholders are useful for tab headers and scoreboards without double-counting.

---

## Example Uses

### TAB header

```text
&7Real: &a%fpp_real% &8| &7Bots: &b%fpp_count% &8| &7Total: &e%fpp_total%
```

### Scoreboard

```text
Bots: %fpp_count%
Players: %fpp_total%
```

### Proxy-aware split

```text
Local bots: %fpp_local_count%
Remote bots: %fpp_network_count%
```

---

## Notes About Using PAPI with FPP

FPP itself registers the placeholders automatically.

You usually use them in:
- TAB headers/footers
- scoreboard plugins
- hologram plugins
- chat / formatting plugins that support PAPI

The old `bot-name.tab-list-format` and `fake-chat.chat-format` style of internal formatting is no longer the primary way to use these values.

---

## Quick Examples

```text
%fpp_total%          → 27
%fpp_count%          → 22
%fpp_local_count%    → 15
%fpp_network_count%  → 7
%fpp_real%           → 5
%fpp_skin%           → player
%fpp_network%        → on
%fpp_server_id%      → survival
%fpp_user_count%     → 3
%fpp_user_max%       → 5
```

---

See also:
- [Proxy-Support](Proxy-Support)
- [Configuration](Configuration)
- [README](../../README)

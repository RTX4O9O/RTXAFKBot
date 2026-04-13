> [🏠 Home](Home.md) · [Getting Started](Getting-Started.md) · [Commands](Commands.md) · [Permissions](Permissions.md) · [Configuration](Configuration.md) · [Language](Language.md) · [Bot Names](Bot-Names.md) · [Bot Messages](Bot-Messages.md) · [Database](Database.md) · [Proxy Support](Proxy-Support.md) · [Config Sync](Config-Sync.md) · [Skin System](Skin-System.md) · [Bot Behaviour](Bot-Behaviour.md) · [Swap System](Swap-System.md) · [Fake Chat](Fake-Chat.md) · **Placeholders** · [FAQ](FAQ.md)

---

> 🎉 **FakePlayerPlugin is now Open Source** — [https://github.com/Pepe-tf/fake-player-plugin](https://github.com/Pepe-tf/fake-player-plugin)

# Placeholders (PlaceholderAPI)

FPP provides **24+ PlaceholderAPI placeholders** organized into five categories.  
Requires [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) - FPP auto-registers on startup, no `/papi ecloud` needed.

> For the full technical reference including integration examples and troubleshooting, see [PLACEHOLDERAPI.md](../PLACEHOLDERAPI.md).
> **Version:** 1.5.8+ · **Total placeholders:** 29+ (10 server-wide · 9 config state · 3 network · 3 per-world dynamic · 3 player-relative)

---

## Server-Wide Placeholders

Same value for all players - no player context required.  
In **NETWORK mode**, count/name placeholders include bots from **all servers** in the proxy.

| Placeholder | Return Type | Description | Example |
|-------------|-------------|-------------|---------|
| `%fpp_count%` | `integer` | Active bots - **all servers** in NETWORK mode, local only otherwise | `5` |
| `%fpp_local_count%` | `integer` | Bots on **this server only** (always local, regardless of mode) | `3` |
| `%fpp_network_count%` | `integer` | Bots on **other servers only** (0 in LOCAL mode) | `2` |
| `%fpp_max%` | `integer` / `∞` | Global bot cap (`limits.max-bots`). `∞` when cap is `0` | `50` or `∞` |
| `%fpp_real%` | `integer` | Real (non-bot) players online | `12` |
| `%fpp_total%` | `integer` | Real players + bots combined (network-wide) | `17` |
| `%fpp_online%` | `integer` | Alias for `%fpp_total%` | `17` |
| `%fpp_frozen%` | `integer` | Bots frozen via `/fpp freeze` (local only) | `2` |
| `%fpp_names%` | `string` | Comma-separated bot display names - **all servers** in NETWORK mode | `Steve, Alex, Notch` |
| `%fpp_network_names%` | `string` | Display names of bots on **other servers only** | `RemoteBot1, RemoteBot2` |
| `%fpp_version%` | `string` | Plugin version | `1.5.8` |

---

## Config State Placeholders

Reflect live `config.yml` values - update instantly after `/fpp reload`.

| Placeholder | Values | Config Key |
|-------------|--------|------------|
| `%fpp_chat%` | `on` / `off` | `fake-chat.enabled` |
| `%fpp_swap%` | `on` / `off` | `swap.enabled` |
| `%fpp_body%` | `on` / `off` | `body.enabled` |
| `%fpp_pushable%` | `on` / `off` | `body.pushable` |
| `%fpp_damageable%` | `on` / `off` | `body.damageable` |
| `%fpp_tab%` | `on` / `off` | `tab-list.enabled` |
| `%fpp_skin%` | `auto` / `custom` / `off` | `skin.mode` |
| `%fpp_max_health%` | number | `combat.max-health` |
| `%fpp_persistence%` | `on` / `off` | `persistence.enabled` |

---

## Network / Proxy Placeholders

Useful when running FPP in NETWORK mode across multiple backend servers.

| Placeholder | Values / Type | Description |
|-------------|---------------|-------------|
| `%fpp_network%` | `on` / `off` | `on` when `database.mode: NETWORK`; `off` otherwise |
| `%fpp_server_id%` | `string` | Value of `database.server-id` for this server |
| `%fpp_spawn_cooldown%` | `integer` | Configured spawn cooldown in seconds (`0` = off) |

> **Proxy-aware placeholders:** When `database.mode: NETWORK`, `%fpp_count%`, `%fpp_total%`, `%fpp_online%`, and `%fpp_names%` automatically include bots from **all servers** in the network. Use `%fpp_local_count%` to always get only this server's bots, and `%fpp_network_count%` / `%fpp_network_names%` for remote-server bots only.

---

## Per-World Placeholders

Dynamic - append any world name after `count_`, `real_`, or `total_`.  
World names are **case-insensitive**; use underscores for spaces.

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%fpp_count_<world>%` | Bots in specific world | `%fpp_count_world%` → `3` |
| `%fpp_real_<world>%` | Real players in world | `%fpp_real_world_nether%` → `1` |
| `%fpp_total_<world>%` | Bots + real players in world | `%fpp_total_world_the_end%` → `4` |

**Common examples:**

| Placeholder | World |
|-------------|-------|
| `%fpp_count_world%` | Default overworld |
| `%fpp_total_world_nether%` | The Nether |
| `%fpp_real_world_the_end%` | The End |
| `%fpp_count_skyblock%` | Custom world `skyblock` |

> **Bot world resolution:** FPP uses the live NMS ServerPlayer body position.  
> Bodyless bots fall back to their last recorded spawn location.

---

## Player-Relative Placeholders

Require an online player context. Fall back to `0` / `""` when the context player is offline.

| Placeholder | Return Type | Description | Example |
|-------------|-------------|-------------|---------|
| `%fpp_user_count%` | `integer` | Bots spawned by this player | `2` |
| `%fpp_user_max%` | `integer` | This player's personal bot limit | `10` |
| `%fpp_user_names%` | `string` | Comma-separated names of player's bots | `bot-Bill_Hub, bot-Bill_Hub-2` |

`%fpp_user_max%` resolution: highest `fpp.bot.<num>` node → fallback to `limits.user-bot-limit`

---

## `%fpp_real%` vs `%fpp_total%` vs `%fpp_online%`

```
%fpp_real%   = Bukkit.getOnlinePlayers().size()   (real players only, never includes bots)
%fpp_total%  = %fpp_real% + %fpp_count%
%fpp_online% = %fpp_real% + %fpp_count%            (identical - use whichever reads better)
```

Fake bots are NMS ServerPlayer entities not accessible via the Bukkit `Player` API.

---

## Usage Examples

### Scoreboard Sidebar

```
&7Players: &f%fpp_real%
&7Bots:    &f%fpp_count%&7/&f%fpp_max%
&8────────────────
&7Total:   &a%fpp_total%
```

### TAB Plugin Header

```yaml
header:
  - "<gray>Players: <white>%fpp_real% <dark_gray>│ <gray>Bots: <white>%fpp_count% <dark_gray>│ <gray>Total: <white>%fpp_total%"
```

### Player's Own Bot Stats

```
&7Your bots: &f%fpp_user_count%&7/&f%fpp_user_max%
&7Names: &f%fpp_user_names%
```

### Per-World Display

```
&8── Overworld ──────────────
  &7Players: &f%fpp_real_world%
  &7Bots:    &f%fpp_count_world%

&8── Nether ─────────────────
  &7Players: &f%fpp_real_world_nether%
  &7Bots:    &f%fpp_count_world_nether%
```

### Status Board

```
&7Chat: %fpp_chat%  &8│  &7Swap: %fpp_swap%  &8│  &7Body: %fpp_body%
&7Skin: %fpp_skin%  &8│  &7Tab: %fpp_tab%
```

### Network / Proxy Status Board

```
&7Network: %fpp_network%  &8│  &7Server: %fpp_server_id%
&7Cooldown: %fpp_spawn_cooldown%s  &8│  &7Persist: %fpp_persistence%
&7This server: %fpp_local_count%  &8│  &7Network total: %fpp_count%
```

### Using PAPI Placeholders in Tab-List Format

```yaml
# config.yml - works because finalizeDisplayName uses server-wide PAPI context
bot-name:
  tab-list-format: '{prefix}{bot_name}{suffix} <gray>(%fpp_count% bots)'
```

> **Note:** Player-relative placeholders (`%fpp_user_count%`, etc.) return `0` / `""` in the tab-list-format context because FPP uses a null player context for server-wide PAPI expansion.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `%fpp_count%` returns unparsed literal | PlaceholderAPI not installed or FPP failed to register - check console for `[FPP] PlaceholderAPI expansion registered` |
| `%fpp_count%` only shows local bots | Ensure `database.mode: NETWORK` and `database.enabled: true` - remote bots require NETWORK mode |
| `%fpp_network_count%` always `0` | Only non-zero in NETWORK mode with other servers sending `BOT_SPAWN` plugin messages |
| `%fpp_user_count%` always `0` | Requires online player context - not usable in server-wide contexts |
| `%fpp_max%` shows `∞` unexpectedly | `limits.max-bots: 0` means unlimited - set a number to get a numeric value |
| Per-world placeholder always `0` | Check world name - case-insensitive but spelling must match; use underscores for spaces |
| Config state shows stale value | Run `/fpp reload` - config state placeholders update immediately |
| `%fpp_pushable%` or `%fpp_damageable%` wrong | Edit `body.pushable` / `body.damageable` in config.yml then run `/fpp reload` |
| `%fpp_network%` always `off` | Only `on` when `database.enabled: true` and `database.mode: NETWORK` |
| `%fpp_server_id%` shows `default` | Set `database.server-id` in config.yml to a unique value per server |
| `%fpp_spawn_cooldown%` shows `0` | `spawn-cooldown` is `0` (disabled) by default - set a positive integer to enable |

---

| [◀ Fake Chat](Fake-Chat.md) | [🏠 Home](Home.md) | [FAQ ▶](FAQ.md) |

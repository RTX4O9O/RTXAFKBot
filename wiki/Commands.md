# Commands

All FPP commands go through the root command `/fpp` (aliases: `/fakeplayer`, `/fp`).  
Sub-commands are listed below. Tab-completion is available for all commands — only sub-commands the sender has permission to use will appear.

---

## Command Overview

| Command | Permission | Description |
|---------|-----------|-------------|
| [`/fpp help`](#fpp-help) | `fpp.help` (everyone) | Show the paginated help menu |
| [`/fpp spawn`](#fpp-spawn) | `fpp.spawn` / `fpp.user.spawn` | Spawn fake player(s) |
| [`/fpp delete`](#fpp-delete) | `fpp.delete` | Delete a bot or all bots |
| [`/fpp list`](#fpp-list) | `fpp.list` | List all active bots |
| [`/fpp info`](#fpp-info) | `fpp.info` / `fpp.user.info` | Query bot info / session history |
| [`/fpp tph`](#fpp-tph) | `fpp.user.tph` | Teleport your bot(s) to you |
| [`/fpp tp`](#fpp-tp) | `fpp.tp` | Teleport yourself to a bot |
| [`/fpp chat`](#fpp-chat) | `fpp.chat` | Toggle bot fake-chat on/off |
| [`/fpp swap`](#fpp-swap) | `fpp.swap` | Toggle the bot rotation system |
| [`/fpp reload`](#fpp-reload) | `fpp.reload` | Hot-reload all configs |

---

## /fpp help

```
/fpp help [page]
```

Displays a paginated help menu listing every command the sender has permission to use.  
The menu is **dynamic** — it only shows commands relevant to the viewer's permissions.  
Click the **◀ Previous** / **Next ▶** buttons at the bottom to navigate pages.

**Examples:**
```
/fpp help
/fpp help 2
```

---

## /fpp spawn

```
/fpp spawn [amount] [--name <botname>]
```

Spawns one or more fake players at the sender's current location.

### Tiers

| Tier | Permission | Amount | Custom Name |
|------|-----------|--------|-------------|
| **Admin** | `fpp.spawn` | Any amount up to global max | `--name` flag available |
| **User** | `fpp.user.spawn` | Up to personal bot limit (default: 1) | Not allowed — auto-named |

### Arguments

| Argument | Required | Description |
|----------|----------|-------------|
| `amount` | No | Number of bots to spawn (default: `1`) |
| `--name <botname>` | No | Custom name for the bot (admin + `fpp.spawn.name` only) |

### User Bot Naming

When a regular player (user-tier) spawns a bot, the name is auto-assigned:

- **1 bot** → `bot-{SpawnerName}`  
- **2+ bots** → `bot-{SpawnerName}-2`, `bot-{SpawnerName}-3`, etc.

### Tab-Complete Presets

Admin users see quick-count suggestions: `1`, `5`, `10`, `15`, `20`  
User-tier players only see `1`.

### Examples

```
/fpp spawn                        # spawn 1 bot with auto name
/fpp spawn 10                     # spawn 10 bots (admin)
/fpp spawn 1 --name Herobrine     # spawn 1 bot named Herobrine (admin)
/fpp spawn 5 --name Steve         # spawn 5 bots all named Steve (not recommended — names must be unique)
```

> Bots are spawned with a configurable random join delay per-bot to mimic organic joins.  
> See [`join-delay`](Configuration.md#join-delay) in config.

---

## /fpp delete

```
/fpp delete <name|all>
```

Disconnects and removes a fake player from the server.  
A leave message is broadcast (if enabled) and the bot's entity is removed.

### Arguments

| Argument | Description |
|----------|-------------|
| `<name>` | The name (or display name) of the bot to delete |
| `all` | Delete every active bot at once (requires `fpp.delete.all`) |

> When deleting multiple bots with `all`, each bot leaves with a configurable random delay.  
> See [`leave-delay`](Configuration.md#leave-delay) in config.

### Examples

```
/fpp delete Herobrine
/fpp delete "bot-El_Pepes"
/fpp delete all
```

Tab-complete suggests active bot names and `all` (if permitted).

---

## /fpp list

```
/fpp list
```

Displays all currently active bots in a formatted list. For each bot it shows:

- Display name  
- Current world and coordinates  
- Uptime (how long the bot has been active)  
- Spawner (who summoned the bot)

**Permission:** `fpp.list` (operator by default)

---

## /fpp info

```
/fpp info
/fpp info <botname>
/fpp info bot <name>
/fpp info spawner <playername>
```

Queries the database for bot session records.

### Admin tier (`fpp.info`)

| Usage | What it shows |
|-------|---------------|
| `/fpp info` | Overview of all live bots + database summary |
| `/fpp info <botname>` | Full session history for the named bot |
| `/fpp info bot <name>` | Same as above |
| `/fpp info spawner <player>` | All bots ever spawned by that player |

Shows: spawn time, despawn time, location, world, spawner, removal reason, uptime.

### User tier (`fpp.user.info`)

| Usage | What it shows |
|-------|---------------|
| `/fpp info` | Your own active bots |
| `/fpp info <botname>` | Limited info for a bot you own |

Shows: world, coordinates, uptime only. Cannot view other players' bots.

---

## /fpp tph

```
/fpp tph [botname]
```

Teleports the player's own bot(s) **to the player**.  
Useful for pulling your bot back if it ended up in a weird location.

- If you own **one bot**, `[botname]` is optional.  
- If you own **multiple bots**, you must specify the name.  
- Admins (`fpp.*`) can teleport **any** bot by name.

**Permission:** `fpp.user.tph` (included in `fpp.user.*`)

### Examples

```
/fpp tph
/fpp tph bot-El_Pepes
/fpp tph Herobrine
```

---

## /fpp tp

```
/fpp tp [botname]
```

Teleports **the player** to one of the active bots.  
Admin-only command — regular players cannot use this.

- If there is **one bot**, `[botname]` is optional.  
- If there are **multiple bots**, you must specify the name.

**Permission:** `fpp.tp` (operator by default)

### Examples

```
/fpp tp
/fpp tp Herobrine
```

---

## /fpp chat

```
/fpp chat [on|off|status]
```

Toggles the fake-chat system. Changes are written to `config.yml` immediately and survive `/fpp reload`.

| Argument | Effect |
|----------|--------|
| `on` | Enable fake chat |
| `off` | Disable fake chat |
| `status` | Show current state without changing it |
| *(none)* | Show current state |

**Permission:** `fpp.chat` (operator by default)

> See [Fake Chat](Fake-Chat.md) for detailed configuration.

---

## /fpp swap

```
/fpp swap [on|off|status]
```

Toggles the bot swap / rotation system. Changes are written to `config.yml` immediately.

| Argument | Effect |
|----------|--------|
| `on` | Enable bot rotation |
| `off` | Disable bot rotation |
| `status` | Show current state without changing it |
| *(none)* | Show current state |

**Permission:** `fpp.swap` (operator by default)

> See [Swap System](Swap-System.md) for detailed configuration.

---

## /fpp reload

```
/fpp reload
```

Hot-reloads all plugin configuration files without restarting the server:

- `config.yml`  
- `language/en.yml` (and any other active language file)  
- `bot-names.yml`  
- `bot-messages.yml`  

Active bots are **not** affected by a reload.  
The skin cache is cleared if `skin.clear-cache-on-reload: true` in config.

**Permission:** `fpp.reload` (operator by default)

---

## Command Aliases

| Alias | Resolves to |
|-------|-------------|
| `/fakeplayer` | `/fpp` |
| `/fp` | `/fpp` |


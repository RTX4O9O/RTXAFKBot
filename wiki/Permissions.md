# Permissions

FPP uses a two-tier permission model:

- **Admin tier** — full control over all bot management (default: OP)  
- **User tier** — limited spawn/info/tph access for regular players (default: everyone)

All nodes integrate natively with **LuckPerms**, PermissionsEx, GroupManager, and any other Bukkit-compatible permission plugin — no special setup required.

---

## Quick Reference

| Node | Default | Description |
|------|---------|-------------|
| `fpp.*` | OP | All FPP permissions (admin wildcard) |
| `fpp.user.*` | Everyone | All user-facing permissions |
| `fpp.help` | Everyone | View `/fpp help` |
| `fpp.user.spawn` | Everyone | Spawn bots (user-tier, limited) |
| `fpp.user.tph` | Everyone | `/fpp tph` — teleport own bot to self |
| `fpp.user.info` | Everyone | `/fpp info` — limited view of own bots |
| `fpp.bot.1` | Everyone | Personal bot limit: 1 (via `fpp.user.*`) |
| `fpp.bot.<num>` | false | Personal bot limit override |
| `fpp.spawn` | OP | Admin spawn (any amount, `--name` flag) |
| `fpp.spawn.multiple` | OP | Spawn more than 1 at a time |
| `fpp.spawn.name` | OP | Use `--name` flag |
| `fpp.delete` | OP | Delete a bot by name |
| `fpp.delete.all` | OP | Delete all bots at once |
| `fpp.list` | OP | View `/fpp list` |
| `fpp.chat` | OP | Toggle fake chat |
| `fpp.swap` | OP | Toggle swap/rotation system |
| `fpp.reload` | OP | Hot-reload configs |
| `fpp.info` | OP | Full admin database query |
| `fpp.tp` | OP | Teleport self to a bot |
| `fpp.bypass.maxbots` | OP | Bypass global max-bots cap |

---

## Wildcard Nodes

### `fpp.*` — Admin wildcard

Grants access to **every** FPP command and feature, including admin-only commands.  
Automatically includes `fpp.user.*` as a child.

```
/lp group admin permission set fpp.* true
```

### `fpp.user.*` — User wildcard

Grants all user-facing commands. This is the safe permission to give regular players.  
**Does NOT** include: delete, reload, swap, chat, list, full-info, or tp.

By default this is granted to **everyone** (`default: true` in `plugin.yml`).  
To restrict it to a specific group only:

```
# Revoke from default group, grant to member group
/lp group default permission set fpp.user.* false
/lp group member permission set fpp.user.* true
```

---

## Bot Limit Nodes — `fpp.bot.<num>`

The personal bot limit for a user-tier player is resolved by scanning `fpp.bot.1` through `fpp.bot.100` — the **highest matching node wins**.

| Node | Limit |
|------|-------|
| `fpp.bot.1` | 1 bot (default, included in `fpp.user.*`) |
| `fpp.bot.2` | 2 bots |
| `fpp.bot.3` | 3 bots |
| `fpp.bot.5` | 5 bots |
| `fpp.bot.10` | 10 bots |
| `fpp.bot.15` | 15 bots |
| `fpp.bot.20` | 20 bots |
| `fpp.bot.50` | 50 bots |
| `fpp.bot.100` | 100 bots |

You can assign **any number 1–100** even if not listed in `plugin.yml`:

```
/lp group vip permission set fpp.bot.5 true
/lp group mvp permission set fpp.bot.10 true
```

If the player has **no** `fpp.bot.*` node, the global fallback `fake-player.user-bot-limit` from `config.yml` is used (default: `1`).

---

## Admin Commands

| Node | Default | Command |
|------|---------|---------|
| `fpp.spawn` | OP | `/fpp spawn [amount] [--name <name>]` |
| `fpp.spawn.multiple` | OP | Spawn > 1 bot at a time |
| `fpp.spawn.name` | OP | Use the `--name` flag |
| `fpp.delete` | OP | `/fpp delete <name>` |
| `fpp.delete.all` | OP | `/fpp delete all` |
| `fpp.list` | OP | `/fpp list` |
| `fpp.chat` | OP | `/fpp chat [on|off|status]` |
| `fpp.swap` | OP | `/fpp swap [on|off|status]` |
| `fpp.reload` | OP | `/fpp reload` |
| `fpp.info` | OP | `/fpp info [bot|spawner] <name>` |
| `fpp.tp` | OP | `/fpp tp [botname]` |
| `fpp.bypass.maxbots` | OP | Bypass global `max-bots` cap |

---

## User Commands

| Node | Default | Command |
|------|---------|---------|
| `fpp.user.spawn` | Everyone | `/fpp spawn` (limited) |
| `fpp.user.tph` | Everyone | `/fpp tph [botname]` |
| `fpp.user.info` | Everyone | `/fpp info [botname]` (limited) |

### User-tier restrictions

- Cannot use `--name` — bots are always named `bot-{PlayerName}` or `bot-{PlayerName}-#`  
- Cannot spawn more than their personal bot limit at a time  
- `/fpp info` only shows their own bots with world, coordinates, and uptime — no session history  
- `/fpp tph` only moves bots they personally spawned  

---

## LuckPerms Setup Examples

### Typical multi-rank server

```bash
# Give all players user access with a 1-bot limit (this is already the default)
/lp group default permission set fpp.user.* true
/lp group default permission set fpp.bot.1 true

# VIP: 5 bots
/lp group vip permission set fpp.bot.5 true

# MVP: 10 bots
/lp group mvp permission set fpp.bot.10 true

# Staff: can delete bots and view list
/lp group staff permission set fpp.delete true
/lp group staff permission set fpp.list true

# Admin: full access
/lp group admin permission set fpp.* true
```

### Restrict regular players from spawning bots entirely

```bash
/lp group default permission set fpp.user.spawn false
```

### Allow only specific players to delete all bots

```bash
# All admins can delete individual bots but not "delete all"
/lp group admin permission set fpp.delete true
/lp group admin permission set fpp.delete.all false

# Only owner can delete all
/lp user Bill_Hub permission set fpp.delete.all true
```

---

## Inheritance Tree

```
fpp.*
├── fpp.help
├── fpp.spawn
│   ├── fpp.spawn.multiple
│   └── fpp.spawn.name
├── fpp.delete
│   └── fpp.delete.all
├── fpp.list
├── fpp.chat
├── fpp.swap
├── fpp.reload
├── fpp.info
├── fpp.tp
├── fpp.bypass.maxbots
└── fpp.user.*
    ├── fpp.user.spawn
    ├── fpp.user.tph
    ├── fpp.user.info
    └── fpp.bot.1
```

---

> **See also:** [PERMS.md](../PERMS.md) for the canonical flat permission list.


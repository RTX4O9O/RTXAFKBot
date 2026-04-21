# ⌨️ Commands

> **Complete FPP command reference - v1.6.6.2**  
> All commands use `/fpp` · aliases `/fakeplayer` and `/fp`

---

## 📋 Command Overview

| Command | Permission | Description |
|---------|------------|-------------|
| `/fpp help [page]` | *(everyone)* | Interactive help GUI (console gets text fallback) |
| `/fpp spawn [amount] [--name <name>]` | `fpp.spawn.user` / `fpp.spawn` | Spawn fake player(s) |
| `/fpp despawn <name\|all\|random [n]>` | `fpp.delete` | Remove bots |
| `/fpp list` | `fpp.list` | List local and remote bots |
| `/fpp info` | `fpp.info` / `fpp.info.user` | Query bot sessions and ownership |
| `/fpp inventory <bot>` | `fpp.inventory` | Open bot inventory GUI |
| `/fpp move <bot> <player>` | `fpp.move` | Follow / navigate to player (positional syntax) |
| `/fpp move <bot\|all> --to <player>` | `fpp.move` | Follow / navigate to player (canonical flag form) |
| `/fpp move <bot\|all> --wp <route> [--random]` | `fpp.move` | Follow a named waypoint route |
| `/fpp move <bot\|all> --roam [x,y,z] [radius]` | `fpp.move` | Autonomous random wander within a radius |
| `/fpp move <bot\|all> --stop` | `fpp.move` | Stop movement / patrol |
| `/fpp mine <bot>` | `fpp.mine` | Continuous mining |
| `/fpp mine <bot> once\|stop` | `fpp.mine` | One-shot or stop mining |
| `/fpp mine <bot> --pos1\|--pos2\|--start\|--status\|--stop` | `fpp.mine` | Area mining mode |
| `/fpp place <bot> [once\|stop]` | `fpp.place` | Continuous or one-shot block placing |
| `/fpp use <bot>` | `fpp.useitem` | Use / activate the block the bot is looking at |
| `/fpp storage <bot> ...` | `fpp.storage` | Manage supply containers for mine/place |
| `/fpp waypoint <route> ...` | `fpp.waypoint` | Manage named patrol routes (`add` auto-creates the route) |
| `/fpp xp <bot>` | `fpp.xp` | Transfer bot XP to yourself |
| `/fpp cmd <bot> ...` | `fpp.cmd` | Execute or store right-click commands |
| `/fpp rename <old> <new>` | `fpp.rename` / `fpp.rename.own` | Rename an active bot |
| `/fpp personality ...` | `fpp.personality` | Assign AI personalities to bots |
| `/fpp badword ...` | `fpp.badword` | Manage runtime badword list |
| `/fpp ping [<bot>] [--ping <ms>\|--random] [--count <n>]` | `fpp.ping` | Set simulated tab-list ping |
| `/fpp attack <bot> [--stop]` | `fpp.attack` | PvE attack — walk to sender, attack entities; `--mob` for stationary mob-targeting |
| `/fpp follow <bot\|all> <player>` | `fpp.follow` | Continuously follow an online player (persists across restarts) |
| `/fpp follow <bot\|all> --stop` | `fpp.follow` | Stop the bot's follow loop |
| `/fpp chat ...` | `fpp.chat` | Control fake chat globally or per-bot |
| `/fpp freeze ...` | `fpp.freeze` | Freeze or unfreeze bots |
| `/fpp swap ...` | `fpp.swap` | Session rotation controls |
| `/fpp peaks ...` | `fpp.peaks` | Peak-hours scheduler |
| `/fpp tp <name>` | `fpp.tp` | Teleport to a bot |
| `/fpp tph [name\|all\|@mine]` | `fpp.tph` | Teleport owned / target bots to you |
| `/fpp rank ...` | `fpp.rank` | Assign LuckPerms groups |
| `/fpp lpinfo [bot]` | `fpp.lpinfo` | LuckPerms diagnostics |
| `/fpp settings` | `fpp.settings` | Open the main settings GUI |
| `/fpp stats` | `fpp.stats` | Live stats panel |
| `/fpp migrate ...` | `fpp.migrate` | Backup, export, and migration tools |
| `/fpp sync ...` | `fpp.sync` | Config sync across proxy network |
| `/fpp alert <message>` | `fpp.alert` | Broadcast network alert |
| `/fpp reload` | `fpp.reload` | Reload config and subsystems |

---

## 🖱️ Bot Interaction Shortcuts

These happen in-world without typing a command:

- **Right-click bot** → opens inventory GUI
- **Right-click bot with stored RC command** → runs stored command instead of opening inventory
- **Shift + right-click bot** → opens `BotSettingGui` when `bot-interaction.shift-right-click-settings: true`

Related config:

```yaml
bot-interaction:
  right-click-enabled: true
  shift-right-click-settings: true
```

---

## 📖 Detailed Command Reference

### 🆘 `/fpp help`

```text
/fpp help [page]
```

Opens the paginated **54-slot help GUI** for players. Console senders get a text fallback.

- permission-filtered
- click-navigable
- one item per command with usage + permission info

---

### 🎭 `/fpp spawn`

```text
/fpp spawn [amount] [--name <name>] [--skin <skin>] [--group <group>]
/fpp spawn [amount] [world] [x y z] [--name <name>]
```

Spawn one or more bots.

**Permissions**
- `fpp.spawn.user` — spawn personal bots
- `fpp.spawn` — full admin spawning

**Related limits**
- `fpp.spawn.limit.<N>` — personal cap
- `fpp.bypass.maxbots` — bypass global cap
- `fpp.bypass.cooldown` — bypass spawn cooldown

**Examples**

```text
/fpp spawn
/fpp spawn 5
/fpp spawn --name Steve
/fpp spawn 3 world 100 64 200
```

---

### 🗑️ `/fpp despawn`

```text
/fpp despawn <name|all|random [n]>
```

Aliases: `delete`, `remove`

Remove one bot, all bots, or a random subset.

```text
/fpp despawn Steve
/fpp despawn all
/fpp despawn random 3
```

> ⚠️ **Startup safety guard:** `despawn all`, `despawn --random <n>`, and `despawn --num <n>` are **blocked** while bot persistence restoration is in progress at startup (the ~2–3 second restore window). A message is shown to the sender when this occurs. Single-bot despawn (`/fpp despawn <name>`) is unaffected.

Permission: `fpp.delete`

---

### 📋 `/fpp list`

```text
/fpp list
```

List active bots with uptime, world, and status.

In `NETWORK` mode it also shows **remote bots** from other proxy servers in a separate section.

Permission: `fpp.list`

---

### ℹ️ `/fpp info`

```text
/fpp info
/fpp info bot <name>
/fpp info spawner <name>
```

Query current bot ownership and historical session data.

**Permissions**
- `fpp.info` — full access
- `fpp.info.user` — own-bot view only

---

### 📦 `/fpp inventory`

```text
/fpp inventory <bot>
/fpp inv <bot>
```

Open the bot's **54-slot double-chest inventory GUI**.

Layout:
- rows 1-3 → main storage
- row 4 → hotbar
- row 5 → label bar
- row 6 → helmet / chest / leggings / boots / offhand

Equipment slots enforce item-type restrictions.

Permission: `fpp.inventory`

---

### 🧭 `/fpp move`

```text
/fpp move <bot> <player>
/fpp move <bot|all> --to <player>
/fpp move <bot|all> --wp <route> [--random]
/fpp move <bot|all> --roam [x,y,z] [radius]
/fpp move <bot|all> --stop
```

Shared A* navigation command.

#### Follow player mode
Makes the bot navigate to an online player. `--to <player>` is the canonical flag form; the old positional `<bot> <player>` syntax still works as a backward-compat fallback.

#### Roam mode (`--roam`)
Bot wanders continuously within a configurable radius (3–500 blocks) around a fixed center.
- If no coordinates are given, the bot's current location is used as the center
- Radius must be between 3 and 500 blocks
- Roam state persists across restarts (saved to `data/bot-tasks.yml` YAML — not in the DB task table)
- Stop with `--stop`

```text
/fpp move Steve --roam
/fpp move Steve --roam 100,64,200 50
/fpp move all --roam 0,64,0 100
```

#### Waypoint route mode
Makes the bot patrol a named route created with `/fpp waypoint`.

#### Stop mode
Stops the bot's active navigation, roam, or patrol without deleting the route.

**Move types used by pathfinding**
- `WALK`
- `ASCEND`
- `DESCEND`
- `PARKOUR`
- `BREAK`
- `PLACE`

**Important behavior**
- target-follow recalculates when target moves beyond `pathfinding.follow-recalc-distance`
- stuck detection forces jump + recalc
- patrols use `pathfinding.patrol-arrival-distance`
- roam uses `pathfinding.max-fall` to avoid unsafe drops

Permission: `fpp.move`

---

### ⛏️ `/fpp mine`

```text
/fpp mine <bot>
/fpp mine <bot> once
/fpp mine <bot> stop
/fpp mine stop

/fpp mine <bot> --pos1
/fpp mine <bot> --pos2
/fpp mine <bot> --start
/fpp mine <bot> --status
/fpp mine <bot> --stop
```

Mine the block the bot is looking at, or run a persistent **area mining** job.

#### Classic mode
- continuous mining
- `once` mines one block then stops
- `stop` cancels mining

#### Area mode
- `--pos1` / `--pos2` define cuboid corners
- `--start` begins mining the selected region continuously
- `--status` shows job info
- `--stop` cancels the area job

**Notes**
- survival mode uses progressive mining speed
- creative mode breaks instantly with cooldown
- tasks survive restart via DB/YAML task persistence
- area selections persist in `data/mine-selections.yml`
- can offload to nearby registered storage containers when inventory fills

Permission: `fpp.mine`

---

### 🏗️ `/fpp place`

```text
/fpp place <bot>
/fpp place <bot> once
/fpp place <bot> stop
/fpp place stop
```

Bot places blocks at its look target.

- continuous mode keeps placing
- `once` places one block and stops
- `stop` cancels place mode
- current classic place task survives restart via task persistence

Permission: `fpp.place`

---

### 🔘 `/fpp use`

```text
/fpp use <bot>
```

Makes the bot right-click / use the block it is looking at.

Useful for:
- chests / barrels
- buttons / levers
- crafting tables
- other interactable blocks

Permission: `fpp.useitem`

---

### 📦 `/fpp storage`

```text
/fpp storage <bot> <name>
/fpp storage <bot> --list
/fpp storage <bot> --remove <name>
/fpp storage <bot> --clear
```

Manage named supply containers for a bot.

Used by:
- `/fpp mine` for offloading mined items
- `/fpp place` for fetching building supplies

Permission: `fpp.storage`

---

### 🗺️ `/fpp waypoint`

```text
/fpp wp add <route>
/fpp wp remove <route> <index>
/fpp wp delete <route>
/fpp wp clear <route>
/fpp wp list [route]
/fpp wp create <route>        (optional — add auto-creates)
```

Alias: `/fpp waypoint`

Manage named routes used by `/fpp move --wp`.

> 💡 **Auto-create:** `/fpp wp add <route>` automatically creates the route if it doesn't exist yet. An in-chat tip is shown when this happens. The explicit `/fpp wp create <route>` command still works but is now optional.

Permission: `fpp.waypoint`

---

### ⭐ `/fpp xp`

```text
/fpp xp <bot>
```

Transfer the bot's total XP to yourself.

Notes:
- bot XP is cleared after transfer
- adds a short XP pickup cooldown to that bot
- blocked entirely if global/per-bot XP pickup is disabled

Permission: `fpp.xp`

---

### 💻 `/fpp cmd`

```text
/fpp cmd <bot> <command...>
/fpp cmd <bot> --add <command...>
/fpp cmd <bot> --clear
/fpp cmd <bot> --show
```

Dispatch a command or store a **right-click command** on the bot.

Stored RC command behavior:
- normal right-click runs the stored command
- shift-right-click can still open `BotSettingGui` if enabled

Permission: `fpp.cmd`

---

### 🔤 `/fpp rename`

```text
/fpp rename <old> <new>
```

Rename an active bot.

The rename flow fully preserves:
- inventory
- armor / offhand
- XP
- LuckPerms group
- chat settings
- AI personality
- frozen state
- stored right-click command

**Permissions**
- `fpp.rename` — rename any bot
- `fpp.rename.own` — rename only bots the sender spawned

---

### 🎭 `/fpp personality`

```text
/fpp personality list
/fpp personality reload
/fpp personality <bot> set <name>
/fpp personality <bot> reset
/fpp personality <bot> show
/fpp persona ...
```

Manage AI personality files.

- personalities live in `plugins/FakePlayerPlugin/personalities/`
- `set` assigns a file by name (without `.txt`)
- `reset` returns the bot to the configured default personality
- `reload` reloads the folder contents

Permission: `fpp.personality`

---

### 🚫 `/fpp badword`

```text
/fpp badword add <word>
/fpp badword remove <word>
/fpp badword list
/fpp badword reload
```

Manage the runtime badword filter list.

Used together with the `badword-filter` config section and `bad-words.yml`.

Permission: `fpp.badword`

---

### 💬 `/fpp chat`

```text
/fpp chat [on|off|status]
/fpp chat <bot> [on|off|say <msg>|tier <tier>|mute [seconds]|info]
/fpp chat all <on|off|tier <tier>|mute [seconds]>
```

Control fake chat globally or per bot.

Chat tiers:
- `quiet`
- `passive`
- `normal`
- `active`
- `chatty`
- `reset`

Permission: `fpp.chat`

---

### ❄️ `/fpp freeze`

```text
/fpp freeze <name|all> [on|off|toggle]
```

Freeze or unfreeze bot movement and AI.

Permission: `fpp.freeze`

---

### 🔄 `/fpp swap`

```text
/fpp swap [on|off|status|now <bot>|list|info <bot>]
```

Manage bot session rotation.

Important newer options reflected in config:
- `swap.min-online`
- `swap.retry-rejoin`
- `swap.retry-delay`

Permission: `fpp.swap`

---

### ⏰ `/fpp peaks`

```text
/fpp peaks [on|off|status|next|force|list|wake [name]|sleep <name>]
```

Manage the peak-hours scheduler.

Requires `swap.enabled: true`.

Permission: `fpp.peaks`

---

### 📍 `/fpp tp`

```text
/fpp tp <name>
```

Teleport yourself to a bot.

Permission: `fpp.tp`

---

### 🏠 `/fpp tph`

```text
/fpp tph [name|all|@mine]
```

Teleport owned or target bots to your location.

Permission: `fpp.tph`

---

### 👑 `/fpp rank`

```text
/fpp rank <bot> <group|clear>
/fpp rank random <group> [num|all]
/fpp rank list
```

Assign LuckPerms groups to bots without respawning them.

Permission: `fpp.rank`

---

### 🔗 `/fpp lpinfo`

```text
/fpp lpinfo [bot]
```

LuckPerms diagnostics.

Shows integration status plus current bot group / prefix / suffix / display-name information.

Permission: `fpp.lpinfo`

---

### ⚙️ `/fpp settings`

```text
/fpp settings
```

Open the main **3-row settings GUI**.

This is the global config GUI, not the per-bot settings chest.

> 💡 **Per-bot settings** (BotSettingGui) are opened by **shift+right-clicking any bot entity** in-world — also uses `fpp.settings` permission. Available to all players granted this permission (default: op).

Categories:
- General
- Body
- Chat
- Swap
- Peak Hours
- PvP
- Pathfinding

Permission: `fpp.settings`

---

### 📊 `/fpp stats`

```text
/fpp stats
```

Show live stats:
- bot count
- frozen bots
- DB totals
- TPS
- system/config state

Permission: `fpp.stats`

---

### 🔧 `/fpp migrate`

```text
/fpp migrate status
/fpp migrate backup
/fpp migrate backups
/fpp migrate config
/fpp migrate lang|names|messages
/fpp migrate db export [file]
/fpp migrate db merge <file>
/fpp migrate db tomysql
```

Migration, export, backup, and YAML sync tools.

Permission: `fpp.migrate`

---

### 🔗 `/fpp sync`

```text
/fpp sync push [file]
/fpp sync pull [file]
/fpp sync status [file]
/fpp sync check [file]
```

Push or pull config files across a proxy network.

Requires `NETWORK` mode + shared MySQL.

Permission: `fpp.sync`

---

### 📣 `/fpp alert`

```text
/fpp alert <message>
```

Broadcast an admin alert across the proxy network.

Permission: `fpp.alert`

---

### 🔃 `/fpp reload`

```text
/fpp reload
```

Reload config, language, names, messages, skin repository, LP display names, and live subsystems.

Permission: `fpp.reload`

---

### 📡 `/fpp ping`

```text
/fpp ping [<bot>]
/fpp ping [<bot>] --ping <ms>
/fpp ping [<bot>] --random
/fpp ping --random --count <n>
```

Set the simulated tab-list latency (ping bar) for one or all bots.

- No flag → shows the bot's current ping
- `--ping <ms>` → set a specific latency (0–9999 ms)
- `--random` → assign a random realistic value
- `--count <n>` → target N random bots at once

**Permissions**
- `fpp.ping` — view current ping
- `fpp.ping.set` — set a specific value with `--ping`
- `fpp.ping.random` — assign random distribution with `--random`
- `fpp.ping.bulk` — target multiple bots with `--count`

---

### ⚔ `/fpp attack`

```text
/fpp attack <bot>
/fpp attack <bot> --stop
/fpp attack <bot> --mob [--range <n>] [--type <mob>] [--priority nearest|lowest-health]
```

**Classic mode:** bot walks to the command sender's position and continuously attacks nearby entities.

**Mob mode (`--mob`):** stationary PvE auto-targeting — scans for nearby hostile mobs within `--range` blocks, smoothly rotates toward the best target, and attacks with proper weapon cooldowns. Re-targets every `attack-mob.retarget-interval` ticks. Never auto-targets players.

- Respects 1.9+ attack cooldown and item-specific cooldowns dynamically
- `--stop` cancels the attack loop

Permission: `fpp.attack`

---

### 🎯 `/fpp follow`

```text
/fpp follow <bot|all> <player>
/fpp follow <bot|all> --stop
```

Bot continuously follows an online player using `PathfindingService` (Owner `FOLLOW`).

- Path recalculates whenever the target moves >3.5 blocks (configurable via `pathfinding.follow-recalc-distance`) or every 60 ticks
- Arrival distance: 2.0 blocks; bot re-navigates 5 ticks after arrival to maintain continuous following
- `--stop` cancels the follow loop for one or all bots
- Persists across restarts — FOLLOW task type saved to `fpp_bot_tasks`; bot resumes following if the target is online after restart
- Respects `pathfinding.max-fall` — will not choose paths with unsafe drops

Permission: `fpp.follow`

---

## 🔐 Permission Quick Reference

```yaml
fpp.op               # admin wildcard
fpp.use              # user wildcard

# user-tier nodes
fpp.spawn.user
fpp.tph
fpp.xp
fpp.info.user
fpp.spawn.limit.<N>

# newer admin feature nodes
fpp.inventory
fpp.move
fpp.mine
fpp.place
fpp.storage
fpp.useitem
fpp.waypoint
fpp.rename
fpp.rename.own
fpp.personality
fpp.badword
fpp.ping
fpp.attack
fpp.follow
```

For the full permission list, see [Permissions](Permissions).

---

## 🚀 Example Workflows

### Basic server-population setup

```text
/fpp spawn 10
/fpp chat on
/fpp swap on
/fpp list
```

### Patrol route setup

```text
/fpp waypoint market add
/fpp waypoint market add
/fpp waypoint market list
/fpp move Steve --wp market
```

### Mining + storage setup

```text
/fpp storage Steve mainchest
/fpp mine Steve --pos1
/fpp mine Steve --pos2
/fpp mine Steve --start
```

### AI conversation setup

```text
/fpp personality list
/fpp personality Steve set default
/msg Steve yo
```

---

For config details, see [Configuration](Configuration). For permission setup, see [Permissions](Permissions).

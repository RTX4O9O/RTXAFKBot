# 🤖 Bot Behaviour

> **How FPP bots behave in the world, in chat, and across restarts**

---

## Overview

FPP bots are built around a real **NMS `ServerPlayer`** entity when physical bodies are enabled.

That means a bot can have:
- tab-list presence
- server-list / player-count presence
- chat presence
- a real in-world player body
- inventory, armor, offhand, XP, and position
- pathfinding and action tasks

This is not a mannequin stack or NPC armor-stand trick — it is a real player-like server entity.

---

## Bot Architecture

```text
FakePlayer
├── name / uuid / display name
├── chat state
├── LP group / display state
├── inventory / armor / offhand / XP
├── bodyless or physical body state
├── optional ServerPlayer body
└── behavior systems
    ├── head AI
    ├── swim AI
    ├── collision physics
    ├── fake chat
    ├── AI conversations
    ├── follow-target AI
    ├── PvE auto-attack AI
    ├── swap / peak-hours scheduling
    └── shared pathfinding + actions
```

---

## Physical Body

When `body.enabled: true`, each bot spawns as a full `ServerPlayer` body.

### What that means

- proper hitbox
- visible player model
- skin support
- item / XP pickup support
- damage / death behavior
- world position and chunk-loading behavior

Relevant config:

```yaml
body:
  enabled: true
  pushable: true
  damageable: true
  pick-up-items: true
  pick-up-xp: true
  drop-items-on-despawn: true
```

---

## In-World Interaction Shortcuts

### Right-click bot

Normal right-click does one of two things:

1. opens the bot inventory GUI, or
2. runs the bot's stored right-click command if one has been configured via `/fpp cmd --add`

### Shift + right-click bot

If enabled, shift-right-click opens the **per-bot settings GUI** (`BotSettingGui`).

Config:

```yaml
bot-interaction:
  right-click-enabled: true
  shift-right-click-settings: true
```

---

## Per-Bot Settings GUI (`BotSettingGui`)

This is different from the global `/fpp settings` GUI.

### Categories

- ⚙ **General** — freeze toggle, head-AI toggle, swim-AI toggle, chunk-load-radius, pick-up-items toggle, pick-up-xp toggle, rename action
- 💬 **Chat** — chat enabled/disabled, tier, AI personality
- ⚔ **PvP** — Live per-bot PvE settings: `pveEnabled` toggle, `pveRange` (scan radius), `pvePriority` (`nearest` / `lowest-health`), `pveMobTypes` (comma-separated entity-type whitelist — empty = all hostile mobs); coming-soon overrides for PvP combat modes
- 📋 **Cmds** — set / clear stored RC command
- ⚠ **Danger** — delete bot

It is designed for quick per-bot tuning without command spam.

---

## Command Blocking and Protection

FPP blocks bots from behaving like real players in places where that would break other systems.

### Command blocking

Bots are protected from normal command execution paths used by other plugins.

Important nuance:
- normal command execution by bots is blocked
- `/fpp cmd` intentionally uses a safe dispatch path so admins can still trigger bot actions on purpose

### Spawn protection

Bots receive a short spawn grace period so lobby/spawn plugins do not immediately teleport them away from the intended spawn location.

---

## Head AI

Bots can look at nearby players.

```yaml
head-ai:
  enabled: true
  look-range: 8.0
  turn-speed: 0.3
  tick-rate: 3
```

What it does:
- scans for nearby players
- picks a target in range
- rotates smoothly rather than snapping instantly

This is disabled while certain action locks are active so the bot does not turn away during mining / placing / using.

---

## Swim AI

```yaml
swim-ai:
  enabled: true
```

When enabled, bots automatically swim upward in water or lava like a player holding jump.

Per-bot override: each bot has its own `swimAiEnabled` toggle (initialised from the global config at spawn). Toggle it in `BotSettingGui` General tab or programmatically via `fp.setSwimAiEnabled(boolean)`.

---

## Chunk Loading

```yaml
chunk-loading:
  enabled: true
  radius: "auto"
  update-interval: 20
```

Per-bot override: each bot has its own `chunkLoadRadius` field:
- `-1` = follow global `chunk-loading.radius`
- `0` = disable chunk loading for this bot
- `1-N` = fixed chunk radius (capped at global max)

Set it in `BotSettingGui` General tab (numeric chat prompt) or programmatically via `fp.setChunkLoadRadius(int)`.

---

## Collision and Physics

Relevant config:

```yaml
collision:
  walk-radius: 0.85
  walk-strength: 0.22
  hit-strength: 0.45
  hit-max-horizontal-speed: 0.80
  bot-radius: 0.90
  bot-strength: 0.14
  max-horizontal-speed: 0.30
```

Behavior includes:
- player pushing a bot by walking into it
- attack knockback
- bot-vs-bot separation to avoid clustering

If `body.pushable: false`, bots become effectively immovable.

---

## Damage, Death, and Respawn

Relevant config:

```yaml
combat:
  max-health: 20.0
  hurt-sound: true

death:
  respawn-on-death: false
  respawn-delay: 15
  suppress-drops: false
```

Behavior:
- bots can take environmental and combat damage when `body.damageable: true`
- they can die like a player
- they can optionally respawn after a delay
- item/XP death drops can be suppressed

WorldGuard integration can also prevent player-sourced damage in no-PvP regions.

---

## Item / XP Pickup

Global defaults come from config:

```yaml
body:
  pick-up-items: true
  pick-up-xp: true
```

Per-bot overrides are available in `BotSettingGui`.

Important behavior:
- turning item pickup off for a specific bot can drop its held inventory to the ground immediately
- turning XP pickup off for a specific bot can drop stored XP to the ground immediately
- XP collection also interacts with `/fpp xp` cooldown logic

---

## Shared Pathfinding and Action Engine

Navigation is now centralized through **`PathfindingService`**.

Used by:
- `/fpp move`
- `/fpp mine`
- `/fpp place`
- `/fpp use`
- waypoint patrols

### Supported move types

- `WALK`
- `ASCEND`
- `DESCEND`
- `PARKOUR`
- `BREAK`
- `PLACE`

### Shared helpers

- `BotNavUtil` — stand positions, facing, action-location checks, block use helpers
- `StorageInteractionHelper` — lock → open → transfer → unlock flow for storage containers

### Action lock handoff

Some navigation flows use an atomic "arrive and lock" handoff so the bot does not drift or rotate away in the tick between movement completion and action start.

---

## Movement Modes

### Continuously follow a player

```text
/fpp follow <bot|all> <player>
/fpp follow <bot|all> --stop
```

The bot continuously follows an online player using `PathfindingService` (Owner `FOLLOW`).

- Path recalculates whenever the target moves >3.5 blocks (configurable via `pathfinding.follow-recalc-distance`) or every 60 ticks
- Arrival distance: 2.0 blocks; re-navigates 5 ticks after arrival for smooth continuous following
- Respects `pathfinding.max-fall` — will not choose paths with unsafe drops
- FOLLOW task persisted in `fpp_bot_tasks` — bot resumes following after restart if the target is online
- Permission: `fpp.follow`

### Follow a player (one-shot / navigate-to)

```text
/fpp move <bot> <player>
/fpp move <bot|all> --to <player>
```

The bot navigates to the target player. `--to` is the canonical flag form; the positional syntax still works.

> **Tip:** Use `/fpp follow` when you want the bot to keep following indefinitely and survive restarts. Use `/fpp move` for one-shot navigation to a player position where the bot should stop on arrival.

### Roam mode (autonomous random wander)

```text
/fpp move <bot|all> --roam [x,y,z] [radius]
/fpp move <bot|all> --stop
```

The bot wanders continuously within a fixed radius (3–500 blocks) around a center point.

- If no coordinates are given, the bot's current position becomes the center
- Roam state persists across restarts via `data/bot-tasks.yml` (YAML-only; not stored in the DB task table)
- Respects `pathfinding.max-fall` — will not choose paths with unsafe drops

### Patrol a waypoint route

```text
/fpp move <bot> --wp <route>
```

The bot walks a named route built with `/fpp waypoint`.

No prior `/fpp wp create` step needed — `/fpp wp add <route>` creates the route automatically on first use.

### Stop movement

```text
/fpp move <bot> --stop
```

---

## Mining, Placing, Using, and Storage

### Mining

Bots can mine a looked-at block continuously or in a cuboid area.

Area mining supports:
- `--pos1`
- `--pos2`
- `--start`
- `--status`
- `--stop`

### Placing

Bots can place blocks continuously or once.

### Using

Bots can right-click / activate the block they are looking at.

### Storage integration

Registered storage containers can be used for:
- depositing mined items
- fetching placement materials

---

## Task Persistence

Active tasks now survive restart.

This includes:
- `MINE`
- `USE`
- `PLACE`
- `PATROL`
- `FOLLOW` — bot resumes following the last target player if they are online after restart

Persistence source:
- DB: `fpp_bot_tasks`
- YAML fallback: `data/bot-tasks.yml`

That means a bot can restart and continue:
- a mine job
- a place job
- a use job
- a waypoint patrol
- following a specific player

---

## Fake Chat Behavior

Bots can chat autonomously using:
- random intervals
- activity tiers
- burst messages
- mention replies
- bot-to-bot conversations
- event-triggered reactions
- player-chat reactions

See [Fake-Chat](Fake-Chat).

---

## AI Conversations

Separate from fake chat, bots can reply privately to:
- `/msg`
- `/tell`
- `/whisper`

These use:
- `ai-conversations.*` config
- provider keys in `secrets.yml`
- default / custom personality files from `personalities/`

Per-bot personality assignment:

```text
/fpp personality <bot> set <name>
```

---

## Rename Behavior

Bots can be renamed live with:

```text
/fpp rename <old> <new>
```

The rename system fully preserves important state and suppresses fake join/leave spam during the rename lifecycle.

Preserved state includes:
- inventory
- XP
- LuckPerms group
- chat settings
- AI personality
- stored command
- frozen state

---

## Swap and Peak-Hours Behavior

### Swap system

Bots can rotate out after a configurable session and come back later.

Important config keys:
- `swap.min-online`
- `swap.retry-rejoin`
- `swap.retry-delay`
- `swap.farewell-chat`
- `swap.greeting-chat`

### Peak hours

Peak-hours scales the number of active AFK bots based on real-world time windows.

Important notes:
- requires `swap.enabled: true`
- only AFK bots are managed
- sleeping state is crash-safe when DB is enabled

---

## Performance Notes

For large bot counts, the biggest behavior-related cost centers are:
- chunk loading
- pathfinding
- chat/event systems
- frequent position syncs

Relevant config:

```yaml
performance:
  position-sync-distance: 128.0
```

Tips:
- reduce bot counts first
- disable systems you do not need
- keep chunk loading on `auto` unless you need a fixed radius
- use freezes for idle bots

---

## Troubleshooting

### Bot does not rotate its head

Check:
- `head-ai.enabled: true`
- `head-ai.look-range` is high enough
- the bot is not frozen or action-locked

### Shift-right-click does nothing

Check:
- `bot-interaction.right-click-enabled: true`
- `bot-interaction.shift-right-click-settings: true`
- you are actually sneaking

### Bot does not continue task after restart

Check:
- `persistence.enabled: true`
- DB is available, or YAML fallback files are writable
- task is one of the persisted task types (mine/use/place/patrol)

### Bot is not reacting in DMs

Check:
- `ai-conversations.enabled: true`
- a valid provider key is in `secrets.yml`
- the personality file exists and was reloaded

---

## Related Pages

- [Commands](Commands)
- [Configuration](Configuration)
- [Fake-Chat](Fake-Chat)
- [Swap-System](Swap-System)
- [Peak-Hours](Peak-Hours)
- [Skin-System](Skin-System)

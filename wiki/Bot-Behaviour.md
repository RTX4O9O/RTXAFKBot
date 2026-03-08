# Bot Behaviour

This page covers all the ways fake player bots behave in the world — their physical body, AI, combat, death, and interaction with chunks.

---

## Physical Body (Mannequin)

Each bot is represented in the world by a **Minecraft Mannequin** entity.  
The Mannequin is a player-shaped entity with a proper hitbox and physics.

Controlled by:
```yaml
fake-player:
  spawn-body: true   # false = tab-list/messages only, no entity
```

When `spawn-body: false`, bots appear only in the tab list and join/leave messages with no visible entity.

### What the Mannequin Does

- Appears as a player-shaped entity in the world
- Has a full player hitbox
- Can be hit, pushed, and damaged
- Can be killed (triggering death/respawn logic)
- Has its head tracked toward the nearest player (see Head AI)
- Loads the surrounding chunks like a real player

### Nametag

Each bot's Mannequin has **no visible vanilla nametag**. Instead, a separate invisible **ArmorStand** with a custom name is used as the nametag, positioned above the bot's head. This allows:
- Formatted MiniMessage names (colours, styles)
- The tag to move with the bot
- Clean removal when the bot is deleted

---

## Head AI

```yaml
fake-player:
  head-ai:
    look-range: 8.0
    turn-speed: 0.3
```

Bots track the nearest real player within `look-range` blocks and rotate their head to face them.

| Setting | Description |
|---------|-------------|
| `look-range` | Detection radius in blocks. `0` disables head tracking entirely. |
| `turn-speed` | Interpolation speed (0.0–1.0). `1.0` snaps instantly, `0.1` is very slow and smooth. |

The head rotates every tick using smooth interpolation — it does not snap.  
The Mannequin's **body** follows the head rotation automatically.

---

## Combat & Damage

Bots can be hit and take damage from players, mobs, and the environment.

```yaml
fake-player:
  combat:
    max-health: 20.0
    hurt-sound: true
```

| Setting | Description |
|---------|-------------|
| `max-health` | Starting health (default player max = `20.0`). |
| `hurt-sound` | Play `entity.player.hurt` sound when a bot is hit. |

### Kill Message

```yaml
fake-player:
  messages:
    kill-message: false
```

When `true`, a vanilla-style kill message is broadcast when a player kills a bot:  
`El_Pepes was slain by Steve`

---

## Death & Respawn

```yaml
fake-player:
  death:
    respawn-on-death: false
    respawn-delay: 60
    suppress-drops: true
```

| Setting | Value | Behaviour |
|---------|-------|-----------|
| `respawn-on-death` | `true` | Bot respawns at its last known location after dying |
| `respawn-on-death` | `false` | Bot permanently leaves the server on death |
| `respawn-delay` | ticks | How long to wait before respawning (20 = 1 second) |
| `suppress-drops` | `true` | Prevents any item drops on death (recommended) |

When a bot is killed:
1. A leave message is broadcast (if `messages.leave-message: true`)
2. The entity is removed
3. If `respawn-on-death: true`, after `respawn-delay` ticks the bot rejoins — a new join message is broadcast and a new Mannequin spawns at the last saved position

---

## Environmental Protection

Bots are immune to the following environmental hazards that would otherwise kill or transform the Mannequin entity:

- **Sunlight fire** — prevented (bots don't burn in daylight)
- **Drowning** — prevented (bots don't drown or convert to Drowned)
- **Zombie conversion** — prevented (Mannequin is not a zombie)
- **Entity AI aggression** — Villagers and passive mobs do not treat bots as hostile

---

## Chunk Loading

```yaml
fake-player:
  chunk-loading:
    enabled: true
    radius: 6
```

When enabled, each bot keeps the chunks around it loaded exactly as a real player would:
- Mobs spawn naturally in the area
- Redstone circuits tick
- Crops and plants grow
- Chunk unloading is prevented while the bot is present

`radius` should not exceed your server's `view-distance` setting in `server.properties`.

---

## Push & Collision

```yaml
fake-player:
  collision:
    walk-radius: 0.85
    walk-strength: 0.22
    max-horizontal-speed: 0.30
    hit-strength: 0.45
    bot-radius: 0.90
    bot-strength: 0.14
```

Bots react to being pushed:

| Interaction | Setting | Description |
|------------|---------|-------------|
| Player walks into bot | `walk-radius` / `walk-strength` | Player is too close → bot is nudged sideways |
| Player punches bot | `hit-strength` | Larger impulse applied on hit |
| Bot bumps another bot | `bot-radius` / `bot-strength` | Bots push each other apart |
| Maximum speed | `max-horizontal-speed` | Caps how fast a bot can be pushed horizontally |

All push forces are horizontal only — bots cannot be launched into the air by walking or punching.

---

## Server List Player Count

When `spawn-body: true`, bots are counted in the server list player count, making the server appear more populated to players browsing the server list.

This is handled automatically via a packet-level hook — no extra configuration needed.


# 🤖 Bot Behaviour

> **Understanding Bot Physics, AI, and Interactions**  
> **Complete Guide to Mannequin Entities and Bot Intelligence**

---

## 🎯 Overview

FPP bots are sophisticated entities that combine **realistic tab list presence** with **physical world interaction**. Each bot consists of multiple components working together to create a seamless fake player experience.

### 🏗️ **Bot Architecture**

```
🎭 Fake Player (Bot)
├── 📊 Tab List Entry     # Shows in player list
├── 💬 Chat Presence      # Can send messages  
├── 🏃 Physical Body      # Mannequin entity (optional)
│   ├── 🎨 Skin Display   # Visual appearance
│   ├── 👀 Head AI        # Head rotation tracking
│   ├── 🥊 Combat System  # Damage and death
│   └── 📍 Physics       # Movement and collision
└── 🧠 AI Systems        # Behavior and intelligence
    ├── 💭 Chat AI        # Message broadcasting
    ├── 🔄 Swap AI        # Player replacement
    └── 📡 Chunk Loading  # World presence
```

---

## 🛡️ **Bot Protection System** *(v1.5.6+)*

FPP includes automatic protection systems to prevent common issues with plugin interference and bot abuse.

### 🚫 **Command Blocking**

**Automatic 4-Layer Protection**

Bots are **completely command-proof** — they cannot execute commands from any source:

```
Layer 1: LOWEST Priority   → Catch commands first
Layer 2: HIGHEST Priority  → Safety net for edge cases
Layer 3: MONITOR Priority  → Final safeguard, re-cancel if needed
Layer 4: CommandSend Event → Clear command suggestions
```

**Protected Against:**
- ✅ First-join command plugins (e.g., giving starter kits to bots)
- ✅ Auto-command schedulers running commands on bots
- ✅ Permission-based command executors
- ✅ `Player.performCommand()` calls from other plugins
- ✅ `Bukkit.dispatchCommand()` calls
- ✅ Command suggestions and tab-completion

**Works Automatically:**
- ❌ No configuration needed
- ❌ No permissions needed
- ✅ Completely transparent
- ✅ Zero performance impact

**Debug Logging:**

```yaml
logging:
  debug:
    nms: true  # Shows command blocking in action
```

**Console Output:**
```
[FPP] BotCommandBlocker: blocked command (LOWEST) for Bot123: /give Bot123 diamond_sword
[FPP] BotCommandBlocker: cleared command suggestions for Bot123
```

---

### 🏠 **Lobby Spawn Protection**

**5-Tick Grace Period**

Bots are **protected from lobby plugin teleports** during their initial spawn (250ms):

**What It Blocks:**
- ✅ EssentialsX spawn-on-join
- ✅ Multiverse respawn anchors
- ✅ Custom lobby teleport plugins
- ✅ Any `PlayerTeleportEvent` with PLUGIN or UNKNOWN cause

**What It Allows:**
- ✅ Admin commands (`/tp`, `/fpp tp`, `/fpp tph`)
- ✅ Manual teleports
- ✅ Teleports after the grace period expires

**How It Works:**

```
1. Bot spawns at player's location
2. Protection activated (5 ticks / 250ms)
3. Lobby plugins' teleport attempts are blocked
4. Protection expires after 5 ticks
5. Bot remains at correct location
```

**Debug Logging:**

```yaml
logging:
  debug:
    nms: true  # Shows spawn protection in action
```

**Console Output:**
```
[FPP] BotSpawnProtection: protecting Bot123 from teleports for 5 ticks
[FPP] BotSpawnProtection: blocked PLUGIN teleport for Bot123 from world (100,64,200) to lobby (0,100,0)
[FPP] BotSpawnProtection: removed protection for Bot123
```

---

### 🏃 **Physical Bodies**

Each bot is a full **NMS ServerPlayer** entity — not a Mannequin. It has a real player model, hitbox, and physics.

### ⚙️ **Body Configuration**

```yaml
body:
  enabled: true      # Spawn a visible entity in the world
  pushable: true     # Players can push bots
  damageable: true   # Bots take damage and can die
```

Both `pushable` and `damageable` are **live-reloadable** via `/fpp reload`.

**Head AI:**
```yaml
head-ai:
  enabled: true
  look-range: 8.0   # Detection radius in blocks
  turn-speed: 0.3   # 0.0 = frozen, 1.0 = instant snap
```

**Swim AI:**
```yaml
swim-ai:
  enabled: true   # Bots swim upward in water/lava; false = bots sink
```

When `swim-ai.enabled: true`, bots automatically hold jump while submerged — mimicking a real player pressing spacebar in water or lava.

### 🎨 **Visual Appearance**

- Skins from the [Skin System](Skin-System.md)
- Display name in configurable format with LuckPerms prefix/suffix
- Full color code and MiniMessage formatting support

---

## 👀 **Head AI System**

### 🧠 **Intelligent Head Tracking**

Bots smoothly rotate to look at the nearest player within a configurable range.

**Configuration (config.yml):**
```yaml
head-ai:
  enabled: true
  look-range: 8.0   # Detection radius in blocks
  turn-speed: 0.3   # Rotation smoothing (0.0 = frozen, 1.0 = instant snap)
```

### 🎯 **Tracking Behavior**

**Target Priority:**
1. **Closest player** within range
2. **Smooth rotation** — no instant snapping; `turn-speed` controls how fast the head turns each tick

**Visual Effect:**
- Bot head follows nearby players naturally
- Creates an impression of awareness and engagement
- Set `look-range: 0` to disable head tracking entirely

---

## 🥊 **Combat & Damage System**

### ⚔️ **Damage Mechanics**

**Damage Configuration (config.yml):**
```yaml
body:
  damageable: true   # Bots can take damage (hot-reloadable)

combat:
  max-health: 20.0   # Health points (20.0 = standard player health)
  hurt-sound: true   # Play hurt sound when bot takes damage
```

### 💀 **Death System**

**When a Bot Dies:**
1. **Death Animation** — Plays death animation and sound
2. **Entity Cleanup** — Bot entity is removed from the world
3. **Tab List Update** — Bot disappears from the player list
4. **Event Logging** — Records death in database (if enabled)

**Death Sources:**
- ✅ Player attacks (sword, bow, etc.)
- ✅ Environmental damage (lava, fall damage, drowning)
- ✅ Entity damage (monsters, explosions)
- ❌ Plugin damage (when `damageable: false`)

**Respawn Options:**
```yaml
death:
  respawn-on-death: false   # Respawn bot instead of removing it on death
  respawn-delay: 60         # Ticks before respawn — 20 ticks = 1 second  (60 = 3 s · 100 = 5 s)
  suppress-drops: true      # Prevent item drops on death
```

---

## 🎾 **Physics & Collision**

### 🏃 **Movement Physics**

Bots support **realistic physics simulation** including collision, pushing, and movement.

**Push Configuration (config.yml):**
```yaml
body:
  pushable: true   # Allow players/entities to push bots (hot-reloadable)

collision:
  walk-radius: 0.85           # Player walk-into activation distance
  walk-strength: 0.22         # Push force when walking into a bot
  hit-strength: 0.45          # Knockback force when hitting a bot
  hit-max-horizontal-speed: 0.80  # Max horizontal speed for hit/explosion knockback
  bot-radius: 0.90            # Bot-vs-bot separation radius
  bot-strength: 0.14          # Separation force between bots
  max-horizontal-speed: 0.30  # Push speed cap for walk/separation sources
```

When `body.pushable: false`, all collision physics are disabled and bots become completely immovable.

### 🎯 **Collision Types**

**1. Player Walks Into Bot**
- **Trigger:** Player gets within `walk-radius`
- **Effect:** Bot is gently pushed aside with `walk-strength`

**2. Player Hits Bot**
- **Trigger:** Player attacks bot (punch, weapon)
- **Effect:** Stronger push with `hit-strength`

**3. Bot Bumps Bot**
- **Trigger:** Two bots get within `bot-radius` of each other
- **Effect:** Bots push apart with `bot-strength` — prevents clustering

---

## 📡 **Chunk Loading System**

### 🌍 **World Presence**

Bots keep chunks loaded around their location exactly like a real player — mobs spawn, redstone ticks, and crops grow inside the loaded radius.

**Configuration (config.yml):**
```yaml
chunk-loading:
  enabled: true
  radius: 0            # 0 = match server simulation-distance
  update-interval: 20  # Ticks between position checks (20 = 1 s)
```

### 📊 **Chunk Management**

- **Load on spawn** — Bot loads surrounding chunks when created
- **Maintain presence** — Keeps chunks loaded while bot exists
- **Smart unloading** — Releases chunks when bot is removed

Set `chunk-loading.enabled: false` if chunk loading is causing performance issues.

---

## 💭 **Chat AI System**

### 🗨️ **Intelligent Messaging**

Bots send random chat messages from `bot-messages.yml` at random intervals. See [Fake Chat](Fake-Chat.md) for full documentation.

**Basic Configuration (config.yml):**
```yaml
fake-chat:
  enabled: false
  require-player-online: true
  chance: 0.75
  interval:
    min: 5
    max: 10
  typing-delay: true        # Simulate typing pause before messages
  activity-variation: true  # Random per-bot chat frequency tier
  stagger-interval: 3       # Min seconds gap between any two bots chatting
```

**Chat Features:**
- **Random selection** — Messages chosen from `bot-messages.yml` randomly
- **Timing variation** — Per-bot independent intervals prevent predictable patterns
- **LuckPerms integration** — bots are real NMS entities; LP detects them as online players and applies prefix/suffix automatically
- **Full color support** — MiniMessage and legacy `&` codes
- **Event reactions** — Bots react to player joins, leaves, and deaths
- **Mention replies** — Bots reply when a player says their name in chat

---

## 🔄 **Swap AI System**

### 🎭 **Session Rotation**

The Swap System periodically rotates bots — each bot leaves after a configurable session and rejoins with a fresh name. See [Swap System](Swap-System.md) for full documentation.

**Configuration (config.yml):**
```yaml
swap:
  enabled: false
  session:
    min: 60    # Minimum session duration (seconds)
    max: 300   # Maximum session duration (seconds)
  absence:
    min: 30    # Minimum offline gap (seconds)
    max: 120   # Maximum offline gap (seconds)
  max-swapped-out: 0        # Max bots offline simultaneously (0 = unlimited)
  farewell-chat: true       # Bot says goodbye before leaving
  greeting-chat: true       # Bot says hi when rejoining
  same-name-on-rejoin: true # Reuse the same name if available
```

Toggle live with `/fpp swap` (bare command toggles on/off, just like `/fpp chat`).

### 🧠 **Personality Archetypes**

Each bot gets a random personality multiplier when swap starts:

| Archetype | Session Modifier |
|-----------|-----------------|
| **quiet** | 2.0× — stays extended periods |
| **passive** | 1.4× — below-average activity |
| **normal** | 1.0× — typical session |
| **active** | 0.7× — leaves more often |
| **chatty** | 0.5× — quick popper |

---

## 📊 **Performance & Optimization**

### ⚡ **System Performance**

**Bot Limits:**
```yaml
limits:
  max-bots: 1000   # Global cap (0 = unlimited)
```

Monitor live with `/fpp stats` or `/fpp stats --detailed`.

### 📈 **Optimization Strategies**

1. **Start small** — Begin with 5–10 bots and increase gradually
2. **Disable unused systems** — Turn off chunk-loading, head-ai, or physics if not needed
3. **Tune intervals** — Increase `chunk-loading.update-interval` and `head-ai.turn-speed` thresholds
4. **Use `/fpp freeze`** — Freeze idle bots to stop their AI ticks
5. **Monitor TPS** — Use `/fpp stats` to watch for performance impact

---

## 🎯 **Use Cases & Examples**

### 🏢 **Server Population Management**

Maintain active appearance during off-peak hours:
```yaml
body:
  enabled: true
  pushable: false    # Prevent griefing
  damageable: false  # Immortal population bots
fake-chat:
  enabled: true
  interval:
    min: 120
    max: 600
swap:
  enabled: true
  session: { min: 300, max: 1800 }
  absence: { min: 30, max: 120 }
```

### 🎮 **Minigame Lobby NPCs**

Static, interactive NPCs for game lobbies:
```yaml
body:
  enabled: true
  pushable: false
  damageable: false
head-ai:
  enabled: true
  look-range: 5.0
chunk-loading:
  enabled: true
  radius: 1
```

### 🏰 **Roleplay Town Inhabitants**

Realistic townsfolk with chat activity:
```yaml
fake-chat:
  enabled: true
  interval: { min: 60, max: 300 }
  activity-variation: true
head-ai:
  enabled: true
  look-range: 6.0
body:
  pushable: true
```

---

## 🔍 **Troubleshooting**

### ❌ **Common Issues**

**Bots not rotating heads:**
- ✅ Check `head-ai.enabled: true`
- ✅ Verify `head-ai.look-range` covers the player distance
- ✅ Ensure `body.enabled: true`

**Performance problems:**
- ✅ Reduce `limits.max-bots`
- ✅ Set `chunk-loading.enabled: false`
- ✅ Increase `chunk-loading.update-interval`
- ✅ Monitor with `/fpp stats --detailed`

**Physics not working:**
- ✅ Verify `body.pushable: true`
- ✅ Check `collision.walk-radius` and `collision.walk-strength` values
- ✅ Ensure physical bodies are enabled (`body.enabled: true`)

### 🛠️ **Diagnostic Commands**

```
/fpp stats --detailed    # Performance and behavior stats
/fpp info <botname>      # Individual bot status
/fpp freeze <botname>    # Pause a specific bot's AI
/fpp list --frozen       # Check frozen bot status
```

---

## 📚 **Related Documentation**

- **[Skin System](Skin-System.md)** — Bot visual appearance
- **[Fake Chat](Fake-Chat.md)** — Chat AI configuration
- **[Swap System](Swap-System.md)** — Session rotation details
- **[Configuration](Configuration.md)** — All behaviour settings
- **[FAQ](FAQ.md#performance-issues)** — Optimization tips

---

**🤖 Master bot behaviour to create the most realistic fake players!**

# Configuration

FPP is configured through `plugins/FakePlayerPlugin/config.yml`.  
All changes take effect immediately after running `/fpp reload` — no server restart required.

---

## Sections at a Glance

| Section | Purpose |
|---------|---------|
| [`language`](#language) | Active language file |
| [`debug`](#debug) | Verbose console logging |
| [`logging.debug.*`](#debug) | Per-subsystem debug toggles (startup, nms, packets, luckperms, network, config-sync, skin, database) |
| [`bot-name`](#bot-display-names) | Admin/user format templates and tab-list format |
| [`skin`](#skin) | Skin system mode (`auto` / `custom` / `off`) |
| [`body`](#body) | Physical entity, pushable & damageable toggles |
| [`persistence`](#persistence) | Save/restore bots across restarts |
| [`join-delay` / `leave-delay`](#join-delay) | Staggered join/leave timing |
| [`combat`](#combat) | Health and hurt sounds |
| [`death`](#death--respawn) | Respawn or leave on death |
| [`messages`](#messages) | Join/leave/kill message toggles |
| [`chunk-loading`](#chunk-loading) | Keep chunks loaded like a real player |
| [`head-ai`](#head-ai) | Head-tracking AI |
| [`swim-ai`](#swim-ai) | Automatic swimming in water/lava |
| [`collision`](#collision--push) | Push physics |
| [`swap`](#swap-system) | Bot session rotation settings |
| [`fake-chat`](#fake-chat) | Bot chat AI + message format |
| [`tab-list`](#tab-list) | Tab-list header/footer and bot visibility toggle |
| [`database`](#database) | SQLite / MySQL storage |
| [`config-sync`](#config-sync) | Cross-server config push/pull mode |

---

## language

```yaml
language: en
```

The filename (without `.yml`) of the language file inside `plugins/FakePlayerPlugin/language/`.  
Default is `en`, which maps to `language/en.yml`.

To add a new language, copy `en.yml`, translate it, and set this value to the new file's name.

---

## debug

```yaml
debug: false  # Legacy master switch - enables all categories below
```

When `true`, FPP prints verbose diagnostic messages to the console — useful for bug reports and development.  
**Disable in production** — it is very noisy.

### Granular Debug Logging *(v1.5.0+)*

For more precise debugging, use per-subsystem toggles instead of the global `debug` flag:

```yaml
logging:
  debug:
    startup: false      # Startup flow, reloads, compatibility, general lifecycle
    nms: false          # Reflection, fake connection setup, NMS internals
    packets: false      # Tab-list/entity packet sending and packet constructor selection
    luckperms: false    # LuckPerms group sync and prefix refresh details
    network: false      # Velocity/Bungee plugin messaging and remote bot sync
    config-sync: false  # Network config push/pull activity and YAML sync internals
    skin: false         # Skin subsystem debugging
    database: false     # Database init, migrations, flushes, and session persistence
```

**Usage Examples:**

| Scenario | Enable |
|----------|--------|
| Bot command blocking verification | `nms: true` |
| Lobby spawn protection verification | `nms: true` |
| Troubleshooting skin loading | `skin: true` |
| Diagnosing LuckPerms prefix issues | `luckperms: true` |
| Debugging proxy network chat | `network: true` |
| Config sync problems | `config-sync: true` |

**Bot Protection Debug Output (v1.5.6+):**

When `nms: true` is enabled, you'll see:

```
[FPP] BotCommandBlocker: blocked command (LOWEST) for BotName: /give BotName diamond_sword
[FPP] BotCommandBlocker: cleared command suggestions for BotName
[FPP] BotSpawnProtection: protecting BotName from teleports for 5 ticks
[FPP] BotSpawnProtection: blocked PLUGIN teleport for BotName from world (100,64,200) to lobby (0,100,0)
[FPP] BotSpawnProtection: removed protection for BotName
```

> **Tip:** Set only the category you need — this keeps logs clean and focused.

---

## Limits

### max-bots

```yaml
limits:
  max-bots: 1000
```

The global maximum number of bots that can be active at any time.  
`0` means unlimited. Players with `fpp.bypass.maxbots` ignore this cap.

### user-bot-limit

```yaml
limits:
  user-bot-limit: 1
```

The default personal bot limit for players with `fpp.user.spawn`.  
This is the fallback when the player has no `fpp.bot.<num>` node.  
Override per-player or per-group with the `fpp.bot.<num>` permission nodes.

### spawn-presets

```yaml
limits:
  spawn-presets: [1, 5, 10, 15, 20]
```

Tab-complete suggestions for the amount argument in `/fpp spawn`.  
Admin users see these presets. User-tier players always see only `1`.

---

## Bot Display Names

```yaml
bot-name:
  admin-format: '{bot_name}'
  user-format:  'bot-{spawner}-{num}'
  tab-list-format: '{prefix}{bot_name}{suffix}'
```

| Key | Description |
|-----|-------------|
| `admin-format` | Display-name template for bots spawned by admins (`fpp.spawn`). Placeholder: `{bot_name}` — the name drawn from `bot-names.yml`. |
| `user-format` | Display-name template for bots spawned by non-admin users (`fpp.user.spawn`). Placeholders: `{bot_name}`, `{spawner}` (the player's name), `{num}` (sequential bot index). |
| `tab-list-format` | **Full tab-list / nametag display format.** Applied as the final step after `admin-format` / `user-format` is resolved. |

### `tab-list-format` placeholders

| Placeholder | Value |
|-------------|-------|
| `{prefix}` | LuckPerms group prefix (empty when LuckPerms is not installed or no prefix is set) |
| `{bot_name}` | Resolved bot name after `admin-format` / `user-format` substitution |
| `{suffix}` | LuckPerms group suffix (empty when LuckPerms is not installed or no suffix is set) |
| `%any_papi%` | Any PlaceholderAPI placeholder — evaluated server-wide (null player context) |

**Examples:**

| Format | Result |
|--------|--------|
| `"{prefix}{bot_name}{suffix}"` | Default — prefix + name + suffix |
| `"&8[&7Bot&8] {bot_name}"` | Static gray `[Bot]` label before the name |
| `"{prefix}{bot_name}{suffix} <gray>%server_uptime%"` | Appends server uptime via PAPI |

## Skin

```yaml
skin:
  mode: auto
  guaranteed-skin: false
```

Controls how bots get their Minecraft player skin.

| Mode | Description |
|------|-------------|
| `auto` *(default)* | Fetches a real Mojang skin matching the bot's name from the Mojang API. Works on online-mode servers. |
| `custom` | Full control — per-bot overrides, a `skins/` PNG folder, and a random pool. Resolution order: per-bot override → `skins/<name>.png` → random PNG from `skins/` → random pool entry → Mojang API. |
| `off` | No skin applied. Bots display the default Steve / Alex appearance. |

`guaranteed-skin` (default `false`) — when `false`, bots whose name has no matching Mojang account use the default Steve/Alex appearance. Set to `true` to attempt a skin fetch even for generated names.

In `custom` mode, place 64×64 or 64×32 PNG skin files inside `plugins/FakePlayerPlugin/skins/`. Name a file `<botname>.png` to assign it exclusively to that bot; any other PNG enters the random pool. Run `/fpp reload` after adding or removing skin files.

## Body

```yaml
body:
  enabled: true
  pushable: true
  damageable: true
```

Controls whether a physical **Mannequin** entity is spawned for each bot and how it interacts with the world.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | boolean | `true` | Spawn a visible Mannequin entity in the world. When `false`, bots appear only in the tab list with join/leave messages — no entity is placed. |
| `pushable` | boolean | `true` | Allow players and other entities to physically push the bot's body. When `false`, the Mannequin is completely immovable — walk-into, hit-knockback, and bot-vs-bot separation forces are all ignored. Hot-reloadable via `/fpp reload`. |
| `damageable` | boolean | `true` | Allow the bot's body to take damage and be killed. When `false`, the Mannequin is invulnerable — hits register the hurt sound (if `combat.hurt-sound: true`) but deal no damage. Hot-reloadable via `/fpp reload`. |

## Persistence

```yaml
persistence:
  enabled: true
```

| Value | Effect |
|-------|--------|
| `true` | Active bots leave with a leave message on server shutdown and rejoin automatically after the server restarts. Their last-known position (not spawn point) is restored. |
| `false` | Bots are removed permanently on shutdown. |

---

## Join Delay

```yaml
join-delay:
  min: 0    # ticks
  max: 1    # ticks
```

When spawning multiple bots, each bot waits a random number of ticks (between `min` and `max`) before appearing.  
`20 ticks = 1 second`. Set both to `0` for instant simultaneous spawning.

This makes batch spawns look like natural player joins rather than a single-frame flood.

---

## Leave Delay

```yaml
leave-delay:
  min: 0    # ticks
  max: 1    # ticks
```

Same concept as join delay, applied when removing multiple bots (`/fpp despawn all`).  
Each bot's leave message and entity removal are staggered by a random delay in this range.

---

## Combat

```yaml
combat:
  max-health: 20.0
  hurt-sound: true
```

| Option | Description |
|--------|-------------|
| `max-health` | Health bots spawn with. Default player health is `20.0`. |
| `hurt-sound` | Play the `entity.player.hurt` sound when a bot takes damage. |

---

## Death & Respawn

```yaml
death:
  respawn-on-death: false
  respawn-delay: 60
  suppress-drops: true
```

| Option | Description |
|--------|-------------|
| `respawn-on-death` | `true` → bot respawns at its last known location after dying. `false` → bot leaves the server permanently on death. |
| `respawn-delay` | Ticks to wait before respawning (20 = 1 second). Only used when `respawn-on-death: true`. |
| `suppress-drops` | Prevent item drops when a bot dies. Recommended to keep `true`. |

---

## Messages

```yaml
messages:
  join-message: true
  leave-message: true
  kill-message: false
  notify-admins-on-join: true
```

| Option | Description |
|--------|-------------|
| `join-message` | Broadcast a vanilla-style join message when a bot is spawned. |
| `leave-message` | Broadcast a vanilla-style leave message when a bot is deleted or dies. |
| `kill-message` | Broadcast a kill message when a player kills a bot. |
| `notify-admins-on-join` | Send compatibility warnings to admins when they join. |

---

## Chunk Loading

```yaml
chunk-loading:
  enabled: true
  radius: 0
  update-interval: 20
```

| Option | Description |
|--------|-------------|
| `enabled` | Keep chunks loaded around each bot like a real player. Mobs spawn, redstone ticks, and crops grow. |
| `radius` | Chunk radius to keep loaded. `0` = match server simulation-distance. |
| `update-interval` | Ticks between position checks (20 = 1 s). |

---

## Head AI

```yaml
head-ai:
  enabled: true
  look-range: 8.0
  turn-speed: 0.3
```

| Option | Description |
|--------|-------------|
| `enabled` | Enable/disable head tracking entirely. |
| `look-range` | Radius (blocks) within which a bot rotates its head to face the nearest player. Set to `0` to disable. |
| `turn-speed` | Interpolation factor (0.0–1.0). `1.0` = instant snap. `0.1` = very slow smooth turn. |

---

## Swim AI

```yaml
swim-ai:
  enabled: true
```

When `enabled: true`, bots automatically swim upward when submerged in water or lava — mimicking a real player holding the spacebar. Set to `false` to let bots sink or drown instead.

---

## Collision / Push

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

| Option | Description |
|--------|-------------|
| `walk-radius` | Distance (blocks) at which a player walking into a bot triggers a push. |
| `walk-strength` | Impulse applied when a player walks into a bot. |
| `hit-strength` | Knockback force when hitting a bot. |
| `hit-max-horizontal-speed` | Max horizontal speed for hit/explosion knockback. |
| `bot-radius` | Radius at which two bots push each other apart. |
| `bot-strength` | Impulse for bot-vs-bot separation. |
| `max-horizontal-speed` | Maximum push speed cap for walk/separation sources. |

---

## Swap System

```yaml
swap:
  enabled: false

  session:
    min: 60    # Minimum session duration in seconds
    max: 300   # Maximum session duration in seconds

  absence:
    min: 30    # Minimum offline time in seconds
    max: 120   # Maximum offline time in seconds

  max-swapped-out: 0        # Max bots offline simultaneously (0 = unlimited)
  farewell-chat: true       # Bots say goodbye before leaving
  greeting-chat: true       # Bots say hi when rejoining
  same-name-on-rejoin: true # Reuse the same name if available on rejoin
```

See [Swap System](Swap-System.md) for a full explanation of every option.

---

## Fake Chat

```yaml
fake-chat:
  enabled: false
  require-player-online: true
  chance: 0.75
  interval:
    min: 5
    max: 10
  typing-delay: true
  burst-chance: 0.12
  burst-delay:
    min: 2
    max: 5
  reply-to-mentions: true
  mention-reply-chance: 0.65
  reply-delay:
    min: 2
    max: 8
  stagger-interval: 3
  activity-variation: true
  history-size: 5
  remote-format: "<yellow>{name}<dark_gray>: <white>{message}"
```

| Key | Description |
|-----|-------------|
| `enabled` | Master toggle for the entire fake-chat system. |
| `require-player-online` | Suppress bot messages when no real players are online. |
| `chance` | Roll probability (0.0–1.0) per interval tick. |
| `interval.min` / `interval.max` | Seconds between each bot's own messages (random range). Hot-reloadable — `/fpp reload` restarts all bot chat loops immediately. |
| `typing-delay` | Simulate a 0–2.5 s typing pause before each message. |
| `burst-chance` | Probability a bot sends a quick follow-up message shortly after. |
| `burst-delay.min` / `max` | Seconds before the follow-up fires. |
| `reply-to-mentions` | When a real player says a bot's name in chat, that bot may reply. |
| `mention-reply-chance` | Probability a named bot actually replies (0.0–1.0). |
| `reply-delay.min` / `max` | Seconds before the mention reply fires. |
| `stagger-interval` | Minimum gap (seconds) between any two bots chatting — prevents floods. 0 = disabled. |
| `activity-variation` | Give each bot a random chat-frequency multiplier (quiet/normal/active/very-active). |
| `history-size` | How many of a bot's own recent messages to remember and avoid repeating. |
| `remote-format` | MiniMessage format for bodyless or proxy-remote bot broadcasts. Placeholders: `{name}`, `{message}`. |
| `chat-format` | **Full chat line format.** Supports MiniMessage tags and legacy `&` codes. Placeholders: `{prefix}`, `{bot_name}`, `{suffix}`, `{message}`. |

**`chat-format` placeholders:**

| Placeholder | Value |
|-------------|-------|
| `{prefix}` | LuckPerms group prefix (empty when LuckPerms is not installed or no prefix is set) |
| `{bot_name}` | Bot display name |
| `{suffix}` | LuckPerms group suffix (empty when LuckPerms is not installed or no suffix is set) |
| `{message}` | Text drawn from `bot-messages.yml` |

**`chat-format` examples:**

| Format string | Result |
|---------------|--------|
| `"&7{bot_name}: {message}"` | Gray name, default colon style (default) |
| `"&7{prefix}{bot_name}&7: {message}"` | LP prefix before name |
| `"{prefix}{bot_name}{suffix}&7: {message}"` | Full prefix + suffix wrap |
| `"<gray>{bot_name}</gray>: {message}"` | MiniMessage gray |
| `"<{bot_name}> {message}"` | IRC-style `<BotName> hello` |
| `"<gradient:#ff0000:#0000ff>{bot_name}</gradient>: {message}"` | Gradient name |

See [Fake Chat](Fake-Chat.md) for a full explanation.

---

## Tab List

```yaml
tab-list:
  enabled: true
```

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | boolean | `true` | **Whether bots appear as entries in the player tab list.** `true` = bots show in the tab list. `false` = bots are invisible in the tab list; they still count toward the server player count shown in the multiplayer server-list screen. Hot-reloadable via `/fpp reload`. |

## Database

```yaml
database:
  mysql:
    enabled: false
    host: "localhost"
    port: 3306
    database: "fpp"
    username: "root"
    password: ""
    use-ssl: false
```

| Option | Description |
|--------|-------------|
| `mysql.enabled` | `true` to use MySQL. `false` (or if MySQL is unreachable) falls back to SQLite. |
| `host` | MySQL server hostname or IP. |
| `port` | MySQL port (default: 3306). |
| `database` | MySQL database name. |
| `username` | MySQL username. |
| `password` | MySQL password. |
| `use-ssl` | Enable SSL for the MySQL connection. |

When MySQL is disabled or unreachable, FPP automatically uses a local **SQLite** database at:  
`plugins/FakePlayerPlugin/data/fpp.db`

> See [Database](Database.md) for schema details and how to query records.

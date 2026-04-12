# ⚙️ Configuration

FPP is configured through `plugins/FakePlayerPlugin/config.yml`.

Most changes apply after running `/fpp reload` — no full restart needed.

> **Bundled config stamp:** `53`  
> **Current migration target:** `55`  
> The bundled file in the jar can be behind the runtime migrator target; migrations fill the gap automatically.

---

## Sections at a Glance

| Section | Purpose |
|---------|---------|
| `language` | Active language file |
| `limits` | Global cap, default user limit, spawn presets |
| `spawn-cooldown` | Seconds between player spawn uses |
| `persistence` | Save/restore bots across restarts |
| `join-delay` / `leave-delay` | Staggered joins/leaves |
| `bot-name` | Display name format templates |
| `skin` | Skin mode, pool, overrides, folder scanning |
| `tab-list` | Bot tab visibility |
| `luckperms` | Default LP group for new bots |
| `badword-filter` | Name profanity filtering |
| `bot-interaction` | Right-click / shift-right-click behavior |
| `messages` | Join/leave/kill/admin-warning toggles |
| `body` | Physical body behavior |
| `combat` | Health and hurt sound |
| `death` | Death/respawn behavior |
| `chunk-loading` | Keep chunks loaded around bots |
| `head-ai` | Head tracking |
| `swim-ai` | Auto swim behavior |
| `collision` | Push / separation physics |
| `pathfinding` | Shared nav tuning for move/mine/place/use/patrol |
| `pvp-ai` | Future / internal PvP AI tuning |
| `fake-chat` | Bot chat, event triggers, player reactions |
| `ai-conversations` | DM AI replies, personalities, typing delay |
| `swap` | Session rotation |
| `peak-hours` | Time-window bot pool scheduler |
| `database` | SQLite / MySQL / NETWORK mode |
| `config-sync` | Cross-server config sync |
| `performance` | Position packet culling |
| `debug` / `logging.debug.*` | Debug logging |
| `update-checker` | Update notifications |
| `metrics` | FastStats telemetry opt-in/out |

---

## `language`

```yaml
language: en
```

Selects the file from `plugins/FakePlayerPlugin/language/`.

Example:
- `en` → `language/en.yml`

---

## `limits`

```yaml
limits:
  max-bots: 1000
  user-bot-limit: 1
  spawn-presets: [1, 5, 10, 15, 20]
```

| Key | Description |
|-----|-------------|
| `max-bots` | Global bot cap. `0` = unlimited |
| `user-bot-limit` | Fallback user-tier limit when no `fpp.spawn.limit.<N>` is granted |
| `spawn-presets` | Spawn-count tab-complete suggestions |

Related permissions:
- `fpp.bypass.maxbots`
- `fpp.spawn.limit.<N>`

---

## `spawn-cooldown`

```yaml
spawn-cooldown: 0
```

Cooldown in **seconds** between `/fpp spawn` uses.

- `0` = disabled
- bypassed by `fpp.bypass.cooldown`

---

## `persistence`

```yaml
persistence:
  enabled: true
```

When enabled, bots are restored after restart.

Also interacts with task persistence:
- mine tasks
- use tasks
- place tasks
- patrol / waypoint tasks

---

## `join-delay` / `leave-delay`

```yaml
join-delay:
  min: 0
  max: 1

leave-delay:
  min: 0
  max: 1
```

Values are in **ticks**.

Quick reference:
- `20` = 1 second
- `40` = 2 seconds
- `100` = 5 seconds

Used to stagger mass spawn / mass despawn actions so they look more natural.

---

## `bot-name`

```yaml
bot-name:
  admin-format: '{bot_name}'
  user-format: 'bot-{spawner}-{num}'
```

| Key | Description |
|-----|-------------|
| `admin-format` | Name format for admin-spawned bots |
| `user-format` | Name format for user-tier bots |

Placeholders:
- `{bot_name}`
- `{spawner}`
- `{num}`

---

## `skin`

```yaml
skin:
  mode: auto
  guaranteed-skin: false
  clear-cache-on-reload: true
  overrides: {}
  pool: []
  use-skin-folder: true
```

Modes:

| Mode | Behavior |
|------|----------|
| `auto` | Mojang skin matching the bot name |
| `custom` | Uses overrides / pool / skin folder pipeline |
| `off` | Default Steve/Alex only |

Important notes:
- `guaranteed-skin: false` is the default
- `overrides` maps `bot-name -> minecraft-username`
- `pool` is a list of usernames used as random fallbacks
- `use-skin-folder: true` scans `plugins/FakePlayerPlugin/skins/`
- a `skins/README.txt` file is generated on first run

See [Skin-System](Skin-System.md).

---

## `tab-list`

```yaml
tab-list:
  enabled: true
```

- `true` = bots appear in the in-game tab list
- `false` = bots are hidden from the tab list but still count toward server player totals

---

## `luckperms`

```yaml
luckperms:
  default-group: ""
```

Default LP group assigned to newly spawned bots.

- blank = LuckPerms `default`
- individual bots can later be changed with `/fpp rank`

---

## `badword-filter`

```yaml
badword-filter:
  enabled: true
  use-global-list: true
  global-list-url: "https://www.cs.cmu.edu/~biglou/resources/bad-words.txt"
  global-list-timeout-ms: 5000
  words: []
  whitelist: []
  auto-rename: true
  auto-detection:
    enabled: true
    mode: strict
```

Used to block or auto-rename bad bot names.

| Key | Description |
|-----|-------------|
| `enabled` | Master switch |
| `use-global-list` | Download and merge a remote profanity list |
| `words` | Inline extra words |
| `whitelist` | Allowed names that would otherwise match |
| `auto-rename` | Replace bad names with a clean generated name instead of hard-blocking |
| `auto-detection.mode` | `off`, `normal`, or `strict` detection |

Detection includes leet-speak normalization and optional stricter anti-evasion matching.

---

## `bot-interaction`

```yaml
bot-interaction:
  right-click-enabled: true
  shift-right-click-settings: true
```

Controls in-world interaction behavior.

| Key | Description |
|-----|-------------|
| `right-click-enabled` | Enables bot right-click interaction at all |
| `shift-right-click-settings` | Opens `BotSettingGui` when sneaking |

Behavior:
- normal right-click → inventory or stored command
- shift-right-click → per-bot settings GUI

---

## `messages`

```yaml
messages:
  join-message: true
  leave-message: true
  kill-message: false
  notify-admins-on-join: true
```

Controls join/leave/kill broadcasts and startup join warnings for admins.

---

## `body`

```yaml
body:
  enabled: true
  pushable: true
  damageable: true
  pick-up-items: true
  pick-up-xp: true
  drop-items-on-despawn: true
```

| Key | Description |
|-----|-------------|
| `enabled` | Spawn a physical NMS `ServerPlayer` body |
| `pushable` | Allow players/entities to push bots |
| `damageable` | Allow damage from normal combat/environment |
| `pick-up-items` | Allow item pickup |
| `pick-up-xp` | Allow XP orb pickup |
| `drop-items-on-despawn` | Drop inventory + XP when the bot is despawned |

Per-bot pickup toggles also exist in `BotSettingGui`.

---

## `combat`

```yaml
combat:
  max-health: 20.0
  hurt-sound: true
```

- `max-health` → bot max HP
- `hurt-sound` → play player hurt sound when damaged

---

## `death`

```yaml
death:
  respawn-on-death: false
  respawn-delay: 15
  suppress-drops: false
```

| Key | Description |
|-----|-------------|
| `respawn-on-death` | Respawn the bot after death |
| `respawn-delay` | Ticks before respawn |
| `suppress-drops` | Prevent death drops |

---

## `chunk-loading`

```yaml
chunk-loading:
  enabled: true
  radius: "auto"
  update-interval: 20
```

Important `radius` behavior in current config:
- `"auto"` = match server simulation distance
- `0` = do not load chunks at all
- positive number = fixed chunk radius

---

## `head-ai`

```yaml
head-ai:
  enabled: true
  look-range: 8.0
  turn-speed: 0.3
  tick-rate: 3
```

| Key | Description |
|-----|-------------|
| `enabled` | Master switch |
| `look-range` | Player detection range in blocks |
| `turn-speed` | Rotation smoothing |
| `tick-rate` | How often the tracking scan runs |

---

## `swim-ai`

```yaml
swim-ai:
  enabled: true
```

When enabled, bots swim upward in water/lava like a real player holding jump.

---

## `collision`

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

Controls push physics and bot-vs-bot separation.

---

## `pathfinding`

```yaml
pathfinding:
  parkour: false
  break-blocks: false
  place-blocks: false
  place-material: DIRT
  arrival-distance: 1.2
  patrol-arrival-distance: 1.5
  waypoint-arrival-distance: 0.65
  sprint-distance: 6.0
  follow-recalc-distance: 3.5
  recalc-interval: 60
  stuck-ticks: 8
  stuck-threshold: 0.04
  break-ticks: 15
  place-ticks: 5
  max-range: 64
  max-nodes: 2000
  max-nodes-extended: 4000
```

Shared navigation tuning for:
- `/fpp move`
- `/fpp mine`
- `/fpp place`
- `/fpp use`
- waypoint patrols

Feature flags:
- `parkour`
- `break-blocks`
- `place-blocks`
- `place-material`

Tuning:
- arrival distances
- follow recalculation rules
- stuck detection thresholds
- block interaction timings
- node/range caps

---

## `pvp-ai`

Currently a future / internal section.

The public plugin still keeps PvP spawning restricted, so treat this as advanced/internal tuning rather than a generally available feature.

---

## `fake-chat`

```yaml
fake-chat:
  enabled: false
  require-player-online: true
  chance: 0.75
  interval:
    min: 5
    max: 10
  typing-delay: true
  activity-variation: true
  history-size: 5
  stagger-interval: 3
  burst-chance: 0.12
  burst-delay:
    min: 2
    max: 5
  reply-to-mentions: true
  mention-reply-chance: 0.65
  reply-delay:
    min: 2
    max: 8
  remote-format: "<yellow>{name}<dark_gray>: <white>{message}"
```

Also includes newer subsections:
- `bot-to-bot`
- `event-triggers.on-player-join`
- `event-triggers.on-death`
- `event-triggers.on-player-leave`
- `event-triggers.on-advancement`
- `event-triggers.on-first-join`
- `event-triggers.on-kill`
- `event-triggers.on-high-level`
- `on-player-chat`
- `keyword-reactions`

See [Fake-Chat](Fake-Chat.md) for the full breakdown.

---

## `ai-conversations`

```yaml
ai-conversations:
  enabled: true
  default-personality: "default"
  typing-delay:
    enabled: true
    base: 1.0
    per-char: 0.07
    max: 5.0
  max-history: 10
  cooldown: 3
  debug: false
```

This powers bot DM replies to:
- `/msg`
- `/tell`
- `/whisper`

### Personality files

- `default-personality` refers to a file in `plugins/FakePlayerPlugin/personalities/`
- `default` means `personalities/default.txt`
- per-bot overrides are assigned with `/fpp personality <bot> set <name>`

### API keys

Keys/endpoints are stored in `plugins/FakePlayerPlugin/secrets.yml`, not in `config.yml`.

Supported provider order:
- OpenAI
- Anthropic
- Groq
- Google Gemini
- Ollama
- Copilot
- Custom OpenAI-compatible

---

## `swap`

```yaml
swap:
  enabled: false
  max-swapped-out: 0
  min-online: 0
  same-name-on-rejoin: true
  farewell-chat: true
  greeting-chat: true
  retry-rejoin: true
  retry-delay: 60
  session:
    min: 60
    max: 300
  absence:
    min: 30
    max: 120
```

Newer important keys:
- `min-online`
- `retry-rejoin`
- `retry-delay`

See [Swap-System](Swap-System.md).

---

## `peak-hours`

```yaml
peak-hours:
  enabled: false
  timezone: "UTC"
  stagger-seconds: 30
  min-online: 0
  notify-transitions: false
```

Scales the bot pool based on time windows.

Requires:
- `swap.enabled: true`

See [Peak-Hours](Peak-Hours.md).

---

## `database`

```yaml
database:
  enabled: true
  mode: "LOCAL"
  server-id: "default"
  mysql-enabled: false
  mysql:
    host: "localhost"
    port: 3306
    database: "fpp"
    username: "root"
    password: ""
    use-ssl: false
    pool-size: 5
    connection-timeout: 30000
  location-flush-interval: 30
  session-history:
    max-rows: 20
```

| Key | Description |
|-----|-------------|
| `enabled` | Master DB switch |
| `mode` | `LOCAL` or `NETWORK` |
| `server-id` | Unique backend ID in proxy networks |
| `mysql-enabled` | Enable MySQL backend |
| `location-flush-interval` | Seconds between batched position flushes |
| `session-history.max-rows` | Max rows returned in info queries |

---

## `config-sync`

```yaml
config-sync:
  mode: "DISABLED"
```

Modes:
- `DISABLED`
- `MANUAL`
- `AUTO_PULL`
- `AUTO_PUSH`

Used only in `NETWORK` mode with shared MySQL.

---

## `performance`

```yaml
performance:
  position-sync-distance: 128.0
```

Maximum distance for position-sync packets.

- `0` = unlimited
- `128.0` = recommended default

---

## `debug` and `logging.debug.*`

```yaml
debug: false
logging:
  debug:
    startup: false
    nms: false
    packets: false
    luckperms: false
    network: false
    config-sync: false
    skin: false
    database: false
    chat: false
    swap: false
```

Use granular flags instead of global `debug` whenever possible.

---

## `update-checker`

```yaml
update-checker:
  enabled: true
```

Controls startup/reload version checks.

---

## `metrics`

```yaml
metrics:
  enabled: true
```

Controls anonymous FastStats telemetry.

---

## Related Pages

- [Getting-Started](Getting-Started.md)
- [Commands](Commands.md)
- [Fake-Chat](Fake-Chat.md)
- [Database](Database.md)
- [Peak-Hours](Peak-Hours.md)
- [Skin-System](Skin-System.md)
- [Migration](Migration.md)

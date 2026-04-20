# 🚀 Getting Started

> **Your complete setup guide for Fake Player Plugin v1.6.6.1**  
> Install it correctly, verify the important systems, and get your first bots online fast.

---

## 📋 System Requirements

### 🖥️ Server Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Server software | Paper 1.21+ | Paper 1.21.11 |
| Java | JDK 21+ | JDK 21+ (Temurin / Oracle) |
| RAM | 1 GB+ | 2 GB+ |
| CPU | 2 cores | 4+ cores for larger bot pools |
| Storage | 100 MB+ | 500 MB+ if using DB backups / MySQL exports |

### 🔧 Optional Integrations

| Plugin | Required | Purpose |
|--------|----------|---------|
| PlaceholderAPI | Optional | `%fpp_*%` placeholders for scoreboards, TAB, holograms, etc. |
| LuckPerms | Optional | Per-bot groups, prefix/suffix integration, group diagnostics |
| WorldGuard | Optional | Prevents player-sourced PvP damage to bots in no-PvP regions |
| NameTag | Optional | Nick-conflict guard, bot isolation from nick cache, skin sync, sync-nick-as-rename |

> `PacketEvents` is **not** a current dependency.

### 🌐 Server Notes

- **Online mode:** recommended if you want `skin.mode: player` to match real Mojang skins reliably
- **Simulation distance:** matters when `chunk-loading.radius: "auto"` is used
- **Version support:** FPP is currently tested up to **1.21.11**; newer MC versions are guarded by the plugin's version check

---

## 📦 Installation

### 1) Install FPP

1. Download the latest jar from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp))
2. Place it in your server's `plugins/` folder
3. Start the server once
4. Wait for FPP to finish its first-run setup

Example folder layout after copying the jar:

```text
plugins/
├── fpp-1.6.6.jar
├── PlaceholderAPI-2.11.x.jar      (optional)
├── LuckPerms-Bukkit-5.5.x.jar     (optional)
├── WorldGuard-Bukkit-7.x.jar      (optional)
└── NameTag-x.x.x.jar              (optional)
```

### 2) Verify first boot

After first launch, check that these files/folders were generated:

```text
plugins/FakePlayerPlugin/
├── config.yml
├── bot-names.yml
├── bot-messages.yml
├── secrets.yml
├── bad-words.yml
├── language/
│   └── en.yml
├── personalities/
│   ├── default.txt
│   ├── friendly.txt
│   ├── grumpy.txt
│   └── noob.txt
├── skins/
│   └── README.txt
└── data/
    └── fpp.db
```

What these are for:

- `config.yml` — main plugin config
- `bot-names.yml` — random bot name pool
- `bot-messages.yml` — fake chat message pools
- `secrets.yml` — AI provider API keys and endpoints
- `bad-words.yml` — local profanity list for the badword filter
- `personalities/` — AI personality prompt files
- `data/fpp.db` — SQLite database (default backend)

---

## ⚡ Quick Setup

### 1) Give yourself permissions

#### Full admin access

```text
/lp user <yourname> permission set fpp.op true
```

#### Or basic user access only

```text
/lp user <yourname> permission set fpp.use true
/lp user <yourname> permission set fpp.spawn.limit.5 true
```

Current permission structure:

- `fpp.op` — admin wildcard
- `fpp.use` — user wildcard
- `fpp.spawn.user` — user spawn permission
- `fpp.tph` — user teleport-home-bot permission
- `fpp.xp` — user XP collection permission
- `fpp.info.user` — user info permission

### 2) Spawn your first bots

```text
/fpp spawn 3
/fpp list
```

Expected result:

- bots appear in the tab list
- server player count increases
- bots appear in-world if `body.enabled: true`
- `/fpp list` shows their names, uptime, and location

### 3) Test direct interaction shortcuts

- **Right-click a bot** → opens inventory, unless it has a stored right-click command
- **Shift + right-click a bot** → opens the per-bot settings GUI (`BotSettingGui`) when enabled

### 4) Enable a few useful systems

```yaml
# config.yml
fake-chat:
  enabled: true

bot-interaction:
  right-click-enabled: true
  shift-right-click-settings: true

body:
  enabled: true
  pushable: true
  damageable: true
```

Then apply live:

```text
/fpp reload
```

---

## 🤖 AI Setup (Optional)

If you want bots to reply to `/msg`, `/tell`, or `/whisper` with AI-generated responses:

### 1) Add a provider key to `secrets.yml`

Example:

```yaml
openai-api-key: "your-key-here"
```

### 2) Keep AI conversations enabled in `config.yml`

```yaml
ai-conversations:
  enabled: true
  default-personality: "default"
```

### 3) Reload

```text
/fpp reload
/fpp personality reload
```

### 4) Test it

```text
/msg <botname> hello
```

The bot should reply using the prompt from `plugins/FakePlayerPlugin/personalities/default.txt` unless that bot has its own assigned personality.

Useful AI commands:

```text
/fpp personality list
/fpp personality <bot> set default
/fpp personality <bot> show
/fpp personality <bot> reset
```

---

## 🔐 Permissions Quick Start

### User-tier example

```yaml
permissions:
  - fpp.use
  - fpp.spawn.user
  - fpp.spawn.limit.5
  - fpp.tph
  - fpp.xp
  - fpp.info.user
```

### Moderator example

```yaml
permissions:
  - fpp.spawn
  - fpp.delete
  - fpp.list
  - fpp.freeze
  - fpp.inventory
  - fpp.move
  - fpp.mine
  - fpp.place
  - fpp.storage
  - fpp.useitem
  - fpp.waypoint
```

### Administrator example

```yaml
permissions:
  - fpp.op
```

See [Permissions](Permissions) for the full list.

---

## ⚙️ Recommended First Tweaks

### Bot limits

```yaml
limits:
  max-bots: 50
  user-bot-limit: 5
```

### Fake chat

```yaml
fake-chat:
  enabled: true
  require-player-online: true
  interval:
    min: 30
    max: 120
```

### Safe starter body config

```yaml
body:
  enabled: true
  pushable: true
  damageable: false
  pick-up-items: true
  pick-up-xp: true
  drop-items-on-despawn: true
```

### LuckPerms default group

```yaml
luckperms:
  default-group: "bots"
```

### AI conversations default personality file

```yaml
ai-conversations:
  enabled: true
  default-personality: "default"
```

---

## 🧪 Test Checklist

### Basic checks

- [ ] `/fpp spawn 1` works
- [ ] Bot appears in tab list
- [ ] Bot appears in world (if `body.enabled: true`)
- [ ] `/fpp despawn <name>` works
- [ ] `/fpp list` shows the bot

### Interaction checks

- [ ] Right-click bot opens inventory or runs stored command
- [ ] Shift-right-click bot opens per-bot settings GUI
- [ ] `/fpp inventory <bot>` opens the 54-slot GUI
- [ ] `/fpp settings` opens the 3-row config GUI

### Optional integration checks

- [ ] LuckPerms prefixes/suffixes appear on bots
- [ ] PlaceholderAPI placeholders resolve in another plugin
- [ ] AI bot replies work after configuring `secrets.yml`
- [ ] WorldGuard blocks player damage in no-PvP regions

---

## 🐛 Common Problems

### "Bots have no skins"

- `skin.mode: player` (default) works best — it fetches Mojang skins and falls back to the built-in 1000-player pool
- with `guaranteed-skin: true` (default), every bot always gets a real-looking skin
- use `skin.mode: random` if you want full control via usernames or PNG files

### "Tab list not working"

- check `tab-list.enabled: true`
- run `/fpp reload`
- if using LP/TAB-style plugins, make sure they are not hiding bot entries intentionally

### "Permission denied"

- user spawning needs `fpp.spawn.user` or `fpp.use`
- admin spawning needs `fpp.spawn` or `fpp.op`
- personal limits come from `fpp.spawn.limit.<N>`

### "Bots disappear after restart"

- keep `persistence.enabled: true`
- ensure database/storage is writable
- in DB mode, FPP restores from `fpp_active_bots`; with DB disabled it falls back to YAML files

### "AI conversations do nothing"

- make sure `ai-conversations.enabled: true`
- add a valid provider key in `secrets.yml`
- run `/fpp reload`
- run `/fpp personality reload` if you edited personality files

---

## 🎓 Good Next Pages

1. [Commands](Commands) — all command modes and examples
2. [Permissions](Permissions) — every node and example group setups
3. [Configuration](Configuration) — detailed config reference
4. [Bot Behaviour](Bot-Behaviour) — physical bodies, AI, interaction systems
5. [Fake Chat](Fake-Chat) — bot-to-bot chat, event triggers, AI-linked reactions
6. [Database](Database) — SQLite/MySQL, task persistence, schema

---

## 🆘 Need Help?

- **Discord:** [Join Community](https://discord.gg/QSN7f67nkJ)
- **GitHub Issues:** [Report a bug or request a feature](https://github.com/Pepe-tf/fake-player-plugin/issues)
- **Source Code:** [https://github.com/Pepe-tf/fake-player-plugin](https://github.com/Pepe-tf/fake-player-plugin)
- **Wiki Home:** [Home](Home)
- **Changelog:** [Changelog](Changelog)

---

**🎉 You're ready to start building your bot setup.**

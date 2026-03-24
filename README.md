# Fake Player Plugin (FPP)

Make your server look more active by spawning fake players that appear in the tab list, join/leave the game, walk around, chat, and show up on the server list — just like real players.
[![Version](https://img.shields.io/modrinth/v/fake-player-plugin-%28fpp%29?style=flat-square&label=version&color=0079FF&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
![MC](https://img.shields.io/badge/Minecraft-1.21.x-0079FF?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Paper-0079FF?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-0079FF?style=flat-square)
[![Modrinth](https://img.shields.io/badge/Modrinth-FPP-00AF5C?style=flat-square&logo=modrinth)](https://modrinth.com/plugin/fake-player-plugin-(fpp))
[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/pzFQWA4TXq)
---
## ✦ What it does
- **Tab list & server count** — fake players show up in the tab list and raise your server's player count
- **Join & leave messages** — bots announce themselves just like real players
- **In-world bodies** — bots have a visible body that can be pushed and damaged
- **Realistic skins** — every bot always gets a real Minecraft skin, never a blank Steve
- **Fake chat** — bots send random chat messages from a customisable list
- **Bot rotation** — bots automatically leave and rejoin with new names on a timer
- **Survives restarts** — bots rejoin at their last location when the server starts back up
- **LuckPerms support** — bots automatically pick up your server's rank prefix
- **Full customisation** — names, messages, skins, delays, limits — all configurable
- **Hot reload** — apply any config change instantly with `/fpp reload`, no restart needed
---
## ✦ Requirements
| | |
|---|---|
| Server | [Paper](https://papermc.io/downloads/paper) 1.21.x |
| Java | 21 or newer |
| LuckPerms | Optional — auto-detected if installed |
> Older 1.21 builds (1.21.0–1.21.8) are partially supported. Some features may be limited — check your console on startup for details.
---
## ✦ Installation
1. Download the latest `.jar` from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp))
2. Drop it into your `plugins/` folder
3. Restart the server
4. Edit `plugins/FakePlayerPlugin/config.yml` to your liking
5. Run `/fpp reload` to apply changes
> Updating from an older version? Your config is upgraded automatically on first start and a backup is created beforehand.
---
## ✦ Commands
All commands start with `/fpp` (also works as `/fakeplayer` or `/fp`).
### Admin
| Command | What it does |
|---|---|
| `/fpp spawn [amount]` | Spawn one or more bots at your location |
| `/fpp spawn --name <name>` | Spawn a bot with a specific name |
| `/fpp delete <name>` | Remove a bot |
| `/fpp delete all` | Remove all bots |
| `/fpp delete random [n]` | Remove one or more random bots |
| `/fpp list` | Show all active bots with location and uptime |
| `/fpp freeze <name\|all>` | Freeze or unfreeze a bot in place |
| `/fpp chat on\|off` | Toggle fake chat |
| `/fpp swap on\|off` | Toggle bot rotation |
| `/fpp stats` | Live overview — bot count, system status, server health |
| `/fpp tp <name>` | Teleport yourself to a bot |
| `/fpp reload` | Reload all config files instantly |
| `/fpp info` | Show session stats from the database |
### Players
| Command | What it does |
|---|---|
| `/fpp spawn` | Spawn your own bot (limited, default 1) |
| `/fpp tph [name]` | Teleport your bot to you |
| `/fpp info [name]` | Check your bot's location and uptime |
---
## ✦ Permissions
| Permission | Who gets it | What it grants |
|---|---|---|
| `fpp.*` | Ops | All admin commands |
| `fpp.user.*` | Everyone | Spawn/manage personal bots |
| `fpp.bot.1` – `fpp.bot.100` | Assigned manually | Personal bot limit |
To give a group more bots, assign the matching node. FPP uses the highest one the player has.
**Example — give VIP players 5 bots:**
```
/lp group vip permission set fpp.user.spawn true
/lp group vip permission set fpp.bot.5 true
```
---
## ✦ Configuration
Edit `plugins/FakePlayerPlugin/config.yml` and run `/fpp reload` to apply.
**Key settings:**
```yaml
limits:
  max-bots: 1000        # Max bots on the server at once (0 = unlimited)
  user-bot-limit: 1     # How many bots regular players can spawn
skin:
  mode: auto            # auto, custom, or off
  guaranteed-skin: true # Always use a real skin, never Steve
body:
  enabled: true         # false = bots in tab list only, no visible body
messages:
  join-message: true    # Show join messages
  leave-message: true   # Show leave messages
  kill-message: false   # Announce when a player kills a bot
fake-chat:
  enabled: false        # Let bots send random chat messages
  chance: 0.75          # Chance per interval to send a message
  interval:
    min: 5              # Seconds between messages (min)
    max: 10             # Seconds between messages (max)
swap:
  enabled: false        # Bots automatically leave and rejoin with new names
  session-min: 120      # Minimum seconds before a bot swaps out
  session-max: 600      # Maximum seconds before a bot swaps out
tab-list:
  enabled: true         # false = bots hidden from tab list but still count
persistence:
  enabled: true         # Bots come back after a server restart
```
---
## ✦ Skins
| Mode | How it works |
|---|---|
| `auto` *(default)* | Each bot gets the Mojang skin matching its name. Unknown names get the fallback skin. |
| `custom` | You control which skins bots use — per-bot overrides, a skin folder, or a random pool. |
| `off` | No skins. All bots use the default Steve/Alex look. |
To use custom skin files, drop standard Minecraft skin PNGs into `plugins/FakePlayerPlugin/skins/` and run `/fpp reload`.
---
## ✦ Bot Names & Chat
- **Names** — edit `plugins/FakePlayerPlugin/bot-names.yml`. Add any names you like (max 16 characters each).
- **Chat messages** — edit `plugins/FakePlayerPlugin/bot-messages.yml`. Use `{name}` for the bot's name or `{random_player}` for a random online player.
- Run `/fpp reload` after editing either file.
---
## ✦ Database
FPP logs every bot session — who spawned it, where, when it joined, and why it left.  
Use `/fpp info` to look up sessions in-game.
- **SQLite** — default, no setup needed
- **MySQL** — enable in `config.yml` for multi-server setups
---
## ✦ Changelog
### v1.4.24 *(2026-03-24)*
- Bumped plugin version and config-version to 26

### v1.4.23 *(2026-03-23)*
- Fixed bot name colours being lost after a server restart
- Fixed join/leave delays being 20× longer than configured
- `/fpp reload` now immediately refreshes bot prefixes from LuckPerms
- Added `/fpp delete random [amount]` to remove a random set of bots
- Running a pre-release build now shows a notice to admins in-game
### v1.4.22 *(2026-03-22)*
- Bot prefixes update live when LuckPerms groups change — no reload or respawn needed
- All LuckPerms colour formats now display correctly (gradients, hex, rainbow)
- Bots always sort below real players in the tab list
- Added `tab-list.enabled` to hide bots from the tab list while keeping the player count
- `/fpp reload` now shows step-by-step progress and total time
### v1.4.21 *(2026-03-21)*
- Fixed bot names showing raw `{placeholder}` text after a server restart
---
## ✦ License
©© 2026 Bill_Hub — All Rights Reserved.  
See [LICENSE](https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/blob/main/LICENSE) for full terms.  
Questions? Join the [Discord](https://discord.gg/pzFQWA4TXq) and ping `Bill_Hub`.
---
*Paper 1.21.x · Java 21 · [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp)) · [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/) · [PaperMC Hangar](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin) · [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/)*

# ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)

> Spawn realistic fake players on your Paper server — complete with tab list, server list count, join/quit messages, and in-world entities.

![Version](https://img.shields.io/badge/version-1.0.0-0079FF?style=flat-square)
![MC](https://img.shields.io/badge/Minecraft-1.21.x-0079FF?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Paper-0079FF?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-0079FF?style=flat-square)

---

## ✦ Features

| Feature | Description |
|---|---|
| **Real ServerPlayer** | Fake players are injected as actual NMS `ServerPlayer` objects — not just client-side packets |
| **Server list count** | Fake players increment the online player count shown on the server list |
| **Tab list** | Fake players appear in the tab list for all online and future players |
| **Join / quit messages** | Vanilla join and quit messages are broadcast automatically |
| **In-world entity** | Fake players appear as visible entities in the world |
| **Persistent** | Fake players survive real player reconnects — no resync needed |
| **Packet fallback** | If NMS injection fails, the plugin falls back to client-side packets gracefully |
| **Dynamic help** | Help command auto-discovers all registered sub-commands — no manual updates needed |
| **Hex colour + small-caps font** | Styled with `#0079FF` accent colour and Unicode small-caps throughout |
| **Fully translatable** | All messages stored in `language/en.yml` |
| **Hot reload** | `/fpp reload` reloads config and language files without restarting |

---

## ✦ Requirements

- **Paper** 1.21.x (tested on 1.21.11)
- **Java** 21+
- No external dependencies — zero runtime JAR requirements

---

## ✦ Installation

1. Download `fpp.jar` from the releases page.
2. Place it in your server's `plugins/` folder.
3. Restart the server.
4. Edit `plugins/FakePlayerPlugin/config.yml` and `language/en.yml` as desired.

---

## ✦ Commands

All sub-commands are under `/fpp` (aliases: `/fakeplayer`, `/fp`).

| Command | Permission | Description |
|---|---|---|
| `/fpp help [page]` | — | Shows the paginated help menu (clickable navigation) |
| `/fpp spawn <amount>` | `fpp.spawn` | Spawns the given number of fake players at your location |
| `/fpp summon <amount>` | `fpp.spawn` | Alias for `/fpp spawn` |
| `/fpp reload` | `fpp.reload` | Reloads the config and language files |

### Examples
```
/fpp spawn 5        — spawns 5 fake players at your current position
/fpp summon 10      — same as spawn
/fpp help 2         — shows page 2 of the help menu
/fpp reload         — hot-reloads all configuration
```

---

## ✦ Permissions

| Permission | Default | Description |
|---|---|---|
| `fpp.spawn` | `op` | Allows spawning/summoning fake players |
| `fpp.reload` | `op` | Allows reloading the plugin configuration |

---

## ✦ Configuration

Located at `plugins/FakePlayerPlugin/config.yml`.

```yaml
# Language file to use (must exist in the language/ folder)
language: en

# Debug logging — prints extra info to console
debug:
  enabled: false
```

---

## ✦ Language / Translations

All player-facing text lives in `plugins/FakePlayerPlugin/language/en.yml`.

To add a new language:
1. Copy `en.yml` to e.g. `fr.yml` in the same folder.
2. Translate the values.
3. Set `language: fr` in `config.yml`.
4. Run `/fpp reload`.

### Key message keys

| Key | Description |
|---|---|
| `prefix` | The `[FPP]` prefix used in all messages |
| `no-permission` | Shown when a player lacks permission |
| `player-only` | Shown when console runs a player-only command |
| `help-header / footer` | Top and bottom borders of the help menu |
| `help-entry` | Format for each command entry in the help menu |
| `reload-success` | Shown after a successful reload |
| `spawn-success` | Shown after fake players are spawned |
| `spawn-invalid-amount` | Shown when an invalid number is provided |

---

## ✦ How It Works

### NMS injection (primary mode)
FPP uses pure reflection to create real `net.minecraft.server.level.ServerPlayer` objects and register them via `PlayerList.placeNewPlayer()`. This means:
- The server itself treats them as real players
- No packets need to be re-sent on player reconnect
- The server list count is accurate automatically

### Packet fallback (secondary mode)
If NMS injection fails (e.g. on an unsupported build), FPP falls back to sending `ClientboundPlayerInfoUpdatePacket` and `ClientboundAddEntityPacket` to each online player. In this mode:
- Fake players are visible only to players who were online at spawn time
- Rejoining players receive the packets again via the `PlayerJoinEvent` listener
- Server list count is **not** incremented

The console will log which mode is active on first spawn.

---

## ✦ Project Structure

```
src/main/java/me/bill/fakePlayerPlugin/
├── FakePlayerPlugin.java          — Main plugin class
├── command/
│   ├── CommandManager.java        — Routes /fpp sub-commands
│   ├── FppCommand.java            — Base command interface
│   ├── HelpCommand.java           — Dynamic paginated help
│   ├── ReloadCommand.java         — Config/lang hot-reload
│   ├── SpawnCommand.java          — Spawn fake players
│   └── SummonCommand.java         — Alias for spawn
├── config/
│   └── Config.java                — Config.yml wrapper
├── fakeplayer/
│   ├── FakePlayer.java            — Fake player data model
│   ├── FakePlayerManager.java     — Lifecycle management
│   ├── NmsHelper.java             — Real ServerPlayer injection via reflection
│   └── PacketHelper.java          — Packet-only fallback
├── lang/
│   └── Lang.java                  — Language file loader
├── listener/
│   └── PlayerJoinListener.java    — Syncs fake players to joining real players
└── util/
    └── FppLogger.java             — Styled console logger
```

---

## ✦ Building

This project uses Maven. Open in IntelliJ IDEA and use the built-in Maven build, or run:

```
mvn clean package
```

The output JAR will be in `target/fpp-1.0.0.jar`.

**Requirements:**
- JDK 21
- `paper-api 1.21.11-R0.1-SNAPSHOT` (fetched automatically from PaperMC's Maven repo)

---

## ✦ Changelog

### 1.0.0 — 2026-03-07
- Initial release
- Real NMS `ServerPlayer` injection via reflection (no compile-time NMS dependency)
- Dynamic help command with clickable page navigation
- `/fpp spawn` and `/fpp summon` commands
- `/fpp reload` hot-reload command
- Hex colour `#0079FF` + Unicode small-caps styling
- Full `language/en.yml` translation support
- Packet fallback mode for compatibility
- Startup and shutdown log messages
- Debug mode with detailed reflection diagnostics

---

## ✦ License

MIT — free to use, modify, and distribute.

---

## ✦ Author

**El_Pepes** — Built with ❤️ for the Paper ecosystem.


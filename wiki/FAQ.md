# FAQ & Troubleshooting

Common issues, questions, and their solutions.

---

## Installation Issues

### The plugin fails to load with `InvalidDescriptionException`

**Cause:** The plugin name in `plugin.yml` contains a space character.  
**Fix:** The plugin name must be `FakePlayerPlugin` (no spaces). Do not rename the JAR to include spaces.

---

### `ClassNotFoundException: com.github.retrooper.packetevents`

**Cause:** PacketEvents is not installed, or an incompatible version is present.  
**Fix:**
1. Download `packetevents-spigot-2.11.2.jar` from the [PacketEvents releases](https://github.com/retrooper/packetevents/releases)
2. Place it in your `plugins/` folder
3. Restart the server — **not** `/reload`

---

### `ClassNotFoundException: net.minecraft.network.PacketFlow`

**Cause:** FPP attempted to use an NMS class that is not accessible under Paper's remapped environment.  
**Fix:** This is handled automatically — FPP falls back to packet-only mode. No action needed. This warning is harmless.

---

### FPP loads but no bots appear after `/fpp spawn`

Check the following:
1. You are running the command as a player (not console)
2. You have the correct permission (`fpp.spawn` or `fpp.user.spawn`)
3. `fake-player.max-bots` is not `0` set to a low number — increase it
4. Check the console for errors during spawn

---

## Bot Appearance Issues

### Bots have no skin / show Steve or Alex

| Cause | Fix |
|-------|-----|
| Server is offline-mode | Switch `skin.mode` to `fetch` |
| Bot name doesn't match a Mojang account | Use real Minecraft usernames |
| `skin.mode: disabled` | Change to `auto` or `fetch` |
| `fetch` mode and Mojang API is rate-limited | Wait a few minutes; reduce bot count |

---

### Bots don't show in the tab list

**Cause:** PacketHelper failed to send the tab-list update packet.  
**Fix:**
1. Enable `debug.enabled: true` in config and check console output on next spawn
2. Ensure PacketEvents is up to date (2.11.2+)
3. Restart the server (not `/reload`) after installing PacketEvents

---

### Bots are invisible in the world but appear in tab

**Cause:** `fake-player.spawn-body: false` in config.  
**Fix:** Set `spawn-body: true` and run `/fpp reload`.

---

### The bot nametag shows a double tag (Mannequin tag + custom tag)

**Fix:** This was a known issue in earlier versions. Update to the latest FPP build. The Mannequin's built-in nametag is suppressed by the plugin — only the custom ArmorStand tag should be visible.

---

### Server list still shows 0 players after spawning bots

**Cause:** The server list hook requires at least one player online to have initialised the packet channel.  
**Fix:** Join the server yourself first, then spawn bots. Once you're online, the server list count will update correctly.

---

## Bot Behaviour Issues

### Bots don't move / have no AI

This is intentional. FPP bots are **static Mannequin entities** with:
- Head tracking toward nearby players (see `head-ai` config)
- Physics / pushback from player and entity collisions

They do not pathfind, walk around, or have mob AI — this is by design.

---

### Bots take fire damage in sunlight

**Fix:** Handled automatically by the plugin. If bots are burning, ensure you are on the latest version. The Mannequin entity type does not naturally burn — if you see fire it may be a visual effect from an unrelated plugin.

---

### Bots turn into Drowned underwater

**Fix:** Handled automatically. Mannequin entities do not have the Zombie conversion mechanic. If conversion is still occurring, check for conflicting plugins that modify entity behaviour.

---

### Villagers flee from bots

**Cause:** In some Paper builds, Mannequins inherit certain entity flags.  
**Fix:** This is handled by the plugin's entity listener. Ensure you are on the latest FPP version.

---

### Bots are not pushable

**Check:**
1. `fake-player.spawn-body: true` must be set
2. `fake-player.collision.walk-strength` should be > 0
3. The collision listener registers at startup — a `/fpp reload` does not re-register listeners. Restart the server if you changed collision settings from a fresh config.

---

### Push force feels too strong / too weak

Adjust these values in `config.yml`:

```yaml
fake-player:
  collision:
    walk-strength: 0.22    # lower = less push when walking into bot
    hit-strength: 0.45     # lower = less knockback when punching bot
```

Then run `/fpp reload`.

---

## Death & Respawn Issues

### Bots respawn but don't show a join message on respawn

**Cause:** The join message was playing on initial spawn but not on respawn.  
**Fix:** This is a known edge case in older versions. Update to the latest build.

---

### Bots leave permanently on death instead of respawning

**Check:** `fake-player.death.respawn-on-death` must be `true` in config.  
If it is `true` and bots still don't respawn, enable debug mode and check the console.

---

### Bot drops items on death

**Fix:** Set `fake-player.death.suppress-drops: true` in config and run `/fpp reload`.

---

## Persistence Issues

### Bots don't rejoin after a server restart

**Check:**
1. `fake-player.persist-on-restart: true` in config
2. The database is accessible (check for database errors in console on startup)
3. Bots were online when the server stopped — bots that were already deleted before shutdown will not rejoin

---

### Bots rejoin at their spawn point instead of their last position

**Fix:** This is a database issue. Ensure the database write on shutdown completed successfully. Check the console for `[FPP] Saving bot positions...` messages during shutdown.

---

### Bots rejoin without a name / have UUID-style names

**Cause:** The bot record in the database lost its name, usually from an abrupt server crash.  
**Fix:** 
1. Run `/fpp delete all` to clean up nameless bots
2. Respawn them manually with `/fpp spawn`
3. The database will be updated correctly on the next clean shutdown

---

## Database Issues

### `Database is not available` error in `/fpp info`

**Cause:** The database failed to initialise at startup.  
**Fix:**
1. Check the console for database errors at startup
2. If using MySQL: verify host, port, username, password, and that the database exists
3. If using SQLite: ensure the plugin has write access to `plugins/FakePlayerPlugin/data/`

---

### MySQL connection refused

**Check:**
1. MySQL server is running
2. `host` and `port` are correct
3. The user has privileges on the `fpp` database
4. Firewall rules allow the connection from the Minecraft server's IP

---

## Swap / Chat Issues

### Fake chat is enabled but bots never send messages

**Check:**
1. `fake-chat.enabled: true` in config
2. `fake-chat.require-player-online: false` if testing with no real players online
3. `fake-chat.chance` is not set to `0`
4. `bot-messages.yml` has entries in the `messages:` list
5. At least one bot is currently active

---

### Swap system is enabled but bots never swap

**Check:**
1. `fake-player.swap.enabled: true`
2. `session-min` is not set too high for your testing window
3. Enable `debug.enabled: true` — swap events are logged when debug is on

---

## Performance Issues

### Server TPS drops with many bots

**Optimisation checklist:**

| Setting | Recommendation |
|---------|---------------|
| `chunk-loading.radius` | Reduce to `4` or lower |
| `head-ai.look-range` | Reduce to `4.0` or set to `0` to disable |
| Bot count | Keep under 50 on low-end hardware |
| `fake-chat` interval | Increase `interval.min` and `interval.max` |

---

### Memory grows over time with many bots

**Cause:** Skin cache growing unbounded in `fetch` mode.  
**Fix:** Switch to `auto` mode (zero memory overhead) or set `clear-cache-on-reload: true` and periodically run `/fpp reload`.

---

## General Tips

- Always restart the server (not `/reload`) when installing or updating FPP or PacketEvents
- Use `/fpp reload` for config changes — it is safe and instant
- Enable `debug.enabled: true` when troubleshooting — disable it in production
- Keep your name pool (`bot-names.yml`) larger than the max number of bots you run simultaneously
- Run `/fpp delete all` before stopping the server if you want clean leave messages for every bot

---

## Getting Help

If your issue is not listed here, contact the plugin owner:

- **Discord:** Bill_Hub  
- **GitHub:** https://github.com/Bill-Hub

> See [LICENSE](../LICENSE) — this software is proprietary. Redistribution and modification are prohibited.


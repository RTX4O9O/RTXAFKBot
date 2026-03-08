# Getting Started

> **Platform:** Paper 1.21.x (Mojang-mapped)  
> **Java:** 21 or higher  
> **Dependencies:** [PacketEvents 2.11.2](https://github.com/retrooper/packetevents) *(must be installed alongside FPP)*

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | Paper (or fork) 1.21.x |
| Java | 21+ |
| PacketEvents | 2.11.2+ |
| Online mode | Recommended (`auto` skin mode requires it) |

> **Forks:** Purpur, Pufferfish, and similar Paper forks are supported.  
> Spigot and CraftBukkit are **not** supported.

---

## Installation

### Step 1 — Install PacketEvents

Download `packetevents-spigot-2.11.2.jar` from the  
[PacketEvents GitHub releases](https://github.com/retrooper/packetevents/releases)  
and place it in your server's `plugins/` folder.

### Step 2 — Install FPP

Place `fpp.jar` in your server's `plugins/` folder alongside PacketEvents.

Your `plugins/` folder should look like:

```
plugins/
  fpp.jar
  packetevents-spigot-2.11.2.jar
  ...
```

### Step 3 — Start the server

Start (or restart) your server. FPP will generate its default config files on first run:

```
plugins/FakePlayerPlugin/
  config.yml
  bot-names.yml
  bot-messages.yml
  language/
    en.yml
  data/
    fpp.db          ← SQLite database (auto-created)
```

### Step 4 — Verify installation

Check the console for the FPP startup banner. It should look similar to:

```
[FPP] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[FPP]   ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ  v1.0.0
[FPP]   Author: Bill_Hub
[FPP] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[FPP] Config loaded. Language: en
[FPP] Database ready. (SQLite)
[FPP] PacketEvents integration active.
[FPP] FPP enabled successfully.
```

---

## First Spawn

Join the server as an operator and run:

```
/fpp spawn 5
```

Five fake players will appear with:
- A join message for each bot  
- Entries in the **tab list**  
- Mannequin bodies in the world at your location  
- The player count in the **server list** updated to reflect them  

---

## Upgrading

1. Stop the server.
2. Replace the old `fpp.jar` with the new one.
3. **Do not delete** `plugins/FakePlayerPlugin/` — your configs and database are preserved.
4. Start the server.

> After an upgrade, run `/fpp reload` to apply any new default config values.

---

## Uninstalling

1. Stop the server.
2. Run `/fpp delete all` before stopping if you want clean leave messages.
3. Remove `fpp.jar` from `plugins/`.
4. Optionally delete `plugins/FakePlayerPlugin/` to remove all data.

---

## Next Steps

- [Commands](Commands.md) — learn how to spawn, delete, and manage bots  
- [Configuration](Configuration.md) — tune the plugin to your server  
- [Permissions](Permissions.md) — set up access for your staff and players  


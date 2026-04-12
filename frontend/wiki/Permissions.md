# 🔐 Permissions

> **Complete permission reference - v1.6.2**  
> FPP uses **`fpp.op`** for admins and **`fpp.use`** for user-tier access.  
> All nodes are declared in `plugin.yml` so they appear in LuckPerms tab-completion.

---

## 🏗️ Permission Structure

```text
fpp.op                  # Admin wildcard (default: op)
fpp.use                 # User wildcard (default: true)
fpp.spawn.limit.<N>     # Personal bot limits (1-100)
fpp.bypass.maxbots      # Ignore global bot cap
fpp.bypass.cooldown     # Ignore spawn cooldown
```

### Important note about user nodes

Older documentation sometimes referenced `fpp.user.spawn`, `fpp.user.tph`, and `fpp.user.xp`.

Current nodes are:

- `fpp.spawn.user`
- `fpp.tph`
- `fpp.xp`
- `fpp.info.user`

---

## 👑 Admin Permissions (`fpp.op`)

`fpp.op` grants all admin commands automatically.

### Core commands

| Permission | Command | Description |
|------------|---------|-------------|
| `fpp.spawn` | `/fpp spawn` | Spawn unlimited bots |
| `fpp.delete` | `/fpp despawn` | Delete any bot |
| `fpp.list` | `/fpp list` | View all active bots |
| `fpp.info` | `/fpp info` | View all bot sessions / DB history |
| `fpp.freeze` | `/fpp freeze` | Freeze / unfreeze any bot |
| `fpp.tp` | `/fpp tp` | Teleport to a bot |
| `fpp.tph` | `/fpp tph` | Teleport any bot to you |
| `fpp.stats` | `/fpp stats` | Open live stats panel |
| `fpp.reload` | `/fpp reload` | Reload config and subsystems |
| `fpp.migrate` | `/fpp migrate` | Backup, export, migration tools |

### Bot systems

| Permission | Command | Description |
|------------|---------|-------------|
| `fpp.chat` | `/fpp chat` | Fake chat control |
| `fpp.swap` | `/fpp swap` | Session rotation control |
| `fpp.peaks` | `/fpp peaks` | Peak-hours scheduler |
| `fpp.rank` | `/fpp rank` | LuckPerms group assignment |
| `fpp.lpinfo` | `/fpp lpinfo` | LuckPerms diagnostics |
| `fpp.settings` | `/fpp settings` | Main settings GUI |

### Interaction / automation

| Permission | Command | Description |
|------------|---------|-------------|
| `fpp.inventory` | `/fpp inventory` | Open any bot inventory GUI |
| `fpp.move` | `/fpp move` | Navigate bots / patrol routes |
| `fpp.mine` | `/fpp mine` | Mining and area-mining |
| `fpp.place` | `/fpp place` | Block placing automation |
| `fpp.storage` | `/fpp storage` | Supply container management |
| `fpp.useitem` | `/fpp use` | Use / activate looked-at block |
| `fpp.cmd` | `/fpp cmd` | Execute or store RC commands |
| `fpp.waypoint` | `/fpp waypoint` | Manage named routes |
| `fpp.rename` | `/fpp rename` | Rename any bot |
| `fpp.rename.own` | `/fpp rename` | Rename only bots the sender spawned |
| `fpp.personality` | `/fpp personality` | Manage AI personalities |
| `fpp.badword` | `/fpp badword` | Manage runtime badword list |

### Network / proxy

| Permission | Command | Description |
|------------|---------|-------------|
| `fpp.alert` | `/fpp alert` | Broadcast admin alert across the proxy |
| `fpp.sync` | `/fpp sync` | Push/pull config across network |

### Bypass nodes

| Permission | Description |
|------------|-------------|
| `fpp.bypass.maxbots` | Ignore `limits.max-bots` |
| `fpp.bypass.cooldown` | Ignore `spawn-cooldown` |

---

## 👤 User Permissions (`fpp.use`)

`fpp.use` is enabled by default for normal players.

| Permission | Command | Description |
|------------|---------|-------------|
| `fpp.use` | *(wildcard)* | Grants the default user-tier feature set |
| `fpp.spawn.user` | `/fpp spawn` | Spawn personal bots |
| `fpp.tph` | `/fpp tph` | Teleport your own bot(s) to you |
| `fpp.xp` | `/fpp xp` | Collect XP from your bot |
| `fpp.info.user` | `/fpp info` | View your own bot information |
| `fpp.spawn.limit.1` | *(included in `fpp.use`)* | Default 1-bot limit |

---

## 🤖 Personal Bot Limits

Grant `fpp.spawn.limit.<N>` to set a player's personal cap. FPP always uses the **highest** limit node the player has.

| Node | Bot cap |
|------|---------|
| `fpp.spawn.limit.1` | 1 |
| `fpp.spawn.limit.2` | 2 |
| `fpp.spawn.limit.3` | 3 |
| `fpp.spawn.limit.5` | 5 |
| `fpp.spawn.limit.10` | 10 |
| `fpp.spawn.limit.15` | 15 |
| `fpp.spawn.limit.20` | 20 |
| `fpp.spawn.limit.50` | 50 |
| `fpp.spawn.limit.100` | 100 |

---

## 🛠️ LuckPerms Examples

### Give VIP players 5 personal bots

```text
/lp group vip permission set fpp.use true
/lp group vip permission set fpp.spawn.user true
/lp group vip permission set fpp.spawn.limit.5 true
/lp group vip permission set fpp.tph true
/lp group vip permission set fpp.xp true
```

### Give staff full admin access

```text
/lp group staff permission set fpp.op true
```

### Give moderators a selected tool set

```text
/lp group mod permission set fpp.spawn true
/lp group mod permission set fpp.delete true
/lp group mod permission set fpp.list true
/lp group mod permission set fpp.freeze true
/lp group mod permission set fpp.inventory true
/lp group mod permission set fpp.move true
/lp group mod permission set fpp.mine true
/lp group mod permission set fpp.place true
/lp group mod permission set fpp.storage true
/lp group mod permission set fpp.useitem true
/lp group mod permission set fpp.waypoint true
```

### Allow rename only for owned bots

```text
/lp group trusted permission set fpp.rename.own true
```

### Disable user spawning for default players

```text
/lp group default permission set fpp.use false
/lp group default permission set fpp.spawn.user false
```

---

## 🧩 Permission Notes

### `fpp.rename` vs `fpp.rename.own`

- `fpp.rename` — rename any active bot
- `fpp.rename.own` — rename only bots you personally spawned
- admins with `fpp.rename` effectively inherit the owned-bot capability too

### `/fpp use` command permission name

The command is `/fpp use`, but the permission node is:

```text
fpp.useitem
```

### `/fpp settings` vs per-bot settings GUI

- `/fpp settings` uses `fpp.settings`
- shift-right-click bot settings are controlled by interaction config and normal bot interaction access, not a separate public permission node

---

## 🆘 Troubleshooting

### "You don't have permission"

- admins should have `fpp.op`
- user-tier spawning requires `fpp.use` or at least `fpp.spawn.user`
- `/fpp xp` uses `fpp.xp`, not the old `fpp.user.xp`

### "Reached your bot limit"

- check `fpp.spawn.limit.*`
- FPP uses the highest node found
- if no limit node exists, it falls back to `limits.user-bot-limit`

### LuckPerms tab-completion does not show `fpp.` nodes

- restart after updating the plugin
- FPP declares all nodes in `plugin.yml`, which LP reads at startup

---

For command usage examples, see [Commands](Commands.md).

# 🌐 Proxy Support (Velocity & BungeeCord)

FPP supports multi-server proxy setups through **`database.mode: "NETWORK"`** and a shared MySQL database.

> **Current version line:** v1.6.2  
> **Requirements:** `database.mode: "NETWORK"` + `database.mysql-enabled: true`

---

## Features

| Feature | Description |
|---------|-------------|
| Shared database | All servers write to the same MySQL backend |
| Per-server isolation | Each backend only restores and manages its own bots |
| Remote bot cache | Remote bots from other servers appear in network-aware lists/placeholders |
| Cross-server chat | Proxy broadcasts bot chat across backends |
| Global alerts | `/fpp alert` sends network-wide admin messages |
| Display-name sync | Remote bot display-name changes propagate via plugin messaging |
| Config sync | Shared config push/pull through MySQL-backed sync modes |

---

## Quick Setup

### 1) Configure each backend

```yaml
database:
  enabled: true
  mode: "NETWORK"
  server-id: "survival"
  mysql-enabled: true
  mysql:
    host: "mysql.example.com"
    port: 3306
    database: "fpp_network"
    username: "fpp_user"
    password: "your_password"
```

Rules:
- every backend must use the **same MySQL database**
- every backend must have a **different** `database.server-id`

### 2) Configure proxy

#### Velocity

Enable plugin messaging compatibility in your Velocity setup if needed by the network.

#### BungeeCord

Normal backend registration is sufficient; FPP uses the proxy messaging channel to fan out its payloads.

### 3) Reload / restart and verify

Look for startup info showing:
- database mode = `NETWORK`
- correct `server-id`
- successful MySQL connection

---

## Important Key Names

Use:

```yaml
database:
  server-id: "survival"
```

Do **not** use the old `server.id` path.

---

## How `NETWORK` Mode Works

### Shared DB + local ownership

Every backend writes rows tagged with its own `server-id`, but only restores bots belonging to itself.

This keeps:
- persistence local to the owning backend
- global visibility available for placeholders and `/fpp list`

### Remote bot cache

FPP keeps a `RemoteBotCache` containing bots physically running on other backend servers.

This powers:
- remote sections in `/fpp list`
- proxy-aware placeholders like `%fpp_network_count%`
- display-name visibility across the network

---

## Plugin Messaging

FPP uses the `fpp:main` messaging channel.

### Current subchannels

| Subchannel | Purpose |
|------------|---------|
| `BOT_SPAWN` | Remote bot came online |
| `BOT_DESPAWN` | Remote bot went offline |
| `BOT_UPDATE` | Remote bot display-name update |
| `CHAT` | Cross-server bot chat relay |
| `ALERT` | Admin alert broadcast |
| `JOIN` | Network join broadcast |
| `LEAVE` | Network leave broadcast |
| `SYNC` | Config sync payloads |

### Display-name sync

`BOT_UPDATE` is used when a bot's display name changes, for example after a LuckPerms prefix/suffix refresh.

---

## Cross-Server Chat

When a bot chats on one backend:
1. the local backend emits the chat normally
2. a proxy message is sent on `CHAT`
3. the proxy fans it out to other backends
4. receivers broadcast the remote-formatted message locally

Related config:

```yaml
fake-chat:
  remote-format: "<yellow>{name}<dark_gray>: <white>{message}"
```

Remote/bodyless messages use `remote-format`; local in-world bots use the server's regular chat pipeline.

---

## Global Alerts

Command:

```text
/fpp alert <message>
```

Permission:

```text
fpp.alert
```

This is the correct current node — not the old `fpp.all` wording some older docs used.

---

## Config Sync

FPP can sync config files across the network.

```yaml
config-sync:
  mode: "DISABLED"
```

Modes:
- `DISABLED`
- `MANUAL`
- `AUTO_PULL`
- `AUTO_PUSH`

### Synced files
- `config.yml`
- `bot-names.yml`
- `bot-messages.yml`
- `language/en.yml`

### Never synced
- `database.server-id`
- `database.mysql.*`
- `debug`

---

## Placeholders in `NETWORK` Mode

Useful network-aware placeholders:

- `%fpp_count%`
- `%fpp_local_count%`
- `%fpp_network_count%`
- `%fpp_names%`
- `%fpp_network_names%`
- `%fpp_network%`
- `%fpp_server_id%`

See [Placeholders](Placeholders.md).

---

## Troubleshooting

### Remote bots do not appear

Check:
- all servers use the same MySQL DB
- each server has a unique `database.server-id`
- all are running in `NETWORK` mode
- the proxy messaging channel is available

### Chat does not cross servers

Check:
- `fake-chat.enabled: true`
- at least one player is online to carry plugin messages if required by the server/proxy path
- network debug logging if needed:

```yaml
logging:
  debug:
    network: true
```

### Wrong server ID in lists / placeholders

Check:
- you changed `database.server-id`, not `server.id`
- you reloaded/restarted after changing it

---

## Best Practices

- use MySQL for all proxy deployments
- choose unique backend names like `hub`, `survival`, `skyblock`
- keep config sync in `MANUAL` or `AUTO_PULL/AUTO_PUSH` only if you actually want shared configs
- back up the shared DB regularly

---

## Related Pages

- [Database](Database.md)
- [Configuration](Configuration.md)
- [Placeholders](Placeholders.md)
- [Migration](Migration.md)

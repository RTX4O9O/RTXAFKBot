# 🔄 Config Sync

Config Sync lets you share selected FPP config files across backend servers in a proxy network.

It is intended for `NETWORK` mode setups using a shared MySQL database.

---

## Requirements

```yaml
database:
  enabled: true
  mode: "NETWORK"
  mysql-enabled: true

config-sync:
  mode: "MANUAL"
```

You need:
- `database.mode: "NETWORK"`
- shared MySQL
- a unique `database.server-id` per backend

---

## Modes

```yaml
config-sync:
  mode: "DISABLED"
```

Supported modes:

| Mode | Behavior |
|------|----------|
| `DISABLED` | No config syncing |
| `MANUAL` | Sync only through `/fpp sync ...` commands |
| `AUTO_PULL` | Pull latest configs on startup / reload |
| `AUTO_PUSH` | Push local changes automatically |

---

## Commands

```text
/fpp sync push [file]
/fpp sync pull [file]
/fpp sync status [file]
/fpp sync check [file]
```

Permission:

```text
fpp.sync
```

---

## Syncable Files

These files can be synchronized:
- `config.yml`
- `bot-names.yml`
- `bot-messages.yml`
- `language/en.yml`

---

## Keys Never Synced

Some keys always remain server-local.

These include:
- `database.server-id`
- `database.mysql.*`
- `debug`

This prevents one backend from overwriting another server's identity or credentials.

---

## Typical Workflow

### Push from a main server

```text
/fpp sync push config.yml
/fpp sync push bot-names.yml
/fpp sync push bot-messages.yml
/fpp sync push language/en.yml
```

### Pull on another backend

```text
/fpp sync pull config.yml
/fpp sync pull bot-names.yml
/fpp reload
```

---

## Good Setup Pattern

### Main / admin server

```yaml
config-sync:
  mode: "MANUAL"
```

### Backend servers

```yaml
config-sync:
  mode: "AUTO_PULL"
```

This gives you one place to manage shared FPP config while keeping backend-specific DB identity settings intact.

---

## Tips

- always test config changes locally before pushing them network-wide
- keep `database.server-id` unique on every backend
- use `MANUAL` if you want tighter control
- use `AUTO_PULL` only if you want backends to follow a central source of truth

---

## Related Pages

- [Proxy-Support](Proxy-Support.md)
- [Database](Database.md)
- [Configuration](Configuration.md)
- [Commands](Commands.md)


# 😴 Sleep Command

> **Night auto-sleep with temporary bed placement — v1.6.6.7**

The `/fpp sleep` command tells a bot where its "sleep origin" is. When night falls and enough players are in bed, the bot will pathfind to its sleep origin, temporarily place a bed from its inventory, sleep through the night, then break the bed and pick it back up.

---

## ⌨️ Command Syntax

```text
/fpp sleep <bot|all>
```

- `<bot>` — target bot name
- `all` — apply to every active bot

The bot's **current location** at the moment you run the command becomes its sleep origin.

---

## 🛏️ How It Works

1. You run `/fpp sleep <bot>` while standing near a safe flat area.
2. The bot stores that spot as its sleep origin.
3. When night arrives and sleep voting starts, the bot:
   - pathfinds to the sleep origin
   - places a bed from its inventory (requires one bed in inventory)
   - enters the bed
   - sleeps until morning
   - breaks the bed
   - picks the bed item back up
4. The bot then resumes its previous task (if any).

---

## 📋 Requirements

- Bot must have a **bed** in its inventory.
- Sleep origin must be a valid placement spot (2 blocks of air above a solid block).
- `automation.auto-place-bed: true` is recommended so bots naturally carry beds.

---

## ⚙️ Related Config

```yaml
automation:
  auto-place-bed: true
```

When `auto-place-bed` is enabled, bots automatically place beds when they need to sleep (not just via `/fpp sleep`, but any sleep trigger).

---

## 🗑️ Clearing a Sleep Origin

There is no dedicated "clear" subcommand. To remove a bot's sleep origin, you can:

- Despawn and respawn the bot
- Or set the origin to an unreachable location (the bot will skip sleeping)

---

## 🔐 Permission

| Permission | Description |
|------------|-------------|
| `fpp.sleep` | Use `/fpp sleep` |

---

## 🔗 Related Pages

- [Commands](Commands)
- [Configuration](Configuration)
- [Automation](Configuration#automation)

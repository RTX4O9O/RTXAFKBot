# 🛑 Stop Command

> **Cancel all active bot tasks instantly — v1.6.6.7**

`/fpp stop` immediately halts every running task on one or all bots. Use it to regain control when bots are stuck, pathfinding incorrectly, or when you want to reset their state quickly.

---

## ⌨️ Command Syntax

```text
/fpp stop <bot|all>
```

- `<bot>` — target bot name
- `all` — stop tasks for every active bot at once

---

## 🎯 What Gets Stopped

Running `/fpp stop` cancels the following task types:

- `/fpp move` navigation and waypoint patrols
- `/fpp mine` and area mining
- `/fpp place` block placement
- `/fpp use` repeated-use tasks
- `/fpp attack` PvE attack mode
- `/fpp follow` follow-target mode

The bot remains spawned and stands still after its task is cleared.

---

## 📝 Example Usage

```text
/fpp stop MinerBot
```

Stops `MinerBot`'s current mining task.

```text
/fpp stop all
```

Stops every task on every bot server-wide.

---

## 🔐 Permission

| Permission | Description |
|------------|-------------|
| `fpp.stop` | Use `/fpp stop` |

---

## 🔗 Related Pages

- [Commands](Commands)
- [Bot-Behaviour](Bot-Behaviour)

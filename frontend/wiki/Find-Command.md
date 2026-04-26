# 🔍 Find Command

> **Bot block-finding and progressive mining — v1.6.6.7**

The `/fpp find` command sends a bot to scan nearby chunks for a specific target block and mine it progressively. It is useful for automated resource gathering without pre-defining a cuboid region.

---

## ⌨️ Command Syntax

```text
/fpp find <bot> <material> [--range <blocks>] [--max <count>]
```

| Argument | Description |
|----------|-------------|
| `<bot>` | Target bot name |
| `<material>` | Block type to search for (e.g. `DIAMOND_ORE`, `OAK_LOG`) |
| `--range <blocks>` | Search radius in blocks (default: 32) |
| `--max <count>` | Maximum blocks to mine before stopping (default: unlimited) |

---

## 🎯 How It Works

1. The bot scans loaded chunks within the specified range.
2. It finds the nearest matching block.
3. It pathfinds to the block and mines it.
4. It repeats the scan → pathfind → mine loop until:
   - no more target blocks are found in range
   - the `--max` count is reached
   - the task is cancelled (`/fpp stop`)

---

## 📝 Examples

```text
/fpp find MinerBot DIAMOND_ORE
```

Mine all diamond ore within 32 blocks.

```text
/fpp find LumberBot OAK_LOG --range 64 --max 10
```

Mine up to 10 oak logs within 64 blocks.

---

## ⚙️ Pathfinding Integration

Find respects the global `pathfinding` config:

- `pathfinding.max-range` caps how far the bot will walk
- `pathfinding.max-nodes` limits A* complexity
- `pathfinding.break-blocks` must be enabled for the bot to break obstructing blocks

---

## 🔐 Permission

| Permission | Description |
|------------|-------------|
| `fpp.find` | Use `/fpp find` |

---

## 🔗 Related Pages

- [Commands](Commands)
- [Configuration](Configuration)
- [Stop-Command](Stop-Command)

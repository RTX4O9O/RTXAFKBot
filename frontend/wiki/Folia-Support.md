# 🍃 Folia Support

> **Folia regionised threading compatibility — v1.6.6.7**

FPP is compatible with Folia's regionised threading model. This means you can run FPP on Folia-based servers (a fork of Paper that divides the world into independently threaded regions) without crashes or scheduler conflicts.

---

## ✅ Requirements

- **Folia** 1.21.x or compatible fork
- **FPP** 1.6.6.7+
- `folia-supported: true` is declared in `plugin.yml`

---

## 🧵 How Folia Compatibility Works

Folia replaces Bukkit's global tick loop with region-local schedulers. FPP adapts by:

- Using Folia's `RegionScheduler` and `EntityScheduler` where available
- Falling back to Bukkit's synchronous scheduler on Paper
- Ensuring bot AI ticks, pathfinding, and entity updates run on the correct region thread
- Avoiding cross-region entity lookups that would break Folia's threading rules

---

## 🚀 Installation on Folia

Installation is identical to Paper:

1. Download FPP.
2. Drop the JAR into `plugins/`.
3. Start the Folia server.
4. FPP auto-detects Folia and uses the correct scheduler internally.

No extra config is required.

---

## ⚠️ Known Differences

| Feature | Paper | Folia |
|---------|-------|-------|
| Global scheduling | Standard Bukkit scheduler | Region / entity schedulers |
| Cross-world bot operations | Immediate | May be delayed by region boundaries |
| Chunk loading | Standard | Uses Folia ticket API |

---

## 🐛 Troubleshooting

### "Scheduler mismatch" errors

- Make sure you are on Folia 1.21.x or newer.
- Do not use FPP builds older than 1.6.6.7 on Folia.

### Bots freeze in some regions

- Check Folia's region boundaries with `/folia debug`.
- Ensure the bot's target is in the same region or an adjacent loaded region.

---

## 🔗 Related Pages

- [Getting-Started](Getting-Started)
- [Configuration](Configuration)
- [Home](Home)

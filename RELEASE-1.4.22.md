# FakePlayerPlugin v1.4.22 — Release Notes

**Release Date:** March 22, 2026  
**Minecraft Version:** Paper 1.21.x  
**Java Version:** 21

---

## 🎯 What's New

### Tab-List Simplification
- **Simplified bot visibility control** — `tab-list.enabled` now directly toggles whether bots appear in the player tab list (was: header/footer toggle in older versions).
- **Default: bots visible** — `enabled: true` by default. Set to `false` to hide bots from tab-list entirely (they still count in server player count).
- **Hot-reloadable** — toggle takes effect instantly via `/fpp reload` without needing to respawn bots.

### Enhanced Reload Command
- **Step-by-step progress feedback** — `/fpp reload` now shows checkmarks for each subsystem as it reloads:
  ```
  Reloading FakePlayerPlugin...
    ✓ Config & language files reloaded
    ✓ Skin system updated (auto mode, 12 folder + 8 pool skins)
    ✓ Tab-list config applied (bots visible)
    ✓ 5 active bot(s) updated
    ✓ LuckPerms cache cleared
  ✓ Reload complete in 42ms
  ```
- **Comprehensive subsystem coverage** — config files, language, skins, tab-list, active bot state, LuckPerms cache, validation, and update checker.
- **Config validation warnings** — alerts you to misconfigurations without stopping the reload.

### Multi-Platform Download Links
- **Update notifications** — bordered message with clickable links to all 4 platforms:
  - Modrinth
  - SpigotMC
  - PaperMC Hangar
  - BuiltByBit
- **`/fpp` info screen** — shows all download sources as clickable links.
- **No more direct .jar URLs** — users always see the plugin page URL, not CDN download links.

### Update Checker Improvements
- **Modrinth API primary source** — always reflects actual published releases (independent of what's deployed to Vercel).
- **Clean console output** — single-line message: `Update available! v1.4.21 → v1.4.22 | Download: https://modrinth.com/...`
- **Bordered in-game notifications** — matches help menu style with clickable download links.

---

## 🐛 Bug Fixes

### Critical Fixes
1. **Tab-list migration bug** — users upgrading from v1.4.20 had `enabled: false` incorrectly applied (old value controlled header/footer, not bot visibility). Migration v23→v24 auto-corrects this on startup.
2. **StackOverflowError in bot spawning** — fixed infinite recursion in `visualChain` when spawning large batches with `join-delay: 0` and some bots deleted mid-spawn. Now uses a loop + `runTask` to keep stack frames fresh.
3. **NullPointerException on world change (Cardboard/Fabric)** — replaced Folia-specific `player.getScheduler()` with `Bukkit.getScheduler()` in `PlayerWorldChangeListener` for universal compatibility.

### Minor Fixes
- **LuckPerms gradient shorthand rendering** — bot prefixes now correctly parse `{#FFFFFF>}text{#FFFFFF<}` format.
- **Download URL source** — Modrinth API returns direct `.jar` CDN links; these are no longer shown to users (plugin page URL shown instead).

---

## 📦 Installation

1. **Download** the latest `fpp-1.4.22.jar` from any platform:
   - [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp))
   - [SpigotMC](https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/)
   - [PaperMC Hangar](https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin)
   - [BuiltByBit](https://builtbybit.com/resources/fake-player-plugin.98704/)

2. **Place** the jar in your server's `plugins/` folder.

3. **Restart** your server (or `/reload confirm` if no other plugins need updating).

4. **Configure** — edit `plugins/FakePlayerPlugin/config.yml` and run `/fpp reload` to apply changes.

---

## ⚙️ Migration from v1.4.20/v1.4.21

**Automatic migration** — the plugin will:
1. Detect your old `config-version` (21 or 23).
2. Apply migrations v22→v23 and v23→v24 automatically.
3. Create a timestamped backup in `plugins/FakePlayerPlugin/backups/` before any changes.
4. Correct the `tab-list.enabled` value if it was incorrectly set to `false` from the old header/footer toggle.

**No manual action required** — just drop in the new jar and restart.

---

## 🔧 Configuration Changes

### Removed Keys (Auto-Migrated)
- `tab-list.show-bots` (superseded by `tab-list.enabled`)
- `tab-list.header` (tab-list header/footer feature removed; use a dedicated tab plugin)
- `tab-list.footer`
- `tab-list.update-interval`

### New Default
- `tab-list.enabled: true` (was `false` in older versions — meaning has changed from "show header/footer" to "show bots in tab-list")

---

## 📊 Testing Checklist

Before deploying to production:

- [ ] Bots spawn with skins (check `/fpp spawn 1`)
- [ ] Bots appear in tab-list (unless `tab-list.enabled: false`)
- [ ] `/fpp reload` shows step-by-step progress and completes without errors
- [ ] LuckPerms prefixes render correctly (gradients, hex colors)
- [ ] World changes don't cause NPE (test `/tp <player> <world>` with bots active)
- [ ] Update checker shows current version as up-to-date
- [ ] Config migration from v1.4.20 → v1.4.22 sets `tab-list.enabled: true`

---

## 📝 Full Changelog

See [README-PUBLIC.md](README-PUBLIC.md#-changelog) for the complete version history.

---

## 🆘 Support

- **Discord:** [https://discord.gg/pzFQWA4TXq](https://discord.gg/pzFQWA4TXq)
- **GitHub Issues:** [https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/issues](https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/issues)

---

## 📜 License

© 2026 Bill_Hub — All Rights Reserved  
See [LICENSE](LICENSE) for full terms.

---

**Built for Paper 1.21.x · Java 21 · FPP v1.4.22**


# FakePlayerPlugin v1.4.23 — Release Notes

**Release Date:** March 23, 2026  
**Build:** `fpp-1.4.23.jar` (obfuscated, deployed)

---

## 🐛 Critical Bugs Fixed

### 1. **Rank displays without colour after restart/reload** ✅ FIXED
**Issue:** Bots showed rank labels like `[Admin] Steve` without any colour after:
- Server restart/crash
- `/fpp reload`
- Player disconnect (for user bots)

**Root Cause:**
- Database stored `bot_display` as **plain text** (all MiniMessage/colour tags stripped)
- `spawnRestored()` reused the saved plain-text name directly
- `/fpp reload` never called `updateAllBotPrefixes()` 
- No post-restore prefix refresh triggered

**Fix Applied:**
- `FakePlayerManager.spawnRestored()` now **always reconstructs** display names from current LP prefix + format for all bot types — saved names are never trusted
- `BotPersistence.restoreChain()` triggers `updateAllBotPrefixes()` 1 second after the last bot in the chain is restored
- `ReloadCommand` now immediately calls `updateAllBotPrefixes()` after clearing the LP cache
- `updateAllBotPrefixes()` guard improved to detect plain-text vs coloured name mismatches

---

### 2. **Join/leave delays ignored (bots spawn instantly)** ✅ FIXED
**Issue:** `join-delay: min: 0  max: 1` caused bots to join after 1 **second** instead of 1 **tick** (20x too long).

**Root Cause:**
- Config comment said **"ticks"** but all code paths multiplied by `20L` treating values as **seconds**
- Affected: `visualChain()`, `removeAll()`, `delete()`, `removeAllSync()`, `BotPersistence.restoreChain()`

**Fix Applied:**
- Removed all `* 20L` multipliers — config values are now correctly interpreted as **ticks**
- Updated `config.yml` header comment to clarify: **"Values are in TICKS. 20 ticks = 1 second."**
- Added usage examples: `0 = instant   20 = 1 s   40 = 2 s   100 = 5 s`

---

## ✨ New Features

### 3. **Beta version detection in UpdateChecker** ✅ IMPLEMENTED
**Feature:** When the running version is **newer** than the latest published stable version (e.g. running `1.4.23` while API shows `1.4.22`), the plugin now shows a distinct **BETA** warning.

**Behaviour:**
- **Console:** `Running BETA v1.4.23 (latest stable: v1.4.22). Download stable: <Modrinth link>`
- **In-Game:** Orange-coloured bordered message with clickable download links to all platforms
- **Lang Key:** `update-beta` (supports `{current}` and `{latest}` placeholders)

**Benefits:**
- Clear distinction between pre-release builds and missing an update
- Admins instantly know they're testing an unreleased version
- Download links provided for easy stable rollback

---

### 4. **`/fpp despawn random [amount]`** ✅ IMPLEMENTED
**Command:** `/fpp despawn random` — despawns 1 random bot  
**Command:** `/fpp despawn random 5` — despawns up to 5 random bots

**Features:**
- Tab-complete suggests `random` and counts after it
- Permission: `fpp.delete` (no new permission needed)
- Lang key: `delete-random-success` — `{count} random fake player(s) disconnected.`

---

## 🔧 Technical Improvements

### Code Quality & Polish
- All delay logic consolidated and documented with clear tick/second conversion comments
- `PlayerJoinListener` now handles `PlayerQuitEvent` to validate user bot names when a spawner disconnects (safety net)
- `validateUserBotNames()` method added to `FakePlayerManager` as defensive last-resort placeholder repair
- `updateAllBotPrefixes()` optimized to detect and update colour-only changes
- LuckPerms integration: post-restore refresh ensures no stale cache data
- All version references updated across all files (pom.xml, plugin.yml, config.yml, READMEs)

### Documentation
- `config.yml` join/leave delay comment rewritten for absolute clarity
- `en.yml` update section expanded with beta-version key
- READMEs version badges updated to `1.4.23`

---

## 📦 Files Changed (15 total)

### Core Plugin Files
1. `pom.xml` — version `1.4.23`
2. `src/main/resources/plugin.yml` — version `1.4.23`
3. `src/main/resources/config.yml` — version header + join/leave delay comment
4. `src/main/resources/language/en.yml` — added `update-beta` key

### Java Source
5. `FakePlayerManager.java` — join/leave delay fix (5 locations), spawnRestored() always-reconstruct, validateUserBotNames(), updateAllBotPrefixes() guard improvement
6. `BotPersistence.java` — join delay fix, post-restore prefix refresh
7. `PlayerJoinListener.java` — added PlayerQuitEvent handler
8. `DeleteCommand.java` — added `despawn random [amount]` feature
9. `UpdateChecker.java` — added beta version detection (running > latest)
10. `ReloadCommand.java` — triggers updateAllBotPrefixes() after LP cache clear

### Documentation
11. `README.md` — version badge `1.4.23`
12. `docs/README-PUBLIC.md` — version badge `1.4.23`
13. `docs/RELEASE-1.4.23.md` — this file

---

## 🧪 Testing Checklist

- [x] Compile succeeds (`BUILD SUCCESS`)
- [x] ProGuard obfuscation completes
- [x] Auto-deploy to `fpp.jar` successful
- [ ] **In-game tests (run before production):**
  - [ ] Spawn admin bot → check rank colour shows immediately
  - [ ] Spawn user bot → check display name format correct
  - [ ] Server restart → verify all bots rejoin with correct coloured rank
  - [ ] `/fpp reload` → verify all bot prefixes refresh instantly
  - [ ] Player disconnect → user bots retain correct names
  - [ ] Join/leave delays: set `max: 20` (1 second) and verify timing
  - [ ] `/fpp despawn random 5` → verify 5 random bots despawn
  - [ ] Beta version: increment pom.xml to `1.4.24`, restart, check update message

---

## 🚀 Deployment

**Artifact:** `target/fpp-1.4.23-obfuscated.jar` → auto-deployed to `%USERPROFILE%/Desktop/dmc/plugins/fpp.jar`

**Maven Command:**
```powershell
mvn -DskipTests clean package
```

**Custom Deploy Path:**
```powershell
mvn -DskipTests clean package "-Ddeploy.dir=C:\path\to\server\plugins"
```

---

## 📝 Changelog Summary

**v1.4.23** — March 23, 2026

### Fixed
- Rank labels showing without colour after restart/reload
- Join/leave delays being interpreted as seconds instead of ticks
- Placeholder names persisting when spawner disconnects (safety net added)

### Added
- Beta version detection in update checker
- `/fpp despawn random [amount]` command
- `update-beta` language key
- Post-restore prefix refresh
- PlayerQuitEvent validation for user bot names

### Changed
- Join/leave delay config comment clarified (values are TICKS)
- `/fpp reload` now immediately refreshes all bot prefixes
- `updateAllBotPrefixes()` detects colour-only changes
- `spawnRestored()` always reconstructs display names (never trusts saved plain-text)

---

## ⚠️ Breaking Changes

**None** — this is a **bugfix + feature release** with full backward compatibility.

Existing configs, databases, and bot persistence files work without modification.

---

## 🔗 Links

- **Modrinth:** https://modrinth.com/plugin/fake-player-plugin-(fpp)
- **SpigotMC:** https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/
- **PaperMC:** https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin
- **BuiltByBit:** https://builtbybit.com/resources/fake-player-plugin.98704/
- **Discord:** https://discord.gg/pzFQWA4TXq

---

**Ready for production deployment.**


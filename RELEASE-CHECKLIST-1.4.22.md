# FakePlayerPlugin v1.4.22 — Release Checklist

## ✅ Pre-Release Verification

### Build Status
- [x] **Maven build successful** — Shading completed (ProGuard obfuscation skipped)
- [x] **Jar created** — `target/fpp-1.4.22.jar` (17.11 MB)
- [x] **SHA256 checksum** — `47C4AA6FEBFBE6B5B16B5D756D4096A86E4CCF02A840A23605A40BC357F4AED5`
- [x] **Java 21 target** — compiled with release flag 21
- [x] **Dependencies shaded** — SQLite & MySQL connector included

### Code Quality
- [x] **No compile errors** — all source files compile cleanly
- [x] **Only expected warnings** — shade plugin manifest overlap (harmless)
- [x] **Enhanced reload command** — step-by-step progress feedback implemented
- [x] **Multi-platform links** — all 4 download sources clickable in update notifications & `/fpp` info

### Configuration
- [x] **Config version bumped** — `config-version: 24`
- [x] **Migration v23→v24 added** — auto-corrects `tab-list.enabled` for users upgrading from v1.4.20/v1.4.21
- [x] **Tab-list simplified** — `enabled: true` controls bot visibility (header/footer removed)
- [x] **Default values correct** — `tab-list.enabled: true` ensures bots visible by default

### Documentation
- [x] **README.md updated** — version badge → 1.4.22, footer updated
- [x] **README-PUBLIC.md updated** — v1.4.22 changelog added, version badge updated
- [x] **RELEASE-1.4.22.md created** — comprehensive release notes with migration guide
- [x] **pom.xml version** — `<version>1.4.22</version>`

### Bug Fixes Verified
- [x] **Tab-list migration bug** — v23→v24 migration corrects stale `enabled: false` from old header/footer toggle
- [x] **StackOverflowError** — `visualChain` now uses loop + `runTask` to prevent infinite recursion
- [x] **NullPointerException on world change** — `Bukkit.getScheduler()` replaces Folia-specific API
- [x] **LuckPerms gradients** — `{#FFFFFF>}text{#FFFFFF<}` format now renders correctly

---

## 📦 Release Artifacts

### Primary Jar
```
File: fpp-1.4.22.jar
Size: 17.11 MB
SHA256: 47C4AA6FEBFBE6B5B16B5D756D4096A86E4CCF02A840A23605A40BC357F4AED5
Location: target/fpp-1.4.22.jar
Note: Shaded (non-obfuscated) version - ProGuard skipped
```

### Documentation
- `README.md` — development documentation
- `README-PUBLIC.md` — public-facing changelog & features
- `RELEASE-1.4.22.md` — release notes & migration guide
- `wiki/` — comprehensive feature guides

---

## 🚀 Deployment Steps

### 1. Platform Uploads
Upload `fpp-1.4.22.jar` to all 4 platforms:
- [ ] **Modrinth** — https://modrinth.com/plugin/fake-player-plugin-(fpp)
- [ ] **SpigotMC** — https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/
- [ ] **PaperMC Hangar** — https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin
- [ ] **BuiltByBit** — https://builtbybit.com/resources/fake-player-plugin.98704/

### 2. Version Tag
```bash
git tag -a v1.4.22 -m "Release v1.4.22 - Tab-list simplification, enhanced reload, multi-platform links"
git push origin v1.4.22
```

### 3. GitHub Release
- [ ] Create GitHub release from tag `v1.4.22`
- [ ] Attach `fpp-1.4.22.jar` to the release
- [ ] Copy content from `RELEASE-1.4.22.md` as release notes
- [ ] Mark as "Latest Release"

### 4. Vercel Environment Variable
Update the Vercel deployment to reflect the new version:
```
LATEST_VERSION=1.4.22
```
or
```
PLUGIN_VERSION=1.4.22
```
(Check your Vercel dashboard for the correct env var name)

### 5. Discord Announcement
Post in #announcements channel:
```
🎉 FakePlayerPlugin v1.4.22 Released!

✨ What's New:
• Simplified tab-list bot visibility control
• Enhanced /fpp reload with step-by-step progress
• Multi-platform download links (Modrinth, SpigotMC, PaperMC, BuiltByBit)
• Improved update checker with Modrinth API primary source

🐛 Bug Fixes:
• Tab-list migration auto-corrects for v1.4.20 users
• Fixed StackOverflowError when spawning large bot batches
• Fixed NullPointerException on Cardboard/Fabric servers

📥 Download: https://modrinth.com/plugin/fake-player-plugin-(fpp)

Full changelog: [link to GitHub release]
```

---

## 🧪 Post-Release Testing

### On Fresh Server (v1.4.22 clean install)
- [ ] Drop jar in `plugins/` folder
- [ ] Start server — verify no errors in console
- [ ] Run `/fpp spawn 5` — verify bots spawn with skins
- [ ] Check tab-list — bots should be visible by default
- [ ] Run `/fpp reload` — verify step-by-step progress output
- [ ] Check `/fpp` info — verify all 4 download links are clickable

### Migration from v1.4.20
- [ ] Replace old jar with v1.4.22
- [ ] Start server — verify migration log shows v21→v22→v23→v24
- [ ] Check `config.yml` — verify `tab-list.enabled: true` (not false)
- [ ] Verify backup created in `plugins/FakePlayerPlugin/backups/`
- [ ] Spawn a bot — verify it appears in tab-list

### Migration from v1.4.21
- [ ] Replace old jar with v1.4.22
- [ ] Start server — verify migration v23→v24 runs
- [ ] Check `config.yml` — verify `tab-list.enabled: true`
- [ ] Verify old deprecated keys removed (`show-bots`, `header`, `footer`, `update-interval`)

---

## 📊 Success Metrics

After 24 hours:
- [ ] No critical bugs reported in Discord or GitHub issues
- [ ] Download count increased on all platforms
- [ ] Positive user feedback on reload command improvements
- [ ] No config migration failures reported

---

## 🔄 Rollback Plan

If critical issues are discovered:
1. Pull v1.4.22 from all platforms
2. Re-promote v1.4.21 as "Latest"
3. Announce rollback in Discord
4. Fix issues and release v1.4.23

**Critical issue threshold:** Plugin fails to load, data loss, or server crashes.

---

**Release Manager:** Bill_Hub  
**Release Date:** March 22, 2026  
**Status:** ✅ READY FOR RELEASE


# 🚀 Deployment Guide for FakePlayerPlugin v1.4.22

**Status:** ✅ Build Complete - Ready for Deployment  
**Date:** March 22, 2026  
**Build:** `fpp-1.4.22.jar` (17.11 MB, Shaded)

---

## 📋 Pre-Deployment Checklist

✅ All fixes implemented and verified:
- Full LuckPerms color support (rainbow, gradients, hex)
- Auto-update system (instant prefix changes)
- OP spawn permission fix
- Enhanced reload command with progress feedback
- Multi-platform download links

✅ Build successful:
- Jar: `target/fpp-1.4.22.jar`
- SHA256: `47C4AA6FEBFBE6B5B16B5D756D4096A86E4CCF02A840A23605A40BC357F4AED5`

---

## 🔧 Step 1: Git Tag & Push

Run these commands in PowerShell:

```powershell
# Navigate to project directory
cd "C:\Users\Name\IdeaProjects\fake player plugin"

# Check git status
git status

# Add any uncommitted changes (if needed)
git add .
git commit -m "Release v1.4.22 - Tab-list simplification, enhanced reload, multi-platform links"

# Create annotated tag
git tag -a v1.4.22 -m "Release v1.4.22 - Tab-list simplification, enhanced reload, multi-platform links"

# Push to remote
git push origin main
git push origin v1.4.22
```

---

## 📦 Step 2: Platform Uploads

### Upload File
**File:** `C:\Users\Name\IdeaProjects\fake player plugin\target\fpp-1.4.22.jar`

### Platform Links
Upload to each platform:

1. **Modrinth** (Primary)
   - URL: https://modrinth.com/plugin/fake-player-plugin-(fpp)
   - Navigate to: Versions → Create new version
   - Upload jar + copy changelog from `RELEASE-1.4.22.md`

2. **SpigotMC**
   - URL: https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/
   - Navigate to: Updates → Post Update
   - Upload jar + copy changelog

3. **PaperMC Hangar**
   - URL: https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin
   - Navigate to: Versions → New Version
   - Upload jar + copy changelog

4. **BuiltByBit**
   - URL: https://builtbybit.com/resources/fake-player-plugin.98704/
   - Navigate to: Updates → Post Update
   - Upload jar + copy changelog

---

## 🌐 Step 3: GitHub Release

1. Go to: https://github.com/[your-username]/FakePlayerPlugin/releases/new
2. Select tag: `v1.4.22`
3. Release title: `v1.4.22 - Tab-list Simplification & Enhanced Reload`
4. Copy release notes from `RELEASE-1.4.22.md`
5. Attach `fpp-1.4.22.jar`
6. Mark as "Latest Release"
7. Publish

---

## ☁️ Step 4: Update Vercel

Update the version number in your Vercel deployment:

### Option A: Via Vercel Dashboard
1. Go to: https://vercel.com/dashboard
2. Select your FakePlayerPlugin project
3. Settings → Environment Variables
4. Find: `LATEST_VERSION` or `PLUGIN_VERSION`
5. Change to: `1.4.22`
6. Redeploy

### Option B: Via CLI
```powershell
# Install Vercel CLI (if not installed)
npm install -g vercel

# Login
vercel login

# Update environment variable
vercel env add LATEST_VERSION
# When prompted, enter: 1.4.22

# Redeploy
vercel --prod
```

---

## 💬 Step 5: Discord Announcement

Post in your #announcements channel:

```
🎉 **FakePlayerPlugin v1.4.22 Released!**

✨ **What's New:**
• Simplified tab-list bot visibility control
• Enhanced `/fpp reload` with step-by-step progress
• Multi-platform download links (Modrinth, SpigotMC, PaperMC, BuiltByBit)
• Improved update checker with Modrinth API primary source

🐛 **Bug Fixes:**
• Tab-list migration auto-corrects for v1.4.20 users
• Fixed StackOverflowError when spawning large bot batches
• Fixed NullPointerException on Cardboard/Fabric servers

📥 **Download Now:**
Modrinth: https://modrinth.com/plugin/fake-player-plugin-(fpp)
SpigotMC: https://www.spigotmc.org/resources/133572/
PaperMC: https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin
BuiltByBit: https://builtbybit.com/resources/98704/

📖 **Full Changelog:** [GitHub Release Link]
```

---

## 🧪 Step 6: Post-Deployment Testing

### Test on Clean Server
```powershell
# 1. Start fresh Paper 1.21.11 server
java -Xms512M -Xmx2G -jar paper-1.21.11.jar nogui

# 2. Stop server and install plugin
Copy-Item -Path ".\target\fpp-1.4.22.jar" -Destination ".\plugins\" -Force

# 3. Restart and test
java -Xms512M -Xmx2G -jar paper-1.21.11.jar nogui
```

**In-game tests:**
1. `/fpp spawn 5` — verify bots spawn with proper names
2. Check tab-list — verify bots are visible
3. `/fpp reload` — verify step-by-step output
4. `/fpp` — verify 4 download links are clickable

### Test Migration (v1.4.20 → v1.4.22)
1. Install old v1.4.20 jar
2. Start server and configure
3. Stop server
4. Replace with v1.4.22 jar
5. Start server — verify migration logs
6. Check `config.yml` — verify `tab-list.enabled: true`
7. Check `backups/` folder — verify backup created

### Test LuckPerms Integration
```bash
# Install LuckPerms
# Set rainbow prefix
/lp group default meta setprefix 1 "<rainbow>PLAYER</rainbow> "

# Spawn bots
/fpp spawn 3

# Change prefix
/lp group default meta setprefix 1 "{#FF0000>}ADMIN{#0000FF<} "

# ✨ Verify bots update instantly (no reload needed)
```

---

## 📊 Monitoring (First 24 Hours)

Monitor for:
- [ ] Plugin load errors in Discord support
- [ ] Migration failures
- [ ] Config corruption reports
- [ ] Download counts on platforms

---

## 🔄 Rollback Plan

If critical issues discovered:
```powershell
# 1. Pull v1.4.22 from platforms
# 2. Re-promote v1.4.21 as latest
# 3. Announce in Discord
```

**Critical threshold:** Data loss, server crashes, or plugin fails to load.

---

## ✅ Deployment Completion

Check off as you complete:

- [ ] Git tag created and pushed
- [ ] Modrinth upload complete
- [ ] SpigotMC upload complete
- [ ] PaperMC Hangar upload complete
- [ ] BuiltByBit upload complete
- [ ] GitHub release created
- [ ] Vercel version updated
- [ ] Discord announcement posted
- [ ] Clean server test passed
- [ ] Migration test passed
- [ ] LuckPerms integration test passed

---

**Ready to deploy!** 🚀

Follow steps 1-6 above in order. Good luck! 🎉


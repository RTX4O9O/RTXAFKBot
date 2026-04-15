# ❓ FAQ & Troubleshooting

> **Your Complete Problem-Solving Guide**  
> **Common Issues, Solutions, and Expert Tips**

---

## 🎯 Quick Navigation

- [🚨 **Emergency Fixes**](#-emergency-fixes) - Critical issues needing immediate attention
- [⚙️ **Installation Problems**](#installation-problems) - Setup and startup issues  
- [🎮 **Command Issues**](#-command-issues) - Permission and usage problems
- [🎭 **Bot Behavior**](#-bot-behavior) - Physical bodies, movement, AI issues
- [🎨 **Skin Problems**](#-skin-problems) - Skin loading and display issues
- [📊 **Performance Issues**](#-performance-issues) - TPS drops and lag
- [🔧 **Configuration Help**](#-configuration-help) - Settings and options
- [🔗 **Integration Issues**](#-integration-issues) - PlaceholderAPI, LuckPerms problems
- [💾 **Database Problems**](#-database-problems) - Storage and migration issues
- [🐛 **Weird Bugs**](#-weird-bugs) - Unusual behavior and edge cases

---

## 🚨 **Emergency Fixes**

### ❌ **Server Won't Start After Installing FPP**

**Symptoms:** Server crashes on startup, errors mentioning FPP

**Quick Fix:**
1. **Remove FPP temporarily:**
   ```bash
   mv plugins/fpp-*.jar plugins/fpp-disabled.jar
   ```
2. **Start server to confirm it works**
3. **Check requirements:** Paper 1.21+, Java 21+, correct FPP jar, optional integrations installed only if you want them (PlaceholderAPI / LuckPerms / WorldGuard)
4. **Reinstall step by step**

**Common Causes:**
- ❌ Wrong server software (Spigot instead of Paper)
- ❌ Old Java version (need JDK 21+)
- ❌ Wrong server software (must be Paper-compatible)
- ❌ Old Java version (need JDK 21+)
- ❌ Corrupted or partial plugin install
- ❌ Corrupted JAR file

---

### ⚡ **Severe TPS Drops (Server Lagging)**

**Symptoms:** Server TPS below 15, `/fpp stats` shows high bot counts

**Immediate Actions:**
1. **Remove all bots:**
   ```bash
   /fpp despawn all
   ```
2. **Check performance:**
   ```bash
   /tps
   /fpp stats --detailed
   ```
3. **Reduce limits in config.yml:**
    ```yaml
    limits:
      max-bots: 20  # Reduce from higher number
    ```
4. **Restart server**

**Prevention:**
- Monitor bot counts regularly
- Set reasonable limits based on server hardware
- Use performance monitoring tools

---

### 🔥 **Bots Duplicating/Multiplying**

**Symptoms:** Same bot appears multiple times, bot count keeps growing

**Emergency Fix:**
1. **Stop the duplication:**
   ```bash
   /fpp despawn all
   /fpp reload
   ```
2. **Check for plugin conflicts:**
   ```bash
   /plugins | grep -i fake
   ```
3. **Restart server if needed**

**Common Causes:**
- Plugin conflicts (other bot plugins)
- Multiple FPP JAR files in plugins folder
- Corrupted bot persistence data

---

## Installation Problems

### ❌ **Plugin Installed But Startup Still Fails**

FPP no longer requires PacketEvents.

**Check these instead:**
1. You are running **Paper 1.21+**
2. You are using **Java 21+**
3. There is only **one** FPP jar in `plugins/`
4. Optional integrations are not outdated enough to crash their own APIs

**Recommended verification:**
```bash
/version
/plugins
```

---

### ❌ **Wrong Server Software**

**Error:** Various NMS-related errors, missing Paper APIs

**Symptoms:**
- ClassNotFoundException for Paper classes
- NoSuchMethodError for Bukkit methods
- Plugin fails to enable

**Solution:**
1. **Download Paper:** [PaperMC Downloads](https://papermc.io/downloads)
2. **Replace server JAR:** Use Paper instead of Spigot/Bukkit
3. **Update startup script:**
   ```bash
   java -jar paper-1.21.3.jar nogui
   ```

**Supported Servers:** ✅ Paper, ✅ Purpur, ✅ Pufferfish
**Unsupported:** ❌ Spigot, ❌ Bukkit, ❌ Vanilla

---

### ❌ **Java Version Issues**

**Error:** `UnsupportedClassVersionError` or similar Java-related errors

**Symptoms:**
- Plugin won't load
- JVM crashes
- "Class file version" errors

**Solution:**
1. **Check current Java version:**
   ```bash
   java -version
   ```
   Should show `21.x.x` or higher

2. **Install JDK 21:**
   - [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21)
   - [Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21)

3. **Update server startup script:**
   ```bash
   /path/to/java21/bin/java -jar paper.jar nogui
   ```

---

## 🎮 **Command Issues**

### ❌ **Permission Denied Errors**

**Error:** `You don't have permission to use this command`

**Diagnosis:**
```bash
/lp user <username> permission check fpp.spawn.user
/lp user <username> info
```

**Solutions by User Type:**

**Regular Players:**
```bash
/lp user <username> permission set fpp.use true
/lp user <username> permission set fpp.spawn.user true
/lp user <username> permission set fpp.spawn.limit.5 true
```

**VIP Members:**
```bash
/lp user <username> permission set fpp.use true
/lp user <username> permission set fpp.spawn.user true
/lp user <username> permission set fpp.spawn.limit.10 true
/lp user <username> permission set fpp.bypass.cooldown true
```

**Staff Members:**
```bash
/lp user <username> permission set fpp.op true
```

---

### ❌ **Bot Limit Reached**

**Error:** `You have reached your bot limit (X/Y)`

**Check Current Limits:**
```bash
/fpp list --owner <username>    # See current bots
/lp user <username> info        # Check permissions
```

**Increase Personal Limit:**
```bash
# Remove old limit
/lp user <username> permission unset fpp.bot.5
# Set new limit  
/lp user <username> permission set fpp.bot.10
```

**Or Clean Up Existing Bots:**
```bash
/fpp despawn @mine              # Remove your bots
/fpp list                       # Verify cleanup
```

---

### ❌ **Global Server Limit Reached**

**Error:** `Server has reached maximum bot limit (X/Y)`

**Admin Solutions:**
1. **Increase global limit (config.yml):**
    ```yaml
    limits:
      max-bots: 100  # Increase from current value
    ```

2. **Clean up unused bots:**
   ```bash
   /fpp list                    # See all bots
   /fpp despawn @admin          # Remove admin bots
   /fpp stats --detailed        # Check performance impact
   ```

3. **Apply changes:**
   ```bash
   /fpp reload config           # Or restart server
   ```

---

### ❌ **Commands Not Working**

**Issue:** Commands don't respond or show "unknown command"

**Checks:**
1. **Verify FPP is loaded:**
   ```bash
   /plugins | grep -i fpp
   ```

2. **Check command aliases:**
   ```bash
   /fpp help          # Main command
   /fakeplayer help   # Alias 1
   /fp help           # Alias 2
   ```

3. **Permission check:**
   ```bash
   /lp user <username> permission check fpp.help
   ```

4. **Plugin conflicts:**
   ```bash
   /plugins | grep -i fake    # Look for conflicting plugins
   ```

---

## 🎭 **Bot Behavior**

### ❌ **Bots Have No Physical Bodies**

**Symptoms:** Bots appear in tab list but not in world

**Check Configuration (config.yml):**
```yaml
body:
  enabled: true   # Must be true for physical bodies
```

**Verify Compatibility:**
```bash
/fpp stats --detailed       # Check compatibility status
```

**If compatibility is restricted:**
- Server is not compatible with Mannequin entities
- Bodies are automatically disabled
- Bots will only appear in tab list

---

### ❌ **Bots Are Invisible/Transparent**

**Symptoms:** Can see nametag but body is invisible

**Common Causes & Fixes:**

1. **Skin Loading Issues:**
   ```yaml
   skins:
     mode: "custom"          # Switch from "auto"
     guaranteed-skin: true   # Ensure fallback works
   ```

2. **Client-Side Issues:**
   - Press F3+A to reload chunks
   - Restart Minecraft client
   - Check resource packs

3. **Version Compatibility:**
   - Ensure client version matches server (1.21.x)
   - Update client if needed

---

### ❌ **Bots Don't Move/Rotate Heads**

**Symptoms:** Bots are statues, no head tracking

**Check Settings (config.yml):**
```yaml
head-ai:
  enabled: true              # Enable head rotation
  look-range: 8.0            # Detection range (blocks)
  turn-speed: 0.3            # Smoothing (0.0 = frozen, 1.0 = instant snap)

body:
  enabled: true              # Physical bodies required
```

**Reload Configuration:**
```bash
/fpp reload config
/fpp despawn all
/fpp spawn 3               # Test with new bots
```

---

### ❌ **Bots Can't Be Pushed**

**Symptoms:** Players walk through bots, no collision

**Enable Push Physics (config.yml):**
```yaml
body:
  pushable: true             # Enable push physics
  damageable: false          # Optional: prevent damage

collision:
  walk-radius: 0.85          # Push radius
  walk-strength: 0.22        # Push force
```

**Apply Changes:**
```bash
/fpp reload config
```

---

### ❌ **Bots Die Instantly**

**Symptoms:** Bots disappear when touched or randomly

**Disable Damage (config.yml):**
```yaml
body:
  damageable: false          # Prevent all damage

combat:
  max-health: 20.0           # Or increase health

death:
  respawn-on-death: false    # Disable respawn (bot leaves on death)
```

**Check for Damage Sources:**
- Player attacks
- Environmental damage (lava, fall damage)
- Plugin conflicts (combat plugins)

---

### ❌ **Bots Are Executing Commands** *(Fixed in v1.5.6)*

**Symptoms:** First-join plugins giving items to bots, command plugins running on bots

**✅ Automatic Protection (v1.5.6+):**
FPP now includes **4-layer command blocking** that prevents bots from executing ANY commands from ANY source. This works automatically with no configuration needed.

**Protected Against:**
- First-join command plugins (`/give`, `/kit starter`)
- Auto-command schedulers
- Permission-based command executors
- `Player.performCommand()` calls
- `Bukkit.dispatchCommand()` calls
- Command suggestions and tab-complete

**Verify Protection:**
```bash
# Enable debug logging to see blocking in action
logging:
  debug:
    nms: true
```

**Console Output:**
```
[FPP] BotCommandBlocker: blocked command (LOWEST) for BotName: /give BotName diamond_sword
[FPP] BotCommandBlocker: cleared command suggestions for BotName
```

**If bots are still executing commands (v1.5.5 or earlier):**
1. **Update to v1.5.6+** - automatic command blocking included
2. **Check plugin version:** `/fpp` or `/plugins`
3. **No configuration needed** - works out of the box

---

### ❌ **Lobby Plugins Teleporting Bots on Spawn** *(Fixed in v1.5.6)*

**Symptoms:** Bots spawn at lobby/spawn instead of player location

**✅ Automatic Protection (v1.5.6+):**
FPP now includes **5-tick spawn protection** that prevents lobby plugins from teleporting bots during their initial spawn. This works automatically with no configuration needed.

**Protected Against:**
- EssentialsX spawn teleports
- Multiverse respawn anchors
- Custom lobby plugins
- Any plugin using `PlayerTeleportEvent` with PLUGIN/UNKNOWN cause

**Still Allows:**
- Admin commands (`/tp`, `/fpp tp`, `/fpp tph`)
- Manual teleports via commands

**Verify Protection:**
```bash
# Enable debug logging to see protection in action
logging:
  debug:
    nms: true
```

**Console Output:**
```
[FPP] BotSpawnProtection: protecting BotName from teleports for 5 ticks
[FPP] BotSpawnProtection: blocked PLUGIN teleport for BotName from world (100,64,200) to lobby (0,100,0)
[FPP] BotSpawnProtection: removed protection for BotName
```

**If bots are still being teleported (v1.5.5 or earlier):**
1. **Update to v1.5.6+** - automatic spawn protection included
2. **Check plugin version:** `/fpp` or `/plugins`
3. **No configuration needed** - works out of the box

**Manual Workaround (older versions):**
```yaml
# Disable lobby teleport for bots in lobby plugin's config
essentials:
  spawn-on-join: false
```

---

## 🎨 **Skin Problems**

### ❌ **All Bots Have Notch Skin**

**Symptoms:** Every bot looks like Notch regardless of name

**Check Skin Mode (config.yml):**
```yaml
skin:
  mode: "player"               # Default — fetches Mojang skins + 1000-player fallback pool
  guaranteed-skin: true        # true = always use fallback pool for non-Mojang names
```

**For Random Mode:**
1. **Add skin files to `plugins/FakePlayerPlugin/skins/`**
2. **Use proper .png format (64x64 or 64x32)**
3. **Reload skins:** `/fpp reload skins`

---

### ❌ **Skins Not Loading in Player Mode**

**Symptoms:** Bots have default Steve skin

**Requirements for Player Mode:**
- ✅ Internet connection required for initial Mojang lookups (cached in DB after first resolve)
- ✅ `guaranteed-skin: true` ensures bots always get a real-looking skin from the built-in 1000-player pool

**Switch to Random Mode for full control:**
```yaml
skin:
  mode: "random"
  guaranteed-skin: true
```

**Add Default Skins:**
1. Download skins from [NameMC](https://namemc.com) or [MinecraftSkins](https://www.minecraftskins.com)
2. Save as .png in `plugins/FakePlayerPlugin/skins/`
3. Name files: `steve.png`, `alex.png`, etc.

---

### ❌ **Custom Skins Not Working**

**Check Skin Files:**
1. **Correct location:** `plugins/FakePlayerPlugin/skins/`
2. **Proper format:** `.png` extension
3. **Valid dimensions:** 64x64 pixels (new format) or 64x32 (old format)
4. **Proper naming:** No spaces, use lowercase

**Test Skin Loading:**
```bash
/fpp reload skins           # Reload skin repository
/fpp spawn --skin steve     # Test specific skin
```

**Debug Skin Issues:**
```bash
/fpp info <botname>         # Check bot's skin source
```

---

## 📊 **Performance Issues**

### ❌ **TPS Drops with Many Bots**

**Symptoms:** Server laggy, `/tps` shows low values

**Performance Analysis:**
```bash
/fpp stats --detailed       # Check bot distribution
/timings report             # Detailed performance analysis
```

**Optimization Steps:**

1. **Reduce Bot Counts:**
    ```yaml
    limits:
      max-bots: 30        # Start conservative
      user-bot-limit: 3   # Reduce per-user limits
    ```

2. **Optimize Bot AI:**
    ```yaml
    head-ai:
      enabled: false          # Disable if not needed

    body:
      pushable: false         # Disable collision physics

    fake-chat:
      enabled: false          # Disable chat AI
    ```

3. **Database Optimization:**
   ```yaml
   database:
     enabled: false          # Disable if not needed
   ```

**Hardware Recommendations:**
- **CPU:** 4+ cores for 50+ bots
- **RAM:** 4GB+ allocated to server
- **Storage:** SSD recommended for database

---

### ❌ **Memory Usage Too High**

**Symptoms:** OutOfMemoryError, high RAM usage

**Check Memory Usage:**
```bash
/fpp stats --detailed       # Bot memory usage
/gc                         # Force garbage collection
```

**Memory Optimization:**
1. **Reduce bot limits**
2. **Disable unnecessary features**
3. **Increase server RAM allocation:**
   ```bash
   java -Xmx4G -Xms2G -jar paper.jar nogui
   ```

---

### ❌ **Chunk Loading Issues**

**Symptoms:** Bots disappear when no players nearby

**Configure Chunk Loading (config.yml):**
```yaml
chunk-loading:
  enabled: true
  radius: "auto"         # "auto" = server simulation-distance, 0 = disabled, N = fixed
```

**Monitor Chunk Loading:**
```bash
/fpp stats --detailed       # Check loaded chunks
```

**⚠️ Warning:** Chunk loading increases memory usage

---

## 🔧 **Configuration Help**

### ❌ **Config File Corrupted**

**Symptoms:** Plugin won't start, YAML parsing errors

**Fix Corrupted Config:**
1. **Backup current config:**
   ```bash
   cp plugins/FakePlayerPlugin/config.yml config.yml.backup
   ```

2. **Restore default config:**
   ```bash
   rm plugins/FakePlayerPlugin/config.yml
   # Restart server to regenerate
   ```

3. **Migrate settings manually from backup**

**Prevent Corruption:**
- Use proper YAML formatting
- Validate YAML: [YAML Lint](https://www.yamllint.com/)
- Make backups before major changes

---

### ❌ **Settings Don't Apply**

**Symptoms:** Changed config but behavior unchanged

**Apply Configuration Changes:**
```bash
/fpp reload
# OR restart the server if you changed DB backend or are troubleshooting startup-only integrations
```

**Configuration Validation:**
```bash
/fpp migrate status         # Check config version/health
```

**Hot-Reload Limitations:**
Some settings require full restart:
- Database configuration
- Database backend changes
- Some startup-only integration detection scenarios
- Major system changes

---

### ❌ **Migration/Update Issues**

**Symptoms:** Config version errors, old settings

**Force Configuration Migration:**
```bash
/fpp migrate config         # Re-run migration
/fpp migrate status         # Check results
```

**Manual Backup Before Updates:**
```bash
/fpp migrate backup         # Create full backup
```

**Rollback if Needed:**
1. Stop server
2. Restore from `plugins/FakePlayerPlugin/backups/`
3. Start server

---

## 🔗 **Integration Issues**

### ❌ **PlaceholderAPI Not Working**

**Symptoms:** Placeholders show as `%fpp_count%` literally

**Install PlaceholderAPI:**
1. Download from [SpigotMC](https://www.spigotmc.org/resources/placeholderapi.6245/)
2. Install: `plugins/PlaceholderAPI-2.11.6.jar`
3. Restart server

**Verify Integration:**
```bash
/papi info FPP              # Should show FPP expansion
/papi parse me %fpp_count%  # Test placeholder
```

**Refresh Placeholders:**
```bash
/papi reload                # Reload PAPI
/fpp reload                 # Reload FPP
```

---

### ❌ **LuckPerms Prefixes Not Showing**

**Symptoms:** Bots don't have prefixes/suffixes in chat/tab

**Enable LP Integration (config.yml):**
```yaml
luckperms:
  default-group: "default"   # LP group assigned to every new bot at spawn
```

**Verify LuckPerms Groups:**
```bash
/lp listgroups              # See available groups
/lp group default info      # Check group has prefix
```

**Test Bot Groups:**
```bash
/fpp rank <botname> admin   # Assign group to bot
/fpp lpinfo <botname>       # Check LP integration
```

---

### ❌ **Tab List Ordering Issues**

**Symptoms:** Bots appear in wrong order in tab list

**Configure Tab Ordering:**

FPP automatically places all bots in the `~fpp` scoreboard team, ensuring they appear below real players in the tab list regardless of LP group weight. No configuration required.

---

## 💾 **Database Problems**

### ❌ **Database Won't Start**

**Error:** `Failed to initialize database`

**Check Configuration (config.yml):**
```yaml
database:
  enabled: true              # Must be true
  
  # For SQLite (default):
  sqlite:
    file: "fpp.db"          # Database file name
  
  # For MySQL:
  mysql:
    enabled: false          # Set to true for MySQL
    host: "localhost"
    port: 3306
    database: "fpp_data"
    username: "fpp_user"
    password: "secure_password"
```

**SQLite Issues:**
- Check file permissions in `plugins/FakePlayerPlugin/data/`
- Ensure disk space available
- Try deleting corrupted database file (data will be lost)

**MySQL Issues:**
- Verify MySQL server is running
- Test connection credentials
- Check firewall/network settings

---

### ❌ **Database Migration Failed**

**Symptoms:** Errors during `/fpp migrate db tomysql`

**Prerequisites for Migration:**
1. **MySQL server running and accessible**
2. **Database and user created:**
   ```sql
   CREATE DATABASE fpp_data;
   CREATE USER 'fpp'@'localhost' IDENTIFIED BY 'password';
   GRANT ALL PRIVILEGES ON fpp_data.* TO 'fpp'@'localhost';
   ```

3. **Configuration updated:**
   ```yaml
   database:
     mysql:
       enabled: true
       # ... connection details
   ```

**Step-by-Step Migration:**
```bash
/fpp migrate backup         # Backup first!
/fpp migrate db tomysql     # Migrate data
/fpp reload config          # Apply new config
```

---

## 🐛 **Weird Bugs**

### ❌ **Bots Stuck in Portals**

**Symptoms:** Bot entities get ejected to different worlds

**Prevention (config.yml):**
```yaml
body:
  enabled: true
  
# Portal handling is automatic - FPP uses PDC-based entity recovery
# to restore bots pushed through portals.
```

**If Already Stuck:**
```bash
/fpp despawn <botname>      # Remove stuck bot
/fpp spawn --name <botname> # Recreate at safe location
```

---

### ❌ **Duplicate Bots After Restart**

**Symptoms:** Same bot appears multiple times after server restart

**Clear Persistence Data:**
```bash
# Stop server
rm plugins/FakePlayerPlugin/data/bot-persistence.yml
# Start server
```

**Prevention:**
- Don't force-kill server during shutdown
- Allow proper shutdown sequence
- Regular backups: `/fpp migrate backup`

---

### ❌ **Bots Appear in Wrong World**

**Symptoms:** Bots spawn in different world than expected

**Check World Names:**
```bash
/fpp list                   # See bot locations
/worlds                     # List all worlds
```

**Fix Bot Locations:**
```bash
/fpp tp <botname>           # Teleport to bot to check
/fpp tph <botname>          # Bring bot to your world
```

---

### ❌ **Plugin Conflicts**

**Symptoms:** Strange behavior, errors mentioning other plugins

**Common Conflicting Plugins:**
- Other fake player/NPC plugins
- Anti-cheat plugins (might flag bot behavior)
- Permission plugins (conflicting permission systems)
- Chat formatting plugins

**Diagnosis:**
1. **Test with minimal plugins:**
   - Keep only FPP + dependencies
   - Add other plugins one by one

2. **Check logs for conflicts:**
   ```bash
   grep -i "fpp\|fake" logs/latest.log
   ```

3. **Use compatibility mode if available**

---

## 🛠️ **Advanced Troubleshooting**

### 🔍 **Debug Mode**

**Enable Debug Logging (config.yml):**
```yaml
debug: true                 # Enable detailed logging
```

**View Debug Output:**
```bash
tail -f logs/latest.log | grep FPP
```

---

### 📊 **Performance Profiling**

**Built-in Diagnostics:**
```bash
/fpp stats --detailed       # Comprehensive statistics
/timings report             # Server-wide performance
```

**External Tools:**
- [spark](https://spark.lucko.me/) - Performance profiler
- [LagGoggles](https://github.com/TerminalMC/LagGoggles) - Lag detection

---

### 🧹 **Clean Reset**

**Complete FPP Reset (⚠️ DESTRUCTIVE):**
```bash
# 1. Stop server
# 2. Remove all FPP data:
rm -rf plugins/FakePlayerPlugin/
rm plugins/fpp-*.jar
# 3. Start server
# 4. Reinstall FPP from scratch
```

**Backup First:**
```bash
/fpp migrate backup         # Create backup before reset
```

---

## 🆘 **Getting Help**

### 💬 **Community Support**

**Discord Server:** [Join Community](https://discord.gg/QSN7f67nkJ)
- Fastest response time
- Screen sharing for complex issues  
- Community troubleshooting

**GitHub Issues:** [Report Bugs](https://github.com/Pepe-tf/fake-player-plugin/issues)
- Bug reports with logs
- Feature requests
- Technical discussions
- Source code: https://github.com/Pepe-tf/fake-player-plugin

### 📋 **When Reporting Issues**

**Include This Information:**
1. **FPP Version:** `/fpp stats`
2. **Server Info:** Paper version, Java version
3. **Error Logs:** Recent console output
4. **Configuration:** Relevant config sections
5. **Steps to Reproduce:** What you did before the issue
6. **Expected vs Actual:** What should happen vs what happened

**Useful Commands for Bug Reports:**
```bash
/fpp stats --detailed       # Plugin statistics
/version                    # Server version
/plugins                    # Installed plugins
/fpp migrate status         # Configuration status
/timings report             # Performance data
```

---

## 🎓 **Prevention Tips**

### ✅ **Best Practices**

1. **Regular Backups:** Use `/fpp migrate backup` before changes
2. **Start Small:** Test with few bots before scaling up
3. **Monitor Performance:** Check `/tps` and `/fpp stats` regularly
4. **Update Safely:** Test updates on development server first
5. **Documentation:** Keep track of configuration changes
6. **Community:** Join Discord for updates and help

### 🔧 **Maintenance Routine**

**Daily:**
- Monitor server performance
- Check for unusual bot behavior

**Weekly:**
- Review bot counts and limits
- Create configuration backup
- Check for plugin updates

**Monthly:**
- Clean up unused bots
- Review and optimize configuration
- Update dependencies (Paper, Java)

---

**🎉 Most issues have simple solutions! Don't hesitate to ask for help in our Discord community.**

For additional help, see:
- **[Getting Started](Getting-Started.md)** - Setup and installation guide
- **[Commands](Commands.md)** - Complete command reference  
- **[Configuration](Configuration.md)** - Detailed configuration guide

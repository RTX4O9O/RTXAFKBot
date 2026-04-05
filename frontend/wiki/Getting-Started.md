# 🚀 Getting Started

> **Your Complete Guide to Setting Up Fake Player Plugin**  
> **From Zero to Bot Army in 5 Minutes!**

---

## 📋 System Requirements

### 🖥️ **Server Requirements**
| Component | Requirement | Recommended |
|-----------|-------------|-------------|
| **Server Software** | Paper 1.21+ | Paper 1.21.3+ (latest) |
| **Java Version** | JDK 21+ | JDK 21+ (Oracle/Temurin) |
| **RAM** | 1GB+ | 2GB+ for optimal performance |
| **CPU** | 2+ cores | 4+ cores for 50+ bots |
| **Storage** | 100MB+ | 500MB+ (with database) |

### 🔧 **Dependencies**
| Plugin | Required | Purpose |
|--------|----------|---------|
| **PacketEvents** | ✅ **Required** | Packet manipulation for tab list |
| **PlaceholderAPI** | ⚠️ Optional | Placeholder support for other plugins |
| **LuckPerms** | ⚠️ Optional | Advanced permissions & prefix/suffix |

### 🌐 **Server Configuration**
- **Online Mode:** Recommended (required for `auto` skin mode)
- **View Distance:** 8+ chunks (for bot visibility)
- **Entity Limit:** Consider increasing for large bot counts

---

## 📦 Installation Guide

### Step 1: **Install Dependencies**

#### **PacketEvents (Required)**
1. Download from [PacketEvents Releases](https://github.com/retrooper/packetevents/releases)
2. Get the **latest** `packetevents-spigot-X.X.X.jar`
3. Place in your `plugins/` folder

#### **PlaceholderAPI (Optional)**
1. Download from [PAPI SpigotMC](https://www.spigotmc.org/resources/placeholderapi.6245/)
2. Place `PlaceholderAPI-X.X.X.jar` in `plugins/` folder

#### **LuckPerms (Optional)**
1. Download from [LuckPerms Downloads](https://luckperms.net/download)
2. Place `LuckPerms-Bukkit-X.X.X.jar` in `plugins/` folder

### Step 2: **Install FPP**

1. **Download FPP** from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp))
2. **Place** `fpp-1.5.10.jar` in your `plugins/` folder
3. **Verify** your plugins folder:

```
📁 plugins/
├── 📦 packetevents-spigot-2.x.x.jar
├── 📦 PlaceholderAPI-2.11.6.jar (optional)
├── 📦 LuckPerms-Bukkit-5.5.x.jar (optional)
└── 📦 fpp-1.5.10.jar
```

### Step 3: **First Launch**

1. **Start your server**
2. **Watch the console** for FPP startup messages:

```
[FPP] ══════════════════════════════════════════════════
[FPP]   FakePlayerPlugin v1.5.10
[FPP] ──────────────────────────────────────────────────
[FPP] ── Runtime ─────────────────────────────────────────
[FPP]   [+] Database ........ SQLite (local)
[FPP]   Config version ...... v37 ✔
[FPP] ── Features ────────────────────────────────────────
[FPP]   [+] Physical bodies . enabled
[FPP]   [+] Persistence ..... enabled
[FPP]   [+] Chunk loading ... enabled
[FPP]   [-] Fake chat ....... disabled
[FPP]   [-] Bot swap ........ disabled
[FPP] ── Integrations ──────────────────────────────────
[FPP]   [-] LuckPerms ....... disabled
[FPP]   [-] Metrics ......... disabled
[FPP] ── Pools & Limits ──────────────────────────────────
[FPP]   Name pool ........... 50
[FPP]   Message pool ........ 30
[FPP]   Skin mode ........... auto
[FPP]   Max bots ............ unlimited
[FPP] ──────────────────────────────────────────────────
[FPP]   Ready: /fpp help
[FPP] ══════════════════════════════════════════════════
```

3. **Check generated files**:

```
📁 plugins/FakePlayerPlugin/
├── 📄 config.yml          # Main configuration
├── 📄 bot-names.yml       # Bot name pools
├── 📄 bot-messages.yml    # Chat message pools
├── 📁 language/
│   └── 📄 en.yml          # All plugin messages
├── 📁 skins/              # Custom skin storage
└── 📁 data/
    └── 📄 fpp.db          # SQLite database
```

---

## ⚡ Quick Setup

### 🎯 **Basic Configuration** (5 minutes)

1. **Set Permissions** (choose one):

   **Option A: Give yourself full access**
   ```
   /lp user <yourname> permission set fpp.*
   ```
   
   **Option B: Add to admin group**
   ```yaml
   # In your permissions plugin
   permissions:
     - fpp.admin.*
   ```

2. **Test Basic Functionality**
   ```
   /fpp spawn 3
   ```
   ✅ Should spawn 3 bots with random names and skins

3. **Verify Tab List Integration**
   - Press **Tab** — see bots listed with real players
   - Check **server list** — player count should include bots

### 🎨 **Recommended Tweaks** (10 minutes)

1. **Configure Bot Names** (`bot-names.yml`):
   ```yaml
   # Add your preferred names
   male-names:
     - "Steve"
     - "Alex"
     - "Notch" 
   
   female-names:
     - "Alice"
     - "Emma"
     - "Sarah"
   ```

2. **Set Global Bot Limit** (`config.yml`):
   ```yaml
   global-bot-limit: 50  # Max bots on server
   user-bot-limit: 5     # Max per regular player
   ```

3. **Enable Fake Chat** (`config.yml`):
   ```yaml
   fake-chat:
     enabled: true
     interval: 300-900     # 5-15 minutes
   ```

4. **Configure Physical Bodies** (`config.yml`):
   ```yaml
   body:
     spawn-body: true      # Physical Mannequin entities
     pushable: true        # Players can push bots
     damageable: false     # Bots take no damage
   ```

---

## 🏆 Advanced Setup

### 🔐 **Permission System Setup**

**For Regular Users:**
```yaml
permissions:
  - fpp.user.spawn        # Can spawn personal bots
  - fpp.user.delete       # Can delete own bots  
  - fpp.user.list         # Can list all bots
  - fpp.bot.5             # Max 5 personal bots
```

**For Staff Members:**
```yaml
permissions:
  - fpp.admin.spawn       # Spawn admin bots
  - fpp.admin.delete      # Delete any bot
  - fpp.admin.reload      # Reload configuration
  - fpp.bypass.cooldown   # No spawn cooldown
```

**For Administrators:**
```yaml
permissions:
  - fpp.*                 # Full access
```

### 🎨 **LuckPerms Integration**

1. **Create Bot Group**:
   ```
   /lp creategroup bots
   /lp group bots permission set group.bots
   /lp group bots meta setweight 1
   /lp group bots meta setprefix "&7[Bot] &f"
   /lp group bots meta setsuffix " &8(AI)"
   ```

2. **Configure FPP** (`config.yml`):
   ```yaml
   luckperms:
     default-group: "bots"   # LP group for all bots
   ```

3. **Result**: Bots appear with prefixes in chat and tab list!

### 📊 **Database Setup (MySQL)**

For better performance with many bots:

1. **Create Database**:
   ```sql
   CREATE DATABASE fpp_data;
   CREATE USER 'fpp'@'localhost' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON fpp_data.* TO 'fpp'@'localhost';
   ```

2. **Configure FPP** (`config.yml`):
   ```yaml
   database:
     enabled: true
     mysql:
       enabled: true
       host: "localhost"
       port: 3306
       database: "fpp_data"
       username: "fpp"
       password: "secure_password"
   ```

---

## 🧪 Testing & Verification

### ✅ **Functionality Checklist**

**Basic Features:**
- [ ] Bots spawn with `/fpp spawn`
- [ ] Bots appear in tab list
- [ ] Server player count increases
- [ ] Bots have skins (if online mode)
- [ ] Bots can be deleted with `/fpp despawn`

**Physical Bodies:**
- [ ] Mannequin entities appear in world
- [ ] Bots have proper hitboxes
- [ ] Head rotation works
- [ ] Push physics work

**Advanced Features:**
- [ ] Fake chat messages appear
- [ ] LuckPerms prefixes show
- [ ] Placeholders work in other plugins
- [ ] Database records sessions

### 🐛 **Common Issues**

**"Bots have no skins"**
- ✅ Enable online mode OR use `custom` skin mode

**"Tab list not working"**
- ✅ Ensure PacketEvents is installed and loaded

**"Permission denied"**
- ✅ Check you have `fpp.user.spawn` or higher

**"Bots disappear after restart"**
- ✅ Enable `persistence.enabled: true` in config.yml (default: true — bots rejoin on restart)

---

## 🎓 Next Steps

### 📖 **Recommended Reading Order**

1. **[⌨️ Commands](Commands.md)** — Learn all available commands
2. **[🔐 Permissions](Permissions.md)** — Set up user access
3. **[⚙️ Configuration](Configuration.md)** — Customize behavior
4. **[🤖 Bot Behaviour](Bot-Behaviour.md)** — Understanding bot AI
5. **[🎨 Skin System](Skin-System.md)** — Managing bot appearances

### 🚀 **Pro Tips**

- **Start Small**: Begin with 5-10 bots, increase gradually
- **Monitor Performance**: Watch for TPS drops with many bots
- **Use Swap System**: For persistent server population
- **Backup Configs**: Before major changes
- **Join Discord**: Get help from the community

### 🎯 **Common Use Cases**

**Minigame Servers:**
- Populate waiting lobbies
- Fill teams for PvP games
- Create NPCs for tutorials

**Survival Servers:**
- Maintain active appearance
- Replace AFK players
- Populate spawn areas

**Creative Servers:**
- Showcase building areas
- Populate cities/towns
- Create busy atmospheres

---

## 🆘 Need Help?

### 💬 **Get Support**
- **Discord:** [Join Community](https://discord.gg/QSN7f67nkJ) — Fastest response
- **Discord:** [Report Issues](https://discord.gg/QSN7f67nkJ) — Bug reports
- **Wiki:** [Browse Docs](.) — Comprehensive guides

### 📚 **Additional Resources**
- **FAQ:** Common questions and solutions
- **Video Tutorials:** Coming soon!
- **Community Configs:** Shared setups

---

**🎉 Congratulations! You're ready to manage your bot army!**

Your fake players are now ready to make your server feel more alive and engaging. Happy botting! 🤖

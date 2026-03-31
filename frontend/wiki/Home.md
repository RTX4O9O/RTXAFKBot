# 🎮 Fake Player Plugin — Wiki

> **The Ultimate Bot Spoofing Plugin for Paper 1.21+**  
> **Version:** 1.4.28 · **Platform:** Paper 1.21+ · **Author:** Bill_Hub

---

## 🌟 Welcome to FPP

**Fake Player Plugin (FPP)** is the most advanced bot-spoofing plugin for Minecraft Paper servers. Create **realistic fake players** that seamlessly integrate with your server ecosystem — appearing in the tab list, server count, and as physical entities in the world.

### ✨ What Makes FPP Special?

- 🎭 **Indistinguishable from real players** — Complete tab list integration
- 🏃 **Physical presence** — Mannequin entities with realistic hitboxes  
- 🎨 **Custom skins** — Use any Minecraft skin or upload your own
- 💬 **Fake chat** — Bots can send messages and interact
- 🔄 **Dynamic swapping** — Replace offline players seamlessly  
- ⚙️ **Highly configurable** — Hundreds of customization options
- 🔐 **Permission system** — Full control over who can do what
- 📊 **PlaceholderAPI** — 18+ placeholders for other plugins
- 🎯 **LuckPerms integration** — Prefix/suffix support with gradients

---

## 🚀 Quick Start

### 📋 Requirements
- **Server:** Paper 1.21+ (latest recommended)
- **Java:** JDK 21+ 
- **RAM:** 2GB+ recommended for optimal performance
- **Plugins:** PlaceholderAPI (optional), LuckPerms (optional)

### ⚡ Installation
1. Download FPP from [Modrinth](https://modrinth.com/plugin/fake-player-plugin-(fpp))
2. Drop the JAR into your `plugins/` folder
3. Restart your server
4. Configure permissions and settings
5. Start spawning bots with `/fpp spawn`!

---

## 📚 Documentation Overview

### 🏁 Getting Started
| Page | Description |
|------|-------------|
| [🚀 Getting Started](Getting-Started.md) | Complete setup guide and first steps |
| [❓ FAQ & Troubleshooting](FAQ.md) | Common questions and solutions |

### ⚙️ Core Features  
| Page | Description |
|------|-------------|
| [⌨️ Commands](Commands.md) | All commands with examples and usage |
| [🔐 Permissions](Permissions.md) | Complete permission system guide |
| [⚙️ Configuration](Configuration.md) | All config options explained |
| [🌍 Language](Language.md) | Customizing messages and translations |

### 🤖 Bot Systems
| Page | Description |
|------|-------------|
| [📝 Bot Names](Bot-Names.md) | Random name generation system |
| [💬 Bot Messages](Bot-Messages.md) | Chat messages and broadcasts |
| [🤖 Bot Behaviour](Bot-Behaviour.md) | Physics, AI, and interactions |
| [🎨 Skin System](Skin-System.md) | Skin management and customization |

### 🔧 Advanced Features
| Page | Description |
|------|-------------|
| [🔄 Swap System](Swap-System.md) | Replace offline players automatically |
| [💭 Fake Chat](Fake-Chat.md) | Bot chat system and formatting |
| [📊 Placeholders (PAPI)](Placeholders.md) | PlaceholderAPI integration |
| [💾 Database](Database.md) | Analytics and session tracking |
| [🌐 Proxy Support](Proxy-Support.md) | Velocity & BungeeCord multi-server networks |
| [🔄 Config Sync](Config-Sync.md) | **NEW:** Synchronize configs across network |
| [🔧 Migration](Migration.md) | Updating and data migration |

---

## 🎯 Key Features Breakdown

### 🎭 **Realistic Fake Players**
- **Tab List Integration** — Bots appear as real players
- **Server Count** — Increases displayed player count  
- **Join/Leave Messages** — Configurable welcome/goodbye messages
- **Chat Integration** — Bots can send messages and participate

### 🏃 **Physical Bodies (Mannequins)**  
- **Player-Shaped Entities** — Proper hitboxes and collision
- **Skin Support** — Display any Minecraft skin
- **Physics & AI** — Walking, head rotation, collision avoidance
- **Combat** — Take damage, die, respawn (all configurable)

### 🎨 **Skin System**
- **Auto Mode** — Fetch skins from Mojang automatically
- **Custom Skins** — Upload your own skin files  
- **Random Pool** — Rotate through multiple skins
- **Fallback System** — Always have a working skin

### 💬 **Chat & Messaging**
- **Fake Chat** — Bots send realistic messages
- **LuckPerms Support** — Prefix/suffix with gradient colors
- **Message Pools** — Random message selection
- **Broadcast System** — Scheduled announcements

### 🔄 **Swap System**  
- **Offline Replacement** — Replace AFK/offline players
- **Seamless Transition** — Maintain server population
- **Smart Detection** — Automatic player monitoring
- **Whitelist Support** — Protect VIP players

### ⚙️ **Configuration**
- **28 Config Versions** — Automatic migration system
- **Hot Reload** — Change settings without restart
- **Backup System** — Automatic config backups
- **Validation** — Prevents invalid configurations

---

## 🔐 Permission System

FPP uses a hierarchical permission system:

```
fpp.*                    # Full access (admin)
├── fpp.admin.*         # Admin commands
├── fpp.user.*          # User commands  
└── fpp.bypass.*        # Bypass restrictions
```

**User Permissions:**
- `fpp.user.spawn` — Spawn personal bots
- `fpp.user.delete` — Delete own bots
- `fpp.user.list` — List all bots

**Admin Permissions:**  
- `fpp.admin.spawn` — Spawn admin bots
- `fpp.admin.delete` — Delete any bot
- `fpp.admin.reload` — Reload configuration

**See [🔐 Permissions](Permissions.md) for the complete list.**

---

## 📊 PlaceholderAPI Integration

FPP provides **18 placeholders** for use with other plugins:

**Server-Wide:**
- `%fpp_count%` — Number of bots
- `%fpp_real%` — Real players online  
- `%fpp_total%` — Total players (real + bots)
- `%fpp_names%` — Comma-separated bot names

**Per-World:**
- `%fpp_count_<world>%` — Bots in specific world
- `%fpp_real_<world>%` — Real players in world

**Player-Relative:**
- `%fpp_user_count%` — Player's bot count
- `%fpp_user_max%` — Player's bot limit

**See [📊 Placeholders](Placeholders.md) for the complete list.**

---

## 🛠️ Technical Specifications

### 🏗️ **Architecture**
- **Built for Paper** — Uses Paper-specific APIs for best performance
- **NMS Integration** — Direct packet manipulation for tab list
- **Multi-threaded** — Background processing for heavy operations  
- **Memory Efficient** — Optimized entity management

### 📈 **Performance**
- **Lightweight** — Minimal server impact
- **Scalable** — Handle 100+ bots efficiently
- **Optimized Packets** — Reduced network overhead
- **Chunk Loading** — Smart chunk management

### 🔒 **Security**  
- **Permission-Based** — Granular access control
- **Input Validation** — Prevents exploits and crashes
- **Rate Limiting** — Anti-spam protections
- **Audit Trail** — Full command logging

---

## 🌐 Community & Support

### 💬 **Get Help**
- **Discord Server:** [Join Community](https://discord.gg/QSN7f67nkJ)
- **GitHub Issues:** [Report Bugs](https://github.com/Pepe-tf/Fake-Player-Plugin-Public-)
- **Wiki:** You're reading it! 📚

### 📢 **Stay Updated**  
- **Modrinth:** [Download Updates](https://modrinth.com/plugin/fake-player-plugin-(fpp))
- **Changelog:** Check GitHub releases for latest features
- **Discord:** Get notified of new versions

### 🤝 **Contributing**
We welcome contributions! Check our GitHub for:
- Bug reports and feature requests
- Code contributions and pull requests  
- Documentation improvements
- Community support

---

## ⚖️ Legal & Licensing

**Fake Player Plugin** is proprietary software developed by **Bill_Hub**.

- ✅ **Free to use** on your server
- ❌ **No redistribution** without permission  
- ❌ **No modification** of the plugin
- ❌ **No commercial resale** 

**For commercial licensing or usage permissions, contact Bill_Hub on Discord.**

> **License:** See [LICENSE](../LICENSE) for full terms and conditions.

---

## 🎉 Ready to Start?

1. **📖 Read** [Getting Started](Getting-Started.md) for setup instructions
2. **⌨️ Learn** [Commands](Commands.md) to control your bots  
3. **⚙️ Configure** [Configuration](Configuration.md) to customize behavior
4. **🎨 Customize** [Bot Names](Bot-Names.md) and [Skins](Skin-System.md)
5. **🚀 Deploy** and enjoy your enhanced server!

**Welcome to the future of Minecraft server population management!** 🎮

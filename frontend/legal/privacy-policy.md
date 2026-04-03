# Privacy Policy

**Effective Date:** April 3, 2026
**Last Updated:** April 3, 2026
**Version:** 2.0

---

> **Quick Summary:** Fake Player Plugin is a server-side Minecraft plugin. We collect no personal data from players. Optional anonymous metrics can be disabled at any time. All plugin data stays on your own server. See the full policy below for complete details.

---

## Table of Contents

1. [Definitions](#1-definitions)
2. [Who We Are](#2-who-we-are)
3. [Scope of This Policy](#3-scope-of-this-policy)
4. [Information We Collect](#4-information-we-collect)
5. [How We Use Information](#5-how-we-use-information)
6. [Legal Basis for Processing (GDPR)](#6-legal-basis-for-processing-gdpr)
7. [Data Storage and Security](#7-data-storage-and-security)
8. [Data Retention](#8-data-retention)
9. [Third-Party Services](#9-third-party-services)
10. [Cookies and Tracking](#10-cookies-and-tracking)
11. [Your Rights](#11-your-rights)
12. [California Privacy Rights (CCPA/CPRA)](#12-california-privacy-rights-ccpacpra)
13. [Children's Privacy (COPPA)](#13-childrens-privacy-coppa)
14. [International Data Transfers](#14-international-data-transfers)
15. [Changes to This Policy](#15-changes-to-this-policy)
16. [Contact Us](#16-contact-us)

---

## 1. Definitions

For the purposes of this Privacy Policy, the following terms have the meanings set out below:

- **"Plugin"** or **"FPP"** refers to Fake Player Plugin, a Minecraft server plugin developed and distributed by Bill_Hub.
- **"We", "Us", "Our"** refers to the developer(s) of Fake Player Plugin.
- **"You"** or **"Server Administrator"** refers to any individual or entity that downloads, installs, configures, or operates the Plugin on a Minecraft server.
- **"Player"** refers to any end-user who connects to a Minecraft server running the Plugin.
- **"Personal Data"** means any information relating to an identified or identifiable natural person, as defined under applicable data protection law.
- **"Processing"** means any operation performed on Personal Data, including collection, recording, storage, use, disclosure, or deletion.
- **"Server"** refers to the Minecraft server instance on which the Plugin is installed and operated.
- **"Bot"** or **"Fake Player"** refers to a simulated player entity created and managed by the Plugin.
- **"Plugin Data"** means configuration files, database records, backups, and any other data files generated or managed by the Plugin on your server.
- **"GDPR"** refers to the General Data Protection Regulation (EU) 2016/679.
- **"CCPA/CPRA"** refers to the California Consumer Privacy Act (Cal. Civ. Code § 1798.100 et seq.) and the California Privacy Rights Act.
- **"COPPA"** refers to the Children's Online Privacy Protection Act (15 U.S.C. §§ 6501–6506).

---

## 2. Who We Are

Fake Player Plugin ("FPP") is developed and maintained by **Bill_Hub** (also known as El_Pepes). The Plugin is a proprietary, server-side software tool for Minecraft servers running the Paper platform.

**Contact:**

| Channel | Link |
|---------|------|
| Discord Server | https://discord.gg/QSN7f67nkJ |
| Plugin Page | https://modrinth.com/plugin/fake-player-plugin-(fpp) |

---

## 3. Scope of This Policy

### 3.1 What This Policy Covers

This Privacy Policy applies to:

- The Fake Player Plugin software (all versions from v1.5.8 onwards)
- The official FPP website and documentation portal
- Any official APIs or update-checking services operated by the FPP developer(s)

### 3.2 What This Policy Does Not Cover

This policy does **not** apply to:

- Third-party Minecraft server networks that use the Plugin — those operators are independent data controllers responsible for their own privacy practices
- Third-party plugins or software that integrate with FPP
- The Minecraft game client or game services operated by Mojang AB or Microsoft Corporation
- Any unofficial forks, mirrors, or redistributions of the Plugin

### 3.3 Role of the Server Administrator

As a **server-side plugin**, FPP operates entirely within the environment of your own server infrastructure. You (the server administrator) are an independent data controller with respect to your players' data. We do not access, receive, or process any data you collect from your players through your use of the Plugin.

---

## 4. Information We Collect

### 4.1 Data Stored Locally on Your Server

The Plugin stores the following data **exclusively on your own server infrastructure**. We have no access to this data at any time.

#### Bot Configuration Data

| Data Type | Purpose | Storage Location |
|-----------|---------|-----------------|
| Bot names | Identify and restore bots | `data/active-bots.yml` or database |
| Bot UUIDs | Unique bot identification | Database or YAML |
| Spawn locations (world, X, Y, Z) | Restore bots after restart | Database or YAML |
| LuckPerms groups | Bot permission group assignment | Database |
| Display names | Tab-list representation | Database |
| Skin data (base64 value + signature) | Visual appearance | Memory / config |
| Session timestamps | Session history and uptime tracking | Database |
| Server ID (network mode) | Multi-server bot attribution | Database |

#### Player-Related Data (Minimal)

| Data Type | Purpose | Storage Location | Retention |
|-----------|---------|-----------------|-----------|
| Player UUID | Bot ownership tracking (user commands) | Database | Until manually deleted |
| Player UUID | Spawn cooldown enforcement | Memory (RAM) only | Cleared on server restart |

> **Important:** Player usernames, IP addresses, chat messages, inventory data, and all other personal player attributes are **never** collected, stored, or processed by FPP.

### 4.2 Optional Anonymous Metrics (bStats / FastStats)

If `metrics.enabled: true` is set in `config.yml`, the following **anonymous, non-identifiable** statistics are periodically sent to the bStats analytics platform:

- Plugin version number
- Minecraft server version
- Server platform type (Paper, Leaf, etc.)
- Number of currently active bots (numeric count)
- Enabled feature flags (boolean: fake chat, swap system, etc.)
- Java runtime version
- Operating system type

**What is explicitly NOT collected:** Server IP, hostname, server name, player usernames or UUIDs, geographic location, operator identity, or any personally identifiable information of any kind.

Disable at any time: set `metrics.enabled: false` in `config.yml` and run `/fpp reload`.

### 4.3 Update Checker Requests

When the Plugin checks for updates, a single HTTP GET request is sent to the Modrinth API containing only the plugin version in the `User-Agent` header (e.g., `FakePlayerPlugin-UpdateChecker/1.5.8`). No server or player data is transmitted. The update checker can be disabled in the configuration.

### 4.4 Website Access Logs

When you visit the official FPP website, standard server-side access logs may capture: IP address, HTTP request method/URL, response status code, browser User-Agent, referring URL, and timestamp. This is used solely for security monitoring and is not used for advertising or profiling. Logs are purged after a maximum of 30 days.

---

## 5. How We Use Information

| Purpose | Legal Basis | Data Used |
|---------|------------|-----------|
| Core plugin functionality | Legitimate interest | Bot data, player UUIDs |
| Bot persistence across restarts | Legitimate interest | Bot names, UUIDs, locations |
| Spawn cooldown enforcement | Legitimate interest | Player UUID (memory only) |
| Anonymous usage analytics | Consent (opt-in) | Anonymous metrics |
| Update notifications | Legitimate interest | Plugin version only |
| Website security | Legitimate interest | Access logs |

We do **not** sell data, use data for advertising, share data with marketing platforms, or build user profiles.

---

## 6. Legal Basis for Processing (GDPR)

For users in the European Economic Area (EEA), United Kingdom, or Switzerland:

| Processing Activity | Legal Basis | GDPR Article |
|--------------------|------------|--------------|
| Plugin functionality and bot persistence | Legitimate interests | Art. 6(1)(f) |
| Optional anonymous metrics | Consent — opt-in, freely withdrawable | Art. 6(1)(a) |
| Update checking | Legitimate interests | Art. 6(1)(f) |
| Website access logs | Legitimate interests — security | Art. 6(1)(f) |

You (the server administrator) act as an independent **Data Controller** under GDPR for any data you process relating to your players. FPP is not a data processor on your behalf.

---

## 7. Data Storage and Security

### 7.1 Local Storage Architecture

All plugin data is stored locally on your server:

- **SQLite:** `plugins/FakePlayerPlugin/data/fpp.db` — WAL mode enabled for integrity
- **YAML files:** `config.yml`, `data/active-bots.yml`, and related files

We have no access to your local storage.

### 7.2 Network Mode (MySQL)

If you enable `database.mode: NETWORK`:

- Data is stored on **your own MySQL server**
- Shared only across servers **within your proxy network**
- We have zero access to your MySQL database
- You are solely responsible for securing your database

### 7.3 Recommended Security Practices

- Restrict file permissions on `plugins/FakePlayerPlugin/` to the server process user
- Use strong MySQL credentials with minimum required privileges
- Enable TLS/SSL for MySQL connections
- Maintain regular off-site server backups
- Keep the Plugin updated to receive security patches

### 7.4 Data Breach Notification

In the event of a confirmed security incident affecting FPP's own systems, we will notify affected parties via our Discord server within 72 hours of becoming aware of the breach, in compliance with GDPR Article 33 where applicable.

---

## 8. Data Retention

| Data Type | Default Retention | Deletion Method |
|-----------|------------------|----------------|
| Active bot records | Until `/fpp despawn` or manual deletion | `/fpp despawn all` |
| Session history | Indefinitely (if database enabled) | Delete from `fpp.db` or MySQL |
| Config backups | 10 most recent (auto-pruned) | Automatic via BackupManager |
| Spawn cooldowns | Until server restart | Automatic (memory only) |
| Custom skin files | Until manually deleted | Delete from `skins/` folder |
| Website access logs | Maximum 30 days | Automatic rolling purge |
| Anonymous metrics | Never individually stored | N/A — aggregated only |

---

## 9. Third-Party Services

### 9.1 Modrinth
- **Purpose:** Plugin distribution and update checking
- **Data sent:** Plugin version in User-Agent header only
- **Privacy Policy:** https://modrinth.com/legal/privacy

### 9.2 Mojang / Microsoft
- **Purpose:** Skin texture fetching (`skin.mode: auto`)
- **Data sent:** Bot name string in API endpoint URL (read-only)
- **Privacy Statement:** https://privacy.microsoft.com/en-us/privacystatement
- **Note:** No player or server data is transmitted

### 9.3 bStats / FastStats
- **Purpose:** Optional anonymous plugin usage analytics
- **Data sent:** Anonymous server metrics (see §4.2)
- **Privacy Policy:** https://bstats.org/privacy-policy
- **Opt-out:** `metrics.enabled: false` in `config.yml`

### 9.4 Your MySQL Provider (Network Mode)
- **Data controller:** You — you select and control your own provider
- **Our access:** None
- **Data stored:** Bot configuration only; no player personal data

---

## 10. Cookies and Tracking

### Plugin
The Plugin itself does **not** use cookies, tracking pixels, fingerprinting, or any browser-based tracking technology of any kind.

### Website
| Technology | Purpose | Personal Data |
|------------|---------|--------------|
| `localStorage` (theme preference) | Stores dark/light mode setting | None — contains only "dark" or "light" |
| Server-side access logs | Security and availability | IP address (operational use only) |

**No advertising cookies, third-party tracking scripts, analytics SDKs, or remarketing technologies** are used on the official FPP website.

---

## 11. Your Rights

### GDPR Rights (EEA / UK / Switzerland)

| Right | Description | How to Exercise |
|-------|-------------|-----------------|
| **Access (Art. 15)** | Copy of data held about you | Review local plugin files; contact us for our systems |
| **Rectification (Art. 16)** | Correct inaccurate data | Edit YAML/database directly |
| **Erasure (Art. 17)** | Request deletion | Delete plugin data folder; contact us |
| **Restriction (Art. 18)** | Restrict processing | Disable features via config |
| **Portability (Art. 20)** | Machine-readable data export | `/fpp migrate db export` |
| **Objection (Art. 21)** | Object to processing | Disable metrics/update checker; contact us |
| **Withdraw Consent** | Withdraw consent at any time | Set `metrics.enabled: false` |
| **Lodge Complaint** | Complain to supervisory authority | Contact your national DPA |

### Rights for All Users
- Know what data is collected (this policy)
- Opt out of optional data collection at any time
- Delete all plugin data by removing the plugin and its data folder

---

## 12. California Privacy Rights (CCPA/CPRA)

California residents have the following additional rights:

- **Right to Know:** Categories and specific pieces of personal information collected about you
- **Right to Delete:** Request deletion of personal information we hold
- **Right to Correct:** Correct inaccurate personal information
- **Right to Opt-Out of Sale:** We do **not** sell personal information — nothing to opt out of
- **Right to Non-Discrimination:** We will not penalise you for exercising your CCPA rights

| CCPA Category | Collected | Sold | Shared for Advertising |
|--------------|-----------|------|----------------------|
| Identifiers (e.g., player UUIDs) | Minimal — locally only | No | No |
| Internet activity (website logs) | Operational only | No | No |
| All other CCPA categories | No | No | No |

---

## 13. Children's Privacy (COPPA)

Fake Player Plugin is a server administration tool directed at and intended for server operators, not for children. We do not knowingly collect personal information from children under the age of 13.

If you believe a child has provided personal information in connection with FPP, contact us via Discord and we will take immediate steps to investigate and delete any such information from our systems.

Server operators using FPP remain independently responsible for their own COPPA compliance with respect to their player bases.

---

## 14. International Data Transfers

All Plugin Data is stored locally on your server. No plugin data is transferred internationally by us.

| Outbound Connection | Data Sent | Transfer Mechanism |
|--------------------|-----------|-------------------|
| Modrinth API (update check) | Plugin version (User-Agent only) | No personal data — mechanism not required |
| bStats (optional metrics) | Anonymous statistics | EU servers — no transfer mechanism required |

---

## 15. Changes to This Policy

### 15.1 Notification of Material Changes

When material changes are made, we will:

1. Update the **"Last Updated"** date and **version number** at the top of this document
2. Post an announcement in our **Discord server**
3. Update the policy on the **official FPP website**

Your continued use of the Plugin after the effective date constitutes acceptance of the updated policy.

### 15.2 Version History

| Version | Date | Summary of Changes |
|---------|------|--------------------|
| 2.0 | April 3, 2026 | Full rewrite: definitions, GDPR legal basis table, CCPA section, data retention table, third-party service details, cookies section, international transfers |
| 1.0 | April 3, 2026 | Initial privacy policy |

---

## 16. Contact Us

| Channel | Details | Response Time |
|---------|---------|--------------|
| **Discord Server** | https://discord.gg/QSN7f67nkJ | Typically within 7 business days |
| **Plugin Page** | https://modrinth.com/plugin/fake-player-plugin-(fpp) | Reviews and messages |

We aim to provide a substantive response to all privacy inquiries within **30 days** in accordance with applicable data protection law.

---

*This Privacy Policy is effective as of April 3, 2026 and applies to Fake Player Plugin v1.5.8 and all subsequent versions.*

*Fake Player Plugin — Developed by Bill_Hub (El_Pepes). Not affiliated with Mojang AB, Microsoft Corporation, or any Minecraft platform provider.*

## Introduction

This Privacy Policy describes how Fake Player Plugin ("FPP", "we", "our", or "the Plugin") handles information when you use our Minecraft server plugin.

## Information We Collect

### Plugin Usage
FPP is a **server-side Minecraft plugin** that runs entirely on your own server. We do **not** collect, store, or transmit any personal information from plugin users.

### Optional Analytics
If you enable metrics collection (`metrics.enabled: true` in config.yml), anonymous usage statistics are collected via FastStats/bStats:
- Server version and plugin version
- Number of active bots
- Enabled features (fake chat, swap system, etc.)
- Server platform (Paper, Velocity, etc.)

**No personally identifiable information** is collected. All metrics are anonymous and aggregated.

### Update Checker
When the plugin checks for updates, it sends:
- Current plugin version
- Server platform (via User-Agent header)

**No server IP addresses, player data, or other identifying information** is transmitted.

## Data Storage

### Local Data
FPP stores the following data **locally on your server**:

**Configuration Files:**
- `config.yml` — Plugin settings
- `bot-names.yml` — Bot name pool
- `bot-messages.yml` — Chat message pool
- `language/en.yml` — Localizable messages

**Database:**
- SQLite (`data/fpp.db`) or MySQL — Bot persistence data, session history
- Stored data: bot names, UUIDs, spawn locations, LuckPerms groups, display names
- Player UUIDs (for bot ownership tracking in user commands)

**Backups:**
- Automatic backups of config files before migrations
- Stored in `backups/` folder

All data remains **on your server** and is never transmitted to external services.

## Third-Party Services

### Mojang API
When `skin.mode: auto` is enabled, FPP fetches skin data from Mojang's public API using bot names. This is **read-only** and does not transmit any server or player data.

### Modrinth API
The update checker queries Modrinth's public API to check for new versions. Only the plugin version is sent in the request.

### Database Providers
If you configure MySQL for network mode, data is stored on your MySQL server. You are responsible for securing your database.

## Network Mode / Proxy Support

When `database.mode: NETWORK` is enabled:
- Bot data is shared across servers via a **shared MySQL database you control**
- Plugin messages are sent between servers via proxy plugin-messaging channels
- All data remains within **your proxy network**
- **No external services** have access to this data

## Data Retention

- **Bot data** persists until manually deleted via `/fpp despawn` or by clearing the database
- **Session history** (if database is enabled) persists indefinitely unless manually deleted
- **Backups** are automatically pruned to the most recent 10 backups
- **Config files** persist until manually deleted

## Your Rights

As a server administrator, you have full control over all data:
- **Access:** All data is stored in readable formats (YAML, SQLite, MySQL)
- **Deletion:** Delete any bot, config, or database file at any time
- **Export:** Use `/fpp migrate db export` to export data
- **Disable:** Set `database.enabled: false` to disable all persistence


*This policy applies to Fake Player Plugin v1.5.8 and later.*


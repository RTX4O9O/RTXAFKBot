# Copyright & License Notice

**Effective Date:** April 3, 2026
**Last Updated:** April 3, 2026
**Version:** 1.0

---

> **Note:** Fake Player Plugin is currently **proprietary software**. All rights are reserved. The developer intends to release the source code under the MIT License in a future version. See the [Future Licensing](#future-licensing-roadmap) section for details.

---

## Copyright Notice

```
Copyright © 2024–2026 Bill_Hub (El_Pepes)
All Rights Reserved.

Fake Player Plugin ("FPP")
Developed and maintained by Bill_Hub (also known as El_Pepes)
```

This copyright notice applies to:

- The Fake Player Plugin compiled binary (`fpp-*.jar`)
- All internal source code, logic, and algorithms
- All configuration file templates and default resources
- All documentation, wiki pages, and associated written content
- The official FPP website and its assets
- Any derivative works created by the Developer

---

## Current License Status

### ⚠️ Proprietary — All Rights Reserved

Fake Player Plugin is currently distributed as **proprietary, closed-source software**. The source code is **not publicly available**.

Under this current proprietary status:

| Permission | Status |
|------------|--------|
| Use the compiled Plugin binary on your own server | ✅ Permitted |
| Configure and operate the Plugin | ✅ Permitted |
| Distribute the Plugin binary (unmodified, with attribution) | ⚠️ Ask first |
| Access or view the source code | ❌ Not permitted |
| Modify the Plugin's internal code | ❌ Not permitted |
| Decompile, reverse-engineer, or disassemble the binary | ❌ Not permitted |
| Create derivative works or forks | ❌ Not permitted |
| Redistribute modified versions | ❌ Not permitted |
| Sell, sublicense, or commercially redistribute | ❌ Not permitted |
| Remove or alter copyright notices | ❌ Not permitted |

Full terms of use are outlined in the [Terms of Service](/legal/terms-of-service).

---

## Future Licensing Roadmap

### 🔓 Planned: Open Source Release under MIT License

The Developer intends to **open source Fake Player Plugin** and release it under the **MIT License** in a future version. This is a firm intention, though the exact timeline depends on code readiness, documentation quality, and project maturity.

### What This Means

When the open-source transition occurs:

- The full source code will be published in a public repository
- The license will be updated to the **MIT License** (see below)
- Community contributions (bug fixes, features, pull requests) will be welcomed
- The compiled binary will remain freely available from all current distribution platforms
- Existing users will not need to take any action — the Plugin will continue to work as-is

### Why Not Open Source Yet?

The project is being prepared for open-source release. This involves:

- **Code cleanup:** Refactoring internal systems for public readability and maintainability
- **Documentation:** Ensuring all public APIs and extension points are documented
- **Security audit:** Reviewing NMS internals and obfuscation before exposing source
- **Build system:** Preparing the Maven build configuration for community use
- **Contribution guidelines:** Drafting a `CONTRIBUTING.md` and code style guide

We appreciate your patience. The open-source release will be announced in our Discord server.

---

## The MIT License (Future)

When the open-source transition is complete, the following license will apply:

```
MIT License

Copyright (c) 2024–2026 Bill_Hub (El_Pepes)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

> **This license text is provided for transparency and planning purposes only.**
> It does not take effect until the Developer formally publishes the source code
> under this license. Until then, the proprietary terms above apply exclusively.

---

## Third-Party Licenses

Fake Player Plugin bundles or depends on the following third-party components. Their licenses are unaffected by FPP's current proprietary status.

### PacketEvents
- **Author:** retrooper and contributors
- **License:** GPL-3.0
- **Purpose:** Packet-level tab list integration
- **Website:** https://packetevents.com

### bStats / FastStats
- **Author:** Bastian Oppermann and contributors
- **License:** MIT License
- **Purpose:** Optional anonymous plugin usage metrics
- **Note:** Metrics collection is opt-in and can be disabled via config

### Paper API / Bukkit API
- **Author:** PaperMC contributors, Bukkit contributors
- **License:** GPL-2.0 (Paper), LGPL-3.0 (Bukkit API)
- **Purpose:** Server API used for plugin development
- **Note:** FPP does not distribute Paper or Bukkit

### Authlib (Mojang)
- **Author:** Mojang AB
- **License:** Proprietary (Microsoft/Mojang)
- **Purpose:** GameProfile and skin data structures
- **Note:** Used as a provided dependency from the Paper server classpath; not bundled

### LuckPerms API (Optional)
- **Author:** lucko and contributors
- **License:** MIT License
- **Purpose:** Per-bot permission groups and prefix/suffix integration
- **Note:** Soft dependency — FPP works without it

### PlaceholderAPI (Optional)
- **Author:** PlaceholderAPI contributors
- **License:** GPL-3.0
- **Purpose:** Placeholder expansion for server plugins
- **Note:** Soft dependency — FPP works without it

### marked.js (Website)
- **Author:** Christopher Jeffrey and contributors
- **License:** MIT License
- **Purpose:** Markdown rendering on the documentation website

### DOMPurify (Website)
- **Author:** Cure53 and contributors
- **License:** Apache-2.0 / MIT License
- **Purpose:** XSS sanitization for rendered Markdown on the documentation website

---

## Trademark Notice

**"Fake Player Plugin"**, **"FPP"**, and the FPP logo are proprietary identifiers of the Developer. They are not registered trademarks, but their use is protected under common law trademark principles.

You may:
- ✅ Refer to the Plugin by its name in reviews, tutorials, and documentation
- ✅ Use the name to indicate compatibility (e.g., "Compatible with Fake Player Plugin")
- ✅ Link to official distribution pages or this website

You may not:
- ❌ Use the name or logo to imply endorsement or official affiliation
- ❌ Use the name or logo as part of a competing product's branding
- ❌ Create unofficial distributions using the "Fake Player Plugin" name

---

## Attribution Requirements

Until the MIT License transition, if you reference, review, or showcase Fake Player Plugin in public content (YouTube videos, blog posts, server advertisements, etc.), we ask that you:

1. **Credit the Plugin** — Reference "Fake Player Plugin by Bill_Hub"
2. **Link to official sources** — Modrinth, SpigotMC, Hangar, or BuiltByBit
3. **Do not misrepresent** — Accurately represent features and limitations

---

## DMCA / Copyright Infringement

If you believe that content on the official FPP website or in the Plugin's resources infringes your copyright, please contact us via Discord with:

1. A description of the copyrighted work you claim has been infringed
2. The specific location of the allegedly infringing material
3. Your contact information
4. A statement of good faith belief that the use is not authorized
5. A statement, under penalty of perjury, that the information is accurate

We will investigate all legitimate DMCA notices and take appropriate action.

**Contact for copyright notices:**

| Channel | Details |
|---------|---------|
| **Discord** | https://discord.gg/QSN7f67nkJ |
| **Plugin Page** | https://modrinth.com/plugin/fake-player-plugin-(fpp) |

---

## Stay Updated

To be notified when the open-source release happens:

- 💬 **Join the Discord:** https://discord.gg/QSN7f67nkJ — announcements are posted here first
- 📦 **Follow on Modrinth:** https://modrinth.com/plugin/fake-player-plugin-(fpp) — follow the project for release notifications

---

*Copyright © 2024–2026 Bill_Hub (El_Pepes). All rights reserved.*
*Fake Player Plugin — Not affiliated with Mojang AB or Microsoft Corporation.*


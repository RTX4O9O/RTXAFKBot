# Extension & Addon Policy

*Effective April 26, 2026 · Applies to Fake Player Plugin v1.6.6.7 and later*

---

## 1. What Are Extensions?

Fake Player Plugin (FPP) provides a lightweight Extension / Addon API that allows third-party developers to create custom modules (**Extensions** or **Addons**) that extend the plugin's functionality. Extensions are **not** standalone Bukkit/Spigot/Paper plugins — they are JAR files loaded dynamically by FPP from the `plugins/FakePlayerPlugin/extensions/` directory.

## 2. Developer Responsibilities

- **Compatibility:** Extensions must target Java 21 or newer and be compatible with Paper 1.21.x (and Folia where applicable).
- **Service Provider Declaration:** Each extension must declare its provider class inside `META-INF/services/` as documented in the Extensions wiki page.
- **Lifecycle Compliance:** Extensions must implement the `FppExtension` interface and respect `onLoad` / `onEnable` / `onDisable` lifecycle hooks.
- **No Core Modification:** Extensions must not modify, replace, or inject into FPP core classes via reflection or bytecode manipulation.
- **No Redistribution of FPP:** Extensions may not bundle, shade, or redistribute FPP classes or assets.

## 3. Server Operator Responsibilities

- **Vetting:** Server operators are solely responsible for reviewing and vetting any third-party extension before installation.
- **Trust:** Only install extensions from developers you trust. FPP loads extensions with the same class-loader privileges as the plugin itself.
- **Backups:** Always back up your server and FPP database before installing or updating an extension.
- **Support Scope:** The FPP developer provides support for the Extension API itself, *not* for individual third-party extensions.

## 4. API Stability & Versioning

- The Extension API follows the same semantic-versioning scheme as FPP (currently v1.6.6.7).
- Breaking API changes will be documented in the [Changelog](/wiki/Changelog) and announced on Discord at least one minor release in advance.
- Extensions compiled against an older API may continue to work, but compatibility is not guaranteed across major versions.

## 5. Liability & Warranty Disclaimer

Extensions are third-party software. The FPP developer (**Bill_Hub**) disclaims all liability for crashes, data loss, security incidents, or performance degradation caused by extensions. This policy is provided "AS IS" without warranty of any kind, just like the underlying MIT-licensed plugin.

## 6. Prohibited Uses

- Extensions may not be used to circumvent player-slot limits, manipulate server rankings, or mislead players about real population counts.
- Extensions may not exploit vulnerabilities in FPP, Paper, or the underlying Minecraft server software.
- Extensions may not collect, transmit, or store personal data from players without explicit consent and proper disclosure.

## 7. Distribution & Attribution

- Extension developers retain full ownership and copyright of their own code.
- Publicly distributed extensions should credit FPP and link to the official repository or wiki.
- The FPP name, logo, and abbreviation remain the exclusive property of Bill_Hub and may not be used to imply official endorsement without written permission.

## 8. Enforcement & Termination

Violation of this policy may result in removal of extension listings from community channels, revocation of Discord roles, and/or formal takedown requests. Server operators who violate these terms via extension misuse are subject to the same termination provisions outlined in the [Terms of Service](/legal/terms-of-service).

---

**Questions?** Reach out on [Discord](https://discord.gg/QSN7f67nkJ) or open an issue on [GitHub](https://github.com/Pepe-tf/fake-player-plugin).

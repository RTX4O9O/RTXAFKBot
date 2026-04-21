[CENTER][SIZE=7][B]ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)[/B][/SIZE]

[SIZE=5][I]Spawn realistic fake players on your Paper server — with tab list presence, server list count, join/leave messages, in-world bodies, guaranteed skins, chunk loading, bot swap/rotation, fake chat, AI conversations, area mining, block placing, pathfinding, follow-target automation, per-bot settings GUI, per-bot swim AI & chunk-radius overrides, per-bot PvE attack settings, per-bot XP & item pickup control, tab-list ping simulation, NameTag plugin integration, LuckPerms integration, proxy network support, Velocity companion plugin, BungeeCord companion plugin, full Paper 1.21.x compatibility (1.21.0–1.21.11), and full hot-reload.[/I][/SIZE]

[SIZE=4][B]Version:[/B] 1.6.6.2  [B]Minecraft:[/B] 1.21.x  [B]Platform:[/B] Paper  [B]Java:[/B] 21+[/SIZE]

[URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)'][B][COLOR=#00AF5C]⬇ Download on Modrinth[/COLOR][/B][/URL]  [URL='https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/'][B][COLOR=#FF6B35]⬇ SpigotMC[/COLOR][/B][/URL]  [URL='https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin'][B][COLOR=#00BFD8]⬇ PaperMC Hangar[/COLOR][/B][/URL]  [URL='https://builtbybit.com/resources/fake-player-plugin.98704/'][B][COLOR=#A855F7]⬇ BuiltByBit[/COLOR][/B][/URL]
[URL='https://discord.gg/QSN7f67nkJ'][B][COLOR=#5865F2]💬 Join Discord[/COLOR][/B][/URL]  [URL='https://fakeplayerplugin.xyz'][B][COLOR=#7B8EF0]📖 Wiki[/COLOR][/B][/URL]  [URL='https://ko-fi.com/fakeplayerplugin'][B][COLOR=#FF5E5B]☕ Support on Ko-fi[/COLOR][/B][/URL]  [URL='https://github.com/sponsors/Pepe-tf'][B][COLOR=#EA4AAA]💖 GitHub Sponsors[/COLOR][/B][/URL]  [URL='https://www.patreon.com/c/F_PP?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink'][B][COLOR=#FF424D]🎗 Patreon[/COLOR][/B][/URL]
[/CENTER]

[HR][/HR]

[SIZE=6][B]✨ What It Does[/B][/SIZE]

FPP adds fake players to your server that look and behave like real ones:

[LIST]
[*]Show up in the [B]tab list[/B] and [B]server list player count[/B]
[*]Broadcast [B]join, leave, and kill messages[/B]
[*]Spawn as [B]physical NMS ServerPlayer entities[/B] — pushable, damageable, solid
[*]Always have a [B]real skin[/B] (guaranteed fallback chain — never Steve/Alex unless you want it)
[*][B]Load chunks[/B] around them exactly like a real player
[*][B]Rotate their head[/B] to face nearby players
[*][B]Swim automatically[/B] in water and lava — mimics a real player holding spacebar
[*][B]Send fake chat messages[/B] from a configurable message pool (with LP prefix/suffix support, typing delays, burst messages, mention replies, and event reactions)
[*][B]Swap in and out[/B] automatically with fresh names and personalities
[*][B]Persist across restarts[/B] — they come back where they left off
[*][B]Freeze[/B] any bot in place with [FONT=monospace]/fpp freeze[/FONT]
[*][B]Open bot inventory[/B] — 54-slot GUI with equipment slots; right-click any bot entity to open
[*][B]Pathfind to players[/B] — A* grid navigation with WALK, ASCEND, DESCEND, PARKOUR, BREAK, PLACE move types
[*][B]Mine blocks[/B] — continuous or one-shot block breaking; area selection with pos1/pos2 cuboid mode
[*][B]Place blocks[/B] — continuous block placing with per-bot supply container support
[*][B]Right-click automation[/B] — assign a command to any bot; right-clicking it runs the command
[*][B]Transfer XP[/B] — drain a bot's entire XP pool to yourself with [FONT=monospace]/fpp xp[/FONT]
[*][B]Named waypoint routes[/B] — save patrol routes; bots walk them on a loop with [FONT=monospace]/fpp move --wp[/FONT]
[*][B]Rename bots[/B] — rename any active bot with full state preservation (inventory, XP, LP group, tasks)
[*][B]Per-bot settings GUI[/B] — shift+right-click any bot to open a 6-row settings chest (General · Chat · PvP · Cmds · Danger)
[*][B]AI conversations[/B] — bots respond to [FONT=monospace]/msg[/FONT] with AI-generated replies; 7 providers (OpenAI, Groq, Anthropic, Gemini, Ollama, Copilot, Custom); per-bot personalities via [FONT=monospace]personalities/[/FONT] folder
[*][B]Badword filter[/B] — case-insensitive with leet-speak normalization, auto-rename bad names, remote word list
[*][B]Set bot ping[/B] — simulate realistic tab-list latency per bot with [FONT=monospace]/fpp ping[/FONT]; fixed, random, or bulk modes
[*][B]PvE attack automation[/B] — bots walk to the sender and attack nearby entities or track mob targets with [FONT=monospace]/fpp attack[/FONT]
[*][B]Follow-target automation[/B] — bots continuously follow any online player with [FONT=monospace]/fpp follow[/FONT]; path recalculates as target moves, persists across restarts
[*][B]Per-bot PvE settings[/B] — pveEnabled, pveRange, pvePriority, pveMobTypes configurable per-bot via BotSettingGui
[*][B]Skin persistence[/B] — resolved skins saved to DB and re-applied on restart without a new Mojang API round-trip
[*][B]NameTag integration[/B] — nick-conflict guard, bot isolation from nick cache, skin sync, auto-rename via nick
[*][B]LuckPerms[/B] — per-bot group assignment, weighted tab-list ordering, prefix/suffix in chat and nametags
[*][B]Proxy/network support[/B] — Velocity & BungeeCord cross-server chat, alerts, and shared database
[*][B]Velocity companion[/B] ([FONT=monospace]fpp-velocity.jar[/FONT]) — drop into your Velocity proxy's [FONT=monospace]plugins/[/FONT] folder to inflate the server-list player count and hover list with FPP bots; includes an anti-scam startup warning
[*][B]BungeeCord companion[/B] ([FONT=monospace]fpp-bungee.jar[/FONT]) — identical feature set for BungeeCord/Waterfall networks; drop into your BungeeCord [FONT=monospace]plugins/[/FONT] folder; no configuration needed
[*][B]Config sync[/B] — push/pull configuration files across your proxy network
[*][B]PlaceholderAPI[/B] — 29+ placeholders including per-world bot counts, network state, proxy-aware counts, and spawn cooldown
[*]Fully [B]hot-reloadable[/B] — no restarts needed
[/LIST]

[HR][/HR]

[SIZE=6][B]📋 Requirements[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Requirement[/B][/TD][TD][B]Version[/B][/TD][/TR]
[TR][TD][URL='https://papermc.io/downloads/paper']Paper[/URL][/TD][TD]1.21.x[/TD][/TR]
[TR][TD]Java[/TD][TD]21+[/TD][/TR]
[TR][TD][URL='https://luckperms.net']LuckPerms[/URL][/TD][TD]Optional — auto-detected[/TD][/TR]
[TR][TD][URL='https://www.spigotmc.org/resources/placeholderapi.6245/']PlaceholderAPI[/URL][/TD][TD]Optional — auto-detected (29+ placeholders)[/TD][/TR]
[TR][TD][URL='https://dev.bukkit.org/projects/worldguard']WorldGuard[/URL][/TD][TD]Optional — auto-detected (no-PvP region protection)[/TD][/TR]
[TR][TD][URL='https://lode.gg']NameTag[/URL][/TD][TD]Optional — auto-detected (nick-conflict guard, skin sync)[/TD][/TR]
[/TABLE]

[B]Note:[/B] Supports all Paper 1.21.x versions (1.21.0 through 1.21.11). Check the server console after startup for any version-specific notes.

[I]SQLite is bundled — no database setup required. MySQL is available for multi-server/proxy setups.[/I]

[HR][/HR]

[SIZE=6][B]🚀 Installation[/B][/SIZE]

[LIST=1]
[*]Download the latest [FONT=monospace]fpp-*.jar[/FONT] from [URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)/versions']Modrinth[/URL] and place it in your [FONT=monospace]plugins/[/FONT] folder.
[*]Restart your server — config files are created automatically.
[*]Edit [FONT=monospace]plugins/FakePlayerPlugin/config.yml[/FONT] to your liking.
[*]Run [FONT=monospace]/fpp reload[/FONT] to apply changes at any time.
[/LIST]

[B]Updating?[/B] FPP automatically migrates your config on first start and creates a timestamped backup before changing anything.

[HR][/HR]

[SIZE=6][B]🎮 Commands[/B][/SIZE]

All commands are under [FONT=monospace]/fpp[/FONT] (aliases: [FONT=monospace]/fakeplayer[/FONT], [FONT=monospace]/fp[/FONT]).

[TABLE="width: 100%"]
[TR][TD][B]Command[/B][/TD][TD][B]Description[/B][/TD][/TR]
[TR][TD][FONT=monospace]/fpp[/FONT][/TD][TD]Plugin info — version, active bots, download links[/TD][/TR]
[TR][TD][FONT=monospace]/fpp help [page][/FONT][/TD][TD]Interactive GUI help menu — paginated, permission-filtered, click-navigable[/TD][/TR]
[TR][TD][FONT=monospace]/fpp spawn [amount] [--name <name>][/FONT][/TD][TD]Spawn fake player(s) at your location[/TD][/TR]
[TR][TD][FONT=monospace]/fpp despawn <name|all|--random [n]|--num <n>>[/FONT][/TD][TD]Remove a bot by name, remove all, remove random N, or remove N oldest (blocked during persistence restore)[/TD][/TR]
[TR][TD][FONT=monospace]/fpp list[/FONT][/TD][TD]List all active bots with uptime and location[/TD][/TR]
[TR][TD][FONT=monospace]/fpp freeze <name|all> [on|off][/FONT][/TD][TD]Freeze or unfreeze bots — frozen bots are immovable; shown with ❄ in list/stats[/TD][/TR]
[TR][TD][FONT=monospace]/fpp inventory <bot>[/FONT][/TD][TD]Open the bot's full 54-slot inventory GUI (alias: /fpp inv)[/TD][/TR]
[TR][TD][FONT=monospace]/fpp move <bot> <player>[/FONT][/TD][TD]Navigate a bot to an online player using A* pathfinding[/TD][/TR]
[TR][TD][FONT=monospace]/fpp move <bot> --wp <route>[/FONT][/TD][TD]Patrol a named waypoint route on a loop[/TD][/TR]
[TR][TD][FONT=monospace]/fpp move <bot> --stop[/FONT][/TD][TD]Stop the bot's current navigation[/TD][/TR]
[TR][TD][FONT=monospace]/fpp mine <bot> [once|stop][/FONT][/TD][TD]Continuous or one-shot block mining[/TD][/TR]
[TR][TD][FONT=monospace]/fpp mine <bot> --pos1|--pos2|--start|--status|--stop[/FONT][/TD][TD]Area-selection cuboid mining mode[/TD][/TR]
[TR][TD][FONT=monospace]/fpp place <bot> [once|stop][/FONT][/TD][TD]Continuous or one-shot block placing[/TD][/TR]
[TR][TD][FONT=monospace]/fpp storage <bot> [name|--list|--remove|--clear][/FONT][/TD][TD]Register supply containers for mine/place restocking[/TD][/TR]
[TR][TD][FONT=monospace]/fpp use <bot>[/FONT][/TD][TD]Bot right-clicks / activates the block it's looking at[/TD][/TR]
[TR][TD][FONT=monospace]/fpp waypoint <name> [create|add|remove|list|clear][/FONT][/TD][TD]Manage named patrol route waypoints ([FONT=monospace]add[/FONT] auto-creates the route)[/TD][/TR]
[TR][TD][FONT=monospace]/fpp xp <bot>[/FONT][/TD][TD]Transfer all of a bot's XP to yourself[/TD][/TR]
[TR][TD][FONT=monospace]/fpp cmd <bot> <command>[/FONT][/TD][TD]Execute a command on a bot; --add/--clear/--show manage its stored right-click command[/TD][/TR]
[TR][TD][FONT=monospace]/fpp rename <old> <new>[/FONT][/TD][TD]Rename a bot preserving all state (inventory, XP, LP group, tasks)[/TD][/TR]
[TR][TD][FONT=monospace]/fpp personality <bot> set|reset|show[/FONT][/TD][TD]Assign or clear AI personality per bot[/TD][/TR]
[TR][TD][FONT=monospace]/fpp personality list|reload[/FONT][/TD][TD]List available personality files or reload them[/TD][/TR]
[TR][TD][FONT=monospace]/fpp ping [<bot>] [--ping <ms>|--random] [--count <n>][/FONT][/TD][TD]Set simulated tab-list ping for one or all bots[/TD][/TR]
[TR][TD][FONT=monospace]/fpp attack <bot> [--stop][/FONT][/TD][TD]Bot walks to sender and attacks nearby entities (PvE); --mob for stationary mob-targeting mode[/TD][/TR]
[TR][TD][FONT=monospace]/fpp follow <bot|all> <player>[/FONT][/TD][TD]Bot continuously follows an online player; path recalculates as target moves[/TD][/TR]
[TR][TD][FONT=monospace]/fpp follow <bot|all> --stop[/FONT][/TD][TD]Stop the bot's current follow loop[/TD][/TR]
[TR][TD][FONT=monospace]/fpp badword add|remove|list|reload[/FONT][/TD][TD]Manage the runtime badword filter list[/TD][/TR]
[TR][TD][FONT=monospace]/fpp chat [on|off|status][/FONT][/TD][TD]Toggle the fake chat system[/TD][/TR]
[TR][TD][FONT=monospace]/fpp swap [on|off|status|now <bot>|list|info <bot>][/FONT][/TD][TD]Toggle / manage the bot swap/rotation system[/TD][/TR]
[TR][TD][FONT=monospace]/fpp peaks [on|off|status|next|force|list|wake <name>|sleep <name>][/FONT][/TD][TD]Time-based bot pool scheduler[/TD][/TR]
[TR][TD][FONT=monospace]/fpp rank <bot> <group>[/FONT][/TD][TD]Assign a specific bot to a LuckPerms group[/TD][/TR]
[TR][TD][FONT=monospace]/fpp rank random <group> [num|all][/FONT][/TD][TD]Assign random bots to a LuckPerms group[/TD][/TR]
[TR][TD][FONT=monospace]/fpp rank list[/FONT][/TD][TD]List all active bots with their current LuckPerms group[/TD][/TR]
[TR][TD][FONT=monospace]/fpp lpinfo [bot-name][/FONT][/TD][TD]LuckPerms diagnostic info — prefix, weight, rank, ordering[/TD][/TR]
[TR][TD][FONT=monospace]/fpp stats[/FONT][/TD][TD]Live statistics panel — bots, frozen, system status, DB totals, TPS[/TD][/TR]
[TR][TD][FONT=monospace]/fpp info [bot <name> | spawner <name>][/FONT][/TD][TD]Query the session database[/TD][/TR]
[TR][TD][FONT=monospace]/fpp tp <name>[/FONT][/TD][TD]Teleport yourself to a bot[/TD][/TR]
[TR][TD][FONT=monospace]/fpp tph [name][/FONT][/TD][TD]Teleport your bot to yourself[/TD][/TR]
[TR][TD][FONT=monospace]/fpp settings[/FONT][/TD][TD]Open the in-game settings GUI — toggle config values live[/TD][/TR]
[TR][TD][FONT=monospace]/fpp alert <message>[/FONT][/TD][TD]Broadcast an admin message network-wide (proxy)[/TD][/TR]
[TR][TD][FONT=monospace]/fpp sync push [file][/FONT][/TD][TD]Upload config file(s) to the proxy network[/TD][/TR]
[TR][TD][FONT=monospace]/fpp sync pull [file][/FONT][/TD][TD]Download config file(s) from the proxy network[/TD][/TR]
[TR][TD][FONT=monospace]/fpp sync status [file][/FONT][/TD][TD]Show sync status and version info[/TD][/TR]
[TR][TD][FONT=monospace]/fpp sync check [file][/FONT][/TD][TD]Check for local changes vs network version[/TD][/TR]
[TR][TD][FONT=monospace]/fpp migrate[/FONT][/TD][TD]Backup, migration, and export tools[/TD][/TR]
[TR][TD][FONT=monospace]/fpp reload[/FONT][/TD][TD]Hot-reload all config, language, skins, name/message pools[/TD][/TR]
[/TABLE]

[HR][/HR]

[SIZE=6][B]🔑 Permissions[/B][/SIZE]

[SIZE=5][B]Admin[/B][/SIZE] [I](fpp.op — default: op)[/I]

[TABLE="width: 100%"]
[TR][TD][B]Permission[/B][/TD][TD][B]Description[/B][/TD][/TR]
[TR][TD][FONT=monospace]fpp.op[/FONT][/TD][TD]All admin commands (admin wildcard, default: op)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.spawn[/FONT][/TD][TD]Spawn bots (unlimited, supports --name and multi-spawn)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.delete[/FONT][/TD][TD]Remove bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.list[/FONT][/TD][TD]List all active bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.freeze[/FONT][/TD][TD]Freeze / unfreeze any bot or all bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.chat[/FONT][/TD][TD]Toggle fake chat[/TD][/TR]
[TR][TD][FONT=monospace]fpp.swap[/FONT][/TD][TD]Toggle bot swap[/TD][/TR]
[TR][TD][FONT=monospace]fpp.rank[/FONT][/TD][TD]Assign bots to LuckPerms groups[/TD][/TR]
[TR][TD][FONT=monospace]fpp.lpinfo[/FONT][/TD][TD]View LuckPerms diagnostic info for any bot[/TD][/TR]
[TR][TD][FONT=monospace]fpp.stats[/FONT][/TD][TD]View the /fpp stats live statistics panel[/TD][/TR]
[TR][TD][FONT=monospace]fpp.info[/FONT][/TD][TD]Query the database[/TD][/TR]
[TR][TD][FONT=monospace]fpp.reload[/FONT][/TD][TD]Reload configuration[/TD][/TR]
[TR][TD][FONT=monospace]fpp.tp[/FONT][/TD][TD]Teleport to bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.tph[/FONT][/TD][TD]Teleport any bot to you[/TD][/TR]
[TR][TD][FONT=monospace]fpp.bypass.maxbots[/FONT][/TD][TD]Bypass the global bot cap[/TD][/TR]
[TR][TD][FONT=monospace]fpp.bypass.cooldown[/FONT][/TD][TD]Bypass the per-player spawn cooldown[/TD][/TR]
[TR][TD][FONT=monospace]fpp.peaks[/FONT][/TD][TD]Manage the peak-hours bot pool scheduler[/TD][/TR]
[TR][TD][FONT=monospace]fpp.settings[/FONT][/TD][TD]Open the in-game settings GUI[/TD][/TR]
[TR][TD][FONT=monospace]fpp.inventory[/FONT][/TD][TD]Open any bot's inventory GUI[/TD][/TR]
[TR][TD][FONT=monospace]fpp.move[/FONT][/TD][TD]Navigate bots with A* pathfinding[/TD][/TR]
[TR][TD][FONT=monospace]fpp.cmd[/FONT][/TD][TD]Execute or store commands on bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.mine[/FONT][/TD][TD]Enable/stop bot block mining[/TD][/TR]
[TR][TD][FONT=monospace]fpp.place[/FONT][/TD][TD]Enable/stop bot block placing[/TD][/TR]
[TR][TD][FONT=monospace]fpp.storage[/FONT][/TD][TD]Register supply containers for bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.useitem[/FONT][/TD][TD]Bot right-click / use-item automation[/TD][/TR]
[TR][TD][FONT=monospace]fpp.waypoint[/FONT][/TD][TD]Manage named patrol route waypoints[/TD][/TR]
[TR][TD][FONT=monospace]fpp.rename[/FONT][/TD][TD]Rename any bot (with full state preservation)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.rename.own[/FONT][/TD][TD]Rename only bots the sender personally spawned[/TD][/TR]
[TR][TD][FONT=monospace]fpp.personality[/FONT][/TD][TD]Assign AI personalities to bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.badword[/FONT][/TD][TD]Manage the runtime badword filter list[/TD][/TR]
[TR][TD][FONT=monospace]fpp.ping[/FONT][/TD][TD]View/set simulated tab-list ping for bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.attack[/FONT][/TD][TD]PvE attack automation (classic & mob-targeting modes)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.follow[/FONT][/TD][TD]Follow-target bot automation (persistent across restarts)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.migrate[/FONT][/TD][TD]Backup, migrate, and export database[/TD][/TR]
[TR][TD][FONT=monospace]fpp.alert[/FONT][/TD][TD]Broadcast network-wide admin alerts[/TD][/TR]
[TR][TD][FONT=monospace]fpp.sync[/FONT][/TD][TD]Push/pull config across proxy network[/TD][/TR]
[/TABLE]

[SIZE=5][B]User[/B][/SIZE] [I](fpp.use — enabled for all players by default)[/I]

[TABLE="width: 100%"]
[TR][TD][B]Permission[/B][/TD][TD][B]Description[/B][/TD][/TR]
[TR][TD][FONT=monospace]fpp.use[/FONT][/TD][TD]All user-tier commands (granted by default)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.spawn.user[/FONT][/TD][TD]Spawn your own bot (limited by fpp.spawn.limit.<num>)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.tph[/FONT][/TD][TD]Teleport your bot to you[/TD][/TR]
[TR][TD][FONT=monospace]fpp.xp[/FONT][/TD][TD]Transfer a bot's XP to yourself[/TD][/TR]
[TR][TD][FONT=monospace]fpp.info.user[/FONT][/TD][TD]View your bot's location and uptime[/TD][/TR]
[/TABLE]

[SIZE=5][B]Bot Limits[/B][/SIZE]

Grant players a [FONT=monospace]fpp.spawn.limit.<num>[/FONT] node to set how many bots they can spawn. FPP picks the highest one they have.

[FONT=monospace]fpp.spawn.limit.1[/FONT]  [FONT=monospace]fpp.spawn.limit.2[/FONT]  [FONT=monospace]fpp.spawn.limit.3[/FONT]  [FONT=monospace]fpp.spawn.limit.5[/FONT]  [FONT=monospace]fpp.spawn.limit.10[/FONT]  [FONT=monospace]fpp.spawn.limit.15[/FONT]  [FONT=monospace]fpp.spawn.limit.20[/FONT]  [FONT=monospace]fpp.spawn.limit.50[/FONT]  [FONT=monospace]fpp.spawn.limit.100[/FONT]

[B]LuckPerms example[/B] — give VIPs 5 bots:
[CODE]
/lp group vip permission set fpp.use true
/lp group vip permission set fpp.spawn.limit.5 true
[/CODE]

[HR][/HR]

[SIZE=6][B]⚙️ Configuration Overview[/B][/SIZE]

Located at [FONT=monospace]plugins/FakePlayerPlugin/config.yml[/FONT]. Run [FONT=monospace]/fpp reload[/FONT] after any change.

[TABLE="width: 100%"]
[TR][TD][B]Section[/B][/TD][TD][B]What it controls[/B][/TD][/TR]
[TR][TD][FONT=monospace]language[/FONT][/TD][TD]Language file to load (language/en.yml)[/TD][/TR]
[TR][TD][FONT=monospace]debug / logging.debug.*[/FONT][/TD][TD]Per-subsystem debug logging (startup, nms, packets, luckperms, network, config-sync, skin, database)[/TD][/TR]
[TR][TD][FONT=monospace]update-checker[/FONT][/TD][TD]Enable/disable startup version check[/TD][/TR]
[TR][TD][FONT=monospace]metrics[/FONT][/TD][TD]Opt-out toggle for anonymous FastStats usage statistics[/TD][/TR]
[TR][TD][FONT=monospace]limits[/FONT][/TD][TD]Global bot cap, per-user limit, spawn tab-complete presets[/TD][/TR]
[TR][TD][FONT=monospace]spawn-cooldown[/FONT][/TD][TD]Seconds between /fpp spawn uses per player (0 = off)[/TD][/TR]
[TR][TD][FONT=monospace]bot-name[/FONT][/TD][TD]Admin/user display name format (admin-format, user-format)[/TD][/TR]
[TR][TD][FONT=monospace]luckperms[/FONT][/TD][TD]default-group — LP group assigned to every new bot at spawn[/TD][/TR]
[TR][TD][FONT=monospace]skin[/FONT][/TD][TD]Skin mode (player/random/none — legacy: auto/custom/off), guaranteed skin, 1000-player fallback pool, DB cache[/TD][/TR]
[TR][TD][FONT=monospace]body[/FONT][/TD][TD]Physical entity (enabled), pushable, damageable, pick-up-items, pick-up-xp, drop-items-on-despawn[/TD][/TR]
[TR][TD][FONT=monospace]persistence[/FONT][/TD][TD]Whether bots rejoin on server restart; task state (mine/place/patrol) also persisted[/TD][/TR]
[TR][TD][FONT=monospace]join-delay / leave-delay[/FONT][/TD][TD]Random delay range (ticks) for natural join/leave timing[/TD][/TR]
[TR][TD][FONT=monospace]messages[/FONT][/TD][TD]Toggle join, leave, kill broadcast messages; admin compatibility notifications[/TD][/TR]
[TR][TD][FONT=monospace]combat[/FONT][/TD][TD]Bot HP and hurt sound[/TD][/TR]
[TR][TD][FONT=monospace]death[/FONT][/TD][TD]Respawn on death, respawn delay, item drop suppression[/TD][/TR]
[TR][TD][FONT=monospace]chunk-loading[/FONT][/TD][TD]Radius, update interval[/TD][/TR]
[TR][TD][FONT=monospace]head-ai[/FONT][/TD][TD]Enable/disable, look range, turn speed[/TD][/TR]
[TR][TD][FONT=monospace]swim-ai[/FONT][/TD][TD]Automatic swimming in water/lava (enabled, default true)[/TD][/TR]
[TR][TD][FONT=monospace]collision[/FONT][/TD][TD]Push physics — walk strength, hit strength, bot separation[/TD][/TR]
[TR][TD][FONT=monospace]pathfinding[/FONT][/TD][TD]A* options — parkour, break-blocks, place-blocks, arrival distances, node limits, max-fall[/TD][/TR]
[TR][TD][FONT=monospace]fake-chat[/FONT][/TD][TD]Enable, chance, interval, typing delays, burst messages, bot-to-bot chat, mention replies, event reactions[/TD][/TR]
[TR][TD][FONT=monospace]ai-conversations[/FONT][/TD][TD]AI DM system — provider config, personality, typing delay, conversation history[/TD][/TR]
[TR][TD][FONT=monospace]badword-filter[/FONT][/TD][TD]Name profanity filter — leet-speak normalization, remote word list, auto-rename[/TD][/TR]
[TR][TD][FONT=monospace]bot-interaction[/FONT][/TD][TD]Right-click / shift-right-click settings GUI toggles[/TD][/TR]
[TR][TD][FONT=monospace]swap[/FONT][/TD][TD]Auto rotation — session length, absence duration, min-online floor, retry-on-fail, farewell/greeting chat[/TD][/TR]
[TR][TD][FONT=monospace]peak-hours[/FONT][/TD][TD]Time-based bot pool scheduler — schedule, day-overrides, stagger-seconds, min-online[/TD][/TR]
[TR][TD][FONT=monospace]performance[/FONT][/TD][TD]Position sync distance culling (position-sync-distance)[/TD][/TR]
[TR][TD][FONT=monospace]tab-list[/FONT][/TD][TD]Show/hide bots in the player tab list[/TD][/TR]
[TR][TD][FONT=monospace]server-list[/FONT][/TD][TD]Whether bots count in the server-list player total; count-bots, include-remote-bots[/TD][/TR]
[TR][TD][FONT=monospace]config-sync[/FONT][/TD][TD]Cross-server config push/pull mode (DISABLED/MANUAL/AUTO_PULL/AUTO_PUSH)[/TD][/TR]
[TR][TD][FONT=monospace]database[/FONT][/TD][TD]mode (LOCAL/NETWORK), server-id, SQLite (default) or MySQL[/TD][/TR]
[/TABLE]

[HR][/HR]

[SIZE=6][B]🤖 AI Conversations[/B][/SIZE]

Bots can respond to [FONT=monospace]/msg[/FONT], [FONT=monospace]/tell[/FONT], and [FONT=monospace]/whisper[/FONT] with AI-generated replies matching their personality.

[B]Setup:[/B]
[LIST=1]
[*]Edit [FONT=monospace]plugins/FakePlayerPlugin/secrets.yml[/FONT] and add your API key
[*]Set [FONT=monospace]ai-conversations.enabled: true[/FONT] in [FONT=monospace]config.yml[/FONT]
[*]Bots will automatically respond — no restart needed
[/LIST]

[B]Supported Providers[/B] (picked in priority order — first key that works wins):

[TABLE="width: 100%"]
[TR][TD][B]Provider[/B][/TD][TD][B]Key in secrets.yml[/B][/TD][/TR]
[TR][TD]OpenAI[/TD][TD][FONT=monospace]openai-api-key[/FONT][/TD][/TR]
[TR][TD]Anthropic[/TD][TD][FONT=monospace]anthropic-api-key[/FONT][/TD][/TR]
[TR][TD]Groq[/TD][TD][FONT=monospace]groq-api-key[/FONT][/TD][/TR]
[TR][TD]Google Gemini[/TD][TD][FONT=monospace]google-gemini-api-key[/FONT][/TD][/TR]
[TR][TD]Ollama[/TD][TD][FONT=monospace]ollama-base-url[/FONT] (local, no key needed)[/TD][/TR]
[TR][TD]Copilot / Azure[/TD][TD][FONT=monospace]copilot-api-key[/FONT][/TD][/TR]
[TR][TD]Custom OpenAI-compatible[/TD][TD][FONT=monospace]custom-openai-base-url[/FONT][/TD][/TR]
[/TABLE]

[B]Personalities:[/B] Drop [FONT=monospace].txt[/FONT] files into [FONT=monospace]plugins/FakePlayerPlugin/personalities/[/FONT] to create custom personality prompts. Assign per-bot with [FONT=monospace]/fpp personality <bot> set <name>[/FONT]. Bundled personalities: [FONT=monospace]friendly[/FONT] · [FONT=monospace]grumpy[/FONT] · [FONT=monospace]noob[/FONT].

[HR][/HR]

[SIZE=6][B]🎨 Skin System[/B][/SIZE]

Three modes — set with [FONT=monospace]skin.mode[/FONT]:

[TABLE="width: 100%"]
[TR][TD][B]Mode[/B][/TD][TD][B]Behaviour[/B][/TD][/TR]
[TR][TD][FONT=monospace]auto[/FONT] [I](default)[/I][/TD][TD]Fetches a real Mojang skin matching the bot's name[/TD][/TR]
[TR][TD][FONT=monospace]player[/FONT] [I](default)[/I][/TD][TD]Fetches a real Mojang skin matching the bot's name[/TD][/TR]
[TR][TD][FONT=monospace]random[/FONT][/TD][TD]Full control — per-bot overrides, a skins/ PNG folder, and a random pool[/TD][/TR]
[TR][TD][FONT=monospace]none[/FONT][/TD][TD]No skin — bots use the default Steve/Alex appearance[/TD][/TR]
[/TABLE]

[B]Skin fallback[/B] ([FONT=monospace]skin.guaranteed-skin[/FONT], default [FONT=monospace]true[/FONT]) — bots whose name has no matching Mojang account get a random skin from the built-in 1000-player fallback pool. Set to [FONT=monospace]false[/FONT] to use the default Steve/Alex appearance instead.

[B]Legacy aliases:[/B] [FONT=monospace]auto[/FONT] = [FONT=monospace]player[/FONT], [FONT=monospace]custom[/FONT] = [FONT=monospace]random[/FONT], [FONT=monospace]off[/FONT] = [FONT=monospace]none[/FONT] — all still accepted.

In [FONT=monospace]random[/FONT] mode the resolution pipeline is: per-bot override → [FONT=monospace]skins/<name>.png[/FONT] → random PNG from [FONT=monospace]skins/[/FONT] folder → random entry from [FONT=monospace]pool[/FONT] → Mojang API for the bot's own name.

[HR][/HR]

[SIZE=6][B]🔤 LuckPerms Integration[/B][/SIZE]

FPP treats bots as real NMS ServerPlayer entities — LuckPerms detects them as online players automatically.

[LIST]
[*][FONT=monospace]luckperms.default-group[/FONT] — assigns every new bot to an LP group at spawn (blank = LP's built-in default)
[*][FONT=monospace]/fpp rank <bot> <group>[/FONT] — change an individual bot's LP group at runtime, no respawn needed
[*][FONT=monospace]/fpp rank random <group> [num|all][/FONT] — assign a group to random bots
[*][FONT=monospace]/fpp rank list[/FONT] — see each bot's current group at a glance
[*][FONT=monospace]/fpp lpinfo [bot][/FONT] — diagnose prefix, weight, rank index, and packet profile name
[*][B]Tab-list ordering[/B] — ~fpp scoreboard team keeps all bots below real players regardless of LP weight
[*][B]Prefix/suffix[/B] — bots use LuckPerms prefix/suffix automatically (real NMS entities — LP detects them natively)
[/LIST]

[CODE]
luckperms:
  default-group: ""   # e.g. "default", "vip", "admin"
[/CODE]

[HR][/HR]

[SIZE=6][B]🌐 Proxy & Network Support[/B][/SIZE]

FPP supports multi-server [B]Velocity[/B] and [B]BungeeCord[/B] proxy networks.

Enable NETWORK mode on every backend server:

[CODE]
database:
  enabled: true
  mode: "NETWORK"
  server-id: "survival"   # unique per server
  mysql-enabled: true
  mysql:
    host: "mysql.example.com"
    database: "fpp_network"
    username: "fpp_user"
    password: "your_password"
[/CODE]

[B]Cross-server features in NETWORK mode:[/B]
[LIST]
[*]Fake chat messages broadcast to all servers on the proxy
[*][FONT=monospace]/fpp alert <message>[/FONT] — network-wide admin alert
[*]Bot join/leave messages visible network-wide
[*]Remote bot tab-list entries synced across servers
[*]Per-server isolation — each server only manages its own bots
[/LIST]

[HR][/HR]

[SIZE=6][B]🔄 Config Sync[/B][/SIZE]

Keep all servers' configurations in sync automatically:

[CODE]
config-sync:
  mode: "AUTO_PULL"   # DISABLED | MANUAL | AUTO_PULL | AUTO_PUSH
[/CODE]

[TABLE="width: 100%"]
[TR][TD][B]Mode[/B][/TD][TD][B]Behaviour[/B][/TD][/TR]
[TR][TD][FONT=monospace]DISABLED[/FONT][/TD][TD]No syncing (default)[/TD][/TR]
[TR][TD][FONT=monospace]MANUAL[/FONT][/TD][TD]Only sync via /fpp sync commands[/TD][/TR]
[TR][TD][FONT=monospace]AUTO_PULL[/FONT][/TD][TD]Auto-pull latest config on every startup/reload[/TD][/TR]
[TR][TD][FONT=monospace]AUTO_PUSH[/FONT][/TD][TD]Push local changes to the network automatically[/TD][/TR]
[/TABLE]

[B]Files synced:[/B] config.yml, bot-names.yml, bot-messages.yml, language/en.yml

[B]Server-specific keys that NEVER sync:[/B] database.server-id, database.mysql.*, debug

[CODE]
/fpp sync push config.yml        # Upload to network
/fpp sync pull config.yml        # Download from network
/fpp sync status                 # Hash + timestamp per file
/fpp sync check                  # Which files have local changes
[/CODE]

[HR][/HR]

[SIZE=6][B]📊 PlaceholderAPI[/B][/SIZE]

When [URL='https://www.spigotmc.org/resources/placeholderapi.6245/']PlaceholderAPI[/URL] is installed, FPP registers placeholders automatically — no restart needed.

Full documentation available on [URL='https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/blob/main/PLACEHOLDERAPI.md']GitHub[/URL].

FPP provides [B]29+ placeholders[/B] in five categories:

[SIZE=5][B]Server-Wide[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Placeholder[/B][/TD][TD][B]Value[/B][/TD][/TR]
[TR][TD][FONT=monospace]%fpp_count%[/FONT][/TD][TD]Active bots (local + remote in NETWORK mode)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_local_count%[/FONT][/TD][TD]Bots on this server only[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_network_count%[/FONT][/TD][TD]Bots on other proxy servers (NETWORK mode)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_max%[/FONT][/TD][TD]Global max-bots limit (or ∞)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_real%[/FONT][/TD][TD]Real (non-bot) players online[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_total%[/FONT][/TD][TD]Total players (real + bots)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_online%[/FONT][/TD][TD]Alias for %fpp_total%[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_frozen%[/FONT][/TD][TD]Number of currently frozen bots[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_names%[/FONT][/TD][TD]Comma-separated bot display names (local + remote in NETWORK mode)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_network_names%[/FONT][/TD][TD]Display names of bots on other proxy servers only[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_version%[/FONT][/TD][TD]Plugin version string[/TD][/TR]
[/TABLE]

[SIZE=5][B]Config State[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Placeholder[/B][/TD][TD][B]Values[/B][/TD][TD][B]Config Key[/B][/TD][/TR]
[TR][TD][FONT=monospace]%fpp_chat%[/FONT][/TD][TD]on / off[/TD][TD]fake-chat.enabled[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_swap%[/FONT][/TD][TD]on / off[/TD][TD]swap.enabled[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_body%[/FONT][/TD][TD]on / off[/TD][TD]body.enabled[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_pushable%[/FONT][/TD][TD]on / off[/TD][TD]body.pushable[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_damageable%[/FONT][/TD][TD]on / off[/TD][TD]body.damageable[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_tab%[/FONT][/TD][TD]on / off[/TD][TD]tab-list.enabled[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_skin%[/FONT][/TD][TD]auto / custom / off[/TD][TD]skin.mode[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_max_health%[/FONT][/TD][TD]number[/TD][TD]combat.max-health[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_persistence%[/FONT][/TD][TD]on / off[/TD][TD]persistence.enabled[/TD][/TR]
[/TABLE]

[SIZE=5][B]Network / Proxy[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Placeholder[/B][/TD][TD][B]Value[/B][/TD][/TR]
[TR][TD][FONT=monospace]%fpp_network%[/FONT][/TD][TD]on when database.mode: NETWORK, otherwise off[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_server_id%[/FONT][/TD][TD]Value of database.server-id[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_spawn_cooldown%[/FONT][/TD][TD]Configured cooldown in seconds (0 = off)[/TD][/TR]
[/TABLE]

[SIZE=5][B]Per-World[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Placeholder[/B][/TD][TD][B]Value[/B][/TD][/TR]
[TR][TD][FONT=monospace]%fpp_count_<world>%[/FONT][/TD][TD]Bots in world (e.g. %fpp_count_world_nether%)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_real_<world>%[/FONT][/TD][TD]Real players in world[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_total_<world>%[/FONT][/TD][TD]Total (real + bots) in world[/TD][/TR]
[/TABLE]

World names are case-insensitive. Use underscores for worlds with spaces.

[SIZE=5][B]Player-Relative[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Placeholder[/B][/TD][TD][B]Value[/B][/TD][/TR]
[TR][TD][FONT=monospace]%fpp_user_count%[/FONT][/TD][TD]Bots owned by the player[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_user_max%[/FONT][/TD][TD]Bot limit for the player[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_user_names%[/FONT][/TD][TD]Comma-separated names of player's bots[/TD][/TR]
[/TABLE]

[SIZE=5][B]Quick Examples[/B][/SIZE]

[B]Scoreboard sidebar:[/B]
[CODE]
&7Bots: &b%fpp_count%&7/&b%fpp_max%
&7Real: &a%fpp_real%
&7Total: &e%fpp_total%
[/CODE]

[B]Tab list header:[/B]
[CODE]
&7Server: &bSurvival &8| &7Players: &a%fpp_real% &8| &7Bots: &b%fpp_count%
[/CODE]

[B]Per-world display:[/B]
[CODE]
&7Overworld: &e%fpp_total_world%
&7Nether:    &c%fpp_total_world_nether%
&7End:       &d%fpp_total_world_the_end%
[/CODE]

[HR][/HR]

[SIZE=6][B]📝 Bot Names & Chat[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]File[/B][/TD][TD][B]Purpose[/B][/TD][/TR]
[TR][TD][FONT=monospace]bot-names.yml[/FONT][/TD][TD]Random name pool. 1–16 chars, letters/digits/underscores. /fpp reload to update.[/TD][/TR]
[TR][TD][FONT=monospace]bot-messages.yml[/FONT][/TD][TD]Random chat messages. Supports {name} and {random_player} placeholders.[/TD][/TR]
[/TABLE]

When the name pool runs out, FPP generates names automatically ([FONT=monospace]Bot1234[/FONT], etc.).

Bot chat uses the server's real chat pipeline, so formatting is handled by your existing chat plugin (LuckPerms, EssentialsX, etc.). For bodyless or proxy-remote bots, the [FONT=monospace]fake-chat.remote-format[/FONT] key controls how messages appear (supports [FONT=monospace]{name}[/FONT] and [FONT=monospace]{message}[/FONT] placeholders).

[HR][/HR]

[SIZE=6][B]🚀 Proxy Companions[/B][/SIZE]

FPP ships two optional companion plugins that inflate the [B]proxy-level[/B] server-list player count to include FPP bots.

[SIZE=5][B]Velocity Companion (fpp-velocity.jar)[/B][/SIZE]

A lightweight standalone Velocity plugin that makes FPP bots count in the [B]proxy[/B] server list — no config required.

[B]What it does:[/B]
[LIST]
[*]Registers the [FONT=monospace]fpp:proxy[/FONT] plugin-messaging channel; listens for [FONT=monospace]BOT_SPAWN[/FONT], [FONT=monospace]BOT_DESPAWN[/FONT], and [FONT=monospace]SERVER_OFFLINE[/FONT] messages from backend servers
[*]Maintains a live bot registry; pings all backend servers every 5 seconds and caches their real+bot player counts
[*]Intercepts [FONT=monospace]ProxyPingEvent[/FONT] to inflate the proxy-level server-list player count and hover sample list (up to 12 bot names shown)
[*]Prints a prominent [B]anti-scam warning[/B] on every startup — this plugin is [B]100% FREE[/B]; if you paid for it, you were scammed
[/LIST]

[B]Installation:[/B]
[LIST=1]
[*]Drop [FONT=monospace]fpp-velocity.jar[/FONT] into your Velocity proxy's [FONT=monospace]plugins/[/FONT] folder — no config file needed
[*]Restart Velocity — the startup banner confirms the channel is registered and ready
[/LIST]

[B]Requirements:[/B] Velocity 3.3.0+

[SIZE=5][B]BungeeCord Companion (fpp-bungee.jar)[/B][/SIZE]

Identical feature set for BungeeCord/Waterfall networks.

[B]What it does:[/B]
[LIST]
[*]Registers the [FONT=monospace]fpp:proxy[/FONT] plugin-messaging channel; listens for [FONT=monospace]BOT_SPAWN[/FONT], [FONT=monospace]BOT_DESPAWN[/FONT], and [FONT=monospace]SERVER_OFFLINE[/FONT] messages from backend servers
[*]Maintains a live bot registry; pings all backend servers every 5 seconds and caches their real+bot player counts
[*]Intercepts [FONT=monospace]ProxyPingEvent[/FONT] to inflate the proxy-level server-list player count and hover sample list (up to 12 bot names shown)
[*]Prints a prominent [B]anti-scam warning[/B] on every startup — this plugin is [B]100% FREE[/B]; if you paid for it, you were scammed
[/LIST]

[B]Installation:[/B]
[LIST=1]
[*]Drop [FONT=monospace]fpp-bungee.jar[/FONT] into your BungeeCord/Waterfall proxy's [FONT=monospace]plugins/[/FONT] folder — no config file needed
[*]Restart BungeeCord
[/LIST]

[B]Requirements:[/B] BungeeCord or any Waterfall fork

[COLOR=#FF4444][B]⚠ FPP and both companion plugins are 100% FREE & open-source.[/B][/COLOR]
[COLOR=#FF4444][B]If you or your server paid money for any of them, you were SCAMMED by a reseller.[/B][/COLOR]

Always download from the official sources:
[LIST]
[*][B]Modrinth:[/B] [URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)']https://modrinth.com/plugin/fake-player-plugin-(fpp)[/URL]
[*][B]GitHub:[/B] [URL='https://github.com/Pepe-tf/fake-player-plugin']https://github.com/Pepe-tf/fake-player-plugin[/URL]
[*][B]Discord:[/B] [URL='https://discord.gg/QSN7f67nkJ']https://discord.gg/QSN7f67nkJ[/URL]
[/LIST]

[HR][/HR]

[SIZE=6][B]📖 Changelog[/B][/SIZE]

[SIZE=5][B]v1.6.6.2[/B][/SIZE] [I](2026-04-21)[/I]

[B]Critical Bug Fixes[/B]

[LIST]
[*][B][FONT=monospace]/fpp despawn all[/FONT] inventory preservation[/B] — Fixed bug where bulk despawn erased all bot inventories and XP. [FONT=monospace]removeAll()[/FONT] now implements proper snapshot logic identical to single-bot despawn — captures inventory and XP before clearing any maps.

[*][B]Dimension spawn coordinate fix[/B] — Bots spawned in Nether/End now stay at exact coordinates. [FONT=monospace]BotSpawnProtectionListener[/FONT] now blocks all teleport causes ([FONT=monospace]NETHER_PORTAL[/FONT], [FONT=monospace]END_PORTAL[/FONT], [FONT=monospace]END_GATEWAY[/FONT]) during the 5-tick spawn grace period.
[/LIST]

[B]Despawn Snapshot Persistence[/B]

[LIST]
[*][B]Inventory/XP survival across restarts[/B] — Bot inventory and XP are preserved when you despawn and respawn the same bot name, even after server restart. New [FONT=monospace]fpp_despawn_snapshots[/FONT] DB table (schema v17→v18) or [FONT=monospace]data/despawn-snapshots.yml[/FONT] fallback.

[*][B]Config migration v64→v65[/B] — Auto-sets [FONT=monospace]body.drop-items-on-despawn: false[/FONT] for existing installs to enable snapshot preservation by default.
[/LIST]

[B]Configuration[/B]

[LIST]
[*][B]New:[/B] [FONT=monospace]messages.death-message[/FONT] (default [FONT=monospace]true[/FONT]) — toggle bot death messages
[*][B]SettingGui:[/B] Added toggles for [FONT=monospace]body.drop-items-on-despawn[/FONT] and [FONT=monospace]messages.death-message[/FONT]
[/LIST]

[B]Technical[/B]

[LIST]
[*]Config version: 63 → 65
[*]Database schema: 17 → 18
[*]Language file character fixes
[*]BotSpawnProtectionListener UUID fallback for early spawn detection
[/LIST]

[SIZE=5][B]v1.6.6.1[/B][/SIZE] [I](2026-04-20)[/I]

[B]🟧 FPP BungeeCord Companion (fpp-bungee.jar)[/B]
[LIST]
[*]New standalone BungeeCord/Waterfall proxy plugin — drop [FONT=monospace]fpp-bungee.jar[/FONT] into your BungeeCord [FONT=monospace]plugins/[/FONT] folder; no config needed
[*]Registers [FONT=monospace]fpp:proxy[/FONT] plugin-messaging channel; listens for [FONT=monospace]BOT_SPAWN[/FONT], [FONT=monospace]BOT_DESPAWN[/FONT], [FONT=monospace]SERVER_OFFLINE[/FONT] messages from backend servers
[*]Maintains a live bot registry; pings all backends every 5 s and caches total player counts
[*]Intercepts [FONT=monospace]ProxyPingEvent[/FONT] to inflate the proxy-level server-list player count and hover sample list (up to 12 bot names shown)
[*]Prints a prominent [B]anti-scam warning[/B] on every startup — FPP and this companion are 100% free; if you paid for them you were scammed
[*]Source: [FONT=monospace]bungee-companion/[/FONT] module in the FPP repository
[/LIST]

[B]🐛 Bug Fixes[/B]
[LIST]
[*][B]Bot join/leave message color fix[/B] — [FONT=monospace]BotBroadcast[/FONT] now parses display names with full MiniMessage + legacy [FONT=monospace]&[/FONT]/[FONT=monospace]§[/FONT] color support. Previously, color tags in bot display names could render as raw text in join/leave broadcasts; display names now render exactly as defined in [FONT=monospace]en.yml[/FONT]
[/LIST]

[SIZE=5][B]v1.6.6[/B][/SIZE] [I](2026-04-20)[/I]

[B]🚀 FPP Velocity Companion (fpp-velocity.jar)[/B]
[LIST]
[*]New standalone Velocity proxy plugin shipped alongside the main Paper plugin as [FONT=monospace]fpp-velocity.jar[/FONT]
[*]Registers [FONT=monospace]fpp:proxy[/FONT] plugin-messaging channel; listens for [FONT=monospace]BOT_SPAWN[/FONT], [FONT=monospace]BOT_DESPAWN[/FONT], [FONT=monospace]SERVER_OFFLINE[/FONT] messages from backend servers
[*]Maintains a live bot registry; pings all backends every 5 s and caches total player counts
[*]Intercepts [FONT=monospace]ProxyPingEvent[/FONT] — inflates the proxy-level server-list player count and hover sample list (up to 12 bot names shown)
[*]Prints a prominent [B]anti-scam warning[/B] on every startup reminding server owners that FPP is [B]100% free[/B] — if you paid for it, you were scammed by a reseller
[*]Requires Velocity 3.3.0+; drop [FONT=monospace]fpp-velocity.jar[/FONT] into your Velocity [FONT=monospace]plugins/[/FONT] folder — no config needed
[/LIST]

[B]🎯 Follow-Target Automation (/fpp follow)[/B]
[LIST]
[*]New [FONT=monospace]/fpp follow <bot|all> <player> [--stop][/FONT] command — bot continuously follows an online player; path recalculates when target moves >3.5 blocks
[*][FONT=monospace]--stop[/FONT] cancels following on one or all bots
[*]FOLLOW task type persisted to [FONT=monospace]fpp_bot_tasks[/FONT] — bot resumes following after restart if target is online
[*]Permission: [FONT=monospace]fpp.follow[/FONT]
[/LIST]

[B]⚔ Per-Bot PvE Settings (now fully live)[/B]
[LIST]
[*]BotSettingGui PvP tab now has live-editable per-bot PvE controls: pveEnabled toggle, pveRange, pvePriority (nearest/lowest-health), pveMobTypes (entity-type whitelist — empty = all hostile)
[*]Settings persisted via DB schema v15→v16
[*]New config keys: [FONT=monospace]attack-mob.default-range[/FONT], [FONT=monospace]default-priority[/FONT], [FONT=monospace]smooth-rotation-speed[/FONT], [FONT=monospace]retarget-interval[/FONT], [FONT=monospace]line-of-sight[/FONT]
[/LIST]

[B]🎨 Skin Persistence Across Restarts (DB v16→v17)[/B]
[LIST]
[*]Resolved bot skins saved to [FONT=monospace]fpp_active_bots[/FONT] ([FONT=monospace]skin_texture[/FONT] + [FONT=monospace]skin_signature[/FONT] columns)
[*]Bots reload their cached skin on server restart — no additional Mojang API round-trip needed
[/LIST]

[B]🌐 Server-List Config Keys[/B]
[LIST]
[*]New [FONT=monospace]server-list.count-bots[/FONT] (default [FONT=monospace]true[/FONT]) — controls whether bots appear in the server-list player count
[*]New [FONT=monospace]server-list.include-remote-bots[/FONT] (default [FONT=monospace]false[/FONT]) — include remote proxy bots in the count (NETWORK mode)
[*]Config v60→v61 migration adds both keys — no behaviour change for existing installs
[/LIST]

[B]🧭 pathfinding.max-fall[/B]
[LIST]
[*]New [FONT=monospace]pathfinding.max-fall[/FONT] key (default [FONT=monospace]3[/FONT]) — A* pathfinder will not descend more than this many blocks in a single unbroken fall
[/LIST]

[B]💾 DB Schema v15 → v16 → v17[/B]
[LIST]
[*]v15→v16: [FONT=monospace]fpp_active_bots[/FONT] gains [FONT=monospace]pve_enabled BOOLEAN DEFAULT 0[/FONT], [FONT=monospace]pve_range DOUBLE DEFAULT 16.0[/FONT], [FONT=monospace]pve_priority VARCHAR(16)[/FONT], [FONT=monospace]pve_mob_type VARCHAR(64)[/FONT]
[*]v16→v17: [FONT=monospace]fpp_active_bots[/FONT] gains [FONT=monospace]skin_texture TEXT[/FONT], [FONT=monospace]skin_signature TEXT[/FONT]
[*]Fully backward-compatible — existing rows receive safe defaults on schema upgrade
[/LIST]

[B]📋 Config v60 → v61 → v62 → v63[/B]
[LIST]
[*]v60→v61: [FONT=monospace]server-list[/FONT] section added ([FONT=monospace]count-bots[/FONT], [FONT=monospace]include-remote-bots[/FONT])
[*]v61→v62: [FONT=monospace]pathfinding.max-fall[/FONT] added
[*]v62→v63: [FONT=monospace]attack-mob.*[/FONT] default config keys added
[/LIST]

[B]🐛 Bug Fixes[/B]
[LIST]
[*][B]Attribute.MAX_HEALTH compatibility[/B] — fixed [FONT=monospace]NoSuchFieldError[/FONT] crash on Paper/Purpur 1.21.1 and older. New [FONT=monospace]AttributeCompat[/FONT] utility resolves [FONT=monospace]MAX_HEALTH[/FONT] (Paper 1.21.3+) or [FONT=monospace]GENERIC_MAX_HEALTH[/FONT] (1.21.1 and below) via reflection at class-load time — all Paper 1.21.x versions (1.21.0–1.21.11) are now fully supported
[*][B]FPP Velocity banner[/B] — replaced [FONT=monospace]█[/FONT] block characters in the anti-scam warning section with [FONT=monospace]═[/FONT] double-line rules to match the rest of the console banner style
[/LIST]

[SIZE=5][B]v1.6.5.1[/B][/SIZE] [I](2026-04-17)[/I]

[B]⚙️ BotSettingGui Now Publicly Available[/B]
[LIST]
[*]Per-bot settings GUI (shift+right-click any bot) is no longer dev-only — available to all users with [FONT=monospace]fpp.settings[/FONT] permission
[*]Removed developer UUID gate; any player with [FONT=monospace]fpp.settings[/FONT] now opens the 6-row settings chest (General · Chat · PvP · Cmds · Danger)
[*]Grant [FONT=monospace]fpp.settings[/FONT] via LuckPerms to allow non-op users to manage per-bot settings
[/LIST]

[SIZE=5][B]v1.6.5[/B][/SIZE] [I](2026-04-17)[/I]

[B]📡 Tab-List Ping Simulation (/fpp ping)[/B]
[LIST]
[*]New [FONT=monospace]/fpp ping [<bot>] [--ping <ms>|--random] [--count <n>][/FONT] command — set the visible tab-list latency for one or all bots
[*][FONT=monospace]--ping <ms>[/FONT] sets a specific latency (0–9999); [FONT=monospace]--random[/FONT] assigns random realistic values; no flag shows current ping
[*][FONT=monospace]--count <n>[/FONT] targets N random bots for bulk operations
[*]4 granular permissions: [FONT=monospace]fpp.ping[/FONT] (view), [FONT=monospace]fpp.ping.set[/FONT] (set), [FONT=monospace]fpp.ping.random[/FONT] (random), [FONT=monospace]fpp.ping.bulk[/FONT] (bulk [FONT=monospace]--count[/FONT])
[/LIST]

[B]⚔ PvE Attack Automation (/fpp attack)[/B]
[LIST]
[*]New [FONT=monospace]/fpp attack <bot> [--stop][/FONT] command — bot walks to the command sender and continuously attacks nearby entities
[*]Respects 1.9+ attack cooldown and item-specific cooldown timers dynamically
[*]Permission: [FONT=monospace]fpp.attack[/FONT]
[/LIST]

[B]🔐 Permission System Restructure[/B]
[LIST]
[*]New [FONT=monospace]fpp.admin[/FONT] node as preferred alias for [FONT=monospace]fpp.op[/FONT] — both grant full access identically
[*]New [FONT=monospace]fpp.despawn[/FONT] node as preferred alias for [FONT=monospace]fpp.delete[/FONT]; new [FONT=monospace]fpp.despawn.bulk[/FONT] and [FONT=monospace]fpp.despawn.own[/FONT] sub-nodes
[*]Granular sub-nodes for chat, move, mine, place, use, rank, inventory, and ping commands
[*]New [FONT=monospace]fpp.command[/FONT] (controls /fpp visibility), [FONT=monospace]fpp.plugininfo[/FONT], [FONT=monospace]fpp.spawn.multiple[/FONT], [FONT=monospace]fpp.notify[/FONT]
[*]All nodes declared in both [FONT=monospace]Perm.java[/FONT] and [FONT=monospace]plugin.yml[/FONT] for LuckPerms tab-completion
[/LIST]

[B]🎨 Skin Mode Rename[/B]
[LIST]
[*][FONT=monospace]skin.mode[/FONT] values renamed: [FONT=monospace]auto[/FONT] → [FONT=monospace]player[/FONT], [FONT=monospace]custom[/FONT] → [FONT=monospace]random[/FONT], [FONT=monospace]off[/FONT] → [FONT=monospace]none[/FONT]
[*]Legacy values still accepted as aliases — no migration needed
[/LIST]

[SIZE=5][B]v1.6.4[/B][/SIZE] [I](2026-04-16)[/I]

[B]🏷️ NameTag Plugin Integration[/B]
[LIST]
[*]New [B]soft-dependency[/B] on the [URL='https://lode.gg']NameTag[/URL] plugin — fully optional, auto-detected at startup
[*][B]Nick-conflict guard[/B] — prevents spawning a bot whose [FONT=monospace]--name[/FONT] matches a real player's current NameTag nickname ([FONT=monospace]nametag-integration.block-nick-conflicts: true[/FONT])
[*][B]Bot isolation[/B] — after each bot spawns, FPP removes it from NameTag's internal player cache to prevent NameTag from treating bots as real players ([FONT=monospace]nametag-integration.bot-isolation: true[/FONT])
[*][B]Sync-nick-as-rename[/B] — when a bot has a NameTag nick set (e.g. via [FONT=monospace]/nick BotA Steve[/FONT]), FPP auto-triggers a full rename ([FONT=monospace]nametag-integration.sync-nick-as-rename: false[/FONT] — opt-in)
[*][B]NameTag skin sync[/B] — bots inherit skins assigned via NameTag; preferred skin check runs before all other skin modes
[*]New [FONT=monospace]NameTagHelper[/FONT] utility class: nick reading, skin reading, cache isolation, formatting strip, nick-conflict checks
[*]New [FONT=monospace]FakePlayer.nameTagNick[/FONT] field; new lang key [FONT=monospace]spawn-name-taken-nick[/FONT]
[/LIST]

[B]🎨 Skin System Overhaul[/B]
[LIST]
[*]New [FONT=monospace]SkinManager[/FONT] class — centralised skin lifecycle: resolve, apply, cache, fallback, NameTag priority
[*][B]Hardcoded 1000-player fallback skin pool[/B] — replaces old [FONT=monospace]skin.fallback-pool[/FONT] and [FONT=monospace]skin.fallback-name[/FONT] config keys; bots with non-Mojang names always get a real-looking skin
[*][B]DB skin cache[/B] — new [FONT=monospace]fpp_skin_cache[/FONT] table with 7-day TTL and auto-cleanup; avoids repeated Mojang API lookups
[*][FONT=monospace]skin.mode[/FONT] default enforced as [FONT=monospace]player[/FONT] for existing installs that had it disabled (v58→v59 migration)
[*][FONT=monospace]skin.fallback-pool[/FONT] and [FONT=monospace]skin.fallback-name[/FONT] removed — hardcoded in SkinManager (v59→v60 migration)
[*]Exposed via [FONT=monospace]plugin.getSkinManager()[/FONT] — public API for skin resolution, application, caching, and preloading
[/LIST]

[B]🏊 Per-Bot Swim AI & Chunk Load Radius[/B]
[LIST]
[*]Each bot now has an individual [B]swim AI toggle[/B] — override the global [FONT=monospace]swim-ai.enabled[/FONT] per-bot without restarting
[*]Each bot now has an individual [B]chunk load radius[/B] — [FONT=monospace]-1[/FONT] = follow global radius, [FONT=monospace]0[/FONT] = disable chunk loading for this bot, [FONT=monospace]1-N[/FONT] = fixed radius (capped at global max)
[*]Both fields initialised from global config at spawn, fully persisted across restarts (DB column + YAML), editable at runtime
[/LIST]

[B]⚙️ BotSettingGui General Tab Expanded[/B]
[LIST]
[*]General tab now has [B]7 action slots[/B]: Frozen · Head-AI · Swim-AI [I](new)[/I] · Chunk-Load-Radius [I](new, numeric prompt)[/I] · Pick-Up-Items · Pick-Up-XP · Rename
[*]Chunk-load-radius uses a chat-input numeric prompt — type a number or [FONT=monospace]-1[/FONT] to reset to global default
[/LIST]

[B]⚔ BotSettingGui PvP Tab[/B]
[LIST]
[*]PvP category now shows coming-soon override previews: difficulty · combat-mode · critting · s-tapping · strafing · shielding · speed-buffs · jump-reset · random · gear · defensive-mode
[/LIST]

[B]💾 DB Schema v14 → v15[/B]
[LIST]
[*]v14: [FONT=monospace]fpp_active_bots[/FONT] gains [FONT=monospace]swim_ai_enabled BOOLEAN DEFAULT 1[/FONT] and [FONT=monospace]chunk_load_radius INT DEFAULT -1[/FONT]
[*]v15: new [FONT=monospace]fpp_skin_cache[/FONT] table (skin name → texture/signature/source/cached_at) with expiry index
[*]Fully backward-compatible — existing rows receive safe defaults on schema upgrade
[/LIST]

[B]📋 Config v53 → v60[/B]
[LIST]
[*]v53→v54: [FONT=monospace]body.drop-items-on-despawn: false[/FONT] injected into existing installs
[*]v54→v55: shared global pathfinding tuning keys added
[*]v55→v56: [FONT=monospace]nametag-integration[/FONT] section added (block-nick-conflicts, bot-isolation)
[*]v56→v57: [FONT=monospace]nametag-integration.sync-nick-as-rename[/FONT] added
[*]v58→v59: [FONT=monospace]skin.mode=player[/FONT], [FONT=monospace]guaranteed-skin=true[/FONT], [FONT=monospace]logging.debug.skin=true[/FONT] enforced for existing installs
[*]v59→v60: removed [FONT=monospace]skin.fallback-pool[/FONT] and [FONT=monospace]skin.fallback-name[/FONT] (hardcoded in SkinManager)
[/LIST]

[SIZE=5][B]v1.6.3[/B][/SIZE] [I](2026-04-14)[/I]

[B]🛡️ Despawn Safety Guard[/B]
[LIST]
[*][FONT=monospace]despawn all[/FONT], [FONT=monospace]--random <n>[/FONT], and [FONT=monospace]--num <n>[/FONT] are now blocked while bot persistence restoration is in progress at startup — prevents startup-queued console commands from killing bots mid-restore during the ~2–3 second restore window
[*]New lang key [FONT=monospace]delete-restore-in-progress[/FONT] shown to sender when the operation is blocked
[*]Single-bot despawn ([FONT=monospace]/fpp despawn <name>[/FONT]) is [B]not[/B] affected — only bulk operations
[/LIST]

[B]🗺️ Waypoint Auto-Create[/B]
[LIST]
[*][FONT=monospace]/fpp wp add <route>[/FONT] now [B]auto-creates[/B] the route if it doesn't exist — no separate [FONT=monospace]create[/FONT] step needed
[*]In-chat tip shown via new [FONT=monospace]wp-route-auto-created[/FONT] lang key when a route is implicitly created
[*][FONT=monospace]/fpp wp create[/FONT] still exists and is valid, but is now optional
[*][FONT=monospace]wp-usage[/FONT] updated so [FONT=monospace]add[/FONT] leads the usage string; [FONT=monospace]wp-list-empty[/FONT] hint updated to point directly to [FONT=monospace]/fpp wp add <route>[/FONT]
[/LIST]

[SIZE=5][B]v1.6.2[/B][/SIZE] [I](2026-04-12)[/I]

[B]🤖 AI Conversations[/B]
[LIST]
[*]New AI DM system — bots respond to [FONT=monospace]/msg[/FONT], [FONT=monospace]/tell[/FONT], [FONT=monospace]/whisper[/FONT] with AI-generated replies
[*]7 provider support: OpenAI · Anthropic · Groq · Google Gemini · Ollama · Copilot/Azure · Custom OpenAI-compatible
[*]API keys stored in [FONT=monospace]plugins/FakePlayerPlugin/secrets.yml[/FONT] (never in config.yml)
[*]Per-bot personality assignment via [FONT=monospace]/fpp personality[/FONT]; personalities stored as [FONT=monospace].txt[/FONT] files in [FONT=monospace]personalities/[/FONT] folder
[*]Bundled sample personalities: [FONT=monospace]friendly[/FONT] · [FONT=monospace]grumpy[/FONT] · [FONT=monospace]noob[/FONT]
[*][FONT=monospace]BotConversationManager[/FONT] — per-player conversation history, rate limiting, typing delay simulation
[/LIST]

[B]🆕 New Commands[/B]
[LIST]
[*][FONT=monospace]/fpp place <bot> [once|stop][/FONT] — continuous or one-shot block placing with supply-container restocking. Permission: [FONT=monospace]fpp.place[/FONT]
[*][FONT=monospace]/fpp storage <bot> [name|--list|--remove|--clear][/FONT] — register supply containers for mine/place jobs. Permission: [FONT=monospace]fpp.storage[/FONT]
[*][FONT=monospace]/fpp use <bot>[/FONT] — bot right-clicks / activates the block it's looking at. Permission: [FONT=monospace]fpp.useitem[/FONT]
[*][FONT=monospace]/fpp waypoint <name> [add|remove|list|clear][/FONT] — manage named patrol route waypoints. Permission: [FONT=monospace]fpp.waypoint[/FONT]
[*][FONT=monospace]/fpp personality [list|reload|<bot> set <name>|reset|show][/FONT] — assign AI personalities. Permission: [FONT=monospace]fpp.personality[/FONT]
[*][FONT=monospace]/fpp badword add|remove|list|reload[/FONT] — manage runtime badword filter list. Permission: [FONT=monospace]fpp.badword[/FONT]
[*][FONT=monospace]/fpp rename <old> <new>[/FONT] — rename bot with full state preservation (inventory, XP, LP group, AI personality, tasks). Permissions: [FONT=monospace]fpp.rename[/FONT] (any) / [FONT=monospace]fpp.rename.own[/FONT] (own only)
[*][FONT=monospace]/fpp mine --pos1/--pos2/--start/--stop[/FONT] — area-selection cuboid mining mode
[/LIST]

[B]⚙️ Per-Bot Settings GUI[/B]
[LIST]
[*]Shift+right-click any bot to open [B]BotSettingGui[/B] — 6-row chest with 5 categories: ⚙ General · 💬 Chat · ⚔ PvP · 📋 Cmds · ⚠ Danger
[*]Toggle freeze, head-AI, chat tier, AI personality selector, stored command, rename action, delete bot
[*]Controlled by [FONT=monospace]bot-interaction.shift-right-click-settings[/FONT] config key
[/LIST]

[B]⛏️ Area Mining Mode[/B]
[LIST]
[*][FONT=monospace]/fpp mine <bot> --pos1[/FONT] / [FONT=monospace]--pos2[/FONT] — select a cuboid mining region
[*][FONT=monospace]/fpp mine <bot> --start[/FONT] — begin mining the selected area continuously
[*]Auto-restocks from nearest registered [FONT=monospace]StorageStore[/FONT] container when inventory fills
[*]Selections persisted to [FONT=monospace]data/mine-selections.yml[/FONT] — survive restarts and auto-resume
[/LIST]

[B]💾 Task Persistence (DB Schema v13)[/B]
[LIST]
[*]Active tasks (mine/use/place/patrol) saved to [FONT=monospace]fpp_bot_tasks[/FONT] DB table and [FONT=monospace]data/bot-tasks.yml[/FONT] on shutdown
[*]Bots automatically resume their job after server restart
[/LIST]

[B]🧭 Navigation & Interaction Engine[/B]
[LIST]
[*][FONT=monospace]PathfindingService[/FONT] — centralised navigation service eliminating duplicate nav code across commands
[*][FONT=monospace]NavigationRequest[/FONT] with [FONT=monospace]lockOnArrival[/FONT] for atomic nav→action lock handoff
[*][FONT=monospace]BotNavUtil[/FONT] — static helpers: [FONT=monospace]findStandLocation[/FONT], [FONT=monospace]faceToward[/FONT], [FONT=monospace]isAtActionLocation[/FONT], [FONT=monospace]useStorageBlock[/FONT]
[*][FONT=monospace]StorageInteractionHelper[/FONT] — shared lock→open-container→transfer→unlock lifecycle
[/LIST]

[B]🎒 Per-Bot Item & XP Pickup Toggles[/B]
[LIST]
[*][FONT=monospace]body.pick-up-items[/FONT] and [FONT=monospace]body.pick-up-xp[/FONT] global defaults (both [FONT=monospace]true[/FONT])
[*]Per-bot overrides in [B]BotSettingGui[/B] — toggling off immediately drops current inventory / XP to ground
[*][FONT=monospace]BotXpPickupListener[/FONT] gates both pickup events per-bot
[/LIST]

[B]📋 Config v47 → v53[/B]
[LIST]
[*]Added [FONT=monospace]bot-interaction[/FONT], [FONT=monospace]ai-conversations[/FONT], [FONT=monospace]badword-filter[/FONT] sections
[*]Added [FONT=monospace]body.drop-items-on-despawn[/FONT] key
[*]Config reorganized into [B]10 clearly numbered sections[/B] with better flow and organization
[*][FONT=monospace]pathfinding[/FONT] moved into section 4 (AI & Navigation)
[/LIST]

[SIZE=5][B]v1.6.0[/B][/SIZE] [I](2026-04-09)[/I]

[B]🖥️ Interactive Help GUI[/B]
[LIST]
[*][FONT=monospace]/fpp help[/FONT] now opens a [B]54-slot double-chest GUI[/B] — paginated, permission-filtered, click-navigable; replaces text output
[*]Each command gets a semantically meaningful Material icon (compass for move, chest for inventory, diamond pickaxe for mine, etc.)
[*]Displays command name, description, usage modes, and permission node per item; up to 45 commands per page; close button
[/LIST]

[B]📦 /fpp inventory[/B] [I](new)[/I]
[LIST]
[*]54-slot double-chest GUI showing the bot's full inventory — main storage, hotbar, equipment slots, and offhand
[*]Equipment slots enforce type restrictions (boots/leggings/chestplate/helmet/offhand)
[*]Right-click any bot entity to open without a command
[*]Permission: [FONT=monospace]fpp.inventory[/FONT]
[/LIST]

[B]🧭 /fpp move[/B] [I](new)[/I]
[LIST]
[*]Navigate a bot to an online player using server-side [B]A* pathfinding[/B]
[*]Supports WALK, ASCEND, DESCEND, PARKOUR, BREAK, PLACE move types; max 64-block range, 2000-node search
[*]Stuck detection + path recalculation when target moves; swim-safe jump coordination
[*]Permission: [FONT=monospace]fpp.move[/FONT]; Pathfinding options configurable via [FONT=monospace]pathfinding.*[/FONT] config section
[/LIST]

[B]⭐ /fpp xp[/B] [I](new)[/I]
[LIST]
[*]Transfer the bot's entire XP pool to yourself; clears bot levels and progress
[*]30-second post-collection cooldown on bot XP pickup (gated by [FONT=monospace]BotXpPickupListener[/FONT])
[*]Permission: [FONT=monospace]fpp.user.xp[/FONT] (user-tier, included in [FONT=monospace]fpp.use[/FONT])
[/LIST]

[B]💻 /fpp cmd[/B] [I](new)[/I]
[LIST]
[*][FONT=monospace]/fpp cmd <bot> <command>[/FONT] — dispatch a command as the bot via [FONT=monospace]Bukkit.dispatchCommand()[/FONT]
[*][FONT=monospace]--add <command>[/FONT] stores a right-click command; [FONT=monospace]--clear[/FONT] removes it; [FONT=monospace]--show[/FONT] displays it
[*]Right-clicking a bot with a stored command runs it instead of opening inventory GUI
[*]Permission: [FONT=monospace]fpp.cmd[/FONT]
[/LIST]

[B]⛏️ /fpp mine[/B] [I](new)[/I]
[LIST]
[*][FONT=monospace]/fpp mine <bot>[/FONT] — continuous block mining at the bot's look target
[*][FONT=monospace]once[/FONT] breaks a single block; [FONT=monospace]stop[/FONT] cancels mining; [FONT=monospace]/fpp mine stop[/FONT] stops all mining bots
[*]Creative mode = instant break with 5-tick cooldown; survival = progressive mining with destroy progress packets
[*]Permission: [FONT=monospace]fpp.mine[/FONT]
[/LIST]

[B]⚙️ Settings GUI Expanded[/B]
[LIST]
[*]Settings GUI now has [B]7 categories[/B]: General, Body, Chat, Swap, Peak Hours, PvP, Pathfinding (up from 5)
[*]New pathfinding toggles: parkour, break-blocks, place-blocks, place-material
[*]New PvP AI settings: difficulty, defensive-mode, detect-range
[/LIST]

[B]🛡️ WorldGuard Integration[/B]
[LIST]
[*]Bots protected from player-sourced PvP damage inside WorldGuard no-PvP regions
[*][FONT=monospace]WorldGuardHelper.isPvpAllowed(location)[/FONT] — fail-open: only explicit DENY blocks bot damage
[*]Soft-depend: WorldGuard auto-detected, fully optional; uses ClassLoader guard identical to LuckPerms pattern
[/LIST]

[B]📋 Config[/B]
[LIST]
[*]Config version bumped from [B]v47 → v51[/B] — adds pathfinding section, XP pickup gate, and cmd/mine subsystem keys
[*]New [FONT=monospace]body.pick-up-xp[/FONT] flag — gate orb pickup globally ([FONT=monospace]true[/FONT] by default)
[*]New [FONT=monospace]pathfinding.*[/FONT] section: [FONT=monospace]parkour[/FONT], [FONT=monospace]break-blocks[/FONT], [FONT=monospace]place-blocks[/FONT], [FONT=monospace]place-material[/FONT]
[/LIST]

[SIZE=5][B]v1.5.17[/B][/SIZE] [I](2026-04-07)[/I]

[B]🔄 Swap System — Critical Fix & Major Enhancements[/B]
[LIST]
[*][B]Critical bug fix:[/B] bots now actually rejoin after swapping out. The rejoin timer was being silently cancelled by [FONT=monospace]delete()[/FONT] calling [FONT=monospace]cancel(uuid)[/FONT] — bots left but never came back. Fixed by registering the rejoin task [I]after[/I] [FONT=monospace]delete()[/FONT] runs so [FONT=monospace]cancel()[/FONT] finds nothing to cancel.
[*]New [FONT=monospace]swap.min-online: 0[/FONT] — minimum bots that must stay online; swap skips if removing one would drop below this floor
[*]New [FONT=monospace]swap.retry-rejoin: true[/FONT] / [FONT=monospace]swap.retry-delay: 60[/FONT] — auto-retry failed rejoins (e.g. max-bots cap temporarily full)
[*]Better bot identification on rejoin: same-name rejoins use [FONT=monospace]getByName()[/FONT]; random-name rejoins use UUID diff
[*]New [FONT=monospace]Personality.SPORADIC[/FONT] type — unpredictable session variance for more natural patterns
[*]Expanded farewell/greeting message pools (~50 entries each)
[*]New [FONT=monospace]/fpp swap info <bot>[/FONT] — shows personality, cycle count, time until next leave, and offline-waiting count
[*][FONT=monospace]/fpp swap list[/FONT] now shows [B]time remaining[/B] in each session
[*][FONT=monospace]/fpp swap status[/FONT] now shows the [FONT=monospace]min-online[/FONT] floor setting
[*]New [FONT=monospace]logging.debug.swap: false[/FONT] — dedicated swap lifecycle debug channel
[/LIST]

[B]⚡ Performance Optimizations[/B]
[LIST]
[*]O(1) bot name lookup via secondary [FONT=monospace]nameIndex[/FONT] map — [FONT=monospace]getByName()[/FONT] was O(n) linear scan, now O(1) ConcurrentHashMap lookup maintained at all add/remove sites
[*]Position sync distance culling — position packets only broadcast to players within [FONT=monospace]performance.position-sync-distance: 128.0[/FONT] blocks (0 = unlimited); saves significant packet overhead on large servers
[/LIST]

[B]🔕 Log Cleanup[/B]
[LIST]
[*]NmsPlayerSpawner per-spawn/despawn log messages demoted from INFO → DEBUG; no more log spam on every bot cycle
[/LIST]

[B]📋 Config Reorganization[/B]
[LIST]
[*][FONT=monospace]config.yml[/FONT] restructured into 9 clearly labelled sections: Spawning · Appearance · Body & Combat · AI Systems · Bot Chat · Scheduling · Database & Network · Performance · Debug & Logging
[*]Config version → [B]v47[/B]
[/LIST]

[SIZE=5][B]v1.5.15[/B][/SIZE] [I](2026-04-06)[/I]

[B]📝 Config Clarity Improvements[/B]
[LIST]
[*]All timing-related values now clearly state their unit (ticks or seconds) with human-readable conversion examples
[*][FONT=monospace]join-delay[/FONT] / [FONT=monospace]leave-delay[/FONT] header updated: "Values are in TICKS — 20 ticks = 1 second" with quick-reference line; [FONT=monospace]min[/FONT]/[FONT=monospace]max[/FONT] keys now carry inline tick-unit comments
[*][FONT=monospace]death.respawn-delay[/FONT] comment shows seconds equivalents: 15 = 0.75 s · 60 = 3 s · 100 = 5 s
[*][FONT=monospace]chunk-loading.update-interval[/FONT] clarified: "in ticks (20 ticks = 1 second). Lower = more responsive, higher = less overhead."
[*][FONT=monospace]swap.session[/FONT] / [FONT=monospace]swap.absence[/FONT] inline comments updated with real-world time examples
[/LIST]

[B]🔧 Build Pipeline Fixes[/B]
[LIST]
[*]ProGuard: removed [FONT=monospace]**.yml[/FONT] from [FONT=monospace]-adaptresourcefilecontents[/FONT] — prevents charset corruption of plugin.yml and language files on Windows builds
[*]ProGuard: removed [FONT=monospace]-dontpreverify[/FONT] — StackMapTable attributes preserved; obfuscated jar passes JVM verifier
[*]ProGuard: MySQL / SQLite shaded classes excluded from preverification to prevent IncompleteClassHierarchyException; merged back verbatim into final jar
[/LIST]

[SIZE=5][B]v1.5.12[/B][/SIZE] [I](2026-04-05)[/I]

[B]🔒 Stable Bot UUID Identity[/B]
[LIST]
[*][FONT=monospace]BotIdentityCache[/FONT] — each bot name is permanently tied to a stable UUID; LuckPerms data, inventory, and session history persist across restarts
[*]Storage: in-memory cache → [FONT=monospace]fpp_bot_identities[/FONT] DB table → [FONT=monospace]data/bot-identities.yml[/FONT] YAML fallback
[/LIST]

[B]⚙️ In-Game Settings GUI[/B]
[LIST]
[*][FONT=monospace]/fpp settings[/FONT] opens a 3-row chest GUI; 5 categories (General, Body, Chat, Swap, Peak Hours)
[*]Toggle booleans instantly; numeric values via chat-input prompt; reset page to JAR defaults; all changes apply live
[*]Permission: [FONT=monospace]fpp.settings[/FONT]
[/LIST]

[B]⏰ Peak Hours Scheduler[/B]
[LIST]
[*][FONT=monospace]PeakHoursManager[/FONT] scales the bot pool by time-of-day windows ([FONT=monospace]peak-hours.schedule[/FONT], [FONT=monospace]day-overrides[/FONT], [FONT=monospace]stagger-seconds[/FONT])
[*]Crash-safe: sleeping-bot state persisted in [FONT=monospace]fpp_sleeping_bots[/FONT] DB table, restored at startup
[*]New command: [FONT=monospace]/fpp peaks [on|off|status|next|force|list|wake <name>|sleep <name>][/FONT] — requires [FONT=monospace]swap.enabled: true[/FONT]
[/LIST]

[B]💬 Per-Bot Chat Control[/B]
[LIST]
[*]Random activity tier per bot: quiet / passive / normal / active / chatty
[*][FONT=monospace]/fpp chat <bot> tier|mute|info[/FONT] per-bot controls; [FONT=monospace]/fpp chat all <on|off|tier|mute>[/FONT] for bulk operations
[*]Event-triggered chat ([FONT=monospace]event-triggers.*[/FONT]) and keyword reactions ([FONT=monospace]keyword-reactions.*[/FONT])
[/LIST]

[B]👻 Bodyless Bot Mode & Bot Types[/B]
[LIST]
[*][FONT=monospace]bodyless[/FONT] flag — bots without a world location exist in tab-list/chat only, no world entity
[*]BotType: AFK (passive) and PVP (combat via BotPvpAI)
[/LIST]

[B]🔧 Config Migration v41 → v44[/B]
[LIST]
[*]v41→v42: Added [FONT=monospace]peak-hours[/FONT] section · v42→v43: Added [FONT=monospace]min-online[/FONT], [FONT=monospace]notify-transitions[/FONT] · v43→v44: Removed [FONT=monospace]auto-enable-swap[/FONT]
[/LIST]

[SIZE=5][B]v1.5.10[/B][/SIZE] [I](2026-04-05)[/I]

[B]/fpp swap Toggle Fix[/B]
[LIST]
[*]Running [FONT=monospace]/fpp swap[/FONT] with no arguments now toggles swap on/off — exactly like [FONT=monospace]/fpp chat[/FONT]
[*]swap-enabled and swap-disabled messages redesigned to match chat toggle style ("session rotation has been enabled/disabled")
[*]swap-status-on / swap-status-off now follow the same "is enabled / is disabled" pattern as chat status messages
[/LIST]

[B]Bot Chat Interval Fix[/B]
[LIST]
[*]Bot chat loops are now restarted on [FONT=monospace]/fpp reload[/FONT] — changes to interval.min/max, chance, and stagger-interval take effect immediately instead of waiting for old scheduled tasks to expire
[*]/fpp reload output shows the new interval range as confirmation
[/LIST]

[B]Fake Chat Realism Enhancements[/B]
[LIST]
[*]typing-delay — simulates a 0–2.5 s typing pause before each message
[*]burst-chance / burst-delay — bots occasionally send a quick follow-up message
[*]reply-to-mentions / mention-reply-chance / reply-delay — bots can reply when a player says their name in chat
[*]activity-variation — random per-bot chat frequency tier (quiet/normal/active/very-active)
[*]history-size — bots avoid repeating their own recent messages
[*]remote-format — MiniMessage format for bodyless / proxy-remote bot broadcasts
[/LIST]

[B]Swim AI[/B]
[LIST]
[*]New swim-ai.enabled config key (default true) — bots automatically swim upward when submerged in water or lava
[*]Set to false to let bots sink instead
[/LIST]

[B]Language & Compatibility[/B]
[LIST]
[*]Biome.name() deprecated call replaced with Biome.getKey().getKey() — compatible with Paper 1.22+
[*]sync-usage and swap-now-usage messages now end with a period for consistency
[*]Startup banner now shows Bot swap status in the Features section
[*]Startup banner now shows actual Skin mode (auto/custom/off) instead of "disabled"
[*]Config version bumped to 41 — adds fake-chat realism keys, remote-format, event-triggers, keyword-reactions; removes tab-list-format and chat-format
[/LIST]

[SIZE=5][B]v1.5.8[/B][/SIZE] [I](2026-04-03)[/I]

[B]Ghost Player / "Anonymous User" Fix[/B]
[LIST]
[*]Replaced reflection-based Connection injection with a proper FakeConnection subclass — no-op send() overrides
[*]Eliminated the phantom "Anonymous User" entry with UUID 0 appearing in the tab list when bots connect
[*]Eliminated NullPointerException and ClassCastException log spam related to bot connections
[/LIST]

[B]%fpp_real% / %fpp_total% Accuracy Fix[/B]
[LIST]
[*]%fpp_real% now correctly subtracts bot count — bots appear in Bukkit.getOnlinePlayers() via placeNewPlayer()
[*]%fpp_real_<world>% similarly excludes bots from per-world real-player counts
[*]%fpp_total% fixed to avoid double-counting: real players + local bots (+ remote bots in NETWORK mode)
[/LIST]

[B]Proxy /fpp list Improvements (NETWORK mode)[/B]
[LIST]
[*]/fpp list shows [server-id] tags for local bots so admins know which server each bot belongs to
[*]Remote bots from other proxy servers shown in a dedicated "Remote bots" section with server, name, and skin info
[*]Total counts include both local and remote bots
[/LIST]

[B]New Proxy Placeholders[/B]
[LIST]
[*]%fpp_local_count% — bots on this server only
[*]%fpp_network_count% — bots on other proxy servers (NETWORK mode)
[*]%fpp_network_names% — comma-separated display names from remote servers
[*]%fpp_count% and %fpp_names% now include remote bots in NETWORK mode (29+ total placeholders)
[/LIST]

[B]LuckPerms ClassLoader Guard[/B]
[LIST]
[*]Fixed NoClassDefFoundError: net/luckperms/api/node/Node crash on servers without LuckPerms
[*]All LP-dependent code gated behind LuckPermsHelper.isAvailable() — no LP classes loaded unless LP is present
[/LIST]

[B]Technical[/B]
[LIST]
[*]Config version bumped to 37 (no structural key changes — version stamp only)
[*]Automatic migration on first startup from any previous version
[*]Fully backward compatible
[/LIST]

[SIZE=5][B]v1.5.6[/B][/SIZE] [I](2026-04-03)[/I]

[B]Knockback fix (1.21.9–1.21.11)[/B]
[LIST]
[*]Bots now correctly receive knockback on 1.21.9+ servers
[*]Tiered strategy system auto-detects the correct MC version API at startup (zero reflection overhead per hit)
[*]GET_MOVEMENT (1.21.9+): uses packet.getMovement() → Vec3 → player.lerpMotion(Vec3)
[*]GET_XA (≤1.21.8): uses packet.getXa/Ya/Za() → lerpMotion(double,double,double) or setDeltaMovement(Vec3) fallback
[/LIST]

[B]Double-disconnect crash fix (Paper 1.21+)[/B]
[LIST]
[*]Fixed IllegalStateException: Already retired spam when bots are slain
[*]injectPacketListenerIntoConnection() now updates both ServerPlayer.connection AND Connection.packetListener fields
[*]Ensures our onDisconnect override handles double-retirement gracefully
[/LIST]

[B]Bot Protection System[/B]
[LIST]
[*]Command blocking — bots can no longer execute commands from ANY source (4-layer protection)
[*]Lobby spawn fix — 5-tick grace period prevents lobby plugins from teleporting bots
[*]New BotCommandBlocker and BotSpawnProtectionListener
[/LIST]

[SIZE=5][B]v1.5.4[/B][/SIZE] [I](2026-04-03)[/I]

[B]PlaceholderAPI Expansion[/B]
[LIST]
[*]26+ placeholders across 5 categories (up from 18+)
[*]Fixed %fpp_skin% incorrectly returning "disabled" instead of actual mode
[*]Added %fpp_persistence% placeholder (shows on/off for persistence.enabled)
[*]New Network/Proxy category: %fpp_network%, %fpp_server_id%, %fpp_spawn_cooldown%
[/LIST]

[B]Skin System Simplified[/B]
[LIST]
[*]Removed skin.fallback-pool and fallback-name (eliminates API rate-limiting)
[*]Changed guaranteed-skin default from true → false
[*]Bots with non-Mojang names now use Steve/Alex skins by default
[*]Config section reduced from ~60 lines to ~18 lines
[/LIST]

[B]Config Migration v35→v36[/B]
[LIST]
[*]Auto-cleanup of orphaned LuckPerms keys (weight-offset, use-prefix, etc.)
[*]Removes old skin.custom section and server: section
[*]Automatic backup created before migration runs
[/LIST]

[B]New Features[/B]
[LIST]
[*]/fpp info screen includes Discord support link
[*]Full support for Leaf server (Paper fork)
[/LIST]

[B]Technical[/B]
[LIST]
[*]Config version bumped to 36
[*]Automatic migration on first startup
[*]Fully backward compatible
[/LIST]

[SIZE=5][B]v1.5.0[/B][/SIZE] [I](2026-03-31)[/I]
[LIST]
[*][B]Proxy/network mode[/B] — full Velocity & BungeeCord support; NETWORK database mode; cross-server chat, alerts, bot join/leave broadcasts, remote bot tab-list sync via fpp:main plugin messaging channel
[*][B]Config sync[/B] — /fpp sync push/pull/status/check; modes: DISABLED, MANUAL, AUTO_PULL, AUTO_PUSH; syncs config.yml, bot-names.yml, bot-messages.yml, language/en.yml; server-specific keys are never uploaded
[*][B]Remote bot cache[/B] — bots on other proxy servers tracked in thread-safe registry for tab-list sync
[*][B]BotTabTeam[/B] — scoreboard team ~fpp places all bots below real players in tab list regardless of LP weight
[*][B]Per-bot LuckPerms groups[/B] — /fpp rank <bot> <group>, /fpp rank random <group> [num|all], /fpp rank list; no respawn needed
[*][B]/fpp lpinfo [bot][/B] — in-game LP diagnostic: prefix, weight, rank index, packet profile name
[*][B]/fpp alert <message>[/B] — broadcast admin message to all servers on the proxy
[*][B]Body pushable/damageable toggles[/B] — body.pushable and body.damageable config keys; live-reloadable
[*][B]Fake-chat format[/B] — fake-chat.chat-format supports {prefix}, {bot_name}, {suffix}, {message}; full LP gradient and color support
[*][B]Tab-list name format[/B] — bot-name.tab-list-format supports {prefix}, {bot_name}, {suffix}, and any PAPI placeholder
[*][B]LuckPerms default group[/B] — luckperms.default-group config key; bots explicitly assigned "default" even when blank
[*][B]Spawn cooldown[/B] — spawn-cooldown config key; fpp.bypass.cooldown permission
[*][B]Per-subsystem debug logging[/B] — logging.debug.startup/nms/packets/luckperms/network/config-sync/skin/database
[*][B]YAML auto-sync[/B] — missing keys merged into en.yml, bot-names.yml, bot-messages.yml on every startup and reload
[*][B]/fpp migrate enhancements[/B] — status, backup, backups, lang, names, messages, config, db merge, db export, db tomysql
[*][B]Config version[/B] bumped to 33
[/LIST]

[SIZE=5][B]v1.4.28[/B][/SIZE] [I](2026-03-26)[/I]
[LIST]
[*][B]Skin diversity fix[/B] — guaranteed-skin fallback pool uses on-demand random selection at startup
[*][B]Vanilla skin pool[/B] — 27 official Minecraft system accounts (Mojang devs + MHF_* skins)
[*][B]Per-world placeholders[/B] — %fpp_count_<world>%, %fpp_real_<world>%, %fpp_total_<world>%
[*][B]%fpp_online%[/B] — alias for %fpp_total%
[*][B]Fake chat prefix/suffix[/B] — {prefix} and {suffix} in chat-format for full LP integration
[*][B]Spawn race condition fixed[/B] — /fpp despawn all during spawn no longer leaves ghost entries
[*][B]Portal/teleport bug fixed[/B] — PDC-based entity recovery for bots pushed through portals
[*][B]Body damageable toggle fixed[/B] — event-level cancellation replaces entity-flag-only approach
[*][B]Body config live reload[/B] — /fpp reload immediately applies body pushable/damageable changes
[/LIST]

[SIZE=5][B]v1.4.27[/B][/SIZE] [I](2026-03-25)[/I]
[LIST]
[*][B]Unified spawn syntax[/B] — /fpp spawn supports [count] [world] [x y z] [--name <name>]
[*][B]Improved /fpp reload output[/B] — box-drawing lines, per-step detail, timing line
[*][B]/fpp reload canUse fix[/B] — operators can now reload without explicit permission nodes
[/LIST]

[SIZE=5][B]v1.4.26[/B][/SIZE] [I](2026-03-25)[/I]
[LIST]
[*][B]Tab-list weight ordering overhauled[/B] — bots perfectly respect LP group weights
[*][B]Rank command system[/B] — /fpp rank <bot> <group> and /fpp rank random
[*][B]Restoration bug fixed[/B] — bots restored after restart maintain correct weights and ranks
[*][B]Auto-update on group change[/B] — prefixes and tab ordering update in real-time
[/LIST]

[SPOILER="Previous Versions"]
[SIZE=5][B]v1.4.24[/B][/SIZE] [I](2026-03-24)[/I]
[LIST]
[*]YAML file syncer — missing keys auto-merged on startup and /fpp reload
[*]/fpp migrate lang|names|messages — force-sync YAML files from JAR
[*]/fpp migrate status — file-sync health panel
[/LIST]

[SIZE=5][B]v1.4.23[/B][/SIZE] [I](2026-03-23)[/I]
[LIST]
[*]Fixed bot name colours lost after server restart
[*]Fixed join/leave delays 20x longer than configured
[*]/fpp reload refreshes bot prefixes from LuckPerms immediately
[*]Added /fpp despawn random [amount]
[/LIST]

[SIZE=5][B]v1.4.22[/B][/SIZE] [I](2026-03-22)[/I]
[LIST]
[*]tab-list.enabled — toggle bot visibility in the tab list
[*]Multi-platform download links in update notifications
[*]Enhanced /fpp reload with step-by-step progress
[*]Update checker now uses Modrinth API as primary source
[*]Bug fixes: tab-list migration, StackOverflowError, NullPointerException, LP gradient support
[/LIST]

[SIZE=5][B]v1.2.7[/B][/SIZE] [I](2026-03-14)[/I]
[LIST]
[*]/fpp freeze, /fpp stats, PlaceholderAPI expansion, spawn cooldown, animated tab-list header/footer, metrics toggle
[/LIST]

[SIZE=5][B]v1.2.2[/B][/SIZE] [I](2026-03-14)[/I]
[LIST]
[*]Guaranteed Skin system, skin.fallback-name, Mojang API rate-limit fix, config auto-migration
[/LIST]

[SIZE=5][B]v1.0.0-rc1[/B][/SIZE] [I](2026-03-08)[/I]
[LIST]
[*]First stable release: full permission system, user-tier commands, bot persistence
[/LIST]

[SIZE=5][B]v0.1.0[/B][/SIZE]
[LIST]
[*]Initial release: tab list, join/leave messages, in-world body, head AI, collision/push system
[/LIST]
[/SPOILER]

[HR][/HR]

[SIZE=6][B]❤️ Support the Project[/B][/SIZE]

If you enjoy FPP and want to help keep it going, consider buying me a coffee:

[CENTER][URL='https://ko-fi.com/fakeplayerplugin'][B][COLOR=#FF5E5B]☕ Support on Ko-fi[/COLOR][/B][/URL]  [URL='https://github.com/sponsors/Pepe-tf'][B][COLOR=#EA4AAA]💖 GitHub Sponsors[/COLOR][/B][/URL]  [URL='https://www.patreon.com/c/F_PP?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink'][B][COLOR=#FF424D]🎗 Support on Patreon[/COLOR][/B][/URL][/CENTER]

Donations are completely optional. Every contribution goes directly toward improving the plugin.

Thank you for using Fake Player Plugin. Without you, it wouldn't be where it is today.

[HR][/HR]

[SIZE=6][B]🔗 Links[/B][/SIZE]

[LIST]
[*][URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)']Modrinth[/URL] — download
[*][URL='https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/']SpigotMC[/URL] — download
[*][URL='https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin']PaperMC Hangar[/URL] — download
[*][URL='https://builtbybit.com/resources/fake-player-plugin.98704/']BuiltByBit[/URL] — download
[*][URL='https://fakeplayerplugin.xyz']Wiki[/URL] — documentation
[*][URL='https://ko-fi.com/fakeplayerplugin']Ko-fi[/URL] — support the project
[*][URL='https://github.com/sponsors/Pepe-tf']GitHub Sponsors[/URL] — support the project
[*][URL='https://www.patreon.com/c/F_PP?utm_medium=unknown&utm_source=join_link&utm_campaign=creatorshare_creator&utm_content=copyLink']Patreon[/URL] — support the project
[*][URL='https://discord.gg/QSN7f67nkJ']Discord[/URL] — support & feedback
[*][URL='https://github.com/Pepe-tf/Fake-Player-Plugin-Public-']GitHub[/URL] — source & issues
[/LIST]

[HR][/HR]

[CENTER][I]Built for Paper 1.21.x (1.21.0–1.21.11) · Java 21 · FPP v1.6.6.2[/I]

[URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)']Modrinth[/URL]  [URL='https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/']SpigotMC[/URL]  [URL='https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin']PaperMC[/URL]  [URL='https://builtbybit.com/resources/fake-player-plugin.98704/']BuiltByBit[/URL]  [URL='https://fakeplayerplugin.xyz']Wiki[/URL][/CENTER]

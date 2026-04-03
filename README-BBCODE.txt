[CENTER][SIZE=7][B]ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)[/B][/SIZE]

[SIZE=5][I]Spawn realistic fake players on your Paper server — with tab list presence, server list count, join/leave messages, in-world bodies, guaranteed skins, chunk loading, bot swap/rotation, fake chat, LuckPerms integration, proxy network support, and full hot-reload.[/I][/SIZE]

[SIZE=4][B]Version:[/B] 1.5.6  [B]Minecraft:[/B] 1.21.x  [B]Platform:[/B] Paper  [B]Java:[/B] 21+[/SIZE]

[URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)'][B][COLOR=#00AF5C]⬇ Download on Modrinth[/COLOR][/B][/URL]  [URL='https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/'][B][COLOR=#FF6B35]⬇ SpigotMC[/COLOR][/B][/URL]  [URL='https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin'][B][COLOR=#00BFD8]⬇ PaperMC Hangar[/COLOR][/B][/URL]  [URL='https://builtbybit.com/resources/fake-player-plugin.98704/'][B][COLOR=#A855F7]⬇ BuiltByBit[/COLOR][/B][/URL]
[URL='https://discord.gg/QSN7f67nkJ'][B][COLOR=#5865F2]💬 Join Discord[/COLOR][/B][/URL]  [URL='https://fakeplayerplugin.xyz'][B][COLOR=#7B8EF0]📖 Wiki[/COLOR][/B][/URL]  [URL='https://ko-fi.com/fakeplayerplugin'][B][COLOR=#FF5E5B]☕ Support on Ko-fi[/COLOR][/B][/URL]
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
[*][B]Send fake chat messages[/B] from a configurable message pool (with LP prefix/suffix support)
[*][B]Swap in and out[/B] automatically with fresh names and personalities
[*][B]Persist across restarts[/B] — they come back where they left off
[*][B]Freeze[/B] any bot in place with [FONT=monospace]/fpp freeze[/FONT]
[*][B]LuckPerms[/B] — per-bot group assignment, weighted tab-list ordering, prefix/suffix in chat and nametags
[*][B]Proxy/network support[/B] — Velocity & BungeeCord cross-server chat, alerts, and shared database
[*][B]Config sync[/B] — push/pull configuration files across your proxy network
[*][B]PlaceholderAPI[/B] — 26+ placeholders including per-world bot counts, network state, and spawn cooldown
[*]Fully [B]hot-reloadable[/B] — no restarts needed
[/LIST]

[HR][/HR]

[SIZE=6][B]📋 Requirements[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Requirement[/B][/TD][TD][B]Version[/B][/TD][/TR]
[TR][TD][URL='https://papermc.io/downloads/paper']Paper[/URL][/TD][TD]1.21.x[/TD][/TR]
[TR][TD]Java[/TD][TD]21+[/TD][/TR]
[TR][TD][URL='https://modrinth.com/plugin/packetevents']PacketEvents[/URL][/TD][TD]2.x[/TD][/TR]
[TR][TD][URL='https://luckperms.net']LuckPerms[/URL][/TD][TD]Optional — auto-detected[/TD][/TR]
[TR][TD][URL='https://www.spigotmc.org/resources/placeholderapi.6245/']PlaceholderAPI[/URL][/TD][TD]Optional — auto-detected (26+ placeholders)[/TD][/TR]
[/TABLE]

[B]Note:[/B] Semi-support for older 1.21 releases (1.21.0 → 1.21.8). FPP will run in restricted compatibility mode — check console for warnings.

[I]SQLite is bundled — no database setup required. MySQL is available for multi-server/proxy setups.[/I]

[HR][/HR]

[SIZE=6][B]🚀 Installation[/B][/SIZE]

[LIST=1]
[*]Download the latest [FONT=monospace]fpp-*.jar[/FONT] from [URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)/versions']Modrinth[/URL] and place it in your [FONT=monospace]plugins/[/FONT] folder.
[*]Download [URL='https://modrinth.com/plugin/packetevents']PacketEvents[/URL] and place it in [FONT=monospace]plugins/[/FONT] too.
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
[TR][TD][FONT=monospace]/fpp help [page][/FONT][/TD][TD]Paginated help with clickable navigation[/TD][/TR]
[TR][TD][FONT=monospace]/fpp spawn [amount] [--name <name>][/FONT][/TD][TD]Spawn fake player(s) at your location[/TD][/TR]
[TR][TD][FONT=monospace]/fpp despawn <name|all|random [n]>[/FONT][/TD][TD]Remove a bot by name, remove all, or remove a random set[/TD][/TR]
[TR][TD][FONT=monospace]/fpp list[/FONT][/TD][TD]List all active bots with uptime and location[/TD][/TR]
[TR][TD][FONT=monospace]/fpp freeze <name|all> [on|off][/FONT][/TD][TD]Freeze or unfreeze bots — frozen bots are immovable; shown with ❄ in list/stats[/TD][/TR]
[TR][TD][FONT=monospace]/fpp chat [on|off|status][/FONT][/TD][TD]Toggle the fake chat system[/TD][/TR]
[TR][TD][FONT=monospace]/fpp swap [on|off|status][/FONT][/TD][TD]Toggle the bot swap/rotation system[/TD][/TR]
[TR][TD][FONT=monospace]/fpp rank <bot> <group>[/FONT][/TD][TD]Assign a specific bot to a LuckPerms group[/TD][/TR]
[TR][TD][FONT=monospace]/fpp rank random <group> [num|all][/FONT][/TD][TD]Assign random bots to a LuckPerms group[/TD][/TR]
[TR][TD][FONT=monospace]/fpp rank list[/FONT][/TD][TD]List all active bots with their current LuckPerms group[/TD][/TR]
[TR][TD][FONT=monospace]/fpp lpinfo [bot-name][/FONT][/TD][TD]LuckPerms diagnostic info — prefix, weight, rank, ordering[/TD][/TR]
[TR][TD][FONT=monospace]/fpp stats[/FONT][/TD][TD]Live statistics panel — bots, frozen, system status, DB totals, TPS[/TD][/TR]
[TR][TD][FONT=monospace]/fpp info [bot <name> | spawner <name>][/FONT][/TD][TD]Query the session database[/TD][/TR]
[TR][TD][FONT=monospace]/fpp tp <name>[/FONT][/TD][TD]Teleport yourself to a bot[/TD][/TR]
[TR][TD][FONT=monospace]/fpp tph [name][/FONT][/TD][TD]Teleport your bot to yourself[/TD][/TR]
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

[SIZE=5][B]Admin[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Permission[/B][/TD][TD][B]Description[/B][/TD][/TR]
[TR][TD][FONT=monospace]fpp.*[/FONT][/TD][TD]All permissions (admin wildcard)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.spawn[/FONT][/TD][TD]Spawn bots (unlimited, supports --name and multi-spawn)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.spawn.multiple[/FONT][/TD][TD]Spawn more than 1 bot at a time[/TD][/TR]
[TR][TD][FONT=monospace]fpp.spawn.name[/FONT][/TD][TD]Use the --name flag[/TD][/TR]
[TR][TD][FONT=monospace]fpp.delete[/FONT][/TD][TD]Remove bots[/TD][/TR]
[TR][TD][FONT=monospace]fpp.delete.all[/FONT][/TD][TD]Remove all bots at once[/TD][/TR]
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
[TR][TD][FONT=monospace]fpp.bypass.maxbots[/FONT][/TD][TD]Bypass the global bot cap[/TD][/TR]
[TR][TD][FONT=monospace]fpp.bypass.cooldown[/FONT][/TD][TD]Bypass the per-player spawn cooldown[/TD][/TR]
[TR][TD][FONT=monospace]fpp.admin.migrate[/FONT][/TD][TD]Backup, migrate, and export database[/TD][/TR]
[/TABLE]

[SIZE=5][B]User[/B][/SIZE] [I](enabled for all players by default)[/I]

[TABLE="width: 100%"]
[TR][TD][B]Permission[/B][/TD][TD][B]Description[/B][/TD][/TR]
[TR][TD][FONT=monospace]fpp.user.*[/FONT][/TD][TD]All user commands[/TD][/TR]
[TR][TD][FONT=monospace]fpp.user.spawn[/FONT][/TD][TD]Spawn your own bot (limited by fpp.bot.<num>)[/TD][/TR]
[TR][TD][FONT=monospace]fpp.user.tph[/FONT][/TD][TD]Teleport your bot to you[/TD][/TR]
[TR][TD][FONT=monospace]fpp.user.info[/FONT][/TD][TD]View your bot's location and uptime[/TD][/TR]
[/TABLE]

[SIZE=5][B]Bot Limits[/B][/SIZE]

Grant players a [FONT=monospace]fpp.bot.<num>[/FONT] node to set how many bots they can spawn. FPP picks the highest one they have.

[FONT=monospace]fpp.bot.1[/FONT]  [FONT=monospace]fpp.bot.2[/FONT]  [FONT=monospace]fpp.bot.3[/FONT]  [FONT=monospace]fpp.bot.5[/FONT]  [FONT=monospace]fpp.bot.10[/FONT]  [FONT=monospace]fpp.bot.15[/FONT]  [FONT=monospace]fpp.bot.20[/FONT]  [FONT=monospace]fpp.bot.50[/FONT]  [FONT=monospace]fpp.bot.100[/FONT]

[B]LuckPerms example[/B] — give VIPs 5 bots:
[CODE]
/lp group vip permission set fpp.user.spawn true
/lp group vip permission set fpp.bot.5 true
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
[TR][TD][FONT=monospace]bot-name[/FONT][/TD][TD]Admin/user display name format; tab-list-format with {prefix}/{bot_name}/{suffix}[/TD][/TR]
[TR][TD][FONT=monospace]luckperms[/FONT][/TD][TD]default-group — LP group assigned to every new bot at spawn[/TD][/TR]
[TR][TD][FONT=monospace]skin[/FONT][/TD][TD]Skin mode (auto/custom/off), guaranteed skin, fallback chain and pool[/TD][/TR]
[TR][TD][FONT=monospace]body[/FONT][/TD][TD]Physical entity (enabled), pushable, damageable — all live-reloadable[/TD][/TR]
[TR][TD][FONT=monospace]persistence[/FONT][/TD][TD]Whether bots rejoin on server restart[/TD][/TR]
[TR][TD][FONT=monospace]join-delay / leave-delay[/FONT][/TD][TD]Random delay range (ticks) for natural join/leave timing[/TD][/TR]
[TR][TD][FONT=monospace]messages[/FONT][/TD][TD]Toggle join, leave, kill broadcast messages; admin compatibility notifications[/TD][/TR]
[TR][TD][FONT=monospace]combat[/FONT][/TD][TD]Bot HP and hurt sound[/TD][/TR]
[TR][TD][FONT=monospace]death[/FONT][/TD][TD]Respawn on death, respawn delay, item drop suppression[/TD][/TR]
[TR][TD][FONT=monospace]chunk-loading[/FONT][/TD][TD]Radius, update interval[/TD][/TR]
[TR][TD][FONT=monospace]head-ai[/FONT][/TD][TD]Enable/disable, look range, turn speed[/TD][/TR]
[TR][TD][FONT=monospace]collision[/FONT][/TD][TD]Push physics — walk strength, hit strength, bot separation[/TD][/TR]
[TR][TD][FONT=monospace]swap[/FONT][/TD][TD]Auto rotation — session length, farewell/greeting chat, AFK simulation[/TD][/TR]
[TR][TD][FONT=monospace]fake-chat[/FONT][/TD][TD]Enable, chance, interval, chat-format ({prefix}/{bot_name}/{suffix}/{message})[/TD][/TR]
[TR][TD][FONT=monospace]tab-list[/FONT][/TD][TD]Show/hide bots in the player tab list[/TD][/TR]
[TR][TD][FONT=monospace]config-sync[/FONT][/TD][TD]Cross-server config push/pull mode (DISABLED/MANUAL/AUTO_PULL/AUTO_PUSH)[/TD][/TR]
[TR][TD][FONT=monospace]database[/FONT][/TD][TD]mode (LOCAL/NETWORK), server-id, SQLite (default) or MySQL[/TD][/TR]
[/TABLE]

[HR][/HR]

[SIZE=6][B]🎨 Skin System[/B][/SIZE]

Three modes — set with [FONT=monospace]skin.mode[/FONT]:

[TABLE="width: 100%"]
[TR][TD][B]Mode[/B][/TD][TD][B]Behaviour[/B][/TD][/TR]
[TR][TD][FONT=monospace]auto[/FONT] [I](default)[/I][/TD][TD]Fetches a real Mojang skin matching the bot's name[/TD][/TR]
[TR][TD][FONT=monospace]custom[/FONT][/TD][TD]Full control — per-bot overrides, a skins/ PNG folder, and a random pool[/TD][/TR]
[TR][TD][FONT=monospace]off[/FONT][/TD][TD]No skin — bots use the default Steve/Alex appearance[/TD][/TR]
[/TABLE]

[B]Skin fallback[/B] ([FONT=monospace]skin.guaranteed-skin[/FONT], default [FONT=monospace]false[/FONT]) — when [FONT=monospace]false[/FONT], bots whose name has no matching Mojang account use the default Steve/Alex appearance. Set to [FONT=monospace]true[/FONT] to attempt a skin fetch even for generated names.

In [FONT=monospace]custom[/FONT] mode the resolution pipeline is: per-bot override → [FONT=monospace]skins/<name>.png[/FONT] → random PNG from [FONT=monospace]skins/[/FONT] folder → random entry from [FONT=monospace]pool[/FONT] → Mojang API for the bot's own name.

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
[*][B]Prefix/suffix[/B] — bot nametags and chat format support {prefix} and {suffix} placeholders
[*][B]Display name format[/B] — bot-name.tab-list-format supports {prefix}, {bot_name}, {suffix}, and PAPI placeholders
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

FPP provides [B]26+ placeholders[/B] in five categories:

[SIZE=5][B]Server-Wide[/B][/SIZE]

[TABLE="width: 100%"]
[TR][TD][B]Placeholder[/B][/TD][TD][B]Value[/B][/TD][/TR]
[TR][TD][FONT=monospace]%fpp_count%[/FONT][/TD][TD]Number of currently active bots[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_max%[/FONT][/TD][TD]Global max-bots limit (or ∞)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_real%[/FONT][/TD][TD]Real (non-bot) players online[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_total%[/FONT][/TD][TD]Total players (real + bots)[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_online%[/FONT][/TD][TD]Alias for %fpp_total%[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_frozen%[/FONT][/TD][TD]Number of currently frozen bots[/TD][/TR]
[TR][TD][FONT=monospace]%fpp_names%[/FONT][/TD][TD]Comma-separated list of bot display names[/TD][/TR]
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

[B]Chat format[/B] ([FONT=monospace]fake-chat.chat-format[/FONT]) supports MiniMessage and legacy & codes:
[CODE]
fake-chat:
  chat-format: "&7{prefix}{bot_name}&7: {message}"
[/CODE]

Placeholders: [FONT=monospace]{prefix}[/FONT] (LP prefix), [FONT=monospace]{bot_name}[/FONT], [FONT=monospace]{suffix}[/FONT] (LP suffix), [FONT=monospace]{message}[/FONT]

[HR][/HR]

[SIZE=6][B]📖 Changelog[/B][/SIZE]

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

[CENTER][URL='https://ko-fi.com/fakeplayerplugin'][B][COLOR=#FF5E5B]☕ Support on Ko-fi[/COLOR][/B][/URL][/CENTER]

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
[*][URL='https://discord.gg/QSN7f67nkJ']Discord[/URL] — support & feedback
[*][URL='https://github.com/Pepe-tf/Fake-Player-Plugin-Public-']GitHub[/URL] — source & issues
[/LIST]

[HR][/HR]

[CENTER][I]Built for Paper 1.21.x · Java 21 · FPP v1.5.6[/I]

[URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)']Modrinth[/URL]  [URL='https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/']SpigotMC[/URL]  [URL='https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin']PaperMC[/URL]  [URL='https://builtbybit.com/resources/fake-player-plugin.98704/']BuiltByBit[/URL]  [URL='https://fakeplayerplugin.xyz']Wiki[/URL][/CENTER]

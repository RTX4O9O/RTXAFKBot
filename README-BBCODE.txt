[CENTER][SIZE=7][B]ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ (FPP)[/B][/SIZE]

[SIZE=5][I]Spawn realistic fake players on your Paper server — with tab list presence, server list count, join/leave messages, in-world bodies, guaranteed skins, chunk loading, bot swap/rotation, fake chat, and full hot-reload support.[/I][/SIZE]

[SIZE=4][B]Version:[/B] 1.4.28 | [B]Minecraft:[/B] 1.21.x | [B]Platform:[/B] Paper | [B]Java:[/B] 21+[/SIZE]

[URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)'][B][COLOR=#00AF5C]🟢 Download on Modrinth[/COLOR][/B][/URL]  [URL='https://discord.gg/QSN7f67nkJ'][B][COLOR=#5865F2]💬 Join Discord[/COLOR][/B][/URL]  [URL='https://fakeplayerplugin.xyz'][B][COLOR=#7B8EF0]📖 Wiki[/COLOR][/B][/URL]
[/CENTER]

[HR][/HR]

[SIZE=6][B]✦ What It Does[/B][/SIZE]

FPP adds fake players to your server that look and behave like real ones:

[LIST]
[*] Show up in the [B]tab list[/B] and [B]server list player count[/B]
[*] Broadcast [B]join, leave, and kill messages[/B]
[*] Spawn as [B]physical entities[/B] in the world — pushable, damageable, solid
[*] Always have a [B]real skin[/B] (no Steve/Alex unless you want it)
[*] [B]Load chunks[/B] around them exactly like a real player
[*] [B]Rotate their head[/B] to face nearby players
[*] [B]Send fake chat messages[/B] from a configurable message pool
[*] [B]Swap in and out[/B] automatically with fresh names and personalities
[*] [B]Persist across restarts[/B] — they come back where they left off
[*] [B]Freeze[/B] any bot in place with [FONT=monospace]/fpp freeze[/FONT]
[*] [B]PlaceholderAPI[/B] support — display bot count and status anywhere
[*] Fully [B]hot-reloadable[/B] — no restarts needed
[/LIST]

[HR][/HR]

[SIZE=6][B]✦ Requirements[/B][/SIZE]

[TABLE="width: 100%"]
[TR]
[TD][B]Requirement[/B][/TD]
[TD][B]Version[/B][/TD]
[/TR]
[TR]
[TD][URL='https://papermc.io/downloads/paper']Paper[/URL][/TD]
[TD]1.21.x[/TD]
[/TR]
[TR]
[TD]Java[/TD]
[TD]21+[/TD]
[/TR]
[TR]
[TD][URL='https://modrinth.com/plugin/packetevents']PacketEvents[/URL][/TD]
[TD]2.x[/TD]
[/TR]
[TR]
[TD][URL='https://luckperms.net']LuckPerms[/URL][/TD]
[TD]Optional — auto-detected[/TD]
[/TR]
[TR]
[TD][URL='https://www.spigotmc.org/resources/placeholderapi.6245/']PlaceholderAPI[/URL][/TD]
[TD]Optional — auto-detected (18 placeholders)[/TD]
[/TR]
[/TABLE]

[B]PlaceholderAPI Integration:[/B] FPP provides 18+ placeholders including per-world bot counts, player-relative stats, and system status. Full documentation available on [URL='https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/blob/main/PLACEHOLDERAPI.md']GitHub[/URL].

[B]Note:[/B] Semi-support is available for older 1.21 releases (1.21.0 → 1.21.8). On those servers some features may be disabled and FPP will run in a restricted compatibility mode — check the server console for detailed warnings.

[I]SQLite is bundled — no database setup required. MySQL is available for multi-server setups.[/I]

[HR][/HR]

[SIZE=6][B]✦ Installation[/B][/SIZE]

[LIST=1]
[*] Download the latest [FONT=monospace]fpp-*.jar[/FONT] from [URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)/versions']Modrinth[/URL] and place it in your [FONT=monospace]plugins/[/FONT] folder.
[*] Download [URL='https://modrinth.com/plugin/packetevents']PacketEvents[/URL] and place it in [FONT=monospace]plugins/[/FONT] too.
[*] Restart your server — config files are created automatically.
[*] Edit [FONT=monospace]plugins/FakePlayerPlugin/config.yml[/FONT] to your liking.
[*] Run [FONT=monospace]/fpp reload[/FONT] to apply changes at any time.
[/LIST]

[B]Updating?[/B] FPP automatically migrates your config on first start and creates a timestamped backup before changing anything.

[HR][/HR]

[SIZE=6][B]✦ Commands[/B][/SIZE]

All commands are under [FONT=monospace]/fpp[/FONT] (aliases: [FONT=monospace]/fakeplayer[/FONT], [FONT=monospace]/fp[/FONT]).

[TABLE="width: 100%"]
[TR]
[TD][B]Command[/B][/TD]
[TD][B]Description[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp[/FONT][/TD]
[TD]Plugin info — version, active bots, Modrinth link[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp help [page][/FONT][/TD]
[TD]Paginated help with clickable navigation[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp spawn [amount] [--name <name>][/FONT][/TD]
[TD]Spawn fake player(s) at your location[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp delete <name|all>[/FONT][/TD]
[TD]Remove a bot by name, or remove all[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp list[/FONT][/TD]
[TD]List all active bots with uptime and location[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp chat [on|off|status][/FONT][/TD]
[TD]Toggle the fake chat system[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp swap [on|off|status][/FONT][/TD]
[TD]Toggle the bot swap/rotation system[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp freeze <name|all> [on|off][/FONT][/TD]
[TD]Freeze or unfreeze a bot — body becomes immovable; shown with ❄ in list/stats[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp rank set <bot> <luckperms-group>[/FONT][/TD]
[TD]Assign a specific bot to a LuckPerms group[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp rank random <luckperms-group> [count|all][/FONT][/TD]
[TD]Assign random bots (or all bots) to a LuckPerms group[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp stats[/FONT][/TD]
[TD]Live statistics panel — bots, frozen count, system status, DB totals, TPS[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp reload[/FONT][/TD]
[TD]Hot-reload all config, language, skins, and name/message pools[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp info [bot <name> | spawner <name>][/FONT][/TD]
[TD]Query the session database[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp tp <name>[/FONT][/TD]
[TD]Teleport yourself to a bot[/TD]
[/TR]
[TR]
[TD][FONT=monospace]/fpp tph[/FONT][/TD]
[TD]Teleport your bot to yourself[/TD]
[/TR]
[/TABLE]

[HR][/HR]

[SIZE=6][B]✦ Permissions[/B][/SIZE]

[SIZE=5][B]Admin[/B][/SIZE]

[TABLE="width: 100%"]
[TR]
[TD][B]Permission[/B][/TD]
[TD][B]Description[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.*[/FONT][/TD]
[TD]All permissions[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.spawn[/FONT][/TD]
[TD]Spawn bots (unlimited, supports --name and multi-spawn)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.delete[/FONT][/TD]
[TD]Remove bots[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.list[/FONT][/TD]
[TD]List all active bots[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.chat[/FONT][/TD]
[TD]Toggle fake chat[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.swap[/FONT][/TD]
[TD]Toggle bot swap[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.freeze[/FONT][/TD]
[TD]Freeze / unfreeze any bot or all bots[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.rank[/FONT][/TD]
[TD]Assign bots to LuckPerms groups (set or random)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.stats[/FONT][/TD]
[TD]View the /fpp stats live statistics panel[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.reload[/FONT][/TD]
[TD]Reload configuration[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.info[/FONT][/TD]
[TD]Query the database[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.tp[/FONT][/TD]
[TD]Teleport to bots[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.bypass.maxbots[/FONT][/TD]
[TD]Bypass the global bot cap[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.bypass.cooldown[/FONT][/TD]
[TD]Bypass the per-player spawn cooldown[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.admin.migrate[/FONT][/TD]
[TD]Backup, migrate, and export database[/TD]
[/TR]
[/TABLE]

[SIZE=5][B]User[/B][/SIZE] [I](enabled for all players by default)[/I]

[TABLE="width: 100%"]
[TR]
[TD][B]Permission[/B][/TD]
[TD][B]Description[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.user.*[/FONT][/TD]
[TD]All user commands[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.user.spawn[/FONT][/TD]
[TD]Spawn your own bot (limited by fpp.bot.<num>)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.user.tph[/FONT][/TD]
[TD]Teleport your bot to you[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fpp.user.info[/FONT][/TD]
[TD]View your bot's location and uptime[/TD]
[/TR]
[/TABLE]

[SIZE=5][B]Bot Limits[/B][/SIZE]

Grant players a [FONT=monospace]fpp.bot.<num>[/FONT] node to set how many bots they can spawn. FPP picks the highest one they have.

[FONT=monospace]fpp.bot.1[/FONT] · [FONT=monospace]fpp.bot.2[/FONT] · [FONT=monospace]fpp.bot.3[/FONT] · [FONT=monospace]fpp.bot.5[/FONT] · [FONT=monospace]fpp.bot.10[/FONT] · [FONT=monospace]fpp.bot.15[/FONT] · [FONT=monospace]fpp.bot.20[/FONT] · [FONT=monospace]fpp.bot.50[/FONT] · [FONT=monospace]fpp.bot.100[/FONT]

[B]LuckPerms example[/B] — give VIPs 5 bots:
[CODE]
/lp group vip permission set fpp.user.spawn true
/lp group vip permission set fpp.bot.5 true
[/CODE]

[HR][/HR]

[SIZE=6][B]✦ Configuration Overview[/B][/SIZE]

Located at [FONT=monospace]plugins/FakePlayerPlugin/config.yml[/FONT]. Run [FONT=monospace]/fpp reload[/FONT] after any change.

[TABLE="width: 100%"]
[TR]
[TD][B]Section[/B][/TD]
[TD][B]What it controls[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]language[/FONT][/TD]
[TD]Language file to load (language/en.yml)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]limits[/FONT][/TD]
[TD]Global bot cap, per-user limit[/TD]
[/TR]
[TR]
[TD][FONT=monospace]spawn-cooldown[/FONT][/TD]
[TD]Seconds between /fpp spawn uses per player (0 = off)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]bot-name[/FONT][/TD]
[TD]Display name format for admin and user bots[/TD]
[/TR]
[TR]
[TD][FONT=monospace]luckperms[/FONT][/TD]
[TD]Whether to prepend the default-group prefix to bot names[/TD]
[/TR]
[TR]
[TD][FONT=monospace]skin[/FONT][/TD]
[TD]Skin mode (auto / custom / off), guaranteed skin, fallback account[/TD]
[/TR]
[TR]
[TD][FONT=monospace]body[/FONT][/TD]
[TD]Whether bots have a physical entity in the world[/TD]
[/TR]
[TR]
[TD][FONT=monospace]persistence[/FONT][/TD]
[TD]Whether bots rejoin on server restart[/TD]
[/TR]
[TR]
[TD][FONT=monospace]join-delay / leave-delay[/FONT][/TD]
[TD]Random delay range (ticks) for natural join/leave timing[/TD]
[/TR]
[TR]
[TD][FONT=monospace]messages[/FONT][/TD]
[TD]Toggle join, leave, and kill broadcast messages[/TD]
[/TR]
[TR]
[TD][FONT=monospace]combat[/FONT][/TD]
[TD]Bot HP and hurt sound[/TD]
[/TR]
[TR]
[TD][FONT=monospace]death[/FONT][/TD]
[TD]Respawn on death, respawn delay, item drop suppression[/TD]
[/TR]
[TR]
[TD][FONT=monospace]chunk-loading[/FONT][/TD]
[TD]Radius, update interval[/TD]
[/TR]
[TR]
[TD][FONT=monospace]head-ai[/FONT][/TD]
[TD]Enable/disable, look range, turn speed[/TD]
[/TR]
[TR]
[TD][FONT=monospace]swap[/FONT][/TD]
[TD]Auto rotation — session length, farewell/greeting chat, AFK simulation[/TD]
[/TR]
[TR]
[TD][FONT=monospace]fake-chat[/FONT][/TD]
[TD]Enable, message chance, interval[/TD]
[/TR]
[TR]
[TD][FONT=monospace]tab-list[/FONT][/TD]
[TD]Optional animated tab-list header/footer with bot count placeholders; show-bots controls whether bots appear as tab-list entries[/TD]
[/TR]
[TR]
[TD][FONT=monospace]metrics[/FONT][/TD]
[TD]Opt-out toggle for anonymous FastStats usage statistics[/TD]
[/TR]
[TR]
[TD][FONT=monospace]database[/FONT][/TD]
[TD]SQLite (default) or MySQL[/TD]
[/TR]
[/TABLE]

[HR][/HR]

[SIZE=6][B]✦ Skin System[/B][/SIZE]

Three modes — set with [FONT=monospace]skin.mode[/FONT]:

[TABLE="width: 100%"]
[TR]
[TD][B]Mode[/B][/TD]
[TD][B]Behaviour[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]auto[/FONT] [I](default)[/I][/TD]
[TD]Fetches a real Mojang skin matching the bot's name[/TD]
[/TR]
[TR]
[TD][FONT=monospace]custom[/FONT][/TD]
[TD]Full control — per-bot overrides, a skins/ PNG folder, and a random pool[/TD]
[/TR]
[TR]
[TD][FONT=monospace]off[/FONT][/TD]
[TD]No skin — bots use the default Steve/Alex appearance[/TD]
[/TR]
[/TABLE]

[B]Guaranteed Skin[/B] ([FONT=monospace]skin.guaranteed-skin: true[/FONT], on by default) ensures every bot always gets a real skin, even if its name isn't a Mojang account. When the primary lookup fails, FPP falls back automatically:

[I]Bot name → folder skins → pool skins → skin.fallback-name (pre-fetched at startup)[/I]

Set [FONT=monospace]skin.fallback-name[/FONT] to any valid Minecraft username (default: [FONT=monospace]Notch[/FONT]).

[HR][/HR]

[SIZE=6][B]✦ PlaceholderAPI[/B][/SIZE]

When [URL='https://www.spigotmc.org/resources/placeholderapi.6245/']PlaceholderAPI[/URL] is installed, FPP registers its placeholders automatically — no restart needed.

[B]📚 Full Documentation:[/B] Complete placeholder reference with usage examples, troubleshooting, and integration guides available on [URL='https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/blob/main/PLACEHOLDERAPI.md']GitHub[/URL].

FPP provides [B]18 placeholders[/B] organized into three categories:

[SIZE=5][B]Server-Wide Placeholders[/B][/SIZE]

[TABLE="width: 100%"]
[TR]
[TD][B]Placeholder[/B][/TD]
[TD][B]Value[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_count%[/FONT][/TD]
[TD]Number of currently active bots[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_max%[/FONT][/TD]
[TD]Global max-bots limit (or ∞)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_real%[/FONT][/TD]
[TD]Number of real (non-bot) players online[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_total%[/FONT][/TD]
[TD]Total players (real + bots)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_online%[/FONT][/TD]
[TD]Alias for %fpp_total%[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_frozen%[/FONT][/TD]
[TD]Number of currently frozen bots[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_chat%[/FONT][/TD]
[TD]on / off — fake-chat state[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_swap%[/FONT][/TD]
[TD]on / off — bot-swap state[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_skin%[/FONT][/TD]
[TD]Current skin mode (auto / custom / off)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_body%[/FONT][/TD]
[TD]on / off — body-spawn state[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_pushable%[/FONT][/TD]
[TD]on / off — body pushable state[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_damageable%[/FONT][/TD]
[TD]on / off — body damageable state[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_tab%[/FONT][/TD]
[TD]on / off — tab-list visibility[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_max_health%[/FONT][/TD]
[TD]Bot max health value[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_names%[/FONT][/TD]
[TD]Comma-separated list of all bot display names[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_version%[/FONT][/TD]
[TD]Plugin version string[/TD]
[/TR]
[/TABLE]

[SIZE=5][B]Per-World Placeholders[/B][/SIZE]

[TABLE="width: 100%"]
[TR]
[TD][B]Placeholder[/B][/TD]
[TD][B]Value[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_count_<world>%[/FONT][/TD]
[TD]Bots in specific world (e.g., %fpp_count_world_nether%)[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_real_<world>%[/FONT][/TD]
[TD]Real players in specific world[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_total_<world>%[/FONT][/TD]
[TD]Total (real + bots) in specific world[/TD]
[/TR]
[/TABLE]

World names are case-insensitive. Use underscores for worlds with spaces.

[SIZE=5][B]Player-Relative Placeholders[/B][/SIZE]

[TABLE="width: 100%"]
[TR]
[TD][B]Placeholder[/B][/TD]
[TD][B]Value[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_user_count%[/FONT][/TD]
[TD]Number of bots owned by the player[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_user_max%[/FONT][/TD]
[TD]Bot limit for the player[/TD]
[/TR]
[TR]
[TD][FONT=monospace]%fpp_user_names%[/FONT][/TD]
[TD]Comma-separated names of player's bots[/TD]
[/TR]
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
&7Nether: &c%fpp_total_world_nether%
&7End: &d%fpp_total_world_the_end%
[/CODE]

[I]Full documentation available on [/I][URL='https://github.com/Pepe-tf/Fake-Player-Plugin-Public-/blob/main/PLACEHOLDERAPI.md'][I]GitHub[/I][/URL].

[HR][/HR]

[SIZE=6][B]✦ Bot Names & Chat[/B][/SIZE]

[TABLE="width: 100%"]
[TR]
[TD][B]File[/B][/TD]
[TD][B]Purpose[/B][/TD]
[/TR]
[TR]
[TD][FONT=monospace]bot-names.yml[/FONT][/TD]
[TD]Random name pool. 1–16 chars, letters/digits/underscores. /fpp reload to update.[/TD]
[/TR]
[TR]
[TD][FONT=monospace]bot-messages.yml[/FONT][/TD]
[TD]Random chat messages. Supports {name} and {random_player} placeholders.[/TD]
[/TR]
[/TABLE]

When the name pool runs out, FPP generates names automatically ([FONT=monospace]Bot1234[/FONT], etc.).

[HR][/HR]

[SIZE=6][B]✦ LuckPerms Integration[/B][/SIZE]

When LuckPerms is installed and [FONT=monospace]luckperms.use-prefix: true[/FONT]:

[LIST]
[*] The [B]default group prefix[/B] is automatically prepended to every bot's display name in the tab list, nametag, and messages
[*] Makes bots blend naturally with real players
[*] Disable any time with [FONT=monospace]luckperms.use-prefix: false[/FONT]
[/LIST]

[HR][/HR]

[SIZE=6][B]✦ Changelog[/B][/SIZE]

[SIZE=5][B]v1.4.28[/B][/SIZE] [I](2026-03-26)[/I]
[LIST]
[*] [B]Skin diversity fix[/B] — guaranteed-skin fallback pool now uses on-demand random selection when bots spawn before async prewarm completes, ensuring every bot gets a unique skin even during rapid spawning at server startup (no more "Notch clone armies")
[*] [B]Vanilla skin pool[/B] — default fallback-pool updated to use 27 official Minecraft system accounts (Mojang developers + MHF_* mob/block skins) instead of content creator accounts for a pure vanilla aesthetic
[*] [B]Per-world placeholders[/B] — added dynamic world-specific bot counts: %fpp_count_<world>%, %fpp_real_<world>%, %fpp_total_<world>% (case-insensitive world names)
[*] [B]PlaceholderAPI expansion[/B] — added %fpp_online% as cleaner alias for %fpp_total% (real players + bots combined)
[*] [B]Fake chat prefix/suffix support[/B] — fake-chat.chat-format now supports {prefix} and {suffix} placeholders for full LuckPerms integration
[*] [B]Spawn race condition fixed[/B] — /fpp despawn all while bots are spawning no longer leaves ghost entries in tab-list/scoreboard
[*] [B]Portal/teleport bug fixed[/B] — bots pushed through portals or cross-world teleports are now protected with PDC-based entity recovery
[*] [B]Body damageable toggle fixed[/B] — body.damageable: false now correctly prevents all damage via event-level cancellation
[*] [B]Body pushable/damageable live reload[/B] — /fpp reload now immediately applies body config changes to all active Mannequins
[*] [B]Enhanced documentation[/B] — added PLACEHOLDERAPI.md reference, updated Skin-System.md with guaranteed-skin details
[/LIST]

[SIZE=5][B]v1.4.27[/B][/SIZE] [I](2026-03-25)[/I]
[LIST]
[*] [B]Unified spawn syntax[/B] — in-game /fpp spawn now supports the same flexible positional syntax as console
[*] [B]Improved /fpp reload output[/B] — box-drawing lines, per-step detail, yellow warning line for config issues
[*] [B]/fpp reload canUse fix[/B] — now correctly delegates to Perm.hasOrOp so operators can reload
[/LIST]

[SIZE=5][B]v1.4.26[/B][/SIZE] [I](2026-03-25)[/I]
[LIST]
[*] [B]Tab-list weight ordering overhauled[/B] — bots now perfectly respect LuckPerms group weights in tab list
[*] [B]New rank command system[/B] — /fpp rank set and /fpp rank random for LuckPerms group assignment
[*] [B]Restoration bug fixed[/B] — bots restored after restart now maintain correct weights and ranks
[*] [B]Auto-update on group change[/B] — bot prefixes and tab ordering update in real-time when LP data changes
[/LIST]

[SPOILER="Previous Versions"]
[SIZE=5][B]v1.4.24[/B][/SIZE] [I](2026-03-24)[/I]
[LIST]
[*] YAML file syncer — missing keys from plugin updates are automatically merged
[*] /fpp migrate lang|names|messages — manually force-sync YAML files
[*] /fpp migrate status now shows file-sync health panel
[/LIST]

[SIZE=5][B]v1.4.23[/B][/SIZE] [I](2026-03-23)[/I]
[LIST]
[*] Fixed bot name colours being lost after server restart
[*] Fixed join/leave delays being 20× longer than configured
[*] /fpp reload now immediately refreshes bot prefixes from LuckPerms
[*] Added /fpp delete random [amount]
[/LIST]

[SIZE=5][B]v1.4.22[/B][/SIZE] [I](2026-03-22)[/I]
[LIST]
[*] Tab-list bot visibility control (tab-list.enabled)
[*] Multi-platform download links in update notifications
[*] Enhanced reload command with step-by-step progress
[*] Update checker improvements using Modrinth API
[*] Multiple bug fixes (tab-list migration, StackOverflowError, NullPointerException, LP gradient support)
[/LIST]

[SIZE=5][B]v1.2.7[/B][/SIZE] [I](2026-03-14)[/I]
[LIST]
[*] /fpp freeze command
[*] /fpp stats live statistics panel
[*] PlaceholderAPI expansion
[*] spawn-cooldown config
[*] Animated tab-list header/footer
[*] FastStats metrics toggle
[/LIST]

[SIZE=5][B]v1.2.2[/B][/SIZE] [I](2026-03-14)[/I]
[LIST]
[*] Guaranteed Skin system
[*] skin.fallback-name config
[*] Rate-limit fix for Mojang API
[*] Config auto-migration
[/LIST]
[/SPOILER]

[HR][/HR]

[SIZE=6][B]✦ Support the Project[/B][/SIZE]

If you enjoy FPP and want to help keep it going, consider buying me a coffee:

[CENTER][URL='https://ko-fi.com/fakeplayerplugin'][B][COLOR=#FF5E5B]☕ Support on Ko-fi[/COLOR][/B][/URL][/CENTER]

Donations are completely optional and don't come with any guaranteed rewards — though I may add small perks like beta access in the future. Every contribution goes directly toward improving the plugin.

Thank you all for using Fake Player Plugin. Without you, it wouldn't be where it is today ❤️

[HR][/HR]

[SIZE=6][B]✦ Links[/B][/SIZE]

[LIST]
[*] [URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)']Modrinth[/URL] — download
[*] [URL='https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/']Spigotmc[/URL] — download
[*] [URL='https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin']PaperMC Hangar[/URL] — download
[*] [URL='https://builtbybit.com/resources/fake-player-plugin.98704/']BuiltByBit[/URL] — download
[*] [URL='https://fakeplayerplugin.xyz']Wiki[/URL] — documentation
[*] [URL='https://ko-fi.com/fakeplayerplugin']Ko-fi[/URL] — support the project
[*] [URL='https://discord.gg/QSN7f67nkJ']Discord[/URL] — support & feedback
[*] [URL='https://github.com/Pepe-tf/Fake-Player-Plugin-Public-']GitHub[/URL] — source & issues
[/LIST]

[HR][/HR]

[CENTER][I]Built for Paper 1.21.x · Java 21 · FPP v1.4.28[/I]

[URL='https://modrinth.com/plugin/fake-player-plugin-(fpp)']Modrinth[/URL]  [URL='https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/']SpigotMC[/URL]  [URL='https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin']PaperMC[/URL]  [URL='https://builtbybit.com/resources/fake-player-plugin.98704/']BuiltByBit[/URL]  [URL='https://fakeplayerplugin.xyz']Wiki[/URL]


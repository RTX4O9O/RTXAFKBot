package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code /fpp info [bot|spawner] <name>} - queries the database and
 * displays bot session history or current bot details.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code /fpp info}              - overall stats</li>
 *   <li>{@code /fpp info <botname>}    - session history for a bot</li>
 *   <li>{@code /fpp info bot <name>}   - same</li>
 *   <li>{@code /fpp info spawner <n>}  - all bots spawned by a player</li>
 * </ul>
 */
public class InfoCommand implements FppCommand {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor LABEL  = NamedTextColor.GRAY;
    private static final TextColor VALUE  = NamedTextColor.WHITE;
    private static final TextColor MUTED  = NamedTextColor.DARK_GRAY;
    private static final TextColor OK     = NamedTextColor.GREEN;
    private static final TextColor ERR    = NamedTextColor.RED;

    private final DatabaseManager   db;
    private final FakePlayerManager manager;

    public InfoCommand(DatabaseManager db, FakePlayerManager manager) {
        this.db      = db;
        this.manager = manager;
    }

    @Override public String getName()        { return "info"; }
    @Override public String getUsage()       { return "[bot|spawner] <name>"; }
    @Override public String getDescription() { return "Query bot session history from the database."; }
    /** Primary admin permission — canUse() also allows fpp.info.user (dual-tier). */
    @Override public String getPermission()  { return Perm.INFO; }

    /** Dual-tier: visible to admins (fpp.info) AND user-tier (fpp.user.info). */
    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.INFO) || Perm.has(sender, Perm.USER_INFO);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean isAdmin = Perm.has(sender, Perm.INFO);
        boolean isUser  = Perm.has(sender, Perm.USER_INFO);

        if (!isAdmin && !isUser) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        // ── User tier: /fpp info [botname] → own bots only, limited view ──────
        if (!isAdmin) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(Lang.get("player-only"));
                return true;
            }
            if (args.length == 0) {
                // Show the user's own active bots
                showUserOwnBots(sender, player);
                return true;
            }
            // User provided a bot name - show limited info only if they own it
            showUserBotInfo(sender, player, args[0]);
            return true;
        }

        // ── Admin tier ────────────────────────────────────────────────────────
        if (args.length == 0) {
            // Always show live bots first, then DB stats if available
            showAdminLiveBots(sender);
            return true;
        }

        String sub  = args[0].toLowerCase();
        String name = args.length > 1 ? args[1] : args[0];

        // Try to match a live bot by internal name first, then plain display name
        FakePlayer live = manager.getActivePlayers().stream()
                .filter(fp -> fp.getName().equalsIgnoreCase(args[0]))
                .findFirst().orElse(null);

        if (live != null && !sub.equals("bot") && !sub.equals("spawner")) {
            showAdminBotInfo(sender, live);
            return true;
        }

        if (db == null) {
            sender.sendMessage(Lang.get("info-db-unavailable"));
            return true;
        }

        switch (sub) {
            case "bot"     -> showBotSessions(sender, args.length > 1 ? name : args[0]);
            case "spawner" -> showSpawnerSessions(sender, name);
            default        -> showBotSessions(sender, args[0]);
        }
        return true;
    }


    // ── User-tier displays ────────────────────────────────────────────────────

    private void showUserOwnBots(CommandSender sender, org.bukkit.entity.Player player) {
        List<FakePlayer> owned = manager.getBotsOwnedBy(player.getUniqueId());

        sender.sendMessage(header("ʏᴏᴜʀ ʙᴏᴛꜱ"));
        if (owned.isEmpty()) {
            sender.sendMessage(Lang.get("list-none"));
            sender.sendMessage(divider());
            return;
        }
        for (FakePlayer fp : owned) {
            sender.sendMessage(Component.empty()
                    .append(Component.text("  ").color(MUTED))
                    .append(Component.text(fp.getDisplayName()).color(ACCENT))
                    .append(Component.text("  ⏱ ").color(MUTED))
                    .append(Component.text(formatUptime(fp.getSpawnTime())).color(VALUE))
                    .append(Component.text("  📍 ").color(MUTED))
                    .append(Component.text(manager.formatLocationForDisplay(fp)).color(VALUE)));
        }
        sender.sendMessage(divider());
    }

    private void showUserBotInfo(CommandSender sender, org.bukkit.entity.Player player, String input) {
        // Match by display name OR internal name
        FakePlayer fp = manager.getActivePlayers().stream()
                .filter(b -> b.getName().equalsIgnoreCase(input))
                .findFirst().orElse(null);

        if (fp == null) {
            sender.sendMessage(Lang.get("info-no-records", "name", input));
            return;
        }

        // Only show info if the player owns it
        if (!player.getUniqueId().equals(fp.getSpawnedByUuid())) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }

        sender.sendMessage(header("ʙᴏᴛ - " + fp.getDisplayName()));
        row(sender, "ᴡᴏʀʟᴅ",    getWorld(fp));
        row(sender, "ʟᴏᴄᴀᴛɪᴏɴ",  manager.formatLocationForDisplay(fp));
        row(sender, "ᴜᴘᴛɪᴍᴇ",    formatUptime(fp.getSpawnTime()));
        row(sender, "ꜱᴘᴀᴡɴᴇᴅ ʙʏ", fp.getSpawnedBy());
        sender.sendMessage(divider());
    }

    // ── Admin-tier displays ───────────────────────────────────────────────────

    /** Shows all currently active bots with live coordinates - no DB needed. */
    private void showAdminLiveBots(CommandSender sender) {
        java.util.Collection<FakePlayer> active = manager.getActivePlayers();

        sender.sendMessage(header("ᴀᴄᴛɪᴠᴇ ʙᴏᴛꜱ (" + active.size() + ")"));

        if (active.isEmpty()) {
            sender.sendMessage(Component.empty()
                    .append(Component.text("  No bots are currently active.").color(MUTED)));
        } else {
            for (FakePlayer fp : active) {
                    sender.sendMessage(Component.empty()
                        .append(Component.text("  ").color(MUTED))
                        .append(Component.text(fp.getDisplayName()).color(ACCENT))
                        .append(Component.text("  ⏱ ").color(MUTED))
                        .append(Component.text(formatUptime(fp.getSpawnTime())).color(VALUE))
                        .append(Component.text("  📍 ").color(MUTED))
                                .append(Component.text(manager.formatLocationForDisplay(fp)).color(VALUE))
                        .append(Component.text("  by ").color(MUTED))
                        .append(Component.text(fp.getSpawnedBy()).color(LABEL)));
            }
        }

        // DB stats block
        if (db != null) {
            me.bill.fakePlayerPlugin.database.DatabaseManager.DbStats stats = db.getStats();
            sender.sendMessage(divider());
            sender.sendMessage(header("ᴅᴀᴛᴀʙᴀꜱᴇ ꜱᴛᴀᴛꜱ (" + stats.backend() + ")"));
            row(sender, "ᴍᴏᴅᴇ",            me.bill.fakePlayerPlugin.config.Config.databaseMode());
            row(sender, "ꜱᴇʀᴠᴇʀ ɪᴅ",       me.bill.fakePlayerPlugin.config.Config.serverId());
            row(sender, "ᴛᴏᴛᴀʟ ꜱᴇꜱꜱɪᴏɴꜱ",  String.valueOf(stats.totalSessions()));
            row(sender, "ᴜɴɪQᴜᴇ ʙᴏᴛꜱ",     String.valueOf(stats.uniqueBots()));
            row(sender, "ᴜɴɪQᴜᴇ ꜱᴘᴀᴡɴᴇʀꜱ", String.valueOf(stats.uniqueSpawners()));
            row(sender, "ᴛᴏᴛᴀʟ ᴜᴘᴛɪᴍᴇ",    stats.formattedUptime());

            java.util.Map<String, Integer> top = db.getTopSpawners(3);
            if (!top.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                top.forEach((p, c) -> { if (sb.length() > 0) sb.append(", "); sb.append(p).append(" (").append(c).append(")"); });
                row(sender, "ᴛᴏᴘ ꜱᴘᴀᴡɴᴇʀꜱ", sb.toString());
            }

            sender.sendMessage(Component.empty()
                    .append(Component.text("  Use /fpp info <name> or /fpp info spawner <name> for history.").color(MUTED)));
        }
        sender.sendMessage(divider());
    }

    /** Full admin view of a single live bot. */
    private void showAdminBotInfo(CommandSender sender, FakePlayer fp) {
        sender.sendMessage(header("ʙᴏᴛ - " + fp.getDisplayName()));
        row(sender, "ɴᴀᴍᴇ",      fp.getDisplayName());
        row(sender, "ɪɴᴛᴇʀɴᴀʟ",  fp.getName());
        row(sender, "ᴜᴜɪᴅ",       fp.getUuid().toString());
        row(sender, "ᴡᴏʀʟᴅ",     getWorld(fp));
        row(sender, "ʟᴏᴄᴀᴛɪᴏɴ",  formatLoc(fp));
        row(sender, "ᴜᴘᴛɪᴍᴇ",    formatUptime(fp.getSpawnTime()));
        row(sender, "ꜱᴘᴀᴡɴᴇᴅ ʙʏ", fp.getSpawnedBy());
        sender.sendMessage(divider());
    }

    private void showBotSessions(CommandSender sender, String botName) {
        // Show live info first if bot is currently active (match by display or internal name)
        FakePlayer live = manager.getActivePlayers().stream()
                .filter(fp -> fp.getName().equalsIgnoreCase(botName))
                .findFirst().orElse(null);

        if (live != null) {
            showAdminBotInfo(sender, live);
        }

        // DB history - use internal name for the DB query
        String internalName = live != null ? live.getName() : botName;
        int limit = me.bill.fakePlayerPlugin.config.Config.dbMaxHistoryRows();
        List<BotRecord> records = db.getSessionsByBot(internalName, limit);
        if (records.isEmpty() && live == null) {
            sender.sendMessage(Lang.get("info-no-records", "name", botName));
            return;
        }
        if (records.isEmpty()) return;

        sender.sendMessage(header("ꜱᴇꜱꜱɪᴏɴ ʜɪꜱᴛᴏʀʏ - " + botName + " (last " + records.size() + ")"));
        for (BotRecord r : records) {
            sender.sendMessage(sessionRow(r));
        }
        sender.sendMessage(divider());
    }

    private void showSpawnerSessions(CommandSender sender, String playerName) {
        int limit = me.bill.fakePlayerPlugin.config.Config.dbMaxHistoryRows();
        List<BotRecord> records = db.getSessionsBySpawner(playerName, limit);
        if (records.isEmpty()) {
            sender.sendMessage(Lang.get("info-no-records", "name", playerName));
            return;
        }
        sender.sendMessage(header("ꜱᴘᴀᴡɴᴇʀ ʜɪꜱᴛᴏʀʏ - " + playerName + " (last " + records.size() + ")"));
        for (BotRecord r : records) {
            sender.sendMessage(sessionRow(r));
        }
        sender.sendMessage(divider());
    }

    // ── Component builders ────────────────────────────────────────────────────

    private Component header(String title) {
        return TextUtil.colorize(
                "<dark_gray><st>━━━</st> <#0079FF>" + title + "</#0079FF> <dark_gray><st>━━━</st>");
    }

    private Component divider() {
        return TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>");
    }

    private void row(CommandSender s, String label, String value) {
        s.sendMessage(Component.empty()
                .append(Component.text("  " + label + ": ").color(LABEL))
                .append(Component.text(value).color(VALUE)));
    }

    private Component sessionRow(BotRecord r) {
        TextColor statusColor = r.isActive() ? OK : ERR;
        String status  = r.isActive() ? "ᴀᴄᴛɪᴠᴇ" : (r.getRemoveReason() != null ? r.getRemoveReason() : "ʀᴇᴍᴏᴠᴇᴅ");
        String spawned = FMT.format(r.getSpawnedAt());
        String loc     = r.getWorldName() + " " + fmt(r.getSpawnX()) + "," + fmt(r.getSpawnY()) + "," + fmt(r.getSpawnZ());

        return Component.empty()
                .append(Component.text("  #" + r.getId() + " ").color(MUTED))
                .append(Component.text(r.getBotName() != null ? r.getBotName() : "?").color(ACCENT))
                .append(Component.text(" - ").color(MUTED))
                .append(Component.text(status).color(statusColor))
                .append(Component.text("  " + spawned + "  " + loc).color(LABEL));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String fmt(double v)        { return String.format("%.0f", v); }

    private static String getWorld(FakePlayer fp) {
        var body = fp.getPhysicsEntity();
        if (body != null && body.isValid() && body.getLocation().getWorld() != null)
            return body.getLocation().getWorld().getName();
        var sl = fp.getSpawnLocation();
        if (sl != null && sl.getWorld() != null) return sl.getWorld().getName();
        return "unknown";
    }

    private static String formatUptime(Instant t) {
        if (t == null) return "?";
        long s = Duration.between(t, Instant.now()).getSeconds();
        if (s < 60)   return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }

    private static String formatLoc(FakePlayer fp) {
        var body = fp.getPhysicsEntity();
        if (body != null && body.isValid()) {
            var l = body.getLocation();
            return (l.getWorld() != null ? l.getWorld().getName() : "?")
                    + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
        }
        var sl = fp.getSpawnLocation();
        if (sl != null) return (sl.getWorld() != null ? sl.getWorld().getName() : "?")
                + " " + sl.getBlockX() + "," + sl.getBlockY() + "," + sl.getBlockZ();
        return "unknown";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        boolean isAdmin = Perm.has(sender, Perm.INFO);
        boolean isUser  = Perm.has(sender, Perm.USER_INFO);
        if (!isAdmin && !isUser) return List.of();

        String current = args.length > 0 ? args[0] : "";
        String lower   = current.toLowerCase();

        if (args.length <= 1) {
            List<String> suggestions = new java.util.ArrayList<>();

            if (isAdmin) {
                // Admin: offer sub-commands + all active bot internal names
                if ("bot".startsWith(lower))     suggestions.add("bot");
                if ("spawner".startsWith(lower)) suggestions.add("spawner");
                manager.getActivePlayers().stream()
                        .map(FakePlayer::getName)
                        .filter(n -> n.toLowerCase().startsWith(lower))
                        .forEach(suggestions::add);
            } else if (sender instanceof org.bukkit.entity.Player player) {
                // User-tier: only own bots by internal name
                manager.getBotsOwnedBy(player.getUniqueId()).stream()
                        .map(FakePlayer::getName)
                        .filter(n -> n.toLowerCase().startsWith(lower))
                        .forEach(suggestions::add);
            }
            return suggestions;
        }

        // arg[0] is "bot" or "spawner" - arg[1] is the name to complete
        if (args.length == 2 && isAdmin) {
            String sub = args[0].toLowerCase();
            String name = args[1].toLowerCase();
            if (sub.equals("bot")) {
                return manager.getActivePlayers().stream()
                        .map(FakePlayer::getName)
                        .filter(n -> n.toLowerCase().startsWith(name))
                        .toList();
            }
            // "spawner" → no live completion (would need DB query)
        }
        return List.of();
    }
}





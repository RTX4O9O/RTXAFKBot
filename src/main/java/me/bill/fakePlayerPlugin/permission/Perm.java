package me.bill.fakePlayerPlugin.permission;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Central permission registry for Fake Player Plugin.
 *
 * <p>Every permission node used by the plugin is declared as a constant here.
 * Use {@link #has(CommandSender, String)} for the standard check and
 * {@link #hasOrOp(CommandSender, String)} when OP should always pass regardless
 * of LuckPerms / group configuration.
 *
 * <h3>LuckPerms compatibility</h3>
 * Paper's {@link CommandSender#hasPermission(String)} delegates to whichever
 * permission plugin is installed (LuckPerms, PermissionsEx, GroupManager, etc.)
 * automatically. No LuckPerms API dependency is needed here; LuckPerms hooks
 * into Bukkit's permission layer at startup. All you need to do is assign the
 * nodes listed below to groups or players via LuckPerms commands - they will
 * take effect immediately.
 *
 * <h3>Inheritance</h3>
 * The wildcard {@code fpp.*} is declared as a Bukkit permission in
 * {@code plugin.yml} with all leaf nodes as children. LuckPerms also respects
 * wildcard inheritance natively:
 * <pre>
 *   /lp group admin permission set fpp.* true
 * </pre>
 */
@SuppressWarnings("unused") // All constants are public API - used by commands via getPermission()
public final class Perm {

    private Perm() {}

    // ── Main command visibility ───────────────────────────────────────────────

    /**
     * Controls whether the {@code /fpp} command is visible and usable at all.
     * Default: {@code true} (all players). Negate via LuckPerms to completely
     * hide {@code /fpp} from a player or group:
     * <pre>/lp group guest permission set fpp.command false</pre>
     * When negated the command produces no output and disappears from tab-complete.
     */
    public static final String COMMAND = "fpp.command";

    /**
     * Controls whether the full plugin info panel is shown when typing bare {@code /fpp}.
     * Default: {@code op}. Players without this permission see only the
     * {@code /fpp help} hint instead of the full panel (version, authors, download links, etc.).
     * Grant to staff who need version/status visibility without full admin access.
     */
    public static final String PLUGIN_INFO = "fpp.plugininfo";

    // ── Admin wildcard ────────────────────────────────────────────────────────

    /**
     * Grants access to every FPP command (admin + user).
     * Replaces the old fpp.* wildcard. Children declared in plugin.yml.
     */
    public static final String OP = "fpp.op";

    // ── User wildcard ─────────────────────────────────────────────────────────

    /**
     * Grants all user-facing FPP commands (spawn, tph, xp, info-self).
     * Does NOT include admin commands (delete, reload, chat, list, info-full).
     * Replaces the old fpp.user.* wildcard. Children declared in plugin.yml.
     */
    public static final String USE = "fpp.use";

    // ── User commands ─────────────────────────────────────────────────────────

    /**
     * User-tier spawn - spawn bots up to the player's personal bot limit.
     * The limit is resolved from {@code fpp.spawn.limit.<num>} nodes (highest wins).
     * Falls back to config {@code fake-player.user-bot-limit} when no limit node is present.
     */
    public static final String USER_SPAWN = "fpp.spawn.user";

    /**
     * Teleport the user's own bot(s) to themselves.
     * {@code /fpp tph [botname]} - user-tier, included in fpp.use.
     */
    public static final String USER_TPH = "fpp.tph";

    /**
     * Teleport the user to one of their own bots.
     * {@code /fpp tp [botname]} - admin-tier by default.
     */
    public static final String TP = "fpp.tp";

    /**
     * Collect XP from a bot.
     * {@code /fpp xp <botname>} - user-tier by default, included in fpp.use.
     */
    public static final String USER_XP = "fpp.xp";

    /**
     * View limited bot info for bots the user owns.
     * {@code /fpp info <botname>} - shows location, world, uptime only.
     * Included in fpp.use.
     */
    public static final String USER_INFO = "fpp.info.user";

    // ── Bot limit nodes ───────────────────────────────────────────────────────

    /**
     * Per-user bot spawn limit - resolved by scanning {@code fpp.spawn.limit.<num>}
     * from 1 to 100. The highest matching node wins.
     *
     * <p>Examples:
     * <pre>
     *   fpp.spawn.limit.1   → player may have at most 1 bot active
     *   fpp.spawn.limit.5   → player may have at most 5 bots active
     *   fpp.spawn.limit.20  → player may have at most 20 bots active
     * </pre>
     *
     * If the player has no {@code fpp.spawn.limit.*} node, the global
     * {@code fake-player.user-bot-limit} config value is used.
     * If the player also has {@link #BYPASS_MAX} the global max is ignored.
     */
    public static final String BOT_LIMIT_PREFIX = "fpp.spawn.limit.";

    // ── Admin commands ────────────────────────────────────────────────────────

    /** View the /fpp help menu. Default: everyone. */
    public static final String HELP       = "fpp.help";

    /** Run /fpp spawn [amount] [--name <name>]. Admin-tier spawn. Default: op. */
    public static final String SPAWN      = "fpp.spawn";

    /** Spawn more than one bot at a time. Inherited from fpp.spawn. */
    public static final String SPAWN_MULTIPLE    = "fpp.spawn.multiple";

    /** Use the --name flag for custom bot names. Inherited from fpp.spawn. */
    public static final String SPAWN_CUSTOM_NAME = "fpp.spawn.name";

    /** Run /fpp delete <name|all>. Default: op. */
    public static final String DELETE     = "fpp.delete";

    /**
     * Delete all bots at once via /fpp delete all.
     * Falls back to {@link #DELETE} when this node is not explicitly set.
     */
    public static final String DELETE_ALL = "fpp.delete.all";

    /** View the /fpp list active-bots output. Default: op. */
    public static final String LIST       = "fpp.list";

    /** Toggle /fpp chat on|off. Default: op. */
    public static final String CHAT       = "fpp.chat";

    /** Run /fpp reload. Default: op. */
    public static final String RELOAD     = "fpp.reload";

    /** Full admin query of /fpp info. Default: op. */
    public static final String INFO       = "fpp.info";

    /** Freeze or unfreeze bots with /fpp freeze <bot|all> [on|off]. */
    public static final String FREEZE        = "fpp.freeze";

    /** View the /fpp stats panel with live and database statistics. */
    public static final String STATS         = "fpp.stats";

    /** Access {@code /fpp migrate} - the config and database migration system. */
    public static final String MIGRATE = "fpp.migrate";

    /** View LuckPerms diagnostic info for a bot via /fpp lpinfo <bot>. */
    public static final String LP_INFO       = "fpp.lpinfo";

    /** Assign LuckPerms groups to bots via /fpp rank. */
    public static final String RANK          = "fpp.rank";

    /** Broadcast network-wide alerts via /fpp alert. */
    public static final String ALERT         = "fpp.alert";

    /** Sync configs across network via /fpp sync. */
    public static final String SYNC          = "fpp.sync";

    /** Control bot session rotation (swap in/out) via /fpp swap. */
    public static final String SWAP          = "fpp.swap";

    /** Control peak-hours bot pool scheduling via /fpp peaks. */
    public static final String PEAKS         = "fpp.peaks";

    /** Open the interactive in-game settings GUI via /fpp settings. */
    public static final String SETTINGS      = "fpp.settings";

    /** Control bot movement (WASD) via /fpp move. */
    public static final String MOVE          = "fpp.move";

    /** Open and edit a bot's inventory via /fpp inventory <bot>. */
    public static final String INVENTORY     = "fpp.inventory";

    /** Execute a server command as a bot via /fpp cmd <bot> <command...>. */
    public static final String CMD           = "fpp.cmd";

    /** Make a bot mine blocks in front of them via /fpp mine <bot>. */
    public static final String MINE          = "fpp.mine";

    /** Register and use named bot storage targets via /fpp storage <bot> [storage_name]. */
    public static final String STORAGE       = "fpp.storage";

    /** Fill a selected cuboid area with blocks via /fpp place <bot> [--pos1|--pos2|--block <spec>|start|stop]. */
    public static final String PLACE         = "fpp.place";

    /**
     * Make a bot right-click whatever it's looking at via /fpp use {@literal <bot>}.
     * Named {@code USE_CMD} (not {@code USE}) to avoid collision with the user-wildcard
     * {@link #USE} = {@code fpp.use}.
     */
    public static final String USE_CMD       = "fpp.useitem";

    /** Create, remove, and list named waypoint patrol routes via /fpp waypoint (alias: /fpp wp). */
    public static final String WAYPOINT      = "fpp.waypoint";

    /**
     * Manage the badword filter and scan/fix active bot names via /fpp badword.
     * Subcommands: check (list flagged bots), update (rename flagged bots), status (filter info).
     */
    public static final String BADWORD       = "fpp.badword";

    /**
     * Manage AI personalities for bots via /fpp personality.
     * Includes listing, assigning, resetting, and reloading personality files.
     * Default: op.
     */
    public static final String PERSONALITY = "fpp.personality";

    /**
     * Rename any active bot via /fpp rename &lt;oldname&gt; &lt;newname&gt;.
     * Admin tier — can rename ALL bots regardless of who spawned them. Default: op.
     */
    public static final String RENAME        = "fpp.rename";

    /**
     * Rename only bots the sender personally spawned via /fpp rename.
     * User opt-in — without this the sender must also have {@link #RENAME}.
     * Default: false.
     */
    public static final String RENAME_OWN    = "fpp.rename.own";

    /**
     * Receive plugin update notifications on join.
     * Automatically granted to OPs via {@link #OP}; grant explicitly to non-OP admins
     * who should also be notified when a newer plugin version is available.
     */
    public static final String NOTIFY = "fpp.notify";

    // ── Admin bypass ─────────────────────────────────────────────────────────

    /** Bypass the max-bots cap defined in config.yml. */
    public static final String BYPASS_MAX = "fpp.bypass.max";

    /** Bypass the spawn cooldown timer. */
    public static final String BYPASS_COOLDOWN = "fpp.bypass.cooldown";

    // ── Static helpers ────────────────────────────────────────────────────────

    /** Returns {@code true} if {@code sender} has the given permission node. */
    public static boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    /**
     * Returns {@code true} if the sender has {@code permission} OR is OP.
     * Equivalent to LuckPerms op-inherited mode.
     */
    public static boolean hasOrOp(CommandSender sender, String permission) {
        if (sender instanceof Player p && p.isOp()) return true;
        return sender.hasPermission(permission);
    }

    /** Returns {@code true} if the sender lacks {@code permission}. */
    public static boolean missing(CommandSender sender, String permission) {
        return !has(sender, permission);
    }

    /** Returns {@code true} if {@code sender} has any of the given nodes. */
    public static boolean hasAny(CommandSender sender, String... permissions) {
        for (String perm : permissions) {
            if (sender.hasPermission(perm)) return true;
        }
        return false;
    }

    /**
     * Resolves the per-user bot limit for {@code sender} by scanning
     * {@code fpp.spawn.limit.1} through {@code fpp.spawn.limit.100} and returning the
     * highest matching number. Returns {@code -1} if no node is set
     * (caller should fall back to global config limit).
     */
    public static int resolveUserBotLimit(CommandSender sender) {
        int best = -1;
        for (int i = 1; i <= 100; i++) {
            if (sender.hasPermission(BOT_LIMIT_PREFIX + i)) {
                best = i;
            }
        }
        return best;
    }
}


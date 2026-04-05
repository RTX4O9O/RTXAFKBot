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
 * nodes listed below to groups or players via LuckPerms commands — they will
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
@SuppressWarnings("unused") // All constants are public API — used by commands via getPermission()
public final class Perm {

    private Perm() {}

    // ── Admin wildcard ────────────────────────────────────────────────────────

    /** Grants access to every FPP command. Children declared in plugin.yml. */
    public static final String ALL        = "fpp.*";

    // ── User wildcard ─────────────────────────────────────────────────────────

    /**
     * Grants all user-facing FPP commands (spawn, tph, info-self).
     * Does NOT include admin commands (delete, reload, chat, list, info-full).
     * Children declared in plugin.yml.
     */
    public static final String USER_ALL   = "fpp.user.*";

    // ── User commands ─────────────────────────────────────────────────────────

    /**
     * User-tier spawn — spawn bots up to the player's personal bot limit.
     * The limit is resolved from {@code fpp.bot.<num>} nodes (highest wins).
     * Falls back to config {@code fake-player.user-bot-limit} when no
     * {@code fpp.bot.*} node is present.
     */
    public static final String USER_SPAWN = "fpp.user.spawn";

    /**
     * Teleport the user's own bot(s) to themselves.
     * {@code /fpp tph [botname]} — user-tier, included in fpp.user.*.
     */
    public static final String TPH        = "fpp.user.tph";

    /**
     * Teleport the user to one of their own bots.
     * {@code /fpp tp [botname]} — admin-tier by default.
     */
    public static final String TP         = "fpp.tp";

    /**
     * View limited bot info for bots the user owns.
     * {@code /fpp info <botname>} — shows location, world, uptime only.
     * Included in fpp.user.*.
     */
    public static final String INFO_SELF  = "fpp.user.info";

    // ── Bot limit nodes ───────────────────────────────────────────────────────

    /**
     * Per-user bot spawn limit — resolved by scanning {@code fpp.bot.<num>}
     * from 1 to 100. The highest matching node wins.
     *
     * <p>Examples:
     * <pre>
     *   fpp.bot.1   → player may have at most 1 bot active
     *   fpp.bot.5   → player may have at most 5 bots active
     *   fpp.bot.20  → player may have at most 20 bots active
     * </pre>
     *
     * If the player has no {@code fpp.bot.*} node, the global
     * {@code fake-player.user-bot-limit} config value is used.
     * If the player also has {@link #BYPASS_MAX} the global max is ignored.
     */
    public static final String BOT_LIMIT_PREFIX = "fpp.bot.";

    // ── Admin commands ────────────────────────────────────────────────────────

    /** View the /fpp help menu. Default: everyone. */
    public static final String HELP       = "fpp.help";

    /** Run /fpp spawn [amount] [--name <name>]. Default: op. */
    public static final String SPAWN      = "fpp.spawn";

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

    // ── Spawn sub-nodes ───────────────────────────────────────────────────────

    /** Spawn more than one bot at a time. Inherited from fpp.spawn. */
    public static final String SPAWN_MULTIPLE    = "fpp.spawn.multiple";

    /** Use the --name flag for custom bot names. Inherited from fpp.spawn. */
    public static final String SPAWN_CUSTOM_NAME = "fpp.spawn.name";

    // ── Admin bypass ─────────────────────────────────────────────────────────

    /** Bypass the max-bots cap defined in config.yml. */
    public static final String BYPASS_MAX = "fpp.bypass.maxbots";

    // ── Migration / Backup ────────────────────────────────────────────────────

    /** Access {@code /fpp migrate} — the config and database migration system. */
    public static final String ADMIN_MIGRATE = "fpp.admin.migrate";

    /** View the /fpp stats panel with live and database statistics. */
    public static final String STATS         = "fpp.stats";

    /** Freeze or unfreeze bots with /fpp freeze <bot|all> [on|off]. */
    public static final String FREEZE        = "fpp.freeze";

    /** View LuckPerms diagnostic info for a bot via /fpp lpinfo <bot>. */
    public static final String LP_INFO       = "fpp.lpinfo";

    /** Assign LuckPerms groups to bots via /fpp rank. */
    public static final String RANK          = "fpp.rank";

    /** Bypass the spawn cooldown timer. */
    public static final String BYPASS_COOLDOWN = "fpp.bypass.cooldown";

    /** Broadcast network-wide alerts via /fpp alert. */
    public static final String ALERT         = "fpp.alert";

    /** Sync configs across network via /fpp sync. */
    public static final String SYNC          = "fpp.sync";

    /** Control bot session rotation (swap in/out) via /fpp swap. */
    public static final String SWAP          = "fpp.swap";

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
     * {@code fpp.bot.1} through {@code fpp.bot.100} and returning the
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

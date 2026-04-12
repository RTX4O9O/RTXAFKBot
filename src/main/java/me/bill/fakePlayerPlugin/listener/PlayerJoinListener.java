package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotBroadcast;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;

/**
 * Syncs all currently active fake players to any real player who joins,
 * so they see the tab list entries and in-world entities immediately.
 */
public class PlayerJoinListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    // Cached reflection field - CraftPlayer.hasPlayedBefore (set once on first use).
    private static volatile Field hasPlayedBeforeField = null;
    // Cached reflection fields for firstPlayed / lastPlayed (zero by default on fresh players).
    private static volatile Field firstPlayedField = null;
    private static volatile Field lastPlayedField  = null;

    public PlayerJoinListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    /**
     * Earliest possible handler - fires before NORMAL/HIGH/MONITOR plugins (e.g. CMI).
     *
     * <p>Suppresses the join message immediately at LOWEST so that management plugins
     * at NORMAL/HIGH see {@code null} and do not send their own join announcement.
     * The MONITOR handler ({@link #onJoin}) then sets the custom {@code bot-join}
     * message from {@code en.yml} so Paper broadcasts it as the final join message.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinEarly(PlayerJoinEvent event) {
        // Try name lookup first, fall back to UUID for reliability.
        FakePlayer fp = manager.getByName(event.getPlayer().getName());
        if (fp == null) fp = manager.getByUuid(event.getPlayer().getUniqueId());
        if (fp == null) return;

        // Suppress at LOWEST so CMI/Essentials/TAB at NORMAL/HIGH see null.
        // The MONITOR handler replaces this with the custom bot-join message.
        event.joinMessage(null);

        // Only force the Bukkit-layer flag for bots that genuinely have prior data.
        if (event.getPlayer().getFirstPlayed() != 0L) {
            forceHasPlayedBefore(event.getPlayer());
        }
    }

    /**
     * Earliest quit handler - fires before NORMAL/HIGH/MONITOR plugins.
     *
     * <p>Suppresses the vanilla quit message at LOWEST so that management plugins
     * at NORMAL/HIGH see {@code null} and do not send their own leave announcement.
     * The MONITOR handler ({@link #onQuit}) then sets the custom {@code bot-leave}
     * message from {@code en.yml} for permanent despawns and bot deaths.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitEarly(PlayerQuitEvent event) {
        // ── Despawning check MUST come before getCount() guard ────────────────
        if (manager.isDespawning(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
            return;
        }

        if (manager.getCount() == 0) return;

        // Try name lookup first, fall back to UUID.
        FakePlayer fp = manager.getByName(event.getPlayer().getName());
        if (fp == null) fp = manager.getByUuid(event.getPlayer().getUniqueId());
        if (fp == null) return;
        // Suppress quit message at LOWEST for body transitions, respawns, and dead bots.
        if (fp.isRespawning() || manager.isBodyTransitioning(fp.getUuid()) || !fp.isAlive()) {
            event.quitMessage(null);
        }
    }

    /**
     * Forces {@code CraftPlayer.hasPlayedBefore = true} on a bot that is already
     * recognised as a returning player by Paper (i.e., Paper loaded a non-zero
     * {@code firstPlayed}).  This is a safety-net for management plugins that read
     * the Bukkit field rather than the NMS flag.  Safe no-op on any unexpected error.
     */
    private static void forceHasPlayedBefore(Player player) {
        try {
            if (hasPlayedBeforeField == null) {
                hasPlayedBeforeField = findField(player.getClass(), "hasPlayedBefore");
            }
            if (hasPlayedBeforeField != null) {
                hasPlayedBeforeField.setBoolean(player, true);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Sets {@code firstPlayed} and {@code lastPlayed} to sensible non-zero values
     * on the given player <em>if they are currently zero</em>.  Call this 2 ticks
     * after spawning a bot (before {@code saveData()}) so the written {@code .dat}
     * file contains real timestamps.  On the next spawn Paper loads those timestamps,
     * sees {@code firstPlayed != 0}, and sets {@code hasPlayedBefore = true}
     * automatically - without FPP needing to override anything at the event level.
     *
     * <p>Safe no-op on any unexpected reflection error.
     */
    public static void stampFirstPlayed(Player player) {
        try {
            if (firstPlayedField == null) {
                firstPlayedField = findField(player.getClass(), "firstPlayed");
            }
            if (lastPlayedField == null) {
                lastPlayedField = findField(player.getClass(), "lastPlayed");
            }
            long now = System.currentTimeMillis();
            if (firstPlayedField != null) {
                long fp = firstPlayedField.getLong(player);
                if (fp == 0L) firstPlayedField.setLong(player, now - 60_000L);
            }
            if (lastPlayedField != null) {
                long lp = lastPlayedField.getLong(player);
                if (lp == 0L) lastPlayedField.setLong(player, now - 1_000L);
            }
        } catch (Throwable ignored) {}
    }

    /** Walks the class hierarchy to find a declared field by name. */
    private static Field findField(Class<?> clazz, String name) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Try name first, fall back to UUID.
        FakePlayer fp = manager.getByName(event.getPlayer().getName());
        if (fp == null) fp = manager.getByUuid(event.getPlayer().getUniqueId());
        if (fp != null) {
            // Suppress join message during respawns, body transitions, and renames.
            // For normal spawns: replace the vanilla message with the custom bot-join
            // from en.yml so Paper broadcasts it automatically (most reliable approach).
            if (fp.isRespawning() || manager.isBodyTransitioning(fp.getUuid())
                    || manager.isRenaming(fp.getUuid())) {
                event.joinMessage(null);
            } else if (Config.joinMessage()) {
                event.joinMessage(BotBroadcast.joinComponent(fp));
            } else {
                event.joinMessage(null);
            }
            // Skip normal join-sync for bots.
            return;
        }


        // Send any stored update notification to admins/ops who join after startup.
        // Also delivered to non-OP players with fpp.notify (e.g. non-OP server admins).
        try {
            var upd = plugin.getUpdateNotification();
            if (upd != null) {
                var p = event.getPlayer();
                if (me.bill.fakePlayerPlugin.permission.Perm.hasOrOp(p, me.bill.fakePlayerPlugin.permission.Perm.OP)
                        || me.bill.fakePlayerPlugin.permission.Perm.has(p, me.bill.fakePlayerPlugin.permission.Perm.NOTIFY)) {
                    try {
                        p.sendMessage(upd);
                    } catch (NoSuchMethodError | NoClassDefFoundError e) {
                        p.sendMessage(upd.toString());
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Warn operators when the plugin is running on an unsupported MC version.
        // The warning repeats on every join so ops cannot miss it.
        try {
            if (plugin.isVersionUnsupported()
                    && me.bill.fakePlayerPlugin.permission.Perm.hasOrOp(
                            event.getPlayer(), me.bill.fakePlayerPlugin.permission.Perm.OP)) {
                event.getPlayer().sendMessage(
                        me.bill.fakePlayerPlugin.lang.Lang.get(
                                "version-unsupported-admin",
                                "version", plugin.getDetectedMcVersion()));
            }
        } catch (Throwable ignored) {}

        if (manager.getCount() == 0 && plugin.getRemoteBotCache().count() == 0) return;
        // If restoration is in progress, defer syncing until it completes (post-restore prefix refresh).
        // This ensures players see bots with correct ranks from the start, not with stale LP data.
        long delayTicks = manager.isRestorationInProgress() ? 40L : 5L;
        // Small delay so the client is fully ready to receive entity packets
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                manager.syncToPlayer(event.getPlayer());
                // Sync bot tab team to player's scoreboard
                var btt = plugin.getBotTabTeam();
                if (btt != null) btt.syncToPlayer(event.getPlayer());
            } catch (Throwable ignored) {}

            // Add virtual tab-list entries for bots running on other proxy servers.
            // These are purely client-side entries - no entity exists on this server.
            if (me.bill.fakePlayerPlugin.config.Config.isNetworkMode()
                    && me.bill.fakePlayerPlugin.config.Config.tabListEnabled()) {
                try {
                    var cache = plugin.getRemoteBotCache();
                    if (cache != null) {
                        for (var entry : cache.getAll()) {
                            me.bill.fakePlayerPlugin.fakeplayer.PacketHelper.sendTabListAddRaw(
                                    event.getPlayer(),
                                    entry.uuid(),
                                    entry.packetProfileName(),
                                    entry.displayName(),
                                    entry.skinValue(),
                                    entry.skinSignature());
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }, delayTicks);
    }

    /**
     * When a spawner disconnects, validate all their user bots' display names to ensure
     * no unresolved {@code {placeholder}} tokens remain.  This is a safety net for the
     * rare edge case where a bot's display name was not fully resolved at spawn/restore time.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();

        // ── Permanent despawn via /fpp despawn ───────────────────────────────
        // isDespawning() check BEFORE getCount() - removeAll() clears activePlayers first.
        if (manager.isDespawning(uuid)) {
            // Suppress any vanilla/third-party quit message - we broadcast manually.
            event.quitMessage(null);
            // Broadcast the custom bot-leave message directly rather than relying on
            // Paper to re-broadcast event.quitMessage() - newer Paper builds (26.1.1+)
            // do not always honour a non-null quitMessage set inside the MONITOR handler.
            // Skip the leave message during renames — a dedicated rename broadcast is sent instead.
            if (Config.leaveMessage() && !manager.isRenaming(uuid)) {
                String displayName = manager.getDespawningDisplayName(uuid);
                if (displayName != null) {
                    BotBroadcast.broadcastLeaveByDisplayName(displayName);
                }
            }
            return;
        }

        if (manager.getCount() == 0) return;

        // Try name first, fall back to UUID.
        FakePlayer fp = manager.getByName(event.getPlayer().getName());
        if (fp == null) fp = manager.getByUuid(uuid);
        if (fp != null) {
            if (fp.isRespawning() || manager.isBodyTransitioning(fp.getUuid())) {
                // Suppress - bot is not actually leaving.
                event.quitMessage(null);
            } else if (!fp.isAlive()) {
                // ── Bot died and was removed via PlayerList.remove() ─────────
                // Suppress vanilla message and broadcast custom leave directly -
                // newer Paper (26.1.1+) does not reliably re-broadcast a Component
                // set on event.quitMessage() from inside the MONITOR handler.
                event.quitMessage(null);
                BotBroadcast.broadcastLeave(fp);
            }
            // Skip further real-player logic.
            return;
        }

        String name = event.getPlayer().getName();
        // Run one tick later so LP has a chance to process the disconnect first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { manager.validateUserBotNames(uuid, name); } catch (Throwable ignored) {}
        }, 2L);
    }
}


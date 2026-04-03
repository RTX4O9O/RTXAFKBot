package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Syncs all currently active fake players to any real player who joins,
 * so they see the tab list entries and in-world entities immediately.
 */
public class PlayerJoinListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public PlayerJoinListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Skip NMS bot players — they show vanilla join messages automatically
        if (manager.getByName(event.getPlayer().getName()) != null) return;


        // Send any stored update notification to admins/ops who join after startup
        try {
            var upd = plugin.getUpdateNotification();
            if (upd != null) {
                if (me.bill.fakePlayerPlugin.permission.Perm.hasOrOp(event.getPlayer(), me.bill.fakePlayerPlugin.permission.Perm.ALL)) {
                    try {
                        event.getPlayer().sendMessage(upd);
                    } catch (NoSuchMethodError | NoClassDefFoundError e) {
                        event.getPlayer().sendMessage(upd.toString());
                    }
                }
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
            // These are purely client-side entries — no entity exists on this server.
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
        if (manager.getCount() == 0) return;

        // Skip NMS bot players — vanilla quit message shows automatically
        if (manager.getByName(event.getPlayer().getName()) != null) return;

        java.util.UUID uuid = event.getPlayer().getUniqueId();
        String name         = event.getPlayer().getName();
        // Run one tick later so LP has a chance to process the disconnect first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { manager.validateUserBotNames(uuid, name); } catch (Throwable ignored) {}
        }, 2L);
    }
}

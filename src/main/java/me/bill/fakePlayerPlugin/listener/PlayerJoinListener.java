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
        // Also send compatibility warnings to admins/ops if configured
        try {
            var warnComp = plugin.getCompatibilityWarning();
            if (warnComp != null && me.bill.fakePlayerPlugin.config.Config.warningsNotifyAdmins()) {
                if (me.bill.fakePlayerPlugin.permission.Perm.hasOrOp(event.getPlayer(), me.bill.fakePlayerPlugin.permission.Perm.ALL)) {
                    try {
                        // Preferred: send as Component (Adventure API)
                        event.getPlayer().sendMessage(warnComp);
                    } catch (NoSuchMethodError | NoClassDefFoundError e) {
                        // Older Bukkit without Adventure: fall back to plain text (strip tags)
                        event.getPlayer().sendMessage(warnComp.toString());
                    }
                }
            }
        } catch (Throwable ignored) {}

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

        if (manager.getCount() == 0) return;
        // Small delay so the client is fully ready to receive entity packets
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { manager.syncToPlayer(event.getPlayer()); } catch (Throwable ignored) {}
        }, 5L);
    }

    /**
     * When a spawner disconnects, validate all their user bots' display names to ensure
     * no unresolved {@code {placeholder}} tokens remain.  This is a safety net for the
     * rare edge case where a bot's display name was not fully resolved at spawn/restore time.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (manager.getCount() == 0) return;
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        String name         = event.getPlayer().getName();
        // Run one tick later so LP has a chance to process the disconnect first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { manager.validateUserBotNames(uuid, name); } catch (Throwable ignored) {}
        }, 2L);
    }
}

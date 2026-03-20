package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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
                if (me.bill.fakePlayerPlugin.permission.Perm.hasOrOp(event.getPlayer(), me.bill.fakePlayerPlugin.permission.Perm.ALL)
                        || event.getPlayer().hasPermission("fakeplayer.notify")) {
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
        event.getPlayer().getScheduler().runDelayed(
                plugin,
                task -> manager.syncToPlayer(event.getPlayer()),
                null,
                5L
        );
    }
}

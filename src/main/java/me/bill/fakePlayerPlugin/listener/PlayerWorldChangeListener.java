package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Handles multi-world support for fake players.
 *
 * <p>When a real player changes worlds, Minecraft's client resets custom
 * player-info (tab-list) packets. This listener re-syncs all bot tab-list
 * entries to the player after they finish the world transition so bots
 * remain visible in the tab list regardless of which world the player is in.
 *
 * <p>The NMS ServerPlayer entities are real server-side
 * entities kept alive by plugin chunk-tickets — they do not need to be
 * respawned. Only the packet-based tab-list entries need to be re-sent.
 */
public class PlayerWorldChangeListener implements Listener {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    public PlayerWorldChangeListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (manager.getCount() == 0) return;

        Player player = event.getPlayer();

        // Re-sync tab list entries after a short delay (3 ticks) so the
        // client has finished its own world-transition state reset.
        // Use Bukkit.getScheduler() (not player.getScheduler()) for compatibility
        // with non-Folia platforms such as Cardboard / Fabric-based servers.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            manager.syncToPlayer(player);
        }, 3L);
    }
}

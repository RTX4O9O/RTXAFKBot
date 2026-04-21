package me.bill.fakePlayerPlugin.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

public class BotSpawnProtectionListener implements Listener {

  private final FakePlayerPlugin plugin;
  private final Set<UUID> protectedBots = new HashSet<>();

  public BotSpawnProtectionListener(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBotJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    if (!isFppBot(player)) return;

    UUID botUuid = player.getUniqueId();
    protectedBots.add(botUuid);

    Config.debugNms(
        "BotSpawnProtection: protecting " + player.getName() + " from teleports for 5 ticks");

    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              protectedBots.remove(botUuid);
              Config.debugNms("BotSpawnProtection: removed protection for " + player.getName());
            },
            5L);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onBotTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();

    if (!isFppBot(player)) return;

    if (!protectedBots.contains(player.getUniqueId())) return;

    PlayerTeleportEvent.TeleportCause cause = event.getCause();

    // Always allow explicit /tp commands.
    if (cause == PlayerTeleportEvent.TeleportCause.COMMAND) {
      return;
    }

    // Block ALL other teleport causes during the 5-tick grace window.
    // This covers PLUGIN and UNKNOWN (other-plugin interference) as well as
    // NETHER_PORTAL / END_PORTAL / END_GATEWAY (dimension respawn logic that
    // fires when a bot is spawned directly into the nether or end and has no
    // prior player-data at that location).
    event.setCancelled(true);
    Config.debugNms(
        "BotSpawnProtection: blocked "
            + cause.name()
            + " teleport for "
            + player.getName()
            + " from "
            + formatLoc(event.getFrom())
            + " to "
            + formatLoc(event.getTo()));
  }

  /**
   * Returns true if {@code player} is a managed FPP bot.
   *
   * <p>During the initial spawn, the PDC key is not yet written (it is set after
   * {@link me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner#spawnFakePlayer} returns), so we
   * fall back to a UUID lookup against the active-player map which is populated before the NMS
   * body is spawned.
   */
  private boolean isFppBot(Player player) {
    // Fast path: PDC key present (works after first-spawn completes and on restores).
    if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
      String marker =
          player
              .getPersistentDataContainer()
              .get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING);
      if (marker != null && marker.startsWith("fpp-visual:")) return true;
    }
    // Fallback: check active-player registry by UUID.
    // This path fires during the very first PlayerJoinEvent emitted inside
    // placeNewPlayer(), before the PDC key has been written.
    me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager manager =
        plugin.getFakePlayerManager();
    return manager != null && manager.getByUuid(player.getUniqueId()) != null;
  }

  private String formatLoc(Location loc) {
    if (loc == null) return "null";
    return String.format(
        "%s (%.1f, %.1f, %.1f)",
        loc.getWorld() != null ? loc.getWorld().getName() : "?",
        loc.getX(),
        loc.getY(),
        loc.getZ());
  }
}

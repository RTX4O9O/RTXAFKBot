package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Prevents automatic kicks (anti-idle, anti-AFK, connection timeout, etc.)
 * from removing NMS fake player bots from the server.
 *
 * <p><b>Intentional kicks are still allowed.</b>  When the plugin kicks a bot
 * intentionally (e.g. on death or despawn) it uses {@code player.kick(Component.empty())},
 * which produces an empty plain-text reason.  This listener detects that empty reason
 * and allows the kick through so the normal death / removal flow can complete.
 *
 * <p>All other kicks (non-empty reason strings like "You have been idle too long",
 * "Flying is not enabled on this server", etc.) are cancelled so bots are not
 * accidentally removed by third-party plugins.
 */
public class FakePlayerKickListener implements Listener {

    private final FakePlayerManager manager;

    public FakePlayerKickListener(FakePlayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        // Only intercept kicks for fake player bots
        if (manager.getByName(event.getPlayer().getName()) == null) return;

        // Allow kicks that originate from our own code (death / despawn).
        // The plugin always kicks bots with Component.empty() for intentional removal,
        // which serialises to an empty string in plain text.
        Component reason = event.reason();
        String plainReason = reason == null ? "" :
                PlainTextComponentSerializer.plainText().serialize(reason);

        if (plainReason.isEmpty()) return; // intentional kick — let it through

        // Cancel all other automated kicks (anti-idle, timeout, anti-cheat, etc.)
        event.setCancelled(true);
    }
}


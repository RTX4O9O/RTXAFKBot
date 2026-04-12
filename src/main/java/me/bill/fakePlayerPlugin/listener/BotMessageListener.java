package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.ai.BotConversationManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Intercepts direct message commands (/msg, /tell, /whisper, /w) sent to bots
 * and routes them to the AI conversation system.
 *
 * <p>Bots will respond with AI-generated messages that match their personality.
 */
public final class BotMessageListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager botManager;
    private final BotConversationManager conversationManager;

    public BotMessageListener(
            FakePlayerPlugin plugin,
            FakePlayerManager botManager,
            BotConversationManager conversationManager
    ) {
        this.plugin = plugin;
        this.botManager = botManager;
        this.conversationManager = conversationManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player sender = event.getPlayer();
        String message = event.getMessage();

        // Skip if player is a bot (prevents bot-to-bot AI loops)
        if (botManager.getByUuid(sender.getUniqueId()) != null) {
            return;
        }

        // Parse command
        String[] parts = message.split("\\s+", 3);
        if (parts.length < 3) return;

        String command = parts[0].toLowerCase().substring(1); // Remove leading /

        // Check if this is a direct message command
        if (!isDirectMessageCommand(command)) {
            return;
        }

        String targetName = parts[1];
        String messageContent = parts[2];

        // Check if target is a bot
        FakePlayer targetBot = botManager.getByName(targetName);
        if (targetBot == null) {
            return; // Not a bot, let vanilla handle it
        }

        // Cancel event so vanilla /msg doesn't fire
        event.setCancelled(true);

        // Send to AI conversation manager
        conversationManager.handleDirectMessage(targetBot, sender, messageContent);

        // Send confirmation to sender (vanilla /msg format)
        sender.sendMessage("§7[me → " + targetBot.getName() + "] §f" + messageContent);
    }

    /**
     * Checks if a command is a direct message command.
     * Supports: /msg, /tell, /whisper, /w, /m, /t, /pm, /dm
     */
    private boolean isDirectMessageCommand(String command) {
        return switch (command) {
            case "msg", "tell", "whisper", "w", "m", "t", "pm", "dm",
                 "message", "emsg", "etell", "ewhisper" -> true;
            default -> false;
        };
    }
}


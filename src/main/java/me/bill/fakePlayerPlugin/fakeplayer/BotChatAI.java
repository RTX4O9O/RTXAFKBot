package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives bot fake-chat. Each bot gets its OWN independent scheduler loop so
 * messages fire as fast as the interval config allows — identical pacing to
 * the join/leave stagger system.
 * <p>
 * Per-bot loop:
 * <ol>
 *   <li>Wait random delay between [min, max] seconds.</li>
 *   <li>Roll chance — skip silently if unlucky.</li>
 *   <li>Pick a message that differs from the bot's own last message.</li>
 *   <li>Resolve {name} / {random_player} placeholders.</li>
 *   <li>Broadcast locally and send to other servers via plugin messaging.</li>
 *   <li>Reschedule.</li>
 * </ol>
 * All config values are re-read on every fire so /fpp reload and
 * /fpp chat on|off take effect immediately without a restart.
 */
public final class BotChatAI {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    /**
     * Thread-local flag marking whether the current broadcast is a remote echo
     * (received from another server via plugin messaging). When true, we must
     * NOT send the message back out via plugin messaging to prevent infinite loops.
     */
    private static final ThreadLocal<Boolean> isRemoteBroadcast = ThreadLocal.withInitial(() -> false);

    /** Last message each bot sent — per-bot no-repeat tracking. */
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    /** Task IDs for each bot's scheduler loop so we can cancel on disable. */
    private final Map<UUID, Integer> taskIds = new ConcurrentHashMap<>();

    public BotChatAI(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
        // Start a watcher that picks up newly spawned bots and gives each one
        // its own loop. Runs every 20 ticks (1 s) — lightweight.
        Bukkit.getScheduler().runTaskTimer(plugin, this::syncBotLoops, 20L, 20L);
    }

    // ── Loop management ───────────────────────────────────────────────────────

    /** Starts loops for new bots, cancels loops for removed bots. */
    private void syncBotLoops() {
        // Start a loop for any newly spawned bot (no Set allocation)
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (!taskIds.containsKey(fp.getUuid())) {
                scheduleNext(fp.getUuid());
            }
        }
        // Cancel loops for bots that are no longer active
        taskIds.entrySet().removeIf(e -> {
            if (manager.getByUuid(e.getKey()) == null) {
                Bukkit.getScheduler().cancelTask(e.getValue());
                lastMessage.remove(e.getKey());
                return true;
            }
            return false;
        });
    }


    private void scheduleNext(UUID botUuid) {
        if (!Config.fakeChatEnabled()) {
            // Re-check in 2 s — quick reaction when chat is re-enabled
            int id = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> scheduleNext(botUuid), 40L).getTaskId();
            taskIds.put(botUuid, id);
            return;
        }

        int minTicks = Config.fakeChatIntervalMin() * 20;
        int maxTicks = Config.fakeChatIntervalMax() * 20;
        if (maxTicks < minTicks) maxTicks = minTicks;

        long delay = minTicks == maxTicks
                ? Math.max(1L, minTicks)
                : Math.max(1L, minTicks + ThreadLocalRandom.current().nextInt(maxTicks - minTicks + 1));

        int id = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fireChat(botUuid);
            // Only reschedule if bot is still active
            if (manager.getByUuid(botUuid) != null) {
                scheduleNext(botUuid);
            } else {
                taskIds.remove(botUuid);
                lastMessage.remove(botUuid);
            }
        }, delay).getTaskId();

        taskIds.put(botUuid, id);
    }

    // ── Fire ──────────────────────────────────────────────────────────────────

    private void fireChat(UUID botUuid) {
        if (!Config.fakeChatEnabled()) return;

        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) return;

        if (Config.fakeChatRequirePlayer() && Bukkit.getOnlinePlayers().isEmpty()) return;

        // Chance roll
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatChance()) return;

        List<String> messages = Config.fakeChatMessages();
        if (messages.isEmpty()) return;

        // Pick message — avoid the same bot repeating its own last message
        String last = lastMessage.get(botUuid);
        String raw;
        if (messages.size() == 1) {
            raw = messages.getFirst();
        } else {
            List<String> pool = new ArrayList<>(messages);
            if (last != null) pool.remove(last);
            raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        lastMessage.put(botUuid, raw);

        // Resolve placeholders
        String message = raw
                .replace("{name}", bot.getName())
                .replace("{random_player}", resolveRandomPlayer(bot));

        // Build the broadcast line from the configurable chat-format.
        // LP prefix/suffix are applied automatically by LuckPerms for real NMS players.
        String formatted = Config.fakeChatFormat()
                .replace("{prefix}", "")
                .replace("{suffix}", "")
                .replace("{bot_name}", bot.getDisplayName())
                .replace("{message}", message);

        // Expand PlaceholderAPI tokens if available
        if (formatted.contains("%")) {
            try {
                if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, formatted);
                }
            } catch (Exception ignored) {}
        }

        Component chatLine = TextUtil.colorize(formatted);

        Bukkit.getServer().broadcast(chatLine);
        Config.debug("BotChatAI: " + bot.getName() + " said: " + message);

        // ── Cross-server sync via plugin messaging ────────────────────────────
        // sendChatToNetwork() embeds a unique message ID so when the proxy echoes
        // the payload back to this server the duplicate is silently suppressed.
        var vc = plugin.getVelocityChannel();
        if (vc != null) {
            // Get prefix/suffix from LuckPerms if available
            String prefix = "";
            String suffix = "";
            try {
                if (me.bill.fakePlayerPlugin.util.LuckPermsHelper.isAvailable()) {
                    String group = me.bill.fakePlayerPlugin.util.LuckPermsHelper.getPrimaryGroup(bot.getUuid());
                    if (group != null && !group.equals("default")) {
                        // For network messaging we need to manually get prefix/suffix
                        // since the remote servers need them to format the message
                        net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                        net.luckperms.api.model.group.Group lpGroup = lp.getGroupManager().getGroup(group);
                        if (lpGroup != null) {
                            prefix = lpGroup.getCachedData().getMetaData().getPrefix();
                            suffix = lpGroup.getCachedData().getMetaData().getSuffix();
                            if (prefix == null) prefix = "";
                            if (suffix == null) suffix = "";
                        }
                    }
                }
            } catch (Exception ignored) {}
            
            vc.sendChatToNetwork(bot.getName(), bot.getDisplayName(), message, prefix, suffix);
            Config.debug("BotChatAI: forwarded to other servers via plugin messaging.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Broadcasts a bot chat message received from another server via plugin messaging.
     *
     * <p>Reconstructs the exact formatted chat line using the same template
     * ({@link Config#fakeChatFormat()}) and colorization ({@link TextUtil#colorize})
     * as the originating server, ensuring consistent appearance across all servers.
     *
     * <p><b>Loop prevention:</b> sets the {@link #isRemoteBroadcast} flag so this
     * message is NOT re-sent back out via plugin messaging.
     *
     * @param botName        internal bot name (e.g. "Notch", "ubot_Player_1")
     * @param botDisplayName display name with prefix/formatting
     * @param message        the resolved message text (placeholders already expanded)
     * @param prefix         LuckPerms prefix (empty string if LP not used)
     * @param suffix         LuckPerms suffix (empty string if LP not used)
     */
    public static void broadcastRemote(String botName, String botDisplayName,
                                       String message, String prefix, String suffix) {
        if (!Config.fakeChatEnabled()) {
            Config.debug("BotChatAI: remote message dropped (fake-chat disabled).");
            return;
        }

        // Mark this as a remote broadcast so fireChat() won't forward it back out
        isRemoteBroadcast.set(true);
        try {
            // Reconstruct the same formatted line the originating server used
            String formatted = Config.fakeChatFormat()
                    .replace("{prefix}", prefix)
                    .replace("{suffix}", suffix)
                    .replace("{bot_name}", botDisplayName)
                    .replace("{message}", message);

            // Expand PlaceholderAPI tokens if available
            if (formatted.contains("%")) {
                try {
                    if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, formatted);
                    }
                } catch (Exception ignored) {}
            }

            Component chatLine = TextUtil.colorize(formatted);
            Bukkit.getServer().broadcast(chatLine);
            Config.debug("BotChatAI: broadcast remote message from bot '" + botName + "'.");
        } finally {
            // Always clear the flag after broadcast (even if an exception occurs)
            isRemoteBroadcast.remove();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveRandomPlayer(FakePlayer self) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!online.isEmpty())
            return online.get(ThreadLocalRandom.current().nextInt(online.size())).getName();

        List<FakePlayer> others = new ArrayList<>(manager.getActivePlayers());
        others.removeIf(fp -> fp.getUuid().equals(self.getUuid()));
        if (!others.isEmpty())
            return others.get(ThreadLocalRandom.current().nextInt(others.size())).getName();

        return self.getName();
    }
}

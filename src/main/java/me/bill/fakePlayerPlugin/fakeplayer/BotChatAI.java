package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.TextUtil;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives bot auto-chat with realistic human-like behaviour.
 *
 * <h3>Realism features</h3>
 * <ul>
 *   <li><b>Typing delay</b> — optional 0–2.5 s pause before each message fires.</li>
 *   <li><b>Burst messages</b> — configurable chance of sending a short follow-up
 *       message 2–5 s after the first, mimicking multi-line real chat.</li>
 *   <li><b>Mention replies</b> — when a real player says a bot's name in chat
 *       the bot has a configurable chance (default 65 %) of replying after a
 *       short delay — see {@code fake-chat.mention-reply-chance}.</li>
 *   <li><b>Stagger interval</b> — minimum gap (default 3 s) between any two bots
 *       chatting prevents message floods.</li>
 *   <li><b>Activity variation</b> — each bot is randomly assigned a quiet /
 *       normal / active / very-active tier that scales its chat interval.</li>
 *   <li><b>Per-bot tier override</b> — {@link FakePlayer#setChatTier(String)} locks a
 *       bot to a specific activity tier; changeable via {@code /fpp chat <bot> tier}.</li>
 *   <li><b>Per-bot mute</b> — {@link FakePlayer#setChatEnabled(boolean)} silences
 *       individual bots; changeable via {@code /fpp chat <bot> on|off}.</li>
 *   <li><b>History window</b> — per-bot Deque keeps the last N messages so the
 *       same line is never repeated back-to-back.</li>
 *   <li><b>Event-triggered chat</b> — bots react to player joins and deaths.</li>
 *   <li><b>Keyword reactions</b> — bots reply when players say configured keywords.</li>
 *   <li><b>Rich placeholders</b> — {@code {name}}, {@code {random_player}},
 *       {@code {online}}, {@code {world}}, {@code {time}}, {@code {biome}}.</li>
 * </ul>
 *
 * <p>All config values are re-read on every fire so {@code /fpp reload} and
 * {@code /fpp chat on|off} take effect immediately.
 */
public final class BotChatAI implements Listener {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    /**
     * Thread-local flag marking whether the current broadcast is a remote echo
     * received from another server. When true we must NOT re-forward the message
     * via plugin messaging to prevent infinite loops.
     */
    private static final ThreadLocal<Boolean> isRemoteBroadcast = ThreadLocal.withInitial(() -> false);

    /** Per-bot rolling message history — avoids repeating recent messages. */
    private final Map<UUID, Deque<String>> messageHistory = new ConcurrentHashMap<>();
    /** Task IDs for each bot's main scheduler loop so we can cancel on removal. */
    private final Map<UUID, Integer> taskIds = new ConcurrentHashMap<>();
    /**
     * Per-bot activity multiplier applied to the base chat interval.
     * Assigned once when the bot's loop starts; preserved until the bot is removed.
     * Can be overridden by {@link #setActivityTier(UUID, String)}.
     */
    private final Map<UUID, Double> activityMultipliers = new ConcurrentHashMap<>();
    /** Pending mention-reply task per bot — only one reply queued at a time. */
    private final Map<UUID, Integer> pendingReplyTasks = new ConcurrentHashMap<>();
    /** Pending event-trigger task per bot — only one at a time. */
    private final Map<UUID, Integer> pendingEventTasks = new ConcurrentHashMap<>();
    /**
     * Timed-mute expiry tasks — when active, the bot is silenced until the task fires
     * and re-enables chat. Key = bot UUID, value = scheduler task ID.
     */
    private final Map<UUID, Integer> muteTaskIds = new ConcurrentHashMap<>();

    /**
     * Timestamp (ms) of the last time any bot actually sent a chat message.
     * Used by the stagger logic to space out bot messages across the server.
     */
    private final AtomicLong lastAnyChatMs = new AtomicLong(0L);

    public BotChatAI(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
        // 1-second watcher: start loops for new bots, clean up removed ones.
        Bukkit.getScheduler().runTaskTimer(plugin, this::syncBotLoops, 20L, 20L);
        // Register chat event listeners.
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Loop management ───────────────────────────────────────────────────────

    private void syncBotLoops() {
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (!taskIds.containsKey(fp.getUuid())) {
                assignActivityMultiplier(fp.getUuid());
                scheduleNext(fp.getUuid());
            }
        }
        taskIds.entrySet().removeIf(e -> {
            if (manager.getByUuid(e.getKey()) == null) {
                Bukkit.getScheduler().cancelTask(e.getValue());
                messageHistory.remove(e.getKey());
                activityMultipliers.remove(e.getKey());
                cancelPendingReply(e.getKey());
                cancelPendingEvent(e.getKey());
                cancelMuteTask(e.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Assigns a random activity tier to a newly-discovered bot.
     * If the bot has a {@link FakePlayer#getChatTier()} override, that tier is used.
     * <ul>
     *   <li>15 % — quiet    (2.0× interval → chats ~half as often)</li>
     *   <li>25 % — passive  (1.4× interval)</li>
     *   <li>30 % — normal   (1.0× interval)</li>
     *   <li>18 % — active   (0.7× interval)</li>
     *   <li>12 % — chatty   (0.5× interval → chats ~twice as often)</li>
     * </ul>
     */
    private void assignActivityMultiplier(UUID botUuid) {
        // Check for a per-bot tier override
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null && fp.getChatTier() != null) {
            activityMultipliers.put(botUuid, tierToMultiplier(fp.getChatTier()));
            return;
        }
        if (!Config.fakeChatActivityVariation()) {
            activityMultipliers.put(botUuid, 1.0);
            return;
        }
        double r = ThreadLocalRandom.current().nextDouble();
        double mult;
        if      (r < 0.15) mult = 2.0;   // quiet
        else if (r < 0.40) mult = 1.4;   // passive
        else if (r < 0.70) mult = 1.0;   // normal
        else if (r < 0.88) mult = 0.7;   // active
        else               mult = 0.5;   // chatty
        activityMultipliers.put(botUuid, mult);
    }

    /**
     * Converts a tier name to its activity multiplier.
     * Unknown values fall back to {@code 1.0} (normal).
     */
    private static double tierToMultiplier(String tier) {
        return switch (tier.toLowerCase(Locale.ROOT)) {
            case "quiet"   -> 2.0;
            case "passive" -> 1.4;
            case "active"  -> 0.7;
            case "chatty"  -> 0.5;
            default        -> 1.0; // "normal" and unrecognised
        };
    }

    /**
     * Overrides the activity tier for a specific bot and resets its multiplier immediately.
     * Pass {@code null} to return to random assignment.
     *
     * @param botUuid UUID of the bot
     * @param tier    one of {@code "quiet"}, {@code "passive"}, {@code "normal"},
     *                {@code "active"}, {@code "chatty"}, or {@code null}
     */
    public void setActivityTier(UUID botUuid, String tier) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) return;
        fp.setChatTier(tier);
        if (tier == null) {
            // Re-roll randomly
            activityMultipliers.remove(botUuid);
            assignActivityMultiplier(botUuid);
        } else {
            activityMultipliers.put(botUuid, tierToMultiplier(tier));
        }
        Config.debugChat("Activity tier for " + fp.getName() + " set to "
                + (tier != null ? tier : "random") + " (mult="
                + activityMultipliers.getOrDefault(botUuid, 1.0) + ")");
    }

    private void scheduleNext(UUID botUuid) {
        if (!Config.fakeChatEnabled()) {
            // Chat disabled — cancel any pending reply/event/burst tasks for this bot
            // so they don't fire after the admin ran /fpp chat off.
            cancelPendingReply(botUuid);
            cancelPendingEvent(botUuid);
            // Cancel the OLD long-delay main task so it can't fire a duplicate loop.
            Integer oldTask = taskIds.remove(botUuid);
            if (oldTask != null) Bukkit.getScheduler().cancelTask(oldTask);
            // Re-check in 2 s — picks up quickly when chat is re-enabled via /fpp reload
            int id = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> scheduleNext(botUuid), 40L).getTaskId();
            taskIds.put(botUuid, id);
            return;
        }

        double mult = activityMultipliers.getOrDefault(botUuid, 1.0);
        int minTicks = Math.max(20, (int)(Config.fakeChatIntervalMin() * 20 * mult));
        int maxTicks = Math.max(minTicks, (int)(Config.fakeChatIntervalMax() * 20 * mult));

        long delay = minTicks == maxTicks
                ? minTicks
                : minTicks + ThreadLocalRandom.current().nextInt(maxTicks - minTicks + 1);

        int id = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fireChat(botUuid);
            if (manager.getByUuid(botUuid) != null) {
                scheduleNext(botUuid);
            } else {
                taskIds.remove(botUuid);
                messageHistory.remove(botUuid);
                activityMultipliers.remove(botUuid);
            }
        }, delay).getTaskId();

        taskIds.put(botUuid, id);
    }

    // ── Fire ──────────────────────────────────────────────────────────────────

    private void fireChat(UUID botUuid) {
        if (!Config.fakeChatEnabled()) return;

        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) return;

        // Per-bot mute check
        if (!bot.isChatEnabled()) return;

        if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

        // Chance roll — skip silently if unlucky
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatChance()) return;

        List<String> messages = Config.fakeChatMessages();
        if (messages.isEmpty()) return;

        String message = pickMessage(botUuid, messages, bot);

        // ── Stagger + typing delay ─────────────────────────────────────────────
        int delayTicks = computePreSendDelay();
        if (delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> sendMessage(bot, message, true), delayTicks);
        } else {
            sendMessage(bot, message, true);
        }
    }

    /**
     * Picks the next message for {@code botUuid}, resolves all placeholders,
     * and updates the history deque.
     *
     * <p>Supported placeholders:
     * <ul>
 *   <li>{@code {name}}          — the bot's own name</li>
 *   <li>{@code {random_player}} — a random real player's name</li>
 *   <li>{@code {online}}        — number of real (non-bot) players online</li>
 *   <li>{@code {world}}         — the bot's current world name</li>
 *   <li>{@code {time}}          — {@code "day"} or {@code "night"} based on world time</li>
 *   <li>{@code {biome}}         — the bot's current biome name (human-readable)</li>
 *   <li>{@code {x}}/{@code {y}}/{@code {z}} — the bot's current block coordinates</li>
 *   <li>{@code {server}}        — the server ID from config ({@code database.server-id})</li>
 *   <li>{@code {date}}          — today's date (e.g. {@code 2026-04-05})</li>
 *   <li>{@code {day}}           — full day of week (e.g. {@code Sunday})</li>
 * </ul>
     */
    private String pickMessage(UUID botUuid, List<String> pool, FakePlayer bot) {
        Deque<String> history = messageHistory.computeIfAbsent(botUuid, k -> new ArrayDeque<>());
        int historySize = Math.max(1, Config.fakeChatHistorySize());

        // Exclude recently-sent messages so the bot doesn't repeat itself
        List<String> available = new ArrayList<>(pool);
        available.removeAll(history);
        if (available.isEmpty()) available = new ArrayList<>(pool); // reset if pool exhausted

        String raw = available.get(ThreadLocalRandom.current().nextInt(available.size()));

        // Roll history window
        history.addLast(raw);
        while (history.size() > historySize) history.pollFirst();

        return resolvePlaceholders(raw, bot);
    }

    /**
     * Resolves all chat placeholders in the given raw message string.
     */
    private String resolvePlaceholders(String raw, FakePlayer bot) {
        String s = raw
                .replace("{name}", bot.getName())
                .replace("{random_player}", resolveRandomPlayer(bot));

        // {online} — real player count
        if (s.contains("{online}")) {
            int realCount = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (manager.getByUuid(p.getUniqueId()) == null) realCount++;
            }
            s = s.replace("{online}", String.valueOf(realCount));
        }

        // {server} — server ID from config
        if (s.contains("{server}")) {
            s = s.replace("{server}", Config.serverId());
        }

        // {date} / {day} — real-world date
        if (s.contains("{date}") || s.contains("{day}")) {
            LocalDate today = LocalDate.now();
            if (s.contains("{date}")) s = s.replace("{date}", today.toString());
            if (s.contains("{day}"))  s = s.replace("{day}",  today.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.ROOT));
        }

        // Location-based placeholders — only resolved when the bot has a valid entity
        if (s.contains("{world}") || s.contains("{time}") || s.contains("{biome}")
                || s.contains("{x}") || s.contains("{y}") || s.contains("{z}")) {
            Location loc = bot.getLiveLocation();
            if (loc != null && loc.getWorld() != null) {
                if (s.contains("{world}")) {
                    s = s.replace("{world}", loc.getWorld().getName());
                }
                if (s.contains("{time}")) {
                    long worldTime = loc.getWorld().getTime();
                    // Day: 0–12000, Night: 12000–24000 (approx)
                    String timeLabel = (worldTime >= 0 && worldTime < 12300) ? "day" : "night";
                    s = s.replace("{time}", timeLabel);
                }
                if (s.contains("{biome}")) {
                    try {
                        // getKey().getKey() returns the registry path already in lowercase
                        // (e.g. "plains", "dark_forest") — avoids the deprecated OldEnum.name()
                        String biome = loc.getBlock().getBiome().getKey().getKey()
                                .replace('_', ' ');
                        s = s.replace("{biome}", biome);
                    } catch (Throwable t) {
                        s = s.replace("{biome}", "unknown");
                    }
                }
                // {x}, {y}, {z} — block coordinates
                if (s.contains("{x}")) s = s.replace("{x}", String.valueOf((int) loc.getX()));
                if (s.contains("{y}")) s = s.replace("{y}", String.valueOf((int) loc.getY()));
                if (s.contains("{z}")) s = s.replace("{z}", String.valueOf((int) loc.getZ()));
            } else {
                s = s.replace("{world}", "unknown")
                      .replace("{time}", "day")
                      .replace("{biome}", "unknown")
                      .replace("{x}", "?")
                      .replace("{y}", "?")
                      .replace("{z}", "?");
            }
        }
        return s;
    }

    /**
     * Calculates extra pre-send delay ticks combining stagger enforcement and
     * an optional simulated typing pause.
     *
     * <p>Stagger: if another bot sent a message less than {@code stagger-interval}
     * seconds ago, we push this message past that gap.  The projected send time
     * is written atomically so the next caller sees it immediately.
     */
    private int computePreSendDelay() {
        int staggerTicks = 0;
        int staggerSec = Config.fakeChatStaggerInterval();
        if (staggerSec > 0) {
            long now       = System.currentTimeMillis();
            long minGapMs  = staggerSec * 1000L;
            long nextSend;
            // CAS loop so concurrent callers (shouldn't happen on main thread, but be safe)
            while (true) {
                long last = lastAnyChatMs.get();
                nextSend  = Math.max(now, last + minGapMs);
                if (lastAnyChatMs.compareAndSet(last, nextSend)) break;
            }
            long extraMs = nextSend - now;
            if (extraMs > 0) {
                staggerTicks = (int)(extraMs / 50L) + 1; // 50 ms per tick
            }
        }

        int typingTicks = 0;
        if (Config.fakeChatTypingDelay()) {
            // Random pause 0–2.5 s converted to ticks
            typingTicks = ThreadLocalRandom.current().nextInt(50);
        }

        return staggerTicks + typingTicks;
    }

    /**
     * Delivers a resolved message for {@code bot} through the appropriate path
     * and optionally schedules a burst follow-up.
     * Respects both the global {@code fake-chat.enabled} flag and the per-bot mute.
     * In-flight typing-delay tasks check this before sending.
     */
    private void sendMessage(FakePlayer bot, String message, boolean allowBurst) {
        if (manager.getByUuid(bot.getUuid()) == null) return; // bot removed while waiting
        // Re-check both global and per-bot flags here so any in-flight typing-delay
        // or burst tasks that were already queued BEFORE a /fpp chat off command
        // respect the new state immediately.
        if (!Config.fakeChatEnabled()) return;
        if (!bot.isChatEnabled()) return;
        sendMessageForced(bot, message, allowBurst);
    }

    /**
     * Same as {@link #sendMessage} but bypasses the global {@code fake-chat.enabled}
     * and per-bot mute checks. Used by admin force-send commands so the message
     * always goes through regardless of chat state.
     */
    private void sendMessageForced(FakePlayer bot, String message, boolean allowBurst) {
        if (manager.getByUuid(bot.getUuid()) == null) return;

        Player playerEntity = bot.getPlayer();
        if (playerEntity != null && playerEntity.isOnline() && !bot.isBodyless()) {
            // Local bot with a body: fire through the real chat pipeline
            dispatchChat(playerEntity, message);
        } else {
            // Bodyless / entity-less bot: broadcast using the configurable remote format
            broadcastFormatted(bot.getDisplayName(), message);
        }
        Config.debugChat(bot.getName() + " said: " + message);

        // ── Cross-server sync via plugin messaging ─────────────────────────────
        var vc = plugin.getVelocityChannel();
        if (vc != null) {
            vc.sendChatToNetwork(bot.getName(), bot.getDisplayName(), message, "", "");
            Config.debugChat("Forwarded to other servers via plugin messaging.");
        }

        // ── Burst follow-up ───────────────────────────────────────────────────
        if (allowBurst) {
            double burstChance = Config.fakeChatBurstChance();
            if (burstChance > 0 && ThreadLocalRandom.current().nextDouble() < burstChance) {
                scheduleBurst(bot);
            }
        }
    }

    /**
     * Broadcasts a message using the configurable remote-format template.
     * Used for bodyless bots and remote-bot echoes.
     */
    private static void broadcastFormatted(String displayName, String message) {
        try {
            String format = Config.fakeChatRemoteFormat()
                    .replace("{name}", displayName)
                    .replace("{message}", message);
            Component chatLine = MiniMessage.miniMessage().deserialize(format);
            Bukkit.getServer().broadcast(chatLine);
        } catch (Throwable t) {
            // Fallback to plain format if MiniMessage fails
            Component chatLine = Component.text("<")
                    .append(TextUtil.colorize(displayName))
                    .append(Component.text("> "))
                    .append(Component.text(message));
            Bukkit.getServer().broadcast(chatLine);
        }
    }

    /** Schedules a short burst follow-up message for {@code bot}. */
    private void scheduleBurst(FakePlayer bot) {
        List<String> pool = Config.chatBurstMessages();
        if (pool.isEmpty()) return;

        int minTicks = Math.max(20, Config.fakeChatBurstDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatBurstDelayMax() * 20);
        long delay = minTicks == maxTicks
                ? minTicks
                : minTicks + ThreadLocalRandom.current().nextInt(maxTicks - minTicks + 1);

        String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        String msg = resolvePlaceholders(raw, bot);

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> sendMessage(bot, msg, false), delay);
    }

    // ── Mention detection (AsyncChatEvent listener) ───────────────────────────

    /**
     * Listens for real player chat messages, schedules a reply for any bot
     * whose name appears in the message text, and fires keyword-triggered
     * reactions when configured.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        if (!Config.fakeChatEnabled()) return;

        // Skip messages sent by bots to prevent infinite loops
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;

        // Extract plain text (thread-safe read on the Adventure Component)
        final String plainText;
        try {
            plainText = PlainTextComponentSerializer.plainText()
                    .serialize(event.message()).toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return;
        }

        // ── Mention replies ────────────────────────────────────────────────────
        if (Config.fakeChatReplyToMentions()) {
            List<UUID> candidates = new ArrayList<>();
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.isChatEnabled()
                        && plainText.contains(fp.getName().toLowerCase(Locale.ROOT))) {
                    candidates.add(fp.getUuid());
                }
            }
            if (!candidates.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (UUID uuid : candidates) {
                        if (!pendingReplyTasks.containsKey(uuid)) {
                            scheduleMentionReply(uuid);
                        }
                    }
                });
            }
        }

        // ── Keyword reactions ──────────────────────────────────────────────────
        if (Config.fakeChatKeywordReactionsEnabled()) {
            Map<String, String> keywordMap = Config.fakeChatKeywordMap();
            if (!keywordMap.isEmpty()) {
                // Find all matching keywords (collect; react to at most one per message)
                List<String> matchedPools = new ArrayList<>();
                for (Map.Entry<String, String> entry : keywordMap.entrySet()) {
                    if (plainText.contains(entry.getKey())) {
                        matchedPools.add(entry.getValue());
                    }
                }
                if (!matchedPools.isEmpty()) {
                    String poolKey = matchedPools.get(
                            ThreadLocalRandom.current().nextInt(matchedPools.size()));
                    Bukkit.getScheduler().runTask(plugin,
                            () -> scheduleKeywordReaction(poolKey));
                }
            }
        }
    }

    /** Schedules a mention-reply for {@code botUuid} (configurable chance of actually firing). */
    private void scheduleMentionReply(UUID botUuid) {
        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null || !bot.isChatEnabled()) return;

        // Configurable chance — bot may ignore the mention
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatMentionReplyChance()) return;

        int minTicks = Math.max(20, Config.fakeChatReplyDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatReplyDelayMax() * 20);
        long delay = minTicks == maxTicks
                ? minTicks
                : minTicks + ThreadLocalRandom.current().nextInt(maxTicks - minTicks + 1);

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingReplyTasks.remove(botUuid);
            FakePlayer b = manager.getByUuid(botUuid);
            if (b == null || !b.isChatEnabled() || !hasRealPlayerOnline()) return;

            // Prefer the dedicated reply pool; fall back to general messages
            List<String> pool = Config.chatReplyMessages();
            if (pool.isEmpty()) pool = Config.fakeChatMessages();
            if (pool.isEmpty()) return;

            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String reply = resolvePlaceholders(raw, b);

            sendMessage(b, reply, false);
            Config.debugChat(b.getName() + " replied to a mention → " + reply);
        }, delay).getTaskId();

        pendingReplyTasks.put(botUuid, taskId);
    }

    /**
     * Picks a random chat-enabled bot and schedules a keyword-reaction message
     * drawn from {@code bot-messages.yml} under {@code keyword-reactions.<poolKey>}.
     */
    private void scheduleKeywordReaction(String poolKey) {
        if (!Config.fakeChatEnabled()) return;
        List<String> pool = Config.chatKeywordReactionMessages(poolKey);
        if (pool.isEmpty()) return;

        // Pick a random chat-enabled bot
        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .toList();
        if (eligible.isEmpty()) return;
        if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));

        // Small random delay (0–3 s) so reaction feels natural
        int delayTicks = ThreadLocalRandom.current().nextInt(60) + 20;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;
            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String msg = resolvePlaceholders(raw, b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " keyword-reaction [" + poolKey + "]: " + msg);
        }, delayTicks);
    }

    private void cancelPendingReply(UUID botUuid) {
        Integer taskId = pendingReplyTasks.remove(botUuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    private void cancelPendingEvent(UUID botUuid) {
        Integer taskId = pendingEventTasks.remove(botUuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    private void cancelMuteTask(UUID botUuid) {
        Integer taskId = muteTaskIds.remove(botUuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    // ── Event-triggered chat ──────────────────────────────────────────────────

    /**
     * Reacts to a real player joining the server.
     * Picks a random chat-enabled bot and has it send a join-reaction message.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnJoinEnabled()) return;

        // Ignore bots joining
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;

        // Chance roll
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnJoinChance()) return;

        List<String> pool = Config.chatJoinReactionMessages();
        if (pool.isEmpty()) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .toList();
        if (eligible.isEmpty()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        final String joinerName = event.getPlayer().getName();

        int minTicks = Math.max(20, Config.fakeChatOnJoinDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatOnJoinDelayMax() * 20);
        long delay   = minTicks + ThreadLocalRandom.current().nextInt(Math.max(1, maxTicks - minTicks + 1));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEventTasks.remove(bot.getUuid());
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;
            if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            // Allow {random_player} to resolve to the joiner for natural-looking reactions
            String msg = resolvePlaceholders(
                    raw.replace("{random_player}", joinerName), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " join-reaction for " + joinerName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Reacts to any entity death (players and other bots).
     * Has a configurable chance of picking a bot to send a death-reaction message.
     * When {@code fake-chat.event-triggers.on-death.players-only} is {@code true},
     * only reactions to real player deaths fire (mobs and animals are ignored).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnDeathEnabled()) return;

        Entity deceased = event.getEntity();

        // Optional player-only filter
        if (Config.fakeChatOnDeathPlayersOnly() && !(deceased instanceof Player)) return;

        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnDeathChance()) return;

        List<String> pool = Config.chatDeathReactionMessages();
        if (pool.isEmpty()) return;


        // Prefer bots not already queued for another event
        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .filter(fp -> !pendingEventTasks.containsKey(fp.getUuid()))
                .toList();
        if (eligible.isEmpty()) return;
        if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        final String deceasedName = deceased.getName();

        int minTicks = Math.max(20, Config.fakeChatOnDeathDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatOnDeathDelayMax() * 20);
        long delay   = minTicks + ThreadLocalRandom.current().nextInt(Math.max(1, maxTicks - minTicks + 1));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEventTasks.remove(bot.getUuid());
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;

            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String msg = resolvePlaceholders(
                    raw.replace("{random_player}", deceasedName), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " death-reaction for " + deceasedName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Reacts to a real player leaving the server.
     * Picks a random chat-enabled bot and has it send a leave-reaction message.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnLeaveEnabled()) return;

        // Ignore bots disconnecting
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;

        // Chance roll
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnLeaveChance()) return;

        List<String> pool = Config.chatLeaveReactionMessages();
        if (pool.isEmpty()) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .toList();
        if (eligible.isEmpty()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        final String leaverName = event.getPlayer().getName();

        int minTicks = Math.max(20, Config.fakeChatOnLeaveDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatOnLeaveDelayMax() * 20);
        long delay   = minTicks + ThreadLocalRandom.current().nextInt(Math.max(1, maxTicks - minTicks + 1));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEventTasks.remove(bot.getUuid());
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;
            if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            // Allow {random_player} to resolve to the player who left
            String msg = resolvePlaceholders(
                    raw.replace("{random_player}", leaverName), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " leave-reaction for " + leaverName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Cancels all current main-loop tasks and immediately reschedules every active bot
     * using fresh interval, chance, and stagger values read from config.
     *
     * <p>Call this after {@code /fpp reload} so changes to
     * {@code fake-chat.interval}, {@code fake-chat.chance}, and
     * {@code fake-chat.stagger-interval} take effect instantly instead of waiting
     * for each bot's old long-delay task to naturally expire.
     *
     * <p>Per-bot tier overrides ({@code /fpp chat <bot> tier}) and per-bot mute
     * states are preserved across the restart.
     */
    public void restartLoops() {
        // Cancel every queued main-loop delayed task so bots don't fire on the old delay
        taskIds.values().forEach(Bukkit.getScheduler()::cancelTask);
        taskIds.clear();
        // Re-read activity multipliers (picks up activity-variation config changes;
        // honours per-bot tier overrides set via /fpp chat <bot> tier)
        for (FakePlayer fp : manager.getActivePlayers()) {
            assignActivityMultiplier(fp.getUuid());
            scheduleNext(fp.getUuid());
        }
        Config.debugChat("BotChatAI loops restarted — new interval "
                + Config.fakeChatIntervalMin() + "–" + Config.fakeChatIntervalMax() + "s,"
                + " stagger " + Config.fakeChatStaggerInterval() + "s");
    }

    /** Cancels all pending tasks. Call this on plugin shutdown / disable. */
    public void cancelAll() {
        taskIds.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingReplyTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingEventTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        muteTaskIds.values().forEach(Bukkit.getScheduler()::cancelTask);
        taskIds.clear();
        messageHistory.clear();
        activityMultipliers.clear();
        pendingReplyTasks.clear();
        pendingEventTasks.clear();
        muteTaskIds.clear();
    }

    /**
     * Immediately cancels all pending chat tasks (main loop, replies, events, bursts)
     * and restarts each bot's loop in the "chat disabled" polling mode (re-checks
     * every 2 s so it picks up immediately when chat is re-enabled).
     *
     * <p>Call this from {@code /fpp chat off} so in-flight typing-delay and event
     * tasks are killed right away rather than waiting until the current long delay fires.
     */
    public void stopAllLoopsNow() {
        // Cancel every tracked task immediately
        taskIds.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingReplyTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingEventTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        taskIds.clear();
        pendingReplyTasks.clear();
        pendingEventTasks.clear();
        // Do NOT clear activityMultipliers / messageHistory — preserve tier overrides
        // and history so they're still active when chat is re-enabled.
        // Restart each bot's loop in disabled-polling mode (scheduleNext will see
        // fakeChatEnabled() == false and create a 2-s re-check task).
        for (FakePlayer fp : manager.getActivePlayers()) {
            scheduleNext(fp.getUuid());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Forces a specific bot to immediately send {@code message} through the
     * appropriate chat path. Does not trigger a burst follow-up.
     * The message is delivered even when global fake-chat is disabled.
     *
     * @param bot     the bot that should speak
     * @param message fully resolved message text (placeholders NOT re-expanded)
     */
    public void forceSendMessage(FakePlayer bot, String message) {
        if (manager.getByUuid(bot.getUuid()) == null) return;
        // Bypass global enabled check — force-send is explicitly requested by admin
        sendMessageForced(bot, message, false);
    }    /**
     * Forces a specific bot to immediately send {@code message}, first expanding
     * all supported chat placeholders ({@code {name}}, {@code {random_player}},
     * {@code {online}}, {@code {world}}, {@code {time}}, {@code {biome}},
     * {@code {x}}, {@code {y}}, {@code {z}}, {@code {server}}, {@code {date}},
     * {@code {day}}).
     *
     * <p>Use this variant when the message string comes directly from a command
     * argument so admin-supplied placeholders are properly resolved.
     *
     * @param bot     the bot that should speak
     * @param message message template with optional placeholder tokens
     */
    public void forceSendMessageResolved(FakePlayer bot, String message) {
        if (manager.getByUuid(bot.getUuid()) == null) return;
        String resolved = resolvePlaceholders(message, bot);
        // Bypass global enabled check — force-send is explicitly requested by admin
        sendMessageForced(bot, resolved, false);
    }

    /**
     * Returns the current activity multiplier for the given bot.
     * A multiplier &lt; 1.0 means the bot chats more frequently than the base
     * interval; &gt; 1.0 means less frequently; 1.0 = normal.
     *
     * @param botUuid bot's UUID
     * @return the multiplier, or {@code 1.0} if not yet assigned
     */
    public double getActivityMultiplier(UUID botUuid) {
        return activityMultipliers.getOrDefault(botUuid, 1.0);
    }

    /**
     * Silences a bot for {@code seconds} seconds, then automatically re-enables it.
     *
     * <p>Any existing timed mute for the bot is cancelled and replaced.
     * Passing {@code 0} or a negative value permanently silences the bot
     * (same as {@link FakePlayer#setChatEnabled(boolean) setChatEnabled(false)}).
     *
     * @param botUuid bot's UUID
     * @param seconds duration of the mute; 0 = permanent
     */
    public void timedMute(UUID botUuid, int seconds) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) return;

        // Cancel any previous timed mute
        Integer prev = muteTaskIds.remove(botUuid);
        if (prev != null) Bukkit.getScheduler().cancelTask(prev);

        fp.setChatEnabled(false);
        Config.debugChat(fp.getName() + " muted" + (seconds > 0 ? " for " + seconds + "s" : " permanently"));

        if (seconds > 0) {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                muteTaskIds.remove(botUuid);
                FakePlayer b = manager.getByUuid(botUuid);
                if (b != null) {
                    b.setChatEnabled(true);
                    Config.debugChat(b.getName() + " mute expired — chat re-enabled");
                }
            }, (long) seconds * 20L).getTaskId();
            muteTaskIds.put(botUuid, taskId);
        }
    }

    // ── Public static dispatch API ────────────────────────────────────────────

    /**
     * Dispatches a bot chat message through the server's real chat pipeline
     * <b>without</b> the {@code [Not Secure]} label that {@link Player#chat(String)}
     * appends in Paper 1.19+ for unsigned messages.
     *
     * <p>Fires {@link AsyncChatEvent} with {@code async=false} (safe from the main
     * thread) so chat-formatting plugins (EssentialsChat, VentureChat, etc.) receive
     * and process the event normally.  Broadcasting is handled directly via
     * {@link Audience#sendMessage}, bypassing the NMS {@code broadcastChatMessage()}
     * path that writes {@code [Not Secure]} to the console log.
     *
     * <p>Falls back to {@link Player#chat(String)} if Paper API is unavailable.
     *
     * @param player     the bot's live {@link Player} entity
     * @param rawMessage resolved message text (placeholders already substituted)
     */
    public static void dispatchChat(Player player, String rawMessage) {
        try {
            // Initial viewer set: all online players + console.
            Set<Audience> viewers = new LinkedHashSet<>(Bukkit.getOnlinePlayers());
            viewers.add(Bukkit.getConsoleSender());

            Component message = Component.text(rawMessage);

            @SuppressWarnings("UnstableApiUsage")
            AsyncChatEvent event = new AsyncChatEvent(
                    false, player, viewers,
                    ChatRenderer.defaultRenderer(),
                    message, message, null);

            Bukkit.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                Component displayName = player.displayName();
                for (Audience viewer : event.viewers()) {
                    viewer.sendMessage(
                            event.renderer().render(player, displayName, event.message(), viewer));
                }
            }
        } catch (Throwable t) {
            Config.debugChat("AsyncChatEvent dispatch failed (" + t.getMessage()
                    + ") — falling back to player.chat()");
            player.chat(rawMessage);
        }
    }

    /**
     * Broadcasts a bot chat message received from another server via plugin messaging.
     *
     * <p>Uses the configurable {@code fake-chat.remote-format} template.
     *
     * <p><b>Loop prevention:</b> sets {@link #isRemoteBroadcast} so the message is
     * NOT re-forwarded via plugin messaging.
     *
     * @param botName        internal bot name
     * @param botDisplayName display name with prefix / formatting
     * @param message        the resolved message text
     * @param prefix         unused — kept for wire-format compatibility
     * @param suffix         unused — kept for wire-format compatibility
     */
    public static void broadcastRemote(String botName, String botDisplayName,
                                       String message, String prefix, String suffix) {
        if (!Config.fakeChatEnabled()) {
            Config.debugChat("Remote message dropped (bot chat disabled).");
            return;
        }
        isRemoteBroadcast.set(true);
        try {
            broadcastFormatted(botDisplayName, message);
            Config.debugChat("Broadcast remote message from bot '" + botName + "'.");
        } finally {
            isRemoteBroadcast.remove();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when at least one non-bot player is currently online.
     */
    private boolean hasRealPlayerOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (manager.getByUuid(p.getUniqueId()) == null) return true;
        }
        return false;
    }

    /**
     * Resolves the {@code {random_player}} placeholder.
     * Prefers real (non-bot) players; falls back to other bots, then the bot itself.
     */
    private String resolveRandomPlayer(FakePlayer self) {
        List<String> real = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (manager.getByUuid(p.getUniqueId()) == null) real.add(p.getName());
        }
        if (!real.isEmpty())
            return real.get(ThreadLocalRandom.current().nextInt(real.size()));

        List<FakePlayer> others = new ArrayList<>(manager.getActivePlayers());
        others.removeIf(fp -> fp.getUuid().equals(self.getUuid()));
        if (!others.isEmpty())
            return others.get(ThreadLocalRandom.current().nextInt(others.size())).getName();

        return self.getName();
    }
}

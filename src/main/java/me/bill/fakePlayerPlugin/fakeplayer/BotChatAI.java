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
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
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
 *   <li><b>Burst messages</b> — short follow-up a few seconds after the first.</li>
 *   <li><b>Bot-to-bot conversations</b> — bots reply to each other forming multi-turn
 *       chat threads up to {@code fake-chat.bot-to-bot.max-chain} exchanges.</li>
 *   <li><b>Mention replies</b> — when a real player names a bot it may reply.</li>
 *   <li><b>Stagger interval</b> — minimum gap between any two bots chatting.</li>
 *   <li><b>Activity variation</b> — each bot has a random quiet/active tier.</li>
 *   <li><b>History window</b> — per-bot deque avoids repeating recent messages.</li>
 *   <li><b>Event-triggered chat</b> — join, leave, death, advancement, PvP kill,
 *       first join, and XP-level milestones.</li>
 *   <li><b>Keyword reactions</b> — reply when players say configured keywords.</li>
 *   <li><b>Rich placeholders</b> — {@code {name}}, {@code {bot_name}},
 *       {@code {random_player}}, {@code {online}}, {@code {world}}, {@code {time}},
 *       {@code {biome}}, {@code {x}/{y}/{z}}, {@code {server}}, {@code {date}},
 *       {@code {day}}, {@code {killer}}, {@code {victim}}, {@code {advancement}},
 *       {@code {level}}.</li>
 * </ul>
 */
public final class BotChatAI implements Listener {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    /** Thread-local flag — true when re-broadcasting a remote-server message (prevents re-forward). */
    private static final ThreadLocal<Boolean> isRemoteBroadcast = ThreadLocal.withInitial(() -> false);

    /** Per-bot rolling message history — avoids repeating recent messages. */
    private final Map<UUID, Deque<String>> messageHistory     = new ConcurrentHashMap<>();
    /** Task IDs for each bot's main scheduler loop. */
    private final Map<UUID, Integer>       taskIds            = new ConcurrentHashMap<>();
    /** Per-bot activity multiplier (tier-based chat frequency scaling). */
    private final Map<UUID, Double>        activityMultipliers = new ConcurrentHashMap<>();
    /** Pending mention-reply task per bot — only one reply queued at a time. */
    private final Map<UUID, Integer>       pendingReplyTasks  = new ConcurrentHashMap<>();
    /** Pending event-trigger task per bot — only one at a time. */
    private final Map<UUID, Integer>       pendingEventTasks  = new ConcurrentHashMap<>();
    /** Timed-mute expiry tasks. */
    private final Map<UUID, Integer>       muteTaskIds        = new ConcurrentHashMap<>();

    /** Timestamp (ms) of the last time any bot sent a chat message (stagger enforcement). */
    private final AtomicLong lastAnyChatMs     = new AtomicLong(0L);
    /** Timestamp (ms) of the last bot-to-bot exchange (rate-limits conversation starts). */
    private final AtomicLong lastBotToBotMs    = new AtomicLong(0L);

    public BotChatAI(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
        Bukkit.getScheduler().runTaskTimer(plugin, this::syncBotLoops, 20L, 20L);
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
     * Assigns a random activity tier to a bot.
     * If the bot has a {@link FakePlayer#getChatTier()} override that tier is used.
     * <ul>
     *   <li>15 % — quiet   (2.0× interval)</li>
     *   <li>25 % — passive (1.4×)</li>
     *   <li>30 % — normal  (1.0×)</li>
     *   <li>18 % — active  (0.7×)</li>
     *   <li>12 % — chatty  (0.5×)</li>
     * </ul>
     */
    private void assignActivityMultiplier(UUID botUuid) {
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

    private static double tierToMultiplier(String tier) {
        return switch (tier.toLowerCase(Locale.ROOT)) {
            case "quiet"   -> 2.0;
            case "passive" -> 1.4;
            case "active"  -> 0.7;
            case "chatty"  -> 0.5;
            default        -> 1.0;
        };
    }

    public void setActivityTier(UUID botUuid, String tier) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) return;
        fp.setChatTier(tier);
        if (tier == null) {
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
            cancelPendingReply(botUuid);
            cancelPendingEvent(botUuid);
            Integer oldTask = taskIds.remove(botUuid);
            if (oldTask != null) Bukkit.getScheduler().cancelTask(oldTask);
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
        if (bot == null || !bot.isChatEnabled()) return;

        if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatChance()) return;

        List<String> messages = Config.fakeChatMessages();
        if (messages.isEmpty()) return;

        String message = pickMessage(botUuid, messages, bot);

        int delayTicks = computePreSendDelay();
        if (delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendMessage(bot, message, true);
                // After natural chat fires, maybe start a bot-to-bot conversation
                triggerBotToBotReaction(bot, 0);
            }, delayTicks);
        } else {
            sendMessage(bot, message, true);
            triggerBotToBotReaction(bot, 0);
        }
    }

    // ── Bot-to-bot conversation engine ────────────────────────────────────────

    /**
     * Tries to make another bot reply to {@code speaker}, potentially chaining
     * back and forth up to {@code fake-chat.bot-to-bot.max-chain} exchanges.
     *
     * <p>Called only from {@link #fireChat} (depth 0) and recursively from within
     * the b2b reply task (depth 1+) — never from event reactions or burst messages.
     *
     * @param speaker    the bot that just spoke
     * @param chainDepth current depth; 0 = first reply, 1 = second reply, etc.
     */
    private void triggerBotToBotReaction(FakePlayer speaker, int chainDepth) {
        if (!Config.fakeChatBotToBotEnabled()) return;
        if (!Config.fakeChatEnabled()) return;
        if (chainDepth >= Config.fakeChatBotToBotMaxChain()) return;

        // Rate-limit: only one conversation start per cooldown window
        long cooldownMs = Config.fakeChatBotToBotCooldown() * 1000L;
        long now = System.currentTimeMillis();
        if (cooldownMs > 0 && (now - lastBotToBotMs.get()) < cooldownMs) return;

        // Chance roll — use reply-chance for depth 0, chain-chance for continuation
        double chance = (chainDepth == 0)
                ? Config.fakeChatBotToBotReplyChance()
                : Config.fakeChatBotToBotChainChance();
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        // Pick a different chat-enabled bot to respond
        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(fp -> !fp.getUuid().equals(speaker.getUuid()))
                .filter(FakePlayer::isChatEnabled)
                .toList();
        if (eligible.isEmpty()) return;
        if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

        FakePlayer replier = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));

        // Reserve cooldown slot immediately so concurrent callers back off
        lastBotToBotMs.set(now);

        int minTicks = Math.max(20, Config.fakeChatBotToBotDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatBotToBotDelayMax() * 20);
        long delay   = minTicks + ThreadLocalRandom.current().nextInt(Math.max(1, maxTicks - minTicks + 1));

        final UUID speakerUuid = speaker.getUuid();
        final UUID replierUuid = replier.getUuid();
        final int  nextDepth   = chainDepth + 1;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!Config.fakeChatEnabled() || !Config.fakeChatBotToBotEnabled()) return;
            FakePlayer s = manager.getByUuid(speakerUuid);
            FakePlayer b = manager.getByUuid(replierUuid);
            if (b == null || !b.isChatEnabled()) return;
            if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

            // Prefer bot-to-bot-replies pool → replies pool → general messages
            List<String> pool = Config.chatBotToBotReplyMessages();
            if (pool.isEmpty()) pool = Config.chatReplyMessages();
            if (pool.isEmpty()) pool = Config.fakeChatMessages();
            if (pool.isEmpty()) return;

            String speakerName = (s != null) ? s.getName() : b.getName();
            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            // {random_player} resolves to the bot that was just speaking
            String msg = resolvePlaceholders(raw.replace("{random_player}", speakerName), b);

            // Update cooldown for this new exchange
            lastBotToBotMs.set(System.currentTimeMillis());
            sendMessage(b, msg, false);
            Config.debugChat("[b2b chain=" + nextDepth + "] " + b.getName()
                    + " → " + speakerName + ": " + msg);

            // Continue the chain (explicit recursion — NOT via sendMessage/fireChat)
            triggerBotToBotReaction(b, nextDepth);
        }, delay);
    }

    // ── Message picking & placeholder resolution ──────────────────────────────

    /**
     * Picks a non-recently-used message from {@code pool}, resolves all
     * placeholders, and updates the per-bot history deque.
     */
    private String pickMessage(UUID botUuid, List<String> pool, FakePlayer bot) {
        Deque<String> history = messageHistory.computeIfAbsent(botUuid, k -> new ArrayDeque<>());
        int historySize = Math.max(1, Config.fakeChatHistorySize());

        List<String> available = new ArrayList<>(pool);
        available.removeAll(history);
        if (available.isEmpty()) available = new ArrayList<>(pool);

        String raw = available.get(ThreadLocalRandom.current().nextInt(available.size()));

        history.addLast(raw);
        while (history.size() > historySize) history.pollFirst();

        return resolvePlaceholders(raw, bot);
    }

    /**
     * Resolves all chat placeholders in {@code raw}.
     *
     * <p>Supported tokens:
     * <ul>
     *   <li>{@code {name}}          — the bot's own name</li>
     *   <li>{@code {bot_name}}      — a random OTHER active bot's name</li>
     *   <li>{@code {random_player}} — a random real player (falls back to a bot)</li>
     *   <li>{@code {player_name}}   — the specific player being reacted to (pre-substituted
     *                                  by the caller; left as-is here if not pre-subbed)</li>
     *   <li>{@code {online}}        — real (non-bot) player count</li>
     *   <li>{@code {server}}        — server ID from config</li>
     *   <li>{@code {date}}          — today's date (e.g. 2026-04-09)</li>
     *   <li>{@code {day}}           — full day-of-week name</li>
     *   <li>{@code {world}}         — the bot's current world name</li>
     *   <li>{@code {time}}          — "day" or "night"</li>
     *   <li>{@code {biome}}         — the bot's current biome</li>
     *   <li>{@code {x}/{y}/{z}}     — block coordinates</li>
     * </ul>
     * Contextual tokens ({@code {killer}}, {@code {victim}}, {@code {advancement}},
     * {@code {level}}, {@code {player_name}}) are pre-substituted by the caller before
     * this method is invoked.
     */
    private String resolvePlaceholders(String raw, FakePlayer bot) {
        String s = raw
                .replace("{name}", bot.getName())
                .replace("{random_player}", resolveRandomPlayer(bot));

        // {bot_name} — a random OTHER bot's name
        if (s.contains("{bot_name}")) {
            s = s.replace("{bot_name}", resolveRandomBotName(bot));
        }

        // {online}
        if (s.contains("{online}")) {
            int realCount = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (manager.getByUuid(p.getUniqueId()) == null) realCount++;
            }
            s = s.replace("{online}", String.valueOf(realCount));
        }

        // {server}
        if (s.contains("{server}")) {
            s = s.replace("{server}", Config.serverId());
        }

        // {date} / {day}
        if (s.contains("{date}") || s.contains("{day}")) {
            LocalDate today = LocalDate.now();
            if (s.contains("{date}")) s = s.replace("{date}", today.toString());
            if (s.contains("{day}"))  s = s.replace("{day}",
                    today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ROOT));
        }

        // Location-based placeholders
        if (s.contains("{world}") || s.contains("{time}") || s.contains("{biome}")
                || s.contains("{x}") || s.contains("{y}") || s.contains("{z}")) {
            Location loc = bot.getLiveLocation();
            if (loc != null && loc.getWorld() != null) {
                if (s.contains("{world}")) s = s.replace("{world}", loc.getWorld().getName());
                if (s.contains("{time}")) {
                    String timeLabel = (loc.getWorld().getTime() < 12300) ? "day" : "night";
                    s = s.replace("{time}", timeLabel);
                }
                if (s.contains("{biome}")) {
                    try {
                        String biome = loc.getBlock().getBiome().getKey().getKey()
                                .replace('_', ' ');
                        s = s.replace("{biome}", biome);
                    } catch (Throwable t) {
                        s = s.replace("{biome}", "unknown");
                    }
                }
                if (s.contains("{x}")) s = s.replace("{x}", String.valueOf((int) loc.getX()));
                if (s.contains("{y}")) s = s.replace("{y}", String.valueOf((int) loc.getY()));
                if (s.contains("{z}")) s = s.replace("{z}", String.valueOf((int) loc.getZ()));
            } else {
                s = s.replace("{world}", "unknown").replace("{time}", "day")
                     .replace("{biome}", "unknown")
                     .replace("{x}", "?").replace("{y}", "?").replace("{z}", "?");
            }
        }
        return s;
    }

    /**
     * Computes the pre-send delay combining stagger enforcement and typing simulation.
     */
    private int computePreSendDelay() {
        int staggerTicks = 0;
        int staggerSec = Config.fakeChatStaggerInterval();
        if (staggerSec > 0) {
            long now      = System.currentTimeMillis();
            long minGapMs = staggerSec * 1000L;
            long nextSend;
            while (true) {
                long last = lastAnyChatMs.get();
                nextSend  = Math.max(now, last + minGapMs);
                if (lastAnyChatMs.compareAndSet(last, nextSend)) break;
            }
            long extraMs = nextSend - now;
            if (extraMs > 0) staggerTicks = (int)(extraMs / 50L) + 1;
        }

        int typingTicks = 0;
        if (Config.fakeChatTypingDelay()) {
            typingTicks = ThreadLocalRandom.current().nextInt(50);
        }

        return staggerTicks + typingTicks;
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private void sendMessage(FakePlayer bot, String message, boolean allowBurst) {
        if (manager.getByUuid(bot.getUuid()) == null) return;
        if (!Config.fakeChatEnabled()) return;
        if (!bot.isChatEnabled()) return;
        sendMessageForced(bot, message, allowBurst);
    }

    private void sendMessageForced(FakePlayer bot, String message, boolean allowBurst) {
        if (manager.getByUuid(bot.getUuid()) == null) return;

        Player playerEntity = bot.getPlayer();
        if (playerEntity != null && playerEntity.isOnline() && !bot.isBodyless()) {
            dispatchChat(playerEntity, message);
        } else {
            broadcastFormatted(bot.getDisplayName(), message);
        }
        Config.debugChat(bot.getName() + " said: " + message);

        // Cross-server sync
        var vc = plugin.getVelocityChannel();
        if (vc != null) {
            vc.sendChatToNetwork(bot.getName(), bot.getDisplayName(), message, "", "");
        }

        // Burst follow-up
        if (allowBurst) {
            double burstChance = Config.fakeChatBurstChance();
            if (burstChance > 0 && ThreadLocalRandom.current().nextDouble() < burstChance) {
                scheduleBurst(bot);
            }
        }
    }

    private static void broadcastFormatted(String displayName, String message) {
        try {
            String format = Config.fakeChatRemoteFormat()
                    .replace("{name}", displayName)
                    .replace("{message}", message);
            Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(format));
        } catch (Throwable t) {
            Bukkit.getServer().broadcast(Component.text("<")
                    .append(TextUtil.colorize(displayName))
                    .append(Component.text("> "))
                    .append(Component.text(message)));
        }
    }

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

        Bukkit.getScheduler().runTaskLater(plugin, () -> sendMessage(bot, msg, false), delay);
    }

    // ── Mention detection ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        if (!Config.fakeChatEnabled()) return;
        // Ignore messages from bots (prevents infinite loops)
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;

        final String plainText;
        try {
            plainText = PlainTextComponentSerializer.plainText()
                    .serialize(event.message()).toLowerCase(Locale.ROOT);
        } catch (Throwable t) { return; }

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
                List<String> matchedPools = new ArrayList<>();
                for (Map.Entry<String, String> entry : keywordMap.entrySet()) {
                    if (plainText.contains(entry.getKey())) matchedPools.add(entry.getValue());
                }
                if (!matchedPools.isEmpty()) {
                    String poolKey = matchedPools.get(
                            ThreadLocalRandom.current().nextInt(matchedPools.size()));
                    Bukkit.getScheduler().runTask(plugin, () -> scheduleKeywordReaction(poolKey));
                }
            }
        }

        // ── Player chat reactions ──────────────────────────────────────────────
        // A random bot may spontaneously react to any real player's message.
        // Filtered by chance, length, and command-prefix guards. Delayed with
        // typing simulation for a human feel.
        if (Config.fakeChatOnPlayerChatEnabled()
                && Config.fakeChatEventTriggersEnabled()) {
            // Filter: skip very short messages (e.g. "k", "ok")
            if (Config.fakeChatOnPlayerChatIgnoreShort() && plainText.length() < 3) return;
            // Filter: skip slash-commands
            if (Config.fakeChatOnPlayerChatIgnoreCommands() && plainText.startsWith("/")) return;

            final String senderName = event.getPlayer().getName();
            // Capture original (non-lowercased) message text for the AI prompt
            final String originalMessage;
            try {
                originalMessage = PlainTextComponentSerializer.plainText()
                        .serialize(event.message());
            } catch (Throwable t) { return; }

            Bukkit.getScheduler().runTask(plugin, () ->
                    schedulePlayerChatReaction(senderName, originalMessage));
        }
    }

    private void scheduleMentionReply(UUID botUuid) {
        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null || !bot.isChatEnabled()) return;
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

            List<String> pool = Config.chatReplyMessages();
            if (pool.isEmpty()) pool = Config.fakeChatMessages();
            if (pool.isEmpty()) return;

            String raw   = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String reply = resolvePlaceholders(raw, b);
            sendMessage(b, reply, false);
            Config.debugChat(b.getName() + " replied to mention → " + reply);
        }, delay).getTaskId();

        pendingReplyTasks.put(botUuid, taskId);
    }

    private void scheduleKeywordReaction(String poolKey) {
        if (!Config.fakeChatEnabled()) return;
        List<String> pool = Config.chatKeywordReactionMessages(poolKey);
        if (pool.isEmpty()) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled).toList();
        if (eligible.isEmpty()) return;
        if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        int delayTicks  = ThreadLocalRandom.current().nextInt(60) + 20;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;
            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            sendMessage(b, resolvePlaceholders(raw, b), false);
            Config.debugChat(b.getName() + " keyword-reaction [" + poolKey + "]");
        }, delayTicks);
    }

    /**
     * Handles the "player chat reaction" feature: when a real player sends any chat
     * message, a random bot may spontaneously react with a short natural response.
     *
     * <p>When {@code fake-chat.event-triggers.on-player-chat.use-ai: true} and an AI
     * provider is configured, the bot generates a contextual response via the AI using
     * the player's actual message as input. Otherwise falls back to the static
     * {@code player-chat-reactions} pool in {@code bot-messages.yml}.
     *
     * @param senderName    the Minecraft username of the player who sent the message
     * @param originalMessage the original (unmodified) plain-text message content
     */
    private void schedulePlayerChatReaction(String senderName, String originalMessage) {
        if (!Config.fakeChatEnabled()) return;

        // Gather eligible bots (chat-enabled, not already mid-reply)
        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .filter(fp -> !pendingReplyTasks.containsKey(fp.getUuid()))
                .toList();
        if (eligible.isEmpty()) return;

        int maxBots = Math.max(1, Config.fakeChatOnPlayerChatMaxBots());
        double chance = Config.fakeChatOnPlayerChatChance();
        double mentionChance = Config.fakeChatOnPlayerChatMentionChance();
        boolean useAi = Config.fakeChatOnPlayerChatUseAi();

        // Check AI availability once (avoid repeated lookups in the loop)
        me.bill.fakePlayerPlugin.ai.BotConversationManager convMgr =
                useAi ? plugin.getBotConversationManager() : null;
        boolean aiAvailable = convMgr != null
                && plugin.getBotConversationManager() != null;

        // Shuffle so we don't always pick the same bots
        List<FakePlayer> shuffled = new ArrayList<>(eligible);
        java.util.Collections.shuffle(shuffled);

        int scheduled = 0;
        for (FakePlayer bot : shuffled) {
            if (scheduled >= maxBots) break;
            if (ThreadLocalRandom.current().nextDouble() > chance) continue;

            final UUID botUuid = bot.getUuid();
            int minTicks = Math.max(20, Config.fakeChatOnPlayerChatDelayMin() * 20);
            int maxTicks = Math.max(minTicks, Config.fakeChatOnPlayerChatDelayMax() * 20);
            long jitter  = minTicks == maxTicks ? 0
                    : ThreadLocalRandom.current().nextInt(maxTicks - minTicks + 1);
            // Stagger multiple bots so they don't all fire at the same tick
            long stagger = scheduled * (10L + ThreadLocalRandom.current().nextInt(10));
            long delay   = minTicks + jitter + stagger;

            if (aiAvailable) {
                // ── AI path ───────────────────────────────────────────────────
                // Capture the message and schedule a delayed task that calls
                // generatePublicChatReaction; the future resolves asynchronously
                // (the AI HTTP call) and we then switch back to main thread to send.
                final me.bill.fakePlayerPlugin.ai.BotConversationManager mgr = convMgr;
                final FakePlayer capturedBot = bot;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!Config.fakeChatEnabled()) return;
                    FakePlayer b = manager.getByUuid(botUuid);
                    if (b == null || !b.isChatEnabled()) return;
                    if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

                    mgr.generatePublicChatReaction(b, senderName, originalMessage)
                            .thenAccept(aiResponse -> {
                                // Strip surrounding quotes the AI sometimes adds
                                String clean = aiResponse.trim()
                                        .replaceAll("^\"|\"$", "")
                                        .replaceAll("^'|'$", "")
                                        .trim();
                                if (clean.isEmpty()) return;
                                // Back to main thread to send the message
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    FakePlayer finalBot = manager.getByUuid(botUuid);
                                    if (finalBot == null || !finalBot.isChatEnabled()) return;
                                    sendMessage(finalBot, clean, false);
                                    Config.debugChat(finalBot.getName()
                                            + " ai-player-chat-reaction → " + senderName
                                            + ": " + clean);
                                });
                            })
                            .exceptionally(err -> {
                                // AI unavailable / rate-limited — fall back to static pool
                                Config.debugChat(b.getName() + " ai-reaction failed ("
                                        + err.getMessage() + ") — falling back to static pool");
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sendStaticPlayerChatReaction(botUuid, senderName, mentionChance));
                                return null;
                            });
                }, delay);
            } else {
                // ── Static pool path ──────────────────────────────────────────
                final boolean doMention = ThreadLocalRandom.current().nextDouble() < mentionChance;
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        sendStaticPlayerChatReaction(botUuid, senderName, doMention ? 1.0 : 0.0),
                        delay);
            }

            scheduled++;
        }
    }

    /**
     * Picks a message from the static {@code player-chat-reactions} pool and sends it.
     * Called both when AI is disabled and as a fallback when the AI call fails.
     */
    private void sendStaticPlayerChatReaction(UUID botUuid, String senderName,
                                               double mentionChance) {
        if (!Config.fakeChatEnabled()) return;
        FakePlayer b = manager.getByUuid(botUuid);
        if (b == null || !b.isChatEnabled()) return;
        if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;

        List<String> pool = Config.chatPlayerChatReactionMessages();
        if (pool.isEmpty()) return;

        boolean doMention = ThreadLocalRandom.current().nextDouble() < mentionChance;
        List<String> filtered;
        if (doMention) {
            filtered = pool.stream().filter(m -> m.contains("{player_name}")).toList();
            if (filtered.isEmpty()) filtered = pool;
        } else {
            filtered = pool.stream().filter(m -> !m.contains("{player_name}")).toList();
            if (filtered.isEmpty()) filtered = pool;
        }

        String raw = filtered.get(ThreadLocalRandom.current().nextInt(filtered.size()));
        String resolved = resolvePlaceholders(raw.replace("{player_name}", senderName), b);
        sendMessage(b, resolved, false);
        Config.debugChat(b.getName() + " static-player-chat-reaction → " + senderName
                + ": " + resolved);
    }

    // ── Event-triggered chat ──────────────────────────────────────────────────

    /**
     * Reacts to a player joining. Distinguishes brand-new (first-join) players
     * and gives them a special welcome using the {@code first-join-reactions} pool.
     * For first joins, a second bot may also chime in shortly after.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;

        boolean isFirstJoin = !event.getPlayer().hasPlayedBefore();

        // Choose pool and chance based on first-join vs. regular join
        final List<String> pool;
        final double chance;
        if (isFirstJoin && Config.fakeChatOnFirstJoinEnabled()) {
            List<String> fjPool = Config.chatFirstJoinReactionMessages();
            pool   = fjPool.isEmpty() ? Config.chatJoinReactionMessages() : fjPool;
            chance = Config.fakeChatOnFirstJoinChance();
        } else {
            if (!Config.fakeChatOnJoinEnabled()) return;
            pool   = Config.chatJoinReactionMessages();
            chance = Config.fakeChatOnJoinChance();
        }
        if (pool.isEmpty()) return;
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled).toList();
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
            String msg = resolvePlaceholders(raw.replace("{random_player}", joinerName), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + (isFirstJoin ? " first-join-reaction" : " join-reaction")
                    + " for " + joinerName + ": " + msg);

            // For first joins: maybe a second bot also welcomes the newcomer
            if (isFirstJoin && manager.getActivePlayers().size() >= 2
                    && ThreadLocalRandom.current().nextDouble() < 0.50) {
                List<FakePlayer> others = manager.getActivePlayers().stream()
                        .filter(FakePlayer::isChatEnabled)
                        .filter(fp -> !fp.getUuid().equals(b.getUuid()))
                        .toList();
                if (!others.isEmpty()) {
                    FakePlayer second = others.get(
                            ThreadLocalRandom.current().nextInt(others.size()));
                    long extraDelay = 20L + ThreadLocalRandom.current().nextInt(60);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        FakePlayer sb = manager.getByUuid(second.getUuid());
                        if (sb == null || !sb.isChatEnabled()) return;
                        List<String> p2 = Config.chatJoinReactionMessages();
                        if (p2.isEmpty()) return;
                        String r2 = p2.get(ThreadLocalRandom.current().nextInt(p2.size()));
                        String m2 = resolvePlaceholders(r2.replace("{random_player}", joinerName), sb);
                        sendMessage(sb, m2, false);
                    }, extraDelay);
                }
            }
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Reacts to entity deaths. When the deceased is a player killed by another real player,
     * the kill-reactions pool may ALSO fire (see {@link #onPlayerKill}).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnDeathEnabled()) return;

        Entity deceased = event.getEntity();
        if (Config.fakeChatOnDeathPlayersOnly() && !(deceased instanceof Player)) return;
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnDeathChance()) return;

        List<String> pool = Config.chatDeathReactionMessages();
        if (pool.isEmpty()) return;

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
                    raw.replace("{random_player}", deceasedName)
                       .replace("{victim}", deceasedName), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " death-reaction for " + deceasedName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Reacts specifically to a real player killing another real player.
     * Uses the {@code kill-reactions} pool with {@code {killer}} and {@code {victim}} placeholders.
     * Fires independently from the general death reaction — both can occur for the same event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnKillEnabled()) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // Ignore bot-on-player or player-on-bot kills
        if (manager.getByUuid(killer.getUniqueId()) != null) return;
        if (manager.getByUuid(event.getEntity().getUniqueId()) != null) return;

        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnKillChance()) return;

        List<String> pool = Config.chatKillReactionMessages();
        if (pool.isEmpty()) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .filter(fp -> !pendingEventTasks.containsKey(fp.getUuid()))
                .toList();
        if (eligible.isEmpty()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        final String killerName = killer.getName();
        final String victimName = event.getEntity().getName();

        int minTicks = Math.max(20, Config.fakeChatOnKillDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatOnKillDelayMax() * 20);
        long delay   = minTicks + ThreadLocalRandom.current().nextInt(Math.max(1, maxTicks - minTicks + 1));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEventTasks.remove(bot.getUuid());
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;
            if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;
            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String msg = resolvePlaceholders(
                    raw.replace("{random_player}", killerName)
                       .replace("{killer}", killerName)
                       .replace("{victim}", victimName), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " kill-reaction: " + killerName
                    + " killed " + victimName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Reacts when a player earns an advancement that is announced to chat.
     * Supports {@code {advancement}} and {@code {random_player}} placeholders.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnAdvancementEnabled()) return;
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;

        // Only react to advancements that show in chat
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (display == null || !display.doesAnnounceToChat()) return;

        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnAdvancementChance()) return;

        List<String> pool = Config.chatAdvancementReactionMessages();
        if (pool.isEmpty()) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .filter(fp -> !pendingEventTasks.containsKey(fp.getUuid()))
                .toList();
        if (eligible.isEmpty()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        final String playerName = event.getPlayer().getName();
        final String advTitle;
        try {
            advTitle = PlainTextComponentSerializer.plainText().serialize(display.title());
        } catch (Throwable t) { return; }

        int minTicks = Math.max(20, Config.fakeChatOnAdvancementDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatOnAdvancementDelayMax() * 20);
        long delay   = minTicks + ThreadLocalRandom.current().nextInt(Math.max(1, maxTicks - minTicks + 1));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEventTasks.remove(bot.getUuid());
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;
            if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;
            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String msg = resolvePlaceholders(
                    raw.replace("{random_player}", playerName)
                       .replace("{advancement}", advTitle), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " advancement-reaction [" + advTitle + "] for "
                    + playerName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Reacts when a player hits a significant XP level milestone (30, 50, 75, 100, …).
     * Supports {@code {level}} and {@code {random_player}} placeholders.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnHighLevelEnabled()) return;
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;

        int newLevel   = event.getNewLevel();
        int minLevel   = Config.fakeChatOnHighLevelMinLevel();
        if (newLevel < minLevel) return;

        // Only react at milestone levels: min-level, then every 25 above that, plus 50/75/100
        boolean isMilestone = (newLevel == minLevel)
                || (newLevel >= 50 && newLevel % 25 == 0);
        if (!isMilestone) return;

        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnHighLevelChance()) return;

        List<String> pool = Config.chatHighLevelReactionMessages();
        if (pool.isEmpty()) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled)
                .filter(fp -> !pendingEventTasks.containsKey(fp.getUuid()))
                .toList();
        if (eligible.isEmpty()) return;

        FakePlayer bot = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        final String playerName = event.getPlayer().getName();
        final String levelStr   = String.valueOf(newLevel);

        int minTicks = Math.max(20, Config.fakeChatOnHighLevelDelayMin() * 20);
        int maxTicks = Math.max(minTicks, Config.fakeChatOnHighLevelDelayMax() * 20);
        long delay   = minTicks + ThreadLocalRandom.current().nextInt(Math.max(1, maxTicks - minTicks + 1));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEventTasks.remove(bot.getUuid());
            FakePlayer b = manager.getByUuid(bot.getUuid());
            if (b == null || !b.isChatEnabled()) return;
            if (Config.fakeChatRequirePlayer() && !hasRealPlayerOnline()) return;
            String raw = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            String msg = resolvePlaceholders(
                    raw.replace("{random_player}", playerName)
                       .replace("{level}", levelStr), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " level-reaction [lvl " + levelStr + "] for "
                    + playerName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    /**
     * Reacts to a real player leaving.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!Config.fakeChatEnabled()) return;
        if (!Config.fakeChatEventTriggersEnabled()) return;
        if (!Config.fakeChatOnLeaveEnabled()) return;
        if (manager.getByUuid(event.getPlayer().getUniqueId()) != null) return;
        if (ThreadLocalRandom.current().nextDouble() > Config.fakeChatOnLeaveChance()) return;

        List<String> pool = Config.chatLeaveReactionMessages();
        if (pool.isEmpty()) return;

        List<FakePlayer> eligible = manager.getActivePlayers().stream()
                .filter(FakePlayer::isChatEnabled).toList();
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
            String msg = resolvePlaceholders(raw.replace("{random_player}", leaverName), b);
            sendMessage(b, msg, false);
            Config.debugChat(b.getName() + " leave-reaction for " + leaverName + ": " + msg);
        }, delay).getTaskId();

        pendingEventTasks.put(bot.getUuid(), taskId);
    }

    // ── Cancellation helpers ──────────────────────────────────────────────────

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

    // ── Public API ────────────────────────────────────────────────────────────

    public void restartLoops() {
        taskIds.values().forEach(Bukkit.getScheduler()::cancelTask);
        taskIds.clear();
        for (FakePlayer fp : manager.getActivePlayers()) {
            assignActivityMultiplier(fp.getUuid());
            scheduleNext(fp.getUuid());
        }
        Config.debugChat("BotChatAI loops restarted - interval "
                + Config.fakeChatIntervalMin() + "–" + Config.fakeChatIntervalMax() + "s, "
                + "stagger " + Config.fakeChatStaggerInterval() + "s, "
                + "b2b " + (Config.fakeChatBotToBotEnabled() ? "on" : "off"));
    }

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

    public void stopAllLoopsNow() {
        taskIds.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingReplyTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingEventTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        taskIds.clear();
        pendingReplyTasks.clear();
        pendingEventTasks.clear();
        for (FakePlayer fp : manager.getActivePlayers()) {
            scheduleNext(fp.getUuid());
        }
    }

    public void forceSendMessage(FakePlayer bot, String message) {
        if (manager.getByUuid(bot.getUuid()) == null) return;
        sendMessageForced(bot, message, false);
    }

    public void forceSendMessageResolved(FakePlayer bot, String message) {
        if (manager.getByUuid(bot.getUuid()) == null) return;
        sendMessageForced(bot, resolvePlaceholders(message, bot), false);
    }

    public double getActivityMultiplier(UUID botUuid) {
        return activityMultipliers.getOrDefault(botUuid, 1.0);
    }

    public void timedMute(UUID botUuid, int seconds) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) return;

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
                    Config.debugChat(b.getName() + " mute expired - chat re-enabled");
                }
            }, (long) seconds * 20L).getTaskId();
            muteTaskIds.put(botUuid, taskId);
        }
    }

    // ── Static dispatch helpers ───────────────────────────────────────────────

    /**
     * Dispatches a bot chat message through the real chat pipeline without the
     * {@code [Not Secure]} label. Falls back to {@link Player#chat} if Paper API fails.
     */
    public static void dispatchChat(Player player, String rawMessage) {
        try {
            Set<Audience> viewers = new LinkedHashSet<>(Bukkit.getOnlinePlayers());
            viewers.add(Bukkit.getConsoleSender());
            Component message = Component.text(rawMessage);

            @SuppressWarnings("UnstableApiUsage")
            AsyncChatEvent event = new AsyncChatEvent(
                    false, player, viewers, ChatRenderer.defaultRenderer(),
                    message, message, null);

            Bukkit.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                Component displayName = player.displayName();
                for (Audience viewer : event.viewers()) {
                    viewer.sendMessage(event.renderer().render(player, displayName, event.message(), viewer));
                }
            }
        } catch (Throwable t) {
            Config.debugChat("AsyncChatEvent dispatch failed (" + t.getMessage()
                    + ") - falling back to player.chat()");
            player.chat(rawMessage);
        }
    }

    /**
     * Broadcasts a remote (cross-server) bot message. Loop-prevention via
     * {@link #isRemoteBroadcast}.
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

    // ── Internal helpers ──────────────────────────────────────────────────────

    private boolean hasRealPlayerOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (manager.getByUuid(p.getUniqueId()) == null) return true;
        }
        return false;
    }

    /**
     * Resolves {@code {random_player}}: prefers real players, falls back to other bots,
     * then the bot itself.
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

    /**
     * Resolves {@code {bot_name}}: returns a random OTHER active bot's name,
     * or the bot's own name if no others are online.
     */
    private String resolveRandomBotName(FakePlayer self) {
        List<FakePlayer> others = manager.getActivePlayers().stream()
                .filter(fp -> !fp.getUuid().equals(self.getUuid()))
                .toList();
        if (!others.isEmpty())
            return others.get(ThreadLocalRandom.current().nextInt(others.size())).getName();
        return self.getName();
    }
}


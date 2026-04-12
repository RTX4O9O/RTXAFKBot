package me.bill.fakePlayerPlugin.ai;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages AI-powered direct message conversations between bots and players.
 *
 * <p>Each bot-player pair maintains a conversation history window for context.
 * Bots respond to /msg, /tell, /whisper commands with AI-generated replies.
 */
public final class BotConversationManager {

    private final FakePlayerPlugin plugin;
    private final AIProviderRegistry aiRegistry;

    /** Per-bot conversation history: botUUID → (playerUUID → message history) */
    private final Map<UUID, Map<UUID, Deque<AIProvider.ChatMessage>>> conversations = new ConcurrentHashMap<>();

    /** Per-bot personality prompts (can be set via command) */
    private final Map<UUID, String> botPersonalities = new ConcurrentHashMap<>();

    /**
     * Per-bot named personality assignments.
     * Only populated when a personality is assigned via
     * {@link #setBotPersonalityByName} — raw-text assignments leave this empty.
     */
    private final Map<UUID, String> botPersonalityNames = new ConcurrentHashMap<>();

    /** Rate limiter: (botUUID, playerUUID) → last response timestamp */
    private final Map<String, Long> lastResponseTimes = new ConcurrentHashMap<>();

    public BotConversationManager(FakePlayerPlugin plugin, AIProviderRegistry aiRegistry) {
        this.plugin = plugin;
        this.aiRegistry = aiRegistry;
    }

    /**
     * Handles a direct message sent to a bot from a player.
     * Generates an AI response and sends it back via /msg.
     *
     * @param bot       the bot receiving the message
     * @param sender    the player who sent the message
     * @param message   the message content
     */
    public void handleDirectMessage(FakePlayer bot, Player sender, String message) {
        if (!aiRegistry.isAvailable()) {
            if (Config.aiConversationsDebug()) {
                plugin.getLogger().warning("[AI] No provider available — cannot respond to DM");
            }
            return;
        }

        if (!Config.aiConversationsEnabled()) {
            return;
        }

        // Rate limit check
        String key = bot.getUuid() + ":" + sender.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = Config.aiConversationsCooldown() * 1000L;
        Long lastResponse = lastResponseTimes.get(key);
        if (lastResponse != null && (now - lastResponse) < cooldown) {
            if (Config.aiConversationsDebug()) {
                plugin.getLogger().info("[AI] Rate limit active for " + sender.getName()
                        + " → " + bot.getName());
            }
            return;
        }

        // Update conversation history
        Map<UUID, Deque<AIProvider.ChatMessage>> botConversations =
                conversations.computeIfAbsent(bot.getUuid(), k -> new ConcurrentHashMap<>());
        Deque<AIProvider.ChatMessage> history =
                botConversations.computeIfAbsent(sender.getUniqueId(), k -> new ArrayDeque<>());

        // Add user message
        history.addLast(new AIProvider.ChatMessage("user", message));

        // Trim history to max window
        int maxHistory = Config.aiConversationsMaxHistory();
        while (history.size() > maxHistory * 2) { // *2 because user + assistant pairs
            history.pollFirst();
        }

        // Build message list for AI
        List<AIProvider.ChatMessage> messageList = new ArrayList<>(history);

        // Get bot personality via shared helper
        String personality = resolvePersonality(bot);

        // Generate response async
        aiRegistry.generateResponse(messageList, bot.getName(), personality)
                .thenAccept(response -> {
                    // Calculate typing delay: base + (chars * per-char), capped at max
                    long delayTicks = 0;
                    if (Config.aiTypingDelayEnabled()) {
                        double delaySecs = Config.aiTypingDelayBase()
                                + (response.length() * Config.aiTypingDelayPerChar());
                        delaySecs = Math.min(delaySecs, Config.aiTypingDelayMax());
                        delayTicks = Math.max(1L, Math.round(delaySecs * 20)); // 20 ticks/sec
                    }

                    // Schedule reply on main thread (with typing delay)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (sender.isOnline()) {
                            // Add assistant response to history
                            history.addLast(new AIProvider.ChatMessage("assistant", response));

                            // Send reply via /msg
                            sendBotReply(bot, sender, response);

                            // Update rate limit
                            lastResponseTimes.put(key, System.currentTimeMillis());

                            if (Config.aiConversationsDebug()) {
                                plugin.getLogger().info("[AI] " + bot.getName() + " → "
                                        + sender.getName() + ": " + response);
                            }
                        }
                    }, delayTicks);
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[AI] Failed to generate response for "
                            + bot.getName() + ": " + ex.getMessage());
                    if (Config.aiConversationsDebug()) {
                        ex.printStackTrace();
                    }
                    return null;
                });
    }

    /**
     * Sends a bot's reply to a player via the /msg command.
     */
    private void sendBotReply(FakePlayer bot, Player recipient, String message) {
        Player botPlayer = bot.getPlayer();
        if (botPlayer != null && botPlayer.isOnline()) {
            // Use Bukkit's built-in /msg command
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "minecraft:tellraw " + recipient.getName()
                            + " [{\"text\":\"[" + bot.getName() + " → me] \",\"color\":\"gray\"},"
                            + "{\"text\":\"" + escapeJson(message) + "\",\"color\":\"white\"}]"
            );
        }
    }

    /**
     * Generates an AI response to something a real player said in <b>public chat</b>.
     * Unlike {@link #handleDirectMessage}, this method does NOT send anything itself —
     * the returned {@link java.util.concurrent.CompletableFuture} resolves to the raw
     * response text, and the caller (e.g. {@code BotChatAI}) is responsible for
     * broadcasting it to public chat via the bot entity.
     *
     * <p>Rate-limited per bot (not per bot-player pair).  The cooldown is read from
     * {@code fake-chat.event-triggers.on-player-chat.ai-cooldown} in config.
     *
     * @param bot           the reacting bot
     * @param playerName    the Minecraft username of the player who chatted
     * @param playerMessage the plain-text content the player sent
     * @return a future that resolves to the AI reply, or fails when rate-limited /
     *         AI unavailable (callers should fall back to the static pool on failure)
     */
    public java.util.concurrent.CompletableFuture<String> generatePublicChatReaction(
            FakePlayer bot, String playerName, String playerMessage) {

        if (!aiRegistry.isAvailable() || !Config.aiConversationsEnabled()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("AI not available or disabled"));
        }

        // Per-bot rate limit — uses a "pc-react:" key prefix separate from DM cooldowns
        String rateKey = "pc-react:" + bot.getUuid();
        long now = System.currentTimeMillis();
        int cooldownSec = Config.fakeChatOnPlayerChatAiCooldown();
        Long lastTime = lastResponseTimes.get(rateKey);
        if (lastTime != null && (now - lastTime) < cooldownSec * 1000L) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("Rate limited"));
        }
        // Reserve the slot immediately so concurrent calls don't all fire at once
        lastResponseTimes.put(rateKey, now);

        // Build personality: same priority as DMs (runtime override → persistent → config default)
        String personality = resolvePersonality(bot);

        // Augment the personality with public-chat context instructions.
        // These override the DM-style prompt without removing the bot's persona.
        String chatPersonality = personality
                + "\n\nCURRENT CONTEXT: You are in a Minecraft server's PUBLIC CHAT."
                + " Player " + playerName + " just said: \"" + playerMessage + "\"."
                + " React naturally as another player would in game chat."
                + " STRICT: 1-8 words max. No full sentences. No quotes around your response."
                + " Sound like a casual Minecraft player, not an AI assistant."
                + " Lowercase preferred. Optional: 1 typo. Match the energy of what they said.";

        // Single-turn: just the player's message as context
        List<AIProvider.ChatMessage> messages = List.of(
                new AIProvider.ChatMessage("user", playerName + ": " + playerMessage)
        );

        if (Config.aiConversationsDebug()) {
            plugin.getLogger().info("[AI][pc-react] " + bot.getName()
                    + " reacting to " + playerName + ": " + playerMessage);
        }

        return aiRegistry.generateResponse(messages, bot.getName(), chatPersonality)
                .whenComplete((result, err) -> {
                    if (err != null) {
                        // Release the rate-limit slot so a fallback can be shown immediately
                        lastResponseTimes.remove(rateKey);
                        if (Config.aiConversationsDebug()) {
                            plugin.getLogger().warning("[AI][pc-react] Failed for "
                                    + bot.getName() + ": " + err.getMessage());
                        }
                    } else if (Config.aiConversationsDebug()) {
                        plugin.getLogger().info("[AI][pc-react] " + bot.getName()
                                + " → " + result);
                    }
                });
    }

    /**
     * Resolves the bot's effective personality prompt (runtime override → persistent
     * per-bot name → config default), substitutes {@code {bot_name}}, and returns
     * the final system prompt string.
     */
    private String resolvePersonality(FakePlayer bot) {
        String personality;
        if (botPersonalities.containsKey(bot.getUuid())) {
            personality = botPersonalities.get(bot.getUuid());
        } else if (bot.getAiPersonality() != null) {
            String text = plugin.getPersonalityRepository() != null
                    ? plugin.getPersonalityRepository().get(bot.getAiPersonality())
                    : null;
            personality = (text != null) ? text : Config.aiConversationsDefaultPersonality();
        } else {
            personality = Config.aiConversationsDefaultPersonality();
        }
        return personality.replace("{bot_name}", bot.getName());
    }

    /**
     * Sets a raw personality prompt for a bot.
     * Clears any named personality association for this bot.
     *
     * @param personality the raw prompt text, or {@code null} / blank to reset to default
     */
    public void setBotPersonality(UUID botUuid, String personality) {
        if (personality == null || personality.isBlank()) {
            botPersonalities.remove(botUuid);
            botPersonalityNames.remove(botUuid);
        } else {
            botPersonalities.put(botUuid, personality);
            botPersonalityNames.remove(botUuid); // raw assignment — no display name
        }
    }

    /**
     * Assigns a personality to a bot by name, looking up the prompt text from
     * the {@link PersonalityRepository}.
     *
     * @param botUuid         the bot's UUID
     * @param personalityName the personality file name (without {@code .txt}, case-insensitive)
     * @param repo            the loaded repository to resolve the text from
     * @return {@code true} if the personality was found and applied
     */
    public boolean setBotPersonalityByName(UUID botUuid, String personalityName,
                                           PersonalityRepository repo) {
        String text = repo.get(personalityName);
        if (text == null) return false;
        botPersonalities.put(botUuid, text);
        botPersonalityNames.put(botUuid, personalityName.toLowerCase(java.util.Locale.ROOT));
        return true;
    }

    /**
     * Gets a bot's current personality (or null if using default).
     */
    public String getBotPersonality(UUID botUuid) {
        return botPersonalities.get(botUuid);
    }

    /**
     * Returns the named personality assigned to a bot, or {@code null} if the
     * bot uses the default personality or was set via raw text.
     *
     * @param botUuid the bot's UUID
     * @return the personality file name (lower-cased), or {@code null}
     */
    public String getBotPersonalityName(UUID botUuid) {
        return botPersonalityNames.get(botUuid);
    }

    /**
     * Clears conversation history for a specific bot-player pair.
     */
    public void clearConversation(UUID botUuid, UUID playerUuid) {
        Map<UUID, Deque<AIProvider.ChatMessage>> botConvos = conversations.get(botUuid);
        if (botConvos != null) {
            botConvos.remove(playerUuid);
        }
        lastResponseTimes.remove(botUuid + ":" + playerUuid);
    }

    /**
     * Clears all conversations for a bot (e.g., when bot is deleted).
     */
    public void clearBotConversations(UUID botUuid) {
        conversations.remove(botUuid);
        botPersonalities.remove(botUuid);
        botPersonalityNames.remove(botUuid);
        // Clear rate limiters
        lastResponseTimes.keySet().removeIf(key -> key.startsWith(botUuid.toString()));
    }

    /**
     * Clears all conversations (e.g., on plugin disable).
     */
    public void clearAll() {
        conversations.clear();
        botPersonalities.clear();
        botPersonalityNames.clear();
        lastResponseTimes.clear();
    }

    /**
     * Gets conversation history size for a bot-player pair.
     */
    public int getConversationSize(UUID botUuid, UUID playerUuid) {
        Map<UUID, Deque<AIProvider.ChatMessage>> botConvos = conversations.get(botUuid);
        if (botConvos == null) return 0;
        Deque<AIProvider.ChatMessage> history = botConvos.get(playerUuid);
        return history != null ? history.size() : 0;
    }

    /**
     * Escapes a string for JSON (basic implementation).
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}


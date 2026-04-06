package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Handles join, leave and kill message broadcasting for fake players.
 *
 * <p>Messages are sent directly to every online player and to the console,
 * matching exactly what the vanilla server does after firing
 * {@code PlayerJoinEvent} / {@code PlayerQuitEvent}.
 *
 * <p>The message text comes from {@code language/en.yml} keys
 * {@code bot-join}, {@code bot-leave} and {@code bot-kill}, so admins can
 * fully customise the wording and colours via the language file.
 */
public final class BotBroadcast {

    private BotBroadcast() {}

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Sends {@code msg} to every online player and to the console —
     * the same delivery path vanilla Paper uses for real join/leave messages.
     */
    private static void send(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    /**
     * Converts a raw display-name string (which may contain MiniMessage tags,
     * LuckPerms gradient shorthand {@code {#RRGGBB>}text{#RRGGBB<}}, or legacy
     * {@code §}-codes) into a {@link Component}.
     * Delegates entirely to {@link TextUtil#colorize} which handles all three
     * formats in the correct order.
     */
    private static Component parseDisplayName(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return TextUtil.colorize(raw);
    }

    /**
     * Builds the broadcast Component by:
     * 1. Getting the raw lang template (e.g. {@code "<yellow>{name} joined the game"}).
     * 2. Splitting on the {@code {name}} placeholder.
     * 3. Parsing each half, then inserting the pre-parsed display-name Component.
     * This avoids embedding a raw display-name string directly into a MiniMessage
     * template, which would corrupt gradient/colour tags or §-codes.
     */
    private static Component buildMessage(String langKey, String displayName,
                                          String... extraArgs) {
        String template = Lang.raw(langKey, extraArgs);
        // Replace {name} sentinel with a unique placeholder we control
        final String SENTINEL = "\u0000NAME\u0000";
        String withSentinel = template.replace("{name}", SENTINEL);

        // Split on our sentinel
        String[] parts = withSentinel.split(SENTINEL, -1);
        Component result = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            result = result.append(TextUtil.colorize(parts[i]));
            if (i < parts.length - 1) {
                result = result.append(parseDisplayName(displayName));
            }
        }
        return result;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Builds the join {@link Component} from {@code bot-join} in {@code en.yml}.
     * Does not check {@code Config.joinMessage()} — callers must guard that.
     */
    public static Component joinComponent(FakePlayer fp) {
        return buildMessage("bot-join", fp.getDisplayName());
    }

    /**
     * Builds the leave {@link Component} from {@code bot-leave} in {@code en.yml}.
     * Does not check {@code Config.leaveMessage()} — callers must guard that.
     */
    public static Component leaveComponent(String displayName) {
        return buildMessage("bot-leave", displayName);
    }

    /**
     * Broadcasts a join message if {@code messages.join-message} is enabled.
     * Used for bodyless bots (no {@code PlayerJoinEvent} fires for them).
     */
    public static void broadcastJoin(FakePlayer fp) {
        if (!Config.joinMessage()) return;
        send(buildMessage("bot-join", fp.getDisplayName()));
    }

    /**
     * Broadcasts a join message using a pre-resolved display-name string.
     * Used by the network layer when a JOIN event arrives from another server
     * via plugin messaging — the {@link FakePlayer} object does not exist here.
     */
    public static void broadcastJoinByDisplayName(String displayName) {
        if (!Config.joinMessage()) return;
        send(buildMessage("bot-join", displayName));
    }

    /**
     * Broadcasts a leave message if {@code messages.leave-message} is enabled.
     */
    public static void broadcastLeave(FakePlayer fp) {
        if (!Config.leaveMessage()) return;
        send(buildMessage("bot-leave", fp.getDisplayName()));
    }

    /**
     * Broadcasts a leave message using a pre-resolved display-name string.
     * Used when the {@link FakePlayer} object may already have been removed
     * from the active map (e.g. entity-death callbacks).
     */
    public static void broadcastLeaveByDisplayName(String displayName) {
        if (!Config.leaveMessage()) return;
        send(buildMessage("bot-leave", displayName));
    }

    /**
     * Broadcasts a kill message when a player kills a bot.
     */
    public static void broadcastKill(String killerName, String botDisplayName) {
        if (!Config.killMessage()) return;
        send(buildMessage("bot-kill", botDisplayName, "killer", killerName));
    }
}


package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    private static final MiniMessage MM = MiniMessage.miniMessage();

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
     * legacy §-codes, or a mix) into a {@link Component}.
     * <p>
     * LuckPerms prefixes are stored with {@code §} codes. We strip those to a
     * Component first, then reserialize to MiniMessage so the whole string can
     * be parsed cleanly by {@link MiniMessage}.
     */
    private static Component parseDisplayName(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        // If LuckPerms (or anything else) injected §-codes, deserialize those
        // first so they don't corrupt the surrounding MiniMessage parse.
        if (raw.indexOf('§') >= 0) {
            // Split on §-codes: deserialize the legacy part, then append any
            // trailing MiniMessage fragment.  Simplest safe approach: deserialize
            // everything through legacySection which at least produces correct colours,
            // then re-parse MiniMessage tags that survived.
            String reSerialized = MM.serialize(
                    LegacyComponentSerializer.legacySection().deserialize(raw));
            return MM.deserialize(reSerialized);
        }
        // Pure MiniMessage (the normal case when LuckPerms is absent / use-prefix: false)
        return MM.deserialize(raw);
    }

    /**
     * Builds the broadcast Component by:
     * 1. Getting the raw lang template (e.g. {@code "<yellow>{name} joined the game"}).
     * 2. Splitting on the {@code {name}} placeholder.
     * 3. Parsing each half as MiniMessage, then inserting the pre-parsed display-name Component.
     *
     * This avoids embedding a raw display-name string (which may contain §-codes)
     * directly into a MiniMessage template, which would corrupt it.
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
     * Broadcasts a join message if {@code messages.join-message} is enabled.
     */
    public static void broadcastJoin(FakePlayer fp) {
        if (!Config.joinMessage()) return;
        send(buildMessage("bot-join", fp.getDisplayName()));
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
     * from the active map (e.g. entity-death callbacks, swap AI).
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


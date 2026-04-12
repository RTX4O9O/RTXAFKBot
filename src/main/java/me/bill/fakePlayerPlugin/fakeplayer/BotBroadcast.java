package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
     * Sends {@code msg} to every online player and to the console -
     * the same delivery path vanilla Paper uses for real join/leave messages.
     */
    private static void send(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    /**
     * Converts a raw display-name string into a {@link Component}.
     * Plain names with no colour markup default to yellow so bot names are never
     * white in join/leave messages.  Names that already carry MiniMessage tags,
     * legacy §/& codes, or LP gradient shorthand keep their own colour.
     */
    private static Component parseDisplayName(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return TextUtil.colorizeOrYellow(raw);
    }

    /**
     * Builds the broadcast Component by:
     * 1. Getting the raw lang template (e.g. {@code "<yellow>{name} joined the game</yellow>"}).
     * 2. Replacing {@code {name}} with a safe MiniMessage insertion tag {@code <fpp_name>}.
     * 3. Parsing the ENTIRE template in one MiniMessage pass with a TagResolver that
     *    inserts the pre-parsed display-name Component at {@code <fpp_name>}.
     *
     * <p>Parsing the full string in one pass means colour tags such as
     * {@code <yellow>…</yellow>} span correctly across the name, so closing tags
     * like {@code </yellow>} are never orphaned and never appear as literal text.
     */
    private static Component buildMessage(String langKey, String displayName,
                                          String... extraArgs) {
        String template = Lang.raw(langKey, extraArgs);
        Component nameComponent = parseDisplayName(displayName);

        // Swap the {name} curly-brace placeholder for a custom MiniMessage tag
        // <fpp_name> so the whole template is parsed as a single unit.
        String withTag = template.replace("{name}", "<fpp_name>");
        String converted = TextUtil.legacyToMiniMessage(withTag);

        TagResolver nameResolver = TagResolver.resolver(
                "fpp_name", Tag.inserting(nameComponent));
        return MiniMessage.miniMessage().deserialize(converted, nameResolver);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Builds the join {@link Component} from {@code bot-join} in {@code en.yml}.
     * Does not check {@code Config.joinMessage()} - callers must guard that.
     */
    public static Component joinComponent(FakePlayer fp) {
        // Use raw display name to preserve color codes
        String displayName = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
        return buildMessage("bot-join", displayName);
    }

    /**
     * Builds the leave {@link Component} from {@code bot-leave} in {@code en.yml}.
     * Does not check {@code Config.leaveMessage()} - callers must guard that.
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
        // Use raw display name to preserve color codes
        String displayName = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
        send(buildMessage("bot-join", displayName));
    }

    /**
     * Broadcasts a join message using a pre-resolved display-name string.
     * Used by the network layer when a JOIN event arrives from another server
     * via plugin messaging - the {@link FakePlayer} object does not exist here.
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
        // Use raw display name to preserve color codes
        String displayName = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
        send(buildMessage("bot-leave", displayName));
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



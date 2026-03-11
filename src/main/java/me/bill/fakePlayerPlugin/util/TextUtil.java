package me.bill.fakePlayerPlugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility class for text formatting.
 * <p>
 * Supports MiniMessage tags (e.g. {@code <#0079FF>text</#0079FF>},
 * {@code <bold>}, {@code <gray>}) as well as legacy {@code &} colour codes
 * via the MiniMessage {@code <legacy:&...>} passthrough.
 */
public final class TextUtil {

    private TextUtil() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── Small-caps Unicode mapping ───────────────────────────────────────────

    private static final String NORMAL =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String SMALL_CAPS =
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ" +   // lower  (a-z)
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";  // upper  (A-Z)

    /**
     * Converts every ASCII letter in {@code text} to its small-caps Unicode equivalent.
     * Non-letter characters are left unchanged.
     */
    public static String toSmallCaps(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            int idx = NORMAL.indexOf(c);
            sb.append(idx >= 0 ? SMALL_CAPS.charAt(idx) : c);
        }
        return sb.toString();
    }

    // ── Colour parsing ───────────────────────────────────────────────────────

    /**
     * Parses a string using MiniMessage.
     * Supports:
     * <ul>
     *   <li>Hex colour tags: {@code <#0079FF>text</#0079FF>}</li>
     *   <li>Named colours: {@code <red>}, {@code <gray>}, {@code <white>} …</li>
     *   <li>Decorations: {@code <bold>}, {@code <italic>}, {@code <strikethrough>} …</li>
     *   <li>Legacy {@code &} codes via {@code <legacy_char>} or pre-converted below</li>
     * </ul>
     */
    public static Component colorize(String text) {
        if (text == null) return Component.empty();
        // Convert legacy & codes first so existing lang keys keep working
        // during any transition period, then parse with MiniMessage.
        String converted = legacyToMiniMessage(text);
        return MM.deserialize(converted);
    }

    /** Shorthand: applies {@link #toSmallCaps(String)} then {@link #colorize(String)}. */
    public static Component format(String text) {
        return colorize(toSmallCaps(text));
    }

    // ── Legacy → MiniMessage bridge ──────────────────────────────────────────

    /**
     * Converts legacy {@code &} or {@code §} colour/format codes to their MiniMessage equivalents
     * so that any remaining legacy codes in lang files still render correctly.
     */
    public static String legacyToMiniMessage(String s) {
        if (s == null || s.isEmpty()) return s;
        // If it contains section-sign legacy codes (§), convert those first
        if (s.indexOf('§') >= 0) {
            Component legacy = LegacyComponentSerializer.legacySection().deserialize(s);
            return MM.serialize(legacy);
        }
        // If it contains ampersand legacy codes (&), convert those
        if (s.indexOf('&') >= 0) {
            Component legacy = LegacyComponentSerializer.legacyAmpersand().deserialize(s);
            return MM.serialize(legacy);
        }
        return s;
    }
}

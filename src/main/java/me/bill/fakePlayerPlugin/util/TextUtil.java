package me.bill.fakePlayerPlugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            "ᴀʙᴄᴅᴠᴡxʏᴢ";  // upper  (A-Z)

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
     * Converts legacy {@code &} or {@code §} colour/format codes, AND
     * LuckPerms gradient shorthand ({@code {#FF0000>}text{#0000FF<}}) to their
     * MiniMessage equivalents so that any remaining legacy codes in lang files
     * and LuckPerms prefixes still render correctly.
     * <p>
     * <b>Supports all modern MiniMessage formats:</b>
     * <ul>
     *   <li>{@code <rainbow>text</rainbow>} — rainbow gradient</li>
     *   <li>{@code <gradient:#FF0000:#0000FF>text</gradient>} — custom gradient</li>
     *   <li>{@code <#9782ff>text</#9782ff>} — hex colors (closing tag optional)</li>
     *   <li>{@code &7[<#9782ff>Phantom</#9782ff>&7]} — mixed legacy + MiniMessage</li>
     *   <li>{@code {#fffff>}[PLAYER]{#00000<}} — LuckPerms gradient shorthand</li>
     * </ul>
     */
    public static String legacyToMiniMessage(String s) {
        if (s == null || s.isEmpty()) return s;
        
        // Step 0: Expand 3-digit hex codes to 6-digit format
        // Example: <#000> → <#000000>, <#abc> → <#aabbcc>, </#f0f> → </#f0f0f0>
        s = expand3DigitHexCodes(s);
        
        // Step 1: Convert LuckPerms {#RRGGBB>}...{#RRGGBB<} gradient/hex shorthand
        // to proper MiniMessage <gradient:...> tags
        if (s.contains("{#")) {
            s = convertLpColorTags(s);
        }
        
        // Step 1.5: Clean up unclosed/malformed hex tags at the end of the string
        // Example: "&7[<#9782ff>Text</#9782ff>&7] <#9782ff>" becomes "&7[<#9782ff>Text</#9782ff>&7] "
        s = s.replaceAll("<#[0-9A-Fa-f]{6}>\\s*$", ""); // Remove unclosed hex tag at end
        s = s.replaceAll("<#[0-9A-Fa-f]{6}>$", ""); // Remove unclosed hex tag at end (no space)
        
        // Step 2: Check if string already contains MiniMessage tags
        // If it does, we need to be careful not to break them when converting legacy codes
        boolean hasMiniMessageTags = s.indexOf('<') >= 0 && (
                s.contains("<rainbow>") || 
                s.contains("<gradient") || 
                s.matches(".*<#[0-9A-Fa-f]{6}>.*") ||
                s.matches(".*<[a-z_]+>.*") // other tags like <bold>, <red>, etc.
        );
        
        // Step 3: Handle mixed legacy + MiniMessage formats
        if (hasMiniMessageTags && (s.indexOf('&') >= 0 || s.indexOf('§') >= 0)) {
            // Mixed format: convert only the legacy codes while preserving MiniMessage tags
            s = convertMixedFormat(s);
            return s;
        }
        
        // Step 4: Pure legacy codes - convert normally
        if (s.indexOf('§') >= 0) {
            Component legacy = LegacyComponentSerializer.legacySection().deserialize(s);
            return MM.serialize(legacy);
        }
        if (s.indexOf('&') >= 0) {
            Component legacy = LegacyComponentSerializer.legacyAmpersand().deserialize(s);
            return MM.serialize(legacy);
        }
        
        // Step 5: Already MiniMessage or plain text - return as-is
        return s;
    }
    
    /**
     * Converts legacy {@code &} codes in a string that contains MiniMessage tags,
     * preserving the MiniMessage tags intact.
     * <p>
     * Example: {@code "&7[<#9782ff>Phantom</#9782ff>&7]"} becomes
     * {@code "<gray>[<#9782ff>Phantom</#9782ff><gray>]"}
     */
    private static String convertMixedFormat(String s) {
        // Replace legacy color codes with their MiniMessage equivalents
        // while being careful not to touch anything inside < > tags
        
        StringBuilder result = new StringBuilder();
        boolean inMiniMessageTag = false;
        
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            
            // Track when we're inside a MiniMessage tag
            if (c == '<') {
                inMiniMessageTag = true;
                result.append(c);
                continue;
            } else if (c == '>') {
                inMiniMessageTag = false;
                result.append(c);
                continue;
            }
            
            // If we're inside a tag, don't convert anything
            if (inMiniMessageTag) {
                result.append(c);
                continue;
            }
            
            // Convert legacy codes when we're outside tags
            if ((c == '&' || c == '§') && i + 1 < s.length()) {
                char code = s.charAt(i + 1);
                String miniTag = legacyCodeToMiniMessage(code);
                if (miniTag != null) {
                    result.append(miniTag);
                    i++; // skip the code character
                    continue;
                }
            }
            
            result.append(c);
        }
        
        return result.toString();
    }
    
    /**
     * Maps a single legacy color/format code to its MiniMessage equivalent.
     * @param code the character after {@code &} or {@code §}
     * @return MiniMessage tag, or {@code null} if code is invalid
     */
    private static String legacyCodeToMiniMessage(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a', 'A' -> "<green>";
            case 'b', 'B' -> "<aqua>";
            case 'c', 'C' -> "<red>";
            case 'd', 'D' -> "<light_purple>";
            case 'e', 'E' -> "<yellow>";
            case 'f', 'F' -> "<white>";
            case 'k', 'K' -> "<obfuscated>";
            case 'l', 'L' -> "<bold>";
            case 'm', 'M' -> "<strikethrough>";
            case 'n', 'N' -> "<underlined>";
            case 'o', 'O' -> "<italic>";
            case 'r', 'R' -> "<reset>";
            default -> null;
        };
    }

    // ── LuckPerms colour-tag conversion ──────────────────────────────────────

    /**
     * Expands 3-digit hex codes to 6-digit format for MiniMessage compatibility.
     * <p>
     * CSS/MiniMessage shorthand: {@code #RGB} expands to {@code #RRGGBB}
     * <ul>
     *   <li>{@code <#000>} → {@code <#000000>} (black)</li>
     *   <li>{@code <#fff>} → {@code <#ffffff>} (white)</li>
     *   <li>{@code <#abc>} → {@code <#aabbcc>} (light blue)</li>
     *   <li>{@code </#f0f>} → {@code </#f0f0f0>} (closing tag)</li>
     * </ul>
     * 
     * @param s input string that may contain 3-digit hex codes
     * @return string with all 3-digit hex codes expanded to 6 digits
     */
    private static String expand3DigitHexCodes(String s) {
        if (s == null || s.indexOf('#') < 0) return s;
        
        // Pattern for 3-digit hex codes in opening tags: <#RGB>
        Pattern openTag3Digit = Pattern.compile("<#([0-9A-Fa-f]{3})>");
        Matcher m = openTag3Digit.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex3 = m.group(1);
            // Expand each digit: RGB → RRGGBB
            String hex6 = String.format("%c%c%c%c%c%c",
                    hex3.charAt(0), hex3.charAt(0),
                    hex3.charAt(1), hex3.charAt(1),
                    hex3.charAt(2), hex3.charAt(2));
            m.appendReplacement(sb, "<#" + hex6 + ">");
        }
        m.appendTail(sb);
        s = sb.toString();
        
        // Pattern for 3-digit hex codes in closing tags: </#RGB>
        Pattern closeTag3Digit = Pattern.compile("</#([0-9A-Fa-f]{3})>");
        m = closeTag3Digit.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String hex3 = m.group(1);
            // Expand each digit: RGB → RRGGBB
            String hex6 = String.format("%c%c%c%c%c%c",
                    hex3.charAt(0), hex3.charAt(0),
                    hex3.charAt(1), hex3.charAt(1),
                    hex3.charAt(2), hex3.charAt(2));
            m.appendReplacement(sb, "</#" + hex6 + ">");
        }
        m.appendTail(sb);
        
        return sb.toString();
    }

    /**
     * Pre-compiled pattern for LuckPerms gradient pairs:
     * {@code {#RRGGBB>}…{#RRGGBB<}}
     * Matches both valid 6-digit hex and invalid/incomplete hex codes for error recovery.
     */
    private static final Pattern LP_GRADIENT =
            Pattern.compile("\\{(#[0-9A-Fa-f]{6})>}(.*?)\\{(#[0-9A-Fa-f]{6})<}",
                    Pattern.DOTALL);
    
    /**
     * Pattern for malformed/incomplete LuckPerms gradient tags that need cleanup.
     * Matches things like {#00000<} (5 digits), {#<}, etc.
     */
    private static final Pattern LP_MALFORMED_TAG =
            Pattern.compile("\\{#[0-9A-Fa-f]{0,5}[<>]}");

    /**
     * Converts LuckPerms colour shorthand to proper MiniMessage tags.
     * <ul>
     *   <li>{@code {#FF0000>}text{#0000FF<}} →
     *       {@code <gradient:#FF0000:#0000FF>text</gradient>}</li>
     *   <li>{@code {#FF0000}} →
     *       {@code <#FF0000>} (solid hex, no closing tag needed)</li>
     * </ul>
     * Recursively processes nested gradient pairs.
     * Malformed tags are removed to prevent rendering errors.
     */
    private static String convertLpColorTags(String s) {
        // First, remove any malformed gradient tags
        s = LP_MALFORMED_TAG.matcher(s).replaceAll("");
        
        // Convert valid gradients
        s = convertLpGradients(s);
        
        // Any remaining bare {#RRGGBB} tags (solid color, no gradient) become <#RRGGBB>
        s = s.replaceAll("\\{(#[0-9A-Fa-f]{6})}", "<$1>");
        
        return s;
    }

    private static String convertLpGradients(String s) {
        Matcher m = LP_GRADIENT.matcher(s);
        if (!m.find()) return s; // fast-path — no gradient tags present
        m.reset();
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String startColor = m.group(1);
            String content    = convertLpGradients(m.group(2)); // recurse for nesting
            String endColor   = m.group(3);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<gradient:" + startColor + ":" + endColor + ">"
                            + content + "</gradient>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

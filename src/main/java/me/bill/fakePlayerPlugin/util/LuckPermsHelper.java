package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralised LuckPerms helper for FakePlayerPlugin.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Resolve the correct LuckPerms prefix and group weight for a bot, taking into account
 *       {@code luckperms.bot-group}, {@code luckperms.use-prefix}, and the spawner's group.</li>
 *   <li>Build the {@code GameProfile} packet-name that controls Minecraft tab-list sort order.</li>
 *   <li>Cache all LP queries with a configurable TTL to avoid repeated API calls per spawn.</li>
 *   <li>Support all LuckPerms color formats: legacy codes, hex colors, gradients, and rainbow.</li>
 * </ul>
 *
 * <h3>Supported color formats</h3>
 * <ul>
 *   <li>MiniMessage tags: {@code <rainbow>text</rainbow>}, {@code <gradient:#FF0000:#0000FF>text</gradient>}</li>
 *   <li>MiniMessage hex: {@code <#9782ff>text</#9782ff>} (closing tag optional)</li>
 *   <li>LuckPerms gradient shorthand: {@code {#fffff>}[PLAYER]{#00000<}}</li>
 *   <li>Mixed formats: {@code &7[<#9782ff>Phantom</#9782ff>&7]}</li>
 *   <li>Legacy codes: {@code &c}, {@code §c}, {@code &l}, etc.</li>
 * </ul>
 *
 * <h3>Tab-list ordering model</h3>
 * <pre>
 *   packet name = [packet-prefix-char][rank-index]_[original-name]
 * </pre>
 * <ul>
 *   <li>{@code packet-prefix-char} (default {@code "{"}) shifts all bots after {@code z}
 *       in Minecraft's lexicographic tab sort.  Use {@code "!"} to put bots first, or leave
 *       blank to not shift at all.</li>
 *   <li>{@code rank-index} (00–99) orders bots <em>among themselves</em> by group weight:
 *       Higher LP weight = lower rank index = appears HIGHER in the bot section.
 *       <ul>
 *         <li>Admin bot (weight=100) → rank=00 → {@code {00_BotName} (top of bot section)</li>
 *         <li>VIP bot (weight=50) → rank=01 → {@code {01_BotName}</li>
 *         <li>Default bot (weight=1) → rank=02 → {@code {02_BotName} (bottom of bot section)</li>
 *       </ul>
 *       This matches standard LuckPerms ordering where higher weight = higher priority.</li>
 *   <li>When {@code weight-ordering-enabled: false} all bots share rank 00.</li>
 * </ul>
 *
 * <h3>Prefix resolution priority</h3>
 * <ol>
 *   <li>Explicit {@code luckperms.bot-group} config → use that group's prefix + weight.</li>
 *   <li>Spawner's online primary group (user bots only, when ownerUuid != null).</li>
 *   <li>{@code "default"} LP group prefix + weight.</li>
 *   <li>Lowest-weight group that carries a prefix.</li>
 *   <li>Empty prefix, weight 0.</li>
 * </ol>
 */
public final class LuckPermsHelper {

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Immutable result of a LuckPerms lookup.
     * {@link #prefix} is already converted to MiniMessage format (or empty string).
     * {@link #weight} is the raw LP group weight integer.
     */
    public record LpData(String prefix, int weight) {
        /** Sentinel returned when LP is unavailable or no data is found. */
        public static final LpData EMPTY = new LpData("", 0);

        /** @return true when a non-blank prefix was resolved. */
        public boolean hasPrefix() {
            return prefix != null && !prefix.isBlank();
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    /** Cached per-group data keyed by group name (lower-case). */
    private static final Map<String, LpData> groupCache = new ConcurrentHashMap<>();
    /** Cached rank list (sorted weight DESC) used by buildPacketProfileName. */
    private static volatile List<Integer> cachedRankList = null;
    /** System.currentTimeMillis() when the cache was last populated. */
    private static volatile long cacheTimestamp = 0L;
    /** How long (ms) cached data stays valid. 60 s is sufficient for in-game use. */
    private static final long CACHE_TTL_MS = 60_000L;

    private LuckPermsHelper() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /** @return {@code true} when LuckPerms is installed and enabled on this server. */
    public static boolean isAvailable() {
        Plugin p = Bukkit.getPluginManager().getPlugin("LuckPerms");
        return p != null && p.isEnabled();
    }

    /**
     * Invalidates all cached LP data.
     * Call from {@code /fpp reload} and whenever LP config may have changed.
     */
    @SuppressWarnings("unused") // called from ReloadCommand
    public static void invalidateCache() {
        groupCache.clear();
        cachedRankList = null;
        cacheTimestamp = 0L;
        Config.debug("[LP] Cache invalidated.");
    }

    /**
     * Invalidates cached data for a specific group.
     * Use this when updating a single bot's rank to avoid clearing the entire ranking system.
     */
    public static void invalidateGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) return;
        String key = "g:" + groupName.toLowerCase();
        groupCache.remove(key);
        Config.debug("[LP] Group cache invalidated for: " + groupName);
    }

    /**
     * Forces refresh of the rank list cache.
     * Call this when a bot is assigned to a new group to ensure all weights are included.
     */
    public static void refreshRankList() {
        cachedRankList = null;
        cacheTimestamp = 0L;
        Config.debug("[LP] Rank list cache refreshed.");
    }

    /**
     * Returns the resolved {@link LpData} (prefix + weight) for a bot spawn.
     *
     * <p><b>Priority order:</b>
     * <ol>
     *   <li>Bot-specific group (set via {@code /fpp rank set <bot> <group>})</li>
     *   <li>Global {@code luckperms.bot-group} config</li>
     *   <li>{@code "default"} group or lowest-weight group with a prefix</li>
     * </ol>
     *
     * <p><b>Weight:</b> The <em>original</em> LP group weight is returned (no penalty
     * applied).  Using the raw weight keeps all groups in the rank list and ensures
     * correct differentiation in {@link #computeRank}.  The {@code luckperms.weight-offset}
     * config is kept for backward compatibility but is no longer applied here — bots are
     * already pushed below all real players by the sort-prefix character ({@code ~} or the
     * configured {@code packet-prefix-char}).
     *
     * @param botSpecificGroup optional LP group override for this specific bot (takes highest priority)
     * @param ownerUuid UUID of the spawning player (reserved; bots use the bot-group or default)
     * @return never {@code null}; falls back to {@link LpData#EMPTY}
     */
    public static LpData getBotLpData(String botSpecificGroup, UUID ownerUuid) {
        if (!isAvailable()) return LpData.EMPTY;
        try {
            LuckPerms lp = LuckPermsProvider.get();

            // ── 1. Bot-specific group override (highest priority) ─────────────
            if (botSpecificGroup != null && !botSpecificGroup.isBlank()) {
                LpData d = getGroupData(lp, botSpecificGroup);
                Config.debug("[LP] getBotLpData: using bot-specific group='" + botSpecificGroup
                        + "' prefix='" + d.prefix() + "' weight=" + d.weight());
                return d;
            }

            // ── 2. Explicit bot-group config (second priority) ────────────────
            String botGroup = Config.luckpermsBotGroup();
            if (botGroup != null && !botGroup.isBlank()) {
                LpData d = getGroupData(lp, botGroup);
                Config.debug("[LP] getBotLpData: using bot-group='" + botGroup
                        + "' prefix='" + d.prefix() + "' weight=" + d.weight());
                return d;
            }

            // ── 3. 'default' group / lowest-weight fallback ───────────────────
            LpData fallback = getDefaultGroupData(lp);
            Config.debug("[LP] getBotLpData: using default/fallback"
                    + " prefix='" + fallback.prefix() + "' weight=" + fallback.weight());
            return fallback;

        } catch (Exception e) {
            Config.debug("[LP] getBotLpData failed: " + e.getMessage());
            return LpData.EMPTY;
        }
    }

    /**
     * Overload for backward compatibility — uses null bot-specific group.
     */
    public static LpData getBotLpData(UUID ownerUuid) {
        return getBotLpData(null, ownerUuid);
    }

    /**
     * Returns only the prefix string (empty when {@code use-prefix: false}).
     * Convenience wrapper around {@link #getBotLpData}.
     */
    @SuppressWarnings("unused") // public API for addons
    public static String getBotPrefix() {
        if (!Config.luckpermsUsePrefix()) return "";
        return getBotLpData(null).prefix();
    }

    /**
     * Returns only the group weight for a bot.
     * Unlike {@link #getBotPrefix}, this ignores the {@code use-prefix} toggle
     * because weight is used for tab-list ordering independently of prefix display.
     */
    @SuppressWarnings("unused") // public API for addons
    public static int getBotWeight() {
        return getBotLpData(null).weight();
    }

    /**
     * Builds the {@code GameProfile} name that encodes sort order in the Minecraft tab list.
     *
     * <pre>
     *   [sortPrefix][rank-index-00..99]_[original-name truncated to fit 16 chars]
     * </pre>
     *
     * <ul>
     *   <li>{@code sortPrefix} is the configured {@code packet-prefix-char} (default {@code "{"}).
     *       When left empty in config the plugin falls back to {@code "~"} so bots always appear
     *       <em>after</em> real players without requiring explicit configuration.
     *       Use {@code "!"} to intentionally place bots <em>before</em> all players.</li>
     *   <li>{@code rank-index} (00–99) orders bots <em>among themselves</em> by LP group weight:
     *       Higher weight = lower rank index = appears HIGHER in bot section.
     *       <ul>
     *         <li>Admin bot (weight=100) → rank=00 → {@code {00_AdminBot} (top of bot section)</li>
     *         <li>Default bot (weight=1) → rank=02 → {@code {02_DefaultBot} (bottom of bot section)</li>
     *       </ul>
     *       This matches standard LuckPerms ordering where higher weight = higher priority.</li>
     *   <li>When {@code weight-ordering-enabled: false} all bots share rank {@code 00}.</li>
     * </ul>
     *
     * @param weight       the bot's resolved group weight (original, unpenalised)
     * @param originalName the bot's internal Minecraft name (≤16 chars)
     * @return the modified packet profile name (always ≤16 chars)
     */
    public static String buildPacketProfileName(int weight, String originalName) {
        String packetChar = Config.luckpermsPacketPrefixChar();
        boolean hasPc = packetChar != null && !packetChar.isBlank();

        // No LP available → return original name, optionally prefixed by packet-char
        if (!isAvailable()) {
            if (!hasPc) return originalName;
            char c = packetChar.charAt(0);
            String pfx = c + "_";
            return pfx + trim(originalName, 16 - pfx.length());
        }

        // Weight ordering disabled → all bots at rank 00
        if (!Config.luckpermsWeightOrderingEnabled()) {
            if (!hasPc) return originalName;
            char c = packetChar.charAt(0);
            String pfx = String.format("%c00_", c);
            return pfx + trim(originalName, 16 - pfx.length());
        }

        // Full weight-ordered packet name
        try {
            int rank = computeRank(weight);
            String prefix;
            if (hasPc) {
                // User-configured prefix character (e.g. '{', '~', '!')
                prefix = String.format("%c%02d_", packetChar.charAt(0), rank);
            } else {
                // No prefix char configured — use '~' (ASCII 126, after 'z' 122)
                prefix = String.format("~%02d_", rank);
            }
            String result = prefix + trim(originalName, 16 - prefix.length());
            
            Config.debug("[LP] buildPacketProfileName: weight=" + weight
                    + " rank=" + rank + " prefix='" + prefix + "' result='" + result + "'");
            return result;
        } catch (Exception e) {
            Config.debug("[LP] buildPacketProfileName failed: " + e.getMessage());
            return originalName;
        }
    }

    /**
     * Returns the default-group prefix for startup/reload diagnostic logging.
     * Does NOT respect the {@code use-prefix} config toggle — always reads LP data.
     */
    @SuppressWarnings("unused") // called via FakePlayerManager.detectLuckPermsPrefix()
    public static String detectDefaultPrefix() {
        if (!isAvailable()) return "";
        try {
            return getDefaultGroupData(LuckPermsProvider.get()).prefix();
        } catch (Exception e) {
            Config.debug("[LP] detectDefaultPrefix failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * Returns a short summary of all loaded groups (name + weight) for debug logging.
     */
    @SuppressWarnings("unused") // called from FakePlayerPlugin startup diagnostic
    public static String buildGroupSummary() {
        if (!isAvailable()) return "LuckPerms not available";
        try {
            LuckPerms lp = LuckPermsProvider.get();
            StringBuilder sb = new StringBuilder();
            List<Group> groups = new ArrayList<>(lp.getGroupManager().getLoadedGroups());
            groups.sort(Comparator.comparingInt((Group g) -> g.getWeight().orElse(0)).reversed());
            for (Group g : groups) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(g.getName()).append("(w=").append(g.getWeight().orElse(0)).append(')');
            }
            return sb.toString();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * Returns a list of all loaded LuckPerms group names, sorted by weight (highest first).
     * Used by {@code /fpp rank} tab completion.
     */
    public static List<String> getAllGroupNames() {
        if (!isAvailable()) return List.of();
        try {
            LuckPerms lp = LuckPermsProvider.get();
            List<Group> groups = new ArrayList<>(lp.getGroupManager().getLoadedGroups());
            groups.sort(Comparator.comparingInt((Group g) -> g.getWeight().orElse(0)).reversed());
            return groups.stream().map(Group::getName).toList();
        } catch (Exception e) {
            Config.debug("[LP] getAllGroupNames failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Checks if a LuckPerms group exists.
     * @param groupName the group name to check (case-insensitive)
     * @return {@code true} if the group exists
     */
    public static boolean groupExists(String groupName) {
        if (!isAvailable() || groupName == null || groupName.isBlank()) return false;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            return lp.getGroupManager().getGroup(groupName) != null;
        } catch (Exception e) {
            Config.debug("[LP] groupExists('" + groupName + "') failed: " + e.getMessage());
            return false;
        }
    }

    // ── Internal: group/user resolution ──────────────────────────────────────

    /**
     * Returns cached {@link LpData} for a specific group name.
     * Queries LP only when the cache is stale.
     */
    private static LpData getGroupData(LuckPerms lp, String groupName) {
        if (groupName == null || groupName.isBlank()) return LpData.EMPTY;
        String key = "g:" + groupName.toLowerCase(Locale.ROOT);
        LpData cached = getCachedIfFresh(key);
        if (cached != null) return cached;

        try {
            Group g = lp.getGroupManager().getGroup(groupName);
            if (g == null) {
                Config.debug("[LP] getGroupData: group '" + groupName + "' not found.");
                return store(key, LpData.EMPTY);
            }
            String rawPrefix = extractBestPrefix(g);
            String prefix    = rawPrefix != null ? TextUtil.legacyToMiniMessage(rawPrefix) : "";
            int    weight    = g.getWeight().orElse(0);
            return store(key, new LpData(prefix, weight));
        } catch (Exception e) {
            Config.debug("[LP] getGroupData('" + groupName + "') failed: " + e.getMessage());
            return LpData.EMPTY;
        }
    }

    /**
     * Returns the best available default group data:
     * tries {@code "default"} group first, then lowest-weight group with a prefix.
     */
    private static LpData getDefaultGroupData(LuckPerms lp) {
        // Try the named 'default' group first
        LpData d = getGroupData(lp, "default");
        // 'default' exists and has SOMETHING (prefix or weight)
        if (d != LpData.EMPTY && (d.hasPrefix() || d.weight() != 0)) return d;

        // Scan all groups for lowest-weight that has a prefix
        String key = "g:__lowest__";
        LpData cached = getCachedIfFresh(key);
        if (cached != null) return cached;

        try {
            int    lowestWeight = Integer.MAX_VALUE;
            String lowestPrefix = null;
            int    foundWeight  = 0;
            for (Group g : lp.getGroupManager().getLoadedGroups()) {
                String rawPrefix = extractBestPrefix(g);
                if (rawPrefix == null || rawPrefix.isBlank()) continue;
                int w = g.getWeight().orElse(0);
                if (w < lowestWeight) {
                    lowestWeight = w;
                    lowestPrefix = rawPrefix;
                    foundWeight  = w;
                }
            }
            if (lowestPrefix != null) {
                LpData result = new LpData(TextUtil.legacyToMiniMessage(lowestPrefix), foundWeight);
                return store(key, result);
            }
        } catch (Exception e) {
            Config.debug("[LP] getDefaultGroupData scan failed: " + e.getMessage());
        }
        return LpData.EMPTY;
    }

    // ── Internal: rank computation ────────────────────────────────────────────

    /**
     * Computes the 0-based rank index for {@code weight} among all loaded groups.
     *
     * <p><b>CRITICAL:</b> Higher LuckPerms weight should result in LOWER rank index
     * (closer to the top). This matches the standard LuckPerms ordering convention:
     * higher weight = higher priority = appears first.
     *
     * <pre>
     *   Example: groups [admin(w=100), vip(w=50), default(w=1)] sorted DESC
     *   admin-bot   weight=100 → index 0 → rank=0 → {00_BotName  (top of bot section)
     *   vip-bot     weight=50  → index 1 → rank=1 → {01_BotName
     *   default-bot weight=1   → index 2 → rank=2 → {02_BotName  (bottom of bot section)
     * </pre>
     *
     * This ensures bots with LOWER weight (like default group) appear BELOW bots
     * with HIGHER weight (like admin group) in the tab list, matching the owner's
     * position.
     *
     * Result is capped at 99.
     */
    private static int computeRank(int weight) {
        List<Integer> ranked = getRankList(); // sorted DESC (highest weight first)
        
        // Direct index mapping: higher weight = lower index = lower rank number = higher position
        int idx = ranked.indexOf(weight);
        if (idx < 0) {
            // Weight not in the list — find insertion point in the DESC-sorted list.
            // Walk until we find the first group whose weight is ≤ ours; that is our slot.
            idx = ranked.size(); // default: below all known groups
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i) <= weight) {
                    idx = i;
                    break;
                }
            }
        }
        
        // Return the index directly (no inversion) — higher weight gets lower rank number
        return Math.min(Math.max(idx, 0), 99);
    }

    /**
     * Returns a sorted list of unique group weights (DESC) — cached for the TTL duration.
     * Uses ALL loaded groups when no bot-group is configured; otherwise uses only that group.
     */
    private static List<Integer> getRankList() {
        List<Integer> cached = cachedRankList;
        if (cached != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            return cached;
        }
        try {
            LuckPerms lp    = LuckPermsProvider.get();
            TreeSet<Integer> ws = new TreeSet<>(Collections.reverseOrder());

            String botGroup = Config.luckpermsBotGroup();
            if (botGroup != null && !botGroup.isBlank()) {
                // Only that group's weight — bots from the configured group all share rank 0
                Group fg = lp.getGroupManager().getGroup(botGroup);
                if (fg != null) ws.add(fg.getWeight().orElse(0));
            } else {
                // All groups — gives a stable rank relative to every LP group's weight
                for (Group g : lp.getGroupManager().getLoadedGroups()) {
                    ws.add(g.getWeight().orElse(0));
                }
            }
            if (ws.isEmpty()) ws.add(0);
            List<Integer> list = new ArrayList<>(ws);
            cachedRankList = list;
            // Note: cacheTimestamp is updated in store() / invalidateCache()
            return list;
        } catch (Exception e) {
            Config.debug("[LP] getRankList failed: " + e.getMessage());
            return Collections.singletonList(0);
        }
    }

    // ── Internal: utilities ───────────────────────────────────────────────────


    /**
     * Extracts the highest-priority prefix from a group.
     * Tries direct prefix nodes first (more accurate), then falls back to
     * the cached metadata accessor.
     */
    private static String extractBestPrefix(Group g) {
        // Direct prefix nodes have the correct raw value (including gradient tags)
        String p = g.getNodes(NodeType.PREFIX)
                .stream()
                .max(Comparator.comparingInt(PrefixNode::getPriority))
                .map(PrefixNode::getMetaValue)
                .orElse(null);
        if (p != null && !p.isBlank()) return p;
        // Fallback: cached metadata (may have already been processed by LP)
        try {
            return g.getCachedData().getMetaData(QueryOptions.nonContextual()).getPrefix();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LpData getCachedIfFresh(String key) {
        if ((System.currentTimeMillis() - cacheTimestamp) >= CACHE_TTL_MS) return null;
        return groupCache.get(key);
    }

    private static LpData store(String key, LpData data) {
        groupCache.put(key, data);
        // Refresh the shared timestamp whenever we write, so subsequent reads use it
        if (cacheTimestamp == 0L) cacheTimestamp = System.currentTimeMillis();
        return data;
    }


    private static String trim(String s, int maxLen) {
        if (maxLen <= 0) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}


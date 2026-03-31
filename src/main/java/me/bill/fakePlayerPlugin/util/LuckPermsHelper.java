package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * LuckPerms integration helper for FakePlayerPlugin.
 *
 * <h3>Architecture</h3>
 * <p>Bots are real NMS {@code ServerPlayer} entities. LP detects them as genuine
 * online players automatically. This class:
 * <ul>
 *   <li>Pre-assigns a group in LP storage <em>before</em> the NMS body spawns so
 *       that {@code PlayerJoinEvent} listeners (TAB, LP itself) see the correct group
 *       immediately at join time.</li>
 *   <li>Preserves a bot's previously-assigned group across restarts — restored bots
 *       keep their {@code /fpp rank} group without being reset to the default.</li>
 *   <li>Subscribes to LP's {@code UserDataRecalculateEvent} so display names update
 *       automatically when an admin changes a bot's group via LP commands.</li>
 *   <li>Reads resolved prefix/suffix from LP's in-memory {@code CachedMetaData} for
 *       tab-list display name rebuilding.</li>
 * </ul>
 */
public final class LuckPermsHelper {

    /** Active LP event subscription — closed in {@link #unsubscribeLpEvents()}. */
    private static EventSubscription<UserDataRecalculateEvent> eventSub;

    private LuckPermsHelper() {}

    // ── Availability ──────────────────────────────────────────────────────────

    /** @return {@code true} when LuckPerms is installed and enabled on this server. */
    public static boolean isAvailable() {
        Plugin p = Bukkit.getPluginManager().getPlugin("LuckPerms");
        return p != null && p.isEnabled();
    }

    /** Safe LP API accessor — returns {@code null} when LP is not available. */
    private static LuckPerms lp() {
        if (!isAvailable()) return null;
        try { return LuckPermsProvider.get(); }
        catch (IllegalStateException e) { return null; }
    }

    // ── Event bus ─────────────────────────────────────────────────────────────

    /**
     * Subscribes to LP's {@code UserDataRecalculateEvent} so that whenever a bot's
     * LP permissions are recalculated (group change via LP command, API, etc.) the
     * plugin automatically rebuilds its tab-list display name with the new prefix.
     *
     * <p>Must be called on the main thread after both LP and {@link FakePlayerManager}
     * are initialised.
     *
     * @param plugin  plugin instance (used to schedule Bukkit tasks)
     * @param manager fake-player manager (used to look up and refresh bots)
     */
    public static void subscribeLpEvents(FakePlayerPlugin plugin, FakePlayerManager manager) {
        LuckPerms api = lp();
        if (api == null) return;
        // Prevent double-subscription (e.g. on reload)
        if (eventSub != null) {
            eventSub.close();
            eventSub = null;
        }
        try {
            eventSub = api.getEventBus().subscribe(
                    plugin,
                    UserDataRecalculateEvent.class,
                    event -> {
                        UUID uuid = event.getUser().getUniqueId();
                        FakePlayer fp = manager.getByUuid(uuid);
                        if (fp == null) return; // not one of our bots
                        // Sync the group field and refresh display on main thread
                        String newGroup = event.getUser().getPrimaryGroup();
                        fp.setLuckpermsGroup(newGroup);
                        Bukkit.getScheduler().runTask(plugin,
                                () -> manager.refreshLpDisplayName(fp));
                        Config.debugLuckPerms("UserDataRecalculate for bot '" + fp.getName()
                                + "' — new group='" + newGroup + "', refreshing display name.");
                    });
            Config.debugLuckPerms("Subscribed to UserDataRecalculateEvent — bot display names will auto-update when LP groups change.");
        } catch (Exception e) {
            FppLogger.warn("[LP] Failed to subscribe to LP events: " + e.getMessage());
        }
    }

    /**
     * Unsubscribes from LP events. Call in {@code onDisable} to prevent memory leaks.
     */
    public static void unsubscribeLpEvents() {
        if (eventSub != null) {
            try { eventSub.close(); } catch (Exception ignored) {}
            eventSub = null;
        }
    }

    // ── Pre-spawn group setup ─────────────────────────────────────────────────

    /**
     * Ensures the bot has the correct LP group in storage <em>before</em> their NMS
     * body is spawned. Called from {@code finishSpawn} to guarantee that LP and
     * tab-list plugins (e.g. TAB) see the correct group when {@code PlayerJoinEvent}
     * fires.
     *
     * <h3>Resolution logic</h3>
     * <ol>
     *   <li>Load the bot's LP user from storage (async).</li>
     *   <li>If the bot already has a non-{@code default} primary group (i.e. a group
     *       explicitly assigned by a previous {@code /fpp rank}) <em>and</em> no
     *       {@code luckperms.default-group} is configured in FPP config, keep the
     *       existing group — this preserves rank across server restarts.</li>
     *   <li>Otherwise assign the configured {@code configGroup}, or {@code "default"}
     *       if the config is blank, and call {@link User#setPrimaryGroup(String)} so
     *       LP reports the correct primary group immediately at join time.</li>
     * </ol>
     *
     * @param botUuid     UUID of the bot
     * @param configGroup value of {@code Config.luckpermsDefaultGroup()} (may be blank)
     * @return future that resolves with the group name that was applied or kept
     */
    public static CompletableFuture<String> ensureGroupBeforeSpawn(UUID botUuid, String configGroup) {
        LuckPerms api = lp();
        if (api == null) return CompletableFuture.completedFuture("default");

        // Fallback target: use the configured group or "default" as baseline
        String targetGroup = (configGroup != null && !configGroup.trim().isEmpty())
                ? configGroup.trim() : "default";

        return api.getUserManager().loadUser(botUuid)
                .thenCompose(user -> {
                    if (user == null) return CompletableFuture.completedFuture(targetGroup);

                    String storedPrimary = user.getPrimaryGroup();
                    // A bot "has a real group" when LP already has an explicit, non-default
                    // primary group recorded (set by a previous /fpp rank assignment).
                    boolean hasExplicitGroup = storedPrimary != null
                            && !storedPrimary.equalsIgnoreCase("default")
                            && !storedPrimary.isBlank();
                    boolean configForcesGroup = configGroup != null && !configGroup.trim().isEmpty();

                    if (hasExplicitGroup && !configForcesGroup) {
                        // Restore path: keep the previously-assigned rank
                        Config.debugLuckPerms("ensureGroupBeforeSpawn: keeping stored group '"
                                + storedPrimary + "' for " + botUuid);
                        return CompletableFuture.completedFuture(storedPrimary);
                    }

                    // New bot path (or config override): assign targetGroup
                    user.data().clear(NodeType.INHERITANCE::matches);
                    user.data().add(InheritanceNode.builder(targetGroup).build());
                    user.setPrimaryGroup(targetGroup);
                    
                    // Force LP to recalculate cached metadata immediately
                    user.getCachedData().invalidate();
                    
                    return api.getUserManager().saveUser(user)
                            .thenApply(v -> {
                                Config.debugLuckPerms("ensureGroupBeforeSpawn: set group '"
                                        + targetGroup + "' for " + botUuid);
                                // Invalidate again after save to ensure fresh calculation
                                user.getCachedData().invalidate();
                                return targetGroup;
                            });
                })
                .exceptionally(ex -> {
                    FppLogger.warn("[LP] ensureGroupBeforeSpawn error for " + botUuid
                            + ": " + ex.getMessage());
                    return targetGroup;
                });
    }


    // ── Runtime group changes ─────────────────────────────────────────────────

    /**
     * Applies an LP group to an <em>online</em> bot by writing directly to the live
     * {@link User} instance that LuckPerms loaded at {@code PlayerJoinEvent}, then
     * calling {@code saveUser()}.
     *
     * <p>This is the key fix for prefix not showing on first spawn. LP keeps one
     * authoritative {@code User} object per online player. {@code saveUser()} on that
     * object fires {@code UserDataRecalculateEvent}, which our event subscriber
     * already handles to rebuild the tab-list display name.</p>
     *
     * <p>Falls back to {@link #setPlayerGroup(UUID, String)} when the user isn't
     * loaded yet (shouldn't happen 5 ticks after spawn, but safe).</p>
     *
     * @param botUuid   the bot's UUID
     * @param groupName LP group to apply
     * @return future that completes after the save (or immediately on fallback)
     */
    public static CompletableFuture<Void> applyGroupToOnlineUser(UUID botUuid, String groupName) {
        LuckPerms api = lp();
        if (api == null) return CompletableFuture.completedFuture(null);

        // getUser() returns the LIVE online User instance LP loaded at PlayerJoinEvent.
        // Modifying it and calling saveUser() fires UserDataRecalculateEvent.
        User onlineUser = api.getUserManager().getUser(botUuid);
        if (onlineUser != null) {
            onlineUser.data().clear(NodeType.INHERITANCE::matches);
            onlineUser.data().add(InheritanceNode.builder(groupName).build());
            onlineUser.setPrimaryGroup(groupName);
            Config.debugLuckPerms("applyGroupToOnlineUser: applying '" + groupName + "' to online user " + botUuid);
            // saveUser() on an online User triggers UserDataRecalculateEvent → prefix updates
            return api.getUserManager().saveUser(onlineUser);
        }

        // Fallback: user not in online cache yet — load from storage and set
        Config.debugLuckPerms("applyGroupToOnlineUser: user not online yet, falling back to setPlayerGroup for " + botUuid);
        return setPlayerGroup(botUuid, groupName);
    }

    /**
     * Sets a bot's LP group at runtime (used by {@code /fpp rank}).
     * Prefers the live online {@link User} instance so the save fires
     * {@code UserDataRecalculateEvent} immediately. Falls back to {@code loadUser()}
     * for offline users.
     *
     * @param playerUuid   the bot's UUID
     * @param newGroupName the LP group name to assign
     * @return future that completes when the assignment is saved
     */
    public static CompletableFuture<Void> setPlayerGroup(UUID playerUuid, String newGroupName) {
        LuckPerms api = lp();
        if (api == null) return CompletableFuture.failedFuture(
                new IllegalStateException("LuckPerms not available"));

        // Prefer the live online instance — saveUser() on it fires UserDataRecalculateEvent
        User onlineUser = api.getUserManager().getUser(playerUuid);
        if (onlineUser != null) {
            onlineUser.data().clear(NodeType.INHERITANCE::matches);
            onlineUser.data().add(InheritanceNode.builder(newGroupName).build());
            onlineUser.setPrimaryGroup(newGroupName);
            return api.getUserManager().saveUser(onlineUser).thenRun(() ->
                    Config.debugLuckPerms("setPlayerGroup (online): " + playerUuid + " → '" + newGroupName + "'"));
        }

        // User not online — fall back to loadUser (offline / pre-spawn use)
        return api.getUserManager().loadUser(playerUuid).thenCompose(user -> {
            if (user == null) return CompletableFuture.failedFuture(
                    new IllegalStateException("LP user not found for " + playerUuid));
            user.data().clear(NodeType.INHERITANCE::matches);
            user.data().add(InheritanceNode.builder(newGroupName).build());
            user.setPrimaryGroup(newGroupName);
            return api.getUserManager().saveUser(user).thenRun(() ->
                    Config.debugLuckPerms("setPlayerGroup (offline): " + playerUuid + " → '" + newGroupName + "'"));
        });
    }

    // ── Meta / prefix resolution ──────────────────────────────────────────────

    /**
     * Synchronously reads the resolved prefix for a bot from LP's in-memory meta
     * cache. Only works when the bot is online (LP has their {@code User} loaded).
     *
     * @param botUuid the bot's UUID
     * @return resolved prefix string, or {@code ""} if LP unavailable / no prefix
     */
    public static String getResolvedPrefix(UUID botUuid) {
        LuckPerms api = lp();
        if (api == null) return "";
        try {
            User user = api.getUserManager().getUser(botUuid);
            if (user == null) return "";
            String prefix = user.getCachedData().getMetaData().getPrefix();
            return prefix != null ? prefix : "";
        } catch (Exception e) {
            Config.debugLuckPerms("getResolvedPrefix error for " + botUuid + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Synchronously reads the resolved suffix for a bot from LP's in-memory meta
     * cache. Only works when the bot is online (LP has their {@code User} loaded).
     *
     * @param botUuid the bot's UUID
     * @return resolved suffix string, or {@code ""} if LP unavailable / no suffix
     */
    public static String getResolvedSuffix(UUID botUuid) {
        LuckPerms api = lp();
        if (api == null) return "";
        try {
            User user = api.getUserManager().getUser(botUuid);
            if (user == null) return "";
            String suffix = user.getCachedData().getMetaData().getSuffix();
            return suffix != null ? suffix : "";
        } catch (Exception e) {
            Config.debugLuckPerms("getResolvedSuffix error for " + botUuid + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Forces LuckPerms to refresh the cached metadata for a user.
     * Useful when LP hasn't finished calculating metadata after a group assignment.
     *
     * @param uuid the UUID of the user to refresh
     */
    public static void refreshUserCache(UUID uuid) {
        LuckPerms api = lp();
        if (api == null) return;
        try {
            User user = api.getUserManager().getUser(uuid);
            if (user == null) {
                // User not loaded yet, try loading them first
                api.getUserManager().loadUser(uuid).thenAccept(loadedUser -> {
                    if (loadedUser != null) {
                        loadedUser.getCachedData().invalidate();
                        Config.debugLuckPerms("Forced cache refresh for " + uuid + " (loaded first)");
                    }
                });
            } else {
                // User already loaded, just invalidate their cache
                user.getCachedData().invalidate();
                Config.debugLuckPerms("Forced cache refresh for " + uuid);
            }
        } catch (Exception e) {
            Config.debugLuckPerms("refreshUserCache error for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Synchronously reads the primary group for a bot from LP's in-memory cache.
     * Only works when the bot is online.
     *
     * @param botUuid the bot's UUID
     * @return primary group name, or {@code "default"} as fallback
     */
    public static String getPrimaryGroup(UUID botUuid) {
        LuckPerms api = lp();
        if (api == null) return "default";
        try {
            User user = api.getUserManager().getUser(botUuid);
            if (user == null) return "default";
            return user.getPrimaryGroup();
        } catch (Exception e) {
            return "default";
        }
    }

    /**
     * Asynchronously reads the primary group for a player UUID from LP storage.
     * Works even when the player is offline. Used by diagnostics and {@code /fpp rank list}.
     *
     * @param playerUuid the UUID to query
     * @return future containing the primary group name, or {@code "default"} on failure
     */
    public static CompletableFuture<String> getStoredPrimaryGroup(UUID playerUuid) {
        LuckPerms api = lp();
        if (api == null) return CompletableFuture.completedFuture("default");
        return api.getUserManager().loadUser(playerUuid)
                .thenApply(user -> user != null ? user.getPrimaryGroup() : "default")
                .exceptionally(ex -> "default");
    }


    // ── Group info / queries ──────────────────────────────────────────────────

    /**
     * Checks if the specified LP group exists (case-insensitive).
     *
     * @param name group name to check
     * @return {@code true} when LP is available and the group is loaded
     */
    public static boolean groupExists(String name) {
        LuckPerms api = lp();
        if (api == null || name == null || name.isBlank()) return false;
        return api.getGroupManager().getGroup(name.toLowerCase()) != null;
    }

    /**
     * Returns a sorted list of all LP group names currently loaded in memory.
     * Used for tab completion in {@code /fpp rank}.
     */
    public static List<String> getAllGroupNames() {
        LuckPerms api = lp();
        if (api == null) return Collections.emptyList();
        return api.getGroupManager().getLoadedGroups()
                .stream()
                .map(Group::getName)
                .sorted()
                .toList();
    }

    /**
     * Returns a human-readable summary of loaded LP groups and their weights.
     * Format: {@code name(w=N), ...} — used by {@code /fpp lpinfo}.
     */
    public static String buildGroupSummary() {
        LuckPerms api = lp();
        if (api == null) return "(LP unavailable)";
        try {
            StringBuilder sb = new StringBuilder();
            api.getGroupManager().getLoadedGroups().stream()
                    .sorted(Comparator.comparing(Group::getName))
                    .forEach(g -> {
                        int w = g.getWeight().orElse(0);
                        if (!sb.isEmpty()) sb.append(", ");
                        sb.append(g.getName()).append("(w=").append(w).append(')');
                    });
            return sb.isEmpty() ? "(none)" : sb.toString();
        } catch (Exception e) {
            return "(error: " + e.getMessage() + ")";
        }
    }

    // ── Deprecated aliases for backwards compatibility ────────────────────────

    /** @deprecated No-op — LP handles caching natively for NMS ServerPlayer entities. */
    @Deprecated
    public static void invalidateCache() {}

    /** @deprecated Use {@link #setPlayerGroup(UUID, String)} instead. */
    @Deprecated
    public static CompletableFuture<Void> addPlayerToGroup(UUID playerUuid, String groupName) {
        return setPlayerGroup(playerUuid, groupName);
    }

    /** @deprecated Use {@link #setPlayerGroup(UUID, String)} with "default" instead. */
    @Deprecated
    public static CompletableFuture<Void> cleanupBotUser(UUID playerUuid) {
        return setPlayerGroup(playerUuid, "default");
    }

    /** @deprecated Use {@link #getStoredPrimaryGroup(UUID)} instead. */
    @Deprecated
    public static CompletableFuture<String> getPlayerPrimaryGroup(UUID playerUuid) {
        return getStoredPrimaryGroup(playerUuid);
    }
}


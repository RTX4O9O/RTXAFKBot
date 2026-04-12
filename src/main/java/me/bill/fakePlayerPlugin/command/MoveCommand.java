package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Navigates a bot to an online player using an A* grid pathfinder,
 * OR patrols a bot through a user-defined list of waypoints in a loop.
 *
 * <h3>Player-follow mode</h3>
 * {@code /fpp move <bot> <player>} - navigates continuously toward the player.
 * Recalculates path when target moves beyond {@code pathfinding.follow-recalc-distance}
 * blocks or every {@code pathfinding.recalc-interval} ticks (both live-config values).
 *
 * <h3>Waypoint patrol mode</h3>
 * <ol>
 *   <li>Create a named route with {@code /fpp wp add <route>} (run multiple times
 *       to build a list of positions).</li>
 *   <li>{@code /fpp move <bot> --wp <route>} - start the patrol loop (bot visits
 *       position 1 → 2 → … → N → 1 → …, forever).</li>
 *   <li>{@code /fpp move <bot> --stop} - stop navigation.</li>
 * </ol>
 *
 * <h3>Navigation loop (1-tick interval)</h3>
 * <ol>
 *   <li>Handles WALK/ASCEND/DESCEND normally (face + forward + conditional jump).</li>
 *   <li>Handles PARKOUR: sprint + request jump when 1–3.5 blocks from landing.</li>
 *   <li>Handles BREAK: stop, face block, wait {@code pathfinding.break-ticks} ticks, break it.</li>
 *   <li>Handles PLACE: stop, wait {@code pathfinding.place-ticks} ticks, place bridge block.</li>
 *   <li>Stuck detection ({@code pathfinding.stuck-ticks} ticks) forces jump + recalc.
 *       Suppressed while breaking or placing.</li>
 *   <li>Falls back to direct face-and-walk when no path is found.</li>
 * </ol>
 */
public final class MoveCommand implements FppCommand {

    // ── Fields ────────────────────────────────────────────────────────────────

    private final FakePlayerManager manager;
    private final PathfindingService pathfinding;
    /**
     * Per-bot randomized waypoint order for `--random` patrols.
     * Maps botUuid → shuffled list of waypoint indices (0..N-1).
     * Each bot gets its own unique random sequence at patrol start.
     */
    private final Map<UUID, List<Integer>> randomWpOrder = new ConcurrentHashMap<>();
    /**
     * Route name currently being patrolled per bot (null when in player-follow or idle).
     * Used by BotPersistence to save patrol state across restarts.
     */
    private final Map<UUID, String>  activeRouteNames  = new ConcurrentHashMap<>();
    /** Whether the active patrol for each bot is in --random order. */
    private final Map<UUID, Boolean> activeRandomFlags = new ConcurrentHashMap<>();
    /**
     * Global named waypoint route registry.  Injected via {@link #setWaypointStore(WaypointStore)}.
     * Used by the {@code --wp <name>} flag.
     */
    private WaypointStore waypointStore;

    public MoveCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    /** Injects the global waypoint store so {@code --wp <name>} can look up destinations. */
    public void setWaypointStore(WaypointStore store) {
        this.waypointStore = store;
    }

    // ── FppCommand ────────────────────────────────────────────────────────────

    @Override public String getName()        { return "move"; }
    @Override public String getUsage()       { return "<bot|all> <player>  |  <bot|all> --wp <route> [--random]  |  <bot|all> --stop"; }
    @Override public String getDescription() { return "Navigate a bot (or all bots) to a player or patrol a named waypoint route in a loop."; }
    @Override public String getPermission()  { return Perm.MOVE; }
    @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.MOVE); }

    // ── execute ───────────────────────────────────────────────────────────────

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        // ── "all" target: apply command to every active bot ───────────────────
        if (args[0].equalsIgnoreCase("all")) {
            return executeAll(sender, args);
        }

        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Lang.get("move-bot-not-found", "name", args[0]));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("move-bot-not-online", "name", args[0]));
            return true;
        }

        // ── Flag sub-commands (--wp / --stop) ────────────────────────────────
        if (args.length >= 2 && args[1].startsWith("--")) {
            String flag = args[1].toLowerCase();

            if (flag.equals("--stop")) {
                if (!pathfinding.isNavigating(bot.getUniqueId(), PathfindingService.Owner.MOVE)) {
                    sender.sendMessage(Lang.get("move-not-navigating", "name", fp.getDisplayName()));
                } else {
                    cancelNavigation(bot.getUniqueId()); // releases lock, clears jump, stops movement
                    sender.sendMessage(Lang.get("move-stopped", "name", fp.getDisplayName()));
                }
                return true;
            }

            if (flag.equals("--wp")) {
                if (waypointStore == null || args.length < 3) {
                    sender.sendMessage(Lang.get("move-wp-usage"));
                    return true;
                }
                String wpName = args[2];
                boolean randomOrder = args.length >= 4 && args[3].equalsIgnoreCase("--random");

                List<Location> route = waypointStore.getRoute(wpName);
                if (route == null || route.isEmpty()) {
                    sender.sendMessage(Lang.get("move-wp-not-found", "name", wpName));
                    return true;
                }
                // Verify at least the first waypoint is in the same world
                Location first = route.get(0);
                if (first.getWorld() == null || !first.getWorld().equals(bot.getWorld())) {
                    sender.sendMessage(Lang.get("move-wp-different-world",
                            "name", fp.getDisplayName(), "waypoint", wpName));
                    return true;
                }
                cancelNavigation(bot.getUniqueId());
                // Record route state for persistence BEFORE starting the task
                activeRouteNames.put(bot.getUniqueId(), wpName);
                activeRandomFlags.put(bot.getUniqueId(), randomOrder);
                if (randomOrder) {
                    startRandomPatrol(bot, route);
                    sender.sendMessage(Lang.get("move-wp-started-random",
                            "name", fp.getDisplayName(), "waypoint", wpName,
                            "count", String.valueOf(route.size())));
                } else {
                    startPatrol(bot, route);
                    sender.sendMessage(Lang.get("move-wp-started",
                            "name", fp.getDisplayName(), "waypoint", wpName,
                            "count", String.valueOf(route.size())));
                }
                return true;
            }

            // Unknown flag - fall through to usage
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        // ── Player-follow mode: /fpp move <bot> <player> ──────────────────────
        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);  // Case-insensitive lookup
            if (target == null) {
                sender.sendMessage(Lang.get("player-not-found", "player", args[1]));
                return true;
            }

            if (!bot.getWorld().equals(target.getWorld())) {
                sender.sendMessage(Lang.get("move-different-world",
                        "name", fp.getDisplayName(), "player", target.getName()));
                return true;
            }

            cancelNavigation(bot.getUniqueId());
            startNavigation(bot, target);

            sender.sendMessage(Lang.get("move-navigating",
                    "name", fp.getDisplayName(), "player", target.getName()));
            return true;
        }

        // ── No second arg - show usage ────────────────────────────────────────
        sender.sendMessage(Lang.get("move-usage"));
        return true;
    }

    // ── "all" bulk handler ────────────────────────────────────────────────────

    /**
     * Handles {@code /fpp move all <flag...>} - applies the navigation command
     * to every currently-active bot.  Bots that are offline or in a different
     * world than the first waypoint / target player are silently skipped and
     * counted in the feedback message.
     */
    private boolean executeAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        String flag = args[1].toLowerCase();

        // ── all --stop ────────────────────────────────────────────────────────
        if (flag.equals("--stop")) {
            int stopped = 0;
            for (FakePlayer fp : manager.getActivePlayers()) {
                Player bot = fp.getPlayer();
                if (bot == null) continue;
                if (pathfinding.isNavigating(bot.getUniqueId(), PathfindingService.Owner.MOVE)) {
                    cancelNavigation(bot.getUniqueId());
                    stopped++;
                }
            }
            sender.sendMessage(Lang.get("move-all-stopped", "count", String.valueOf(stopped)));
            return true;
        }

        // ── all --wp <route> [--random] ───────────────────────────────────────
        if (flag.equals("--wp")) {
            if (waypointStore == null || args.length < 3) {
                sender.sendMessage(Lang.get("move-wp-usage"));
                return true;
            }
            String wpName = args[2];
            boolean randomOrder = args.length >= 4 && args[3].equalsIgnoreCase("--random");

            List<Location> route = waypointStore.getRoute(wpName);
            if (route == null || route.isEmpty()) {
                sender.sendMessage(Lang.get("move-wp-not-found", "name", wpName));
                return true;
            }

            int started = 0, skipped = 0;
            for (FakePlayer fp : manager.getActivePlayers()) {
                Player bot = fp.getPlayer();
                if (bot == null || !bot.isOnline()) { skipped++; continue; }
                Location first = route.get(0);
                if (first.getWorld() == null || !first.getWorld().equals(bot.getWorld())) { skipped++; continue; }
                cancelNavigation(bot.getUniqueId());
                activeRouteNames.put(bot.getUniqueId(), wpName);
                activeRandomFlags.put(bot.getUniqueId(), randomOrder);
                if (randomOrder) startRandomPatrol(bot, route);
                else             startPatrol(bot, route);
                started++;
            }

            if (randomOrder) {
                sender.sendMessage(Lang.get("move-all-wp-started-random",
                        "waypoint", wpName, "count", String.valueOf(started), "skipped", String.valueOf(skipped)));
            } else {
                sender.sendMessage(Lang.get("move-all-wp-started",
                        "waypoint", wpName, "count", String.valueOf(started), "skipped", String.valueOf(skipped)));
            }
            return true;
        }

        // ── all <player> (player-follow) ──────────────────────────────────────
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Lang.get("player-not-found", "player", args[1]));
            return true;
        }

        int started = 0, skipped = 0;
        for (FakePlayer fp : manager.getActivePlayers()) {
            Player bot = fp.getPlayer();
            if (bot == null || !bot.isOnline()) { skipped++; continue; }
            if (!bot.getWorld().equals(target.getWorld())) { skipped++; continue; }
            cancelNavigation(bot.getUniqueId());
            startNavigation(bot, target);
            started++;
        }
        sender.sendMessage(Lang.get("move-all-navigating",
                "player", target.getName(), "count", String.valueOf(started), "skipped", String.valueOf(skipped)));
        return true;
    }

    // ── Navigation state machine ──────────────────────────────────────────────

    private void startNavigation(@NotNull Player bot, @NotNull Player target) {
        final UUID botUuid = bot.getUniqueId();
        final UUID targetUuid = target.getUniqueId();
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) return;
        pathfinding.navigate(fp, new PathfindingService.NavigationRequest(
                PathfindingService.Owner.MOVE,
                () -> {
                    Player liveTarget = Bukkit.getPlayer(targetUuid);
                    if (liveTarget == null || !liveTarget.isOnline()) return null;
                    return liveTarget.getLocation();
                },
                Config.pathfindingArrivalDistance(),
                Config.pathfindingFollowRecalcDistance(),
                Integer.MAX_VALUE,
                null,
                () -> clearMoveState(botUuid),
                null
        ));
    }

    private void startPatrol(@NotNull Player bot, @NotNull List<Location> waypoints) {
        navigatePatrolStep(bot.getUniqueId(), new ArrayList<>(waypoints), 0);
    }

    private void startRandomPatrol(@NotNull Player bot, @NotNull List<Location> waypoints) {
        UUID botUuid = bot.getUniqueId();
        List<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < waypoints.size(); i++) shuffled.add(i);
        Collections.shuffle(shuffled, new Random());
        randomWpOrder.put(botUuid, shuffled);
        navigateRandomPatrolStep(botUuid, new ArrayList<>(waypoints), 0);
    }

    private void navigatePatrolStep(@NotNull UUID botUuid, @NotNull List<Location> waypoints, int idx) {
        if (waypoints.isEmpty()) { clearMoveState(botUuid); return; }
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) { clearMoveState(botUuid); return; }
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) { clearMoveState(botUuid); return; }

        int nextIdx = Math.floorMod(idx, waypoints.size());
        Location dest = waypoints.get(nextIdx);
        if (dest.getWorld() == null || !dest.getWorld().equals(bot.getWorld())) {
            clearMoveState(botUuid);
            return;
        }

        int arrivalIdx = (nextIdx + 1) % waypoints.size();
        pathfinding.navigate(fp, new PathfindingService.NavigationRequest(
                PathfindingService.Owner.MOVE,
                () -> dest,
                Config.pathfindingPatrolArrivalDistance(),
                0.0,
                10,
                () -> navigatePatrolStep(botUuid, waypoints, arrivalIdx),
                () -> clearMoveState(botUuid),
                () -> navigatePatrolStep(botUuid, waypoints, arrivalIdx)
        ));
    }

    private void navigateRandomPatrolStep(@NotNull UUID botUuid, @NotNull List<Location> waypoints, int cycleIdx) {
        if (waypoints.isEmpty()) { clearMoveState(botUuid); return; }
        List<Integer> order = randomWpOrder.get(botUuid);
        if (order == null || order.isEmpty()) { clearMoveState(botUuid); return; }

        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) { clearMoveState(botUuid); return; }
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) { clearMoveState(botUuid); return; }

        int normalizedCycle = cycleIdx;
        if (normalizedCycle >= order.size()) {
            Collections.shuffle(order, new Random());
            normalizedCycle = 0;
        }

        int actualIdx = order.get(normalizedCycle);
        Location dest = waypoints.get(actualIdx);
        if (dest.getWorld() == null || !dest.getWorld().equals(bot.getWorld())) {
            clearMoveState(botUuid);
            return;
        }

        int nextCycle = normalizedCycle + 1;
        pathfinding.navigate(fp, new PathfindingService.NavigationRequest(
                PathfindingService.Owner.MOVE,
                () -> dest,
                Config.pathfindingPatrolArrivalDistance(),
                0.0,
                10,
                () -> navigateRandomPatrolStep(botUuid, waypoints, nextCycle),
                () -> clearMoveState(botUuid),
                () -> navigateRandomPatrolStep(botUuid, waypoints, nextCycle)
        ));
    }

    private void clearMoveState(@NotNull UUID botUuid) {
        randomWpOrder.remove(botUuid);
        activeRouteNames.remove(botUuid);
        activeRandomFlags.remove(botUuid);
    }

    /**
     * Cancels the active navigation task for a bot and fully releases all navigation state:
     * nav-jump, navigation lock (re-enables head-AI), and movement inputs.
     * Safe to call even when no task is running.
     */
    private void cancelNavigation(@NotNull UUID botUuid) {
        pathfinding.cancel(botUuid);
        clearMoveState(botUuid);
    }

    /** Cancels all active navigation tasks. Called from {@code onDisable}. */
    public void cancelAll() {
        pathfinding.cancelAll(PathfindingService.Owner.MOVE);
        new ArrayList<>(activeRouteNames.keySet()).forEach(this::clearMoveState);
        new ArrayList<>(randomWpOrder.keySet()).forEach(this::clearMoveState);
    }

    /**
     * Cleans up all navigation state for a specific bot.
     * Call this when a bot is despawned/deleted to prevent memory leaks.
     *
     * @param botUuid the bot's UUID
     */
    public void cleanupBot(@NotNull UUID botUuid) {
        cancelNavigation(botUuid);
    }

    // ── Tab-complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String in = args[0].toLowerCase();
            // "all" bulk target
            if ("all".startsWith(in)) out.add("all");
            for (FakePlayer fp : manager.getActivePlayers())
                if (fp.getName().toLowerCase().startsWith(in)) out.add(fp.getName());
        } else if (args.length == 2) {
            String in = args[1].toLowerCase();
            // Flag options
            for (String flag : List.of("--wp", "--stop"))
                if (flag.startsWith(in)) out.add(flag);
            // Online player names (for player-follow mode) - exclude bots
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (manager.getByName(p.getName()) == null && p.getName().toLowerCase().startsWith(in))
                    out.add(p.getName());
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("--wp")) {
            // Suggest known waypoint names
            if (waypointStore != null) {
                String in = args[2].toLowerCase();
                for (String name : waypointStore.getNames())
                    if (name.startsWith(in)) out.add(name);
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("--wp")) {
            // Suggest --random flag after the waypoint name
            String in = args[3].toLowerCase();
            if ("--random".startsWith(in)) out.add("--random");
        }
        return out;
    }

    // ── Persistence API (used by BotPersistence to save/restore patrol state) ──

    /**
     * Returns the name of the waypoint route currently being patrolled by the given bot,
     * or {@code null} when the bot is in player-follow mode or not navigating.
     */
    @Nullable
    public String getActiveRouteForBot(@NotNull UUID botUuid) {
        return activeRouteNames.get(botUuid);
    }

    /**
     * Returns {@code true} if the bot's active patrol is in {@code --random} order.
     */
    public boolean isRandomPatrolForBot(@NotNull UUID botUuid) {
        Boolean v = activeRandomFlags.get(botUuid);
        return v != null && v;
    }

    /**
     * Resumes a waypoint patrol for a bot that was restored from persistence.
     * Cancels any existing navigation first, records the route name/random flag,
     * then starts the appropriate patrol loop.
     *
     * @param bot       the bot's Bukkit {@link Player}
     * @param route     snapshot of the waypoint positions (from {@link WaypointStore})
     * @param random    {@code true} to shuffle patrol order
     * @param routeName the route name (stored in {@link #activeRouteNames} for next save)
     */
    public void resumePatrol(@NotNull Player bot, @NotNull List<Location> route,
                             boolean random, @NotNull String routeName) {
        UUID uid = bot.getUniqueId();
        cancelNavigation(uid);
        activeRouteNames.put(uid, routeName);
        activeRandomFlags.put(uid, random);
        if (random) startRandomPatrol(bot, route);
        else        startPatrol(bot, route);
    }
}


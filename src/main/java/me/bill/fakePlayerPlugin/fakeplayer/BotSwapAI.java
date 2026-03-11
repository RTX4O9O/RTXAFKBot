package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Realistic bot swap / rotation system.
 *
 * <h3>Realism features</h3>
 * <ul>
 *   <li><b>Personality</b> — each bot is assigned one of three session archetypes
 *       at schedule-time: VISITOR (short), REGULAR (medium), LURKER (long).
 *       Personalities bias the session length independently of the global config.</li>
 *   <li><b>Session growth</b> — a bot that has swapped many times gradually stays
 *       longer per session (simulates a "returning regular").</li>
 *   <li><b>Time-of-day bias</b> — session lengths are shortened during off-peak
 *       hours (night) and extended during peak hours (evening), mirroring real
 *       player behaviour.</li>
 *   <li><b>Farewell chat</b> — before leaving the bot optionally sends a
 *       farewell message ("gtg", "brb", "cya", etc.).</li>
 *   <li><b>Greeting chat</b> — after the replacement bot joins it optionally
 *       sends a greeting ("hey", "back", "what did I miss", etc.).</li>
 *   <li><b>Reconnect simulation</b> — small configurable chance the rejoining
 *       bot keeps the same name (as if the player briefly lost connection).</li>
 *   <li><b>Staggered leave</b> — uses the global leave-delay config so the
 *       body disappears and leave-message fire with the same natural lag as
 *       a normal /fpp delete.</li>
 *   <li><b>AFK kick simulation</b> — low chance of a very short extra delay
 *       before rejoin, as if the server AFK-kicked the player.</li>
 * </ul>
 */
public final class BotSwapAI {

    // ── Personality archetypes ────────────────────────────────────────────────

    private enum Personality {
        /** Quick visitor — stays 30–60 % of the configured session range. */
        VISITOR,
        /** Typical player — uses the configured range as-is. */
        REGULAR,
        /** Long-term lurker — stays 150–250 % of the configured session range. */
        LURKER
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    /** uuid → active session countdown task */
    private final Map<UUID, BukkitTask> sessionTasks   = new ConcurrentHashMap<>();
    /** uuid → personality assigned at first schedule */
    private final Map<UUID, Personality> personalities = new ConcurrentHashMap<>();
    /** uuid → how many times this uuid has swapped (session count) */
    private final Map<UUID, Integer> swapCounts        = new ConcurrentHashMap<>();

    // ── Farewell / greeting message pools ────────────────────────────────────

    private static final List<String> FAREWELLS = List.of(
            "gtg", "brb", "cya", "gotta run", "be back later", "bye everyone",
            "peace ✌", "afk for a bit", "dinner time lol", "gonna log off",
            "see ya", "later", "gn everyone", "bye!", "logging off",
            "gotta go do stuff", "be back in a bit", "ttyl", "afk", "bbs",
            "ok gtg now", "bye byee", "seeya around", "taking a break",
            "stepping out for a sec", "gonna grab food", "one sec brb",
            "lag is killing me lol", "my pc is dying", "phone call brb"
    );

    private static final List<String> GREETINGS = List.of(
            "hey", "back", "yo", "hi", "wassup", "hello", "I'm back",
            "missed me?", "what did I miss?", "heyy", "yo what's good",
            "back at it", "ready to grind", "let's go", "sup everyone",
            "finally back", "ok I'm here", "heyo", "back again lol",
            "hi everyone", "just reconnected", "connection dropped lol",
            "stupid internet", "wifi fixed", "alright I'm back",
            "what happened while I was gone?", "did anything cool happen?",
            "back from dinner", "ok ready now", "lets get it"
    );

    // ── Constructor ───────────────────────────────────────────────────────────

    public BotSwapAI(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts a swap timer for {@code fp}.
     * Assigns a personality on the first call; subsequent calls (after rejoin)
     * inherit the same personality and increment the swap count.
     */
    public void schedule(FakePlayer fp) {
        if (!Config.swapEnabled()) return;
        cancel(fp.getUuid());

        // Assign personality once per uuid
        personalities.computeIfAbsent(fp.getUuid(), u -> randomPersonality());

        long sessionTicks = sessionTicks(fp.getUuid());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> doLeave(fp), sessionTicks);
        sessionTasks.put(fp.getUuid(), task);

        Config.debug("Swap scheduled for " + fp.getName()
                + " [" + personalities.get(fp.getUuid()) + "]"
                + " in " + (sessionTicks / 20) + "s"
                + " (swap #" + swapCounts.getOrDefault(fp.getUuid(), 0) + ").");
    }

    /** Cancels the swap timer for {@code uuid}. */
    public void cancel(UUID uuid) {
        BukkitTask t = sessionTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    /** Cancels all swap timers (called on plugin disable). */
    public void cancelAll() {
        sessionTasks.values().forEach(BukkitTask::cancel);
        sessionTasks.clear();
        personalities.clear();
        swapCounts.clear();
    }

    // ── Leave phase ───────────────────────────────────────────────────────────

    private void doLeave(FakePlayer fp) {
        if (manager.getByUuid(fp.getUuid()) == null) return;
        sessionTasks.remove(fp.getUuid());

        // Capture location BEFORE removing from world
        Location loc = currentLocation(fp);
        if (loc == null) {
            Config.debug("Swap: no valid location for " + fp.getName() + " — skipping.");
            return;
        }

        // Snapshot identity before name is freed
        final String leavingName = fp.getDisplayName();
        final UUID   leavingUuid = fp.getUuid();
        final int    swapCount   = swapCounts.getOrDefault(leavingUuid, 0);

        Config.debug("Swap leave: " + leavingName
                + " (swap #" + swapCount + ", personality=" + personalities.get(leavingUuid) + ")");

        // ── 1. Farewell chat (before body disappears) ─────────────────────
        if (Config.swapFarewellChat() && shouldChat()) {
            sendBotChat(fp, randomFrom(FAREWELLS));
        }

        // ── 2. Short "packing up" pause (makes it feel natural) ──────────
        long leaveDelay = leaveStaggerTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            // ── 3. Visual remove ──────────────────────────────────────────
            manager.swapRemove(fp);
            FakePlayerBody.removeAll(fp);
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player p : online) PacketHelper.sendTabListRemove(p, fp);

            if (Config.leaveMessage()) {
                BotBroadcast.broadcastLeaveByDisplayName(leavingName);
            }

            // ── 4. Schedule rejoin ────────────────────────────────────────
            long rejoinTicks = rejoinTicks(swapCount);
            Config.debug("Swap: " + leavingName + " rejoining in " + (rejoinTicks / 20) + "s.");
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> doRejoin(loc, leavingName, leavingUuid, swapCount + 1),
                    rejoinTicks);

        }, leaveDelay);
    }

    // ── Rejoin phase ──────────────────────────────────────────────────────────

    private void doRejoin(Location loc, String oldName, UUID oldUuid, int newSwapCount) {
        if (!Config.swapEnabled()) return;

        // Decide name: reconnect (same name) or new name
        boolean reconnect = ThreadLocalRandom.current().nextDouble()
                < Config.swapReconnectChance();
        String forcedName = reconnect ? oldName : null;

        Config.debug("Swap rejoin: " + (reconnect ? "reconnect as " + oldName : "new name")
                + " at " + loc.getWorld().getName()
                + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());

        // Spawn via manager — picks name, creates FakePlayer, does full visual chain
        int spawned = manager.spawnSwap(loc, forcedName);

        if (spawned <= 0) {
            Config.debug("Swap rejoin failed (no names available or at limit). Retrying in 30s.");
            // Retry in 30 s — might free up if another bot gets deleted
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> doRejoin(loc, oldName, oldUuid, newSwapCount), 600L);
            return;
        }

        // Find the newly spawned bot to attach personality + swap count + greeting
        // Delay lookup slightly so finishSpawn has registered it
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FakePlayer newBot = reconnect
                    ? findByName(oldName)
                    : findNewest(loc);

            if (newBot == null) return;

            // Carry over personality + increment swap count
            UUID newUuid = newBot.getUuid();
            personalities.putIfAbsent(newUuid, personalities.getOrDefault(oldUuid, randomPersonality()));
            swapCounts.put(newUuid, newSwapCount);

            // Greeting chat — short delay after join message
            if (Config.swapGreetingChat() && shouldChat()) {
                long greetDelay = 20L + ThreadLocalRandom.current().nextInt(60); // 1–4 s
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> sendBotChat(newBot, randomFrom(GREETINGS)), greetDelay);
            }
        }, 10L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Session duration in ticks — factors in:
     * personality multiplier, session growth (returning regulars stay longer),
     * time-of-day bias, and per-bot jitter.
     */
    private long sessionTicks(UUID uuid) {
        int min = Config.swapSessionMin();
        int max = Math.max(min + 1, Config.swapSessionMax());
        int base = min + ThreadLocalRandom.current().nextInt(max - min + 1);

        // Personality multiplier
        Personality p = personalities.getOrDefault(uuid, Personality.REGULAR);
        double multiplier = switch (p) {
            case VISITOR -> 0.3 + ThreadLocalRandom.current().nextDouble() * 0.3; // 30–60 %
            case REGULAR -> 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4; // 80–120 %
            case LURKER  -> 1.5 + ThreadLocalRandom.current().nextDouble(); // 150–250 %
        };

        // Session growth — each swap adds up to 15 % more stay time (caps at +75 %)
        int count = swapCounts.getOrDefault(uuid, 0);
        double growth = Math.min(1.0 + count * 0.15, 1.75);

        // Time-of-day bias
        double timeBias = timeOfDayBias();

        // Jitter
        int jitter = Config.swapJitter();
        int jitterOffset = jitter > 0
                ? ThreadLocalRandom.current().nextInt(jitter * 2 + 1) - jitter
                : 0;

        int seconds = (int) Math.round(base * multiplier * growth * timeBias) + jitterOffset;
        seconds = Math.max(15, seconds); // hard floor: 15 s

        return (long) seconds * 20L;
    }

    /**
     * Rejoin gap in ticks — LURKERS wait longer before coming back;
     * after many swaps the gap shrinks (the player "knows the server now").
     */
    private long rejoinTicks(int swapCount) {
        int min = Config.swapRejoinDelayMin();
        int max = Math.max(min + 1, Config.swapRejoinDelayMax());
        int seconds = min + ThreadLocalRandom.current().nextInt(max - min + 1);

        // Shrink gap slightly with experience (familiarity)
        double familiarity = Math.max(0.5, 1.0 - swapCount * 0.05);
        seconds = (int) Math.round(seconds * familiarity);

        // AFK-kick simulation — rare long gap
        if (ThreadLocalRandom.current().nextInt(100) < Config.swapAfkKickChance()) {
            seconds += 60 + ThreadLocalRandom.current().nextInt(120); // +1–3 min
            Config.debug("Swap: AFK-kick simulation — extra " + seconds + "s gap.");
        }

        seconds = Math.max(2, seconds);
        return (long) seconds * 20L;
    }

    /**
     * Time-of-day bias: returns a multiplier for session length.
     * Peak hours (18–22) → longer sessions (up to 1.4×).
     * Off-peak (01–06)   → shorter sessions (down to 0.5×).
     * Shoulder hours     → linear interpolation.
     */
    private double timeOfDayBias() {
        if (!Config.swapTimeOfDayBias()) return 1.0;
        int hour = LocalTime.now().getHour();
        // Peak: 18–22  → 1.4
        if (hour >= 18 && hour <= 22) return 1.4;
        // Off-peak: 1–5 → 0.5
        if (hour >= 1  && hour <= 5)  return 0.5;
        // Ramp up: 6–17
        if (hour >= 6  && hour <= 17) return 0.5 + (hour - 6) * (0.9 / 12.0);
        // Late night: 23–0
        return 1.4 - (hour == 23 ? 0.3 : 0.7);
    }

    /** Stagger ticks for the leave phase — matches the global leave-delay config. */
    private long leaveStaggerTicks() {
        int min = Config.leaveDelayMin();
        int max = Math.max(min, Config.leaveDelayMax());
        if (max <= 0) return 1L;
        return Math.max(1L, min + ThreadLocalRandom.current().nextInt(max - min + 1));
    }

    /** Whether to send a chat message this swap cycle (70 % base chance). */
    private boolean shouldChat() {
        if (!Config.fakeChatEnabled()) return false;
        if (Config.fakeChatRequirePlayer() && Bukkit.getOnlinePlayers().isEmpty()) return false;
        return ThreadLocalRandom.current().nextDouble() < 0.70;
    }

    private void sendBotChat(FakePlayer bot, String message) {
        if (!bot.getPhysicsEntity().isValid()) return;
        Component chatLine = Component.empty()
                .append(Component.text("<", NamedTextColor.WHITE))
                .append(Component.text(bot.getName(), TextColor.color(0x0079FF)))
                .append(Component.text("> ", NamedTextColor.WHITE))
                .append(Component.text(message, NamedTextColor.WHITE));
        Bukkit.getServer().broadcast(chatLine);
    }

    private static Personality randomPersonality() {
        int r = ThreadLocalRandom.current().nextInt(100);
        if (r < 25) return Personality.VISITOR; // 25 %
        if (r < 75) return Personality.REGULAR; // 50 %
        return Personality.LURKER;              // 25 %
    }

    private static <T> T randomFrom(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private Location currentLocation(FakePlayer fp) {
        if (fp.getPhysicsEntity() != null && fp.getPhysicsEntity().isValid())
            return fp.getPhysicsEntity().getLocation().clone();
        if (fp.getSpawnLocation() != null)
            return fp.getSpawnLocation().clone();
        return null;
    }

    private FakePlayer findByName(String name) {
        for (FakePlayer fp : manager.getActivePlayers())
            if (fp.getName().equalsIgnoreCase(name)) return fp;
        return null;
    }

    /** Finds the most recently spawned bot within 32 blocks of the given location. */
    private FakePlayer findNewest(Location loc) {
        FakePlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (fp.getPhysicsEntity() == null || !fp.getPhysicsEntity().isValid()) continue;
            if (!fp.getPhysicsEntity().getWorld().equals(loc.getWorld())) continue;
            double d = fp.getPhysicsEntity().getLocation().distanceSquared(loc);
            if (d < bestDist && d < 1024) { // within 32 blocks
                bestDist = d;
                best = fp;
            }
        }
        return best;
    }
}

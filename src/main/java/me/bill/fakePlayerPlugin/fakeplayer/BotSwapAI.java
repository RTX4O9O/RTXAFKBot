package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives the bot swap / session-rotation system.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Every active AFK bot receives a randomised session countdown at startup
 *       (or when the feature is enabled via {@code /fpp swap on}).</li>
 *   <li>When the countdown expires the bot says an optional farewell message,
 *       waits a short "packing-up" pause, then leaves via the normal
 *       {@link FakePlayerManager#delete(String)} path — the vanilla leave
 *       message fires through NMS just like a real player disconnect.</li>
 *   <li>After a configurable absence period the bot respawns at its last
 *       known location, optionally with the same or a fresh random name, and
 *       says a greeting message.</li>
 *   <li>Each bot is assigned a {@link Personality} that scales its session
 *       duration. Regulars also accumulate a small "growth" multiplier so
 *       long-standing bots naturally log longer sessions over time.</li>
 * </ol>
 *
 * <h3>Config keys</h3>
 * All keys live under {@code swap.*} in {@code config.yml}.  See
 * {@link Config#swapEnabled()} and siblings for the full list.
 */
public final class BotSwapAI {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    // ── Farewell / Greeting pools ─────────────────────────────────────────────

    private static final List<String> FAREWELLS = List.of(
            "gtg", "brb", "bye!", "cya", "gotta go", "peace ✌",
            "afk for a bit", "dinner time lol", "gonna log off",
            "see ya", "later", "gn everyone", "logging off",
            "gotta do stuff", "be back in a bit", "ttyl", "bbs",
            "ok gtg now", "bye byee", "seeya around", "taking a break",
            "stepping out for a sec", "gonna grab food", "one sec brb",
            "lag is killing me lol", "my pc is dying", "phone call brb",
            "back in a few", "gonna touch grass real quick", "grabbing dinner brb",
            "afk", "need a break", "stepping away", "be right back"
    );

    private static final List<String> GREETINGS = List.of(
            "back", "hey", "yo", "hi", "wassup", "I'm back",
            "missed me?", "what did I miss?", "heyy", "yo what's good",
            "back at it", "ready to grind", "let's go", "sup everyone",
            "finally back", "ok I'm here", "heyo", "back again lol",
            "hi everyone", "just reconnected", "connection dropped lol",
            "stupid internet", "wifi fixed", "alright I'm back",
            "what happened while I was gone?", "did anything cool happen?",
            "back from dinner", "ok ready now", "lets get it",
            "recharged and back", "I return", "back from the void",
            "here again lol", "connection issues smh"
    );

    // ── Personality ───────────────────────────────────────────────────────────

    /**
     * Per-bot personality that scales session duration and influences how
     * often the bot participates in farewell / greeting chat.
     */
    public enum Personality {
        /** Average session times, normal behaviour. */
        CASUAL(1.0),
        /** Power player — longer sessions. */
        GRINDER(1.6),
        /** Social type — shorter sessions, quick to return. */
        SOCIAL(0.65),
        /** Quiet presence — very long sessions, rarely leaves. */
        LURKER(2.2),
        /** High activity — short cycles, frequent rejoins. */
        ACTIVE(0.45);

        /** Multiplied against the configured base session duration. */
        public final double sessionMultiplier;

        Personality(double mult) { sessionMultiplier = mult; }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Active session countdown task per live bot UUID. */
    private final Map<UUID, Integer>     sessionTimers  = new ConcurrentHashMap<>();
    /** "Packing up" pre-delete delay task per bot UUID (started when session expires). */
    private final Map<UUID, Integer>     packingTimers  = new ConcurrentHashMap<>();
    /** Pending rejoin task per old (removed) bot UUID. */
    private final Map<UUID, Integer>     rejoinTimers   = new ConcurrentHashMap<>();
    /** Personality assigned to each UUID (active AND awaiting rejoin). */
    private final Map<UUID, Personality> personalities = new ConcurrentHashMap<>();
    /** Cumulative swap cycles completed per UUID. */
    private final Map<UUID, Integer>     swapCounts    = new ConcurrentHashMap<>();
    /** Count of bots currently offline awaiting rejoin. */
    private final AtomicInteger          swappedOut    = new AtomicInteger(0);
    /** Epoch-ms when each bot's current session will expire (for status display). */
    private final Map<UUID, Long>        sessionExpiry = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public BotSwapAI(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;

        // 1-second watcher: schedule new bots, clean up removed ones.
        // 2-tick startup delay so the manager is fully ready.
        Bukkit.getScheduler().runTaskTimer(plugin, this::syncBotSchedules, 40L, 20L);
    }

    // ── Sync loop ─────────────────────────────────────────────────────────────

    private void syncBotSchedules() {
        if (!Config.swapEnabled()) return;

        for (FakePlayer fp : manager.getActivePlayers()) {
            UUID id = fp.getUuid();
            // PVP bots never swap out
            if (fp.getBotType() == BotType.PVP) continue;
            if (!sessionTimers.containsKey(id)) {
                assignPersonality(id);
                schedule(fp);
            }
        }

        // Cancel session/packing timers for bots that were removed externally
        sessionTimers.entrySet().removeIf(e -> {
            if (manager.getByUuid(e.getKey()) == null) {
                Bukkit.getScheduler().cancelTask(e.getValue());
                cleanupState(e.getKey());
                return true;
            }
            return false;
        });
        packingTimers.entrySet().removeIf(e -> {
            if (manager.getByUuid(e.getKey()) == null) {
                Bukkit.getScheduler().cancelTask(e.getValue());
                return true;
            }
            return false;
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Schedules (or reschedules) a session countdown for {@code fp}.
     * No-op when swap is disabled.
     *
     * @param fp the fake player to schedule
     */
    public void schedule(FakePlayer fp) {
        if (!Config.swapEnabled()) return;
        if (fp.getBotType() == BotType.PVP) return;

        UUID id = fp.getUuid();
        // Cancel any existing timer first
        Integer prev = sessionTimers.remove(id);
        if (prev != null) Bukkit.getScheduler().cancelTask(prev);

        assignPersonality(id);
        long delay = sessionDurationTicks(id);
        sessionExpiry.put(id, System.currentTimeMillis() + (delay * 50L)); // 50 ms per tick

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sessionTimers.remove(id);
            FakePlayer current = manager.getByUuid(id);
            if (current != null) doLeave(current);
        }, delay).getTaskId();

        sessionTimers.put(id, taskId);
        Config.debugChat("[SwapAI] Session scheduled for " + fp.getName()
                + " (delay=" + (delay / 20) + "s, personality="
                + personalities.getOrDefault(id, Personality.CASUAL).name().toLowerCase() + ")");
    }

    /**
     * Cancels the swap timer for a specific bot.
     * Called when the bot is removed externally (e.g. {@code /fpp despawn}).
     *
     * @param uuid the UUID of the bot being removed
     */
    public void cancel(UUID uuid) {
        Integer tid = sessionTimers.remove(uuid);
        if (tid != null) Bukkit.getScheduler().cancelTask(tid);
        Integer ptid = packingTimers.remove(uuid);
        if (ptid != null) Bukkit.getScheduler().cancelTask(ptid);
        Integer rid = rejoinTimers.remove(uuid);
        if (rid != null) {
            Bukkit.getScheduler().cancelTask(rid);
            swappedOut.decrementAndGet();
        }
        cleanupState(uuid);
    }

    /** Cancels ALL pending swap / rejoin timers. */
    public void cancelAll() {
        sessionTimers.values().forEach(Bukkit.getScheduler()::cancelTask);
        packingTimers.values().forEach(Bukkit.getScheduler()::cancelTask);
        rejoinTimers.values().forEach(Bukkit.getScheduler()::cancelTask);
        sessionTimers.clear();
        packingTimers.clear();
        rejoinTimers.clear();
        personalities.clear();
        swapCounts.clear();
        sessionExpiry.clear();
        swappedOut.set(0);
    }

    /**
     * Forces an immediate leave for the named bot.
     *
     * @param botName name of the active bot to swap out
     * @return {@code true} if found and the leave was triggered, {@code false} if not found
     */
    public boolean triggerNow(String botName) {
        FakePlayer fp = findByName(botName);
        if (fp == null) return false;
        UUID id = fp.getUuid();
        Integer tid = sessionTimers.remove(id);
        if (tid != null) Bukkit.getScheduler().cancelTask(tid);
        // Also cancel any existing packing-up task to avoid a double-leave
        Integer ptid = packingTimers.remove(id);
        if (ptid != null) Bukkit.getScheduler().cancelTask(ptid);
        doLeave(fp);
        return true;
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    /** Number of bots currently offline and waiting to rejoin. */
    public int getSwappedOutCount() { return swappedOut.get(); }

    /** Number of active bots currently in their session countdown. */
    public int getActiveSessionCount() { return sessionTimers.size(); }

    /** UUIDs of all active bots that have a live session timer. */
    public Set<UUID> getActiveSessions() {
        return Collections.unmodifiableSet(new HashSet<>(sessionTimers.keySet()));
    }

    /**
     * Returns how many seconds until the NEXT bot swaps out (the one with the
     * soonest expiry), or {@code -1} if no sessions are scheduled.
     */
    public long getNextSwapSeconds() {
        long now = System.currentTimeMillis();
        long soonest = Long.MAX_VALUE;
        for (long expiry : sessionExpiry.values()) {
            if (expiry < soonest) soonest = expiry;
        }
        if (soonest == Long.MAX_VALUE) return -1;
        return Math.max(0, (soonest - now) / 1000L);
    }

    /**
     * Human-readable personality label for a bot, e.g. {@code "casual"}.
     * Returns {@code "unset"} if no personality has been assigned yet.
     */
    public String getPersonalityLabel(UUID uuid) {
        Personality p = personalities.get(uuid);
        return p != null ? p.name().toLowerCase() : "unset";
    }

    /** How many complete swap cycles this bot UUID has finished. */
    public int getSwapCount(UUID uuid) {
        return swapCounts.getOrDefault(uuid, 0);
    }

    // ── Leave phase ───────────────────────────────────────────────────────────

    private void doLeave(FakePlayer fp) {
        if (!Config.swapEnabled()) return;

        // Respect max-swapped-out limit
        int maxOut = Config.swapMaxSwappedOut();
        if (maxOut > 0 && swappedOut.get() >= maxOut) {
            Config.debugChat("[SwapAI] " + fp.getName() + " leave skipped (maxSwappedOut="
                    + maxOut + "), rescheduling.");
            schedule(fp);
            return;
        }

        UUID  leavingUuid = fp.getUuid();
        Personality p     = personalities.getOrDefault(leavingUuid, Personality.CASUAL);
        int   newCount    = swapCounts.getOrDefault(leavingUuid, 0) + 1;
        Location lastLoc  = fp.getLiveLocation();
        String oldName    = fp.getName();

        Config.debugChat("[SwapAI] " + oldName + " starting leave (swap #" + newCount
                + ", personality=" + p.name().toLowerCase() + ")");

        // 1. Farewell chat — fires before the body disappears
        if (Config.swapFarewellChat() && shouldChat()) {
            sendBotChat(fp, randomFrom(FAREWELLS));
        }

        // 2. Natural "packing up" pause (1–5 s) so farewell message lands before leave notification
        long preDeleteDelay = 20L + ThreadLocalRandom.current().nextInt(80);

        // Track this task so cancelAll() / cancel() can stop it while it is pending.
        int packingId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            packingTimers.remove(leavingUuid);

            // Guard: swap disabled or bot manually removed while we were waiting
            if (!Config.swapEnabled()) return;
            if (manager.getByName(oldName) == null) return;

            // 3. Normal delete — fires vanilla leave message via NMS, cleans up tab-list etc.
            // Remove from swap state BEFORE calling manager.delete() to prevent the
            // delete() path from calling cancel(uuid) and cleaning up state we still need.
            sessionTimers.remove(leavingUuid);
            sessionExpiry.remove(leavingUuid);
            swappedOut.incrementAndGet();

            // 4. Build the rejoin delay: absence + native leave-delay buffer
            int absMin = Config.swapAbsenceMin();
            int absMax = Math.max(absMin, Config.swapAbsenceMax());
            int absSec = absMin + (absMax > absMin
                    ? ThreadLocalRandom.current().nextInt(absMax - absMin + 1) : 0);
            long absenceTicks     = Math.max(40L, (long) absSec * 20L);
            long leaveBuffer      = (long) Math.max(Config.leaveDelayMin(), Config.leaveDelayMax()) + 10L;
            long totalRejoinDelay = absenceTicks + leaveBuffer;

            int rid = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                rejoinTimers.remove(leavingUuid);
                doRejoin(lastLoc, oldName, newCount, p);
            }, totalRejoinDelay).getTaskId();

            rejoinTimers.put(leavingUuid, rid);
            Config.debugChat("[SwapAI] " + oldName + " offline for ~" + absSec
                    + "s — rejoining in " + (totalRejoinDelay / 20) + "s.");

            // Delete AFTER the rejoin timer is registered so cancelAll() can reach it.
            manager.delete(oldName);

        }, preDeleteDelay).getTaskId();

        packingTimers.put(leavingUuid, packingId);
    }

    // ── Rejoin phase ──────────────────────────────────────────────────────────

    private void doRejoin(Location loc, String oldName, int newSwapCount, Personality p) {
        swappedOut.decrementAndGet();

        if (!Config.swapEnabled()) return;
        if (loc == null || loc.getWorld() == null) return;

        // Name selection: reuse old name if configured and currently available
        String customName = null;
        if (Config.swapSameNameOnRejoin() && !manager.isNameUsed(oldName)) {
            customName = oldName;
        }

        // Snapshot active UUIDs so we can identify the newly spawned bot
        Set<UUID> before = manager.getActiveUUIDs();

        int result = manager.spawn(loc, 1, null, customName, true);
        if (result <= 0) {
            Config.debugChat("[SwapAI] Rejoin failed for ex-bot '" + oldName
                    + "' (spawn result=" + result + ")");
            return;
        }

        // Wait a tick for the NMS spawn + visual chain to settle
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FakePlayer newBot = null;
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (!before.contains(fp.getUuid())) {
                    newBot = fp;
                    break;
                }
            }

            if (newBot == null) {
                Config.debugChat("[SwapAI] Rejoin: could not find new bot after spawn for '"
                        + oldName + "'");
                return;
            }

            // Carry personality and swap count forward
            personalities.put(newBot.getUuid(), p);
            swapCounts.put(newBot.getUuid(), newSwapCount);

            Config.debugChat("[SwapAI] " + newBot.getName() + " rejoined (swap #"
                    + newSwapCount + ", personality=" + p.name().toLowerCase() + ")");

            // Greeting chat — slight delay after the join message
            if (Config.swapGreetingChat() && shouldChat()) {
                UUID newId = newBot.getUuid();
                long greetDelay = 20L + ThreadLocalRandom.current().nextInt(60);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    FakePlayer b = manager.getByUuid(newId);
                    if (b != null) sendBotChat(b, randomFrom(GREETINGS));
                }, greetDelay);
            }

            // Start next session countdown
            schedule(newBot);
        }, 10L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Calculates session duration in ticks for a bot.
     * Factors in personality multiplier and a growth bonus for experienced cyclers.
     */
    private long sessionDurationTicks(UUID botUuid) {
        Personality p   = personalities.getOrDefault(botUuid, Personality.CASUAL);
        int minSec      = Config.swapSessionMin();
        int maxSec      = Math.max(minSec, Config.swapSessionMax());
        int spread      = maxSec - minSec;
        int baseSec     = minSec + (spread > 0 ? ThreadLocalRandom.current().nextInt(spread + 1) : 0);
        // Growth: +8% per completed swap cycle, capped at +40% (5 cycles)
        int count       = swapCounts.getOrDefault(botUuid, 0);
        double growth   = 1.0 + (Math.min(count, 5) * 0.08);
        long ticks      = (long)(baseSec * p.sessionMultiplier * growth * 20.0);
        return Math.max(200L, ticks); // minimum 10 s
    }

    /** {@code true} when fake-chat is enabled and at least one real player is online. */
    private boolean shouldChat() {
        if (!Config.fakeChatEnabled()) return false;
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            if (manager.getByUuid(p.getUniqueId()) == null) return true; // at least one real player
        }
        // No real players — still fire if require-player-online is off
        return !Config.fakeChatRequirePlayer()
                && ThreadLocalRandom.current().nextDouble() < 0.70;
    }

    private void sendBotChat(FakePlayer bot, String message) {
        org.bukkit.entity.Player entity = bot.getPlayer();
        if (entity == null || !entity.isValid() || !entity.isOnline()) return;
        BotChatAI.dispatchChat(entity, message);
    }

    private void assignPersonality(UUID botUuid) {
        personalities.computeIfAbsent(botUuid, k -> randomPersonality());
    }

    private void cleanupState(UUID uuid) {
        personalities.remove(uuid);
        swapCounts.remove(uuid);
        sessionExpiry.remove(uuid);
    }

    private static Personality randomPersonality() {
        double r = ThreadLocalRandom.current().nextDouble();
        if      (r < 0.18) return Personality.GRINDER;
        else if (r < 0.36) return Personality.SOCIAL;
        else if (r < 0.50) return Personality.LURKER;
        else if (r < 0.65) return Personality.ACTIVE;
        else               return Personality.CASUAL;
    }

    private static <T> T randomFrom(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private FakePlayer findByName(String name) {
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (fp.getName().equalsIgnoreCase(name)) return fp;
        }
        return null;
    }
}


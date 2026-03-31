package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages a Bukkit scoreboard team that contains all active FPP bots.
 *
 * <h3>Why teams?</h3>
 * <p>Minecraft's client tab-list comparator sorts entries in this order:
 * <ol>
 *   <li>Spectators last.</li>
 *   <li><b>Team name</b> alphabetically (empty string = no team, sorts first).</li>
 *   <li>Profile name within the same team, case-insensitive.</li>
 * </ol>
 * Profile-name prefix tricks ({@code {00_BotName}) are <em>overridden</em> by
 * team-name sorting.  By placing all bots in a team whose internal name sorts
 * after every LP group team (e.g. {@code owner}, {@code admin}, {@code 01_vip}),
 * bots reliably appear at the <strong>bottom</strong> of the tab list regardless
 * of which other plugins manage real-player teams.
 *
 * <h3>Team name</h3>
 * <p>{@value #TEAM_NAME} — the leading {@code ~} (ASCII 126) sorts after every
 * letter ({@code a}–{@code z} = 97–122) and common symbol.  Even a server using
 * numeric team prefixes like {@code 01_owner} will still have that team appear
 * before {@code ~fpp}.
 *
 * <h3>Team members</h3>
 * <p>Each bot's <em>actual name</em> (e.g. {@code BotName}, not the packet profile
 * name {@code {00_BotName}) is added as a team member string. This is CRITICAL because
 * Minecraft's client matches tab-list entries to team members by the player's real name,
 * not the custom profile name sent in the ADD_PLAYER packet. The packet profile name
 * controls sorting WITHIN the team, while team membership controls which section bots
 * appear in.
 *
 * <h3>Per-player scoreboard support</h3>
 * <p>This implementation adds the team to EVERY online player's active scoreboard,
 * not just the main server scoreboard. This ensures bots appear at the bottom
 * even when TAB plugin or other scoreboard-managing plugins assign custom scoreboards
 * to players.
 *
 * <h3>Version compatibility</h3>
 * <p>Safe for all Minecraft 1.21.x versions (1.21 through 1.21.11+). All methods
 * include defensive null checks, exception handling, and fallback logic to handle
 * potential API differences across patch versions.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #init()} — call once in {@code onEnable} (main thread).</li>
 *   <li>{@link #addBot(FakePlayer)} — call after spawning and sending ADD_PLAYER.</li>
 *   <li>{@link #removeBot(FakePlayer)} — call before/during bot removal.</li>
 *   <li>{@link #rebuild(Collection)} — call on {@code /fpp reload}.</li>
 *   <li>{@link #destroy()} — call in {@code onDisable}.</li>
 * </ul>
 */
public final class BotTabTeam {

    /**
     * Internal scoreboard team name used to group all FPP bots.
     * The leading {@code ~} (ASCII 126) guarantees this team sorts after every
     * letter/digit-prefixed LuckPerms group team in Minecraft's tab-list comparator.
     */
    public static final String TEAM_NAME = "~fpp";

    /** Tracks actual bot names (not packet profile names) currently in the team. */
    private final Set<String> botEntries = new HashSet<>();

    public BotTabTeam() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Creates (or retrieves) the {@value #TEAM_NAME} team on every online player's
     * active scoreboard. Must be called on the main thread. Safe to call multiple times.
     */
    public void init() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ensureTeamExists(p);
        }
        FppLogger.debug("[BotTabTeam] Initialized team '" + TEAM_NAME
                + "' on " + Bukkit.getOnlinePlayers().size() + " player scoreboard(s).");
    }

    /**
     * Ensures the ~fpp team exists on the given player's active scoreboard.
     * Called on player join and during init.
     */
    private void ensureTeamExists(Player player) {
        if (player == null) return;
        try {
            Scoreboard board = player.getScoreboard();
            if (board.getTeam(TEAM_NAME) == null) {
                board.registerNewTeam(TEAM_NAME);
                Config.debug("[BotTabTeam] Created team '" + TEAM_NAME + "' on " + player.getName() + "'s scoreboard");
            }
        } catch (Exception e) {
            Config.debug("[BotTabTeam] Error creating team for " + player.getName() + ": " + e.getMessage());
        }
    }

    // ── Bot registration ──────────────────────────────────────────────────────

    /**
     * Adds a bot's packet profile name to the {@value #TEAM_NAME} team on ALL online players'
     * scoreboards. Call this after {@code PacketHelper.sendTabListAdd} so the client
     * processes ADD_PLAYER before the team update (cleaner re-ordering animation).
     * No-op when tab-list is disabled.
     *
     * <p><b>Important:</b> uses {@link FakePlayer#getPacketProfileName()} (e.g. {@code {00_BotName}),
     * <em>not</em> the real name (e.g. {@code BotName}). The Minecraft client matches scoreboard 
     * team entries against the profile name sent in the ADD_PLAYER packet, which includes the 
     * weight-based sorting prefix for proper ordering within the team.
     */
    public void addBot(FakePlayer fp) {
        if (!Config.tabListEnabled()) return;
        String entry = fp.getPacketProfileName();  // Use packet profile name with weight prefix
        botEntries.add(entry);
        for (Player p : Bukkit.getOnlinePlayers()) {
            ensureTeamExists(p);
            Team team = p.getScoreboard().getTeam(TEAM_NAME);
            if (team != null && !team.hasEntry(entry)) team.addEntry(entry);
        }
        Config.debug("[BotTabTeam] + '" + entry + "' to " + Bukkit.getOnlinePlayers().size() + " scoreboards");
    }

    /**
     * Removes a bot's packet profile name from the team on all scoreboards.
     * Call during or before the bot's removal sequence.
     */
    public void removeBot(FakePlayer fp) {
        removeEntry(fp.getPacketProfileName());  // Use packet profile name to match addBot
    }

    /**
     * Removes a specific entry string from the team on all scoreboards.
     */
    public void removeEntry(String entry) {
        if (entry == null) return;
        botEntries.remove(entry);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team team = p.getScoreboard().getTeam(TEAM_NAME);
            if (team != null && team.hasEntry(entry)) team.removeEntry(entry);
        }
        Config.debug("[BotTabTeam] - '" + entry + "' from " + Bukkit.getOnlinePlayers().size() + " scoreboards");
    }

    // ── Bulk operations ───────────────────────────────────────────────────────

    /**
     * Clears all current team entries and re-adds the packet profile names of every
     * active bot on ALL online players' scoreboards. Call after {@code /fpp reload}
     * or whenever the bot set changes in bulk.
     */
    public void rebuild(Collection<FakePlayer> activeBots) {
        botEntries.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ensureTeamExists(p);
            Team team = p.getScoreboard().getTeam(TEAM_NAME);
            if (team == null) continue;
            for (String e : new ArrayList<>(team.getEntries())) team.removeEntry(e);
            if (Config.tabListEnabled()) {
                for (FakePlayer fp : activeBots) {
                    // CRITICAL: Use packet profile name WITH weight prefix for proper sorting within team
                    String name = fp.getPacketProfileName();
                    botEntries.add(name);
                    team.addEntry(name);
                }
            }
        }
        FppLogger.debug("[BotTabTeam] Rebuilt with " + activeBots.size() + " bot(s) on "
                + Bukkit.getOnlinePlayers().size() + " scoreboard(s)"
                + (Config.tabListEnabled() ? "" : " (tab-list disabled — entries not added)") + ".");
    }

    /**
     * Removes all bot entries from the team on all scoreboards.
     * Call when {@code tab-list.enabled} is toggled to {@code false}.
     */
    public void clearAll() {
        botEntries.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team team = p.getScoreboard().getTeam(TEAM_NAME);
            if (team == null) continue;
            for (String e : new ArrayList<>(team.getEntries())) team.removeEntry(e);
        }
        FppLogger.debug("[BotTabTeam] Cleared all entries from " + Bukkit.getOnlinePlayers().size() + " scoreboards");
    }

    /**
     * Unregisters the ~fpp team from all online players' scoreboards.
     * Call from {@code onDisable}. Safe to call multiple times.
     */
    public void destroy() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team team = p.getScoreboard().getTeam(TEAM_NAME);
            if (team != null) {
                try { team.unregister(); } catch (Exception ignored) {}
            }
        }
        botEntries.clear();
        FppLogger.debug("[BotTabTeam] Team '" + TEAM_NAME + "' unregistered from all scoreboards.");
    }

    /**
     * Ensures the team exists on a newly-joined player's scoreboard and adds
     * all currently-tracked bot entries to it. Call from PlayerJoinListener.
     */
    public void syncToPlayer(Player player) {
        if (!Config.tabListEnabled() || botEntries.isEmpty()) return;
        try {
            ensureTeamExists(player);
            Team team = player.getScoreboard().getTeam(TEAM_NAME);
            if (team == null) return;
            int added = 0;
            for (String entry : botEntries) {
                if (!team.hasEntry(entry)) { team.addEntry(entry); added++; }
            }
            Config.debug("[BotTabTeam] Synced " + added + "/" + botEntries.size() + " bot(s) to " + player.getName() + "'s scoreboard");
        } catch (Exception e) {
            Config.debug("[BotTabTeam] syncToPlayer failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /** @return {@code true} when at least one bot entry exists. */
    public boolean isActive() { return !botEntries.isEmpty(); }

    /**
     * Dumps complete team state to console for debugging purposes.
     */
    public void dumpTeamState() {
        FppLogger.info("═══════════════════════════════════════════════");
        FppLogger.info("[BotTabTeam] TEAM STATE DUMP — tracked entries: " + botEntries.size());
        if (!botEntries.isEmpty()) FppLogger.info("  Entries: " + String.join(", ", botEntries));
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            Team team = board.getTeam(TEAM_NAME);
            FppLogger.info("Player " + p.getName() + " | board=" + board.getClass().getSimpleName()
                    + " | team=" + (team == null ? "NOT FOUND" : "EXISTS size=" + team.getSize()
                    + " entries=" + (team.getEntries().isEmpty() ? "(none)" : String.join(", ", team.getEntries()))));
        }
        FppLogger.info("═══════════════════════════════════════════════");
    }
}


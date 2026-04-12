package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * {@code /fpp badword} — Badword filter management and active-bot name scanning.
 *
 * <h3>Subcommands</h3>
 * <pre>
 *   /fpp badword check   - List active bots whose names fail the badword filter (read-only)
 *   /fpp badword update  - Despawn flagged bots and respawn with random clean names,
 *                          fully restoring inventory, XP, chat settings, LP group, and
 *                          frozen state so no data is lost during the rename.
 *   /fpp badword status  - Show filter configuration, word counts, and current scan result
 * </pre>
 *
 * <p>Because bot MC usernames are immutable (baked into the NMS {@code GameProfile}),
 * a rename must despawn + respawn the entity.  This also guarantees the nametag above
 * the bot's head and any death messages show the new name, not the old one.
 *
 * <p>Requires {@code fpp.badword} (child of {@code fpp.op}).
 */
public class BadwordCommand implements FppCommand {

    private static final String ACCENT = "<#0079FF>";
    private static final String CLOSE  = "</#0079FF>";
    private static final String GRAY   = "<gray>";
    private static final String GREEN  = "<green>";
    private static final String RED    = "<red>";
    private static final String YELLOW = "<yellow>";

    /** Ticks between polling attempts while waiting for a new bot's entity to be ready. */
    private static final long POLL_INTERVAL_TICKS = 5L;
    /** Maximum ticks to wait before giving up on restoring data (200 = 10 seconds). */
    private static final int  POLL_TIMEOUT_TICKS  = 200;

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    public BadwordCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ── FppCommand ────────────────────────────────────────────────────────────

    @Override public String getName()        { return "badword"; }
    @Override public String getUsage()       { return "<check|update|status>"; }
    @Override public String getDescription() { return "Scan and fix bot names flagged by the badword filter."; }
    @Override public String getPermission()  { return Perm.BADWORD; }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.BADWORD);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (Perm.missing(sender, Perm.BADWORD)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check"  -> doCheck(sender);
            case "update" -> doUpdate(sender);
            case "status" -> doStatus(sender);
            default       -> sendHelp(sender);
        }
        return true;
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    /**
     * Lists all active bots whose MC usernames fail the badword filter.
     * Read-only — no bots are changed.
     */
    private void doCheck(CommandSender sender) {
        if (!Config.isBadwordFilterEnabled()) {
            msg(sender, YELLOW + "⚠ " + GRAY + "Badword filter is " + RED + "disabled"
                    + GRAY + ". Enable it with " + ACCENT + "badword-filter.enabled: true" + CLOSE
                    + GRAY + " in config.yml, then run " + ACCENT + "/fpp reload" + CLOSE + GRAY + ".");
            return;
        }
        if (BadwordFilter.getBadwordCount() == 0) {
            msg(sender, YELLOW + "⚠ " + GRAY + "No badword sources are active."
                    + " Enable " + ACCENT + "badword-filter.use-global-list" + CLOSE
                    + GRAY + " or add words to " + ACCENT + "badword-filter.words" + CLOSE
                    + GRAY + " / " + ACCENT + "bad-words.yml" + CLOSE + GRAY + ".");
            return;
        }

        List<FakePlayer> flagged = findFlaggedBots();
        int total = manager.getActivePlayers().size();

        if (flagged.isEmpty()) {
            msg(sender, GREEN + "✔ " + GRAY + "All " + total + " active bot(s) have clean names.");
            return;
        }

        msg(sender, ACCENT + "ꜰʟᴀɡɡᴇᴅ ʙᴏᴛ ɴᴀᴍᴇꜱ" + CLOSE
                + GRAY + " (" + flagged.size() + " of " + total + " bots):");
        for (FakePlayer fp : flagged) {
            String badword = BadwordFilter.findBadword(fp.getName());
            msg(sender, GRAY + "  • " + ACCENT + fp.getName() + CLOSE
                    + GRAY + " — contains: " + RED + (badword != null ? badword : "???"));
        }
        msg(sender, GRAY + "  Run " + ACCENT + "/fpp badword update" + CLOSE
                + GRAY + " to replace all flagged names with random clean names.");
    }

    /**
     * For every active bot whose MC username fails the badword filter:
     * <ol>
     *   <li>Captures a full {@link BotSnapshot} (inventory, XP, chat settings, LP group, frozen state)</li>
     *   <li>Deletes the bot (all flagged bots are deleted in one batch before any respawn)</li>
     *   <li>Respawns the bot under a random clean name from bot-names.yml (staggered delays)</li>
     *   <li>Polls until the new bot's NMS entity is fully online, then restores all saved data</li>
     * </ol>
     * The nametag above the bot's head and death messages will both show the new name
     * because the MC username is baked into the NMS {@code GameProfile} — only a respawn
     * can change it.
     *
     * <p>Made {@code public} so {@code MigrateCommand} can call it programmatically (e.g.
     * from {@code /fpp migrate apply}).
     */
    public void doUpdate(CommandSender sender) {
        if (!Config.isBadwordFilterEnabled()) {
            msg(sender, YELLOW + "⚠ " + GRAY + "Badword filter is " + RED + "disabled"
                    + GRAY + ". Enable it in config.yml first.");
            return;
        }
        if (BadwordFilter.getBadwordCount() == 0) {
            msg(sender, YELLOW + "⚠ " + GRAY + "No badword sources are active — nothing to check.");
            return;
        }

        List<FakePlayer> flagged = findFlaggedBots();
        if (flagged.isEmpty()) {
            msg(sender, GREEN + "✔ " + GRAY + "All " + manager.getActivePlayers().size()
                    + " active bot(s) have clean names — nothing to update.");
            return;
        }

        msg(sender, ACCENT + "ʙᴀᴅᴡᴏʀᴅ ᴜᴘᴅᴀᴛᴇ" + CLOSE
                + GRAY + " — processing " + flagged.size() + " flagged bot(s)…");

        // ── 1. Pre-assign clean names and snapshot data (before any deletion) ──
        Set<String>      reserved = new HashSet<>();
        List<RenameTask> tasks    = new ArrayList<>();

        for (FakePlayer fp : flagged) {
            String cleanName = findAvailableCleanName(reserved);
            if (cleanName == null) {
                msg(sender, RED + "  ✘ " + GRAY + "No clean name available for "
                        + ACCENT + fp.getName() + CLOSE
                        + GRAY + " — add more names to bot-names.yml and retry.");
                continue;
            }
            reserved.add(cleanName.toLowerCase());
            tasks.add(new RenameTask(
                    fp.getName(),
                    cleanName,
                    fp.getLiveLocation(),
                    fp.getBotType(),
                    BotSnapshot.from(fp)
            ));
        }

        if (tasks.isEmpty()) {
            msg(sender, RED + "✘ " + GRAY + "Could not find enough clean names in the pool."
                    + " Add more names to bot-names.yml and retry.");
            return;
        }

        // ── 2. Delete all flagged bots in one batch ───────────────────────────
        for (RenameTask task : tasks) {
            msg(sender, GRAY + "  Renaming " + RED + task.oldName()
                    + GRAY + " → " + ACCENT + task.newName() + CLOSE + GRAY + "…");
            manager.delete(task.oldName());
        }

        // ── 3. Respawn each bot with its clean name (staggered: 12 + 8*i ticks) ──
        for (int i = 0; i < tasks.size(); i++) {
            final RenameTask task  = tasks.get(i);
            final long       delay = 12L + (long) i * 8L;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location loc = task.location();
                if (loc == null) {
                    msg(sender, RED + "  ✘ " + GRAY + "Cannot respawn "
                            + ACCENT + task.newName() + CLOSE
                            + GRAY + " — no location saved (bodyless bot had no world position).");
                    return;
                }

                int result = manager.spawn(loc, 1, null, task.newName(), true, task.botType());
                if (result <= 0) {
                    String reason = switch (result) {
                        case  0  -> "name already taken";
                        case -1  -> "global bot limit reached";
                        case -2  -> "name failed Minecraft validation";
                        default  -> "unknown error (code " + result + ")";
                    };
                    msg(sender, RED + "  ✘ " + GRAY + "Failed to spawn "
                            + ACCENT + task.newName() + CLOSE + GRAY + ": " + reason + ".");
                    return;
                }

                // ── 4. Poll until entity is ready, then restore snapshot data ──
                scheduleSnapshotRestore(sender, task);

            }, delay);
        }
    }

    /**
     * Schedules a repeating task that waits for the newly-spawned bot's NMS entity
     * to become available, then restores all snapshot data to it.
     */
    private void scheduleSnapshotRestore(CommandSender sender, RenameTask task) {
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed += (int) POLL_INTERVAL_TICKS;

                if (elapsed > POLL_TIMEOUT_TICKS) {
                    cancel();
                    msg(sender, YELLOW + "  ⚠ " + GRAY + "Timed out waiting for "
                            + ACCENT + task.newName() + CLOSE
                            + GRAY + " to finish spawning. Bot is online but some data "
                            + "may not have been restored.");
                    return;
                }

                FakePlayer newFp = manager.getByName(task.newName());
                if (newFp == null) return; // not in manager yet

                // For bots with a body: wait for the NMS player entity to be set.
                // For bodyless bots: player will always be null; proceed immediately.
                if (!newFp.isBodyless() && newFp.getPlayer() == null) return;

                cancel();

                // Apply everything that doesn't depend on the NMS entity being ready
                // (includes AI personality restoration)
                task.snapshot().applyChatAndState(newFp);

                // Sync AI personality to DB so it persists after the rename
                if (task.snapshot().aiPersonality() != null) {
                    me.bill.fakePlayerPlugin.database.DatabaseManager dbm = plugin.getDatabaseManager();
                    if (dbm != null) dbm.updateBotAiPersonality(newFp.getUuid().toString(),
                            task.snapshot().aiPersonality());
                }

                // Apply inventory + XP on the NMS entity (body must exist)
                if (newFp.getPlayer() != null) {
                    task.snapshot().applyInventoryAndXp(newFp.getPlayer());
                }

                // Apply LP group a few ticks later so the spawn LP flow finishes first
                if (task.snapshot().lpGroup() != null && plugin.isLuckPermsAvailable()) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        FakePlayer fp = manager.getByName(task.newName());
                        if (fp == null) return;
                        me.bill.fakePlayerPlugin.util.LuckPermsHelper
                                .applyGroupToOnlineUser(fp.getUuid(), task.snapshot().lpGroup());
                        fp.setLuckpermsGroup(task.snapshot().lpGroup());
                        // Refresh display name so LP prefix/suffix reflects the restored group
                        manager.refreshLpDisplayName(fp);
                    }, 5L);
                }

                msg(sender, GREEN + "  ✔ " + ACCENT + task.newName() + CLOSE
                        + GRAY + " is online" + GRAY
                        + " <dark_gray>(" + RED + task.oldName() + GRAY + " renamed, data restored"
                        + "<dark_gray>)" + GRAY + ".");
            }
        }.runTaskTimer(plugin, POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);
    }

    /** Displays badword filter configuration and a live scan summary. */
    private void doStatus(CommandSender sender) {
        boolean enabled = Config.isBadwordFilterEnabled();
        int     words   = BadwordFilter.getBadwordCount();
        int     wl      = BadwordFilter.getWhitelistCount();
        boolean autoRen = Config.isBadwordAutoRenameEnabled();
        boolean global  = Config.isBadwordGlobalListEnabled();
        String  mode    = Config.getBadwordAutoDetectionMode();

        msg(sender, ACCENT + "ʙᴀᴅᴡᴏʀᴅ ꜰɪʟᴛᴇʀ ꜱᴛᴀᴛᴜꜱ" + CLOSE);
        msg(sender, GRAY + "  Filter     : " + (enabled ? GREEN + "✔ enabled" : RED + "✘ disabled"));
        msg(sender, GRAY + "  Global list: " + (global ? GREEN + "✔ on" : RED + "✘ off"));
        msg(sender, GRAY + "  Words      : " + ACCENT + words + CLOSE);
        msg(sender, GRAY + "  Whitelist  : " + ACCENT + wl + CLOSE
                + GRAY + " entr" + (wl == 1 ? "y" : "ies"));
        msg(sender, GRAY + "  Auto-rename: " + (autoRen
                ? GREEN + "✔ on " + GRAY + "(bad names get a random clean name at spawn)"
                : RED   + "✘ off " + GRAY + "(bad names are hard-blocked at spawn)"));
        msg(sender, GRAY + "  Detection  : " + ACCENT + mode + CLOSE);

        if (enabled && words > 0) {
            List<FakePlayer> flagged = findFlaggedBots();
            int total = manager.getActivePlayers().size();
            if (flagged.isEmpty()) {
                msg(sender, GREEN + "  ✔ " + GRAY + "All " + total
                        + " active bot(s) have clean names.");
            } else {
                msg(sender, YELLOW + "  ⚠ " + ACCENT + flagged.size() + CLOSE
                        + GRAY + " of " + total + " active bot(s) have flagged names. Run "
                        + ACCENT + "/fpp badword update" + CLOSE + GRAY + " to fix them.");
            }
        } else if (enabled) {
            msg(sender, YELLOW + "  ⚠ " + GRAY
                    + "No badword sources are active — filter is enabled but has nothing to check.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns all active bots whose MC username fails {@link BadwordFilter#isAllowed}. */
    private List<FakePlayer> findFlaggedBots() {
        List<FakePlayer> result = new ArrayList<>();
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (!BadwordFilter.isAllowed(fp.getName())) result.add(fp);
        }
        return result;
    }

    /**
     * Picks a random name from the bot-names pool that is currently unused,
     * passes the badword filter, and has not already been reserved in this batch.
     */
    private String findAvailableCleanName(Set<String> reserved) {
        List<String> pool = BotNameConfig.getNames();
        if (pool.isEmpty()) return null;

        Random rand     = new Random();
        int    attempts = Math.min(30, pool.size() * 3);

        for (int i = 0; i < attempts; i++) {
            String candidate = pool.get(rand.nextInt(pool.size()));
            if (candidate == null || candidate.isBlank())         continue;
            if (candidate.length() > 16)                          continue;
            if (!candidate.matches("[a-zA-Z0-9_]+"))              continue;
            if (!BadwordFilter.isAllowed(candidate))              continue;
            if (reserved.contains(candidate.toLowerCase()))       continue;
            if (manager.getByName(candidate) != null)             continue;
            if (org.bukkit.Bukkit.getPlayerExact(candidate) != null) continue;
            return candidate;
        }
        return null;
    }

    // ── Tab complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (Perm.missing(sender, Perm.BADWORD)) return List.of();
        if (args.length == 1) return filter(List.of("check", "update", "status"), args[0]);
        return List.of();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        msg(sender, ACCENT + "ʙᴀᴅᴡᴏʀᴅ ꜰɪʟᴛᴇʀ" + CLOSE);
        row(sender, "/fpp badword check",  "List active bots with flagged names (read-only)");
        row(sender, "/fpp badword update", "Replace flagged bot names — preserves all bot data");
        row(sender, "/fpp badword status", "Show filter config, word count, and scan result");
    }

    private void row(CommandSender sender, String cmd, String desc) {
        msg(sender, GRAY + "  " + ACCENT + cmd + CLOSE + " " + GRAY + "- " + desc);
    }

    private void msg(CommandSender sender, String mm) {
        sender.sendMessage(TextUtil.colorize(mm));
    }

    private static List<String> filter(List<String> options, String partial) {
        String p = partial.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).toList();
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of a bot's state captured immediately before deletion.
     * All {@link ItemStack} entries are deep-cloned at capture time so the delete
     * operation cannot corrupt them.
     */
    private static final class BotSnapshot {

        // Inventory
        private final ItemStack[] mainContents;   // slots 0-35
        private final ItemStack[] armorContents;  // boots, legs, chest, helm  (4 slots)
        private final ItemStack[] extraContents;  // offhand                   (1 slot)

        // XP
        private final int   xpLevel;
        private final float xpProgress;
        private final int   totalXp;

        // Chat
        private final boolean chatEnabled;
        private final String  chatTier;    // null = random

        // Movement
        private final boolean frozen;

        // LuckPerms
        private final String lpGroup;   // null = not resolved / default

        // AI personality
        private final String aiPersonality; // null = not assigned

        private BotSnapshot(ItemStack[] main, ItemStack[] armor, ItemStack[] extra,
                            int xpLevel, float xpProgress, int totalXp,
                            boolean chatEnabled, String chatTier,
                            boolean frozen, String lpGroup, String aiPersonality) {
            this.mainContents  = main;
            this.armorContents = armor;
            this.extraContents = extra;
            this.xpLevel       = xpLevel;
            this.xpProgress    = xpProgress;
            this.totalXp       = totalXp;
            this.chatEnabled   = chatEnabled;
            this.chatTier      = chatTier;
            this.frozen        = frozen;
            this.lpGroup       = lpGroup;
            this.aiPersonality = aiPersonality;
        }

        /** Build a snapshot from a live {@link FakePlayer}, deep-cloning all items. */
        static BotSnapshot from(FakePlayer fp) {
            Player entity = fp.getPlayer();

            // Inventory (safe even if entity is null — returns empty arrays)
            ItemStack[] main  = cloneItems(entity != null
                    ? entity.getInventory().getContents()      : new ItemStack[36]);
            ItemStack[] armor = cloneItems(entity != null
                    ? entity.getInventory().getArmorContents() : new ItemStack[4]);
            ItemStack[] extra = cloneItems(entity != null
                    ? entity.getInventory().getExtraContents() : new ItemStack[1]);

            // XP
            int   lvl  = entity != null ? entity.getLevel()            : 0;
            float prog = entity != null ? entity.getExp()              : 0f;
            int   tot  = entity != null ? entity.getTotalExperience()  : 0;

            // LP group — use the cached value on FakePlayer (set by finishSpawn / /fpp rank)
            String lpg = fp.getLuckpermsGroup();
            // Treat "default" as null so we don't override with the literal string "default"
            if ("default".equalsIgnoreCase(lpg)) lpg = null;

            return new BotSnapshot(
                    main, armor, extra,
                    lvl, prog, tot,
                    fp.isChatEnabled(), fp.getChatTier(),
                    fp.isFrozen(),
                    lpg,
                    fp.getAiPersonality()  // preserve persistent AI personality through rename
            );
        }

        /** LP group accessor (used by the outer class to schedule the LP re-apply). */
        String lpGroup() { return lpGroup; }

        /** AI personality accessor (preserved through rename). */
        String aiPersonality() { return aiPersonality; }

        /**
         * Applies inventory contents and XP to the given Bukkit player entity.
         * Must be called on the main thread once the entity exists.
         */
        void applyInventoryAndXp(Player entity) {
            PlayerInventory inv = entity.getInventory();
            if (mainContents.length  > 0) inv.setContents(mainContents);
            if (armorContents.length > 0) inv.setArmorContents(armorContents);
            if (extraContents.length > 0) inv.setExtraContents(extraContents);

            entity.setLevel(xpLevel);
            entity.setExp(Math.max(0f, Math.min(1f, xpProgress)));
            entity.setTotalExperience(totalXp);
        }

        /**
         * Applies chat settings, frozen state, and AI personality directly to the
         * {@link FakePlayer} object. Safe to call as soon as the bot is in the manager
         * (entity not required).
         */
        void applyChatAndState(FakePlayer fp) {
            fp.setChatEnabled(chatEnabled);
            if (chatTier != null) fp.setChatTier(chatTier);
            if (frozen)           fp.setFrozen(true);
            // Restore persistent AI personality so it survives the rename
            if (aiPersonality != null) fp.setAiPersonality(aiPersonality);
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static ItemStack[] cloneItems(ItemStack[] items) {
            if (items == null) return new ItemStack[0];
            ItemStack[] copy = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                copy[i] = items[i] != null ? items[i].clone() : null;
            }
            return copy;
        }
    }

    /**
     * Packages all per-bot rename parameters together so the staggered
     * scheduler tasks capture them cleanly in a single final reference.
     */
    private record RenameTask(
            String      oldName,
            String      newName,
            Location    location,
            BotType     botType,
            BotSnapshot snapshot
    ) {}
}

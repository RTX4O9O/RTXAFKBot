package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-phase use command - mirrors the structure of {@link MineCommand}.
 *
 * <h3>Phase 1 - Navigation</h3>
 * The bot walks/sprints to the sender's exact position using A* pathfinding.
 * Normal physics, knockback, and pushable are all active during this phase.
 *
 * <h3>Phase 2 - Right-click loop</h3>
 * Once the bot arrives (XZ ≤ {@code pathfinding.arrival-distance}) it is snapped to the exact
 * spot, its position is locked via {@code FakePlayerManager.lockForAction()},
 * head-AI is suppressed, and the right-click loop begins.
 *
 * <p>On each tick the bot's look-direction is raycast (4.5 survival / 5 creative).
 * Three interaction paths are attempted per hand:
 * <ol>
 *   <li><b>Block</b> - {@code gameMode.useItemOn()}</li>
 *   <li><b>Entity</b> - {@code interactAt()} + {@code interactOn()}</li>
 *   <li><b>Air / item</b> - {@code gameMode.useItem()}</li>
 * </ol>
 * A 3-tick freeze is applied after each successful action.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code /fpp use <bot>}      - walk to your spot, right-click continuously.</li>
 *   <li>{@code /fpp use <bot> once} - walk to your spot, right-click once, stop.</li>
 *   <li>{@code /fpp use <bot> stop} - stop navigating / stop right-clicking.</li>
 *   <li>{@code /fpp use stop}       - stop all bots.</li>
 * </ul>
 *
 * <p>Permission: {@link Perm#USE_CMD} ({@code fpp.useitem}), default {@code op}.
 */
public final class UseCommand implements FppCommand {


    // ── Fields ────────────────────────────────────────────────────────────────

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;
    private final PathfindingService pathfinding;

    /** Phase-2 right-click task IDs. */
    private final Map<UUID, Integer>    useTasks  = new ConcurrentHashMap<>();
    /** Active use lock locations per bot (populated at lock-and-use start). */
    private final Map<UUID, Location>   activeUseLocations = new ConcurrentHashMap<>();
    /** Whether the active use task is once-only per bot. */
    private final Map<UUID, Boolean>    activeUseOnceFlags  = new ConcurrentHashMap<>();

    public UseCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin  = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    // ── FppCommand ────────────────────────────────────────────────────────────

    @Override public String getName()        { return "use"; }
    @Override public String getUsage()       { return "<bot> [once|stop]  |  stop"; }
    @Override public String getDescription() { return "Walk a bot to your position then right-click what it's looking at."; }
    @Override public String getPermission()  { return Perm.USE_CMD; }
    @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.USE_CMD); }

    // ── execute ───────────────────────────────────────────────────────────────

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("use-usage"));
            return true;
        }

        // /fpp use stop - stop all bots
        if (args[0].equalsIgnoreCase("stop") && args.length == 1) {
            stopAll();
            sender.sendMessage(Lang.get("use-stopped-all"));
            return true;
        }

        String botName = args[0];
        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("use-not-found", "name", botName));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("use-bot-offline", "name", fp.getDisplayName()));
            return true;
        }

        // /fpp use <bot> stop
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            cancelAll(fp.getUuid());
            sender.sendMessage(Lang.get("use-stopped", "name", fp.getDisplayName()));
            return true;
        }

        // /fpp use <bot> [once]
        boolean once = args.length >= 2 && args[1].equalsIgnoreCase("once");

        // Destination = sender's location (player) or bot's current location (console)
        Location dest = (sender instanceof Player sp)
                ? sp.getLocation().clone()
                : bot.getLocation().clone();

        // Cancel any existing nav/use for this bot
        cancelAll(fp.getUuid());

        double xzDist = PathfindingService.xzDist(bot.getLocation(), dest);
        if (xzDist <= Config.pathfindingArrivalDistance()) {
            // Already close enough - lock and start right-clicking immediately
            lockAndStartUsing(fp, once, dest);
            sender.sendMessage(once
                    ? Lang.get("use-started-once", "name", fp.getDisplayName())
                    : Lang.get("use-started",      "name", fp.getDisplayName()));
        } else {
            // Walk to the destination first
            startNavigation(fp, once, dest);
            sender.sendMessage(Lang.get("use-walking", "name", fp.getDisplayName()));
        }
        return true;
    }

    // ── Tab-complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            if ("stop".startsWith(prefix)) out.add("stop");
            for (FakePlayer fp : manager.getActivePlayers())
                if (fp.getName().toLowerCase().startsWith(prefix)) out.add(fp.getName());
            return out;
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("stop")) {
            String prefix = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            if ("once".startsWith(prefix)) out.add("once");
            if ("stop".startsWith(prefix)) out.add("stop");
            return out;
        }

        return List.of();
    }

    // ── Phase 1: navigation ───────────────────────────────────────────────────

    private void startNavigation(FakePlayer fp, boolean once, Location dest) {
        pathfinding.navigate(fp, new PathfindingService.NavigationRequest(
                PathfindingService.Owner.USE,
                () -> dest,
                Config.pathfindingArrivalDistance(),
                0.0,
                Integer.MAX_VALUE,
                () -> lockAndStartUsing(fp, once, dest),
                null,
                null
        ));
    }

    // ── Phase 2: lock + right-click loop ─────────────────────────────────────

    /**
     * Called once the bot has arrived at the use destination.
     * Snaps to the exact position, locks position + head-AI, and starts the
     * 1-tick right-click loop.
     */
    private void lockAndStartUsing(FakePlayer fp, boolean once, Location lockLoc) {
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null) return;

        // Final snap to exact position (bot is ≤ ARRIVAL_DIST away - imperceptible)
        bot.teleport(lockLoc);

        // Lock position + suppress head-AI in the manager's tick loop
        manager.lockForAction(uuid, lockLoc);

        // Record active use state for persistence
        activeUseLocations.put(uuid, lockLoc.clone());
        activeUseOnceFlags.put(uuid, once);

        // Per-task freeze counter
        final int[] freeze = {0};

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player b = fp.getPlayer();
            if (b == null || !b.isOnline()) { stopUsing(uuid); return; }

            if (freeze[0] > 0) { freeze[0]--; return; }

            ServerPlayer nms = ((CraftPlayer) b).getHandle();

            // If bot is actively holding/using an item (bow, eating), wait
            if (nms.isUsingItem()) {
                if (once) stopUsing(uuid);
                return;
            }

            HitResult hit = rayTrace(nms);
            if (hit == null || hit.getType() == HitResult.Type.MISS) return;

            boolean acted = false;

            for (InteractionHand hand : InteractionHand.values()) {

                // ── Block interaction ─────────────────────────────────────────
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    var pos  = blockHit.getBlockPos();
                    Direction side = blockHit.getDirection();

                    if (pos.getY() < nms.level().getMaxY() - (side == Direction.UP ? 1 : 0)
                            && nms.level().mayInteract(nms, pos)) {
                        nms.resetLastActionTime();
                        var result = nms.gameMode.useItemOn(
                                nms, nms.level(), nms.getItemInHand(hand), hand, blockHit);
                        if (result.consumesAction()) {
                            nms.swing(hand);
                            freeze[0] = 3;
                            acted = true;
                            break;
                        }
                    }

                // ── Entity interaction ────────────────────────────────────────
                } else if (hit.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHit = (EntityHitResult) hit;
                    var entity = entityHit.getEntity();

                    boolean handWasEmpty   = nms.getItemInHand(hand).isEmpty();
                    boolean itemFrameEmpty = (entity instanceof ItemFrame ife)
                            && ife.getItem().isEmpty();
                    Vec3 relPos = entityHit.getLocation().subtract(
                            entity.getX(), entity.getY(), entity.getZ());

                    nms.resetLastActionTime();

                    if (entity.interactAt(nms, relPos, hand).consumesAction()) {
                        freeze[0] = 3; acted = true; break;
                    }
                    if (nms.interactOn(entity, hand).consumesAction()
                            && !(handWasEmpty && itemFrameEmpty)) {
                        freeze[0] = 3; acted = true; break;
                    }
                }

                // ── Item use in air (bow, food, ender pearl, etc.) ────────────
                if (nms.gameMode.useItem(nms, nms.level(), nms.getItemInHand(hand), hand)
                        .consumesAction()) {
                    nms.resetLastActionTime();
                    freeze[0] = 3; acted = true; break;
                }
            }

            if (once && acted) stopUsing(uuid);

        }, 0L, 1L).getTaskId();

        useTasks.put(uuid, taskId);
    }

    // ── Stop helpers ──────────────────────────────────────────────────────────

    /**
     * Cancels both the navigation task AND the right-click task, releases the
     * position lock and any held item.
     */
    private void cancelAll(UUID botUuid) {
        pathfinding.cancel(botUuid);

        stopUsing(botUuid);

        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                NmsPlayerSpawner.setJumping(bot, false);
                bot.setSprinting(false);
            }
        }
    }

    /** Stops only the right-click phase (task + lock + item release). */
    public void stopUsing(UUID botUuid) {
        Integer taskId = useTasks.remove(botUuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);

        manager.unlockAction(botUuid);   // reuses the same general action lock mechanism

        // Clear persistence tracking
        activeUseLocations.remove(botUuid);
        activeUseOnceFlags.remove(botUuid);

        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline())
                ((CraftPlayer) bot).getHandle().releaseUsingItem();
        }
    }

    /** Stops all bots (navigation + right-clicking). */
    public void stopAll() {
        pathfinding.cancelAll(PathfindingService.Owner.USE);
        new HashSet<>(useTasks.keySet()).forEach(this::cancelAll);
    }

    /** Returns {@code true} if the bot is currently navigating to the use spot. */
    public boolean isNavigating(UUID botUuid) { return pathfinding.isNavigating(botUuid); }

    /** Returns {@code true} if the bot is actively right-clicking. */
    public boolean isUsing(UUID botUuid)      { return useTasks.containsKey(botUuid); }

    // ── Persistence API ───────────────────────────────────────────────────────

    /**
     * Returns the active use lock location for the given bot,
     * or {@code null} when the bot is not in use mode.
     */
    @org.jetbrains.annotations.Nullable
    public Location getActiveUseLocation(UUID botUuid) {
        return activeUseLocations.get(botUuid);
    }

    /**
     * Returns {@code true} if the bot's active use task is once-only.
     */
    public boolean isActiveUseOnce(UUID botUuid) {
        Boolean v = activeUseOnceFlags.get(botUuid);
        return v != null && v;
    }

    /**
     * Resumes a right-click use loop for a bot restored from persistence.
     * Navigates to the location if not already close enough.
     */
    public void resumeUsing(FakePlayer fp, boolean once, Location loc) {
        if (fp == null || loc == null) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        cancelAll(fp.getUuid());
        if (PathfindingService.xzDist(bot.getLocation(), loc) <= Config.pathfindingArrivalDistance()) {
            lockAndStartUsing(fp, once, loc);
        } else {
            startNavigation(fp, once, loc);
        }
    }

    // ── NMS raytracer ─────────────────────────────────────────────────────────

    /**
     * Combined block + entity raycast from the bot's eye position.
     * Reach: 4.5 blocks (survival) / 5 blocks (creative).
     * Entity takes priority when closer than the hit block face.
     */
    @SuppressWarnings("resource")
    private static HitResult rayTrace(ServerPlayer player) {
        double reach   = player.gameMode.isCreative() ? 5.0 : 4.5;
        Vec3 eyePos    = player.getEyePosition(1.0f);
        Vec3 viewVec   = player.getViewVector(1.0f);
        Vec3 endPos    = eyePos.add(viewVec.x * reach, viewVec.y * reach, viewVec.z * reach);

        BlockHitResult blockHit;
        try {
            blockHit = player.level().clip(new ClipContext(
                    eyePos, endPos,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        } catch (Exception e) {
            return null;
        }

        double maxSqDist = reach * reach;
        if (blockHit.getType() != HitResult.Type.MISS)
            maxSqDist = blockHit.getLocation().distanceToSqr(eyePos);

        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0);

        EntityHitResult entityHit    = null;
        double          entityDistSq = maxSqDist;

        for (var entity : player.level().getEntities(player, searchBox,
                e -> !e.isSpectator() && e.isPickable())) {
            AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            var  hitOpt    = entityBox.clip(eyePos, endPos);
            if (entityBox.contains(eyePos)) {
                if (entityDistSq >= 0) {
                    entityHit    = new EntityHitResult(entity, hitOpt.orElse(eyePos));
                    entityDistSq = 0;
                }
            } else if (hitOpt.isPresent()) {
                double d = eyePos.distanceToSqr(hitOpt.get());
                if (d < entityDistSq || entityDistSq == 0) {
                    entityHit    = new EntityHitResult(entity, hitOpt.get());
                    entityDistSq = d;
                }
            }
        }

        if (entityHit != null) return (HitResult) entityHit;
        return (HitResult) blockHit;
    }

}


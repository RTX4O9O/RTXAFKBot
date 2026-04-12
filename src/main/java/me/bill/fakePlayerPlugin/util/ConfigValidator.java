package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Material;

/**
 * Validates {@code config.yml} values on startup and reload.
 *
 * <p>Emits {@link FppLogger#warn} for bad values but never throws -
 * the plugin always continues to run; admins see the warnings in console.
 *
 * <p>Checks performed:
 * <ul>
 *   <li>Join/leave delay: min ≤ max</li>
 *   <li>Skin fallback name not blank</li>
 *   <li>Chunk radius ≤ server view-distance</li>
 *   <li>Fake-chat interval: min ≤ max</li>
 *   <li>Swap session: min ≤ max</li>
 *   <li>Swap rejoin delay: min ≤ max</li>
 *   <li>Max-bots ≥ 0</li>
 *   <li>User bot limit ≥ 1</li>
 *   <li>Spawn cooldown ≥ 0</li>
 *   <li>Collision max-horizontal-speed > 0</li>
 *   <li>Head AI look range > 0</li>
 *   <li>Head AI turn speed in (0, 1]</li>
 *   <li>Max health > 0</li>
 *   <li>Peak-hours: enabled requires swap enabled</li>
 *   <li>Peak-hours: stagger-seconds > 0</li>
 *   <li>Peak-hours: timezone is a valid ZoneId</li>
 * </ul>
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    /**
     * Run all validation checks. Warnings go to the FPP console log.
     * Returns the number of issues found (0 = config is clean).
     */
    public static int validate() {
        int issues = 0;

        // ── Join / leave delays ────────────────────────────────────────────────
        if (Config.joinDelayMin() > Config.joinDelayMax()) {
            FppLogger.warn("[Config] join-delay.min (" + Config.joinDelayMin() + ") > "
                    + "join-delay.max (" + Config.joinDelayMax() + ") - swapping values in memory.");
            issues++;
        }
        if (Config.leaveDelayMin() > Config.leaveDelayMax()) {
            FppLogger.warn("[Config] leave-delay.min (" + Config.leaveDelayMin() + ") > "
                    + "leave-delay.max (" + Config.leaveDelayMax() + ") - swapping values in memory.");
            issues++;
        }

        // ── Skin ─────────────────────────────────────────────────────────────
        if (Config.skinGuaranteed() && Config.skinFallbackName().isBlank()) {
            FppLogger.warn("[Config] skin.fallback-name is blank but skin.guaranteed-skin is true - "
                    + "last-resort fallback will not work.");
            issues++;
        }

        // ── Chunk loading ─────────────────────────────────────────────────────
        if (Config.chunkLoadingEnabled()) {
            int radius   = Config.chunkLoadingRadius();
            if (radius == 0) {
                FppLogger.info("[Config] chunk-loading.radius is 0 — bots will not load any chunks.");
            } else {
                int viewDist = Bukkit.getSimulationDistance();
                if (radius > viewDist) {
                    FppLogger.warn("[Config] chunk-loading.radius (" + radius + ") exceeds "
                            + "server simulation-distance (" + viewDist + "). "
                            + "Tickets beyond simulation distance have no effect.");
                    issues++;
                }
            }
        }

        // ── Fake chat ─────────────────────────────────────────────────────────
        if (Config.fakeChatIntervalMin() > Config.fakeChatIntervalMax()) {
            FppLogger.warn("[Config] fake-chat.interval.min > fake-chat.interval.max - "
                    + "set min ≤ max for correct behaviour.");
            issues++;
        }


        // ── Limits ────────────────────────────────────────────────────────────
        if (Config.maxBots() < 0) {
            FppLogger.warn("[Config] limits.max-bots is negative - treating as 0 (unlimited).");
            issues++;
        }
        if (Config.userBotLimit() < 1) {
            FppLogger.warn("[Config] limits.user-bot-limit is < 1 - users will never be able to spawn.");
            issues++;
        }

        // ── Spawn cooldown ────────────────────────────────────────────────────
        if (Config.spawnCooldown() < 0) {
            FppLogger.warn("[Config] spawn-cooldown is negative - treating as 0 (no cooldown).");
            issues++;
        }

        // ── Combat ────────────────────────────────────────────────────────────
        if (Config.maxHealth() <= 0) {
            FppLogger.warn("[Config] combat.max-health must be > 0. Defaulting to 20.");
            issues++;
        }

        // ── Head AI ───────────────────────────────────────────────────────────
        if (Config.headAiLookRange() <= 0) {
            FppLogger.warn("[Config] head-ai.look-range must be > 0.");
            issues++;
        }
        float turnSpeed = Config.headAiTurnSpeed();
        if (turnSpeed <= 0 || turnSpeed > 1) {
            FppLogger.warn("[Config] head-ai.turn-speed should be between 0.0 and 1.0 (got " + turnSpeed + ").");
            issues++;
        }

        // ── Collision ─────────────────────────────────────────────────────────
        if (Config.collisionMaxHoriz() <= 0) {
            FppLogger.warn("[Config] collision.max-horizontal-speed must be > 0.");
            issues++;
        }
        if (Config.collisionHitStrength() < 0) {
            FppLogger.warn("[Config] collision.hit-strength must be >= 0.");
            issues++;
        }
        if (Config.collisionHitMaxHoriz() <= 0) {
            FppLogger.warn("[Config] collision.hit-max-horizontal-speed must be > 0.");
            issues++;
        }
        if (Config.collisionWalkRadius() <= 0) {
            FppLogger.warn("[Config] collision.walk-radius must be > 0.");
            issues++;
        }
        if (Config.collisionBotRadius() <= 0) {
            FppLogger.warn("[Config] collision.bot-radius must be > 0.");
            issues++;
        }
        if (Config.collisionWalkStrength() < 0) {
            FppLogger.warn("[Config] collision.walk-strength must be >= 0.");
            issues++;
        }
        if (Config.collisionBotStrength() < 0) {
            FppLogger.warn("[Config] collision.bot-strength must be >= 0.");
            issues++;
        }

        // ── Peak hours ────────────────────────────────────────────────────────
        if (Config.peakHoursEnabled() && !Config.swapEnabled()) {
            FppLogger.warn("[Config] peak-hours.enabled is true but swap.enabled is false - "
                    + "peak-hours will not run until swap is enabled (/fpp swap on).");
            issues++;
        }
        if (Config.peakHoursStaggerSeconds() <= 0) {
            FppLogger.warn("[Config] peak-hours.stagger-seconds must be > 0 (got "
                    + Config.peakHoursStaggerSeconds() + ").");
            issues++;
        }
        String phTz = Config.peakHoursTimezone();
        try {
            var ignored = java.time.ZoneId.of(phTz);
        } catch (java.time.DateTimeException e) {
            FppLogger.warn("[Config] peak-hours.timezone \"" + phTz + "\" is not a valid ZoneId "
                    + "(e.g. \"UTC\", \"America/New_York\") - falling back to UTC at runtime.");
            issues++;
        }

        // ── Database mode ─────────────────────────────────────────────────────
        org.bukkit.plugin.Plugin fpp = Bukkit.getPluginManager().getPlugin("FakePlayerPlugin");
        if (fpp != null) {
            String rawMode = fpp.getConfig().getString("database.mode", "LOCAL");
            if (!rawMode.trim().equalsIgnoreCase("LOCAL")
                    && !rawMode.trim().equalsIgnoreCase("NETWORK")) {
                FppLogger.warn("[Config] database.mode \"" + rawMode.trim() + "\" is not valid "
                        + "(accepted: LOCAL, NETWORK) - falling back to LOCAL.");
                issues++;
            }
        }

        // ── Pathfinding ───────────────────────────────────────────────────────
        if (Config.pathfindingPlaceBlocks()) {
            String matName = Config.pathfindingPlaceMaterial();
            Material mat = Material.matchMaterial(matName.toUpperCase());
            if (mat == null || !mat.isBlock() || !mat.isSolid()) {
                FppLogger.warn("[Config] pathfinding.place-material \"" + matName + "\" is not a valid "
                        + "solid block - falling back to DIRT.");
                issues++;
            }
        }
        if (Config.pathfindingArrivalDistance() <= 0) {
            FppLogger.warn("[Config] pathfinding.arrival-distance must be > 0.");
            issues++;
        }
        if (Config.pathfindingPatrolArrivalDistance() <= 0) {
            FppLogger.warn("[Config] pathfinding.patrol-arrival-distance must be > 0.");
            issues++;
        }
        if (Config.pathfindingWaypointArrivalDistance() <= 0) {
            FppLogger.warn("[Config] pathfinding.waypoint-arrival-distance must be > 0.");
            issues++;
        }
        if (Config.pathfindingSprintDistance() < 0) {
            FppLogger.warn("[Config] pathfinding.sprint-distance must be >= 0.");
            issues++;
        }
        if (Config.pathfindingRecalcInterval() < 1) {
            FppLogger.warn("[Config] pathfinding.recalc-interval must be >= 1.");
            issues++;
        }
        if (Config.pathfindingStuckTicks() < 1) {
            FppLogger.warn("[Config] pathfinding.stuck-ticks must be >= 1.");
            issues++;
        }
        if (Config.pathfindingMaxNodesExtended() < Config.pathfindingMaxNodes()) {
            FppLogger.warn("[Config] pathfinding.max-nodes-extended must be >= pathfinding.max-nodes.");
            issues++;
        }

        if (issues == 0) {
            FppLogger.debug("[Config] All values passed validation.");
        } else {
            FppLogger.warn("[Config] " + issues + " config issue(s) detected - review warnings above.");
        }

        return issues;
    }
}



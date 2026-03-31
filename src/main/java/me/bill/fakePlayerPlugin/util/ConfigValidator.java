package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;

/**
 * Validates {@code config.yml} values on startup and reload.
 *
 * <p>Emits {@link FppLogger#warn} for bad values but never throws —
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
                    + "join-delay.max (" + Config.joinDelayMax() + ") — swapping values in memory.");
            issues++;
        }
        if (Config.leaveDelayMin() > Config.leaveDelayMax()) {
            FppLogger.warn("[Config] leave-delay.min (" + Config.leaveDelayMin() + ") > "
                    + "leave-delay.max (" + Config.leaveDelayMax() + ") — swapping values in memory.");
            issues++;
        }

        // ── Skin ─────────────────────────────────────────────────────────────
        if (Config.skinGuaranteed() && Config.skinFallbackName().isBlank()) {
            FppLogger.warn("[Config] skin.fallback-name is blank but skin.guaranteed-skin is true — "
                    + "last-resort fallback will not work.");
            issues++;
        }

        // ── Chunk loading ─────────────────────────────────────────────────────
        if (Config.chunkLoadingEnabled()) {
            int radius    = Config.chunkLoadingRadius();
            int viewDist  = Bukkit.getSimulationDistance();
            if (radius > viewDist) {
                FppLogger.warn("[Config] chunk-loading.radius (" + radius + ") exceeds "
                        + "server simulation-distance (" + viewDist + "). "
                        + "Tickets beyond simulation distance have no effect.");
                issues++;
            }
        }

        // ── Fake chat ─────────────────────────────────────────────────────────
        if (Config.fakeChatIntervalMin() > Config.fakeChatIntervalMax()) {
            FppLogger.warn("[Config] fake-chat.interval.min > fake-chat.interval.max — "
                    + "set min ≤ max for correct behaviour.");
            issues++;
        }

        // ── Swap ─────────────────────────────────────────────────────────────
        if (Config.swapEnabled()) {
            if (Config.swapSessionMin() > Config.swapSessionMax()) {
                FppLogger.warn("[Config] swap.session-min > swap.session-max — "
                        + "bots may swap instantly.");
                issues++;
            }
            if (Config.swapRejoinDelayMin() > Config.swapRejoinDelayMax()) {
                FppLogger.warn("[Config] swap.rejoin-delay-min > swap.rejoin-delay-max.");
                issues++;
            }
        }

        // ── Limits ────────────────────────────────────────────────────────────
        if (Config.maxBots() < 0) {
            FppLogger.warn("[Config] limits.max-bots is negative — treating as 0 (unlimited).");
            issues++;
        }
        if (Config.userBotLimit() < 1) {
            FppLogger.warn("[Config] limits.user-bot-limit is < 1 — users will never be able to spawn.");
            issues++;
        }

        // ── Spawn cooldown ────────────────────────────────────────────────────
        if (Config.spawnCooldown() < 0) {
            FppLogger.warn("[Config] spawn-cooldown is negative — treating as 0 (no cooldown).");
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

        // ── Database mode ─────────────────────────────────────────────────────
        org.bukkit.plugin.Plugin fpp = Bukkit.getPluginManager().getPlugin("FakePlayerPlugin");
        if (fpp != null) {
            String rawMode = fpp.getConfig().getString("database.mode", "LOCAL");
            if (!rawMode.trim().equalsIgnoreCase("LOCAL")
                    && !rawMode.trim().equalsIgnoreCase("NETWORK")) {
                FppLogger.warn("[Config] database.mode \"" + rawMode.trim() + "\" is not valid "
                        + "(accepted: LOCAL, NETWORK) — falling back to LOCAL.");
                issues++;
            }
        }

        if (issues == 0) {
            FppLogger.debug("[Config] All values passed validation.");
        } else {
            FppLogger.warn("[Config] " + issues + " config issue(s) detected — review warnings above.");
        }

        return issues;
    }
}


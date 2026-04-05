package me.bill.fakePlayerPlugin.fakeplayer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import me.bill.fakePlayerPlugin.config.Config;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PVP bot AI — drives movement and melee attacks for all {@link BotType#PVP} bots.
 *
 * <h3>State machine</h3>
 * <ul>
 *   <li><b>CHASE</b> — sprint at target when {@literal >} {@link #COMBAT_RANGE} away.</li>
 *   <li><b>COMBAT</b> — strafe, W-tap / S-tap (drag-back), jump-crit, angle-arc, combo
 *       aggression, target-flee boost, elevation awareness.</li>
 *   <li><b>RETREAT</b> — back-pedal 3–5 ticks after a hit (70 % of hits);
 *       30 % chance of aggressive follow-through instead for combo pressure.</li>
 * </ul>
 *
 * <h3>Crash safety</h3>
 * Every velocity write goes through {@link #safeSetVelocity} which silently drops
 * any {@code NaN}/{@code Infinity} component.  Additionally, every code path that
 * divides by {@code horizDist} guards with {@code horizDist < 1e-4}.
 */
public final class BotPvpAI {

    // ── Difficulty system (datapack-inspired: easy → hacker) ─────────────────

    /**
     * Maps {@code pvp-ai.difficulty} to datapack-equivalent combat parameters.
     *
     * <pre>
     *   easy   — 1.5 block reach, full-cooldown only, rare crits/s-taps
     *   medium — 2.0 block reach, slight early-attack window          (default)
     *   hard   — 2.5 block reach, sprint-resets and s-taps more often
     *   tier1  — 3.0 block reach, near-optimal, aggressive combo
     *   hacker — 4.6 block reach, always crits, never misses timing
     * </pre>
     */
    public enum Difficulty {
        EASY   (1.5,  true,  0.20),
        MEDIUM (2.0,  false, 0.45),
        HARD   (2.5,  false, 0.65),
        TIER1  (3.0,  false, 0.80),
        HACKER (4.6,  false, 1.00);

        /** Maximum melee attack distance in blocks. */
        public final double attackRange;
        /**
         * When {@code true} the bot always waits for a full weapon cooldown
         * before swinging (Easy — reduces attack rate significantly).
         */
        public final boolean waitFullCooldown;
        /**
         * Probability (0–1) that temp behaviour flags (crit, s-tap, jump-reset)
         * are activated each randomize cycle.  Higher = more consistent execution.
         */
        public final double flipChance;

        Difficulty(double attackRange, boolean waitFullCooldown, double flipChance) {
            this.attackRange      = attackRange;
            this.waitFullCooldown = waitFullCooldown;
            this.flipChance       = flipChance;
        }

        /** Resolves the current difficulty from {@code pvp-ai.difficulty} in config. */
        public static Difficulty fromConfig() {
            return switch (Config.pvpAiDifficulty()) {
                case "easy"   -> EASY;
                case "hard"   -> HARD;
                case "tier1"  -> TIER1;
                case "hacker" -> HACKER;
                default       -> MEDIUM;
            };
        }
    }

    // ── Tuning ────────────────────────────────────────────────────────────────

    private static final double SPRINT_SPEED   = 0.28;   // full chase sprint (blocks/tick)
    private static final double COMBAT_SPEED   = 0.20;   // forward in-combat speed
    private static final double STRAFE_SPEED   = 0.13;   // perpendicular strafe component
    private static final double RETREAT_SPEED  = 0.18;   // back-pedal speed
    private static final double DRAG_SPEED     = 0.10;   // S-tap drag-back speed (W-tap release)

    private static final double COMBAT_RANGE   = 6.0;    // strafe / W-tap begins here
    private static final double ATTACK_RANGE   = 3.0;    // melee hit distance (Minecraft vanilla limit)
    private static final double JUMP_RANGE     = 4.5;    // jump fires here (> ATTACK_RANGE for crit)

    // ── Distance management ───────────────────────────────────────────────────

    /** Ideal combat distance — bot tries to maintain this range. */
    private static final double PREFERRED_DISTANCE = 2.0;
    /** Back-pedal threshold — matches datapack {@code distanceH=..1.2}. */
    private static final double MIN_DISTANCE = 1.2;
    /** Chase threshold in combat — matches datapack {@code distanceH=3.2..}. */
    private static final double MAX_DISTANCE = 3.2;

    // ── Randomization (datapack: randomize.mcfunction re-rolls every 9–14 ticks) ──

    /** Minimum ticks between behaviour flag re-rolls. */
    private static final int RANDOMIZE_CD_MIN = 9;
    /** Maximum ticks between behaviour flag re-rolls. */
    private static final int RANDOMIZE_CD_MAX = 14;

    // ── Crystal PVP (datapack: crystal.mcfunction, d1.mcfunction) ─────────────

    /** Minimum distance to place crystal away from bot (safety). */
    private static final double CRYSTAL_PLACE_MIN_DIST = 2.5;
    /** Maximum distance to place crystal from bot. */
    private static final double CRYSTAL_PLACE_MAX_DIST = 5.0;
    /** Cooldown after placing/hitting a crystal before next crystal action. */
    private static final int CRYSTAL_COOLDOWN = 22;
    /** Cooldown after popping a totem before refilling. */
    private static final int TOTEM_REFILL_DELAY = 5;

    // ── Ender pearl usage (datapack: pearl.mcfunction) ────────────────────────

    /** Throw pearl when target is this far away. */
    private static final double PEARL_THROW_DISTANCE = 7.0;
    /** Cooldown after throwing a pearl. */
    private static final int PEARL_COOLDOWN = 40; // 2 seconds

    private static final double ATTACK_DAMAGE  = 1.0;    // bare-fist HP (half a heart)
    private static final int    COOLDOWN_MIN   = 8;      // fastest attack (≈ 2.5 CPS)
    private static final int    COOLDOWN_MAX   = 14;     // slowest attack (≈ 1.4 CPS)

    private static final double DETECT_RANGE   = 32.0;
    
    /** Range to detect hostile mobs (smaller than player detection). */
    private static final double MOB_DETECT_RANGE = 16.0;

    /** Chance the bot charges forward after a hit instead of retreating (combo pressure). */
    private static final double FOLLOWTHROUGH_CHANCE = 0.30;
    /** Speed boost factor when target is actively fleeing. */
    private static final double FLEE_BOOST     = 1.15;

    // ── Physics restrictions ──────────────────────────────────────────────────

    /** Movement speed multiplier when in water or lava (realistic water drag). */
    private static final double WATER_MOVEMENT_FACTOR = 0.50;
    /** Max horizontal velocity change per tick when airborne (limited air control). */
    private static final double AIR_CONTROL_LIMIT = 0.05;

    // ── Human imperfection (mistakes & delays) ────────────────────────────────

    /** Chance per tick to add a small reaction delay (15% = fairly reactive). */
    private static final double DELAY_CHANCE = 0.15;
    /** Min/max ticks for reaction delay when it occurs. */
    private static final int DELAY_MIN = 2;
    private static final int DELAY_MAX = 6;

    /** Chance the bot swings but misses the actual attack (8% = occasional whiff). */
    private static final double MISS_CHANCE = 0.08;

    /** Chance per tick to make a movement mistake (5% = rare). */
    private static final double MOVEMENT_MISTAKE_CHANCE = 0.05;
    /** Duration of movement mistake in ticks. */
    private static final int MISTAKE_DURATION = 4;

    // ── Strafe behavior (human-like sustained direction) ─────────────────────

    /** Min/max ticks to strafe in one direction before switching. */
    private static final int STRAFE_DURATION_MIN = 20;
    private static final int STRAFE_DURATION_MAX = 45;
    /** Pause duration between strafe direction changes (0-8 ticks). */
    private static final int STRAFE_PAUSE_MAX = 8;

    // ── Jump direction probabilities ──────────────────────────────────────────

    /** Chance to jump forward/backward (80%) vs left/right (20%). */
    private static final double JUMP_FORWARD_BACK_CHANCE = 0.80;

    // ── Weapon system ─────────────────────────────────────────────────────────

    /** Weapon check interval (every 40 ticks = 2 seconds). */
    private static final int WEAPON_CHECK_INTERVAL = 40;

    /** Weapon damage values (vanilla 1.9+ attack damage). */
    private static final Map<Material, Double> WEAPON_DAMAGE = Map.ofEntries(
        // Swords
        Map.entry(Material.NETHERITE_SWORD, 8.0),
        Map.entry(Material.DIAMOND_SWORD, 7.0),
        Map.entry(Material.IRON_SWORD, 6.0),
        Map.entry(Material.GOLDEN_SWORD, 4.0),
        Map.entry(Material.STONE_SWORD, 5.0),
        Map.entry(Material.WOODEN_SWORD, 4.0),
        // Axes (slower but higher damage)
        Map.entry(Material.NETHERITE_AXE, 10.0),
        Map.entry(Material.DIAMOND_AXE, 9.0),
        Map.entry(Material.IRON_AXE, 9.0),
        Map.entry(Material.GOLDEN_AXE, 7.0),
        Map.entry(Material.STONE_AXE, 9.0),
        Map.entry(Material.WOODEN_AXE, 7.0),
        // Trident
        Map.entry(Material.TRIDENT, 9.0)
    );

    /** Weapon cooldown ticks (vanilla 1.9+ attack speed). */
    private static final Map<Material, Integer> WEAPON_COOLDOWN = Map.ofEntries(
        // Swords (fast - 0.625s = 12.5 ticks)
        Map.entry(Material.NETHERITE_SWORD, 12),
        Map.entry(Material.DIAMOND_SWORD, 12),
        Map.entry(Material.IRON_SWORD, 12),
        Map.entry(Material.GOLDEN_SWORD, 12),
        Map.entry(Material.STONE_SWORD, 12),
        Map.entry(Material.WOODEN_SWORD, 12),
        // Axes (slow - varies by tier)
        Map.entry(Material.NETHERITE_AXE, 20), // 1.0s
        Map.entry(Material.DIAMOND_AXE, 20),
        Map.entry(Material.IRON_AXE, 22),      // 1.1s
        Map.entry(Material.GOLDEN_AXE, 20),
        Map.entry(Material.STONE_AXE, 25),     // 1.25s
        Map.entry(Material.WOODEN_AXE, 25),
        // Trident
        Map.entry(Material.TRIDENT, 22)        // 1.1s
    );

    // ── Totem management ──────────────────────────────────────────────────────

    /** Min/max delay (ticks) when swapping new totem to offhand after use. */
    private static final int TOTEM_SWAP_DELAY_MIN = 4;
    private static final int TOTEM_SWAP_DELAY_MAX = 12;

    // ── Survival system ───────────────────────────────────────────────────────

    /** Health threshold to start fleeing (30% = 6 hearts). */
    private static final double FLEE_HEALTH_THRESHOLD = 6.0;
    /** Health threshold to eat healing food (50% = 10 hearts). */
    private static final double EAT_HEALTH_THRESHOLD = 10.0;
    /** Hunger threshold to eat food (40% = 8 hunger). */
    private static final int EAT_HUNGER_THRESHOLD = 8;

    /** Cooldown between eating attempts (ticks). */
    private static final int EAT_COOLDOWN = 20;
    /** Cooldown between potion throws (ticks). */
    private static final int POTION_COOLDOWN = 30;

    // ── Potion phase system (switch → aim back/down → throw) ─────────────────

    /** Min ticks the bot holds the potion in hand before starting to aim (human reaction lag). */
    private static final int POTION_SWITCH_DELAY_MIN = 3;
    /** Max ticks the bot holds the potion in hand before starting to aim. */
    private static final int POTION_SWITCH_DELAY_MAX = 9;
    /** Ticks the bot spends rotating to look back and down before releasing the throw. */
    private static final int POTION_AIM_TICKS         = 3;
    /**
     * Pitch angle (degrees) when aiming splash potion downward at own feet.
     * Positive pitch = looking downward in Minecraft coordinates.
     */
    private static final float POTION_AIM_DOWN_PITCH  = 50f;
    /** Back-pedal speed during potion aiming (bot retreats while loading). */
    private static final double POTION_RETREAT_SPEED  = 0.09;

    // ── Sprint-reset PvP mechanic ─────────────────────────────────────────────

    /**
     * Probability (per attack) that the bot performs a 1-tick sprint-pause before swinging.
     * Mimics human combat rhythm of briefly releasing the sprint key before a hit.
     */
    private static final double SPRINT_RESET_CHANCE   = 0.55;
    /** Ticks to hold sprint off during a sprint-reset window (1 = single-tick pause). */
    private static final int    SPRINT_RESET_TICKS    = 1;

    // ── Velocity micro-variation ──────────────────────────────────────────────

    /** Small random jitter added to lateral velocity each tick for natural-looking strafing. */
    private static final double VEL_JITTER_MAG        = 0.011;

    // ── Potion-use phase enum ─────────────────────────────────────────────────

    /**
     * Three-stage state machine for human-like splash-potion use.
     * <ol>
     *   <li>{@link #SWITCH_DELAY} – bot is already holding the potion but waits (reaction lag).</li>
     *   <li>{@link #AIM_TURN}    – bot rotates to look back and down toward its own feet.</li>
     *   <li>{@link #THROW}       – bot releases the potion (this tick), then returns to IDLE.</li>
     * </ol>
     */
    private enum PotionPhase { IDLE, SWITCH_DELAY, AIM_TURN, THROW }

    // ── Per-bot state ─────────────────────────────────────────────────────────

    private static final class BotState {
        UUID    targetId       = null;
        UUID    mobTargetId    = null; // hostile mob currently targeting (priority over players)
        UUID    specificTarget = null; // specific player to hunt (set via command)
        boolean defensiveMode  = true; // only attack when attacked first (default: true)

        // Attacker tracking for defensive mode
        UUID    lastAttacker   = null; // player who last hit this bot
        int     attackerTimeout = 0;   // ticks until attacker is forgotten (60 ticks = 3 seconds)

        // Attack
        int     attackCooldown = 0;
        int     comboCount     = 0;   // consecutive hits; resets when target changes

        // Retreat / follow-through
        int     retreatTicks   = 0;
        boolean followThrough  = false; // true = chase instead of retreat after hit

        // Knockback freeze: pause AI movement when hit to let knockback play out
        int     kbFreezeTicks  = 0;

        // Strafe (+1 right / -1 left)
        int     strafeDir      = 1;
        int     strafeTicks    = 0;
        int     strafePause    = 0;   // pause ticks between strafe direction changes

        // W-tap / S-tap rhythm
        int     wtapPhase      = 0;   // 0 = sprint, 1 = drag-back release
        int     wtapTicks      = 6;

        // Jump-crit
        boolean jumpQueued     = false;
        boolean inAirAfterJump = false;

        // Micro-juke yaw offset
        float   jukeOffset     = 0f;
        int     jukeTicks      = 0;

        // Angle-arc entry: offset applied when first entering combat range
        float   arcOffset      = 0f;
        boolean arcActive      = false;
        int     arcTicks       = 0;

        // Human imperfection fields
        int     reactionDelay  = 0;   // ticks of hesitation before next action
        int     mistakeTicks   = 0;   // ticks remaining for current movement mistake
        double  mistakeAngle   = 0.0; // wrong direction angle during mistake

        // Weapon system fields
        int     weaponCheckTimer = 0; // ticks until next weapon inventory check
        Material currentWeapon   = null; // currently equipped weapon type
        int     weaponCooldown   = 0; // ticks until weapon is ready to attack again

        // Totem management fields
        int     totemSwapDelay   = 0; // ticks until totem can be swapped to offhand
        boolean totemUsed        = false; // tracks if totem was just consumed

        // Survival system fields
        boolean isFleeing        = false; // true when running away from danger
        int     eatCooldown      = 0;     // ticks until can eat again
        int     potionCooldown   = 0;     // ticks until can throw potion again
        boolean isEating         = false; // true when actively eating
        int     eatingTicks      = 0;     // ticks remaining to finish eating
        Material pendingFoodType = null;  // food being eaten — benefits applied when timer hits 0

        // ── Potion-phase state machine ────────────────────────────────────────
        PotionPhase potionPhase    = PotionPhase.IDLE;
        int  potionPhaseTimer      = 0;      // countdown ticks for the current phase
        ItemStack queuedPotion     = null;   // the potion snapshot queued for use
        int  queuedPotionSlot      = -1;     // inventory slot the potion came from

        // ── Sprint-reset cadence (human W-tap rhythm) ─────────────────────────
        boolean pendingSprintReset = false;  // true = paused sprint, waiting 1 tick before hitting
        int     sprintResetTicks   = 0;      // ticks left in the sprint-reset window

        // ── Post-hit micro-variation ──────────────────────────────────────────
        int  backpedalBoostTicks   = 0;      // brief extra speed boost on retreat after landing a hit

        // ── Smooth look interpolation (human-like head rotation) ─────────────
        // Inspired by reference mod's BotPlayerActionPack.lookInterpolated().
        // smoothYaw/Pitch hold the currently-broadcast angles; we lerp these toward
        // the desired angles each tick so the head never teleports.
        float smoothYaw   = Float.NaN;  // NaN = uninitialized; snaps to target on first tick
        float smoothPitch = 0f;

        // ── Orbital combat positioning ─────────────────────────────────────
        // Instead of pure perpendicular strafe the bot maintains a rotating orbit
        // around the target at ~PREFERRED_DISTANCE, producing a natural fighting circle.
        double orbitAngle = 0.0;        // current angle on the orbit circle (radians)
        boolean orbitInit = false;      // true once the orbit angle has been seeded from real position

        // ── Aggression scaling ─────────────────────────────────────────────
        // Increased when target is low HP (press the advantage), decreased when
        // bot is low HP / potion phase is running (be cautious).
        double aggression = 1.0;        // 0.5 = cautious, 1.0 = normal, 1.4 = aggressive

        // ── Flee evasion oscillation ──────────────────────────────────────
        int fleeTick = 0;               // increments every flee tick, drives sin-wave strafe

        // ── Datapack-inspired randomized behaviour flags ──────────────────
        // Re-rolled every RANDOMIZE_CD_MIN – RANDOMIZE_CD_MAX ticks, just like
        // randomize.mcfunction in the reference datapack.  Each flag independently
        // gates a behaviour feature for the current cycle.
        boolean tempCrit    = true;   // sprint-reset crits active this cycle
        boolean tempStap    = true;   // s-tap drag-back active this cycle
        boolean tempShield  = false;  // raise shield after 3+ combo hits (requires shield in inv)
        boolean tempStrafe  = true;   // orbital strafing active this cycle
        boolean tempJreset  = true;   // jump-reset on knockback active this cycle
        int     randomizeTimer = 1;   // ticks until next re-roll (1 = fires on first tick)

        // ── Crystal PVP & mobility (datapack: crystal.mcfunction, pearl.mcfunction) ──
        BotCombatMode combatMode = BotCombatMode.FIST;  // current combat mode (auto-detected from inventory)
        int     crystalCooldown  = 0;   // ticks until next crystal place/hit
        int     pearlCooldown    = 0;   // ticks until next ender pearl throw
        boolean isRefilling      = false;  // true when bot is refilling totem (pause combat)
        int     refillTimer      = 0;   // countdown for refill sequence
        int     totemPopCount    = 0;   // number of totems popped this life (for difficulty scaling)
    }

    private final FakePlayerManager manager;
    private final Map<UUID, BotState> states = new ConcurrentHashMap<>();

    public BotPvpAI(FakePlayerManager manager) {
        this.manager = manager;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    public void tickBot(FakePlayer fp, Player bot, List<Player> online) {
        BotState s   = states.computeIfAbsent(fp.getUuid(), k -> {
            BotState ns = new BotState();
            ns.defensiveMode = Config.pvpAiDefensiveMode(); // read from config at first creation
            return ns;
        });
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Tick counters
        if (s.attackCooldown > 0) s.attackCooldown--;
        if (s.retreatTicks   > 0) s.retreatTicks--;
        if (s.kbFreezeTicks  > 0) s.kbFreezeTicks--;
        if (s.strafeTicks    > 0) s.strafeTicks--;
        if (s.strafePause    > 0) s.strafePause--;
        if (s.jukeTicks      > 0) s.jukeTicks--;
        if (s.wtapTicks      > 0) s.wtapTicks--;
        if (s.arcTicks       > 0) s.arcTicks--;
        if (s.arcTicks == 0)      s.arcActive = false;
        if (s.inAirAfterJump && bot.isOnGround()) s.inAirAfterJump = false;

        // Human imperfection counters
        if (s.reactionDelay  > 0) s.reactionDelay--;
        if (s.mistakeTicks   > 0) s.mistakeTicks--;

        // Weapon & totem system counters
        if (s.weaponCheckTimer > 0) s.weaponCheckTimer--;
        if (s.weaponCooldown   > 0) s.weaponCooldown--;
        if (s.totemSwapDelay   > 0) s.totemSwapDelay--;
        if (s.crystalCooldown  > 0) s.crystalCooldown--;
        if (s.pearlCooldown    > 0) s.pearlCooldown--;
        if (s.refillTimer      > 0) s.refillTimer--;

        // Potion-phase, sprint-reset, and post-hit boost counters
        if (s.potionPhaseTimer     > 0) s.potionPhaseTimer--;
        if (s.sprintResetTicks     > 0) s.sprintResetTicks--;
        if (s.backpedalBoostTicks  > 0) s.backpedalBoostTicks--;

        // Survival system counters
        if (s.attackerTimeout  > 0) s.attackerTimeout--;
        if (s.eatCooldown      > 0) s.eatCooldown--;
        if (s.potionCooldown   > 0) s.potionCooldown--;
        // Randomize behaviour flags (datapack: randomize.mcfunction every 9-14 ticks)
        if (s.randomizeTimer   > 0) s.randomizeTimer--;
        if (s.randomizeTimer  == 0) randomize(s, rng);
        if (s.eatingTicks      > 0) {
            s.eatingTicks--;
            // Broadcast eat animation every 4 ticks while eating
            if (s.eatingTicks % 4 == 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getUniqueId().equals(fp.getUuid()))
                        PacketHelper.sendEatAnimation(p, fp);
                }
            }
            // Apply food benefits when timer completes
            if (s.eatingTicks == 0) {
                s.isEating = false;
                if (s.pendingFoodType != null) {
                    applyFoodBenefits(bot, s.pendingFoodType);
                    s.pendingFoodType = null;
                }
            }
        }

        // ── Weapon + armour system: equip best available from inventory ─────────
        if (s.weaponCheckTimer <= 0) {
            updateWeapon(bot, s);
            updateArmor(bot);   // dynamic best-armour equip
            s.weaponCheckTimer = WEAPON_CHECK_INTERVAL;
        }

        // ── Combat mode detection: auto-switch based on inventory ───────────────
        s.combatMode = detectCombatMode(bot);

        // ── Totem refill sequence (datapack: pop.mcfunction) ─────────────────────
        if (s.refillTimer > 0) {
            handleTotemRefill(bot, s);
            return; // Skip all other actions while refilling
        }

        // ── Totem system: manage totem swapping and recovery ─────────────────
        manageTotem(bot, s, rng);
        
        // ── Survival system: check health/hunger and handle healing/eating ───
        handleSurvival(bot, s, rng);

        // ── Safety fallback: if somehow eating without flee flag, just stop ────
        // In practice this block is dead code because startEating always sets
        // isFleeing = true — the bot will always run away while eating healing food.
        if (s.isEating && !s.isFleeing) {
            bot.setSprinting(false);
            // Zero out horizontal velocity so bot stands still
            Vector eatVel = bot.getVelocity();
            eatVel.setX(0);
            eatVel.setZ(0);
            safeSetVelocity(bot, eatVel);
            return; // Skip all combat and movement this tick
        }

        // ── Check for hostile mobs attacking the bot (priority target) ───────
        LivingEntity mobTarget = checkForHostileMobs(bot, s);
        
        // If hostile mob detected, switch to mob combat mode
        if (mobTarget != null) {
            combatMob(fp, bot, s, rng, mobTarget);
            return;
        }
        
        // Clear mob target if no longer valid
        s.mobTargetId = null;

        // Resolve target
        Player target = resolveTarget(fp, bot, s, online);
        if (target == null) {
            s.targetId    = null;
            s.comboCount  = 0;
            bot.setSprinting(false);
            return;
        }
        // Reset combo when target switches; also re-seed orbit so circling restarts naturally
        if (!target.getUniqueId().equals(s.targetId)) {
            s.comboCount = 0;
            s.arcActive  = false;
            s.orbitInit  = false; // re-seed orbit angle for new target
        }
        s.targetId = target.getUniqueId();

        // ── Seed smooth look state on very first appearance ───────────────────
        if (Float.isNaN(s.smoothYaw)) {
            Location bl = bot.getLocation(), tl = target.getLocation();
            double sdx = tl.getX() - bl.getX(), sdz = tl.getZ() - bl.getZ();
            double sdist = Math.sqrt(sdx*sdx + sdz*sdz);
            if (sdist > 1e-4) {
                s.smoothYaw   = (float)(-Math.toDegrees(Math.atan2(sdx, sdz)));
                s.smoothPitch = (float)(-Math.toDegrees(Math.atan2(tl.getY() - bl.getY() + 1.0 - 1.62, sdist)));
            }
        }

        // ── Potion phase machine (suspends all other AI until done) ──────────
        // If the bot is in the middle of a switch→aim→throw cycle, hand off to
        // the phase processor and skip the normal movement + attack logic.
        if (s.potionPhase != PotionPhase.IDLE) {
            processPotionPhase(fp, bot, s, target);
            return;
        }

        // ── Crystal PVP mode (datapack: crystal.mcfunction, d1.mcfunction) ────
        if (s.combatMode == BotCombatMode.CRYSTAL) {
            handleCrystalPvp(fp, bot, s, target, rng);
            return; // Crystal mode has its own complete AI loop
        }

        // ── Ender pearl mobility (datapack: pearl.mcfunction) ─────────────────
        Location botLoc = bot.getLocation();
        Location tgtLoc = target.getLocation();
        double rawDx    = tgtLoc.getX() - botLoc.getX();
        double rawDz    = tgtLoc.getZ() - botLoc.getZ();
        double horizDist = Math.sqrt(rawDx * rawDx + rawDz * rawDz);

        if (horizDist > PEARL_THROW_DISTANCE && s.pearlCooldown == 0 && hasEnderPearl(bot)) {
            throwEnderPearl(bot, s, target, horizDist);
        }

        // ── Random reaction delay (human hesitation) ──────────────────────────
        // Occasionally pause before making decisions (looks less robotic)
        if (s.reactionDelay == 0 && rng.nextDouble() < DELAY_CHANCE) {
            s.reactionDelay = rng.nextInt(DELAY_MIN, DELAY_MAX + 1);
        }
        // Skip AI decisions during delay (still face target, though)
        if (s.reactionDelay > 0) {
            faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist,
                    tgtLoc.getY() - botLoc.getY(), 0f);
            return; // Don't move or attack during delay
        }

        // ── Random movement mistake ───────────────────────────────────────────
        // Occasionally move in the wrong direction briefly (human error)
        if (s.mistakeTicks == 0 && rng.nextDouble() < MOVEMENT_MISTAKE_CHANCE) {
            s.mistakeTicks  = MISTAKE_DURATION;
            s.mistakeAngle  = rng.nextDouble(-90, 91); // ±90° wrong direction
        }


        // ── Update aggression based on target and self HP ────────────────────
        // More aggressive when target is low, more cautious when self is low.
        double targetHpRatio = target.getHealth() / target.getMaxHealth();
        double selfHpRatio   = bot.getHealth() / bot.getMaxHealth();
        s.aggression = 1.0 + (0.4 * (1.0 - targetHpRatio)) - (0.35 * (1.0 - selfHpRatio));
        s.aggression = Math.max(0.55, Math.min(1.45, s.aggression));

        // ── Detect knockback: pause AI movement when hit ─────────────────────
        Vector vel = bot.getVelocity();
        double velMag = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
        // Threshold: horizontal velocity spike above normal movement (0.28) suggests knockback
        if (velMag > 0.35 && s.kbFreezeTicks == 0) {
            s.kbFreezeTicks = 4; // freeze AI movement for 4 ticks to let knockback play out
        }

        // Skip AI movement while knockback is active — let physics handle it
        if (s.kbFreezeTicks > 0) {
            faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist,
                    tgtLoc.getY() - botLoc.getY(), 0f); // still face target, no juke
            // Still allow attacks during knockback
            if (horizDist <= Difficulty.fromConfig().attackRange && s.attackCooldown == 0) {
                performAttack(s, rng, bot, target);
            }
            return;
        }

        // ── Guard: identical position → only attack, skip movement ───────────
        if (horizDist < 1e-4) {
            if (s.attackCooldown == 0) {
                bot.swingMainHand();
                NmsPlayerSpawner.performAttack(bot, target, ATTACK_DAMAGE);
                s.attackCooldown = rng.nextInt(COOLDOWN_MIN, COOLDOWN_MAX + 1);
                s.retreatTicks   = rng.nextInt(3, 6);
            }
            return;
        }

        // ── Angle-arc: enter combat from an angle (more natural approach) ────
        if (!s.arcActive && horizDist > COMBAT_RANGE * 0.9 && horizDist < COMBAT_RANGE * 1.1) {
            s.arcOffset = (float)(rng.nextInt(-30, 31)); // ±30° arc angle at entry
            s.arcTicks  = rng.nextInt(15, 30);
            s.arcActive = true;
        }

        // ── Fleeing mode: run away when low HP with no healing ───────────────
        if (s.isFleeing) {
            doFlee(fp, bot, s, rawDx, rawDz, horizDist);
            return; // Skip combat logic while fleeing (doFlee handles facing direction)
        }

        // ── Movement phase ────────────────────────────────────────────────────
        if (s.retreatTicks > 0 && !s.followThrough) {
            doRetreat(bot, s, rawDx, rawDz, horizDist);
        } else if (horizDist > COMBAT_RANGE) {
            doChase(bot, s, rng, rawDx, rawDz, horizDist, target);
        } else {
            doCombat(bot, s, rng, rawDx, rawDz, horizDist, botLoc, target);
        }

        // ── Face target (predictive aim + juke) ───────────────────────────────
        float juke = (horizDist <= COMBAT_RANGE)
                ? (s.jukeOffset + (s.arcActive ? s.arcOffset * 0.4f : 0f))
                : 0f;
        faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist,
                tgtLoc.getY() - botLoc.getY(), juke);

        // ── Sprint-reset: briefly pause sprinting 1 tick before swinging ────────
        // Mimics human combat rhythm — 55 % of attacks are preceded by a 1-tick sprint pause.
        // Clears the pendingSprintReset flag once the pause window expires.
        if (s.pendingSprintReset) {
            if (s.sprintResetTicks == 0) {
                s.pendingSprintReset = false; // Pause expired — allow the attack this tick
            } else {
                // Still pausing: face target, hold position, then return
                faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist,
                        tgtLoc.getY() - botLoc.getY(), 0f);
                return;
            }
        }

        // ── Melee attack ──────────────────────────────────────────────────────
        double effectiveAttackRange = Difficulty.fromConfig().attackRange;
        if (horizDist <= effectiveAttackRange && s.attackCooldown == 0) {
            // Line-of-sight check: don't attack through walls
            if (hasLineOfSight(bot, target)) {
                // Queue sprint-reset before attack (55 % chance when sprinting, tempCrit gates crits)
                if (s.tempCrit && !s.pendingSprintReset && bot.isSprinting() && rng.nextDouble() < SPRINT_RESET_CHANCE) {
                    bot.setSprinting(false);
                    s.pendingSprintReset = true;
                    s.sprintResetTicks   = SPRINT_RESET_TICKS;
                    // Face target while pausing, then come back next tick
                    faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist,
                            tgtLoc.getY() - botLoc.getY(), 0f);
                    return;
                }
                performAttack(s, rng, bot, target);
            }
        }
    }

    // ── Attack helper ─────────────────────────────────────────────────────────

    /**
     * Executes a melee attack.
     *
     * <h3>Key improvements vs. naive implementation (reference-mod inspired):</h3>
     * <ul>
     *   <li><b>Fall-distance crits</b> — uses {@code bot.getFallDistance() > 0.2f}
     *       (same signal the reference mod's delayed-attack preserves with saved
     *       {@code fallDistance}) rather than only tracking a boolean jump flag.</li>
     *   <li><b>Weapon charge window</b> — like the reference's
     *       {@code getAttackStrengthScale(0.5F) < 1.0F} guard, the bot has a small
     *       random window (0–2 ticks before full charge) where it may swing slightly
     *       early.  Early attacks deal proportionally less damage, matching vanilla.</li>
     *   <li><b>Aggression scaling</b> — combo damage boost now also scaled by
     *       {@code s.aggression} (higher when target is low HP).</li>
     * </ul>
     */
    private static void performAttack(BotState s, ThreadLocalRandom rng, Player bot, Player target) {
        // ── Weapon cooldown check with small early-attack window ─────────────
        // Easy difficulty always waits for full cooldown.
        // Other difficulties allow a small early-attack window (human reflex).
        int earlyWindow = Difficulty.fromConfig().waitFullCooldown ? 0 : rng.nextInt(3);
        if (s.weaponCooldown > earlyWindow) {
            return; // weapon not ready yet
        }
        // Charge ratio: 1.0 = fully charged, <1.0 = slightly early
        double chargeRatio = (s.weaponCooldown == 0) ? 1.0 : Math.max(0.82, 1.0 - s.weaponCooldown * 0.09);

        bot.swingMainHand();

        // ── Random miss (human inaccuracy) ────────────────────────────────────
        // Sometimes swing the arm but don't actually land the hit (8% chance)
        if (rng.nextDouble() < MISS_CHANCE) {
            // Whiffed attack — animation plays but no damage/effects
            applyWeaponCooldown(s, rng);
            s.comboCount     = 0; // Reset combo on miss
            // Still queue a retreat to make it look natural
            s.retreatTicks   = rng.nextInt(2, 4);
            s.followThrough  = false;
            return; // Exit without dealing damage
        }

        // ── Critical hit detection ────────────────────────────────────────────
        // Mirrors the datapack: tempCrit flag gates whether crits are attempted.
        // Physical conditions: falling (fall distance > 0.2) OR post-jump descent.
        // Water, lava, climbing, and on-ground conditions all cancel crits.
        boolean onGround = bot.isOnGround();
        boolean inFluid  = bot.isInWater() || bot.isInLava();
        boolean falling  = bot.getFallDistance() > 0.20f;
        boolean isCritical = s.tempCrit && !onGround && !inFluid
                && (s.inAirAfterJump || falling);

        // Base damage from weapon or fist
        double dmg = getWeaponDamage(s.currentWeapon);

        // Apply charge ratio (early attacks deal less damage — vanilla-accurate)
        dmg *= chargeRatio;

        // Critical hit: 1.5× multiplier (vanilla mechanic)
        if (isCritical) {
            dmg *= 1.5;
        }

        // Combo scaling: consecutive hits slightly increase damage (up to +30%)
        // Further boosted by aggression factor when target is low HP.
        if (s.comboCount > 0) {
            double comboBoost = (1.0 + Math.min(s.comboCount, 6) * 0.05) * s.aggression;
            dmg *= comboBoost;
        }

        // Spawn crit particles BEFORE the attack so players see them
        if (isCritical) {
            target.getWorld().spawnParticle(
                    Particle.CRIT,
                    target.getLocation().add(0, 1.0, 0),
                    8, 0.3, 0.3, 0.3, 0.1);
        }

        // Perform the actual attack using NMS ServerPlayer.attack(Entity)
        // This triggers proper combat mechanics: knockback, crit detection, damage events
        NmsPlayerSpawner.performAttack(bot, target, dmg);

        s.inAirAfterJump = false;
        s.comboCount++;

        // Apply weapon-specific cooldown
        applyWeaponCooldown(s, rng);

        // Post-hit decision: 30 % chance to follow through, 70 % to retreat
        if (rng.nextDouble() < FOLLOWTHROUGH_CHANCE) {
            s.retreatTicks  = 0;
            s.followThrough = true;   // sprint forward instead of back
        } else {
            // Retreat duration varies by weapon speed:
            // Slow weapons (axe ≥ 20 tick cooldown) need shorter retreat — attacks are infrequent anyway.
            // Fast weapons (sword 12 tick cooldown) use a longer retreat window to create hit-and-run rhythm.
            boolean slowWeapon = (s.currentWeapon != null
                    && WEAPON_COOLDOWN.getOrDefault(s.currentWeapon, 12) >= 20);
            s.retreatTicks      = slowWeapon ? rng.nextInt(2, 4) : rng.nextInt(3, 7);
            s.followThrough     = false;
            s.jumpQueued        = rng.nextBoolean();
            s.backpedalBoostTicks = 2; // 2-tick speed boost on the retreat for snappy feel
        }
    }

    // ── CHASE ─────────────────────────────────────────────────────────────────

    private static void doChase(Player bot, BotState s, ThreadLocalRandom rng,
                                 double dx, double dz, double horizDist, Player target) {
        if (horizDist < 1e-4) return;
        double nx = dx / horizDist;
        double nz = dz / horizDist;

        // Apply movement mistake (if active)
        double[] mistake = applyMovementMistake(s, nx, nz);
        nx = mistake[0];
        nz = mistake[1];

        // Speed boost when target is fleeing
        double speed = SPRINT_SPEED;
        Vector tv    = target.getVelocity();
        double dot   = tv.getX() * nx + tv.getZ() * nz; // positive = target moving away
        if (dot > 0.05) speed *= FLEE_BOOST;

        Vector vel = bot.getVelocity();
        vel.setX(nx * speed);
        vel.setZ(nz * speed);

        // Terrain navigation: jump up 1-block obstacles (only when on ground)
        if (bot.isOnGround() && shouldJumpObstacle(bot, nx, nz)) {
            vel.setY(0.42);
        }

        // Apply physics restrictions (water drag, air control)
        applyPhysicsRestrictions(bot, vel);
        
        safeSetVelocity(bot, vel);
        bot.setSprinting(true);

        // Queue jump for crit attack - increased from 33% to 60% chance
        if (horizDist < COMBAT_RANGE * 1.5 && !s.jumpQueued)
            s.jumpQueued = rng.nextInt(10) < 6;
    }

    // ── COMBAT ────────────────────────────────────────────────────────────────

    /**
     * In-range combat movement.
     *
     * <h3>Orbital circling (reference-mod inspired)</h3>
     * Instead of purely perpendicular left/right strafing, the bot maintains an
     * <em>orbit</em> around the target at {@link #PREFERRED_DISTANCE}.  Each tick
     * the orbit angle advances by ~3.2° (adjustable by aggression), so the bot
     * naturally spirals around the target — matching the reference mod's
     * angle-based {@code forward = cos(relAngle)} / {@code strafe = -sin(relAngle)}
     * walking direction, but translated to our direct-velocity API.
     *
     * <p>The W-tap / S-tap rhythm still fires on top: the bot briefly pauses
     * forward thrust to create the sprint-stop rhythm that generates crits.
     */
    private static void doCombat(Player bot, BotState s, ThreadLocalRandom rng,
                                  double dx, double dz, double horizDist,
                                  Location botLoc, Player target) {
        if (horizDist < 1e-4) return;

        Location tgtLoc = target.getLocation();

        double nx = dx / horizDist;
        double nz = dz / horizDist;

        // Apply movement mistake (if active) BEFORE arc offset
        double[] mistake = applyMovementMistake(s, nx, nz);
        nx = mistake[0];
        nz = mistake[1];

        // ── Orbital circling ──────────────────────────────────────────────────
        // Seed the orbit angle from the bot's real position on first entry so the
        // orbit always starts at the correct place (no jarring direction jump).
        if (!s.orbitInit) {
            // atan2 of the direction FROM target TO bot = bot's current angle on the circle
            s.orbitAngle = Math.atan2(botLoc.getX() - tgtLoc.getX(),
                                      botLoc.getZ() - tgtLoc.getZ());
            s.orbitInit  = true;
        }

        // ── Improved strafe: orbit angle advances each tick ───────────────────
        // tempStrafe gates whether strafing is active this randomize cycle.
        // Pause-cadence during strafePause: don't advance the angle (= strafing pause).
        if (!s.tempStrafe) {
            s.strafeDir = 0; // disable strafe this cycle
        } else if (s.strafePause > 0) {
            s.strafeDir = 0;
        } else if (s.strafeTicks <= 0) {
            s.strafePause = rng.nextInt(STRAFE_PAUSE_MAX + 1);
            s.strafeDir   = rng.nextBoolean() ? 1 : -1;
            s.strafeTicks = rng.nextInt(STRAFE_DURATION_MIN, STRAFE_DURATION_MAX + 1);
            s.jukeOffset  = (float) rng.nextInt(-12, 13);
            s.jukeTicks   = s.strafeTicks;
        }

        // Advance orbit angle this tick (modulated by aggression — more aggressive = orbit faster)
        double orbitSpeed = 0.053 * s.aggression; // ~3.0–3.8° / tick
        s.orbitAngle += orbitSpeed * s.strafeDir;

        // Target point on the orbit circle
        double orbitRadius = PREFERRED_DISTANCE * (1.0 + rng.nextDouble() * 0.25); // slight radius jitter
        double orbitTargetX = tgtLoc.getX() + Math.sin(s.orbitAngle) * orbitRadius;
        double orbitTargetZ = tgtLoc.getZ() + Math.cos(s.orbitAngle) * orbitRadius;

        // Direction from bot toward the orbit point
        double toOrbitX  = orbitTargetX - botLoc.getX();
        double toOrbitZ  = orbitTargetZ - botLoc.getZ();
        double orbitDist = Math.sqrt(toOrbitX * toOrbitX + toOrbitZ * toOrbitZ);

        // Perpendicular strafe (traditional) — keeps working when orbit dist is tiny
        double jitter = (rng.nextDouble() - 0.5) * VEL_JITTER_MAG;
        double sx = -nz * s.strafeDir + jitter;
        double sz =  nx * s.strafeDir - jitter;

        // Apply arc-entry angle offset to forward vector
        if (s.arcActive && Math.abs(s.arcOffset) > 1f) {
            double rad = Math.toRadians(s.arcOffset);
            double cos = Math.cos(rad), sin = Math.sin(rad);
            double onx = nx * cos - nz * sin;
            double onz = nx * sin + nz * cos;
            nx = onx; nz = onz;
        }

        // ── Distance management ───────────────────────────────────────────────
        // Zones match the datapack's sprint.mcfunction:
        //   distanceH < 1.2  → back-pedal (MIN_DISTANCE)
        //   1.2 – 3.2        → neutral / orbit
        //   distanceH > 3.2  → sprint forward (doChase handles > COMBAT_RANGE)
        boolean tooClose = horizDist < MIN_DISTANCE;
        boolean tooFar   = horizDist > MAX_DISTANCE;

        // ── S-tap (datapack: hitcd matches 5.. on ground → move backward) ────
        // When tempStap is active and the bot is in the post-hit cooldown window
        // (attackCooldown 5–8 ticks after hitting), force a brief back-pedal.
        // Combined with tempCrit this mimics the classic S-tap into sprint-crit rhythm.
        boolean doStap = s.tempStap && s.attackCooldown >= 5 && s.attackCooldown <= 8
                && bot.isOnGround() && !tooClose;

        // ── Combo shield (datapack: combo matches 3.. → use continuous) ───────
        // If the target has landed 3+ consecutive hits and the bot has a shield,
        // raise it for a brief window to block incoming damage.
        if (s.tempShield && s.comboCount >= 3) {
            raiseShieldIfAvailable(bot);
        }

        // W-tap / S-tap rhythm
        if (s.wtapTicks <= 0) {
            s.wtapPhase = (s.wtapPhase == 0) ? 1 : 0;
            s.wtapTicks = (s.wtapPhase == 0)
                    ? rng.nextInt(4, 8)   // sprint: 4–7 t
                    : rng.nextInt(2, 4);  // drag-back release: 2–3 t
        }

        // Follow-through: sprint forward instead of strafing after an aggressive hit
        if (s.followThrough) {
            Vector vel = bot.getVelocity();
            vel.setX(nx * SPRINT_SPEED * s.aggression);
            vel.setZ(nz * SPRINT_SPEED * s.aggression);
            applyPhysicsRestrictions(bot, vel);
            safeSetVelocity(bot, vel);
            bot.setSprinting(true);
            if (horizDist <= ATTACK_RANGE) s.followThrough = false;
            return;
        }

        Vector vel = bot.getVelocity();

        // S-tap override: briefly back-pedal to reset sprint regardless of distance zone
        if (doStap) {
            vel.setX(-nx * DRAG_SPEED);
            vel.setZ(-nz * DRAG_SPEED);
            bot.setSprinting(false);
            applyPhysicsRestrictions(bot, vel);
            safeSetVelocity(bot, vel);
            return;
        }

        // ── Blend orbital + traditional movement ─────────────────────────────
        // When the bot is far from its orbit point, pull it toward the orbit point
        // strongly.  When close, rely on the pure perpendicular strafe for smoothness.
        if (tooClose) {
            // Back up: orbit angle still advances but we override direction to retreat
            if (s.wtapPhase == 0) {
                vel.setX(-nx * RETREAT_SPEED + sx * STRAFE_SPEED * 0.5);
                vel.setZ(-nz * RETREAT_SPEED + sz * STRAFE_SPEED * 0.5);
                bot.setSprinting(false);
            } else {
                vel.setX(-nx * (RETREAT_SPEED + DRAG_SPEED));
                vel.setZ(-nz * (RETREAT_SPEED + DRAG_SPEED));
                bot.setSprinting(false);
            }
        } else if (tooFar) {
            if (s.wtapPhase == 0) {
                // Blend: move toward orbit point + traditional close-in
                if (orbitDist > 0.5 && orbitDist > 1e-4) {
                    double onx = toOrbitX / orbitDist;
                    double onz = toOrbitZ / orbitDist;
                    vel.setX(onx * COMBAT_SPEED + sx * STRAFE_SPEED * 0.4);
                    vel.setZ(onz * COMBAT_SPEED + sz * STRAFE_SPEED * 0.4);
                } else {
                    vel.setX(nx * COMBAT_SPEED + sx * STRAFE_SPEED);
                    vel.setZ(nz * COMBAT_SPEED + sz * STRAFE_SPEED);
                }
                bot.setSprinting(true);
            } else {
                vel.setX(-nx * DRAG_SPEED + sx * (STRAFE_SPEED * 0.3));
                vel.setZ(-nz * DRAG_SPEED + sz * (STRAFE_SPEED * 0.3));
                bot.setSprinting(false);
            }
        } else {
            // In ideal range: pure orbital strafe, slight forward component
            if (s.wtapPhase == 0) {
                if (orbitDist > 0.3 && orbitDist > 1e-4) {
                    double onx = toOrbitX / orbitDist;
                    double onz = toOrbitZ / orbitDist;
                    // Mostly orbit direction, small forward pull to maintain distance
                    vel.setX(onx * COMBAT_SPEED * 0.8 + nx * COMBAT_SPEED * 0.15);
                    vel.setZ(onz * COMBAT_SPEED * 0.8 + nz * COMBAT_SPEED * 0.15);
                } else {
                    vel.setX(nx * (COMBAT_SPEED * 0.3) + sx * STRAFE_SPEED);
                    vel.setZ(nz * (COMBAT_SPEED * 0.3) + sz * STRAFE_SPEED);
                }
                bot.setSprinting(true);
            } else {
                vel.setX(sx * (STRAFE_SPEED * 0.6));
                vel.setZ(sz * (STRAFE_SPEED * 0.6));
                bot.setSprinting(false);
            }
        }

        // ── Smart jump logic: mostly forward/back, rarely left/right ─────────
        boolean shouldJump = false;
        Vector jumpDir = new Vector(nx, 0, nz);

        if (s.jumpQueued && horizDist <= JUMP_RANGE && horizDist > Difficulty.fromConfig().attackRange && bot.isOnGround()) {
            shouldJump = true;
            if (rng.nextDouble() > JUMP_FORWARD_BACK_CHANCE) {
                jumpDir.setX(sx * 0.8 + nx * 0.2);
                jumpDir.setZ(sz * 0.8 + nz * 0.2);
            }
            s.jumpQueued     = false;
            s.inAirAfterJump = true;
        } else if (bot.isOnGround() && shouldJumpObstacle(bot, nx, nz)) {
            shouldJump = true;
        } else if (tooClose && bot.isOnGround() && rng.nextDouble() < 0.15) {
            shouldJump = true;
            jumpDir.setX(-nx);
            jumpDir.setZ(-nz);
        }

        if (shouldJump) {
            vel.setY(0.42);
            double jumpMag = Math.sqrt(jumpDir.getX() * jumpDir.getX() + jumpDir.getZ() * jumpDir.getZ());
            if (jumpMag > 1e-4) {
                double boost = 0.1;
                vel.setX(vel.getX() + (jumpDir.getX() / jumpMag) * boost);
                vel.setZ(vel.getZ() + (jumpDir.getZ() / jumpMag) * boost);
            }
        }

        applyPhysicsRestrictions(bot, vel);
        safeSetVelocity(bot, vel);
    }

    // ── RETREAT ───────────────────────────────────────────────────────────────

    private static void doRetreat(Player bot, BotState s, double dx, double dz, double horizDist) {
        if (horizDist < 1e-4) return;
        double nx = dx / horizDist;
        double nz = dz / horizDist;

        // Apply movement mistake (if active)
        double[] mistake = applyMovementMistake(s, nx, nz);
        nx = mistake[0];
        nz = mistake[1];

        // Brief speed burst immediately after landing a hit (backpedalBoostTicks > 0)
        double speed = (s.backpedalBoostTicks > 0) ? RETREAT_SPEED * 1.35 : RETREAT_SPEED;

        Vector vel = bot.getVelocity();
        vel.setX(-nx * speed);
        vel.setZ(-nz * speed);
        // Apply physics restrictions (water drag, air control)
        applyPhysicsRestrictions(bot, vel);
        bot.setSprinting(false);
        safeSetVelocity(bot, vel);
    }

    // ── Randomize (datapack: randomize.mcfunction) ────────────────────────────

    /**
     * Re-rolls the per-bot temporary behaviour flags, exactly like the reference
     * datapack's {@code randomize.mcfunction} which fires every 9–14 ticks.
     *
     * <p>Each flag is independently activated with probability
     * {@link Difficulty#flipChance} (higher difficulty = higher chance = more
     * consistent execution of crits, s-taps, etc.).
     * {@code tempShield} has a fixed 50 % chance regardless of difficulty.
     * {@code tempStrafe} has a fixed 70 % chance (usually on).
     */
    private static void randomize(BotState s, ThreadLocalRandom rng) {
        double flip = Difficulty.fromConfig().flipChance;
        s.tempCrit   = rng.nextDouble() < flip;
        s.tempStap   = rng.nextDouble() < flip;
        s.tempJreset = rng.nextDouble() < flip;
        s.tempShield = rng.nextDouble() < 0.50;
        s.tempStrafe = rng.nextDouble() < 0.70;
        s.randomizeTimer = rng.nextInt(RANDOMIZE_CD_MIN, RANDOMIZE_CD_MAX + 1);
    }

    // ── Shield helper ─────────────────────────────────────────────────────────

    /**
     * If the bot has a shield in its offhand (or inventory), start using it.
     * Only activates when no totem is occupying the offhand — totems take priority.
     */
    private static void raiseShieldIfAvailable(Player bot) {
        PlayerInventory inv = bot.getInventory();
        ItemStack offhand = inv.getItemInOffHand();
        // Only raise shield if no totem in offhand
        if (offhand != null && offhand.getType() == Material.TOTEM_OF_UNDYING) return;
        if (offhand != null && offhand.getType() == Material.SHIELD) {
            NmsPlayerSpawner.startUsingMainHandItem(bot); // triggers use-item for offhand via NMS
            return;
        }
        // Check inventory for shield and move to offhand if totem is absent
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.SHIELD) {
                ItemStack prev = inv.getItemInOffHand();
                inv.setItemInOffHand(item.clone());
                inv.setItem(i, (prev == null || prev.getType() == Material.AIR) ? null : prev);
                NmsPlayerSpawner.startUsingMainHandItem(bot);
                return;
            }
        }
    }

    // ── FLEE ──────────────────────────────────────────────────────────────────

    /**
     * Flee from target when low HP.
     *
     * <h3>Improvements over naive "run straight away"</h3>
     * <ul>
     *   <li><b>Oscillating side-strafe</b> — a sin-wave lateral component makes the
     *       escape path unpredictable, similar to how experienced players dodge
     *       while retreating.  Amplitude scales with distance so at very close
     *       range the bot runs more directly away.</li>
     *   <li><b>Speed scales with aggression inversely</b> — when the bot is
     *       critically low it sprints at 130% speed (desperation); when only
     *       slightly low it uses normal flee speed.</li>
     * </ul>
     */
    private static void doFlee(FakePlayer fp, Player bot, BotState s, double dx, double dz, double horizDist) {
        if (horizDist < 1e-4) return;
        double nx = dx / horizDist;
        double nz = dz / horizDist;

        // Perpendicular (strafe) direction
        double px = -nz;
        double pz =  nx;

        // Oscillating side-strafe — sin wave driven by s.fleeTick counter
        s.fleeTick++;
        double strafeAmp   = Math.max(0.0, 1.0 - horizDist / 12.0) * 0.5; // fades at long range
        double sineStrafe  = Math.sin(s.fleeTick * 0.28) * strafeAmp;

        // Base flee speed scales with urgency (lower HP = faster)
        double fleeSpeed = SPRINT_SPEED * (1.1 + (1.0 - s.aggression) * 0.25);

        Vector vel = bot.getVelocity();
        vel.setX(-nx * fleeSpeed + px * sineStrafe);
        vel.setZ(-nz * fleeSpeed + pz * sineStrafe);

        // Jump over obstacles while fleeing
        if (bot.isOnGround() && shouldJumpObstacle(bot, -nx, -nz)) {
            vel.setY(0.42);
        }

        applyPhysicsRestrictions(bot, vel);
        safeSetVelocity(bot, vel);
        bot.setSprinting(true);

        // Face the direction we're running (away from target)
        // Use a direct snap here — during flee we want the bot to look where it's going,
        // not be limited by the smooth lerp (which would lag behind during fast escapes).
        float yaw   = (float)(-Math.toDegrees(Math.atan2(-dx, -dz)));
        float pitch = -5f; // Slight downward tilt — looking at the escape path ahead

        if (Float.isFinite(yaw)) {
            // Sync smooth state so lerp doesn't snap when fight resumes
            if (!Float.isNaN(s.smoothYaw)) {
                s.smoothYaw   = lerpAngle(s.smoothYaw, yaw, 0.65f);
                s.smoothPitch = s.smoothPitch + (pitch - s.smoothPitch) * 0.5f;
                yaw   = s.smoothYaw;
                pitch = s.smoothPitch;
            }
            bot.setRotation(yaw, pitch);
            NmsPlayerSpawner.setHeadYaw(bot, yaw);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getUniqueId().equals(fp.getUuid()))
                    PacketHelper.sendRotation(p, fp, yaw, pitch, yaw);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Applies a movement mistake by rotating the direction vector by the mistake angle.
     * This makes the bot briefly move in the wrong direction (human error).
     *
     * @param s the bot state (contains mistake angle)
     * @param nx normalized X direction (will be modified)
     * @param nz normalized Z direction (will be modified)
     * @return array [newNx, newNz] with mistake applied
     */
    private static double[] applyMovementMistake(BotState s, double nx, double nz) {
        if (s.mistakeTicks <= 0) return new double[]{nx, nz};

        // Rotate direction vector by mistake angle
        double rad = Math.toRadians(s.mistakeAngle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double newNx = nx * cos - nz * sin;
        double newNz = nx * sin + nz * cos;

        return new double[]{newNx, newNz};
    }

    /**
     * Applies realistic physics restrictions to velocity based on bot's environment.
     *
     * <p><b>Water/Lava:</b> Reduces horizontal movement to 50% speed (water drag).
     * <p><b>Mid-air:</b> Limits velocity changes to 0.05 blocks/tick (minimal air control).
     * <p><b>On ground:</b> Full movement control (no restrictions).
     *
     * @param bot the bot entity
     * @param vel the desired velocity vector (will be modified in-place)
     */
    private static void applyPhysicsRestrictions(Player bot, Vector vel) {
        boolean inWater = bot.isInWater() || bot.isInLava();
        boolean onGround = bot.isOnGround();

        // Water/lava: reduce movement speed (realistic water drag)
        if (inWater) {
            vel.setX(vel.getX() * WATER_MOVEMENT_FACTOR);
            vel.setZ(vel.getZ() * WATER_MOVEMENT_FACTOR);
            return; // Water movement doesn't need air control checks
        }

        // Mid-air: limit horizontal velocity changes (minimal air control)
        if (!onGround) {
            Vector current = bot.getVelocity();
            double deltaX = vel.getX() - current.getX();
            double deltaZ = vel.getZ() - current.getZ();

            // Clamp each component to max air control limit
            if (Math.abs(deltaX) > AIR_CONTROL_LIMIT) {
                deltaX = Math.signum(deltaX) * AIR_CONTROL_LIMIT;
            }
            if (Math.abs(deltaZ) > AIR_CONTROL_LIMIT) {
                deltaZ = Math.signum(deltaZ) * AIR_CONTROL_LIMIT;
            }

            // Apply limited change to current velocity (momentum-based)
            vel.setX(current.getX() + deltaX);
            vel.setZ(current.getZ() + deltaZ);
            // Keep vertical velocity unchanged (gravity/jump handled separately)
        }

        // On ground: full control, no restrictions needed
    }

    /**
     * Enhanced obstacle detection — checks if the bot should jump to navigate terrain.
     * 
     * <p>Checks multiple points ahead for better pathfinding:
     * <ul>
     *   <li>Immediate path (0.6 blocks ahead)</li>
     *   <li>Extended path (1.2 blocks ahead for early detection)</li>
     *   <li>Vertical clearance (2 blocks up for safety)</li>
     * </ul>
     * 
     * @return true if bot should jump to navigate obstacle
     */
    private static boolean shouldJumpObstacle(Player bot, double nx, double nz) {
        Location loc = bot.getLocation();
        
        // Check immediate path (0.6 blocks ahead)
        double checkX1 = loc.getX() + nx * 0.6;
        double checkZ1 = loc.getZ() + nz * 0.6;
        
        // Check extended path (1.2 blocks ahead for early detection)
        double checkX2 = loc.getX() + nx * 1.2;
        double checkZ2 = loc.getZ() + nz * 1.2;
        
        Location checkLoc1 = new Location(loc.getWorld(), checkX1, loc.getY(), checkZ1);
        Location checkLoc2 = new Location(loc.getWorld(), checkX2, loc.getY(), checkZ2);
        
        Block immediate = checkLoc1.getBlock();
        Block extended  = checkLoc2.getBlock();
        
        // Check if immediate path has obstacle
        if (immediate.getType().isSolid() && !immediate.isPassable()) {
            // Check if it's a jumpable 1-block obstacle
            Block above = immediate.getRelative(0, 1, 0);
            Block twoAbove = immediate.getRelative(0, 2, 0);
            
            // Can jump if: block above is passable AND block 2-above is passable (full clearance)
            if ((!above.getType().isSolid() || above.isPassable()) && 
                (!twoAbove.getType().isSolid() || twoAbove.isPassable())) {
                return true; // Safe to jump
            }
        }
        
        // Check extended path for early jump (smooth navigation)
        if (extended.getType().isSolid() && !extended.isPassable()) {
            Block extendedAbove = extended.getRelative(0, 1, 0);
            Block extendedTwoAbove = extended.getRelative(0, 2, 0);
            
            // Early jump if clear ahead
            if ((!extendedAbove.getType().isSolid() || extendedAbove.isPassable()) &&
                (!extendedTwoAbove.getType().isSolid() || extendedTwoAbove.isPassable())) {
                // Only jump early if immediate path is also clear (don't jump into walls)
                if (!immediate.getType().isSolid() || immediate.isPassable()) {
                    return true;
                }
            }
        }
        
        // Check for gap/hole ahead (prevent falling)
        Block groundAhead = checkLoc1.getBlock().getRelative(0, -1, 0);
        if (!groundAhead.getType().isSolid() && !bot.isInWater()) {
            // There's a gap — check if we need to jump across
            Block twoAhead = checkLoc2.getBlock().getRelative(0, -1, 0);
            if (twoAhead.getType().isSolid()) {
                // Gap is jumpable (1-block wide)
                return true;
            }
        }
        
        return false;
    }


    /**
     * Sets bot velocity only when all components are finite.
     * Silently drops the call on {@code NaN} / {@code Infinity} to prevent the
     * {@code "x not finite"} crash that occurs when horizDist collapses to zero.
     */
    private static void safeSetVelocity(Player bot, Vector vel) {
        if (!Double.isFinite(vel.getX()) || !Double.isFinite(vel.getY()) || !Double.isFinite(vel.getZ())) {
            return; // discard — avoids IllegalArgumentException from Bukkit
        }
        bot.setVelocity(vel);
    }

    // ── Angle helpers (used by smooth look interpolation) ────────────────────

    /** Wraps {@code deg} to the range {@code [-180, 180]}. */
    private static float wrapDeg(float deg) {
        deg %= 360f;
        if (deg >  180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    /**
     * Lerps from {@code from} toward {@code to} by factor {@code t}, always
     * taking the shortest arc around the 360° circle.
     * <p>Example: {@code lerpAngle(170, -170, 0.5f)} → {@code 180} (goes via 180,
     * not all the way around through 0).
     */
    private static float lerpAngle(float from, float to, float t) {
        return from + wrapDeg(to - from) * t;
    }

    /**
     * Face target with 1-tick predictive aim, an optional yaw juke offset, and
     * <b>smooth look interpolation</b> that lerps the bot's head toward the target
     * angle instead of snapping — matching the reference mod's
     * {@code BotPlayerActionPack.lookInterpolated()} behaviour.
     *
     * <p>Lerp speed adapts to the required turn angle:
     * <ul>
     *   <li>{@literal > 60°} — fast (0.72 / tick) for large corrections</li>
     *   <li>{@literal > 20°} — medium (0.52 / tick) for normal tracking</li>
     *   <li>{@literal ≤ 20°} — slow (0.40 / tick) for fine micro-adjustments</li>
     * </ul>
     * Pitch lerps slightly faster than yaw so the bot doesn't visibly tilt
     * away from the target during quick up/down movements.
     */
    private static void faceTarget(FakePlayer fp, Player bot, BotState s, Player target,
                                    double dx, double dz, double horizDist,
                                    double dy, float jukeOffset) {
        // Lead aim by 1 tick of target's velocity (predictive aim)
        Vector tv    = target.getVelocity();
        double pdx   = dx + tv.getX();
        double pdz   = dz + tv.getZ();
        double pdist = Math.sqrt(pdx * pdx + pdz * pdz);
        if (pdist > 1e-4 && Double.isFinite(pdist)) { dx = pdx; dz = pdz; horizDist = pdist; }

        if (horizDist < 1e-4) return;

        // Desired angles
        float targetYaw   = (float)(-Math.toDegrees(Math.atan2(dx, dz))) + jukeOffset;
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy + 1.0 - 1.62, horizDist)));

        if (!Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)) return;

        // ── Smooth look interpolation ────────────────────────────────────────
        float yaw, pitch;
        if (s != null && !Float.isNaN(s.smoothYaw)) {
            float angleDiff = Math.abs(wrapDeg(targetYaw - s.smoothYaw));
            // Adaptive lerp factor — larger turn = snap faster to avoid lag
            float t = angleDiff > 60f ? 0.72f : (angleDiff > 20f ? 0.52f : 0.40f);
            s.smoothYaw   = lerpAngle(s.smoothYaw, targetYaw, t);
            // Pitch lerps a touch faster (vertical tracking is more critical for visuals)
            float pt = Math.min(1f, t * 1.35f);
            s.smoothPitch = s.smoothPitch + (targetPitch - s.smoothPitch) * pt;
            s.smoothPitch = Math.max(-90f, Math.min(90f, s.smoothPitch));
            yaw   = s.smoothYaw;
            pitch = s.smoothPitch;
        } else {
            // First tick — snap directly and seed the smooth state
            yaw = targetYaw;
            pitch = targetPitch;
            if (s != null) {
                s.smoothYaw   = yaw;
                s.smoothPitch = pitch;
            }
        }
        // ────────────────────────────────────────────────────────────────────

        bot.setRotation(yaw, pitch);
        NmsPlayerSpawner.setHeadYaw(bot, yaw);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(fp.getUuid()))
                PacketHelper.sendRotation(p, fp, yaw, pitch, yaw);
        }
    }

    // ── Target resolution ─────────────────────────────────────────────────────

    // ── Public API for bot configuration ──────────────────────────────────────

    /**
     * Sets a specific player target for this bot to hunt.
     * Bot will prioritize this player over all other targets.
     *
     * @param botUuid the bot's UUID
     * @param targetUuid the player to hunt, or null to clear
     */
    public void setSpecificTarget(UUID botUuid, UUID targetUuid) {
        BotState s = states.get(botUuid);
        if (s != null) {
            s.specificTarget = targetUuid;
            s.defensiveMode = (targetUuid == null); // Aggressive when has specific target
        }
    }

    /**
     * Sets defensive mode for this bot.
     *
     * @param botUuid the bot's UUID
     * @param defensive true = only attack when attacked first, false = attack any player
     */
    public void setDefensiveMode(UUID botUuid, boolean defensive) {
        BotState s = states.get(botUuid);
        if (s != null) {
            s.defensiveMode = defensive;
        }
    }

    /**
     * Called when a bot is damaged by a player.
     * Tracks attacker for defensive mode retaliation.
     *
     * @param botUuid the bot's UUID
     * @param attackerUuid the attacking player's UUID
     */
    public void onBotAttacked(UUID botUuid, UUID attackerUuid) {
        BotState s = states.get(botUuid);
        if (s != null) {
            s.lastAttacker = attackerUuid;
            s.attackerTimeout = 60; // Remember attacker for 3 seconds
        }
    }

    // ── Survival system ───────────────────────────────────────────────────────

    /**
     * Handles bot survival: eating, healing, fleeing when low HP.
     *
     * Priority order:
     * 1. Throw healing potion (instant, works while moving/fleeing)
     * 2. Eat golden apple / enchanted golden apple (healing food)
     * 3. Eat any food (hunger)
     * 4. Flee when critically low with no healing items
     */
    private static void handleSurvival(Player bot, BotState s, ThreadLocalRandom rng) {
        double health = bot.getHealth();
        int    food   = bot.getFoodLevel();

        // Don't start a new action while eating
        if (s.isEating) return;

        // ── Priority 1: POTION (queued: switch → aim back/down → throw) ──────
        // Only start a new potion phase when not already executing one.
        if (health <= EAT_HEALTH_THRESHOLD && s.potionCooldown == 0
                && s.potionPhase == PotionPhase.IDLE) {
            if (queuePotionUse(bot, s, rng)) return;
        }

        // ── Priority 2: HEALING FOOD — only if no healing potion is available ───
        // Never overlap with an active potion phase; never eat food when a potion
        // still exists in inventory (potion is always the higher-priority heal).
        if (health <= EAT_HEALTH_THRESHOLD && s.eatCooldown == 0
                && s.potionPhase == PotionPhase.IDLE
                && !hasHealingPotion(bot)) {
            if (tryEatHealingFood(bot, s)) return;
        }

        // ── Priority 3: ANY FOOD when hungry ─────────────────────────────────
        if (food <= EAT_HUNGER_THRESHOLD && s.eatCooldown == 0) {
            tryEatAnyFood(bot, s);
        }

        // ── Flee decision: critical HP and nothing worked ─────────────────────
        if (health <= FLEE_HEALTH_THRESHOLD) {
            // Still fleeing → keep fleeing until recovered
            if (!s.isFleeing) s.fleeTick = 0; // reset oscillation on flee start
            s.isFleeing = true;
        }

        // Stop fleeing once health is safely above threshold
        if (s.isFleeing && health > FLEE_HEALTH_THRESHOLD + 4.0) {
            s.isFleeing = false;
            s.orbitInit = false; // re-seed orbit so re-engagement looks natural
        }
    }

    /**
     * Queues a healing potion for use by starting the 3-phase potion state machine.
     *
     * <p>Phase order:
     * <ol>
     *   <li>{@link PotionPhase#SWITCH_DELAY} — bot holds the potion but waits
     *       {@link #POTION_SWITCH_DELAY_MIN}–{@link #POTION_SWITCH_DELAY_MAX} ticks
     *       (human reaction lag; bot retreats slowly during this window).</li>
     *   <li>{@link PotionPhase#AIM_TURN} — bot rotates to look back and downward
     *       ({@link #POTION_AIM_TICKS} ticks), so the splash lands at its own feet
     *       without "locking on" to the enemy with its head.</li>
     *   <li>{@link PotionPhase#THROW} — bot executes the throw (see
     *       {@link #executePotionThrow}), then resets to IDLE.</li>
     * </ol>
     *
     * @return {@code true} if a potion was found and the phase machine was started
     */
    private static boolean queuePotionUse(Player bot, BotState s, ThreadLocalRandom rng) {
        PlayerInventory inv = bot.getInventory();

        // Prioritize SPLASH potions over drinkable — more impactful
        for (int pass = 0; pass < 2; pass++) {
            Material targetType = (pass == 0) ? Material.SPLASH_POTION : Material.POTION;

            for (int i = 0; i < 36; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() != targetType) continue;
                if (!(item.getItemMeta() instanceof PotionMeta meta)) continue;
                if (!isHealingPotionMeta(meta)) continue;

                // Snapshot the potion for later use
                s.queuedPotion    = item.clone();
                s.queuedPotionSlot = i;

                // Move potion to main hand immediately so observers see the switch
                ItemStack currentMain = inv.getItemInMainHand();
                inv.setItemInMainHand(item.clone());
                inv.setItem(i, (currentMain == null || currentMain.getType() == Material.AIR)
                        ? null : currentMain);

                // Start SWITCH_DELAY phase (hold potion, wait before aiming)
                s.potionPhase      = PotionPhase.SWITCH_DELAY;
                s.potionPhaseTimer = rng.nextInt(POTION_SWITCH_DELAY_MIN, POTION_SWITCH_DELAY_MAX + 1);

                return true;
            }
        }
        return false;
    }

    /**
     * Per-tick handler for the potion state machine.
     * Called by {@link #tickBot} whenever {@code s.potionPhase != IDLE}.
     *
     * <ul>
     *   <li>SWITCH_DELAY — bot retreats slowly, faces target, waits for timer.</li>
     *   <li>AIM_TURN     — bot smoothly rotates to look away from target and downward.</li>
     *   <li>THROW        — bot executes the throw, then returns to IDLE.</li>
     * </ul>
     */
    private static void processPotionPhase(FakePlayer fp, Player bot, BotState s, Player target) {
        // Advance the countdown first; phases transition when it reaches 0.
        // (potionPhaseTimer was already decremented in tickBot's counter section.)

        switch (s.potionPhase) {

            case SWITCH_DELAY -> {
                // Holding potion — stop sprinting, do a slow back-pedal while "preparing"
                bot.setSprinting(false);

                if (target != null) {
                    Location botLoc = bot.getLocation();
                    Location tgtLoc = target.getLocation();
                    double dx       = tgtLoc.getX() - botLoc.getX();
                    double dz       = tgtLoc.getZ() - botLoc.getZ();
                    double dist     = Math.sqrt(dx * dx + dz * dz);

                    if (dist > 1e-4) {
                        double nx = dx / dist;
                        double nz = dz / dist;
                        Vector vel = bot.getVelocity();
                        vel.setX(-nx * POTION_RETREAT_SPEED);
                        vel.setZ(-nz * POTION_RETREAT_SPEED);
                        applyPhysicsRestrictions(bot, vel);
                        safeSetVelocity(bot, vel);

                        // Keep facing the target naturally during the hold
                        double dy = tgtLoc.getY() - botLoc.getY();
                        faceTarget(fp, bot, s, target, dx, dz, dist, dy, 0f);
                    }
                }

                // Timer reached 0 → transition to aim
                if (s.potionPhaseTimer == 0) {
                    s.potionPhase      = PotionPhase.AIM_TURN;
                    s.potionPhaseTimer = POTION_AIM_TICKS;
                }
            }

            case AIM_TURN -> {
                // Rotate to look AWAY from target and DOWN at own feet.
                // This prevents the bot's head from pointing at the enemy while healing —
                // a tell-tale sign of automation.  The smooth lerp makes the turn look natural.
                bot.setSprinting(false);

                float awayYaw;
                if (target != null) {
                    Location botLoc = bot.getLocation();
                    Location tgtLoc = target.getLocation();
                    double dx       = tgtLoc.getX() - botLoc.getX();
                    double dz       = tgtLoc.getZ() - botLoc.getZ();
                    double dist     = Math.sqrt(dx * dx + dz * dz);

                    if (dist > 1e-4) {
                        // Slow retreat while turning
                        double nx = dx / dist, nz = dz / dist;
                        Vector vel = bot.getVelocity();
                        vel.setX(-nx * POTION_RETREAT_SPEED);
                        vel.setZ(-nz * POTION_RETREAT_SPEED);
                        applyPhysicsRestrictions(bot, vel);
                        safeSetVelocity(bot, vel);
                    }

                    // Yaw pointing AWAY from target (exact opposite direction)
                    double rdx = tgtLoc.getX() - botLoc.getX();
                    double rdz = tgtLoc.getZ() - botLoc.getZ();
                    awayYaw = (float)(-Math.toDegrees(Math.atan2(-rdx, -rdz)));
                } else {
                    awayYaw = bot.getLocation().getYaw(); // no target — just look down
                }

                // Lerp pitch toward POTION_AIM_DOWN_PITCH for a smooth tilt
                float curPitch  = bot.getLocation().getPitch();
                float lerpPitch = curPitch + (POTION_AIM_DOWN_PITCH - curPitch) * 0.6f;

                if (Float.isFinite(awayYaw) && Float.isFinite(lerpPitch)) {
                    bot.setRotation(awayYaw, lerpPitch);
                    NmsPlayerSpawner.setHeadYaw(bot, awayYaw);

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.getUniqueId().equals(fp.getUuid()))
                            PacketHelper.sendRotation(p, fp, awayYaw, lerpPitch, awayYaw);
                    }
                }

                // Timer reached 0 → throw
                if (s.potionPhaseTimer == 0) {
                    s.potionPhase      = PotionPhase.THROW;
                    s.potionPhaseTimer = 1; // executes next tick
                }
            }

            case THROW -> {
                // Execute the actual throw, then return to IDLE
                executePotionThrow(bot, s);
                s.potionPhase      = PotionPhase.IDLE;
                s.potionPhaseTimer = 0;
            }

            default -> s.potionPhase = PotionPhase.IDLE; // safety fallback
        }
    }

    /**
     * Executes the actual splash / drinkable potion use.
     * Called from {@link #processPotionPhase} during the THROW phase.
     *
     * <p>For splash potions the bot spawns a visual {@link ThrownPotion} with a
     * gentle downward velocity so it explodes directly underfoot, matching the
     * look-back/look-down aim set up during AIM_TURN.  Effects are also applied
     * directly for reliability regardless of whether the splash entity triggers them.
     */
    private static void executePotionThrow(Player bot, BotState s) {
        if (s.queuedPotion == null) return;

        ItemStack potion = s.queuedPotion;
        if (!(potion.getItemMeta() instanceof PotionMeta meta)) {
            s.queuedPotion    = null;
            s.queuedPotionSlot = -1;
            return;
        }

        boolean isSplash = (potion.getType() == Material.SPLASH_POTION);

        if (isSplash) {
            // Throw at own feet — bot is already looking down/back from AIM_TURN
            Location throwLoc = bot.getLocation().add(0, 1.5, 0);
            ThrownPotion thrown = bot.getWorld().spawn(throwLoc, ThrownPotion.class);
            thrown.setItem(potion.clone());
            // Small downward velocity so it splashes right at the bot's feet
            thrown.setVelocity(new Vector(0, -0.5, 0));
            thrown.setShooter(bot);
        }

        // Apply effects directly as well (reliable fallback even if splash misses)
        applyPotionEffects(bot, meta);

        // Consume the item from main hand
        ItemStack mainHand = bot.getInventory().getItemInMainHand();
        if (mainHand != null && !mainHand.getType().isAir()) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            if (mainHand.getAmount() <= 0) bot.getInventory().setItemInMainHand(null);
        }

        s.potionCooldown   = POTION_COOLDOWN;
        s.queuedPotion     = null;
        s.queuedPotionSlot = -1;
    }

    /**
     * Returns true if the bot has any healing (splash or drinkable) potion in its inventory.
     * Used to decide whether to fall back to food or keep waiting for a potion use.
     */
    private static boolean hasHealingPotion(Player bot) {
        PlayerInventory inv = bot.getInventory();
        for (int pass = 0; pass < 2; pass++) {
            Material targetType = (pass == 0) ? Material.SPLASH_POTION : Material.POTION;
            for (int i = 0; i < 36; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() != targetType) continue;
                if (!(item.getItemMeta() instanceof PotionMeta meta)) continue;
                if (isHealingPotionMeta(meta)) return true;
            }
        }
        return false;
    }

    /** Returns true if the PotionMeta has healing or regeneration (base type OR custom effects). */
    private static boolean isHealingPotionMeta(PotionMeta meta) {
        // Check base potion type (normal brewing stand potions — most common)
        PotionType base = meta.getBasePotionType();
        if (base != null) {
            String n = base.name();
            if (n.contains("HEALING") || n.contains("HEALTH")
                    || n.contains("REGENERATION") || n.contains("REGEN")) {
                return true;
            }
        }
        // Also check custom effects (API-added effects)
        for (PotionEffect effect : meta.getCustomEffects()) {
            PotionEffectType t = effect.getType();
            if (t.equals(PotionEffectType.INSTANT_HEALTH)
                    || t.equals(PotionEffectType.REGENERATION)) {
                return true;
            }
        }
        return false;
    }

    /** Applies all potion effects from meta to the bot (custom + base type). */
    private static void applyPotionEffects(Player bot, PotionMeta meta) {
        for (PotionEffect effect : meta.getCustomEffects()) {
            bot.addPotionEffect(effect);
        }
        PotionType base = meta.getBasePotionType();
        if (base != null) {
            String n = base.name();
            if (n.contains("HEALING") || n.contains("HEALTH")) {
                double heal = n.contains("STRONG") ? 8.0 : 4.0;
                bot.setHealth(Math.min(bot.getMaxHealth(), bot.getHealth() + heal));
            } else if (n.contains("REGENERATION") || n.contains("REGEN")) {
                int amp = n.contains("STRONG") ? 1 : 0;
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, amp));
            }
        }
    }

    /**
     * Try to eat healing food (golden apple, enchanted golden apple).
     */
    private static boolean tryEatHealingFood(Player bot, BotState s) {
        return tryEatFood(bot, s, true);
    }

    /**
     * Try to eat any edible food.
     */
    private static boolean tryEatAnyFood(Player bot, BotState s) {
        return tryEatFood(bot, s, false);
    }

    /**
     * Try to eat food from inventory.
     *
     * @param healingOnly if true, only eat golden apples; if false, eat any food
     */
    private static boolean tryEatFood(Player bot, BotState s, boolean healingOnly) {
        PlayerInventory inv = bot.getInventory();

        // Priority order for healing food
        Material[] healingPriority = {
            Material.ENCHANTED_GOLDEN_APPLE, // Best healing
            Material.GOLDEN_APPLE             // Good healing
        };

        // Check healing food first if needed
        if (healingOnly) {
            for (Material food : healingPriority) {
                for (int i = 0; i < 36; i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() == food) {
                        startEating(bot, s, item, i);
                        return true;
                    }
                }
            }
            return false;
        }

        // Find any edible food
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType().isEdible()) {
                startEating(bot, s, item, i);
                return true;
            }
        }

        return false;
    }

    /**
     * Start eating food item.
     * Moves food to main hand, triggers NMS startUsingItem so Minecraft handles
     * the eating animation, sounds, duration (32 ticks) and item consumption natively.
     * Benefits are applied by Minecraft's own food mechanic when eating completes.
     */
    private static void startEating(Player bot, BotState s, ItemStack food, int slot) {
        PlayerInventory inv = bot.getInventory();

        // Move food to main hand (keep it there — Minecraft will consume it after 32 ticks)
        ItemStack currentMain = inv.getItemInMainHand();
        if (currentMain.getType() == Material.AIR) currentMain = null;
        inv.setItemInMainHand(food.clone());
        inv.setItem(slot, currentMain);

        // Tell NMS the bot is now using the item in its main hand.
        // This: sets isHandActive metadata, broadcasts to nearby clients (eat animation),
        // queues item-use processing in doTick(), and after 32 ticks Minecraft consumes
        // the item and applies food effects automatically.
        NmsPlayerSpawner.startUsingMainHandItem(bot);

        // Track eating state so we can pause PVP while eating
        s.isEating        = true;
        s.eatingTicks     = 36; // slightly longer than vanilla 32 ticks for safety
        s.eatCooldown     = EAT_COOLDOWN;
        s.pendingFoodType = null; // Minecraft handles benefits natively now
        s.isFleeing       = true; // flee while eating — bot sprints away instead of standing still
    }

    /**
     * Applies the nutrition and potion effects for a given food type.
     * Only called as fallback if NMS eating fails.
     */
    private static void applyFoodBenefits(Player bot, Material type) {
        switch (type) {
            case ENCHANTED_GOLDEN_APPLE -> {
                bot.setHealth(Math.min(bot.getMaxHealth(), bot.getHealth() + 8.0));
                bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 4));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,  2400, 3));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,  6000, 0));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
            }
            case GOLDEN_APPLE -> {
                bot.setHealth(Math.min(bot.getMaxHealth(), bot.getHealth() + 4.0));
                bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 4));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,   2400, 0));
            }
            case COOKED_BEEF, COOKED_PORKCHOP -> {
                bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 8));
                bot.setSaturation(Math.min(20f, bot.getSaturation() + 12.8f));
            }
            case BREAD -> {
                bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 5));
                bot.setSaturation(Math.min(20f, bot.getSaturation() + 6f));
            }
            case COOKED_CHICKEN -> {
                bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 6));
                bot.setSaturation(Math.min(20f, bot.getSaturation() + 7.2f));
            }
            default -> {
                // Generic edible: give 3 food points
                if (type.isEdible()) {
                    bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 3));
                }
            }
        }
    }

    // ── Weapon system helpers ─────────────────────────────────────────────────
    
    /**
     * Scans bot inventory and equips the best available weapon.
     * Runs every {@link #WEAPON_CHECK_INTERVAL} ticks (2 seconds).
     * Includes intelligent inventory management to make space.
     */
    private static void updateWeapon(Player bot, BotState s) {
        PlayerInventory inv = bot.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        
        // Find best weapon in entire inventory
        ItemStack bestWeapon = null;
        double bestDamage = ATTACK_DAMAGE; // Start with fist damage (1.0)
        int bestSlot = -1;
        
        // Check hotbar (slots 0-8)
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && WEAPON_DAMAGE.containsKey(item.getType())) {
                double damage = WEAPON_DAMAGE.get(item.getType());
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestWeapon = item;
                    bestSlot = slot;
                }
            }
        }
        
        // If no weapon in hotbar, check main inventory (9-35) and move to hotbar
        if (bestWeapon == null) {
            for (int slot = 9; slot < 36; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item != null && WEAPON_DAMAGE.containsKey(item.getType())) {
                    double damage = WEAPON_DAMAGE.get(item.getType());
                    if (damage > bestDamage) {
                        bestDamage = damage;
                        bestWeapon = item;
                        bestSlot = slot;
                    }
                }
            }

            // If found weapon in main inventory, move to hotbar
            if (bestWeapon != null) {
                int emptyHotbarSlot = findEmptyHotbarSlot(inv);
                if (emptyHotbarSlot != -1) {
                    // Move weapon to empty hotbar slot
                    inv.setItem(emptyHotbarSlot, bestWeapon.clone());
                    inv.setItem(bestSlot, null);
                    bestSlot = emptyHotbarSlot;
                } else {
                    // Hotbar full - swap with least valuable item in hotbar
                    int swapSlot = findLeastValuableHotbarSlot(inv);
                    ItemStack temp = inv.getItem(swapSlot);
                    inv.setItem(swapSlot, bestWeapon.clone());
                    inv.setItem(bestSlot, temp);
                    bestSlot = swapSlot;
                }
            }
        }

        // If better weapon found, switch to it
        if (bestWeapon != null && !bestWeapon.equals(mainHand)) {
            // Move current mainhand item to the weapon's slot
            inv.setItem(bestSlot, mainHand);
            // Equip best weapon
            inv.setItemInMainHand(bestWeapon);
            s.currentWeapon = bestWeapon.getType();
        } else if (mainHand != null && mainHand.getType() != Material.AIR) {
            s.currentWeapon = mainHand.getType();
        } else {
            s.currentWeapon = null; // Fist
        }
    }
    
    /**
     * Gets the attack damage for the given weapon type.
     * Returns fist damage (1.0) if weapon is null or not recognized.
     */
    private static double getWeaponDamage(Material weapon) {
        if (weapon == null) return ATTACK_DAMAGE;
        return WEAPON_DAMAGE.getOrDefault(weapon, ATTACK_DAMAGE);
    }
    
    /**
     * Applies weapon-specific attack cooldown.
     * Uses vanilla attack speed values for realistic timing.
     */
    private static void applyWeaponCooldown(BotState s, ThreadLocalRandom rng) {
        if (s.currentWeapon != null && WEAPON_COOLDOWN.containsKey(s.currentWeapon)) {
            // Use weapon-specific cooldown
            s.weaponCooldown = WEAPON_COOLDOWN.get(s.currentWeapon);
            s.attackCooldown = s.weaponCooldown; // Also update legacy cooldown for compatibility
        } else {
            // Fist cooldown (slightly randomized for human-like timing)
            int cdBase = rng.nextInt(COOLDOWN_MIN, COOLDOWN_MAX + 1);
            // Combo bonus: faster attacks as combo grows
            s.attackCooldown = Math.max(COOLDOWN_MIN, cdBase - Math.min(s.comboCount / 2, 3));
            s.weaponCooldown = s.attackCooldown;
        }
    }
    
    // ── Totem management system ───────────────────────────────────────────────
    
    /**
     * Manages Totem of Undying in offhand slot.
     * 
     * <p>Behavior:
     * <ul>
     *   <li>If no totem in offhand but totem in inventory → move to offhand</li>
     *   <li>If totem was used (offhand now empty) → wait random delay, then swap new totem</li>
     *   <li>Delay: {@link #TOTEM_SWAP_DELAY_MIN}-{@link #TOTEM_SWAP_DELAY_MAX} ticks (human-like)</li>
     *   <li>Handles inventory reorganization if needed</li>
     * </ul>
     */
    private static void manageTotem(Player bot, BotState s, ThreadLocalRandom rng) {
        PlayerInventory inv = bot.getInventory();
        ItemStack offhand = inv.getItemInOffHand();
        
        // Check if totem was just used (offhand went from totem to empty/air)
        if (s.totemUsed && s.totemSwapDelay == 0) {
            // Totem cooldown expired — try to get new totem
            ItemStack newTotem = findTotemInInventory(inv);
            if (newTotem != null) {
                // Move new totem to offhand
                inv.setItemInOffHand(newTotem.clone());
                newTotem.setAmount(0); // Remove from original slot
                s.totemUsed = false;
            }
        }
        
        // Detect if totem was just consumed
        if (!s.totemUsed && (offhand == null || offhand.getType() == Material.AIR)) {
            // Check if we previously had a totem (by checking inventory for totems)
            // If we find totems in inventory but offhand is empty, a totem was likely used
            if (findTotemInInventory(inv) != null) {
                s.totemUsed = true;
                // Set random delay before swapping new totem (human-like hesitation)
                s.totemSwapDelay = rng.nextInt(TOTEM_SWAP_DELAY_MIN, TOTEM_SWAP_DELAY_MAX + 1);
            }
        }
        
        // If no totem in offhand and not in cooldown, try to equip one
        if (!s.totemUsed && s.totemSwapDelay == 0 && 
            (offhand == null || offhand.getType() != Material.TOTEM_OF_UNDYING)) {
            ItemStack totem = findTotemInInventory(inv);
            if (totem != null) {
                ItemStack currentOffhand = inv.getItemInOffHand();

                // Move totem to offhand
                inv.setItemInOffHand(totem.clone());
                totem.setAmount(0); // Remove from original slot
                
                // If offhand had something, try to put it back in inventory
                if (currentOffhand != null && currentOffhand.getType() != Material.AIR) {
                    // Try to find empty slot in inventory
                    int emptySlot = findEmptyInventorySlot(inv);
                    if (emptySlot != -1) {
                        inv.setItem(emptySlot, currentOffhand);
                    } else {
                        // No space - drop it (better than keeping totem out of offhand)
                        bot.getWorld().dropItemNaturally(bot.getLocation(), currentOffhand);
                    }
                }
            }
        }
    }
    
    /**
     * Finds the first Totem of Undying in bot's inventory (excluding offhand).
     * 
     * @return the totem ItemStack, or null if none found
     */
    private static ItemStack findTotemInInventory(PlayerInventory inv) {
        // Check hotbar (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                return item;
            }
        }
        // Check main inventory (9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                return item;
            }
        }
        return null;
    }
    
    // ── Inventory management helpers ──────────────────────────────────────────
    
    /**
     * Finds the first empty slot in the hotbar.
     * @return slot index (0-8), or -1 if hotbar is full
     */
    private static int findEmptyHotbarSlot(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Finds the first empty slot in entire inventory (excluding armor/offhand).
     * @return slot index (0-35), or -1 if inventory is full
     */
    private static int findEmptyInventorySlot(PlayerInventory inv) {
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Finds the least valuable item in hotbar (for swapping with better weapon).
     * Prioritizes non-weapons, then weaker weapons, then totems last.
     * 
     * @return slot index (0-8) of least valuable item
     */
    private static int findLeastValuableHotbarSlot(PlayerInventory inv) {
        int worstSlot = 0;
        double lowestValue = Double.MAX_VALUE;
        
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            double value = getItemValue(item);
            
            if (value < lowestValue) {
                lowestValue = value;
                worstSlot = i;
            }
        }
        
        return worstSlot;
    }
    
    /**
     * Calculates item value for inventory management decisions.
     * Higher value = more important to keep.
     * 
     * @return value score (0-1000)
     */
    private static double getItemValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0.0; // Empty = lowest value
        }
        
        Material type = item.getType();
        
        // Totems are most valuable (don't swap these)
        if (type == Material.TOTEM_OF_UNDYING) {
            return 1000.0;
        }
        
        // Weapons have value based on damage
        if (WEAPON_DAMAGE.containsKey(type)) {
            return WEAPON_DAMAGE.get(type) * 100; // 400-1000 range
        }
        
        // Golden apples are valuable
        if (type == Material.GOLDEN_APPLE) {
            return 800.0;
        }
        if (type == Material.ENCHANTED_GOLDEN_APPLE) {
            return 950.0;
        }
        
        // Armor pieces
        if (type.name().endsWith("_HELMET") || type.name().endsWith("_CHESTPLATE") ||
            type.name().endsWith("_LEGGINGS") || type.name().endsWith("_BOOTS")) {
            if (type.name().startsWith("NETHERITE")) return 700.0;
            if (type.name().startsWith("DIAMOND")) return 600.0;
            if (type.name().startsWith("IRON")) return 500.0;
            if (type.name().startsWith("GOLDEN")) return 400.0;
            if (type.name().startsWith("CHAINMAIL")) return 450.0;
            return 300.0; // Leather
        }
        
        // Food items
        if (type.isEdible()) {
            return 200.0;
        }
        
        // Ender pearls
        if (type == Material.ENDER_PEARL) {
            return 600.0;
        }
        
        // Potions
        if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
            return 500.0;
        }
        
        // Everything else has minimal value
        return 100.0;
    }
    
    // ── Line-of-sight check ───────────────────────────────────────────────────
    
    /**
     * Checks if the bot has a clear line of sight to the target.
     * Prevents attacking through walls/blocks.
     * 
     * @return true if bot can see target, false if blocked by solid blocks
     */
    private static boolean hasLineOfSight(Player bot, Player target) {
        Location botEye = bot.getEyeLocation();
        Location targetEye = target.getEyeLocation();
        
        // Use Bukkit's built-in line-of-sight check
        // This raytraces between the two locations and checks for solid blocks
        try {
            return bot.hasLineOfSight(target);
        } catch (Exception e) {
            // Fallback: manual raytrace if hasLineOfSight fails
            return raytraceLineOfSight(botEye, targetEye);
        }
    }
    
    /**
     * Overload for LivingEntity targets (mobs).
     */
    private static boolean hasLineOfSight(Player bot, LivingEntity target) {
        Location botEye = bot.getEyeLocation();
        Location targetEye = target.getEyeLocation();
        
        try {
            return bot.hasLineOfSight(target);
        } catch (Exception e) {
            return raytraceLineOfSight(botEye, targetEye);
        }
    }
    
    /**
     * Manual raytrace for line-of-sight check.
     * Steps along the ray from bot to target, checking for solid blocks.
     * 
     * @return true if path is clear, false if blocked
     */
    private static boolean raytraceLineOfSight(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) {
            return false;
        }
        
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();
        
        // Step along the ray in 0.5 block increments
        double step = 0.5;
        for (double d = 0; d < distance; d += step) {
            Location check = from.clone().add(direction.clone().multiply(d));
            Block block = check.getBlock();
            
            // If block is solid and not passable, line of sight is blocked
            if (block.getType().isSolid() && !block.isPassable()) {
                return false;
            }
        }
        
        return true; // Path is clear
    }
    
    // ── Hostile mob detection and combat ──────────────────────────────────────
    
    /**
     * Checks for hostile mobs near the bot that are targeting it.
     * Priority: Mobs currently attacking > Mobs with aggro > Nearby hostile mobs
     * 
     * @return the hostile mob to fight, or null if none detected
     */
    private static LivingEntity checkForHostileMobs(Player bot, BotState s) {
        // Check if we already have a mob target that's still valid
        if (s.mobTargetId != null) {
            for (Entity entity : bot.getWorld().getEntities()) {
                if (entity.getUniqueId().equals(s.mobTargetId) && entity instanceof Mob mob) {
                    if (mob.isValid() && !mob.isDead() && 
                        bot.getLocation().distanceSquared(mob.getLocation()) <= MOB_DETECT_RANGE * MOB_DETECT_RANGE) {
                        return mob;
                    }
                }
            }
            // Existing target no longer valid
            s.mobTargetId = null;
        }
        
        // Find hostile mobs within range
        Mob closestAggro = null;
        double closestAggroDist = MOB_DETECT_RANGE * MOB_DETECT_RANGE;
        
        Mob closestHostile = null;
        double closestHostileDist = MOB_DETECT_RANGE * MOB_DETECT_RANGE;
        
        for (Entity entity : bot.getNearbyEntities(MOB_DETECT_RANGE, MOB_DETECT_RANGE, MOB_DETECT_RANGE)) {
            if (!(entity instanceof Mob mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            
            // Check if it's a hostile mob type
            if (!isHostileMob(mob)) continue;
            
            double distSq = bot.getLocation().distanceSquared(mob.getLocation());
            
            // Priority 1: Mobs actively targeting the bot
            if (mob.getTarget() != null && mob.getTarget().equals(bot)) {
                if (distSq < closestAggroDist) {
                    closestAggroDist = distSq;
                    closestAggro = mob;
                }
            }
            // Priority 2: Nearby hostile mobs
            else if (distSq < closestHostileDist) {
                closestHostileDist = distSq;
                closestHostile = mob;
            }
        }
        
        // Return highest priority target
        if (closestAggro != null) {
            s.mobTargetId = closestAggro.getUniqueId();
            return closestAggro;
        }
        if (closestHostile != null) {
            s.mobTargetId = closestHostile.getUniqueId();
            return closestHostile;
        }
        
        return null;
    }
    
    /**
     * Checks if the given mob is a hostile type that should be fought.
     */
    private static boolean isHostileMob(Mob mob) {
        return switch (mob.getType()) {
            case ZOMBIE, HUSK, DROWNED, SKELETON, STRAY, WITHER_SKELETON,
                 CREEPER, SPIDER, CAVE_SPIDER, ENDERMAN, BLAZE, GHAST,
                 SLIME, MAGMA_CUBE, WITCH, VINDICATOR, EVOKER, PILLAGER,
                 RAVAGER, VEX, PHANTOM, HOGLIN, PIGLIN_BRUTE, ZOGLIN,
                 WARDEN, SILVERFISH, ENDERMITE, GUARDIAN, ELDER_GUARDIAN,
                 SHULKER, PIGLIN, ZOMBIFIED_PIGLIN -> true;
            default -> false;
        };
    }
    
    /**
     * Combat routine when fighting hostile mobs.
     * Simpler than player combat - just chase and attack.
     */
    private static void combatMob(FakePlayer fp, Player bot, BotState s, 
                                    ThreadLocalRandom rng, LivingEntity mobTarget) {
        Location botLoc = bot.getLocation();
        Location mobLoc = mobTarget.getLocation();
        
        double dx = mobLoc.getX() - botLoc.getX();
        double dz = mobLoc.getZ() - botLoc.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        
        // Face the mob
        if (horizDist > 1e-4) {
            double nx = dx / horizDist;
            double nz = dz / horizDist;
            
            float yaw = (float)(-Math.toDegrees(Math.atan2(dx, dz)));
            double dy = mobLoc.getY() - botLoc.getY();
            float pitch = (float)(-Math.toDegrees(Math.atan2(dy + 1.0 - 1.62, horizDist)));
            
            if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
                bot.setRotation(yaw, pitch);
                NmsPlayerSpawner.setHeadYaw(bot, yaw);
                
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getUniqueId().equals(fp.getUuid()))
                        PacketHelper.sendRotation(p, fp, yaw, pitch, yaw);
                }
            }
            
            // Move toward mob if far, attack if close
            if (horizDist > ATTACK_RANGE) {
                // Chase the mob
                Vector vel = bot.getVelocity();
                vel.setX(nx * SPRINT_SPEED);
                vel.setZ(nz * SPRINT_SPEED);
                
                // Jump over obstacles
                if (bot.isOnGround() && shouldJumpObstacle(bot, nx, nz)) {
                    vel.setY(0.42);
                }
                
                applyPhysicsRestrictions(bot, vel);
                safeSetVelocity(bot, vel);
                bot.setSprinting(true);
            } else {
                // Close enough - attack the mob
                if (s.weaponCooldown == 0 && hasLineOfSight(bot, mobTarget)) {
                    bot.swingMainHand();
                    
                    // Use weapon damage
                    double dmg = getWeaponDamage(s.currentWeapon);
                    
                    // Attack the mob using NMS
                    mobTarget.damage(dmg, bot);
                    
                    // Apply weapon cooldown
                    applyWeaponCooldown(s, rng);
                    
                    // Brief back-step after hit
                    s.retreatTicks = 2;
                }
                
                // Back-step if in retreat phase
                if (s.retreatTicks > 0) {
                    Vector vel = bot.getVelocity();
                    vel.setX(-nx * RETREAT_SPEED);
                    vel.setZ(-nz * RETREAT_SPEED);
                    applyPhysicsRestrictions(bot, vel);
                    safeSetVelocity(bot, vel);
                    bot.setSprinting(false);
                }
            }
        }
    }

    // ── Target resolution ─────────────────────────────────────────────────────

    private Player resolveTarget(FakePlayer fp, Player bot, BotState s, List<Player> online) {
        // Priority 1: Specific target (set via command)
        if (s.specificTarget != null) {
            Player specific = Bukkit.getPlayer(s.specificTarget);
            if (specific != null && specific.isOnline() && !specific.isDead()) {
                // Hunt this player no matter the distance
                if (specific.getWorld().equals(bot.getWorld())) {
                    return specific;
                }
            }
            // Specific target not valid - clear it
            s.specificTarget = null;
        }

        // Priority 2: Last attacker (defensive mode retaliation)
        if (s.defensiveMode && s.lastAttacker != null && s.attackerTimeout > 0) {
            Player attacker = Bukkit.getPlayer(s.lastAttacker);
            if (isValidTarget(attacker, bot, s)) {
                return attacker;
            }
        }

        // Priority 3: Current target (if still valid)
        if (s.targetId != null) {
            Player existing = Bukkit.getPlayer(s.targetId);
            if (isValidTarget(existing, bot, s)) return existing;
        }

        // Priority 4: Find new target
        // In defensive mode: no new targets unless attacked
        if (s.defensiveMode) {
            return null; // Don't aggro on new players
        }

        // Aggressive mode: find nearest player
        Player nearest = null;
        double bestSq  = Config.pvpAiDetectRange() * Config.pvpAiDetectRange();
        for (Player p : online) {
            if (!isValidTarget(p, bot, s)) continue;
            double dSq = bot.getLocation().distanceSquared(p.getLocation());
            if (dSq < bestSq) { bestSq = dSq; nearest = p; }
        }
        return nearest;
    }

    private boolean isValidTarget(Player p, Player bot, BotState s) {
        if (p == null || !p.isOnline() || p.isDead()) return false;
        if (!p.getWorld().equals(bot.getWorld())) return false;
        if (manager.getByUuid(p.getUniqueId()) != null) return false; // never target other bots
        // Only target SURVIVAL and ADVENTURE mode players — ignore CREATIVE and SPECTATOR
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return false;

        // For specific target, no range limit
        if (s.specificTarget != null && p.getUniqueId().equals(s.specificTarget)) {
            return true;
        }

        // For other targets, check range
        double detectRange = Config.pvpAiDetectRange();
        return bot.getLocation().distanceSquared(p.getLocation()) <= detectRange * detectRange;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────


    // ── Dynamic armour management ─────────────────────────────────────────────

    /**
     * Scans the bot's inventory for armour pieces better than what it is currently
     * wearing and swaps them in automatically.  Called every {@link #WEAPON_CHECK_INTERVAL}
     * ticks alongside {@link #updateWeapon}.
     *
     * <p>Priority order (highest armour-value wins):
     * netherite &gt; diamond &gt; iron &gt; chainmail &gt; gold &gt; leather.
     */
    private static void updateArmor(Player bot) {
        PlayerInventory inv = bot.getInventory();
        upgradeArmorSlot(inv, "HELMET");
        upgradeArmorSlot(inv, "CHESTPLATE");
        upgradeArmorSlot(inv, "LEGGINGS");
        upgradeArmorSlot(inv, "BOOTS");
    }

    /** Checks one armour slot and equips the best available piece from inventory. */
    private static void upgradeArmorSlot(PlayerInventory inv, String suffix) {
        ItemStack current = switch (suffix) {
            case "HELMET"     -> inv.getHelmet();
            case "CHESTPLATE" -> inv.getChestplate();
            case "LEGGINGS"   -> inv.getLeggings();
            case "BOOTS"      -> inv.getBoots();
            default           -> null;
        };
        double currentVal = getArmorValue(current);

        ItemStack best    = current;
        double    bestVal = currentVal;
        int       bestSlot = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!item.getType().name().endsWith("_" + suffix)) continue;
            double val = getArmorValue(item);
            if (val > bestVal) { bestVal = val; best = item; bestSlot = i; }
        }

        if (bestSlot != -1 && best != null) {
            // Move old armour back to the slot the new piece came from
            inv.setItem(bestSlot, (current != null && current.getType() != Material.AIR)
                    ? current.clone() : null);
            switch (suffix) {
                case "HELMET"     -> inv.setHelmet(best.clone());
                case "CHESTPLATE" -> inv.setChestplate(best.clone());
                case "LEGGINGS"   -> inv.setLeggings(best.clone());
                case "BOOTS"      -> inv.setBoots(best.clone());
            }
        }
    }

    /** Numeric armour quality score used for dynamic equip comparisons. */
    private static double getArmorValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;
        String n = item.getType().name();
        if (n.startsWith("NETHERITE")) return 100.0;
        if (n.startsWith("DIAMOND"))   return  80.0;
        if (n.startsWith("IRON"))      return  60.0;
        if (n.startsWith("CHAINMAIL")) return  50.0;
        if (n.startsWith("GOLDEN"))    return  40.0;
        if (n.startsWith("LEATHER"))   return  30.0;
        return 0.0;
    }

    // ── Combat mode detection (datapack: tick.mcfunction checks tag) ─────────

    /**
     * Detects the bot's current combat mode based on inventory contents.
     * Priority: CRYSTAL (has crystals + obsidian) → SWORD (has weapon) → FIST.
     */
    private static BotCombatMode detectCombatMode(Player bot) {
        PlayerInventory inv = bot.getInventory();

        boolean hasCrystals = false;
        boolean hasObsidian = false;

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            if (item.getType() == Material.END_CRYSTAL) hasCrystals = true;
            if (item.getType() == Material.OBSIDIAN) hasObsidian = true;
        }

        if (hasCrystals && hasObsidian) return BotCombatMode.CRYSTAL;

        ItemStack mainHand = inv.getItemInMainHand();
        if (mainHand != null && WEAPON_DAMAGE.containsKey(mainHand.getType())) {
            return BotCombatMode.SWORD;
        }

        return BotCombatMode.FIST;
    }

    // ── Crystal PVP system (datapack: crystal.mcfunction, d1.mcfunction) ──────

    /**
     * Full crystal PVP AI loop (datapack: d1.mcfunction, crystal.mcfunction).
     */
    private static void handleCrystalPvp(FakePlayer fp, Player bot, BotState s,
                                          Player target, ThreadLocalRandom rng) {
        Location botLoc = bot.getLocation();
        Location tgtLoc = target.getLocation();
        double dx = tgtLoc.getX() - botLoc.getX();
        double dz = tgtLoc.getZ() - botLoc.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        faceTarget(fp, bot, s, target, dx, dz, horizDist, tgtLoc.getY() - botLoc.getY(), 0f);

        bot.setSprinting(true);
        Vector vel = bot.getVelocity();
        if (horizDist > 1e-4) {
            double nx = dx / horizDist, nz = dz / horizDist;
            vel.setX(nx * SPRINT_SPEED);
            vel.setZ(nz * SPRINT_SPEED);
        }
        applyPhysicsRestrictions(bot, vel);
        safeSetVelocity(bot, vel);

        if (s.crystalCooldown == 0) tryPlaceAndHitCrystal(bot, s, target);

        if (s.tempStrafe && s.strafeTicks > 0 && horizDist > 1e-4) {
            Vector sv = bot.getVelocity();
            double perpX = -dz / horizDist, perpZ = dx / horizDist;
            sv.setX(sv.getX() + perpX * STRAFE_SPEED * s.strafeDir);
            sv.setZ(sv.getZ() + perpZ * STRAFE_SPEED * s.strafeDir);
            applyPhysicsRestrictions(bot, sv);
            safeSetVelocity(bot, sv);
        }

        switchToHotbarSlot(bot, 1);
    }

    private static void tryPlaceAndHitCrystal(Player bot, BotState s, Player target) {
        Location tgtLoc = target.getLocation(), botLoc = bot.getLocation();
        double dist = botLoc.distance(tgtLoc);
        if (dist < CRYSTAL_PLACE_MIN_DIST || dist > CRYSTAL_PLACE_MAX_DIST) return;

        Block targetBlock = tgtLoc.getBlock().getRelative(BlockFace.DOWN);
        if (targetBlock.getType() == Material.AIR || targetBlock.isPassable()) {
            ItemStack obsidian = findItem(bot, Material.OBSIDIAN);
            if (obsidian != null) targetBlock.setType(Material.OBSIDIAN);
        }

        Block crystalBlock = targetBlock.getRelative(BlockFace.UP);
        if (targetBlock.getType() == Material.OBSIDIAN && crystalBlock.getType() == Material.AIR) {
            ItemStack crystal = findItem(bot, Material.END_CRYSTAL);
            if (crystal != null) {
                EnderCrystal ec = (EnderCrystal) crystalBlock.getWorld().spawnEntity(
                    crystalBlock.getLocation().add(0.5, 0, 0.5), EntityType.END_CRYSTAL);
                ec.setShowingBottom(false);

                Bukkit.getScheduler().runTaskLater(
                    bot.getServer().getPluginManager().getPlugin("FakePlayerPlugin"), () -> {
                        if (ec.isValid()) {
                            ec.remove();
                            for (Entity e : ec.getNearbyEntities(6, 6, 6)) {
                                if (e instanceof LivingEntity le && !e.equals(bot)) le.damage(6.0, bot);
                            }
                        }
                    }, 1L);

                s.crystalCooldown = CRYSTAL_COOLDOWN;
                crystal.setAmount(crystal.getAmount() - 1);
            }
        }
    }

    // ── Ender pearl (datapack: pearl.mcfunction) ──────────────────────────────

    private static boolean hasEnderPearl(Player bot) {
        return findItem(bot, Material.ENDER_PEARL) != null;
    }

    private static void throwEnderPearl(Player bot, BotState s, Player target, double dist) {
        ItemStack pearl = findItem(bot, Material.ENDER_PEARL);
        if (pearl == null) return;

        PlayerInventory inv = bot.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        inv.setItemInMainHand(pearl.clone());

        Location tgtLoc = target.getLocation();
        double targetY = dist >= 7.0 ? 62.6 : 61.0;
        Vector dir = new Location(tgtLoc.getWorld(), tgtLoc.getX(), targetY, tgtLoc.getZ())
            .toVector().subtract(bot.getEyeLocation().toVector()).normalize();

        EnderPearl thrown = bot.launchProjectile(EnderPearl.class);
        thrown.setVelocity(dir.multiply(1.5));

        pearl.setAmount(pearl.getAmount() - 1);
        if (pearl.getAmount() <= 0) inv.remove(pearl);
        inv.setItemInMainHand(mainHand);

        s.pearlCooldown = PEARL_COOLDOWN;
    }

    // ── Totem refill (datapack: pop.mcfunction) ───────────────────────────────

    private static void handleTotemRefill(Player bot, BotState s) {
        bot.setSprinting(false);
        Vector vel = bot.getVelocity();
        vel.setX(0); vel.setZ(0);
        safeSetVelocity(bot, vel);

        PlayerInventory inv = bot.getInventory();
        if (s.refillTimer == 18) switchToHotbarSlot(bot, 9);

        if (s.refillTimer == 10) {
            ItemStack totem = findItem(bot, Material.TOTEM_OF_UNDYING);
            if (totem != null) {
                inv.setItemInOffHand(totem.clone());
                totem.setAmount(totem.getAmount() - 1);
            }
        }

        if (s.refillTimer == 5) {
            ItemStack totem = findItem(bot, Material.TOTEM_OF_UNDYING);
            if (totem != null) {
                inv.setItem(8, totem.clone());
                totem.setAmount(totem.getAmount() - 1);
            }
        }

        if (s.refillTimer == 0) {
            s.isRefilling = false;
            s.totemPopCount++;
        }
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private static ItemStack findItem(Player bot, Material mat) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == mat && item.getAmount() > 0) return item;
        }
        return null;
    }

    private static void switchToHotbarSlot(Player bot, int slot) {
        if (slot >= 0 && slot <= 8) bot.getInventory().setHeldItemSlot(slot);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void stopBot(UUID botUuid) {
        BotState s = states.remove(botUuid);
        if (s != null) {
            Player body = Bukkit.getPlayer(botUuid);
            if (body != null) body.setSprinting(false);
        }
    }

    public void cancelAll() {
        states.clear();
    }
}

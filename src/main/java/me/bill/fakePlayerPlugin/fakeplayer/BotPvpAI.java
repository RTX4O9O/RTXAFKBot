package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import me.bill.fakePlayerPlugin.util.AttributeCompat;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

public final class BotPvpAI {

  public enum Difficulty {
    EASY(1.5, true, 0.20),
    MEDIUM(2.0, false, 0.45),
    HARD(2.5, false, 0.65),
    TIER1(3.0, false, 0.80),
    HACKER(4.6, false, 1.00);

    public final double attackRange;

    public final boolean waitFullCooldown;

    public final double flipChance;

    Difficulty(double attackRange, boolean waitFullCooldown, double flipChance) {
      this.attackRange = attackRange;
      this.waitFullCooldown = waitFullCooldown;
      this.flipChance = flipChance;
    }

    public static Difficulty fromConfig() {
      return switch (Config.pvpAiDifficulty()) {
        case "easy" -> EASY;
        case "hard" -> HARD;
        case "tier1" -> TIER1;
        case "hacker" -> HACKER;
        default -> MEDIUM;
      };
    }
  }

  private static final double SPRINT_SPEED = 0.28;
  private static final double COMBAT_SPEED = 0.20;
  private static final double STRAFE_SPEED = 0.13;
  private static final double RETREAT_SPEED = 0.18;
  private static final double DRAG_SPEED = 0.10;

  private static final double COMBAT_RANGE = 6.0;
  private static final double ATTACK_RANGE = 3.0;
  private static final double JUMP_RANGE = 4.5;

  private static final double PREFERRED_DISTANCE = 2.0;

  private static final double MIN_DISTANCE = 1.2;

  private static final double MAX_DISTANCE = 3.2;

  private static final int RANDOMIZE_CD_MIN = 9;

  private static final int RANDOMIZE_CD_MAX = 14;

  private static final double CRYSTAL_PLACE_MIN_DIST = 2.5;

  private static final double CRYSTAL_PLACE_MAX_DIST = 5.0;

  private static final int CRYSTAL_COOLDOWN = 22;

  private static final int TOTEM_REFILL_DELAY = 5;

  private static final double PEARL_THROW_DISTANCE = 7.0;

  private static final int PEARL_COOLDOWN = 40;

  private static final double ATTACK_DAMAGE = 1.0;
  private static final int COOLDOWN_MIN = 8;
  private static final int COOLDOWN_MAX = 14;

  private static final double DETECT_RANGE = 32.0;

  private static final double MOB_DETECT_RANGE = 16.0;

  private static final double FOLLOWTHROUGH_CHANCE = 0.30;

  private static final double FLEE_BOOST = 1.15;

  private static final double WATER_MOVEMENT_FACTOR = 0.50;

  private static final double AIR_CONTROL_LIMIT = 0.05;

  private static final double DELAY_CHANCE = 0.15;

  private static final int DELAY_MIN = 2;
  private static final int DELAY_MAX = 6;

  private static final double MISS_CHANCE = 0.08;

  private static final double MOVEMENT_MISTAKE_CHANCE = 0.05;

  private static final int MISTAKE_DURATION = 4;

  private static final int STRAFE_DURATION_MIN = 20;
  private static final int STRAFE_DURATION_MAX = 45;

  private static final int STRAFE_PAUSE_MAX = 8;

  private static final double JUMP_FORWARD_BACK_CHANCE = 0.80;

  private static final int WEAPON_CHECK_INTERVAL = 40;

  private static final Map<Material, Double> WEAPON_DAMAGE =
      Map.ofEntries(
          Map.entry(Material.NETHERITE_SWORD, 8.0),
          Map.entry(Material.DIAMOND_SWORD, 7.0),
          Map.entry(Material.IRON_SWORD, 6.0),
          Map.entry(Material.GOLDEN_SWORD, 4.0),
          Map.entry(Material.STONE_SWORD, 5.0),
          Map.entry(Material.WOODEN_SWORD, 4.0),
          Map.entry(Material.NETHERITE_AXE, 10.0),
          Map.entry(Material.DIAMOND_AXE, 9.0),
          Map.entry(Material.IRON_AXE, 9.0),
          Map.entry(Material.GOLDEN_AXE, 7.0),
          Map.entry(Material.STONE_AXE, 9.0),
          Map.entry(Material.WOODEN_AXE, 7.0),
          Map.entry(Material.TRIDENT, 9.0));

  private static final Map<Material, Integer> WEAPON_COOLDOWN =
      Map.ofEntries(
          Map.entry(Material.NETHERITE_SWORD, 12),
          Map.entry(Material.DIAMOND_SWORD, 12),
          Map.entry(Material.IRON_SWORD, 12),
          Map.entry(Material.GOLDEN_SWORD, 12),
          Map.entry(Material.STONE_SWORD, 12),
          Map.entry(Material.WOODEN_SWORD, 12),
          Map.entry(Material.NETHERITE_AXE, 20),
          Map.entry(Material.DIAMOND_AXE, 20),
          Map.entry(Material.IRON_AXE, 22),
          Map.entry(Material.GOLDEN_AXE, 20),
          Map.entry(Material.STONE_AXE, 25),
          Map.entry(Material.WOODEN_AXE, 25),
          Map.entry(Material.TRIDENT, 22));

  private static final int TOTEM_SWAP_DELAY_MIN = 4;
  private static final int TOTEM_SWAP_DELAY_MAX = 12;

  private static final double FLEE_HEALTH_THRESHOLD = 6.0;

  private static final double EAT_HEALTH_THRESHOLD = 10.0;

  private static final int EAT_HUNGER_THRESHOLD = 8;

  private static final int EAT_COOLDOWN = 20;

  private static final int POTION_COOLDOWN = 30;

  private static final int POTION_SWITCH_DELAY_MIN = 3;

  private static final int POTION_SWITCH_DELAY_MAX = 9;

  private static final int POTION_AIM_TICKS = 3;

  private static final float POTION_AIM_DOWN_PITCH = 50f;

  private static final double POTION_RETREAT_SPEED = 0.09;

  private static final double SPRINT_RESET_CHANCE = 0.55;

  private static final int SPRINT_RESET_TICKS = 1;

  private static final double VEL_JITTER_MAG = 0.011;

  private enum PotionPhase {
    IDLE,
    SWITCH_DELAY,
    AIM_TURN,
    THROW
  }

  private static final class BotState {
    UUID targetId = null;
    UUID mobTargetId = null;
    UUID specificTarget = null;
    boolean defensiveMode = true;

    UUID lastAttacker = null;
    int attackerTimeout = 0;

    int attackCooldown = 0;
    int comboCount = 0;

    int retreatTicks = 0;
    boolean followThrough = false;

    int kbFreezeTicks = 0;

    int strafeDir = 1;
    int strafeTicks = 0;
    int strafePause = 0;

    int wtapPhase = 0;
    int wtapTicks = 6;

    boolean jumpQueued = false;
    boolean inAirAfterJump = false;

    float jukeOffset = 0f;
    int jukeTicks = 0;

    float arcOffset = 0f;
    boolean arcActive = false;
    int arcTicks = 0;

    int reactionDelay = 0;
    int mistakeTicks = 0;
    double mistakeAngle = 0.0;

    int weaponCheckTimer = 0;
    Material currentWeapon = null;
    int weaponCooldown = 0;

    int totemSwapDelay = 0;
    boolean totemUsed = false;

    boolean isFleeing = false;
    int eatCooldown = 0;
    int potionCooldown = 0;
    boolean isEating = false;
    int eatingTicks = 0;
    int eatingSlot = -1;
    Material pendingFoodType = null;

    PotionPhase potionPhase = PotionPhase.IDLE;
    int potionPhaseTimer = 0;
    ItemStack queuedPotion = null;
    int queuedPotionSlot = -1;

    boolean pendingSprintReset = false;
    int sprintResetTicks = 0;

    int backpedalBoostTicks = 0;

    float smoothYaw = Float.NaN;
    float smoothPitch = 0f;

    double orbitAngle = 0.0;
    boolean orbitInit = false;

    double aggression = 1.0;

    int fleeTick = 0;

    boolean tempCrit = true;
    boolean tempStap = true;
    boolean tempShield = false;
    boolean tempStrafe = true;
    boolean tempJreset = true;
    int randomizeTimer = 1;

    BotCombatMode combatMode = BotCombatMode.FIST;
    int crystalCooldown = 0;
    int pearlCooldown = 0;
    boolean isRefilling = false;
    int refillTimer = 0;
    int totemPopCount = 0;
  }

  private final FakePlayerManager manager;
  private final Map<UUID, BotState> states = new ConcurrentHashMap<>();

  public BotPvpAI(FakePlayerManager manager) {
    this.manager = manager;
  }

  public void tickBot(FakePlayer fp, Player bot, List<Player> online) {
    BotState s =
        states.computeIfAbsent(
            fp.getUuid(),
            k -> {
              BotState ns = new BotState();
              ns.defensiveMode = Config.pvpAiDefensiveMode();
              return ns;
            });
    ThreadLocalRandom rng = ThreadLocalRandom.current();

    if (s.attackCooldown > 0) s.attackCooldown--;
    if (s.retreatTicks > 0) s.retreatTicks--;
    if (s.kbFreezeTicks > 0) s.kbFreezeTicks--;
    if (s.strafeTicks > 0) s.strafeTicks--;
    if (s.strafePause > 0) s.strafePause--;
    if (s.jukeTicks > 0) s.jukeTicks--;
    if (s.wtapTicks > 0) s.wtapTicks--;
    if (s.arcTicks > 0) s.arcTicks--;
    if (s.arcTicks == 0) s.arcActive = false;
    if (s.inAirAfterJump && isGrounded(bot)) s.inAirAfterJump = false;

    if (s.reactionDelay > 0) s.reactionDelay--;
    if (s.mistakeTicks > 0) s.mistakeTicks--;

    if (s.weaponCheckTimer > 0) s.weaponCheckTimer--;
    if (s.weaponCooldown > 0) s.weaponCooldown--;
    if (s.totemSwapDelay > 0) s.totemSwapDelay--;
    if (s.crystalCooldown > 0) s.crystalCooldown--;
    if (s.pearlCooldown > 0) s.pearlCooldown--;
    if (s.refillTimer > 0) s.refillTimer--;

    if (s.potionPhaseTimer > 0) s.potionPhaseTimer--;
    if (s.sprintResetTicks > 0) s.sprintResetTicks--;
    if (s.backpedalBoostTicks > 0) s.backpedalBoostTicks--;

    if (s.attackerTimeout > 0) s.attackerTimeout--;
    if (s.eatCooldown > 0) s.eatCooldown--;
    if (s.potionCooldown > 0) s.potionCooldown--;

    if (s.randomizeTimer > 0) s.randomizeTimer--;
    if (s.randomizeTimer == 0) randomize(s, rng);
    if (s.eatingTicks > 0) {
      s.eatingTicks--;

      if (s.eatingTicks % 4 == 0) {
        for (Player p : Bukkit.getOnlinePlayers()) {
          if (!p.getUniqueId().equals(fp.getUuid())) PacketHelper.sendEatAnimation(p, fp);
        }
      }

      if (s.eatingTicks == 0) {
        s.isEating = false;

        // Consume the food item after eating completes
        ItemStack foodInHand = bot.getInventory().getItemInMainHand();
        if (foodInHand != null && foodInHand.getType().isEdible()) {
          foodInHand.setAmount(foodInHand.getAmount() - 1);
          if (foodInHand.getAmount() <= 0) {
            // Food fully consumed - restore original main hand item
            if (s.eatingSlot >= 0 && s.eatingSlot < 36) {
              ItemStack originalItem = bot.getInventory().getItem(s.eatingSlot);
              bot.getInventory().setItemInMainHand(originalItem);
              bot.getInventory().setItem(s.eatingSlot, null);
            } else {
              bot.getInventory().setItemInMainHand(null);
            }
          }
        }
        s.eatingSlot = -1;

        if (s.pendingFoodType != null) {
          applyFoodBenefits(bot, s.pendingFoodType);
          s.pendingFoodType = null;
        }
      }
    }

    if (s.weaponCheckTimer <= 0) {
      updateWeapon(bot, s);
      updateArmor(bot);
      s.weaponCheckTimer = WEAPON_CHECK_INTERVAL;
    }

    s.combatMode = detectCombatMode(bot);

    if (s.refillTimer > 0) {
      handleTotemRefill(bot, s);
      return;
    }

    manageTotem(bot, s, rng);

    handleSurvival(bot, s, rng);

    if (s.isEating && !s.isFleeing) {
      bot.setSprinting(false);

      Vector eatVel = bot.getVelocity();
      eatVel.setX(0);
      eatVel.setZ(0);
      safeSetVelocity(bot, eatVel);
      return;
    }

    LivingEntity mobTarget = checkForHostileMobs(bot, s);

    if (mobTarget != null) {
      combatMob(fp, bot, s, rng, mobTarget);
      return;
    }

    s.mobTargetId = null;

    Player target = resolveTarget(fp, bot, s, online);
    if (target == null) {
      s.targetId = null;
      s.comboCount = 0;
      bot.setSprinting(false);
      return;
    }

    if (!target.getUniqueId().equals(s.targetId)) {
      s.comboCount = 0;
      s.arcActive = false;
      s.orbitInit = false;
    }
    s.targetId = target.getUniqueId();

    if (Float.isNaN(s.smoothYaw)) {
      Location bl = bot.getLocation(), tl = target.getLocation();
      double sdx = tl.getX() - bl.getX(), sdz = tl.getZ() - bl.getZ();
      double sdist = Math.sqrt(sdx * sdx + sdz * sdz);
      if (sdist > 1e-4) {
        s.smoothYaw = (float) (-Math.toDegrees(Math.atan2(sdx, sdz)));
        s.smoothPitch =
            (float) (-Math.toDegrees(Math.atan2(tl.getY() - bl.getY() + 1.0 - 1.62, sdist)));
      }
    }

    if (s.potionPhase != PotionPhase.IDLE) {
      processPotionPhase(fp, bot, s, target);
      return;
    }

    if (s.combatMode == BotCombatMode.CRYSTAL) {
      handleCrystalPvp(fp, bot, s, target, rng);
      return;
    }

    Location botLoc = bot.getLocation();
    Location tgtLoc = target.getLocation();
    double rawDx = tgtLoc.getX() - botLoc.getX();
    double rawDz = tgtLoc.getZ() - botLoc.getZ();
    double horizDist = Math.sqrt(rawDx * rawDx + rawDz * rawDz);

    if (horizDist > PEARL_THROW_DISTANCE && s.pearlCooldown == 0 && hasEnderPearl(bot)) {
      throwEnderPearl(bot, s, target, horizDist);
    }

    if (s.reactionDelay == 0 && rng.nextDouble() < DELAY_CHANCE) {
      s.reactionDelay = rng.nextInt(DELAY_MIN, DELAY_MAX + 1);
    }

    if (s.reactionDelay > 0) {
      faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist, tgtLoc.getY() - botLoc.getY(), 0f);
      return;
    }

    if (s.mistakeTicks == 0 && rng.nextDouble() < MOVEMENT_MISTAKE_CHANCE) {
      s.mistakeTicks = MISTAKE_DURATION;
      s.mistakeAngle = rng.nextDouble(-90, 91);
    }

    double targetHpRatio = target.getHealth() / maxHp(target);
    double selfHpRatio = bot.getHealth() / maxHp(bot);
    s.aggression = 1.0 + (0.4 * (1.0 - targetHpRatio)) - (0.35 * (1.0 - selfHpRatio));
    s.aggression = Math.max(0.55, Math.min(1.45, s.aggression));

    Vector vel = bot.getVelocity();
    double velMag = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());

    if (velMag > 0.35 && s.kbFreezeTicks == 0) {
      s.kbFreezeTicks = 4;
    }

    if (s.kbFreezeTicks > 0) {
      faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist, tgtLoc.getY() - botLoc.getY(), 0f);

      if (horizDist <= Difficulty.fromConfig().attackRange && s.attackCooldown == 0) {
        performAttack(s, rng, bot, target);
      }
      return;
    }

    if (horizDist < 1e-4) {
      if (s.attackCooldown == 0) {
        bot.swingMainHand();
        NmsPlayerSpawner.performAttack(bot, target, ATTACK_DAMAGE);
        s.attackCooldown = rng.nextInt(COOLDOWN_MIN, COOLDOWN_MAX + 1);
        s.retreatTicks = rng.nextInt(3, 6);
      }
      return;
    }

    if (!s.arcActive && horizDist > COMBAT_RANGE * 0.9 && horizDist < COMBAT_RANGE * 1.1) {
      s.arcOffset = (float) (rng.nextInt(-30, 31));
      s.arcTicks = rng.nextInt(15, 30);
      s.arcActive = true;
    }

    if (s.isFleeing) {
      doFlee(fp, bot, s, rawDx, rawDz, horizDist);
      return;
    }

    if (s.retreatTicks > 0 && !s.followThrough) {
      doRetreat(bot, s, rawDx, rawDz, horizDist);
    } else if (horizDist > COMBAT_RANGE) {
      doChase(bot, s, rng, rawDx, rawDz, horizDist, target);
    } else {
      doCombat(bot, s, rng, rawDx, rawDz, horizDist, botLoc, target);
    }

    float juke =
        (horizDist <= COMBAT_RANGE) ? (s.jukeOffset + (s.arcActive ? s.arcOffset * 0.4f : 0f)) : 0f;
    faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist, tgtLoc.getY() - botLoc.getY(), juke);

    if (s.pendingSprintReset) {
      if (s.sprintResetTicks == 0) {
        s.pendingSprintReset = false;
      } else {

        faceTarget(fp, bot, s, target, rawDx, rawDz, horizDist, tgtLoc.getY() - botLoc.getY(), 0f);
        return;
      }
    }

    double effectiveAttackRange = Difficulty.fromConfig().attackRange;
    if (horizDist <= effectiveAttackRange && s.attackCooldown == 0) {

      if (hasLineOfSight(bot, target)) {

        if (s.tempCrit
            && !s.pendingSprintReset
            && bot.isSprinting()
            && rng.nextDouble() < SPRINT_RESET_CHANCE) {
          bot.setSprinting(false);
          s.pendingSprintReset = true;
          s.sprintResetTicks = SPRINT_RESET_TICKS;

          faceTarget(
              fp, bot, s, target, rawDx, rawDz, horizDist, tgtLoc.getY() - botLoc.getY(), 0f);
          return;
        }
        performAttack(s, rng, bot, target);
      }
    }
  }

  private static void performAttack(BotState s, ThreadLocalRandom rng, Player bot, Player target) {

    int earlyWindow = Difficulty.fromConfig().waitFullCooldown ? 0 : rng.nextInt(3);
    if (s.weaponCooldown > earlyWindow) {
      return;
    }

    double chargeRatio =
        (s.weaponCooldown == 0) ? 1.0 : Math.max(0.82, 1.0 - s.weaponCooldown * 0.09);

    bot.swingMainHand();

    if (rng.nextDouble() < MISS_CHANCE) {

      applyWeaponCooldown(s, rng);
      s.comboCount = 0;

      s.retreatTicks = rng.nextInt(2, 4);
      s.followThrough = false;
      return;
    }

    boolean onGround = isGrounded(bot);
    boolean inFluid = bot.isInWater() || bot.isInLava();
    boolean falling = bot.getFallDistance() > 0.20f;
    boolean isCritical = s.tempCrit && !onGround && !inFluid && (s.inAirAfterJump || falling);

    double dmg = getWeaponDamage(s.currentWeapon);

    dmg *= chargeRatio;

    if (isCritical) {
      dmg *= 1.5;
    }

    if (s.comboCount > 0) {
      double comboBoost = (1.0 + Math.min(s.comboCount, 6) * 0.05) * s.aggression;
      dmg *= comboBoost;
    }

    if (isCritical) {
      target
          .getWorld()
          .spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 8, 0.3, 0.3, 0.3, 0.1);
    }

    NmsPlayerSpawner.performAttack(bot, target, dmg);

    s.inAirAfterJump = false;
    s.comboCount++;

    applyWeaponCooldown(s, rng);

    if (rng.nextDouble() < FOLLOWTHROUGH_CHANCE) {
      s.retreatTicks = 0;
      s.followThrough = true;
    } else {

      boolean slowWeapon =
          (s.currentWeapon != null && WEAPON_COOLDOWN.getOrDefault(s.currentWeapon, 12) >= 20);
      s.retreatTicks = slowWeapon ? rng.nextInt(2, 4) : rng.nextInt(3, 7);
      s.followThrough = false;
      s.jumpQueued = rng.nextBoolean();
      s.backpedalBoostTicks = 2;
    }
  }

  private static void doChase(
      Player bot,
      BotState s,
      ThreadLocalRandom rng,
      double dx,
      double dz,
      double horizDist,
      Player target) {
    if (horizDist < 1e-4) return;
    double nx = dx / horizDist;
    double nz = dz / horizDist;

    double[] mistake = applyMovementMistake(s, nx, nz);
    nx = mistake[0];
    nz = mistake[1];

    double speed = SPRINT_SPEED;
    Vector tv = target.getVelocity();
    double dot = tv.getX() * nx + tv.getZ() * nz;
    if (dot > 0.05) speed *= FLEE_BOOST;

    Vector vel = bot.getVelocity();
    vel.setX(nx * speed);
    vel.setZ(nz * speed);

    if (isGrounded(bot) && shouldJumpObstacle(bot, nx, nz)) {
      vel.setY(0.42);
    }

    applyPhysicsRestrictions(bot, vel);

    safeSetVelocity(bot, vel);
    bot.setSprinting(true);

    if (horizDist < COMBAT_RANGE * 1.5 && !s.jumpQueued) s.jumpQueued = rng.nextInt(10) < 6;
  }

  private static void doCombat(
      Player bot,
      BotState s,
      ThreadLocalRandom rng,
      double dx,
      double dz,
      double horizDist,
      Location botLoc,
      Player target) {
    if (horizDist < 1e-4) return;

    Location tgtLoc = target.getLocation();

    double nx = dx / horizDist;
    double nz = dz / horizDist;

    double[] mistake = applyMovementMistake(s, nx, nz);
    nx = mistake[0];
    nz = mistake[1];

    if (!s.orbitInit) {

      s.orbitAngle = Math.atan2(botLoc.getX() - tgtLoc.getX(), botLoc.getZ() - tgtLoc.getZ());
      s.orbitInit = true;
    }

    if (!s.tempStrafe) {
      s.strafeDir = 0;
    } else if (s.strafePause > 0) {
      s.strafeDir = 0;
    } else if (s.strafeTicks <= 0) {
      s.strafePause = rng.nextInt(STRAFE_PAUSE_MAX + 1);
      s.strafeDir = rng.nextBoolean() ? 1 : -1;
      s.strafeTicks = rng.nextInt(STRAFE_DURATION_MIN, STRAFE_DURATION_MAX + 1);
      s.jukeOffset = (float) rng.nextInt(-12, 13);
      s.jukeTicks = s.strafeTicks;
    }

    double orbitSpeed = 0.053 * s.aggression;
    s.orbitAngle += orbitSpeed * s.strafeDir;

    double orbitRadius = PREFERRED_DISTANCE * (1.0 + rng.nextDouble() * 0.25);
    double orbitTargetX = tgtLoc.getX() + Math.sin(s.orbitAngle) * orbitRadius;
    double orbitTargetZ = tgtLoc.getZ() + Math.cos(s.orbitAngle) * orbitRadius;

    double toOrbitX = orbitTargetX - botLoc.getX();
    double toOrbitZ = orbitTargetZ - botLoc.getZ();
    double orbitDist = Math.sqrt(toOrbitX * toOrbitX + toOrbitZ * toOrbitZ);

    double jitter = (rng.nextDouble() - 0.5) * VEL_JITTER_MAG;
    double sx = -nz * s.strafeDir + jitter;
    double sz = nx * s.strafeDir - jitter;

    if (s.arcActive && Math.abs(s.arcOffset) > 1f) {
      double rad = Math.toRadians(s.arcOffset);
      double cos = Math.cos(rad), sin = Math.sin(rad);
      double onx = nx * cos - nz * sin;
      double onz = nx * sin + nz * cos;
      nx = onx;
      nz = onz;
    }

    boolean tooClose = horizDist < MIN_DISTANCE;
    boolean tooFar = horizDist > MAX_DISTANCE;

    boolean doStap =
        s.tempStap
            && s.attackCooldown >= 5
            && s.attackCooldown <= 8
            && isGrounded(bot)
            && !tooClose;

    if (s.tempShield && s.comboCount >= 3) {
      raiseShieldIfAvailable(bot);
    }

    if (s.wtapTicks <= 0) {
      s.wtapPhase = (s.wtapPhase == 0) ? 1 : 0;
      s.wtapTicks = (s.wtapPhase == 0) ? rng.nextInt(4, 8) : rng.nextInt(2, 4);
    }

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

    if (doStap) {
      vel.setX(-nx * DRAG_SPEED);
      vel.setZ(-nz * DRAG_SPEED);
      bot.setSprinting(false);
      applyPhysicsRestrictions(bot, vel);
      safeSetVelocity(bot, vel);
      return;
    }

    if (tooClose) {

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

      if (s.wtapPhase == 0) {
        if (orbitDist > 0.3 && orbitDist > 1e-4) {
          double onx = toOrbitX / orbitDist;
          double onz = toOrbitZ / orbitDist;

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

    boolean shouldJump = false;
    Vector jumpDir = new Vector(nx, 0, nz);

    if (s.jumpQueued
        && horizDist <= JUMP_RANGE
        && horizDist > Difficulty.fromConfig().attackRange
        && isGrounded(bot)) {
      shouldJump = true;
      if (rng.nextDouble() > JUMP_FORWARD_BACK_CHANCE) {
        jumpDir.setX(sx * 0.8 + nx * 0.2);
        jumpDir.setZ(sz * 0.8 + nz * 0.2);
      }
      s.jumpQueued = false;
      s.inAirAfterJump = true;
    } else if (isGrounded(bot) && shouldJumpObstacle(bot, nx, nz)) {
      shouldJump = true;
    } else if (tooClose && isGrounded(bot) && rng.nextDouble() < 0.15) {
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

  private static void doRetreat(Player bot, BotState s, double dx, double dz, double horizDist) {
    if (horizDist < 1e-4) return;
    double nx = dx / horizDist;
    double nz = dz / horizDist;

    double[] mistake = applyMovementMistake(s, nx, nz);
    nx = mistake[0];
    nz = mistake[1];

    double speed = (s.backpedalBoostTicks > 0) ? RETREAT_SPEED * 1.35 : RETREAT_SPEED;

    Vector vel = bot.getVelocity();
    vel.setX(-nx * speed);
    vel.setZ(-nz * speed);

    applyPhysicsRestrictions(bot, vel);
    bot.setSprinting(false);
    safeSetVelocity(bot, vel);
  }

  private static void randomize(BotState s, ThreadLocalRandom rng) {
    double flip = Difficulty.fromConfig().flipChance;
    s.tempCrit = rng.nextDouble() < flip;
    s.tempStap = rng.nextDouble() < flip;
    s.tempJreset = rng.nextDouble() < flip;
    s.tempShield = rng.nextDouble() < 0.50;
    s.tempStrafe = rng.nextDouble() < 0.70;
    s.randomizeTimer = rng.nextInt(RANDOMIZE_CD_MIN, RANDOMIZE_CD_MAX + 1);
  }

  private static void raiseShieldIfAvailable(Player bot) {
    PlayerInventory inv = bot.getInventory();
    ItemStack offhand = inv.getItemInOffHand();

    if (offhand != null && offhand.getType() == Material.TOTEM_OF_UNDYING) return;
    if (offhand != null && offhand.getType() == Material.SHIELD) {
      NmsPlayerSpawner.startUsingMainHandItem(bot);
      return;
    }

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

  private static void doFlee(
      FakePlayer fp, Player bot, BotState s, double dx, double dz, double horizDist) {
    if (horizDist < 1e-4) return;
    double nx = dx / horizDist;
    double nz = dz / horizDist;

    double px = -nz;
    double pz = nx;

    s.fleeTick++;
    double strafeAmp = Math.max(0.0, 1.0 - horizDist / 12.0) * 0.5;
    double sineStrafe = Math.sin(s.fleeTick * 0.28) * strafeAmp;

    double fleeSpeed = SPRINT_SPEED * (1.1 + (1.0 - s.aggression) * 0.25);

    Vector vel = bot.getVelocity();
    vel.setX(-nx * fleeSpeed + px * sineStrafe);
    vel.setZ(-nz * fleeSpeed + pz * sineStrafe);

    if (isGrounded(bot) && shouldJumpObstacle(bot, -nx, -nz)) {
      vel.setY(0.42);
    }

    applyPhysicsRestrictions(bot, vel);
    safeSetVelocity(bot, vel);
    bot.setSprinting(true);

    float yaw = (float) (-Math.toDegrees(Math.atan2(-dx, -dz)));
    float pitch = -5f;

    if (Float.isFinite(yaw)) {

      if (!Float.isNaN(s.smoothYaw)) {
        s.smoothYaw = lerpAngle(s.smoothYaw, yaw, 0.65f);
        s.smoothPitch = s.smoothPitch + (pitch - s.smoothPitch) * 0.5f;
        yaw = s.smoothYaw;
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

  private static double[] applyMovementMistake(BotState s, double nx, double nz) {
    if (s.mistakeTicks <= 0) return new double[] {nx, nz};

    double rad = Math.toRadians(s.mistakeAngle);
    double cos = Math.cos(rad);
    double sin = Math.sin(rad);
    double newNx = nx * cos - nz * sin;
    double newNz = nx * sin + nz * cos;

    return new double[] {newNx, newNz};
  }

  private static void applyPhysicsRestrictions(Player bot, Vector vel) {
    boolean inWater = bot.isInWater() || bot.isInLava();
    boolean onGround = isGrounded(bot);

    if (inWater) {
      vel.setX(vel.getX() * WATER_MOVEMENT_FACTOR);
      vel.setZ(vel.getZ() * WATER_MOVEMENT_FACTOR);
      return;
    }

    if (!onGround) {
      Vector current = bot.getVelocity();
      double deltaX = vel.getX() - current.getX();
      double deltaZ = vel.getZ() - current.getZ();

      if (Math.abs(deltaX) > AIR_CONTROL_LIMIT) {
        deltaX = Math.signum(deltaX) * AIR_CONTROL_LIMIT;
      }
      if (Math.abs(deltaZ) > AIR_CONTROL_LIMIT) {
        deltaZ = Math.signum(deltaZ) * AIR_CONTROL_LIMIT;
      }

      vel.setX(current.getX() + deltaX);
      vel.setZ(current.getZ() + deltaZ);
    }
  }

  private static boolean shouldJumpObstacle(Player bot, double nx, double nz) {
    Location loc = bot.getLocation();

    double checkX1 = loc.getX() + nx * 0.6;
    double checkZ1 = loc.getZ() + nz * 0.6;

    double checkX2 = loc.getX() + nx * 1.2;
    double checkZ2 = loc.getZ() + nz * 1.2;

    Location checkLoc1 = new Location(loc.getWorld(), checkX1, loc.getY(), checkZ1);
    Location checkLoc2 = new Location(loc.getWorld(), checkX2, loc.getY(), checkZ2);

    Block immediate = checkLoc1.getBlock();
    Block extended = checkLoc2.getBlock();

    if (immediate.getType().isSolid() && !immediate.isPassable()) {

      Block above = immediate.getRelative(0, 1, 0);
      Block twoAbove = immediate.getRelative(0, 2, 0);

      if ((!above.getType().isSolid() || above.isPassable())
          && (!twoAbove.getType().isSolid() || twoAbove.isPassable())) {
        return true;
      }
    }

    if (extended.getType().isSolid() && !extended.isPassable()) {
      Block extendedAbove = extended.getRelative(0, 1, 0);
      Block extendedTwoAbove = extended.getRelative(0, 2, 0);

      if ((!extendedAbove.getType().isSolid() || extendedAbove.isPassable())
          && (!extendedTwoAbove.getType().isSolid() || extendedTwoAbove.isPassable())) {

        if (!immediate.getType().isSolid() || immediate.isPassable()) {
          return true;
        }
      }
    }

    Block groundAhead = checkLoc1.getBlock().getRelative(0, -1, 0);
    if (!groundAhead.getType().isSolid() && !bot.isInWater()) {

      Block twoAhead = checkLoc2.getBlock().getRelative(0, -1, 0);
      if (twoAhead.getType().isSolid()) {

        return true;
      }
    }

    return false;
  }

  private static void safeSetVelocity(Player bot, Vector vel) {
    if (!Double.isFinite(vel.getX())
        || !Double.isFinite(vel.getY())
        || !Double.isFinite(vel.getZ())) {
      return;
    }
    bot.setVelocity(vel);
  }

  private static float wrapDeg(float deg) {
    deg %= 360f;
    if (deg > 180f) deg -= 360f;
    if (deg < -180f) deg += 360f;
    return deg;
  }

  private static float lerpAngle(float from, float to, float t) {
    return from + wrapDeg(to - from) * t;
  }

  private static void faceTarget(
      FakePlayer fp,
      Player bot,
      BotState s,
      Player target,
      double dx,
      double dz,
      double horizDist,
      double dy,
      float jukeOffset) {

    Vector tv = target.getVelocity();
    double pdx = dx + tv.getX();
    double pdz = dz + tv.getZ();
    double pdist = Math.sqrt(pdx * pdx + pdz * pdz);
    if (pdist > 1e-4 && Double.isFinite(pdist)) {
      dx = pdx;
      dz = pdz;
      horizDist = pdist;
    }

    if (horizDist < 1e-4) return;

    float targetYaw = (float) (-Math.toDegrees(Math.atan2(dx, dz))) + jukeOffset;
    float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy + 1.0 - 1.62, horizDist)));

    if (!Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)) return;

    float yaw, pitch;
    if (s != null && !Float.isNaN(s.smoothYaw)) {
      float angleDiff = Math.abs(wrapDeg(targetYaw - s.smoothYaw));

      float t = angleDiff > 60f ? 0.72f : (angleDiff > 20f ? 0.52f : 0.40f);
      s.smoothYaw = lerpAngle(s.smoothYaw, targetYaw, t);

      float pt = Math.min(1f, t * 1.35f);
      s.smoothPitch = s.smoothPitch + (targetPitch - s.smoothPitch) * pt;
      s.smoothPitch = Math.max(-90f, Math.min(90f, s.smoothPitch));
      yaw = s.smoothYaw;
      pitch = s.smoothPitch;
    } else {

      yaw = targetYaw;
      pitch = targetPitch;
      if (s != null) {
        s.smoothYaw = yaw;
        s.smoothPitch = pitch;
      }
    }

    bot.setRotation(yaw, pitch);
    NmsPlayerSpawner.setHeadYaw(bot, yaw);

    for (Player p : Bukkit.getOnlinePlayers()) {
      if (!p.getUniqueId().equals(fp.getUuid())) PacketHelper.sendRotation(p, fp, yaw, pitch, yaw);
    }
  }

  public void setSpecificTarget(UUID botUuid, UUID targetUuid) {
    BotState s = states.get(botUuid);
    if (s != null) {
      s.specificTarget = targetUuid;
      s.defensiveMode = (targetUuid == null);
    }
  }

  public void setDefensiveMode(UUID botUuid, boolean defensive) {
    BotState s = states.get(botUuid);
    if (s != null) {
      s.defensiveMode = defensive;
    }
  }

  public void onBotAttacked(UUID botUuid, UUID attackerUuid) {
    BotState s = states.get(botUuid);
    if (s != null) {
      s.lastAttacker = attackerUuid;
      s.attackerTimeout = 60;
    }
  }

  private static void handleSurvival(Player bot, BotState s, ThreadLocalRandom rng) {
    double health = bot.getHealth();
    int food = bot.getFoodLevel();

    if (s.isEating) return;

    if (health <= EAT_HEALTH_THRESHOLD
        && s.potionCooldown == 0
        && s.potionPhase == PotionPhase.IDLE) {
      if (queuePotionUse(bot, s, rng)) return;
    }

    if (health <= EAT_HEALTH_THRESHOLD
        && s.eatCooldown == 0
        && s.potionPhase == PotionPhase.IDLE
        && !hasHealingPotion(bot)) {
      if (tryEatHealingFood(bot, s)) return;
    }

    if (food <= EAT_HUNGER_THRESHOLD && s.eatCooldown == 0) {
      tryEatAnyFood(bot, s);
    }

    if (health <= FLEE_HEALTH_THRESHOLD) {

      if (!s.isFleeing) s.fleeTick = 0;
      s.isFleeing = true;
    }

    if (s.isFleeing && health > FLEE_HEALTH_THRESHOLD + 4.0) {
      s.isFleeing = false;
      s.orbitInit = false;
    }
  }

  private static boolean queuePotionUse(Player bot, BotState s, ThreadLocalRandom rng) {
    PlayerInventory inv = bot.getInventory();

    for (int pass = 0; pass < 2; pass++) {
      Material targetType = (pass == 0) ? Material.SPLASH_POTION : Material.POTION;

      for (int i = 0; i < 36; i++) {
        ItemStack item = inv.getItem(i);
        if (item == null || item.getType() != targetType) continue;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) continue;
        if (!isHealingPotionMeta(meta)) continue;

        s.queuedPotion = item.clone();
        s.queuedPotionSlot = i;

        ItemStack currentMain = inv.getItemInMainHand();
        inv.setItemInMainHand(item.clone());
        inv.setItem(
            i, (currentMain == null || currentMain.getType() == Material.AIR) ? null : currentMain);

        s.potionPhase = PotionPhase.SWITCH_DELAY;
        s.potionPhaseTimer = rng.nextInt(POTION_SWITCH_DELAY_MIN, POTION_SWITCH_DELAY_MAX + 1);

        return true;
      }
    }
    return false;
  }

  private static void processPotionPhase(FakePlayer fp, Player bot, BotState s, Player target) {

    switch (s.potionPhase) {
      case SWITCH_DELAY -> {
        bot.setSprinting(false);

        if (target != null) {
          Location botLoc = bot.getLocation();
          Location tgtLoc = target.getLocation();
          double dx = tgtLoc.getX() - botLoc.getX();
          double dz = tgtLoc.getZ() - botLoc.getZ();
          double dist = Math.sqrt(dx * dx + dz * dz);

          if (dist > 1e-4) {
            double nx = dx / dist;
            double nz = dz / dist;
            Vector vel = bot.getVelocity();
            vel.setX(-nx * POTION_RETREAT_SPEED);
            vel.setZ(-nz * POTION_RETREAT_SPEED);
            applyPhysicsRestrictions(bot, vel);
            safeSetVelocity(bot, vel);

            double dy = tgtLoc.getY() - botLoc.getY();
            faceTarget(fp, bot, s, target, dx, dz, dist, dy, 0f);
          }
        }

        if (s.potionPhaseTimer == 0) {
          s.potionPhase = PotionPhase.AIM_TURN;
          s.potionPhaseTimer = POTION_AIM_TICKS;
        }
      }

      case AIM_TURN -> {
        bot.setSprinting(false);

        float awayYaw;
        if (target != null) {
          Location botLoc = bot.getLocation();
          Location tgtLoc = target.getLocation();
          double dx = tgtLoc.getX() - botLoc.getX();
          double dz = tgtLoc.getZ() - botLoc.getZ();
          double dist = Math.sqrt(dx * dx + dz * dz);

          if (dist > 1e-4) {

            double nx = dx / dist, nz = dz / dist;
            Vector vel = bot.getVelocity();
            vel.setX(-nx * POTION_RETREAT_SPEED);
            vel.setZ(-nz * POTION_RETREAT_SPEED);
            applyPhysicsRestrictions(bot, vel);
            safeSetVelocity(bot, vel);
          }

          double rdx = tgtLoc.getX() - botLoc.getX();
          double rdz = tgtLoc.getZ() - botLoc.getZ();
          awayYaw = (float) (-Math.toDegrees(Math.atan2(-rdx, -rdz)));
        } else {
          awayYaw = bot.getLocation().getYaw();
        }

        float curPitch = bot.getLocation().getPitch();
        float lerpPitch = curPitch + (POTION_AIM_DOWN_PITCH - curPitch) * 0.6f;

        if (Float.isFinite(awayYaw) && Float.isFinite(lerpPitch)) {
          bot.setRotation(awayYaw, lerpPitch);
          NmsPlayerSpawner.setHeadYaw(bot, awayYaw);

          for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(fp.getUuid()))
              PacketHelper.sendRotation(p, fp, awayYaw, lerpPitch, awayYaw);
          }
        }

        if (s.potionPhaseTimer == 0) {
          s.potionPhase = PotionPhase.THROW;
          s.potionPhaseTimer = 1;
        }
      }

      case THROW -> {
        executePotionThrow(bot, s);
        s.potionPhase = PotionPhase.IDLE;
        s.potionPhaseTimer = 0;
      }

      default -> s.potionPhase = PotionPhase.IDLE;
    }
  }

  private static void executePotionThrow(Player bot, BotState s) {
    if (s.queuedPotion == null) return;

    ItemStack potion = s.queuedPotion;
    if (!(potion.getItemMeta() instanceof PotionMeta meta)) {
      s.queuedPotion = null;
      s.queuedPotionSlot = -1;
      return;
    }

    boolean isSplash = (potion.getType() == Material.SPLASH_POTION);

    if (isSplash) {

      Location throwLoc = bot.getLocation().add(0, 1.5, 0);
      ThrownPotion thrown = bot.getWorld().spawn(throwLoc, ThrownPotion.class);
      thrown.setItem(potion.clone());

      thrown.setVelocity(new Vector(0, -0.5, 0));
      thrown.setShooter(bot);
    }

    applyPotionEffects(bot, meta);

    ItemStack mainHand = bot.getInventory().getItemInMainHand();
    if (mainHand != null && !mainHand.getType().isAir()) {
      mainHand.setAmount(mainHand.getAmount() - 1);
      if (mainHand.getAmount() <= 0) bot.getInventory().setItemInMainHand(null);
    }

    s.potionCooldown = POTION_COOLDOWN;
    s.queuedPotion = null;
    s.queuedPotionSlot = -1;
  }

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

  private static boolean isHealingPotionMeta(PotionMeta meta) {

    PotionType base = meta.getBasePotionType();
    if (base != null) {
      String n = base.name();
      if (n.contains("HEALING")
          || n.contains("HEALTH")
          || n.contains("REGENERATION")
          || n.contains("REGEN")) {
        return true;
      }
    }

    for (PotionEffect effect : meta.getCustomEffects()) {
      PotionEffectType t = effect.getType();
      if (t.equals(PotionEffectType.INSTANT_HEALTH) || t.equals(PotionEffectType.REGENERATION)) {
        return true;
      }
    }
    return false;
  }

  private static void applyPotionEffects(Player bot, PotionMeta meta) {
    for (PotionEffect effect : meta.getCustomEffects()) {
      bot.addPotionEffect(effect);
    }
    PotionType base = meta.getBasePotionType();
    if (base != null) {
      String n = base.name();
      if (n.contains("HEALING") || n.contains("HEALTH")) {
        double heal = n.contains("STRONG") ? 8.0 : 4.0;
        bot.setHealth(Math.min(maxHp(bot), bot.getHealth() + heal));
      } else if (n.contains("REGENERATION") || n.contains("REGEN")) {
        int amp = n.contains("STRONG") ? 1 : 0;
        bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, amp));
      }
    }
  }

  private static boolean tryEatHealingFood(Player bot, BotState s) {
    return tryEatFood(bot, s, true);
  }

  private static boolean tryEatAnyFood(Player bot, BotState s) {
    return tryEatFood(bot, s, false);
  }

  private static boolean tryEatFood(Player bot, BotState s, boolean healingOnly) {
    PlayerInventory inv = bot.getInventory();

    Material[] healingPriority = {Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_APPLE};

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

    for (int i = 0; i < 36; i++) {
      ItemStack item = inv.getItem(i);
      if (item != null && item.getType().isEdible()) {
        startEating(bot, s, item, i);
        return true;
      }
    }

    return false;
  }

  private static void startEating(Player bot, BotState s, ItemStack food, int slot) {
    PlayerInventory inv = bot.getInventory();

    ItemStack currentMain = inv.getItemInMainHand();
    if (currentMain.getType() == Material.AIR) currentMain = null;
    inv.setItemInMainHand(food.clone());
    inv.setItem(slot, currentMain);

    NmsPlayerSpawner.startUsingMainHandItem(bot);

    s.isEating = true;
    s.eatingTicks = 36;
    s.eatCooldown = EAT_COOLDOWN;
    s.pendingFoodType = food.getType();  // Remember food type for benefits
    s.eatingSlot = slot;  // Remember where original main hand item is stored
    s.isFleeing = true;
  }

  private static void applyFoodBenefits(Player bot, Material type) {
    switch (type) {
      case ENCHANTED_GOLDEN_APPLE -> {
        bot.setHealth(Math.min(maxHp(bot), bot.getHealth() + 8.0));
        bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 4));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
      }
      case GOLDEN_APPLE -> {
        bot.setHealth(Math.min(maxHp(bot), bot.getHealth() + 4.0));
        bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 4));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
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
        if (type.isEdible()) {
          bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + 3));
        }
      }
    }
  }

  private static void updateWeapon(Player bot, BotState s) {
    PlayerInventory inv = bot.getInventory();
    ItemStack mainHand = inv.getItemInMainHand();

    ItemStack bestWeapon = null;
    double bestDamage = ATTACK_DAMAGE;
    int bestSlot = -1;

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

      if (bestWeapon != null) {
        int emptyHotbarSlot = findEmptyHotbarSlot(inv);
        if (emptyHotbarSlot != -1) {

          inv.setItem(emptyHotbarSlot, bestWeapon.clone());
          inv.setItem(bestSlot, null);
          bestSlot = emptyHotbarSlot;
        } else {

          int swapSlot = findLeastValuableHotbarSlot(inv);
          ItemStack temp = inv.getItem(swapSlot);
          inv.setItem(swapSlot, bestWeapon.clone());
          inv.setItem(bestSlot, temp);
          bestSlot = swapSlot;
        }
      }
    }

    if (bestWeapon != null && !bestWeapon.equals(mainHand)) {

      inv.setItem(bestSlot, mainHand);

      inv.setItemInMainHand(bestWeapon);
      s.currentWeapon = bestWeapon.getType();
    } else if (mainHand != null && mainHand.getType() != Material.AIR) {
      s.currentWeapon = mainHand.getType();
    } else {
      s.currentWeapon = null;
    }
  }

  private static double getWeaponDamage(Material weapon) {
    if (weapon == null) return ATTACK_DAMAGE;
    return WEAPON_DAMAGE.getOrDefault(weapon, ATTACK_DAMAGE);
  }

  private static void applyWeaponCooldown(BotState s, ThreadLocalRandom rng) {
    if (s.currentWeapon != null && WEAPON_COOLDOWN.containsKey(s.currentWeapon)) {

      s.weaponCooldown = WEAPON_COOLDOWN.get(s.currentWeapon);
      s.attackCooldown = s.weaponCooldown;
    } else {

      int cdBase = rng.nextInt(COOLDOWN_MIN, COOLDOWN_MAX + 1);

      s.attackCooldown = Math.max(COOLDOWN_MIN, cdBase - Math.min(s.comboCount / 2, 3));
      s.weaponCooldown = s.attackCooldown;
    }
  }

  private static void manageTotem(Player bot, BotState s, ThreadLocalRandom rng) {
    PlayerInventory inv = bot.getInventory();
    ItemStack offhand = inv.getItemInOffHand();

    if (s.totemUsed && s.totemSwapDelay == 0) {

      ItemStack newTotem = findTotemInInventory(inv);
      if (newTotem != null) {

        inv.setItemInOffHand(newTotem.clone());
        newTotem.setAmount(0);
        s.totemUsed = false;
      }
    }

    if (!s.totemUsed && (offhand == null || offhand.getType() == Material.AIR)) {

      if (findTotemInInventory(inv) != null) {
        s.totemUsed = true;

        s.totemSwapDelay = rng.nextInt(TOTEM_SWAP_DELAY_MIN, TOTEM_SWAP_DELAY_MAX + 1);
      }
    }

    if (!s.totemUsed
        && s.totemSwapDelay == 0
        && (offhand == null || offhand.getType() != Material.TOTEM_OF_UNDYING)) {
      ItemStack totem = findTotemInInventory(inv);
      if (totem != null) {
        ItemStack currentOffhand = inv.getItemInOffHand();

        inv.setItemInOffHand(totem.clone());
        totem.setAmount(0);

        if (currentOffhand != null && currentOffhand.getType() != Material.AIR) {

          int emptySlot = findEmptyInventorySlot(inv);
          if (emptySlot != -1) {
            inv.setItem(emptySlot, currentOffhand);
          } else {

                        // No empty slot - consume the item instead of dropping it to prevent dupe
          }
        }
      }
    }
  }

  private static ItemStack findTotemInInventory(PlayerInventory inv) {

    for (int i = 0; i < 9; i++) {
      ItemStack item = inv.getItem(i);
      if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
        return item;
      }
    }

    for (int i = 9; i < 36; i++) {
      ItemStack item = inv.getItem(i);
      if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
        return item;
      }
    }
    return null;
  }

  private static int findEmptyHotbarSlot(PlayerInventory inv) {
    for (int i = 0; i < 9; i++) {
      ItemStack item = inv.getItem(i);
      if (item == null || item.getType() == Material.AIR) {
        return i;
      }
    }
    return -1;
  }

  private static int findEmptyInventorySlot(PlayerInventory inv) {
    for (int i = 0; i < 36; i++) {
      ItemStack item = inv.getItem(i);
      if (item == null || item.getType() == Material.AIR) {
        return i;
      }
    }
    return -1;
  }

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

  private static double getItemValue(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) {
      return 0.0;
    }

    Material type = item.getType();

    if (type == Material.TOTEM_OF_UNDYING) {
      return 1000.0;
    }

    if (WEAPON_DAMAGE.containsKey(type)) {
      return WEAPON_DAMAGE.get(type) * 100;
    }

    if (type == Material.GOLDEN_APPLE) {
      return 800.0;
    }
    if (type == Material.ENCHANTED_GOLDEN_APPLE) {
      return 950.0;
    }

    if (type.name().endsWith("_HELMET")
        || type.name().endsWith("_CHESTPLATE")
        || type.name().endsWith("_LEGGINGS")
        || type.name().endsWith("_BOOTS")) {
      if (type.name().startsWith("NETHERITE")) return 700.0;
      if (type.name().startsWith("DIAMOND")) return 600.0;
      if (type.name().startsWith("IRON")) return 500.0;
      if (type.name().startsWith("GOLDEN")) return 400.0;
      if (type.name().startsWith("CHAINMAIL")) return 450.0;
      return 300.0;
    }

    if (type.isEdible()) {
      return 200.0;
    }

    if (type == Material.ENDER_PEARL) {
      return 600.0;
    }

    if (type == Material.POTION
        || type == Material.SPLASH_POTION
        || type == Material.LINGERING_POTION) {
      return 500.0;
    }

    return 100.0;
  }

  private static boolean hasLineOfSight(Player bot, Player target) {
    Location botEye = bot.getEyeLocation();
    Location targetEye = target.getEyeLocation();

    try {
      return bot.hasLineOfSight(target);
    } catch (Exception e) {

      return raytraceLineOfSight(botEye, targetEye);
    }
  }

  private static boolean hasLineOfSight(Player bot, LivingEntity target) {
    Location botEye = bot.getEyeLocation();
    Location targetEye = target.getEyeLocation();

    try {
      return bot.hasLineOfSight(target);
    } catch (Exception e) {
      return raytraceLineOfSight(botEye, targetEye);
    }
  }

  private static boolean raytraceLineOfSight(Location from, Location to) {
    if (!from.getWorld().equals(to.getWorld())) {
      return false;
    }

    Vector direction = to.toVector().subtract(from.toVector());
    double distance = direction.length();
    direction.normalize();

    double step = 0.5;
    for (double d = 0; d < distance; d += step) {
      Location check = from.clone().add(direction.clone().multiply(d));
      Block block = check.getBlock();

      if (block.getType().isSolid() && !block.isPassable()) {
        return false;
      }
    }

    return true;
  }

  private static LivingEntity checkForHostileMobs(Player bot, BotState s) {

    if (s.mobTargetId != null) {
      for (Entity entity : bot.getWorld().getEntities()) {
        if (entity.getUniqueId().equals(s.mobTargetId) && entity instanceof Mob mob) {
          if (mob.isValid()
              && !mob.isDead()
              && bot.getLocation().distanceSquared(mob.getLocation())
                  <= MOB_DETECT_RANGE * MOB_DETECT_RANGE) {
            return mob;
          }
        }
      }

      s.mobTargetId = null;
    }

    Mob closestAggro = null;
    double closestAggroDist = MOB_DETECT_RANGE * MOB_DETECT_RANGE;

    Mob closestHostile = null;
    double closestHostileDist = MOB_DETECT_RANGE * MOB_DETECT_RANGE;

    for (Entity entity :
        bot.getNearbyEntities(MOB_DETECT_RANGE, MOB_DETECT_RANGE, MOB_DETECT_RANGE)) {
      if (!(entity instanceof Mob mob)) continue;
      if (!mob.isValid() || mob.isDead()) continue;

      if (!isHostileMob(mob)) continue;

      double distSq = bot.getLocation().distanceSquared(mob.getLocation());

      if (mob.getTarget() != null && mob.getTarget().equals(bot)) {
        if (distSq < closestAggroDist) {
          closestAggroDist = distSq;
          closestAggro = mob;
        }
      } else if (distSq < closestHostileDist) {
        closestHostileDist = distSq;
        closestHostile = mob;
      }
    }

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

  private static boolean isHostileMob(Mob mob) {
    return switch (mob.getType()) {
      case ZOMBIE,
          HUSK,
          DROWNED,
          SKELETON,
          STRAY,
          WITHER_SKELETON,
          CREEPER,
          SPIDER,
          CAVE_SPIDER,
          ENDERMAN,
          BLAZE,
          GHAST,
          SLIME,
          MAGMA_CUBE,
          WITCH,
          VINDICATOR,
          EVOKER,
          PILLAGER,
          RAVAGER,
          VEX,
          PHANTOM,
          HOGLIN,
          PIGLIN_BRUTE,
          ZOGLIN,
          WARDEN,
          SILVERFISH,
          ENDERMITE,
          GUARDIAN,
          ELDER_GUARDIAN,
          SHULKER,
          PIGLIN,
          ZOMBIFIED_PIGLIN ->
          true;
      default -> false;
    };
  }

  private static void combatMob(
      FakePlayer fp, Player bot, BotState s, ThreadLocalRandom rng, LivingEntity mobTarget) {
    Location botLoc = bot.getLocation();
    Location mobLoc = mobTarget.getLocation();

    double dx = mobLoc.getX() - botLoc.getX();
    double dz = mobLoc.getZ() - botLoc.getZ();
    double horizDist = Math.sqrt(dx * dx + dz * dz);

    if (horizDist > 1e-4) {
      double nx = dx / horizDist;
      double nz = dz / horizDist;

      float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
      double dy = mobLoc.getY() - botLoc.getY();
      float pitch = (float) (-Math.toDegrees(Math.atan2(dy + 1.0 - 1.62, horizDist)));

      if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
        bot.setRotation(yaw, pitch);
        NmsPlayerSpawner.setHeadYaw(bot, yaw);

        for (Player p : Bukkit.getOnlinePlayers()) {
          if (!p.getUniqueId().equals(fp.getUuid()))
            PacketHelper.sendRotation(p, fp, yaw, pitch, yaw);
        }
      }

      if (horizDist > ATTACK_RANGE) {

        Vector vel = bot.getVelocity();
        vel.setX(nx * SPRINT_SPEED);
        vel.setZ(nz * SPRINT_SPEED);

        if (isGrounded(bot) && shouldJumpObstacle(bot, nx, nz)) {
          vel.setY(0.42);
        }

        applyPhysicsRestrictions(bot, vel);
        safeSetVelocity(bot, vel);
        bot.setSprinting(true);
      } else {

        if (s.weaponCooldown == 0 && hasLineOfSight(bot, mobTarget)) {
          bot.swingMainHand();

          double dmg = getWeaponDamage(s.currentWeapon);

          mobTarget.damage(dmg, bot);

          applyWeaponCooldown(s, rng);

          s.retreatTicks = 2;
        }

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

  private Player resolveTarget(FakePlayer fp, Player bot, BotState s, List<Player> online) {

    if (s.specificTarget != null) {
      Player specific = Bukkit.getPlayer(s.specificTarget);
      if (specific != null && specific.isOnline() && !specific.isDead()) {

        if (specific.getWorld().equals(bot.getWorld())) {
          return specific;
        }
      }

      s.specificTarget = null;
    }

    if (s.defensiveMode && s.lastAttacker != null && s.attackerTimeout > 0) {
      Player attacker = Bukkit.getPlayer(s.lastAttacker);
      if (isValidTarget(attacker, bot, s)) {
        return attacker;
      }
    }

    if (s.targetId != null) {
      Player existing = Bukkit.getPlayer(s.targetId);
      if (isValidTarget(existing, bot, s)) return existing;
    }

    if (s.defensiveMode) {
      return null;
    }

    Player nearest = null;
    double bestSq = Config.pvpAiDetectRange() * Config.pvpAiDetectRange();
    for (Player p : online) {
      if (!isValidTarget(p, bot, s)) continue;
      double dSq = bot.getLocation().distanceSquared(p.getLocation());
      if (dSq < bestSq) {
        bestSq = dSq;
        nearest = p;
      }
    }
    return nearest;
  }

  private boolean isValidTarget(Player p, Player bot, BotState s) {
    if (p == null || !p.isOnline() || p.isDead()) return false;
    if (!p.getWorld().equals(bot.getWorld())) return false;
    if (manager.getByUuid(p.getUniqueId()) != null) return false;

    GameMode gm = p.getGameMode();
    if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return false;

    if (s.specificTarget != null && p.getUniqueId().equals(s.specificTarget)) {
      return true;
    }

    double detectRange = Config.pvpAiDetectRange();
    return bot.getLocation().distanceSquared(p.getLocation()) <= detectRange * detectRange;
  }

  private static void updateArmor(Player bot) {
    PlayerInventory inv = bot.getInventory();
    upgradeArmorSlot(inv, "HELMET");
    upgradeArmorSlot(inv, "CHESTPLATE");
    upgradeArmorSlot(inv, "LEGGINGS");
    upgradeArmorSlot(inv, "BOOTS");
  }

  private static void upgradeArmorSlot(PlayerInventory inv, String suffix) {
    ItemStack current =
        switch (suffix) {
          case "HELMET" -> inv.getHelmet();
          case "CHESTPLATE" -> inv.getChestplate();
          case "LEGGINGS" -> inv.getLeggings();
          case "BOOTS" -> inv.getBoots();
          default -> null;
        };
    double currentVal = getArmorValue(current);

    ItemStack best = current;
    double bestVal = currentVal;
    int bestSlot = -1;

    for (int i = 0; i < 36; i++) {
      ItemStack item = inv.getItem(i);
      if (item == null || item.getType() == Material.AIR) continue;
      if (!item.getType().name().endsWith("_" + suffix)) continue;
      double val = getArmorValue(item);
      if (val > bestVal) {
        bestVal = val;
        best = item;
        bestSlot = i;
      }
    }

    if (bestSlot != -1 && best != null) {

      inv.setItem(
          bestSlot,
          (current != null && current.getType() != Material.AIR) ? current.clone() : null);
      switch (suffix) {
        case "HELMET" -> inv.setHelmet(best.clone());
        case "CHESTPLATE" -> inv.setChestplate(best.clone());
        case "LEGGINGS" -> inv.setLeggings(best.clone());
        case "BOOTS" -> inv.setBoots(best.clone());
      }
    }
  }

  private static double getArmorValue(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return 0.0;
    String n = item.getType().name();
    if (n.startsWith("NETHERITE")) return 100.0;
    if (n.startsWith("DIAMOND")) return 80.0;
    if (n.startsWith("IRON")) return 60.0;
    if (n.startsWith("CHAINMAIL")) return 50.0;
    if (n.startsWith("GOLDEN")) return 40.0;
    if (n.startsWith("LEATHER")) return 30.0;
    return 0.0;
  }

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

  private static void handleCrystalPvp(
      FakePlayer fp, Player bot, BotState s, Player target, ThreadLocalRandom rng) {
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
        EnderCrystal ec =
            (EnderCrystal)
                crystalBlock
                    .getWorld()
                    .spawnEntity(
                        crystalBlock.getLocation().add(0.5, 0, 0.5), EntityType.END_CRYSTAL);
        ec.setShowingBottom(false);

        me.bill.fakePlayerPlugin.util.FppScheduler.runSyncLater(
            bot.getServer().getPluginManager().getPlugin("FakePlayerPlugin"),
            () -> {
              if (ec.isValid()) {
                ec.remove();
                for (Entity e : ec.getNearbyEntities(6, 6, 6)) {
                  if (e instanceof LivingEntity le && !e.equals(bot)) le.damage(6.0, bot);
                }
              }
            },
            1L);

        s.crystalCooldown = CRYSTAL_COOLDOWN;
        crystal.setAmount(crystal.getAmount() - 1);
      }
    }
  }

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
    Vector dir =
        new Location(tgtLoc.getWorld(), tgtLoc.getX(), targetY, tgtLoc.getZ())
            .toVector()
            .subtract(bot.getEyeLocation().toVector())
            .normalize();

    EnderPearl thrown = bot.launchProjectile(EnderPearl.class);
    thrown.setVelocity(dir.multiply(1.5));

    pearl.setAmount(pearl.getAmount() - 1);
    if (pearl.getAmount() <= 0) inv.remove(pearl);
    inv.setItemInMainHand(mainHand);

    s.pearlCooldown = PEARL_COOLDOWN;
  }

  private static void handleTotemRefill(Player bot, BotState s) {
    bot.setSprinting(false);
    Vector vel = bot.getVelocity();
    vel.setX(0);
    vel.setZ(0);
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

  private static boolean isGrounded(Player p) {
    return ((Entity) p).isOnGround();
  }

  private static double maxHp(LivingEntity e) {
    AttributeInstance inst = e.getAttribute(AttributeCompat.MAX_HEALTH);
    return inst != null ? inst.getValue() : 20.0;
  }
}

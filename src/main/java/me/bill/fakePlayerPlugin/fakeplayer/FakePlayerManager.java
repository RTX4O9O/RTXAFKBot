package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.api.event.FppBotTeleportEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.BotTabTeam;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.RandomNameGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;

public class FakePlayerManager {

  public static NamespacedKey FAKE_PLAYER_KEY;

  private final FakePlayerPlugin plugin;
  private final Map<UUID, FakePlayer> activePlayers = new ConcurrentHashMap<>();

  private final Map<Integer, FakePlayer> entityIdIndex = new ConcurrentHashMap<>();

  private static final org.bukkit.util.Vector ZERO_VELOCITY = new org.bukkit.util.Vector(0, 0, 0);

  /**
   * Cosine of the maximum gaze angle for head-AI player tracking.
   * A player must be looking toward the bot within this cone for the bot to
   * react. cos(15°) ≈ 0.9659 produces natural-feeling eye-contact without
   * bots mechanically staring at every nearby player.
   */
  private static final double HEAD_AI_GAZE_COS = Math.cos(Math.toRadians(15.0));

  private final Map<String, FakePlayer> nameIndex = new ConcurrentHashMap<>();
  private final Set<String> usedNames = new HashSet<>();

  private final Map<UUID, Long> spawnCooldowns = new ConcurrentHashMap<>();

  private final Map<UUID, float[]> botHeadRotation = new ConcurrentHashMap<>();

  private final Map<UUID, float[]> botSpawnRotation = new ConcurrentHashMap<>();

  private volatile boolean restorationInProgress = false;

  private volatile List<String> cleanNamePool = Collections.emptyList();

  private List<Player> cachedOnlinePlayers = new ArrayList<>();

  private int headAiTickCounter = 0;

  private final Set<UUID> bodyTransitionBots = new HashSet<>();

  private final Map<UUID, Integer> navJumpHolding = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<UUID, Location> actionLockedBots = new ConcurrentHashMap<>();

  private final Set<UUID> navLockedBots = ConcurrentHashMap.newKeySet();

  private final ConcurrentHashMap<UUID, String> despawningBotIds = new ConcurrentHashMap<>();

  private final Set<UUID> renamingBotIds = ConcurrentHashMap.newKeySet();

  private final Map<UUID, Double> trackedFallDistance = new ConcurrentHashMap<>();

  private final Set<UUID> wasOnGround = ConcurrentHashMap.newKeySet();

  /**
   * Inventory + XP snapshot saved when a bot is manually despawned (only when
   * {@code dropItemsOnDespawn=false}). Keyed by lowercase bot name. Consumed on the next
   * same-name spawn so inventory and XP survive a manual despawn → spawn cycle.
   */
  private record DespawnSnapshot(
      ItemStack[] mainContents,
      ItemStack[] armorContents,
      ItemStack[] extraContents,
      int xpTotal,
      int xpLevel,
      float xpProgress) {}

  private final ConcurrentHashMap<String, DespawnSnapshot> despawnSnapshots =
      new ConcurrentHashMap<>();

  /** YAML fallback file for despawn snapshots (used when DB is disabled). */
  private volatile java.io.File despawnSnapshotFile = null;

  private ChunkLoader chunkLoader;
  private DatabaseManager db;
  private BotPersistence persistence;
  private BotTabTeam botTabTeam;

  private BotSwapAI botSwapAI;

  private BotIdentityCache identityCache;

  public void setChunkLoader(ChunkLoader cl) {
    this.chunkLoader = cl;
  }

  public void setDatabaseManager(DatabaseManager db) {
    this.db = db;
  }

  public void setBotPersistence(BotPersistence p) {
    this.persistence = p;
  }

  public void setBotTabTeam(BotTabTeam t) {
    this.botTabTeam = t;
  }

  public void setBotSwapAI(BotSwapAI ai) {
    this.botSwapAI = ai;
  }

  public void setIdentityCache(BotIdentityCache ic) {
    this.identityCache = ic;
  }

  public void refreshCleanNamePool() {
    List<String> raw = me.bill.fakePlayerPlugin.config.Config.namePool();
    List<String> clean = new ArrayList<>(raw.size());
    for (String n : raw) {
      if (n == null || n.isEmpty() || n.length() > 16 || !n.matches("[a-zA-Z0-9_]+")) continue;
      if (!me.bill.fakePlayerPlugin.util.BadwordFilter.isAllowed(n)) continue;
      clean.add(n);
    }
    cleanNamePool = Collections.unmodifiableList(clean);
    me.bill.fakePlayerPlugin.config.Config.debugStartup(
        "Clean name pool refreshed: "
            + clean.size()
            + "/"
            + raw.size()
            + " names pass the badword filter.");
  }

  public BotChatAI getBotChatAI() {
    return plugin.getBotChatAI();
  }

  public BotSwapAI getBotSwapAI() {
    return botSwapAI;
  }

  public FakePlayerManager(FakePlayerPlugin plugin) {
    this.plugin = plugin;
    FAKE_PLAYER_KEY = new NamespacedKey(plugin, "fake_player_name");

    if (!me.bill.fakePlayerPlugin.util.AttributionManager.quickAuthorCheck()
        || !me.bill.fakePlayerPlugin.util.AttributionApiManager.quickEndpointCheck()) {
      FppLogger.warn("Plugin attribution integrity check failed in FakePlayerManager.");
    }

    long flushTicks = Math.max(20L, Config.dbLocationFlushInterval() * 20L);
    FppScheduler.runSyncRepeating(
        plugin,
        () -> {
              if (activePlayers.isEmpty()) return;
              for (FakePlayer fp : activePlayers.values()) {

                org.bukkit.Location loc = fp.getLiveLocation();
                if (loc == null || loc.getWorld() == null) continue;
                String world = loc.getWorld().getName();

                fp.setSpawnLocation(loc.clone());
                if (db != null) {
                  db.updateLastLocation(
                      fp.getUuid(),
                      world,
                      loc.getX(),
                      loc.getY(),
                      loc.getZ(),
                      loc.getYaw(),
                      loc.getPitch());
                }
              }
              if (db != null) db.flushPendingLocations();
            },
        flushTicks,
        flushTicks);

    FppScheduler.runSyncRepeating(
        plugin,
        () -> {
              if (!Config.tabListEnabled()) return;
              if (activePlayers.isEmpty()) return;
              List<Player> online = cachedOnlinePlayers;
              if (online == null || online.isEmpty()) return;
              for (FakePlayer fp : activePlayers.values()) {
                if (!fp.isTabListDirty()) continue;
                fp.clearTabListDirty();
                for (Player p : online) {
                  PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                  if (fp.hasCustomPing()) {
                    PacketHelper.sendTabListLatencyUpdate(p, fp);
                  }
                }
              }
            },
        20L,
        20L);

    FppScheduler.runSyncRepeating(
        plugin,
        () -> {
              if (activePlayers.isEmpty()) return;

              Collection<? extends Player> live = Bukkit.getOnlinePlayers();
              List<Player> online = cachedOnlinePlayers;
              if (online == null || online.size() != live.size()) {
                online = new ArrayList<>(live);
                cachedOnlinePlayers = online;
              }

              headAiTickCounter++;
              final boolean headAiOn = Config.headAiEnabled();
              final int headAiRate = Config.headAiTickRate();

              final boolean doHeadAi = headAiOn && (headAiTickCounter % headAiRate == 0);
              final double rangeSq =
                  doHeadAi ? Config.headAiLookRange() * Config.headAiLookRange() : 0;
              final float speed = doHeadAi ? Config.headAiTurnSpeed() : 0f;

              final double psd = Config.positionSyncDistance();
              final double posSyncDistSq = psd > 0 ? psd * psd : -1;

              final int onlineCount = online.size();
              final double[] playerX = new double[onlineCount];
              final double[] playerY = new double[onlineCount];
              final double[] playerZ = new double[onlineCount];
              final org.bukkit.World[] playerWorld = new org.bukkit.World[onlineCount];
              for (int pi = 0; pi < onlineCount; pi++) {
                Location pl = online.get(pi).getLocation();
                playerX[pi] = pl.getX();
                playerY[pi] = pl.getY();
                playerZ[pi] = pl.getZ();
                playerWorld[pi] = pl.getWorld();
              }

              for (FakePlayer fp : activePlayers.values()) {
                Player bot = fp.getPlayer();

                if (bot == null || !bot.isValid() || !bot.isOnline() || bot.isDead()) continue;
                Location before = bot.getLocation();

                if (!fp.isFrozen()) {

                  boolean isNavigating = plugin.getPathfindingService() != null
                      && plugin.getPathfindingService().isNavigating(fp.getUuid());

                  if (fp.isSwimAiEnabled()) {
                    boolean navJump =
                        isNavigating && navJumpHolding.getOrDefault(fp.getUuid(), 0) > 0;
                    PathfindingService.tickSwimAi(bot, navJump, isNavigating);
                  }
                  navJumpHolding.computeIfPresent(fp.getUuid(), (k, v) -> v > 1 ? v - 1 : null);

                  if (fp.isAutoEatEnabled()) {
                    tickAutoEat(bot);
                  }

                  // Sleeping bots: check every tick whether NMS has woken the bot
                  // (bed broken, monsters nearby, time-skip complete). Syncing here
                  // rather than waiting for the 40-tick nightWatchTick sweep means the
                  // bot stands up on the exact same tick the bed is removed.
                  if (fp.isSleeping()) {
                    net.minecraft.server.level.ServerPlayer nmsBot =
                        ((org.bukkit.craftbukkit.entity.CraftPlayer) bot).getHandle();
                    if (!nmsBot.isSleeping()) {
                      // NMS already woke the bot — clear flag and fall through to normal tick.
                      fp.setSleeping(false);
                      actionLockedBots.remove(fp.getUuid());
                      // fall through — bot immediately resumes normal physics/AI below
                    } else {
                      // Still genuinely sleeping: zero velocity, tick physics so that
                      // NMS sleepCounter increments (needed for time-skip to dawn),
                      // then skip all AI/movement for this tick.
                      bot.setVelocity(ZERO_VELOCITY);
                      NmsPlayerSpawner.tickPhysics(bot);
                      var fppApiTickSleep = plugin.getFppApiImpl();
                      if (fppApiTickSleep != null) fppApiTickSleep.fireTickHandlers(fp, bot);
                      continue;
                    }
                  }

                  NmsPlayerSpawner.tickPhysics(bot);

                  if (doHeadAi
                      && fp.isHeadAiEnabled()
                      && !actionLockedBots.containsKey(fp.getUuid())
                      && !navLockedBots.contains(fp.getUuid())) {

                    // Head-AI target selection: only track a player who is actively
                    // looking at this bot (eye-contact model). Conditions:
                    //   1. Player is within look range (rangeSq)
                    //   2. Bot has line of sight to the player
                    //   3. The player's look direction points toward the bot
                    //      within HEAD_AI_GAZE_COS (cos of ~15 degrees).
                    // This prevents bots from mechanically staring at whoever
                    // walks past; they only react when a player is gazing at them.
                    Player target = null;
                    double bestSq = rangeSq;
                    for (int pi2 = 0; pi2 < onlineCount; pi2++) {
                      Player p = online.get(pi2);
                      if (activePlayers.containsKey(p.getUniqueId())) continue;
                      if (p.getGameMode() == GameMode.SPECTATOR) continue;
                      if (playerWorld[pi2] != before.getWorld()) continue;
                      double ddx = playerX[pi2] - before.getX();
                      double ddy = playerY[pi2] - before.getY();
                      double ddz = playerZ[pi2] - before.getZ();
                      double dSq = ddx * ddx + ddy * ddy + ddz * ddz;
                      if (dSq > bestSq) continue;
                      // Check that the player is looking toward this bot (gaze test).
                      // We compare the player's look direction against the unit vector
                      // from the player's eye to the bot's eye.
                      org.bukkit.util.Vector lookDir = p.getEyeLocation().getDirection();
                      double botEyeX = before.getX() - playerX[pi2];
                      double botEyeY = (before.getY() + 1.62) - (playerY[pi2] + 1.62);
                      double botEyeZ = before.getZ() - playerZ[pi2];
                      double dist = Math.sqrt(botEyeX * botEyeX + botEyeY * botEyeY + botEyeZ * botEyeZ);
                      if (dist < 0.001) continue;
                      double dot = (lookDir.getX() * botEyeX + lookDir.getY() * botEyeY + lookDir.getZ() * botEyeZ) / dist;
                      // cos(15°) ≈ 0.9659 — tighter cone means more deliberate eye-contact
                      if (dot < HEAD_AI_GAZE_COS) continue;
                      if (!hasLineOfSightIgnoringGlass(bot, p)) continue;
                      bestSq = dSq;
                      target = p;
                    }

                    final Location beforeCapture = before;
                    float[] rot =
                        botHeadRotation.computeIfAbsent(
                            fp.getUuid(),
                            k -> new float[] {beforeCapture.getYaw(), beforeCapture.getPitch()});

                    float prevYaw = rot[0];
                    float prevPitch = rot[1];

                    if (target != null) {

                      Location eye = bot.getEyeLocation();
                      Location tgt = target.getEyeLocation();
                      double dx = tgt.getX() - eye.getX();
                      double dy = tgt.getY() - eye.getY();
                      double dz = tgt.getZ() - eye.getZ();
                      double horiz = Math.sqrt(dx * dx + dz * dz);
                      float targetYaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
                      float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
                      rot[0] = lerpAngle(rot[0], targetYaw, speed);
                      rot[1] = lerpAngle(rot[1], targetPitch, speed);
                    }

                    if (Math.abs(rot[0] - prevYaw) > 0.01f
                        || Math.abs(rot[1] - prevPitch) > 0.01f) {
                      bot.setRotation(rot[0], rot[1]);
                      NmsPlayerSpawner.setHeadYaw(bot, rot[0]);
                      for (int pi2 = 0; pi2 < onlineCount; pi2++) {
                        Player p = online.get(pi2);
                        if (p.getUniqueId().equals(fp.getUuid())) continue;
                        if (playerWorld[pi2] != before.getWorld()) continue;
                        if (posSyncDistSq > 0) {
                          double ddx = playerX[pi2] - before.getX();
                          double ddz = playerZ[pi2] - before.getZ();
                          if (ddx * ddx + ddz * ddz > posSyncDistSq) continue;
                        }
                        PacketHelper.sendRotation(p, fp, rot[0], rot[1], rot[0]);
                      }
                    }
                  }

                  Location miningLock = actionLockedBots.get(fp.getUuid());
                  if (miningLock != null) {
                    Location cur = bot.getLocation();
                    boolean outOfPlace =
                        !cur.getWorld().equals(miningLock.getWorld())
                            || cur.distanceSquared(miningLock) > 0.0001;
                    if (outOfPlace) {
                      FppScheduler.teleportAsync(bot, miningLock);
                    }

                    float ly = miningLock.getYaw();
                    float lp = miningLock.getPitch();
                    bot.setRotation(ly, lp);
                    NmsPlayerSpawner.setHeadYaw(bot, ly);
                    for (int pi2 = 0; pi2 < onlineCount; pi2++) {
                      Player p = online.get(pi2);
                      if (p.getUniqueId().equals(fp.getUuid())) continue;
                      if (playerWorld[pi2] != miningLock.getWorld()) continue;
                      if (posSyncDistSq > 0) {
                        double ddx = playerX[pi2] - miningLock.getX();
                        double ddz = playerZ[pi2] - miningLock.getZ();
                        if (ddx * ddx + ddz * ddz > posSyncDistSq) continue;
                      }
                      PacketHelper.sendRotation(p, fp, ly, lp, ly);
                    }

                    bot.setVelocity(ZERO_VELOCITY);
                  }

                  // Fire addon tick handlers.
                  var fppApiTick = plugin.getFppApiImpl();
                  if (fppApiTick != null) fppApiTick.fireTickHandlers(fp, bot);
                }

                Location after = bot.getLocation();
                tickFallDamage(fp, bot, before, after);
                double dxM = before.getX() - after.getX();
                double dyM = before.getY() - after.getY();
                double dzM = before.getZ() - after.getZ();

                org.bukkit.util.Vector vel2 = bot.getVelocity();
                boolean moved =
                    before.getWorld() == after.getWorld()
                        && (dxM * dxM + dyM * dyM + dzM * dzM) > 1e-8;
                double vx = vel2.getX(), vy = vel2.getY(), vz2 = vel2.getZ();
                if (moved || (vx * vx + vy * vy + vz2 * vz2) > 1e-6) {
                  if (onlineCount > 0) {
                    for (int pi2 = 0; pi2 < onlineCount; pi2++) {
                      Player p = online.get(pi2);
                      if (p.equals(bot)) continue;

                      if (posSyncDistSq > 0) {
                        if (playerWorld[pi2] != after.getWorld()) continue;
                        double ddx = playerX[pi2] - after.getX();
                        double ddy = playerY[pi2] - after.getY();
                        double ddz = playerZ[pi2] - after.getZ();
                        if (ddx * ddx + ddy * ddy + ddz * ddz > posSyncDistSq) continue;
                      }
                      PacketHelper.sendPositionSync(p, bot, after);
                    }
                  }
                }
              }
            },
        1L,
        1L);
  }

  private void tickFallDamage(FakePlayer fp, Player bot, Location before, Location after) {
    UUID uuid = fp.getUuid();
    if (actionLockedBots.containsKey(uuid) || bot.isDead() || bot.getGameMode() == GameMode.CREATIVE) {
      trackedFallDistance.remove(uuid);
      wasOnGround.add(uuid);
      return;
    }
    boolean onGround = !bot.getLocation().clone().subtract(0, 0.05, 0).getBlock().isPassable();
    if (!before.getWorld().equals(after.getWorld())) {
      trackedFallDistance.remove(uuid);
      if (onGround) wasOnGround.add(uuid);
      else wasOnGround.remove(uuid);
      return;
    }
    if (isFallDamageCancelledByLandingBlock(bot)) {
      trackedFallDistance.remove(uuid);
      if (onGround) wasOnGround.add(uuid);
      else wasOnGround.remove(uuid);
      return;
    }
    double dy = before.getY() - after.getY();
    if (!onGround && dy > 0.0) {
      trackedFallDistance.put(
          uuid,
          Math.max(trackedFallDistance.getOrDefault(uuid, 0.0), Math.max(dy, bot.getFallDistance())));
    }
    if (onGround) {
      double distance = trackedFallDistance.getOrDefault(uuid, 0.0);
      if (!wasOnGround.contains(uuid) && distance > 3.0) {
        double damage = Math.floor(distance - 3.0);
        if (damage > 0.0) {
          double beforeHealth = bot.getHealth();
          bot.damage(damage);
          if (!bot.isDead() && Math.abs(bot.getHealth() - beforeHealth) < 0.001) {
            bot.setHealth(Math.max(0.0, beforeHealth - damage));
            playHurtFeedback(fp, bot);
          }
        }
      }
      trackedFallDistance.remove(uuid);
      wasOnGround.add(uuid);
    } else {
      wasOnGround.remove(uuid);
    }
  }

  private boolean isFallDamageCancelledByLandingBlock(Player bot) {
    if (bot.isInWater() || bot.isInLava()) return true;
    Material feet = bot.getLocation().getBlock().getType();
    Material below = bot.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
    if (Tag.CLIMBABLE.isTagged(feet) || Tag.CLIMBABLE.isTagged(below)) return true;
    return feet == Material.BUBBLE_COLUMN
        || below == Material.BUBBLE_COLUMN
        || below == Material.HAY_BLOCK
        || below == Material.SLIME_BLOCK;
  }

  public void playHurtFeedback(FakePlayer fp, Player bot) {
    for (Player viewer : cachedOnlinePlayers) {
      if (viewer == null
          || !viewer.isOnline()
          || viewer.getWorld() != bot.getWorld()
          || viewer.getLocation().distanceSquared(bot.getLocation()) > 256 * 256) {
        continue;
      }
      PacketHelper.sendHurtAnimation(viewer, fp);
    }

    if (!Config.hurtSound()) {
      return;
    }

    Location loc = bot.getLocation();
    if (loc.getWorld() != null) {
      loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
  }

  public boolean physicalBodiesEnabled() {
    return Config.spawnBody();
  }

  public boolean isRestorationInProgress() {
    return restorationInProgress;
  }

  public void setRestorationInProgress(boolean inProgress) {
    this.restorationInProgress = inProgress;
  }

  public boolean isBodyTransitioning(UUID uuid) {
    return bodyTransitionBots.contains(uuid);
  }

  public boolean isDespawning(UUID uuid) {
    return despawningBotIds.containsKey(uuid);
  }

  public String getDespawningDisplayName(UUID uuid) {
    return despawningBotIds.get(uuid);
  }

  public void markRenaming(UUID uuid) {
    renamingBotIds.add(uuid);
  }

  public void unmarkRenaming(UUID uuid) {
    renamingBotIds.remove(uuid);
  }

  public boolean isRenaming(UUID uuid) {
    return renamingBotIds.contains(uuid);
  }

  public String formatLocationForDisplay(FakePlayer fp) {
    if (!physicalBodiesEnabled()) {
      return "No Body";
    }
    var body = fp.getPhysicsEntity();
    if (body != null && body.isValid()) {
      var l = body.getLocation();
      return (l.getWorld() != null ? l.getWorld().getName() : "?")
          + " "
          + l.getBlockX()
          + ","
          + l.getBlockY()
          + ","
          + l.getBlockZ();
    }
    var sl = fp.getSpawnLocation();
    if (sl != null)
      return (sl.getWorld() != null ? sl.getWorld().getName() : "?")
          + " "
          + sl.getBlockX()
          + ","
          + sl.getBlockY()
          + ","
          + sl.getBlockZ();
    return "unknown";
  }

  public int spawn(Location location, int count, Player spawner) {
    return spawn(location, count, spawner, null, false, BotType.AFK);
  }

  public int spawn(Location location, int count, Player spawner, String customName) {
    return spawn(location, count, spawner, customName, false, BotType.AFK);
  }

  public int spawnUserBot(Location location, int count, Player spawner, boolean bypassMax) {
    return spawnUserBot(location, count, spawner, bypassMax, BotType.AFK);
  }

  public int spawnUserBot(
      Location location, int count, Player spawner, boolean bypassMax, BotType botType) {
    int maxBots = Config.maxBots();
    if (!bypassMax && maxBots > 0) {
      int available = maxBots - activePlayers.size();
      if (available <= 0) return -1;
      count = Math.min(count, available);
    }

    String spawnerName = spawner.getName();
    UUID spawnerUuid = spawner.getUniqueId();

    int alreadyOwned = getBotsOwnedBy(spawnerUuid).size();

    List<FakePlayer> batch = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      UserBotName ubn = generateUserBotName(spawnerName, alreadyOwned + i);
      UUID uuid = resolveUuid(ubn.internalName());
      PlayerProfile profile = Bukkit.createProfile(uuid, ubn.internalName());
      FakePlayer fp = new FakePlayer(uuid, ubn.internalName(), profile);
      fp.setBotType(botType);

      fp.setSkinName(pickRandomSkinName());

      String cleanBotName = "bot" + (alreadyOwned + i + 1);
      String rawUserName =
          Config.userBotNameFormat()
              .replace("{spawner}", spawnerName)
              .replace("{num}", String.valueOf(alreadyOwned + i + 1))
              .replace("{bot_name}", cleanBotName);

      fp.setRawDisplayName(rawUserName);
      String userDisplay = finalizeDisplayName(rawUserName, ubn.internalName());
      fp.setDisplayName(userDisplay);
      fp.setSpawnLocation(location);
      fp.setSpawnedBy(spawnerName, spawnerUuid);
      activePlayers.put(uuid, fp);
      nameIndex.put(ubn.internalName().toLowerCase(), fp);
      batch.add(fp);

      if (db != null) {
        BotRecord record =
            new BotRecord(
                0,
                ubn.internalName(),
                uuid,
                spawnerName,
                spawnerUuid,
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                Instant.now(),
                null,
                null);
        fp.setDbRecord(record);
        db.recordSpawn(
            record,
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(me.bill.fakePlayerPlugin.util.TextUtil.colorize(ubn.displayName())));
      }
    }
    if (batch.isEmpty()) return 0;

    int total = batch.size();
    FppScheduler.runSync(plugin, () -> visualChain(batch, 0, location));
    return total;
  }

  public int spawn(
      Location location, int count, Player spawner, String customName, boolean bypassMax) {
    return spawn(location, count, spawner, customName, bypassMax, BotType.AFK);
  }

  public int spawn(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      BotType botType) {
    return spawn(location, count, spawner, customName, bypassMax, botType, false);
  }

  public int spawn(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      BotType botType,
      boolean forceRandomName) {
    int maxBots = Config.maxBots();
    if (!bypassMax && maxBots > 0) {
      int available = maxBots - activePlayers.size();
      if (available <= 0) return -1;
      count = Math.min(count, available);
    }

    String spawnerName = spawner != null ? spawner.getName() : "CONSOLE";
    UUID spawnerUuid = spawner != null ? spawner.getUniqueId() : new UUID(0, 0);

    if (customName != null) {

      String effectiveName = customName;

      if (effectiveName.isEmpty()
          || effectiveName.length() > 16
          || !effectiveName.matches("[a-zA-Z0-9_]+")) return -2;

      if (usedNames.contains(effectiveName)) return 0;

      Player realPlayer = Bukkit.getPlayerExact(effectiveName);
      if (realPlayer != null && !activePlayers.containsKey(realPlayer.getUniqueId())) return -4;

      if (plugin.isNameTagAvailable()
          && me.bill.fakePlayerPlugin.config.Config.nameTagBlockNickConflicts()
          && me.bill.fakePlayerPlugin.util.NameTagHelper.isNickUsedByRealPlayer(
              effectiveName, this)) {
        return -5;
      }
      count = 1;
    }

    List<FakePlayer> batch = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String baseName;
      String name;

      if (customName != null) {
        baseName = customName;
        name = customName;
      } else {
        name = generateName(forceRandomName);
        baseName = name;
      }

      if (name == null) break;
      UUID uuid = resolveUuid(name);
      PlayerProfile profile = Bukkit.createProfile(uuid, name);
      FakePlayer fp = new FakePlayer(uuid, name, profile);
      fp.setBotType(botType);

      fp.setSkinName(baseName != null ? baseName : name);

      String rawAdminName = Config.adminBotNameFormat().replace("{bot_name}", name);
      fp.setRawDisplayName(rawAdminName);
      String displayName = finalizeDisplayName(rawAdminName, name);
      fp.setDisplayName(displayName);
      fp.setSpawnLocation(location);
      fp.setSpawnedBy(spawnerName, spawnerUuid);
      activePlayers.put(uuid, fp);
      nameIndex.put(name.toLowerCase(), fp);
      batch.add(fp);

      if (db != null) {
        BotRecord record =
            new BotRecord(
                0,
                name,
                uuid,
                spawnerName,
                spawnerUuid,
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                Instant.now(),
                null,
                null);
        fp.setDbRecord(record);
        db.recordSpawn(
            record,
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(me.bill.fakePlayerPlugin.util.TextUtil.colorize(displayName)));
      }
    }
    if (batch.isEmpty()) return 0;

    int total = batch.size();

    FppScheduler.runSync(plugin, () -> visualChain(batch, 0, location));
    return total;
  }

  public int spawnBodyless(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      boolean spawnBodyless) {
    return spawnBodyless(
        location, count, spawner, customName, bypassMax, spawnBodyless, BotType.AFK);
  }

  public int spawnBodyless(
      Location location,
      int count,
      Player spawner,
      String customName,
      boolean bypassMax,
      boolean spawnBodyless,
      BotType botType) {
    int result = spawn(location, count, spawner, customName, bypassMax, botType);
    if (result > 0 && spawnBodyless) {

      String spawnerName = spawner != null ? spawner.getName() : "CONSOLE";
      UUID spawnerUuid = spawner != null ? spawner.getUniqueId() : new UUID(0, 0);

      long now = System.currentTimeMillis();
      activePlayers.values().stream()
          .filter(
              fp ->
                  fp.getSpawnedBy().equals(spawnerName)
                      && fp.getSpawnedByUuid().equals(spawnerUuid)
                      && (now - fp.getSpawnTime().toEpochMilli()) < 1000)
          .limit(result)
          .forEach(fp -> fp.setBodyless(true));
    }
    return result;
  }

  private void visualChain(List<FakePlayer> batch, int index, Location location) {
    if (batch == null) return;

    while (index < batch.size() && !activePlayers.containsKey(batch.get(index).getUuid())) {
      index++;
    }
    if (index >= batch.size()) return;

    FakePlayer fp = batch.get(index);
    finishSpawn(fp, location);

    int delayMinTicks = Config.joinDelayMin();
    int delayMaxTicks = Math.max(delayMinTicks, Config.joinDelayMax());
    long delayTicks;
    if (delayMaxTicks <= 0) {
      delayTicks = 0L;
    } else {
      int spread = delayMaxTicks - delayMinTicks;
      delayTicks =
          delayMinTicks + (spread > 0 ? ThreadLocalRandom.current().nextInt(spread + 1) : 0);
      if (delayTicks < 1) delayTicks = 0L;
    }

    final int next = index + 1;
    if (delayTicks <= 0) {

      FppScheduler.runSync(plugin, () -> visualChain(batch, next, location));
    } else {
      FppScheduler.runSyncLater(plugin, () -> visualChain(batch, next, location), delayTicks);
    }
  }

  private void finishSpawn(FakePlayer fp, Location spawnLoc) {
    fp.setSpawnTime(java.time.Instant.now());

    if (plugin.isLuckPermsAvailable()) {
      String cfgGroup = Config.luckpermsDefaultGroup();
      UUID botUuid = fp.getUuid();

      me.bill
          .fakePlayerPlugin
          .util
          .LuckPermsHelper
          .ensureGroupBeforeSpawn(botUuid, cfgGroup)
          .thenAccept(
              appliedGroup -> {
                fp.setLuckpermsGroup(appliedGroup);
                FppScheduler.runSync(
                    plugin,
                    () -> {
                      if (!activePlayers.containsKey(botUuid)) return;
                      spawnBodyAndFinish(fp, spawnLoc);
                    });
              })
          .exceptionally(
              ex -> {
                FppLogger.warn(
                    "[LP] Pre-assign failed for '" + fp.getName() + "': " + ex.getMessage());

                FppScheduler.runSync(
                    plugin,
                    () -> {
                      if (!activePlayers.containsKey(botUuid)) return;
                      spawnBodyAndFinish(fp, spawnLoc);
                    });
                return null;
              });
    } else {
      spawnBodyAndFinish(fp, spawnLoc);
    }
  }

  private void spawnBodyAndFinish(FakePlayer fp, Location spawnLoc) {

    FakePlayerBody.resolveAndFinish(
        plugin,
        fp,
        spawnLoc,
        () -> {
          if (!activePlayers.containsKey(fp.getUuid())) {
            Config.debug(
                "finishSpawn aborted for '"
                    + fp.getName()
                    + "' - removed before body spawn callback fired.");
            return;
          }

          if (fp.getAiPersonality() == null) {
            me.bill.fakePlayerPlugin.ai.PersonalityRepository repo =
                plugin.getPersonalityRepository();
            if (repo != null && repo.size() > 0) {
              java.util.List<String> names = repo.getNames();
              String randomName =
                  names.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(names.size()));
              fp.setAiPersonality(randomName);
              if (db != null) db.updateBotAiPersonality(fp.getUuid().toString(), randomName);
              Config.debugChat(
                  "Assigned random AI personality '"
                      + randomName
                      + "' to bot '"
                      + fp.getName()
                      + "'.");
            }
          }

          if (!fp.isBodyless() && Config.spawnBody()) {
            if (NmsPlayerSpawner.isFolia() && fp.getPlayer() == null) {
              // On Folia, dispatch placement to the destination chunk's region thread
              // and continue the spawn pipeline asynchronously.
              FakePlayerBody.spawnAsync(
                  fp,
                  spawnLoc,
                  asyncBody -> {
                    if (asyncBody == null) {
                      FppScheduler.runSync(
                          plugin,
                          () -> {
                            FppLogger.warn(
                                "finishSpawn: async body spawn failed for '"
                                    + fp.getName()
                                    + "' - rolling back.");
                            delete(fp.getName());
                          });
                      return;
                    }
                    FppScheduler.runSync(
                        plugin,
                        () -> {
                          if (!activePlayers.containsKey(fp.getUuid())) return;
                          fp.setPhysicsEntity(asyncBody);
                          entityIdIndex.put(asyncBody.getEntityId(), fp);
                          fp.setPacketProfileName(fp.getName());
                          // Re-enter the spawn pipeline; now fp.getPlayer() != null so
                          // the Folia branch is skipped and downstream logic runs normally.
                          spawnBodyAndFinish(fp, spawnLoc);
                        });
                  });
              return;
            }
            Player body =
                fp.getPlayer() != null ? fp.getPlayer() : FakePlayerBody.spawn(fp, spawnLoc);
            if (body != null) {
              fp.setPhysicsEntity(body);
              entityIdIndex.put(body.getEntityId(), fp);
              fp.setPacketProfileName(fp.getName());

              if (plugin.isWorldGuardAvailable()
                  && !me.bill.fakePlayerPlugin.util.WorldGuardHelper.isPvpAllowed(spawnLoc)) {
                org.bukkit.Location wgSafeLoc =
                    me.bill.fakePlayerPlugin.util.WorldGuardHelper.findSafeLocation(
                        spawnLoc.getWorld());
                if (wgSafeLoc != null) {
                  final Player safeBody = body;
                  final org.bukkit.Location safeTarget = wgSafeLoc;
                  // Use 25L so this fires after the BotSpawnProtectionListener's
                  // 20-tick protection window (which is used for custom-world spawns).
                  FppScheduler.runSyncLater(
                      plugin,
                      () -> {
                        if (!activePlayers.containsKey(fp.getUuid())) return;
                        if (!safeBody.isOnline()) return;
                        FppScheduler.teleportAsync(safeBody, safeTarget);
                        fp.setSpawnLocation(safeTarget);
                        Config.debug(
                            "WorldGuard: teleported bot '"
                                + fp.getName()
                                + "' out of no-pvp region"
                                + " to world spawn "
                                + safeTarget.getBlockX()
                                + ","
                                + safeTarget.getBlockY()
                                + ","
                                + safeTarget.getBlockZ());
                      },
                      25L);
                } else {
                  me.bill.fakePlayerPlugin.util.FppLogger.warn(
                      "WorldGuard: bot '"
                          + fp.getName()
                          + "' spawned in a no-pvp region but world spawn"
                          + " is also in a no-pvp region - cannot find a"
                          + " safe location.");
                }
              }

              final Player savedBody = body;
              final String savedName = fp.getName();
              final java.util.UUID savedUuid = fp.getUuid();

              if (plugin.isNameTagAvailable()
                  && me.bill.fakePlayerPlugin.config.Config.nameTagIsolation()) {
                final java.util.UUID isolateUuid = fp.getUuid();
                FppScheduler.runSyncLater(
                    plugin,
                    () -> {
                      me.bill.fakePlayerPlugin.util.NameTagHelper.NickData nickData =
                          me.bill.fakePlayerPlugin.util.NameTagHelper.clearBotFromCache(
                              isolateUuid);

                      FakePlayer botFp = getByUuid(isolateUuid);
                      if (botFp != null && nickData != null) {
                        botFp.setNameTagNick(nickData.nick());
                        if (plugin.getSkinManager() != null && nickData.skin() != null) {
                          plugin
                              .getSkinManager()
                              .applyNameTagSkin(botFp, nickData.skin(), nickData.nick());
                        }

                        if (me.bill.fakePlayerPlugin.config.Config.nameTagSyncNickAsRename()
                            && nickData.canRename()
                            && !nickData.plainNick().equalsIgnoreCase(botFp.getName())) {
                          final me.bill.fakePlayerPlugin.util.NameTagHelper.BotSkin savedSkin =
                              nickData.skin();
                          final String targetName = nickData.plainNick();
                          new me.bill.fakePlayerPlugin.util.BotRenameHelper(plugin, this)
                              .rename(org.bukkit.Bukkit.getConsoleSender(), botFp, targetName);
                          if (savedSkin != null) {
                            final int[] elapsed = {0};
                            final int[] skinTaskId = {-1};
                            skinTaskId[0] =
                                FppScheduler.runSyncRepeatingWithId(
                                    plugin,
                                    () -> {
                                      elapsed[0] += 5;
                                      if (elapsed[0] > 120) {
                                        FppScheduler.cancelTask(skinTaskId[0]);
                                        return;
                                      }
                                      FakePlayer newBot = getByName(targetName);
                                      if (newBot == null) return;
                                      if (plugin.getSkinManager() != null) {
                                        plugin
                                            .getSkinManager()
                                            .applyNameTagSkin(newBot, savedSkin, targetName);
                                      }
                                    },
                                    20L,
                                    5L);
                          }
                        }
                      }
                    },
                    3L);
              }

              FppScheduler.runSyncLater(
                  plugin,
                  () -> {
                    if (!savedBody.isOnline()) return;
                    try {

                      me.bill.fakePlayerPlugin.listener.PlayerJoinListener.stampFirstPlayed(savedBody);
                      savedBody.saveData();
                      FppLogger.debug(
                          "FakePlayerManager: initial playerdata"
                              + " saved for '"
                              + savedName
                              + "' uuid="
                              + savedUuid);
                    } catch (Exception e) {
                      FppLogger.warn(
                          "FakePlayerManager: initial saveData"
                              + " failed for '"
                              + savedName
                              + "' uuid="
                              + savedUuid
                              + ": "
                              + e.getMessage());
                    }
                  },
                  2L);
            } else {
              FppLogger.warn(
                  "finishSpawn: body spawn failed for '"
                      + fp.getName()
                      + "' - rolling back bot to avoid ghost keepalive connection.");
              delete(fp.getName());
              return;
            }
          } else if (fp.isBodyless()) {

            Config.debug("Bodyless spawn: skipping physical body for " + fp.getName());
          } else {

            fp.setBodyless(true);
            Config.debug("Body spawn skipped (body.enabled=false) for " + fp.getName());
          }

          List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
          if (Config.tabListEnabled()) {
            boolean isNmsPlayer = fp.getPlayer() != null;
            Config.debug(
                "Sending tab-list for '"
                    + fp.getName()
                    + "' display='"
                    + fp.getDisplayName()
                    + "' packet='"
                    + fp.getPacketProfileName()
                    + "' nms="
                    + isNmsPlayer);

            if (isNmsPlayer) {
              for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
              for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
            } else {

              for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
              for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
            }

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(fp.getUuid())) return;
                  boolean nms = fp.getPlayer() != null;
                  Config.debug("Re-sending tab-list (3t) for '" + fp.getName() + "' nms=" + nms);
                  for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, fp);
                  for (Player p : Bukkit.getOnlinePlayers())
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);
                },
                3L);

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(fp.getUuid())) return;
                  boolean nms = fp.getPlayer() != null;
                  Config.debug("Re-sending tab-list (20t) for '" + fp.getName() + "' nms=" + nms);
                  for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, fp);
                  for (Player p : Bukkit.getOnlinePlayers())
                    PacketHelper.sendTabListDisplayNameUpdate(p, fp);

                  if (botTabTeam != null) botTabTeam.addBot(fp);

                  var vc = plugin.getVelocityChannel();
                  if (vc != null) vc.broadcastBotSpawn(fp);
                },
                20L);
          } else {
            Config.debug("Tab-list disabled - unlisting '" + fp.getName() + "'");

            Player bot = fp.getPlayer();
            if (bot != null) NmsPlayerSpawner.setListed(bot, false);

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(fp.getUuid())) return;
                  for (Player p : Bukkit.getOnlinePlayers()) {
                    PacketHelper.sendTabListUpdateListed(p, fp, false);
                  }

                  var vc = plugin.getVelocityChannel();
                  if (vc != null) vc.broadcastBotSpawn(fp);
                },
                3L);
          }

          FppScheduler.runSyncLater(
              plugin,
              () -> {
                if (!activePlayers.containsKey(fp.getUuid())) return;
                if (fp.isBodyless()) {
                  BotBroadcast.broadcastJoin(fp);
                }

                var vc = plugin.getVelocityChannel();
                if (vc != null) vc.broadcastJoinToNetwork(fp);
              },
              2L);

          if (persistence != null && Config.persistOnRestart()) {
            persistence.saveAsync(activePlayers.values());
          }

          // Restore inventory+XP from a prior manual despawn of the same bot name.
          DespawnSnapshot despawnSnap = despawnSnapshots.remove(fp.getName().toLowerCase());
          if (despawnSnap != null) {
            removeDespawnSnapshotPersistent(fp.getName().toLowerCase());
            final UUID snapBotUuid = fp.getUuid();
            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  FakePlayer restoredFp = getByUuid(snapBotUuid);
                  if (restoredFp == null) return;
                  Player restoredBot = restoredFp.getPlayer();
                  if (restoredBot == null || !restoredBot.isValid()) return;
                  applyDespawnSnapshot(restoredBot, despawnSnap);
                  Config.debugSwap(
                      "[DespawnSnapshot] Restored inventory+XP to '" + restoredFp.getName() + "'.");
                },
                10L);
          }

          if (plugin.isLuckPermsAvailable()) {
            UUID botUuid = fp.getUuid();
            String group =
                fp.getLuckpermsGroup() != null && !fp.getLuckpermsGroup().isBlank()
                    ? fp.getLuckpermsGroup()
                    : (!me.bill.fakePlayerPlugin.config.Config.luckpermsDefaultGroup().isBlank()
                        ? me.bill.fakePlayerPlugin.config.Config.luckpermsDefaultGroup()
                        : "default");

            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  if (!activePlayers.containsKey(botUuid)) return;
                  me.bill
                      .fakePlayerPlugin
                      .util
                      .LuckPermsHelper
                      .applyGroupToOnlineUser(botUuid, group)
                      .thenRun(
                          () ->
                              FppScheduler.runSyncLater(
                                  plugin,
                                  () -> {
                                    if (!activePlayers.containsKey(botUuid)) return;
                                    refreshLpDisplayName(fp);

                                    if (botSwapAI != null) {
                                      botSwapAI.schedule(fp);
                                    }
                                  },
                                  2L));
                },
                5L);
          } else {

              if (botSwapAI != null) {
              FppScheduler.runSyncLater(
                  plugin,
                  () -> {
                    if (!activePlayers.containsKey(fp.getUuid())) return;
                    botSwapAI.schedule(fp);
                  },
                  10L);
            }
          }

          // Fire API spawn event.
          var fppApi = plugin.getFppApi();
          if (fppApi != null) {
            me.bill.fakePlayerPlugin.api.event.FppBotSpawnEvent spawnEvt =
                new me.bill.fakePlayerPlugin.api.event.FppBotSpawnEvent(
                    new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(fp), fp.isRestoredSpawn());
            Bukkit.getPluginManager().callEvent(spawnEvt);
          }
        },
        () -> {
          if (!activePlayers.containsKey(fp.getUuid())) return;
          if (Config.tabListEnabled()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
              PacketHelper.sendTabListDisplayNameUpdate(p, fp);
            }
          }
        });
  }

  public void spawnRestored(
      String name,
      UUID uuid,
      String savedDisplayName,
      String spawnedBy,
      UUID spawnedByUuid,
      Location location) {
    spawnRestored(name, uuid, savedDisplayName, spawnedBy, spawnedByUuid, location, BotType.AFK);
  }

  public void spawnRestored(
      String name,
      UUID uuid,
      String savedDisplayName,
      String spawnedBy,
      UUID spawnedByUuid,
      Location location,
      BotType botType) {

    if (usedNames.contains(name)) return;

    if (identityCache != null) identityCache.prime(name, uuid);

    PlayerProfile profile = Bukkit.createProfile(uuid, name);
    FakePlayer fp = new FakePlayer(uuid, name, profile);
    fp.setBotType(botType);
    fp.setRestoredSpawn(true);

    boolean isUserBot = name.startsWith("ubot_");
    if (isUserBot) {
      fp.setSkinName(pickRandomSkinName());
    } else {
      fp.setSkinName(name);
    }

    String effectiveSpawner = (spawnedBy != null && !spawnedBy.isBlank()) ? spawnedBy : "Unknown";
    String displayName;

    if (isUserBot) {
      int lastUs = name.lastIndexOf('_');
      int botIdx = 1;
      if (lastUs > 0 && lastUs < name.length() - 1) {
        try {
          botIdx = Integer.parseInt(name.substring(lastUs + 1));
        } catch (NumberFormatException ignored) {
          botIdx = 1;
        }
      }
      String rawName =
          Config.userBotNameFormat()
              .replace("{spawner}", effectiveSpawner)
              .replace("{num}", String.valueOf(botIdx))
              .replace("{bot_name}", name);

      Config.debug(
          "[Restore] user-bot '"
              + name
              + "' type="
              + botType
              + " spawner='"
              + effectiveSpawner
              + "' num="
              + botIdx);
      fp.setRawDisplayName(rawName);
      displayName = finalizeDisplayName(rawName, name);
    } else {
      String rawName = Config.adminBotNameFormat().replace("{bot_name}", name);
      Config.debug("[Restore] admin-bot '" + name + "' type=" + botType);
      fp.setRawDisplayName(rawName);
      displayName = finalizeDisplayName(rawName, name);
    }

    fp.setDisplayName(displayName);
    fp.setSpawnLocation(location);
    fp.setSpawnedBy(effectiveSpawner, spawnedByUuid);
    usedNames.add(name);
    activePlayers.put(uuid, fp);
    nameIndex.put(name.toLowerCase(), fp);

    if (db != null) {
      BotRecord record =
          new BotRecord(
              0,
              name,
              uuid,
              effectiveSpawner,
              spawnedByUuid,
              location.getWorld() != null ? location.getWorld().getName() : "unknown",
              location.getX(),
              location.getY(),
              location.getZ(),
              location.getYaw(),
              location.getPitch(),
              Instant.now(),
              null,
              null);
      fp.setDbRecord(record);
      String plainDisplay =
          net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
              .serialize(me.bill.fakePlayerPlugin.util.TextUtil.colorize(displayName));
      db.recordSpawn(record, plainDisplay);
    }

    finishSpawn(fp, location);
    Config.debug("Restored bot: " + name + " at " + location);
  }

  public void validateUserBotNames(UUID spawnerUuid, String spawnerName) {
    if (activePlayers.isEmpty()) return;
    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
    for (FakePlayer fp : activePlayers.values()) {
      if (!spawnerUuid.equals(fp.getSpawnedByUuid())) continue;
      if (!fp.getName().startsWith("ubot_")) continue;

      String current = fp.getDisplayName();

      if (!PLACEHOLDER_PATTERN.matcher(current).find()) continue;

      String botName = fp.getName();
      int lastUs = botName.lastIndexOf('_');
      int idx = 1;
      if (lastUs > 0 && lastUs < botName.length() - 1) {
        try {
          idx = Integer.parseInt(botName.substring(lastUs + 1));
        } catch (NumberFormatException ignored) {
          idx = 1;
        }
      }
      String rawDisplay =
          Config.userBotNameFormat()
              .replace("{spawner}", spawnerName)
              .replace("{num}", String.valueOf(idx))
              .replace("{bot_name}", fp.getName());
      fp.setRawDisplayName(rawDisplay);
      String newDisplay = finalizeDisplayName(rawDisplay, fp.getName());
      fp.setDisplayName(newDisplay);

      if (Config.tabListEnabled()) {
        for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
      }
      FppLogger.warn(
          "[FPP] Repaired placeholder name for bot '"
              + fp.getName()
              + "' (owner: "
              + spawnerName
              + ") → '"
              + newDisplay
              + "'");
    }
  }

  public void removeAll() {
    if (activePlayers.isEmpty()) return;

    List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());

    // Snapshot inventory + XP for ALL bots BEFORE clearing maps or removing entities.
    // This ensures /fpp despawn all preserves items just like single-bot despawn does.
    if (!Config.dropItemsOnDespawn()) {
      for (FakePlayer fp : toRemove) {
        if (renamingBotIds.contains(fp.getUuid())) continue;
        Player snapBody = fp.getPhysicsEntity();
        if (snapBody != null && snapBody.isOnline()) {
          String botName = fp.getName();
          DespawnSnapshot snap =
              new DespawnSnapshot(
                  cloneContents(snapBody.getInventory().getContents()),
                  cloneContents(snapBody.getInventory().getArmorContents()),
                  cloneContents(snapBody.getInventory().getExtraContents()),
                  snapBody.getTotalExperience(),
                  snapBody.getLevel(),
                  snapBody.getExp());
          despawnSnapshots.put(botName.toLowerCase(), snap);
          persistDespawnSnapshot(botName.toLowerCase(), snap);
          Config.debugSwap("[DespawnSnapshot] Saved inventory+XP for '" + botName + "' (bulk despawn).");
        }
      }
    }

    activePlayers.clear();
    usedNames.clear();
    entityIdIndex.clear();

    botHeadRotation.clear();
    botSpawnRotation.clear();

    long maxDelay = 0;

    for (int i = 0; i < toRemove.size(); i++) {
      FakePlayer fp = toRemove.get(i);

      long leaveDelayTicks = (long) i;
      maxDelay = Math.max(maxDelay, leaveDelayTicks);

      final FakePlayer target = fp;
      Runnable doVisualRemove =
          () -> {
            // Drop items if configured (same logic as single-bot despawn).
            if (Config.dropItemsOnDespawn()) {
              dropBotContents(target);
            }

            String despawnName = resolveDespawnDisplayName(target);
            despawningBotIds.put(target.getUuid(), despawnName);
            try {
              FakePlayerBody.removeAll(target);
            } finally {
              despawningBotIds.remove(target.getUuid());
            }
            if (chunkLoader != null) chunkLoader.releaseForBot(target);

            if (botTabTeam != null) botTabTeam.removeBot(target);

            List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

            if (Config.leaveMessage()) {
              var vc = plugin.getVelocityChannel();
              if (vc != null) vc.broadcastLeaveToNetwork(target.getDisplayName());
            }
            if (db != null) db.recordRemoval(target.getUuid(), "DELETED");

            var vc2 = plugin.getVelocityChannel();
            if (vc2 != null) vc2.broadcastBotDespawn(target.getUuid());
            Config.debug("Removed bot: " + target.getName());
          };

      if (leaveDelayTicks <= 0L) {
        Player body = target.getPlayer();
        if (body != null) FppScheduler.runAtEntity(plugin, body, doVisualRemove);
        else FppScheduler.runSync(plugin, doVisualRemove);
      } else {
        Player body = target.getPlayer();
        if (body != null) FppScheduler.runAtEntityLaterWithId(plugin, body, doVisualRemove, leaveDelayTicks);
        else FppScheduler.runSyncLater(plugin, doVisualRemove, leaveDelayTicks);
      }
    }

    final long saveDelay = maxDelay + 20L;
    FppScheduler.runSyncLater(
        plugin,
        () -> {
          if (persistence != null && Config.persistOnRestart()) {
            persistence.saveAsync(activePlayers.values());
          }
        },
        saveDelay);

    Config.debug("Staggered visual removal of " + toRemove.size() + " fake player(s).");
  }

  private static void dropBotContents(FakePlayer target) {
    Player bot = target.getPhysicsEntity();
    if (bot == null || !bot.isOnline()) return;

    Location loc = bot.getLocation();
    World world = loc.getWorld();
    if (world == null) return;

    for (ItemStack item : bot.getInventory().getContents()) {
      if (item != null && item.getType() != Material.AIR) {
        world.dropItemNaturally(loc, item);
      }
    }
    bot.getInventory().clear();

    int xp = bot.getTotalExperience();
    if (xp > 0) {
      world.spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));
      bot.setTotalExperience(0);
      bot.setLevel(0);
      bot.setExp(0f);
    }
  }

  private static ItemStack[] cloneContents(ItemStack[] contents) {
    if (contents == null) return new ItemStack[0];
    ItemStack[] clone = new ItemStack[contents.length];
    for (int i = 0; i < contents.length; i++) {
      if (contents[i] != null) clone[i] = contents[i].clone();
    }
    return clone;
  }

  private static void applyDespawnSnapshot(Player bot, DespawnSnapshot snap) {
    org.bukkit.inventory.PlayerInventory inv = bot.getInventory();
    if (snap.mainContents().length > 0) inv.setContents(snap.mainContents());
    if (snap.armorContents().length > 0) inv.setArmorContents(snap.armorContents());
    if (snap.extraContents().length > 0) inv.setExtraContents(snap.extraContents());
    bot.setTotalExperience(0);
    bot.setLevel(0);
    bot.setExp(0f);
    bot.setLevel(snap.xpLevel());
    bot.setExp(Math.max(0f, Math.min(1f, snap.xpProgress())));
    bot.setTotalExperience(snap.xpTotal());
  }

  // ── Despawn snapshot persistence ─────────────────────────────────────────

  /**
   * Called once from {@code FakePlayerPlugin.onEnable()} after the DB manager is wired in.
   * Loads any persisted despawn snapshots into the in-memory map so bots whose inventory was saved
   * before a restart can still be restored when they are spawned again.
   */
  public void initDespawnSnapshots() {
    despawnSnapshotFile =
        new java.io.File(plugin.getDataFolder(), "data" + java.io.File.separator + "despawn-snapshots.yml");

    // 1. Try DB first (primary store)
    if (db != null) {
      try {
        List<DatabaseManager.DespawnSnapshotRow> rows =
            db.loadDespawnSnapshotsForServer(me.bill.fakePlayerPlugin.config.Config.serverId());
        for (DatabaseManager.DespawnSnapshotRow row : rows) {
          DespawnSnapshot snap =
              deserializeSlots(row.inventoryData(), row.xpTotal(), row.xpLevel(), row.xpProgress());
          if (snap != null) despawnSnapshots.put(row.botName().toLowerCase(), snap);
        }
        if (!rows.isEmpty()) {
          Config.debugDatabase(
              "[DespawnSnapshot] Loaded " + rows.size() + " snapshot(s) from DB.");
        }
      } catch (Exception e) {
        FppLogger.warn("[DespawnSnapshot] Failed to load from DB: " + e.getMessage());
      }
      return; // DB is authoritative — skip YAML
    }

    // 2. YAML fallback
    if (!despawnSnapshotFile.exists()) return;
    try {
      org.bukkit.configuration.file.YamlConfiguration yaml =
          org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(despawnSnapshotFile);
      org.bukkit.configuration.ConfigurationSection sec = yaml.getConfigurationSection("snapshots");
      if (sec == null) return;
      for (String key : sec.getKeys(false)) {
        org.bukkit.configuration.ConfigurationSection entry = sec.getConfigurationSection(key);
        if (entry == null) continue;
        String invData = entry.getString("inventory-data", "");
        int xpTotal = entry.getInt("xp-total", 0);
        int xpLevel = entry.getInt("xp-level", 0);
        float xpProgress = (float) entry.getDouble("xp-progress", 0.0);
        DespawnSnapshot snap = deserializeSlots(invData, xpTotal, xpLevel, xpProgress);
        if (snap != null) despawnSnapshots.put(key.toLowerCase(), snap);
      }
      if (!sec.getKeys(false).isEmpty()) {
        Config.debugDatabase(
            "[DespawnSnapshot] Loaded " + sec.getKeys(false).size() + " snapshot(s) from YAML.");
      }
    } catch (Exception e) {
      FppLogger.warn("[DespawnSnapshot] Failed to load YAML: " + e.getMessage());
    }
  }

  /** Persists a snapshot to the DB (primary) or YAML fallback (async, best-effort). */
  private void persistDespawnSnapshot(String botNameLower, DespawnSnapshot snap) {
    String invData = serializeSlots(snap);
    String serverId = me.bill.fakePlayerPlugin.config.Config.serverId();

    if (db != null) {
      db.saveDespawnSnapshot(
          botNameLower, serverId, invData, snap.xpTotal(), snap.xpLevel(), snap.xpProgress());
      return;
    }

    // YAML fallback — write async
    final java.io.File yamlFile = despawnSnapshotFile;
    if (yamlFile == null) return;
    final String invDataFinal = invData;
    final int xpT = snap.xpTotal(), xpL = snap.xpLevel();
    final float xpP = snap.xpProgress();
    FppScheduler.runAsync(
        plugin,
        () -> {
          try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                yamlFile.exists()
                    ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(yamlFile)
                    : new org.bukkit.configuration.file.YamlConfiguration();
            String path = "snapshots." + botNameLower;
            yaml.set(path + ".inventory-data", invDataFinal);
            yaml.set(path + ".xp-total", xpT);
            yaml.set(path + ".xp-level", xpL);
            yaml.set(path + ".xp-progress", (double) xpP);
            yaml.set(path + ".saved-at", System.currentTimeMillis());
            yaml.save(yamlFile);
          } catch (Exception e) {
            FppLogger.warn("[DespawnSnapshot] YAML save failed: " + e.getMessage());
          }
        });
  }

  /** Removes a snapshot from DB/YAML after it has been restored (called on respawn). */
  private void removeDespawnSnapshotPersistent(String botNameLower) {
    String serverId = me.bill.fakePlayerPlugin.config.Config.serverId();
    if (db != null) {
      db.deleteDespawnSnapshot(botNameLower, serverId);
      return;
    }

    final java.io.File yamlFile = despawnSnapshotFile;
    if (yamlFile == null || !yamlFile.exists()) return;
    FppScheduler.runAsync(
        plugin,
        () -> {
          try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(yamlFile);
            yaml.set("snapshots." + botNameLower, null);
            yaml.save(yamlFile);
          } catch (Exception e) {
            FppLogger.warn("[DespawnSnapshot] YAML delete failed: " + e.getMessage());
          }
        });
  }

  /**
   * Serialises a {@link DespawnSnapshot} to a compact pipe-delimited string.
   * Format: {@code slot:base64|slot:base64|…} — uses only chars safe from splitting.
   * Uses {@code mainContents} (all 41 slots) which already contains armor and offhand.
   */
  private static String serializeSlots(DespawnSnapshot snap) {
    StringBuilder sb = new StringBuilder();
    ItemStack[] all = snap.mainContents();
    for (int i = 0; i < all.length; i++) {
      if (all[i] != null && all[i].getType() != Material.AIR) {
        try {
          String b64 = java.util.Base64.getEncoder().encodeToString(all[i].serializeAsBytes());
          if (sb.length() > 0) sb.append('|');
          sb.append(i).append(':').append(b64);
        } catch (Exception ignored) {}
      }
    }
    return sb.toString();
  }

  /**
   * Deserialises the pipe-delimited slot string back into a {@link DespawnSnapshot}.
   * Returns {@code null} if the data is blank or entirely corrupt.
   */
  private static DespawnSnapshot deserializeSlots(
      String data, int xpTotal, int xpLevel, float xpProgress) {
    ItemStack[] main = new ItemStack[41];
    if (data != null && !data.isBlank()) {
      for (String token : data.split("\\|")) {
        int colon = token.indexOf(':');
        if (colon < 1) continue;
        try {
          int slot = Integer.parseInt(token.substring(0, colon));
          if (slot < 0 || slot >= main.length) continue;
          byte[] bytes = java.util.Base64.getDecoder().decode(token.substring(colon + 1));
          main[slot] = ItemStack.deserializeBytes(bytes);
        } catch (Exception ignored) {}
      }
    }
    // Build armor (slots 36-39) and extra (slot 40) sub-arrays for applyDespawnSnapshot
    ItemStack[] armor = new ItemStack[]{main[36], main[37], main[38], main[39]};
    ItemStack[] extra = new ItemStack[]{main[40]};
    boolean hasContent = xpTotal > 0 || xpLevel > 0 || xpProgress > 0f;
    for (ItemStack item : main) {
      if (item != null && item.getType() != Material.AIR) { hasContent = true; break; }
    }
    if (!hasContent) return null;
    return new DespawnSnapshot(main, armor, extra, xpTotal, xpLevel, xpProgress);
  }

  public boolean delete(String name) {
    FakePlayer fp = getByName(name);
    if (fp == null) return false;

    // Fire API despawn event before any state is removed.
    var fppApi = plugin.getFppApi();
    if (fppApi != null) {
      me.bill.fakePlayerPlugin.api.event.FppBotDespawnEvent despawnEvt =
          new me.bill.fakePlayerPlugin.api.event.FppBotDespawnEvent(
              new me.bill.fakePlayerPlugin.api.impl.FppBotImpl(fp));
      Bukkit.getPluginManager().callEvent(despawnEvt);
    }

    final FakePlayer target = fp;
    final String botName = target.getName();

    // Snapshot inventory + XP BEFORE removing from maps or despawning entity.
    // Must happen synchronously here (not in the delayed task) so bulk operations
    // like /fpp despawn all don't race — by the time the 1-tick delay fires,
    // earlier bots' entities may already be gone and snapBody.isOnline() fails.
    if (!Config.dropItemsOnDespawn() && !renamingBotIds.contains(target.getUuid())) {
      Player snapBody = target.getPhysicsEntity();
      if (snapBody != null && snapBody.isOnline()) {
        DespawnSnapshot snap =
            new DespawnSnapshot(
                cloneContents(snapBody.getInventory().getContents()),
                cloneContents(snapBody.getInventory().getArmorContents()),
                cloneContents(snapBody.getInventory().getExtraContents()),
                snapBody.getTotalExperience(),
                snapBody.getLevel(),
                snapBody.getExp());
        despawnSnapshots.put(botName.toLowerCase(), snap);
        persistDespawnSnapshot(botName.toLowerCase(), snap);
        Config.debugSwap("[DespawnSnapshot] Saved inventory+XP for '" + botName + "'.");
      }
    }

    activePlayers.remove(target.getUuid());
    nameIndex.remove(botName.toLowerCase());
    usedNames.remove(botName);

    if (target.getPhysicsEntity() != null)
      entityIdIndex.remove(target.getPhysicsEntity().getEntityId());

    if (botSwapAI != null) botSwapAI.cancel(target.getUuid());

    botHeadRotation.remove(target.getUuid());
    botSpawnRotation.remove(target.getUuid());

    actionLockedBots.remove(target.getUuid());

    navLockedBots.remove(target.getUuid());

    trackedFallDistance.remove(target.getUuid());
    wasOnGround.remove(target.getUuid());
    target.clearMetadata();
    var pathfinding = plugin.getPathfindingService();
    if (pathfinding != null) pathfinding.cancel(target.getUuid());

    var moveCmd = plugin.getMoveCommand();
    if (moveCmd != null) moveCmd.cleanupBot(target.getUuid());
    var mineCmd = plugin.getMineCommand();
    if (mineCmd != null) {
      mineCmd.cleanupBot(target.getUuid());
      mineCmd.clearSelection(target.getUuid());
    }
    var placeCmd = plugin.getPlaceCommand();
    if (placeCmd != null) placeCmd.cleanupBot(target.getUuid());
    var useCmd = plugin.getUseCommand();
    if (useCmd != null) useCmd.stopUsing(target.getUuid());
    var followCmd = plugin.getFollowCommand();
    if (followCmd != null) followCmd.cleanupBot(target.getUuid());
    var sleepCmd = plugin.getSleepCommand();
    if (sleepCmd != null) sleepCmd.cleanupBot(target.getUuid());

    long leaveDelay = 1L;

    Runnable doVisualRemove =
        () -> {
          // Snapshot was already taken synchronously at the start of delete()
          // so we only need to drop items here if configured.
          if (Config.dropItemsOnDespawn()) {
            dropBotContents(target);
          }

          String despawnName = resolveDespawnDisplayName(target);
          despawningBotIds.put(target.getUuid(), despawnName);
          try {

            FakePlayerBody.removeAll(target);
          } finally {
            despawningBotIds.remove(target.getUuid());
          }
          if (chunkLoader != null) chunkLoader.releaseForBot(target);

          if (botTabTeam != null) botTabTeam.removeBot(target);

          List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
          for (Player online : snapshot) PacketHelper.sendTabListRemove(online, target);

          if (Config.leaveMessage() && !renamingBotIds.contains(target.getUuid())) {
            var vc = plugin.getVelocityChannel();
            if (vc != null) vc.broadcastLeaveToNetwork(target.getDisplayName());
          }
          if (db != null) db.recordRemoval(target.getUuid(), "DELETED");

          if (plugin.isLuckPermsAvailable()) {
            me.bill
                .fakePlayerPlugin
                .util
                .LuckPermsHelper
                .setPlayerGroup(target.getUuid(), "default")
                .thenRun(() -> Config.debug("Cleaned up LP data for bot: " + botName))
                .exceptionally(
                    throwable -> {
                      me.bill.fakePlayerPlugin.util.FppLogger.warn(
                          "Failed to cleanup LP data for bot "
                              + botName
                              + ": "
                              + throwable.getMessage());
                      return null;
                    });
          }

          var vc2 = plugin.getVelocityChannel();
          if (vc2 != null) vc2.broadcastBotDespawn(target.getUuid());
          Config.debug("Deleted fake player: " + botName);
          if (persistence != null && Config.persistOnRestart()) {
            persistence.saveAsync(activePlayers.values());
          }
        };

    Player body = target.getPlayer();
    if (body != null) FppScheduler.runAtEntityLaterWithId(plugin, body, doVisualRemove, leaveDelay);
    else FppScheduler.runSyncLater(plugin, doVisualRemove, leaveDelay);

    return true;
  }

  public void removeAllSync() {
    removeAllSync(false);
  }

  public void removeAllSyncFast() {
    removeAllSync(true);
  }

  private void removeAllSync(boolean fastShutdown) {
    if (activePlayers.isEmpty()) return;

    List<FakePlayer> toRemove = new ArrayList<>(activePlayers.values());
    activePlayers.clear();
    nameIndex.clear();
    usedNames.clear();
    entityIdIndex.clear();

    botHeadRotation.clear();
    botSpawnRotation.clear();

    List<Player> snapshot = fastShutdown ? List.of() : new ArrayList<>(Bukkit.getOnlinePlayers());

    for (FakePlayer fp : toRemove) {

      if (fastShutdown) FakePlayerBody.removeAllFast(fp);
      else FakePlayerBody.removeAll(fp);
      if (chunkLoader != null) chunkLoader.releaseForBot(fp);

      if (botTabTeam != null) botTabTeam.removeBot(fp);
      if (!fastShutdown) {
        for (Player online : snapshot) PacketHelper.sendTabListRemove(online, fp);
      }

      Config.debug("Shutdown removed bot: " + fp.getName());
    }

    FppLogger.info("Shutdown: removed " + toRemove.size() + " bot(s)." + (fastShutdown ? " (fast)" : ""));
  }

  private void tickAutoEat(Player bot) {
    if (bot.getGameMode() == GameMode.CREATIVE || bot.getGameMode() == GameMode.SPECTATOR) return;
    if (bot.getFoodLevel() > 14 && (bot.getFoodLevel() >= 6 || bot.isSprinting())) return;
    var inv = bot.getInventory();
    for (int slot = 0; slot < inv.getSize(); slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getAmount() <= 0) continue;
      int food = foodValue(item.getType());
      if (food <= 0) continue;
      item.setAmount(item.getAmount() - 1);
      if (item.getAmount() <= 0) inv.setItem(slot, null);
      else inv.setItem(slot, item);
      bot.setFoodLevel(Math.min(20, bot.getFoodLevel() + food));
      bot.setSaturation(Math.min(20f, bot.getSaturation() + Math.max(1f, food * 0.6f)));
      bot.swingMainHand();
      return;
    }
  }

  private int foodValue(Material type) {
    return switch (type) {
      case COOKED_BEEF, COOKED_PORKCHOP -> 8;
      case GOLDEN_CARROT, BAKED_POTATO, COOKED_CHICKEN, MUSHROOM_STEW, BEETROOT_SOUP, RABBIT_STEW -> 6;
      case BREAD, COOKED_COD, COOKED_RABBIT -> 5;
      case APPLE, CARROT, COOKED_SALMON, CHORUS_FRUIT -> 4;
      case POTATO, BEETROOT, MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES -> 2;
      case COOKIE, DRIED_KELP -> 1;
      default -> 0;
    };
  }

  public void applyBodyConfig() {
    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

    if (physicalBodiesEnabled()) {

      for (FakePlayer fp : new ArrayList<>(activePlayers.values())) {
        Player body = fp.getPlayer();

        if (body != null && body.isValid()) {

          body.setInvulnerable(false);
          body.setCollidable(Config.bodyPushable());
          try {
            var attr = body.getAttribute(me.bill.fakePlayerPlugin.util.AttributeCompat.MAX_HEALTH);
            if (attr != null) {
              double hp = Config.maxHealth();
              attr.setBaseValue(hp);
              if (body.getHealth() > hp) body.setHealth(hp);
            }
          } catch (Exception ignored) {
          }

        } else if (fp.isBodyless()) {

          Location loc = fp.getSpawnLocation();
          if (loc == null || loc.getWorld() == null) continue;

          bodyTransitionBots.add(fp.getUuid());
          fp.setBodyless(false);
          try {
            FakePlayerBody.resolveAndFinish(
                plugin,
                fp,
                loc,
                () -> {
                  Player newBody = FakePlayerBody.spawn(fp, loc);
                  if (newBody != null) {
                    fp.setPhysicsEntity(newBody);
                    entityIdIndex.put(newBody.getEntityId(), fp);
                    fp.setPacketProfileName(fp.getName());

                    if (Config.tabListEnabled()) {
                      for (Player p : online) {
                        PacketHelper.sendTabListRemove(p, fp);
                        PacketHelper.sendTabListAdd(p, fp);
                      }
                    }

                    me.bill.fakePlayerPlugin.listener.PlayerJoinListener.stampFirstPlayed(newBody);
                    FppLogger.info("BodyConfig: body shown for '" + fp.getName() + "'");
                  } else {
                    fp.setBodyless(true);
                    FppLogger.warn("BodyConfig: failed to show body for '" + fp.getName() + "'");
                  }
                });
          } finally {
            bodyTransitionBots.remove(fp.getUuid());
          }
        }
      }

      var btt = plugin.getBotTabTeam();
      if (btt != null) btt.applyCollisionRule(Config.bodyPushable());
      return;
    }

    for (FakePlayer fp : new ArrayList<>(activePlayers.values())) {
      Player body = fp.getPlayer();
      if (body == null || !body.isOnline()) continue;

      fp.setSpawnLocation(body.getLocation());
      int entityId = body.getEntityId();

      entityIdIndex.remove(entityId);
      fp.setPhysicsEntity(null);
      fp.setBodyless(true);

      bodyTransitionBots.add(fp.getUuid());
      FppScheduler.runAtEntity(
          plugin,
          body,
          () -> {
            try {
              NmsPlayerSpawner.removeFakePlayer(body);
            } finally {
              bodyTransitionBots.remove(fp.getUuid());
            }

            if (Config.tabListEnabled()) {
              for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
            }
            FppLogger.info("BodyConfig: body hidden for '" + fp.getName() + "', now tab-list only");
          });
    }
  }

  public void removeByName(String name) {
    activePlayers
        .values()
        .removeIf(
            fp -> {
              if (!fp.getName().equals(name)) return false;
              nameIndex.remove(fp.getName().toLowerCase());
              usedNames.remove(fp.getName());
              botHeadRotation.remove(fp.getUuid());
              botSpawnRotation.remove(fp.getUuid());
              actionLockedBots.remove(fp.getUuid());
              navLockedBots.remove(fp.getUuid());
              if (db != null) db.recordRemoval(fp.getUuid(), "DIED");
              Config.debug("Removed from registry: " + name);
              return true;
            });
    if (persistence != null && Config.persistOnRestart()) {
      persistence.saveAsync(activePlayers.values());
    }
  }

  public void syncToPlayer(Player player) {
    if (!Config.tabListEnabled()) {

      for (FakePlayer fp : activePlayers.values()) {
        PacketHelper.sendTabListUpdateListed(player, fp, false);
      }
      return;
    }
    for (FakePlayer fp : activePlayers.values()) {

      boolean isNmsPlayer = fp.getPlayer() != null;

      if (isNmsPlayer) {

        PacketHelper.sendTabListDisplayNameUpdate(player, fp);
      } else {

        PacketHelper.sendTabListAdd(player, fp);
      }

      if (fp.hasCustomPing()) {
        PacketHelper.sendTabListLatencyUpdate(player, fp);
      }
    }
  }

  public void applyTabListConfig() {
    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (online.isEmpty() || activePlayers.isEmpty()) return;

    boolean listed = Config.tabListEnabled();

    for (FakePlayer fp : activePlayers.values()) {
      Player bot = fp.getPlayer();
      if (bot != null) {

        boolean nmsOk = NmsPlayerSpawner.setListed(bot, listed);
        if (!nmsOk) {

          for (Player p : online) {
            PacketHelper.sendTabListUpdateListed(p, fp, listed);
          }
        }
      }
    }

    if (listed) {

      for (FakePlayer fp : activePlayers.values()) {
        for (Player p : online) {
          PacketHelper.sendTabListRemove(p, fp);
          PacketHelper.sendTabListAdd(p, fp);
        }
      }
      if (botTabTeam != null) botTabTeam.rebuild(activePlayers.values());
      Config.debug("applyTabListConfig: re-added " + activePlayers.size() + " bots to tab list.");
    } else {

      for (FakePlayer fp : activePlayers.values()) {
        for (Player p : online) {
          PacketHelper.sendTabListUpdateListed(p, fp, false);
        }
      }

      if (botTabTeam != null) botTabTeam.clearAll();
      Config.debug("applyTabListConfig: unlisted " + activePlayers.size() + " bots from tab list.");
    }
  }

  public void validateEntities() {

    java.util.Set<String> activeNames =
        activePlayers.values().stream()
            .map(FakePlayer::getName)
            .collect(java.util.stream.Collectors.toSet());

    for (FakePlayer fp : activePlayers.values()) {
      Entity body = fp.getPhysicsEntity();

      if (!physicalBodiesEnabled()) {
        if (body != null && body.isValid()) {
          try {
            body.remove();
          } catch (Exception ignored) {
          }
          entityIdIndex.remove(body.getEntityId());
          fp.setPhysicsEntity(null);
        }
        continue;
      }

      if (fp.isBodyless()) continue;

      if (body != null && body.isValid()) continue;

      Config.debug(
          "validateEntities: body of '" + fp.getName() + "' invalid - attempting respawn.");

      fp.setPhysicsEntity(null);

      org.bukkit.Location loc = fp.getSpawnLocation();
      if (loc == null || loc.getWorld() == null) continue;

      FakePlayerBody.resolveAndFinish(
          plugin,
          fp,
          loc,
          () -> {
            Player newBody = FakePlayerBody.spawn(fp, loc);
            if (newBody == null) return;

            fp.setPhysicsEntity(newBody);
            entityIdIndex.put(newBody.getEntityId(), fp);

            final FakePlayer target = fp;
            FppScheduler.runSyncLater(
                plugin,
                () -> {
                  for (Player p : Bukkit.getOnlinePlayers()) PacketHelper.sendTabListAdd(p, target);
                },
                2L);
          });
    }
  }

  public FakePlayer getByEntity(Entity entity) {

    FakePlayer fp = entityIdIndex.get(entity.getEntityId());
    if (fp != null) return fp;

    if (FAKE_PLAYER_KEY == null) return null;
    String botName =
        entity
            .getPersistentDataContainer()
            .get(FAKE_PLAYER_KEY, org.bukkit.persistence.PersistentDataType.STRING);
    if (botName == null || botName.isBlank()) return null;

    FakePlayer candidate = getByName(botName);
    if (candidate == null) return null;

    Entity oldBody = candidate.getPhysicsEntity();
    if (oldBody != null && oldBody.getEntityId() != entity.getEntityId()) {
      entityIdIndex.remove(oldBody.getEntityId());
    }

    if (entity instanceof org.bukkit.entity.Player player) {
      candidate.setPhysicsEntity(player);
      entityIdIndex.put(entity.getEntityId(), candidate);
      Config.debug(
          "getByEntity: recovered '"
              + botName
              + "' via PDC after world-change - new entityId="
              + entity.getEntityId());
      return candidate;
    }

    Config.debug("getByEntity: entity is not a Player, cannot recover bot: " + botName);
    return null;
  }

  public FakePlayer getByUuid(UUID uuid) {
    if (uuid == null) return null;
    return activePlayers.get(uuid);
  }

  public void removeFromEntityIndex(int entityId) {
    entityIdIndex.remove(entityId);
  }

  public void interruptNavigationOwner(UUID botUuid, PathfindingService.Owner owner) {
    if (botUuid == null || owner == null) return;
    switch (owner) {
      case MOVE -> {
        var cmd = plugin.getMoveCommand();
        if (cmd != null) cmd.cleanupBot(botUuid);
      }
      case MINE -> {
        var cmd = plugin.getMineCommand();
        if (cmd != null) cmd.stopMining(botUuid);
      }
      case PLACE -> {
        var cmd = plugin.getPlaceCommand();
        if (cmd != null) cmd.stopPlacing(botUuid);
      }
      case USE -> {
        var cmd = plugin.getUseCommand();
        if (cmd != null) cmd.stopUsing(botUuid);
      }
      case ATTACK -> {
        var cmd = plugin.getAttackCommand();
        if (cmd != null) cmd.stopAttacking(botUuid);
      }
      case FOLLOW -> {
        var cmd = plugin.getFollowCommand();
        if (cmd != null) cmd.stopFollowing(botUuid);
      }
      case SLEEP -> {
        var cmd = plugin.getSleepCommand();
        if (cmd != null) cmd.cleanupBot(botUuid);
      }
      case SYSTEM -> {
        var pathfinding = plugin.getPathfindingService();
        if (pathfinding != null) pathfinding.cancel(botUuid);
      }
    }
  }

  public void registerEntityIndex(int entityId, FakePlayer fp) {
    entityIdIndex.put(entityId, fp);
  }

  public FakePlayer getByEntityId(int entityId) {
    return entityIdIndex.get(entityId);
  }

  public List<String> getActiveNames() {
    return activePlayers.values().stream().map(FakePlayer::getName).collect(Collectors.toList());
  }

  public boolean isNameUsed(String name) {
    return usedNames.contains(name);
  }

  public Set<UUID> getActiveUUIDs() {
    return Collections.unmodifiableSet(new HashSet<>(activePlayers.keySet()));
  }

  public FakePlayer getByName(String name) {
    if (name == null || name.isBlank()) return null;
    return nameIndex.get(name.toLowerCase());
  }

  public void renameBot(FakePlayer bot, String newName) {
    String oldDisplay = bot.getDisplayName();
    if (oldDisplay.equalsIgnoreCase(newName)) return;

    bot.setDisplayName(newName);
    bot.setRawDisplayName(newName);

    if (Config.tabListEnabled() && bot.getPlayer() != null) {
      for (Player p : Bukkit.getOnlinePlayers()) {
        PacketHelper.sendTabListRemove(p, bot);
        PacketHelper.sendTabListAdd(p, bot);
      }
    }
  }

  public boolean isOnCooldown(UUID playerUuid) {
    int secs = Config.spawnCooldown();
    if (secs <= 0) return false;
    Long last = spawnCooldowns.get(playerUuid);
    if (last == null) return false;
    return (System.currentTimeMillis() - last) / 1000L < secs;
  }

  public void requestNavJump(UUID botUuid) {
    navJumpHolding.put(botUuid, 5);
  }

  public void clearNavJump(UUID botUuid) {
    navJumpHolding.remove(botUuid);
  }

  public Integer getNavJumpHolding(UUID botUuid) {
    return navJumpHolding.get(botUuid);
  }

  public void setNavJumpHolding(UUID botUuid, int value) {
    navJumpHolding.put(botUuid, value);
  }

  public void lockForAction(UUID botUuid, Location loc) {
    actionLockedBots.put(botUuid, loc.clone());

    botHeadRotation.put(botUuid, new float[] {loc.getYaw(), loc.getPitch()});
    botSpawnRotation.put(botUuid, new float[] {loc.getYaw(), loc.getPitch()});
  }

  public void unlockAction(UUID botUuid) {
    actionLockedBots.remove(botUuid);
  }

  public boolean isActionLocked(UUID botUuid) {
    return actionLockedBots.containsKey(botUuid);
  }

  public void updateActionLockRotation(UUID botUuid, float yaw, float pitch) {
    Location loc = actionLockedBots.get(botUuid);
    if (loc != null) {
      loc.setYaw(yaw);
      loc.setPitch(pitch);
    }
  }

  public void lockForNavigation(UUID botUuid) {
    navLockedBots.add(botUuid);
  }

  public void unlockNavigation(UUID botUuid) {
    navLockedBots.remove(botUuid);
  }

  public boolean isNavigationLocked(UUID botUuid) {
    return navLockedBots.contains(botUuid);
  }

  public long getRemainingCooldown(UUID playerUuid) {
    int secs = Config.spawnCooldown();
    if (secs <= 0) return 0;
    Long last = spawnCooldowns.get(playerUuid);
    if (last == null) return 0;
    long elapsed = (System.currentTimeMillis() - last) / 1000L;
    return Math.max(0, secs - elapsed);
  }

  public void recordSpawnCooldown(UUID playerUuid) {
    spawnCooldowns.put(playerUuid, System.currentTimeMillis());
  }

  public void clearCooldown(UUID playerUuid) {
    spawnCooldowns.remove(playerUuid);
  }

  public Collection<FakePlayer> getActivePlayers() {
    return Collections.unmodifiableCollection(activePlayers.values());
  }

  public int getCount() {
    return activePlayers.size();
  }

  public List<FakePlayer> getBotsOwnedBy(java.util.UUID ownerUuid) {
    return activePlayers.values().stream()
        .filter(fp -> ownerUuid.equals(fp.getSpawnedByUuid()))
        .collect(Collectors.toList());
  }

  public boolean teleportBot(FakePlayer fp, org.bukkit.Location destination) {
    Player body = fp.getPlayer();
    if (body == null || !body.isValid()) return false;
    var event = new FppBotTeleportEvent(
        new FppBotImpl(fp), body.getLocation(), destination);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) return false;
    FppScheduler.teleportAsync(body, event.getTo());
    fp.setSpawnLocation(event.getTo().clone());
    return true;
  }

  private String finalizeDisplayName(String rawName, String botName) {
    String display = rawName;

    if (display.contains("%")) {
      try {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
          display = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, display);
        }
      } catch (Exception ignored) {
      }
    }

    return sanitizeDisplayName(display, botName);
  }

  public void refreshLpDisplayName(FakePlayer fp) {
    if (!activePlayers.containsKey(fp.getUuid())) return;
    if (!plugin.isLuckPermsAvailable()) return;

    String rawContent = fp.getRawDisplayName();
    if (rawContent == null || rawContent.isBlank()) rawContent = fp.getName();

    String display = rawContent;

    if (display.contains("%")) {
      try {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
          display = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, display);
        }
      } catch (Exception ignored) {
      }
    }

    display = sanitizeDisplayName(display, fp.getName());

    if (display == null || display.isBlank()) {
      display = fp.getName();
      Config.debug(
          "[LP] Display name was blank after sanitise for '"
              + fp.getName()
              + "' — falling back to raw bot name.");
    }
    fp.setDisplayName(display);

    Player body = fp.getPlayer();
    if (body != null && body.isValid()) {
      try {
        body.displayName(me.bill.fakePlayerPlugin.util.TextUtil.colorize(rawContent));
      } catch (Exception ignored) {
      }
    }

    if (Config.tabListEnabled()) {
      List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
      for (Player p : online) PacketHelper.sendTabListDisplayNameUpdate(p, fp);
    }

    if (Config.isNetworkMode()) {
      var vc = plugin.getVelocityChannel();
      if (vc != null) vc.broadcastBotDisplayNameUpdate(fp);
    }

    Config.debug("[LP] Refreshed display name for '" + fp.getName() + "': '" + display + "'");
  }

  private void refreshLpDisplayNameWithRetry(FakePlayer fp, int attempt) {
    if (!activePlayers.containsKey(fp.getUuid())) return;
    if (!plugin.isLuckPermsAvailable()) return;

    String prefix = me.bill.fakePlayerPlugin.util.LuckPermsHelper.getResolvedPrefix(fp.getUuid());
    String suffix = me.bill.fakePlayerPlugin.util.LuckPermsHelper.getResolvedSuffix(fp.getUuid());

    if (prefix.isEmpty() && suffix.isEmpty() && attempt < 2) {
      Config.debug(
          "[LP] No prefix/suffix for '"
              + fp.getName()
              + "' on attempt "
              + (attempt + 1)
              + ", forcing LP refresh...");
      me.bill.fakePlayerPlugin.util.LuckPermsHelper.refreshUserCache(fp.getUuid());

      UUID botUuid = fp.getUuid();
      FppScheduler.runSyncLater(
          plugin,
          () -> {
            if (!activePlayers.containsKey(botUuid)) return;
            refreshLpDisplayNameWithRetry(fp, attempt + 1);
          },
          10L);
      return;
    }

    refreshLpDisplayName(fp);
  }

  private static final java.util.regex.Pattern PLACEHOLDER_PATTERN =
      java.util.regex.Pattern.compile("\\{[a-zA-Z_][a-zA-Z0-9_]*\\}");

  private String sanitizeDisplayName(String displayName, String context) {
    if (displayName == null || !displayName.contains("{")) {

      return (displayName == null || displayName.isBlank()) ? context : displayName;
    }
    java.util.regex.Matcher m = PLACEHOLDER_PATTERN.matcher(displayName);
    if (!m.find()) {
      return displayName.isBlank() ? context : displayName;
    }
    String fallback = pickRandomSkinName();
    String sanitized = PLACEHOLDER_PATTERN.matcher(displayName).replaceAll(fallback);
    FppLogger.warn(
        "Unreplaced placeholder(s) in display name for '"
            + context
            + "': '"
            + displayName
            + "' - replaced with '"
            + fallback
            + "'. Check bot-name.user-format / bot-name.admin-format in config.yml.");

    return sanitized.isBlank() ? context : sanitized;
  }

  public void applyPing(FakePlayer fp, int pingMs) {
    fp.setPing(pingMs);
    me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner.setPing(fp.getPlayer(), pingMs);
    if (Config.tabListEnabled()) {
      for (Player p : cachedOnlinePlayers) {
        PacketHelper.sendTabListLatencyUpdate(p, fp);
      }
    }
  }

  private String resolveDespawnDisplayName(FakePlayer fp) {
    if (plugin.isNameTagAvailable()) {
      try {
        String freshNick = me.bill.fakePlayerPlugin.util.NameTagHelper.getNick(fp.getUuid());
        if (freshNick != null && !freshNick.isEmpty()) {
          return freshNick;
        }
      } catch (Throwable ignored) {
      }
    }
    if (fp.getNameTagNick() != null && !fp.getNameTagNick().isEmpty()) {
      return fp.getNameTagNick();
    }
    return fp.getDisplayName();
  }

  private String pickRandomSkinName() {
    String skinMode = Config.skinMode();
    if (skinMode != null) {
      String normalized = skinMode.trim().toLowerCase();
      if ("player".equals(normalized) || "auto".equals(normalized)) {

        return SkinManager.pickRandomPoolName();
      }
    }

    List<String> pool = Config.namePool();
    if (pool.isEmpty()) return fallbackName();
    return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
  }

  private UUID resolveUuid(String botName) {
    if (identityCache != null) return identityCache.lookupOrCreate(botName);
    return UUID.randomUUID();
  }

  private String generateName() {
    return generateName(false);
  }

  private String generateName(boolean forceRandom) {
    if (forceRandom || "random".equals(Config.botNameMode())) {
      return generateRandomName();
    }

    List<String> pool = cleanNamePool;
    if (pool.isEmpty()) return fallbackName();

    String chosen = null;
    int count = 0;
    for (String n : pool) {

      if (n == null || n.isEmpty() || n.length() > 16 || !n.matches("[a-zA-Z0-9_]+")) continue;
      if (usedNames.contains(n) || Bukkit.getPlayerExact(n) != null) continue;
      count++;
      if (ThreadLocalRandom.current().nextInt(count) == 0) chosen = n;
    }
    if (chosen != null) {
      usedNames.add(chosen);
      return chosen;
    }
    return fallbackName();
  }

  private String generateRandomName() {
    String generated;
    int attempts = 0;
    do {
      generated = RandomNameGenerator.generate();
      if (++attempts > 200) return fallbackName();
    } while (generated == null || usedNames.contains(generated) || Bukkit.getPlayerExact(generated) != null);
    usedNames.add(generated);
    return generated;
  }

  private String fallbackName() {
    String generated;
    int attempts = 0;
    do {
      generated = "Bot" + ThreadLocalRandom.current().nextInt(1000, 9999);
      if (++attempts > 200) return null;
    } while (usedNames.contains(generated) || Bukkit.getPlayerExact(generated) != null);
    usedNames.add(generated);
    return generated;
  }

  public record UserBotName(String internalName, String displayName) {}

  public UserBotName generateUserBotName(String spawnerName, int existingCount) {
    String suffix = String.valueOf(existingCount + 1);
    final String PREFIX = "ubot_";
    final String SEP = "_";
    int maxSpawnerLen = 16 - PREFIX.length() - SEP.length() - suffix.length();
    String truncated =
        spawnerName.length() > maxSpawnerLen
            ? spawnerName.substring(0, Math.max(1, maxSpawnerLen))
            : spawnerName;
    String internal = PREFIX + truncated + SEP + suffix;
    if (internal.length() > 16) internal = internal.substring(0, 16);
    usedNames.add(internal);

    String display =
        sanitizeDisplayName(
            Config.userBotNameFormat()
                .replace("{spawner}", spawnerName)
                .replace("{num}", suffix)
                .replace("{bot_name}", internal),
            internal);
    return new UserBotName(internal, display);
  }

  public void updateAllBotPrefixes() {

    Config.debug("updateAllBotPrefixes: skipped (bots are real players, LP handles natively)");
  }

  private static boolean hasLineOfSightIgnoringGlass(Player from, Player to) {
    Location start = from.getEyeLocation();
    Location end = to.getEyeLocation();
    if (start.getWorld() == null || !start.getWorld().equals(end.getWorld())) return false;

    org.bukkit.util.Vector dir = end.toVector().subtract(start.toVector());
    double distance = dir.length();
    if (distance < 1e-6) return true;
    dir.normalize();

    try {
      BlockIterator iter =
          new BlockIterator(
              start.getWorld(), start.toVector(), dir, 0.0, (int) Math.ceil(distance) + 1);
      while (iter.hasNext()) {
        org.bukkit.block.Block block = iter.next();
        Material type = block.getType();
        if (!type.isSolid()) continue;
        if (isGlassLike(type)) continue;

        double blockDistSq = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(start);
        if (blockDistSq < distance * distance) return false;
      }
    } catch (Exception e) {
      return from.hasLineOfSight(to);
    }
    return true;
  }

  private static boolean isGlassLike(Material mat) {
    return mat.name().contains("GLASS");
  }

  private static float lerpAngle(float from, float to, float t) {
    float diff = to - from;
    while (diff > 180f) diff -= 360f;
    while (diff < -180f) diff += 360f;
    return from + diff * t;
  }

  public void persistBotSettings(FakePlayer fp) {
    DatabaseManager db = plugin.getDatabaseManager();
    if (db == null || fp == null) return;
    db.updateBotAllSettings(
        fp.getUuid().toString(),
        fp.isFrozen(),
        fp.isChatEnabled(),
        fp.getChatTier(),
        fp.getRightClickCommand(),
        fp.getAiPersonality(),
        fp.isPickUpItemsEnabled(),
        fp.isPickUpXpEnabled(),
        fp.isHeadAiEnabled(),
        fp.isNavParkour(),
        fp.isNavBreakBlocks(),
        fp.isNavPlaceBlocks(),
        fp.isNavAvoidWater(),
        fp.isNavAvoidLava(),
        fp.isSwimAiEnabled(),
        fp.getChunkLoadRadius(),
        fp.getPing(),
        fp.isPveEnabled(),
        fp.getPveRange(),
        fp.getPvePriority(),
        fp.getPveMobType(),
        fp.getPveSmartAttackMode().name());
  }
}

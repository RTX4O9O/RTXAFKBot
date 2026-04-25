package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.BotRecord;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public final class FakePlayer {

  private final String name;
  private final PlayerProfile profile;
  private final UUID uuid;

  private Location spawnLocation;

  private Player player;

  private String spawnedBy = "UNKNOWN";
  private UUID spawnedByUuid = new UUID(0, 0);
  private BotRecord dbRecord;
  private Instant spawnTime = Instant.now();

  private String displayName = null;

  private String rawDisplayName = null;

  private String skinName = null;

  private SkinProfile resolvedSkin = null;

  private double totalDamageTaken = 0.0;

  private int deathCount = 0;

  private int lastChunkX = Integer.MIN_VALUE;
  private int lastChunkZ = Integer.MIN_VALUE;

  private Entity nametagEntity = null;

  private String packetProfileName = null;

  private boolean alive = true;

  private boolean respawning = false;

  private int tabRefreshCount = 0;

  private transient volatile Object cachedNmsDisplayComponent;

  private transient volatile String cachedNmsDisplaySource;

  private transient volatile Object cachedTabListGameProfile;

  private boolean frozen = false;

  private String lastKnownWorld = null;

  private boolean bodyless = false;

  /** True when this bot was spawned via {@code spawnRestored} (server restart persistence). */
  private boolean restoredSpawn = false;

  private String luckpermsGroup = null;

  private BotType botType = BotType.AFK;

  private boolean chatEnabled = true;

  private String chatTier = null;

  private String aiPersonality = null;

  private String rightClickCommand = null;

  private boolean headAiEnabled = true;

  private boolean pickUpItemsEnabled = Config.bodyPickUpItems();

  private boolean pickUpXpEnabled = Config.bodyPickUpXp();

  private boolean navParkour = Config.pathfindingParkour();

  private boolean navBreakBlocks = Config.pathfindingBreakBlocks();

  private boolean navPlaceBlocks = Config.pathfindingPlaceBlocks();

  private boolean navAvoidWater = false;

  private boolean navAvoidLava = false;

  private boolean navSprintJump = Config.pathfindingSprintJump();

  private boolean swimAiEnabled = Config.swimAiEnabled();

  private int chunkLoadRadius = -1;

  private boolean pveEnabled = false;
  private double pveRange = Config.attackMobDefaultRange();
  private String pvePriority = Config.attackMobDefaultPriority();
  private boolean pveMoveToTarget = false;
  private Set<String> pveMobTypes = new LinkedHashSet<>();
  private final Set<UUID> sharedControllers = ConcurrentHashMap.newKeySet();
  private boolean autoEatEnabled = Config.autoEatEnabled();
  private boolean autoPlaceBedEnabled = Config.autoPlaceBedEnabled();

  private volatile String nameTagNick = null;

  private int ping = -1;

  private volatile boolean tabListDirty = true;

  /** Addon-attached metadata — transient, cleared on despawn. */
  private final java.util.Map<String, Object> metadata = new java.util.concurrent.ConcurrentHashMap<>();

  // ── Sleep system ──────────────────────────────────────────────────────────
  /** Station location used as the center for bed searching. null = not configured. */
  private Location sleepOrigin = null;
  /** Bed-search radius in blocks. 0 = sleep disabled for this bot. */
  private double sleepRadius = 0.0;
  /** True while the bot is currently in the sleeping state. */
  private boolean sleeping = false;

  public FakePlayer(UUID uuid, String name, PlayerProfile profile) {
    this.uuid = uuid;
    this.name = name;
    this.profile = profile;
  }

  public UUID getUuid() {
    return uuid;
  }

  public String getName() {
    return name;
  }

  public PlayerProfile getProfile() {
    return profile;
  }

  public Location getSpawnLocation() {
    return spawnLocation;
  }

  public org.bukkit.entity.Player getPhysicsEntity() {
    return player;
  }

  public org.bukkit.entity.Player getPlayer() {
    return player;
  }

  public int getEntityId() {
    return player != null ? player.getEntityId() : -1;
  }

  public String getDisplayName() {
    return displayName != null ? displayName : name;
  }

  public String getSkinName() {
    return skinName != null ? skinName : name;
  }

  public Location getLiveLocation() {
    if (player != null && player.isOnline() && !player.isDead()) return player.getLocation();
    return spawnLocation;
  }

  public Duration getUptime() {
    return Duration.between(spawnTime, Instant.now());
  }

  public String getUptimeFormatted() {
    Duration d = getUptime();
    long h = d.toHours();
    long m = d.toMinutesPart();
    long s = d.toSecondsPart();
    if (h > 0) return h + "h " + m + "m " + s + "s";
    if (m > 0) return m + "m " + s + "s";
    return s + "s";
  }

  public double getTotalDamageTaken() {
    return totalDamageTaken;
  }

  public int getDeathCount() {
    return deathCount;
  }

  public boolean isAlive() {
    return alive;
  }

  public int getTabRefreshCount() {
    return tabRefreshCount;
  }

  public boolean isFrozen() {
    return frozen;
  }

  public String getLastKnownWorld() {
    return lastKnownWorld;
  }

  public void addDamageTaken(double amount) {
    totalDamageTaken += amount;
  }

  public void incrementDeathCount() {
    deathCount++;
  }

  public void setAlive(boolean alive) {
    this.alive = alive;
  }

  public void incrementTabRefresh() {
    tabRefreshCount++;
  }

  public void setFrozen(boolean frozen) {
    this.frozen = frozen;
  }

  public Object getCachedNmsDisplayComponent() {
    return cachedNmsDisplayComponent;
  }

  public String getCachedNmsDisplaySource() {
    return cachedNmsDisplaySource;
  }

  public void setCachedNmsDisplay(Object comp, String source) {
    this.cachedNmsDisplayComponent = comp;
    this.cachedNmsDisplaySource = source;
  }

  public Object getCachedTabListGameProfile() {
    return cachedTabListGameProfile;
  }

  public void setCachedTabListGameProfile(Object profile) {
    this.cachedTabListGameProfile = profile;
  }

  public int getLastChunkX() {
    return lastChunkX;
  }

  public int getLastChunkZ() {
    return lastChunkZ;
  }

  public void setLastChunk(int cx, int cz) {
    lastChunkX = cx;
    lastChunkZ = cz;
  }

  public boolean hasMovedChunk(int cx, int cz) {
    return cx != lastChunkX || cz != lastChunkZ;
  }

  public void setSpawnLocation(Location loc) {
    this.spawnLocation = loc;
  }

  public void setPlayer(org.bukkit.entity.Player p) {
    this.player = p;
  }

  public void setPhysicsEntity(Entity e) {
    this.player = e instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player) e : null;
  }

  public void setDisplayName(String name) {
    this.displayName = name;
    this.tabListDirty = true;
    this.cachedNmsDisplayComponent = null;
    this.cachedNmsDisplaySource = null;
  }

  public String getRawDisplayName() {
    return rawDisplayName;
  }

  public void setRawDisplayName(String name) {
    this.rawDisplayName = name;
  }

  public String getLuckpermsGroup() {
    return luckpermsGroup;
  }

  public void setLuckpermsGroup(String group) {
    this.luckpermsGroup = group;
  }

  public BotType getBotType() {
    return botType != null ? botType : BotType.AFK;
  }

  public void setBotType(BotType type) {
    this.botType = type;
  }

  public boolean isRespawning() {
    return respawning;
  }

  public void setRespawning(boolean v) {
    this.respawning = v;
  }

  public boolean isChatEnabled() {
    return chatEnabled;
  }

  public void setChatEnabled(boolean v) {
    this.chatEnabled = v;
  }

  public String getChatTier() {
    return chatTier;
  }

  public void setChatTier(String tier) {
    this.chatTier = tier;
  }

  public String getAiPersonality() {
    return aiPersonality;
  }

  public void setAiPersonality(String personality) {
    this.aiPersonality = personality;
  }

  public void setSkinName(String name) {
    this.skinName = name;
  }

  public void setResolvedSkin(SkinProfile skin) {
    this.resolvedSkin = skin;
    this.cachedTabListGameProfile = null;
  }

  public String getRightClickCommand() {
    return rightClickCommand;
  }

  public void setRightClickCommand(String cmd) {
    this.rightClickCommand =
        (cmd == null || cmd.isBlank()) ? null : cmd.stripLeading().replaceFirst("^/", "");
  }

  public boolean hasRightClickCommand() {
    return rightClickCommand != null;
  }

  public boolean isHeadAiEnabled() {
    return headAiEnabled;
  }

  public void setHeadAiEnabled(boolean v) {
    this.headAiEnabled = v;
  }

  public boolean isPickUpItemsEnabled() {
    return pickUpItemsEnabled;
  }

  public void setPickUpItemsEnabled(boolean v) {
    this.pickUpItemsEnabled = v;
  }

  public boolean isPickUpXpEnabled() {
    return pickUpXpEnabled;
  }

  public void setPickUpXpEnabled(boolean v) {
    this.pickUpXpEnabled = v;
  }

  public boolean isNavParkour() {
    return navParkour;
  }

  public void setNavParkour(boolean v) {
    this.navParkour = v;
  }

  public boolean isNavBreakBlocks() {
    return navBreakBlocks;
  }

  public void setNavBreakBlocks(boolean v) {
    this.navBreakBlocks = v;
  }

  public boolean isNavPlaceBlocks() {
    return navPlaceBlocks;
  }

  public void setNavPlaceBlocks(boolean v) {
    this.navPlaceBlocks = v;
  }

  public boolean isNavAvoidWater() {
    return navAvoidWater;
  }

  public void setNavAvoidWater(boolean v) {
    this.navAvoidWater = v;
  }

  public boolean isNavAvoidLava() {
    return navAvoidLava;
  }

  public void setNavAvoidLava(boolean v) {
    this.navAvoidLava = v;
  }

  public boolean isNavSprintJump() {
    return navSprintJump;
  }

  public void setNavSprintJump(boolean v) {
    this.navSprintJump = v;
  }

  public boolean isSwimAiEnabled() {
    return swimAiEnabled;
  }

  public void setSwimAiEnabled(boolean v) {
    this.swimAiEnabled = v;
  }

  public int getChunkLoadRadius() {
    return chunkLoadRadius;
  }

  public void setChunkLoadRadius(int r) {
    this.chunkLoadRadius = r;
  }

  @org.jetbrains.annotations.Nullable
  public String getNameTagNick() {
    return nameTagNick;
  }

  public void setNameTagNick(@org.jetbrains.annotations.Nullable String nick) {
    this.nameTagNick = nick;

    this.cachedNmsDisplayComponent = null;
    this.cachedNmsDisplaySource = null;
  }

  public int getPing() {
    return ping;
  }

  public void setPing(int ping) {
    int newPing = (ping < 0) ? -1 : Math.min(ping, 9999);
    if (this.ping != newPing) {
      this.ping = newPing;
      this.tabListDirty = true;
    }
  }

  public boolean hasCustomPing() {
    return ping >= 0;
  }

  public boolean isTabListDirty() {
    return tabListDirty;
  }

  public void clearTabListDirty() {
    tabListDirty = false;
  }

  public void markTabListDirty() {
    tabListDirty = true;
  }

  public SkinProfile getResolvedSkin() {
    return resolvedSkin;
  }

  public String getSpawnedBy() {
    return spawnedBy;
  }

  public UUID getSpawnedByUuid() {
    return spawnedByUuid;
  }

  public BotRecord getDbRecord() {
    return dbRecord;
  }

  public Instant getSpawnTime() {
    return spawnTime;
  }

  public void setSpawnedBy(String name, UUID uuid) {
    this.spawnedBy = name;
    this.spawnedByUuid = uuid;
  }

  public void setDbRecord(BotRecord record) {
    this.dbRecord = record;
  }

  public void setSpawnTime(Instant t) {
    this.spawnTime = t;
  }

  public boolean isBodyless() {
    return bodyless;
  }

  public void setBodyless(boolean bodyless) {
    this.bodyless = bodyless;
  }

  public boolean isRestoredSpawn() {
    return restoredSpawn;
  }

  public void setRestoredSpawn(boolean restoredSpawn) {
    this.restoredSpawn = restoredSpawn;
  }

  public Entity getNametagEntity() {
    return nametagEntity;
  }

  public void setNametagEntity(Entity entity) {
    this.nametagEntity = entity;
  }

  public String getPacketProfileName() {
    String p = packetProfileName != null ? packetProfileName : name;
    return (p == null || p.isBlank()) ? name : p;
  }

  public void setPacketProfileName(String n) {
    this.packetProfileName = (n == null || n.isBlank()) ? this.name : n;
  }

  public boolean isPveEnabled() {
    return pveEnabled;
  }

  public void setPveEnabled(boolean v) {
    this.pveEnabled = v;
  }

  public double getPveRange() {
    return pveRange;
  }

  public void setPveRange(double v) {
    this.pveRange = v;
  }

  public String getPvePriority() {
    return pvePriority;
  }

  public void setPvePriority(String v) {
    this.pvePriority = v;
  }

  public boolean isPveMoveToTarget() {
    return pveMoveToTarget;
  }

  public void setPveMoveToTarget(boolean v) {
    this.pveMoveToTarget = v;
  }

  public String getPveMobType() {
    return pveMobTypes.isEmpty() ? null : String.join(",", pveMobTypes);
  }

  public void setPveMobType(String v) {
    pveMobTypes.clear();
    if (v != null && !v.isBlank()) {
      for (String part : v.split(",")) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) pveMobTypes.add(trimmed);
      }
    }
  }

  public Set<String> getPveMobTypes() {
    return pveMobTypes;
  }

  public void setPveMobTypes(Set<String> types) {
    this.pveMobTypes = types != null ? new LinkedHashSet<>(types) : new LinkedHashSet<>();
  }

  public boolean togglePveMobType(String type) {
    if (pveMobTypes.contains(type)) {
      pveMobTypes.remove(type);
      return false;
    } else {
      pveMobTypes.add(type);
      return true;
    }
  }

  public boolean hasPveMobType(String type) {
    return pveMobTypes.contains(type);
  }

  public Set<UUID> getSharedControllers() {
    return Set.copyOf(sharedControllers);
  }

  public boolean hasSharedController(UUID uuid) {
    return uuid != null && sharedControllers.contains(uuid);
  }

  public boolean addSharedController(UUID uuid) {
    return uuid != null && !uuid.equals(spawnedByUuid) && sharedControllers.add(uuid);
  }

  public boolean removeSharedController(UUID uuid) {
    return uuid != null && sharedControllers.remove(uuid);
  }

  public boolean isAutoEatEnabled() {
    return autoEatEnabled;
  }

  public void setAutoEatEnabled(boolean autoEatEnabled) {
    this.autoEatEnabled = autoEatEnabled;
  }

  public boolean isAutoPlaceBedEnabled() {
    return autoPlaceBedEnabled;
  }

  public void setAutoPlaceBedEnabled(boolean autoPlaceBedEnabled) {
    this.autoPlaceBedEnabled = autoPlaceBedEnabled;
  }

  // ── Sleep system accessors ────────────────────────────────────────────────

  @org.jetbrains.annotations.Nullable
  public Location getSleepOrigin() {
    return sleepOrigin;
  }

  public void setSleepOrigin(@org.jetbrains.annotations.Nullable Location origin) {
    this.sleepOrigin = origin != null ? origin.clone() : null;
  }

  public double getSleepRadius() {
    return sleepRadius;
  }

  public void setSleepRadius(double radius) {
    this.sleepRadius = radius;
  }

  public boolean isSleeping() {
    return sleeping;
  }

  public void setSleeping(boolean sleeping) {
    this.sleeping = sleeping;
  }

  // ── Addon metadata ────────────────────────────────────────────────────────

  @org.jetbrains.annotations.Nullable
  public Object getMetadata(String key) {
    return key != null ? metadata.get(key) : null;
  }

  public void setMetadata(String key, Object value) {
    if (key == null) return;
    if (value == null) {
      metadata.remove(key);
    } else {
      metadata.put(key, value);
    }
  }

  public boolean hasMetadata(String key) {
    return key != null && metadata.containsKey(key);
  }

  public void removeMetadata(String key) {
    if (key != null) metadata.remove(key);
  }

  @NotNull
  public java.util.Map<String, Object> getMetadataMap() {
    return java.util.Map.copyOf(metadata);
  }

  public void clearMetadata() {
    metadata.clear();
  }
}

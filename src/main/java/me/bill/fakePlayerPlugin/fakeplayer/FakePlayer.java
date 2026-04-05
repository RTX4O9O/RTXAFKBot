package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.bill.fakePlayerPlugin.database.BotRecord;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single active fake player.
 *
 * <p><b>NMS Entity System:</b> Each bot is a single NMS {@code ServerPlayer} entity
 * spawned via {@link NmsPlayerSpawner}. The entity appears to clients as a genuine 
 * player with a body, nametag, tab-list entry, and full interaction support (damage, 
 * knockback, death, drowning, mob targeting, etc.).
 *
 * <p><b>Fake Connection:</b> The NMS player is backed by a fake {@code EmbeddedChannel} 
 * connection that discards all outbound packets. The channel uses a discard-proxy
 * (see {@link NmsPlayerSpawner#createDiscardChannel}) to prevent Netty NPEs during
 * server tick flush operations.
 *
 * <p><b>Auth bypass:</b> The player is created directly in JVM memory without a TCP 
 * connection, so the Mojang session server handshake is never triggered.
 */
@SuppressWarnings("unused") // Public API — used by addons and InfoCommand
public final class FakePlayer {

    private final String        name;
    private final PlayerProfile profile;
    private final UUID          uuid;

    private Location   spawnLocation;

    /** The NMS ServerPlayer entity — physics, skin, nametag all in one. */
    private Player     player;

    // ── Metadata ─────────────────────────────────────────────────────────────
    private String    spawnedBy     = "UNKNOWN";
    private UUID      spawnedByUuid = new UUID(0, 0);
    private BotRecord dbRecord;
    private Instant   spawnTime     = Instant.now();

    /**
     * The text shown in the nametag and tab list.
     * When {@code null}, {@link #getName()} is used for display.
     */
    private String displayName = null;

    /**
     * The "raw" display content: the value that goes into the {@code {bot_name}}
     * slot of {@code bot-name.tab-list-format} before LP prefix/suffix are added.
     * Stored at spawn/restore time and used by
     * {@code FakePlayerManager.refreshLpDisplayName()} to rebuild the full
     * display name after LuckPerms assigns the bot's group.
     */
    private String rawDisplayName = null;

    /**
     * The Minecraft username used for skin resolution.
     * Admin bots: same as {@link #name}.
     * User bots: a random pool name (their internal name has no Mojang skin).
     */
    private String skinName = null;


    /**
     * The resolved skin texture (value + signature) for this bot.
     * Populated after async skin resolution. {@code null} if skin mode is "off".
     */
    private SkinProfile resolvedSkin = null;

    // ── Session stats ─────────────────────────────────────────────────────────

    /** Total damage this bot has received during its current session. */
    private double totalDamageTaken = 0.0;

    /** Number of times this bot has been killed. */
    private int    deathCount       = 0;

    /**
     * Last chunk X and Z this bot was in — used by ChunkLoader to
     * detect cross-chunk movement without a Location allocation.
     */
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;

    // ── Legacy compatibility fields ───────────────────────────────────────────
    /** Nametag entity - kept for compatibility with old systems */
    private Entity nametagEntity = null;
    
    /** Packet profile name - since bots are real players now, this is just the real name */
    private String packetProfileName = null;

    /** Whether the bot is currently alive (false after death, until respawn). */
    private boolean alive = true;

    /** How many times this bot has been "tag list refreshed" (diagnostic). */
    private int tabRefreshCount = 0;

    /**
     * Whether this bot is currently frozen in place.
     * Frozen bots have {@code setImmovable(true)} and {@code setGravity(false)};
     * head-AI skips them and they are highlighted in /fpp list & /fpp stats.
     */
    private boolean frozen = false;

    /** Last world name — used for fast orphan/cross-world detection. */
    private String lastKnownWorld = null;


    /**
     * When {@code true}, this bot spawns without a physical body.
     * Used for console-spawned bots with incomplete location data.
     * Bots still appear in tab list and chat, but have no entity in the world.
     */
    private boolean bodyless = false;

    /**
     * The LuckPerms group currently assigned to this bot.
     * Set by FakePlayerManager during spawn/restore (from LP storage) and by /fpp rank.
     * {@code null} = not yet resolved (LP unavailable or not yet loaded).
     * Used to avoid re-querying LP on every display-name rebuild and to survive reloads.
     */
    private String luckpermsGroup = null;

    /**
     * The archetype this bot was spawned as.
     * {@link BotType#AFK} = default passive bot.
     * {@link BotType#PVP} = PvP bot (name prefixed {@code pvp_}).
     */
    private BotType botType = BotType.AFK;

    /**
     * Whether this bot participates in auto-chat and mention-replies.
     * {@code false} = permanently silenced until re-enabled via {@code /fpp chat <bot> on}.
     */
    private boolean chatEnabled = true;

    /**
     * Per-bot chat activity tier override. When non-null, overrides the randomly-assigned
     * tier. Valid values: {@code "quiet"}, {@code "passive"}, {@code "normal"},
     * {@code "active"}, {@code "chatty"}. {@code null} = use random assignment.
     */
    private String chatTier = null;

    public FakePlayer(UUID uuid, String name, PlayerProfile profile) {
        this.uuid    = uuid;
        this.name    = name;
        this.profile = profile;
    }

    // ── Core getters ──────────────────────────────────────────────────────────
    public UUID          getUuid()          { return uuid; }
    public String        getName()          { return name; }
    public PlayerProfile getProfile()       { return profile; }
    public Location      getSpawnLocation() { return spawnLocation; }
    
    /** Returns the NMS Player entity (same as {@link #getPlayer()}). */
    public org.bukkit.entity.Player getPhysicsEntity() { 
        return player;
    }
    
    /** Get the NMS Player entity. */
    public org.bukkit.entity.Player getPlayer() { 
        return player;
    }

    /** 
     * Get the entity ID of the NMS Player entity.
     * Returns -1 if no player entity is set.
     */
    public int getEntityId() { 
        return player != null ? player.getEntityId() : -1; 
    }

    /**
     * The text shown in the nametag and tab list.
     * Falls back to {@link #getName()} when no display name is set.
     */
    public String getDisplayName() { return displayName != null ? displayName : name; }

    /**
     * The Minecraft username used for skin lookup.
     * Falls back to {@link #getName()} if not explicitly set.
     */
    public String getSkinName() { return skinName != null ? skinName : name; }

    /**
     * Returns the most accurate current location for this bot:
     * the live NMS Player body position if available, otherwise the last
     * recorded spawn location.
     */
    public Location getLiveLocation() {
        if (player != null && player.isValid()) return player.getLocation();
        return spawnLocation;
    }

    /**
     * Returns the bot's uptime as a {@link Duration}.
     * Accurate to the second.
     */
    public Duration getUptime() {
        return Duration.between(spawnTime, Instant.now());
    }

    /** Uptime formatted as {@code Xh Ym Zs} for display in /fpp list and /fpp info. */
    public String getUptimeFormatted() {
        Duration d = getUptime();
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0)  return h + "h " + m + "m " + s + "s";
        if (m > 0)  return m + "m " + s + "s";
        return s + "s";
    }

    // ── Session stats ─────────────────────────────────────────────────────────
    public double getTotalDamageTaken()          { return totalDamageTaken; }
    public int    getDeathCount()                { return deathCount; }
    public boolean isAlive()                     { return alive; }
    public int    getTabRefreshCount()           { return tabRefreshCount; }
    public boolean isFrozen()                    { return frozen; }
    public String getLastKnownWorld()            { return lastKnownWorld; }

    public void addDamageTaken(double amount)    { totalDamageTaken += amount; }
    public void incrementDeathCount()            { deathCount++; }
    public void setAlive(boolean alive)          { this.alive = alive; }
    public void incrementTabRefresh()            { tabRefreshCount++; }
    public void setFrozen(boolean frozen)        { this.frozen = frozen; }

    // ── Chunk tracking (for ChunkLoader fast-path) ────────────────────────────
    public int getLastChunkX()                   { return lastChunkX; }
    public int getLastChunkZ()                   { return lastChunkZ; }
    public void setLastChunk(int cx, int cz)     { lastChunkX = cx; lastChunkZ = cz; }
    public boolean hasMovedChunk(int cx, int cz) { return cx != lastChunkX || cz != lastChunkZ; }

    // ── Core setters ──────────────────────────────────────────────────────────
    public void setSpawnLocation(Location loc)    { this.spawnLocation = loc; }
    
    /** Set the NMS Player entity. */
    public void setPlayer(org.bukkit.entity.Player p) { this.player = p; }
    
    /** Set the physics entity (for compatibility — accepts Player or casts to Player). */
    public void setPhysicsEntity(Entity e) { 
        this.player = e instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player) e : null;
    }
    
    public void setDisplayName(String name)       { this.displayName   = name; }
    /** Raw display content (the {@code {bot_name}} part before LP prefix/suffix). */
    public String getRawDisplayName()             { return rawDisplayName; }
    public void setRawDisplayName(String name)    { this.rawDisplayName = name; }
    /** Currently-assigned LuckPerms group for this bot, or {@code null} if not yet set. */
    public String getLuckpermsGroup()             { return luckpermsGroup; }
    public void setLuckpermsGroup(String group)   { this.luckpermsGroup = group; }
    /** The archetype this bot was spawned as ({@link BotType#AFK} by default). */
    public BotType getBotType()                   { return botType != null ? botType : BotType.AFK; }
    public void    setBotType(BotType type)       { this.botType = type; }

    /** Whether this bot participates in auto-chat. {@code false} = silenced. */
    public boolean isChatEnabled()               { return chatEnabled; }
    public void    setChatEnabled(boolean v)     { this.chatEnabled = v; }

    /**
     * Per-bot chat activity tier override, or {@code null} for random assignment.
     * Values: {@code "quiet"}, {@code "passive"}, {@code "normal"}, {@code "active"}, {@code "chatty"}.
     */
    public String  getChatTier()                 { return chatTier; }
    public void    setChatTier(String tier)      { this.chatTier = tier; }
    public void setSkinName(String name)          { this.skinName      = name; }
    public void setResolvedSkin(SkinProfile skin) { this.resolvedSkin  = skin; }


    /** The resolved skin for this bot, or {@code null} if not yet resolved or skin is off. */
    public SkinProfile getResolvedSkin()          { return resolvedSkin; }

    // ── Metadata ──────────────────────────────────────────────────────────────
    public String    getSpawnedBy()              { return spawnedBy; }
    public UUID      getSpawnedByUuid()          { return spawnedByUuid; }
    public BotRecord getDbRecord()               { return dbRecord; }
    public Instant   getSpawnTime()              { return spawnTime; }

    public void setSpawnedBy(String name, UUID uuid) {
        this.spawnedBy     = name;
        this.spawnedByUuid = uuid;
    }
    public void setDbRecord(BotRecord record)    { this.dbRecord  = record; }
    public void setSpawnTime(Instant t)          { this.spawnTime = t; }


    // ── Bodyless spawn ────────────────────────────────────────────────────────
    /**
     * Returns {@code true} if this bot spawns without a physical body
     * (tab-list and chat only, no entity in world).
     */
    public boolean isBodyless() { return bodyless; }

    /**
     * Sets whether this bot should spawn without a physical body.
     * Used for console spawns without location data.
     */
    public void setBodyless(boolean bodyless) { this.bodyless = bodyless; }

    // ── Legacy compatibility methods ──────────────────────────────────────────
    /** Get nametag entity (legacy compatibility) */
    public Entity getNametagEntity() { return nametagEntity; }
    
    /** Set nametag entity (legacy compatibility) */
    public void setNametagEntity(Entity entity) { this.nametagEntity = entity; }
    
    /** Get packet profile name - for real NMS players this is just the real name */
    public String getPacketProfileName() { 
        return packetProfileName != null ? packetProfileName : name; 
    }
    
    /** Set packet profile name - for real NMS players this is just the real name */
    public void setPacketProfileName(String name) { 
        this.packetProfileName = name; 
    }
}

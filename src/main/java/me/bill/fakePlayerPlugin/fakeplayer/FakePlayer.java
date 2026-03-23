package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.bill.fakePlayerPlugin.database.BotRecord;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single active fake player.
 *
 * <p>Entity stack (two entities total):
 * <pre>
 *   ArmorStand  — invisible marker, custom name visible, rides the Mannequin
 *       ↓ rides
 *   Mannequin   — physics body + skin.
 *                 setImmovable(false)   → vanilla entity-separation push/knockback
 *                 setGravity(true)      → falls naturally
 *                 setInvulnerable(false)→ takes damage
 *                 setProfile(name)      → client resolves skin automatically
 * </pre>
 */
@SuppressWarnings("unused") // Public API — used by addons and InfoCommand
public final class FakePlayer {

    private final String        name;
    private final PlayerProfile profile;
    private final UUID          uuid;

    private Location   spawnLocation;

    /** The Mannequin — physics body AND visual skin. */
    private Entity     physicsEntity;

    /** Invisible ArmorStand riding the Mannequin — displays the nametag. */
    private ArmorStand nametagEntity;

    // ── Metadata ─────────────────────────────────────────────────────────────
    private String    spawnedBy     = "UNKNOWN";
    private UUID      spawnedByUuid = new UUID(0, 0);
    private BotRecord dbRecord;
    private Instant   spawnTime     = Instant.now();

    /**
     * Optional display name shown in the nametag and tab list.
     * When {@code null}, {@link #getName()} is used for display.
     */
    private String displayName = null;

    /**
     * The Minecraft username used for skin resolution.
     * Admin bots: same as {@link #name}.
     * User bots: a random pool name (their internal name has no Mojang skin).
     */
    private String skinName = null;

    /**
     * Optional override for the profile name sent in PlayerInfo packets (tab list).
     * This is used to inject a small, non-visible sort prefix based on LuckPerms
     * group weight so bots can be ordered in the tab list without changing their
     * visible display name or skin lookup source.
     */
    private String packetProfileName = null;

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

    /**
     * Sequential index for user-tier bots (the {@code {num}} placeholder value).
     * Set to {@code -1} for admin-spawned bots.
     * Stored so that {@link me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager#updateAllBotPrefixes()}
     * can correctly rebuild display names after a LuckPerms group change without
     * leaving {@code {spawner}} or {@code {num}} as literal text.
     */
    private int botIndex = -1;

    /** Last world name — used for fast orphan/cross-world detection. */
    private String lastKnownWorld = null;

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
    public Entity        getPhysicsEntity() { return physicsEntity; }
    public ArmorStand    getNametagEntity() { return nametagEntity; }

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

    /** Convenience cast — physicsEntity is always a Mannequin when body is enabled. */
    public Mannequin getMannequin() {
        return (physicsEntity instanceof Mannequin m) ? m : null;
    }

    /** Entity ID of the Mannequin body, or {@code -1} if no body. */
    public int getEntityId() {
        return physicsEntity != null ? physicsEntity.getEntityId() : -1;
    }

    // ── Live position ─────────────────────────────────────────────────────────

    /**
     * Returns the most accurate current location for this bot:
     * the live Mannequin body position if available, otherwise the last
     * recorded spawn location.
     */
    public Location getLiveLocation() {
        Entity body = physicsEntity;
        if (body instanceof Mannequin m && m.isValid()) return m.getLocation();
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
    public int  getBotIndex()                    { return botIndex; }
    public void setBotIndex(int index)           { this.botIndex = index; }

    // ── Chunk tracking (for ChunkLoader fast-path) ────────────────────────────
    public int getLastChunkX()                   { return lastChunkX; }
    public int getLastChunkZ()                   { return lastChunkZ; }
    public void setLastChunk(int cx, int cz)     { lastChunkX = cx; lastChunkZ = cz; }
    public boolean hasMovedChunk(int cx, int cz) { return cx != lastChunkX || cz != lastChunkZ; }

    // ── Core setters ──────────────────────────────────────────────────────────
    public void setSpawnLocation(Location loc)    { this.spawnLocation = loc; }
    public void setPhysicsEntity(Entity e)        { this.physicsEntity = e; }
    public void setNametagEntity(ArmorStand as)   { this.nametagEntity = as; }
    public void setDisplayName(String name)       { this.displayName   = name; }
    public void setSkinName(String name)          { this.skinName      = name; }
    public void setResolvedSkin(SkinProfile skin) { this.resolvedSkin  = skin; }

    /** The profile name to use when building the GameProfile in tab packets. */
    public String getPacketProfileName() {
        return packetProfileName != null ? packetProfileName : name;
    }

    public void setPacketProfileName(String s) { this.packetProfileName = s; }

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
}

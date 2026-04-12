package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.bill.fakePlayerPlugin.config.Config;
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
 * helper to prevent Netty NPEs during
 * server tick flush operations.
 *
 * <p><b>Auth bypass:</b> The player is created directly in JVM memory without a TCP 
 * connection, so the Mojang session server handshake is never triggered.
 */
@SuppressWarnings("unused") // Public API - used by addons and InfoCommand
public final class FakePlayer {

    private final String        name;
    private final PlayerProfile profile;
    private final UUID          uuid;

    private Location   spawnLocation;

    /** The NMS ServerPlayer entity - physics, skin, nametag all in one. */
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
     * Last chunk X and Z this bot was in - used by ChunkLoader to
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

    /**
     * {@code true} while the bot is in the middle of a server-side respawn
     * (died with {@code respawn-on-death: true} and is waiting for
     * {@code spigot().respawn()} to complete). Used to suppress the join/quit
     * messages that the vanilla pipeline would otherwise broadcast.
     */
    private boolean respawning = false;

    /** How many times this bot has been "tag list refreshed" (diagnostic). */
    private int tabRefreshCount = 0;

    // ── Tab-list packet caches (performance) ──────────────────────────────────

    /**
     * Cached NMS {@code net.minecraft.network.chat.Component} for the display name.
     * Built by {@code PacketHelper.sendTabListDisplayNameUpdate} and reused for all
     * subsequent players in the same refresh cycle.  Invalidated whenever
     * {@link #setDisplayName(String)} is called.
     */
    private transient volatile Object cachedNmsDisplayComponent;

    /**
     * The {@link #displayName} string that was serialised into
     * {@link #cachedNmsDisplayComponent}.  Used to detect stale cache entries.
     */
    private transient volatile String cachedNmsDisplaySource;

    /**
     * Cached authlib {@code GameProfile(uuid, name)} instance.
     * The UUID and internal name never change, so this object is created once
     * and reused for every tab-list UPDATE_DISPLAY_NAME packet.
     * Populated lazily by {@code PacketHelper}.
     */
    private transient volatile Object cachedTabListGameProfile;

    /**
     * Whether this bot is currently frozen in place.
     * Frozen bots have {@code setImmovable(true)} and {@code setGravity(false)};
     * head-AI skips them and they are highlighted in /fpp list & /fpp stats.
     */
    private boolean frozen = false;

    /** Last world name - used for fast orphan/cross-world detection. */
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

    /**
     * Persistent AI personality name for this bot (from the
     * {@code personalities/} folder via {@link me.bill.fakePlayerPlugin.ai.PersonalityRepository}).
     * Assigned randomly at first spawn and persisted to the database + YAML.
     * {@code null} = no personality assigned yet; the config default will be used.
     */
    private String aiPersonality = null;

    /**
     * Command to execute as this bot when any player right-clicks its body.
     * When non-null, the right-click fires {@code Bukkit.dispatchCommand(botPlayer, rightClickCommand)}
     * instead of opening the inventory GUI.
     * Set via {@code /fpp cmd <bot> --add <command>}; cleared via {@code --clear}.
     * {@code null} = not set (right-click falls through to inventory GUI).
     */
    private String rightClickCommand = null;

    /**
     * Whether head-AI (look-at-player tracking) is enabled for this bot.
     * {@code true} = bot smoothly rotates to face the nearest player within range.
     * {@code false} = bot's head stays locked in place (does not track players).
     */
    private boolean headAiEnabled = true;

    /** Whether this bot can pick up item entities into its inventory. */
    private boolean pickUpItemsEnabled = Config.bodyPickUpItems();

    /** Whether this bot can pick up XP orbs / receive normal XP gains. */
    private boolean pickUpXpEnabled = Config.bodyPickUpXp();

    // ── Per-bot pathfinding overrides ──────────────────────────────────────────
    /** Whether this bot may sprint-jump across gaps (per-bot override of global config). */
    private boolean navParkour     = Config.pathfindingParkour();
    /** Whether this bot may break obstructing blocks during navigation. */
    private boolean navBreakBlocks = Config.pathfindingBreakBlocks();
    /** Whether this bot may place bridge blocks during navigation. */
    private boolean navPlaceBlocks = Config.pathfindingPlaceBlocks();

    // ── Per-bot swim AI + chunk loading ───────────────────────────────────────
    /**
     * Whether swim-AI (auto-jump in water/lava) is enabled for this bot.
     * Defaults to the global {@code swim-ai.enabled} config value at spawn time.
     * {@code false} = bot will sink rather than swim up to the surface.
     */
    private boolean swimAiEnabled = Config.swimAiEnabled();

    /**
     * Per-bot chunk-loading radius override.
     * {@code -1} = use the global {@code chunk-loading.radius} config value.
     * {@code 0}  = no chunk loading for this bot (saves server load).
     * {@code 1}–{@code N} = load this many chunks around the bot, capped at the global max.
     */
    private int chunkLoadRadius = -1;

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

    // ── Tab-list packet cache API (used by PacketHelper) ──────────────────────

    /** Returns the cached NMS Component for the display name, or {@code null} if stale. */
    public Object getCachedNmsDisplayComponent()  { return cachedNmsDisplayComponent; }
    /** Returns the display-name string the cached component was built from. */
    public String getCachedNmsDisplaySource()     { return cachedNmsDisplaySource; }
    /** Stores a freshly-built NMS Component and the source string it was built from. */
    public void   setCachedNmsDisplay(Object comp, String source) {
        this.cachedNmsDisplayComponent = comp;
        this.cachedNmsDisplaySource    = source;
    }

    /** Returns the cached {@code GameProfile(uuid, name)} object, or {@code null} on first call. */
    public Object getCachedTabListGameProfile()               { return cachedTabListGameProfile; }
    /** Stores the reusable {@code GameProfile(uuid, name)} object. */
    public void   setCachedTabListGameProfile(Object profile) { this.cachedTabListGameProfile = profile; }

    // ── Chunk tracking (for ChunkLoader fast-path) ────────────────────────────
    public int getLastChunkX()                   { return lastChunkX; }
    public int getLastChunkZ()                   { return lastChunkZ; }
    public void setLastChunk(int cx, int cz)     { lastChunkX = cx; lastChunkZ = cz; }
    public boolean hasMovedChunk(int cx, int cz) { return cx != lastChunkX || cz != lastChunkZ; }

    // ── Core setters ──────────────────────────────────────────────────────────
    public void setSpawnLocation(Location loc)    { this.spawnLocation = loc; }
    
    /** Set the NMS Player entity. */
    public void setPlayer(org.bukkit.entity.Player p) { this.player = p; }
    
    /** Set the physics entity (for compatibility - accepts Player or casts to Player). */
    public void setPhysicsEntity(Entity e) { 
        this.player = e instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player) e : null;
    }
    
    public void setDisplayName(String name) {
        this.displayName = name;
        // Invalidate packet caches so the next sendTabListDisplayNameUpdate
        // re-serialises the new name before broadcasting.
        this.cachedNmsDisplayComponent = null;
        this.cachedNmsDisplaySource    = null;
    }
    /** Raw display content (the {@code {bot_name}} part before LP prefix/suffix). */
    public String getRawDisplayName()             { return rawDisplayName; }
    public void setRawDisplayName(String name)    { this.rawDisplayName = name; }
    /** Currently-assigned LuckPerms group for this bot, or {@code null} if not yet set. */
    public String getLuckpermsGroup()             { return luckpermsGroup; }
    public void setLuckpermsGroup(String group)   { this.luckpermsGroup = group; }
    /** The archetype this bot was spawned as ({@link BotType#AFK} by default). */
    public BotType getBotType()                   { return botType != null ? botType : BotType.AFK; }
    public void    setBotType(BotType type)       { this.botType = type; }

    /** Whether the bot is mid-respawn (suppress join/quit messages). */
    public boolean isRespawning()               { return respawning; }
    public void    setRespawning(boolean v)     { this.respawning = v; }

    /** Whether this bot participates in auto-chat. {@code false} = silenced. */
    public boolean isChatEnabled()               { return chatEnabled; }
    public void    setChatEnabled(boolean v)     { this.chatEnabled = v; }

    /**
     * Per-bot chat activity tier override, or {@code null} for random assignment.
     * Values: {@code "quiet"}, {@code "passive"}, {@code "normal"}, {@code "active"}, {@code "chatty"}.
     */
    public String  getChatTier()                 { return chatTier; }
    public void    setChatTier(String tier)      { this.chatTier = tier; }

    /**
     * The persistent AI personality name assigned to this bot (from the personalities/ folder).
     * {@code null} = no personality; config default personality is used in conversations.
     */
    public String  getAiPersonality()                  { return aiPersonality; }
    public void    setAiPersonality(String personality) { this.aiPersonality = personality; }

    public void setSkinName(String name)          { this.skinName      = name; }
    public void setResolvedSkin(SkinProfile skin) { this.resolvedSkin  = skin; }

    // ── Right-click command ───────────────────────────────────────────────────
    /** Returns the command stored for right-click execution, or {@code null} if not set. */
    public String  getRightClickCommand()           { return rightClickCommand; }
    /** Stores a command for right-click execution (without leading {@code /}). */
    public void    setRightClickCommand(String cmd) { this.rightClickCommand = (cmd == null || cmd.isBlank()) ? null : cmd.stripLeading().replaceFirst("^/", ""); }
    /** Returns {@code true} when a right-click command is configured on this bot. */
    public boolean hasRightClickCommand()           { return rightClickCommand != null; }

    // ── Head AI ───────────────────────────────────────────────────────────────
    /** Whether head-AI (look-at-player tracking) is enabled for this bot. */
    public boolean isHeadAiEnabled()               { return headAiEnabled; }
    /** Enable/disable head-AI tracking for this bot. */
    public void    setHeadAiEnabled(boolean v)     { this.headAiEnabled = v; }

    // ── Pickup toggles ─────────────────────────────────────────────────────────
    /** Whether this bot can pick up item entities into its inventory. */
    public boolean isPickUpItemsEnabled()          { return pickUpItemsEnabled; }
    /** Enable/disable item pickup for this bot. */
    public void    setPickUpItemsEnabled(boolean v){ this.pickUpItemsEnabled = v; }

    /** Whether this bot can pick up XP orbs / receive normal XP gains. */
    public boolean isPickUpXpEnabled()             { return pickUpXpEnabled; }
    /** Enable/disable XP pickup for this bot. */
    public void    setPickUpXpEnabled(boolean v)   { this.pickUpXpEnabled = v; }

    // ── Per-bot pathfinding overrides ──────────────────────────────────────────
    /** Whether this bot may sprint-jump across gaps during navigation. */
    public boolean isNavParkour()                  { return navParkour; }
    public void    setNavParkour(boolean v)        { this.navParkour = v; }
    /** Whether this bot may break obstructing blocks during navigation. */
    public boolean isNavBreakBlocks()              { return navBreakBlocks; }
    public void    setNavBreakBlocks(boolean v)    { this.navBreakBlocks = v; }
    /** Whether this bot may place bridge blocks during navigation. */
    public boolean isNavPlaceBlocks()              { return navPlaceBlocks; }
    public void    setNavPlaceBlocks(boolean v)    { this.navPlaceBlocks = v; }

    // ── Swim AI ────────────────────────────────────────────────────────────────
    /** Whether swim-AI (auto-jump in fluid) is enabled for this bot. */
    public boolean isSwimAiEnabled()               { return swimAiEnabled; }
    /** Enable/disable swim-AI for this bot. */
    public void    setSwimAiEnabled(boolean v)     { this.swimAiEnabled = v; }

    // ── Per-bot chunk-load radius ──────────────────────────────────────────────
    /**
     * Per-bot chunk-loading radius override.
     * {@code -1} = use the global config value.
     * {@code 0}  = disabled for this bot.
     * {@code 1}–{@code N} = override radius (capped at global max by {@code ChunkLoader}).
     */
    public int  getChunkLoadRadius()               { return chunkLoadRadius; }
    /** Set the per-bot chunk-load radius; use {@code -1} to follow the global config. */
    public void setChunkLoadRadius(int r)          { this.chunkLoadRadius = r; }


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
    
    /**
     * Get packet profile name - for real NMS players this is just the real name.
     * <p><b>Never returns blank</b>: if the stored value is null or blank (which would
     * cause the vanilla client to display "Anonymous Player"), falls back to the
     * bot's internal Minecraft username.
     */
    public String getPacketProfileName() {
        String p = packetProfileName != null ? packetProfileName : name;
        return (p == null || p.isBlank()) ? name : p;
    }

    /**
     * Set packet profile name - sanitised so a blank value can never be stored.
     * Blank or null values are silently replaced with the bot's internal MC username.
     */
    public void setPacketProfileName(String n) {
        this.packetProfileName = (n == null || n.isBlank()) ? this.name : n;
    }
}


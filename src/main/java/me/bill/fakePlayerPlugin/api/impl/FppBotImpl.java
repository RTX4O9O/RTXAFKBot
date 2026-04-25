package me.bill.fakePlayerPlugin.api.impl;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FppBotImpl implements FppBot {

  private final FakePlayer fp;

  public FppBotImpl(@NotNull FakePlayer fp) { this.fp = fp; }

  public @NotNull FakePlayer getHandle() { return fp; }

  @Override public @NotNull String getName() { return fp.getName(); }
  @Override public @NotNull UUID   getUuid() { return fp.getUuid(); }

  @Override public @NotNull String getDisplayName() {
    String dn = fp.getDisplayName();
    return dn != null ? dn : fp.getName();
  }

  @Override public void setDisplayName(@NotNull String name) {
    fp.setDisplayName(name);
  }

  @Override public @Nullable String getSkinName() { return fp.getSkinName(); }

  @Override public @NotNull Location getLocation() { return fp.getLiveLocation(); }

  @Override public @NotNull String getWorldName() {
    Location loc = fp.getLiveLocation();
    return loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
  }

  @Override public @Nullable Player getEntity() { return fp.getPhysicsEntity(); }

  @Override public boolean isBodyless() { return fp.isBodyless(); }

  @Override public boolean isFrozen()                  { return fp.isFrozen(); }
  @Override public void    setFrozen(boolean frozen)   { fp.setFrozen(frozen); }
  @Override public boolean isAlive()                   { return fp.isAlive(); }
  @Override public boolean isRespawning()              { return fp.isRespawning(); }

  @Override public boolean  isChatEnabled()                  { return fp.isChatEnabled(); }
  @Override public void     setChatEnabled(boolean enabled)  { fp.setChatEnabled(enabled); }
  @Override public @Nullable String getChatTier()            { return fp.getChatTier(); }
  @Override public void     setChatTier(@Nullable String t)  { fp.setChatTier(t); }
  @Override public @Nullable String getAiPersonality()       { return fp.getAiPersonality(); }
  @Override public void     setAiPersonality(@Nullable String p) { fp.setAiPersonality(p); }

  @Override public boolean isHeadAiEnabled()                   { return fp.isHeadAiEnabled(); }
  @Override public void    setHeadAiEnabled(boolean e)         { fp.setHeadAiEnabled(e); }
  @Override public boolean isSwimAiEnabled()                   { return fp.isSwimAiEnabled(); }
  @Override public void    setSwimAiEnabled(boolean e)         { fp.setSwimAiEnabled(e); }
  @Override public boolean isPickUpItemsEnabled()              { return fp.isPickUpItemsEnabled(); }
  @Override public void    setPickUpItemsEnabled(boolean e)    { fp.setPickUpItemsEnabled(e); }
  @Override public boolean isPickUpXpEnabled()                 { return fp.isPickUpXpEnabled(); }
  @Override public void    setPickUpXpEnabled(boolean e)       { fp.setPickUpXpEnabled(e); }

  @Override public boolean isNavParkour()                    { return fp.isNavParkour(); }
  @Override public void    setNavParkour(boolean e)          { fp.setNavParkour(e); }
  @Override public boolean isNavBreakBlocks()                { return fp.isNavBreakBlocks(); }
  @Override public void    setNavBreakBlocks(boolean e)      { fp.setNavBreakBlocks(e); }
  @Override public boolean isNavPlaceBlocks()                { return fp.isNavPlaceBlocks(); }
  @Override public void    setNavPlaceBlocks(boolean e)      { fp.setNavPlaceBlocks(e); }
  @Override public boolean isNavSprintJump()                 { return fp.isNavSprintJump(); }
  @Override public void    setNavSprintJump(boolean e)       { fp.setNavSprintJump(e); }
  @Override public int     getChunkLoadRadius()              { return fp.getChunkLoadRadius(); }
  @Override public void    setChunkLoadRadius(int r)         { fp.setChunkLoadRadius(r); }

  @Override public boolean isPveEnabled()                    { return fp.isPveEnabled(); }
  @Override public void    setPveEnabled(boolean e)          { fp.setPveEnabled(e); }
  @Override public double  getPveRange()                     { return fp.getPveRange(); }
  @Override public void    setPveRange(double r)             { fp.setPveRange(r); }
  @Override public @Nullable String getPvePriority()         { return fp.getPvePriority(); }
  @Override public void    setPvePriority(@Nullable String p){ fp.setPvePriority(p); }

  @Override public @NotNull String getSpawnedBy() {
    String s = fp.getSpawnedBy();
    return s != null ? s : "CONSOLE";
  }

  @Override public @NotNull UUID getSpawnedByUuid() {
    UUID u = fp.getSpawnedByUuid();
    return u != null ? u : new UUID(0, 0);
  }

  @Override public boolean isOwnedBy(@NotNull UUID playerUuid) {
    return playerUuid.equals(getSpawnedByUuid());
  }

  @Override public boolean hasControllerAccess(@NotNull UUID playerUuid) {
    return isOwnedBy(playerUuid) || fp.hasSharedController(playerUuid);
  }

  @Override public @NotNull Set<UUID> getSharedControllerUuids() {
    return fp.getSharedControllers();
  }

  @Override public boolean grantControllerAccess(@NotNull UUID playerUuid) {
    return fp.addSharedController(playerUuid);
  }

  @Override public boolean revokeControllerAccess(@NotNull UUID playerUuid) {
    return fp.removeSharedController(playerUuid);
  }

  @Override public @NotNull Duration getUptime()       { return fp.getUptime(); }
  @Override public int               getDeathCount()   { return fp.getDeathCount(); }
  @Override public double            getTotalDamageTaken() { return fp.getTotalDamageTaken(); }

  @Override public boolean isInWater() {
    Player ent = fp.getPhysicsEntity();
    return ent != null && ent.isInWater();
  }

  @Override public boolean isInLava() {
    Player ent = fp.getPhysicsEntity();
    return ent != null && ent.isInLava();
  }

  @Override public boolean isSprinting() {
    Player ent = fp.getPhysicsEntity();
    return ent != null && ent.isSprinting();
  }

  @Override public int getPing() { return fp.getPing(); }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FppBotImpl other)) return false;
    return fp.getUuid().equals(other.fp.getUuid());
  }

  @Override public int    hashCode() { return fp.getUuid().hashCode(); }
  @Override public String toString() { return "FppBot{" + fp.getName() + "/" + fp.getUuid() + "}"; }
}

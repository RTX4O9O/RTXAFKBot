package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class FakePlayerBody {

  public static final String VISUAL_PDC_VALUE = "fpp-visual";

  public static final String NAMETAG_PDC_VALUE = "fpp-nametag";

  private FakePlayerBody() {}

  public static Player spawn(FakePlayer fp, Location loc) {
    if (loc == null || loc.getWorld() == null) return null;
    try {

      Player player =
          NmsPlayerSpawner.spawnFakePlayer(
              fp.getUuid(),
              fp.getName(),
              fp.getResolvedSkin(),
              loc.getWorld(),
              loc.getX(),
              loc.getY(),
              loc.getZ());

      if (player == null) {
        FppLogger.warn("FakePlayerBody.spawn: NmsPlayerSpawner returned null for " + fp.getName());
        return null;
      }

      finalizeSpawnedBody(fp, player);
      return player;

    } catch (Exception e) {
      FppLogger.error("FakePlayerBody.spawn failed for " + fp.getName() + ": " + e.getMessage());
      return null;
    }
  }

  /**
   * Folia-safe async body spawn. Dispatches NMS placement to the destination chunk's region
   * thread, then invokes the callback with the resulting Bukkit Player (or null on failure).
   */
  public static void spawnAsync(FakePlayer fp, Location loc, java.util.function.Consumer<Player> callback) {
    if (loc == null || loc.getWorld() == null) {
      callback.accept(null);
      return;
    }
    NmsPlayerSpawner.spawnFakePlayerAsync(
        fp.getUuid(),
        fp.getName(),
        fp.getResolvedSkin(),
        loc.getWorld(),
        loc.getX(),
        loc.getY(),
        loc.getZ(),
        player -> {
          if (player == null) {
            FppLogger.warn(
                "FakePlayerBody.spawnAsync: NmsPlayerSpawner returned null for " + fp.getName());
            callback.accept(null);
            return;
          }
          try {
            finalizeSpawnedBody(fp, player);
          } catch (Exception e) {
            FppLogger.error(
                "FakePlayerBody.spawnAsync finalize failed for "
                    + fp.getName()
                    + ": "
                    + e.getMessage());
          }
          callback.accept(player);
        });
  }

  private static void finalizeSpawnedBody(FakePlayer fp, Player player) {
    try {
      if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
        player
            .getPersistentDataContainer()
            .set(
                FakePlayerManager.FAKE_PLAYER_KEY,
                PersistentDataType.STRING,
                VISUAL_PDC_VALUE + ":" + fp.getName());
      }
    } catch (Exception e) {
      FppLogger.debug(
          "FakePlayerBody.spawn: PDC tag failed for " + fp.getName() + ": " + e.getMessage());
    }

    player.setGravity(true);
    player.setInvulnerable(false);
    player.setCollidable(true);
    player.setCanPickupItems(fp.isPickUpItemsEnabled());

    String displayName =
        fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
    if (displayName != null && !displayName.isEmpty()) {
      try {
        player.displayName(me.bill.fakePlayerPlugin.util.TextUtil.colorize(displayName));
      } catch (Exception e) {
        FppLogger.debug(
            "Failed to set display name for " + fp.getName() + ": " + e.getMessage());
      }
    }

    try {
      var maxHpAttr =
          player.getAttribute(me.bill.fakePlayerPlugin.util.AttributeCompat.MAX_HEALTH);
      if (maxHpAttr != null) {
        double hp = Config.maxHealth();
        maxHpAttr.setBaseValue(hp);
        player.setHealth(hp);
      }
    } catch (Exception ignored) {
    }

    player.setAllowFlight(false);
    player.setFlying(false);

    boolean shouldNudgeDown = true;
    try {
      shouldNudgeDown = !player.isInWater() && !player.isInLava();
    } catch (Throwable ignored) {
    }
    if (shouldNudgeDown) {
      player.setVelocity(new org.bukkit.util.Vector(0, -0.001, 0));
    }

    Config.debug(
        "FakePlayerBody: spawned "
            + fp.getName()
            + " (gravity=true, damageable="
            + Config.bodyDamageable()
            + ", flying=false)");

    applyPaperSkin(player, fp.getResolvedSkin());
  }

  public static void removeAll(FakePlayer fp) {
    removeAll(fp, false);
  }

  public static void removeAllFast(FakePlayer fp) {
    removeAll(fp, true);
  }

  private static void removeAll(FakePlayer fp, boolean fast) {
    if (fp == null) return;
    try {
      Player player = fp.getPlayer();
      if (player != null && player.isOnline()) {
        if (fast) NmsPlayerSpawner.removeFakePlayerFast(player);
        else NmsPlayerSpawner.removeFakePlayer(player);
      }
    } catch (Exception e) {
      FppLogger.error(
          "FakePlayerBody.removeAll failed for "
              + (fp.getName() != null ? fp.getName() : "?")
              + ": "
              + e.getMessage());
    }
  }

  public static void resolveAndFinish(
      Plugin plugin,
      FakePlayer fp,
      Location loc,
      Runnable onReady,
      @org.jetbrains.annotations.Nullable Runnable onSkinApplied) {
    me.bill.fakePlayerPlugin.FakePlayerPlugin fpp =
        me.bill.fakePlayerPlugin.FakePlayerPlugin.getInstance();
    me.bill.fakePlayerPlugin.fakeplayer.SkinManager skinManager =
        fpp != null ? fpp.getSkinManager() : null;

    if (skinManager == null) {
      onReady.run();
      return;
    }

    if (fp.getResolvedSkin() == null || !fp.getResolvedSkin().isValid()) {
      me.bill.fakePlayerPlugin.fakeplayer.SkinProfile cached = skinManager.getCachedSkinForBot(fp);
      if (cached != null && cached.isValid()) {
        fp.setResolvedSkin(cached);
      }
    }

    onReady.run();

    String rawMode = me.bill.fakePlayerPlugin.config.Config.skinMode().trim().toLowerCase();
    if ("off".equals(rawMode) || "disabled".equals(rawMode) || "none".equals(rawMode)) {
      return;
    }

    skinManager.resolveEffectiveSkin(
        fp,
        skin -> {
          if (skin == null || !skin.isValid()) {
            if (onSkinApplied != null) onSkinApplied.run();
            return;
          }
          FppScheduler.runSyncLater(
              plugin,
              () -> {
                Player body = fp.getPlayer();
                if (body == null || !body.isOnline()) return;
                skinManager.applySkinFromProfile(fp, skin);
                if (onSkinApplied != null) onSkinApplied.run();
              },
              3L);
        });
  }

  public static void resolveAndFinish(
      Plugin plugin, FakePlayer fp, Location loc, Runnable onReady) {
    resolveAndFinish(plugin, fp, loc, onReady, null);
  }

  public static void applyResolvedSkin(
      Plugin plugin, FakePlayer fp, org.bukkit.entity.Entity body) {
    if (!(body instanceof Player player)) return;
    me.bill.fakePlayerPlugin.FakePlayerPlugin fpp =
        me.bill.fakePlayerPlugin.FakePlayerPlugin.getInstance();
    if (fpp != null && fpp.getSkinManager() != null) {
      fpp.getSkinManager().applySkinFromProfile(fp, fp.getResolvedSkin());
      return;
    }
    applyPaperSkin(player, fp.getResolvedSkin());
  }

  private static void applyPaperSkin(Player bot, SkinProfile skin) {
    if (skin == null || !skin.isValid()) return;
    me.bill.fakePlayerPlugin.FakePlayerPlugin fpp =
        me.bill.fakePlayerPlugin.FakePlayerPlugin.getInstance();
    if (fpp != null && fpp.getSkinManager() != null) {
      me.bill.fakePlayerPlugin.fakeplayer.FakePlayer fp =
          fpp.getFakePlayerManager() != null
              ? fpp.getFakePlayerManager().getByUuid(bot.getUniqueId())
              : null;
      if (fp != null && fpp.getSkinManager().applySkinFromProfile(fp, skin)) {
        Config.debugSkin(
            "FakePlayerBody: skin-manager skin applied to "
                + bot.getName()
                + " ("
                + skin.getSource()
                + ")");
        return;
      }
    }
    try {
      var profile = bot.getPlayerProfile();
      profile.removeProperty("textures");
      profile.setProperty(
          new com.destroystokyo.paper.profile.ProfileProperty(
              "textures", skin.getValue(), skin.getSignature() != null ? skin.getSignature() : ""));
      bot.setPlayerProfile(profile);
      Config.debugSkin(
          "FakePlayerBody: paper skin applied to " + bot.getName() + " (" + skin.getSource() + ")");

      NmsPlayerSpawner.forceAllSkinParts(bot);
    } catch (Exception e) {
      FppLogger.debug(
          "FakePlayerBody: paper skin apply failed for " + bot.getName() + ": " + e.getMessage());
    }
  }

  public static void removeNametag(FakePlayer fp) {}

  public static org.bukkit.entity.Entity spawnNametag(
      FakePlayer fp, org.bukkit.entity.Entity body) {

    return null;
  }

  public static void removeOrphanedNametags(String reason) {}

  public static void removeOrphanedBodies(String reason) {}
}

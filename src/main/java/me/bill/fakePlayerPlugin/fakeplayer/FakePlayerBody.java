package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-entity stack per fake player:
 * <pre>
 *   ArmorStand  (nametag — invisible, rides Mannequin)
 *       ↓ rides
 *   Mannequin   (physics body + skin)
 * </pre>
 *
 * <h3>Skin pipeline</h3>
 * <p>Skins appear in TWO places:
 * <ol>
 *   <li>The <b>Mannequin body</b> — set via {@code Mannequin.setProfile()}.</li>
 *   <li>The <b>tab-list packet</b> — the {@code GameProfile} inside
 *       {@code ClientboundPlayerInfoUpdatePacket} must carry the {@code textures}
 *       property for the player icon to show correctly.</li>
 * </ol>
 * <p>After a skin is resolved it is stored on {@link FakePlayer#setResolvedSkin}
 * so {@link PacketHelper#sendTabListAdd} can inject the texture properties.
 *
 * <h3>Skin modes (config: {@code skin.mode})</h3>
 * <dl>
 *   <dt>{@code auto}</dt><dd>Paper resolves skin from Mojang automatically by name.
 *       Only works when {@code skinName} is a real Mojang account name.</dd>
 *   <dt>{@code custom}</dt><dd>Full pipeline: name override → folder → pool → Mojang fallback.</dd>
 *   <dt>{@code off}</dt><dd>No skin — Steve/Alex appearance.</dd>
 * </dl>
 */
@SuppressWarnings("UnstableApiUsage")
public final class FakePlayerBody {

    public static final String NAMETAG_PDC_VALUE = "fpp_nametag";
    public static final String VISUAL_PDC_VALUE  = "fpp_visual";

    private FakePlayerBody() {}

    // ── Spawn ─────────────────────────────────────────────────────────────────

    public static Entity spawn(FakePlayer fp, Location loc) {
        try {
            return loc.getWorld().spawn(loc, Mannequin.class, m -> {
                m.setGravity(true);
                m.setInvulnerable(false);
                m.setImmovable(false);
                m.setCollidable(true);
                m.setRemoveWhenFarAway(false);
                m.setSilent(true);
                m.setCustomNameVisible(false);
                m.customName(null);

                var maxHp = m.getAttribute(Attribute.MAX_HEALTH);
                if (maxHp != null) maxHp.setBaseValue(Config.maxHealth());
                m.setHealth(Config.maxHealth());

                m.getPersistentDataContainer().set(
                        FakePlayerManager.FAKE_PLAYER_KEY,
                        PersistentDataType.STRING,
                        fp.getName()
                );
            });
        } catch (Exception e) {
            FppLogger.warn("FakePlayerBody.spawn failed for " + fp.getName() + ": " + e.getMessage());
            return null;
        }
    }

    // ── Skin ──────────────────────────────────────────────────────────────────

    /**
     * Resolves the bot's skin asynchronously, stores it on {@code fp}, then
     * calls {@code onReady} on the main thread.  Used by finishSpawn so the
     * tab-list packet is only sent AFTER skin data is available — preventing
     * the "default Steve flash" that occurred when the packet was sent first.
     *
     * <p>When {@code skin.guaranteed-skin} is {@code true}, the system always
     * applies some skin. If the primary lookup fails (name not on Mojang,
     * empty pool, network error), it falls through to the guaranteed fallback
     * chain: folder skins → pool skins → pre-loaded fallback name → on-demand
     * fallback fetch. Only {@code off} mode skips this entirely.
     */
    public static void resolveAndFinish(Plugin plugin, FakePlayer fp,
                                        Location loc, Runnable onReady) {
        String mode     = Config.skinMode();
        String skinName = fp.getSkinName();

        switch (mode) {
            case "off", "disabled" -> {
                fp.setResolvedSkin(null);
                onReady.run();
            }
            case "custom", "fetch" -> {
                SkinRepository.get().resolve(skinName, resolved ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (resolved != null && resolved.isValid()) {
                            fp.setResolvedSkin(resolved);
                            FppLogger.debug("Skin(custom) pre-resolved for " + skinName + ".");
                            onReady.run();
                        } else {
                            // Custom pipeline found nothing — fall back to direct Mojang fetch
                            SkinFetcher.fetchAsync(skinName, (value, sig) ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (value != null && !value.isBlank()) {
                                        fp.setResolvedSkin(new SkinProfile(value, sig, "auto:" + skinName));
                                        FppLogger.debug("Skin(custom→auto-fallback) resolved for " + skinName + ".");
                                        onReady.run();
                                    } else if (Config.skinGuaranteed()) {
                                        // All custom + Mojang sources failed — use guaranteed skin
                                        FppLogger.debug("Skin(custom→guaranteed) for " + skinName + " — applying any available skin.");
                                        SkinRepository.get().getAnyValidSkin(fallback ->
                                            Bukkit.getScheduler().runTask(plugin, () -> {
                                                fp.setResolvedSkin(fallback);
                                                if (fallback != null)
                                                    FppLogger.debug("Skin(guaranteed) applied for " + skinName + " → " + fallback.getSource());
                                                else
                                                    FppLogger.debug("Skin(guaranteed): nothing available for " + skinName + " — Steve/Alex.");
                                                onReady.run();
                                            })
                                        );
                                    } else {
                                        fp.setResolvedSkin(null);
                                        onReady.run();
                                    }
                                })
                            );
                        }
                    })
                );
            }
            default -> { // "auto" — Mojang resolves skin by name; guaranteed fallback for unknown names
                SkinFetcher.fetchAsync(skinName, (value, sig) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (value != null && !value.isBlank()) {
                            fp.setResolvedSkin(new SkinProfile(value, sig, "auto:" + skinName));
                            FppLogger.debug("Skin(auto) pre-resolved for " + skinName + ".");
                            onReady.run();
                        } else if (Config.skinGuaranteed()) {
                            // Name not on Mojang (generated name, user bot, etc.) — use guaranteed skin
                            FppLogger.debug("Skin(auto→guaranteed) for " + skinName
                                    + " — name has no Mojang account, applying fallback.");
                            SkinRepository.get().getAnyValidSkin(fallback ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    fp.setResolvedSkin(fallback);
                                    if (fallback != null)
                                        FppLogger.debug("Skin(guaranteed) applied for " + skinName + " → " + fallback.getSource());
                                    else
                                        FppLogger.debug("Skin(guaranteed): nothing available for " + skinName + " — Steve/Alex.");
                                    onReady.run();
                                })
                            );
                        } else {
                            fp.setResolvedSkin(null);
                            onReady.run();
                        }
                    })
                );
            }
        }
    }

    /**
     * Applies an already-resolved skin (from {@code fp.getResolvedSkin()}) to the
     * Mannequin body.  Called 1 tick after spawn — skin is guaranteed resolved
     * because {@link #resolveAndFinish} ran before finishSpawn continued.
     */
    public static void applyResolvedSkin(Plugin plugin, FakePlayer fp, Entity body) {
        applyToMannequin(body, fp.getSkinName(), fp.getResolvedSkin());
    }

    /**
     * Legacy respawn path — resolves + applies + resends tab on already-live body.
     */
    public static void applySkin(Plugin plugin, FakePlayer fp, Entity body) {
        String mode     = Config.skinMode();
        String skinName = fp.getSkinName();
        switch (mode) {
            case "auto"            -> resolveAutoAndApply(plugin, fp, body, skinName);
            case "custom", "fetch" -> resolveCustomAndApply(plugin, fp, body, skinName);
            case "off", "disabled" -> { /* nothing */ }
            default                -> resolveAutoAndApply(plugin, fp, body, skinName);
        }
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    private static void resolveAutoAndApply(Plugin plugin, FakePlayer fp, Entity body, String skinName) {
        SkinFetcher.fetchAsync(skinName, (value, sig) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (value != null && !value.isBlank()) {
                    SkinProfile resolved = new SkinProfile(value, sig, "auto:" + skinName);
                    fp.setResolvedSkin(resolved);
                    applyToMannequin(body, skinName, resolved);
                    resendTab(fp);
                } else if (Config.skinGuaranteed()) {
                    SkinRepository.get().getAnyValidSkin(fallback ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            fp.setResolvedSkin(fallback);
                            applyToMannequin(body, skinName, fallback);
                            resendTab(fp);
                        })
                    );
                } else {
                    fp.setResolvedSkin(null);
                    applyToMannequin(body, skinName, null);
                    resendTab(fp);
                }
            })
        );
    }

    private static void resolveCustomAndApply(Plugin plugin, FakePlayer fp, Entity body, String skinName) {
        SkinRepository.get().resolve(skinName, skin ->
                applyOnMainThread(plugin, fp, body, skinName, skin));
    }

    private static void applyOnMainThread(Plugin plugin, FakePlayer fp, Entity body,
                                          String skinName, SkinProfile skin) {
        Bukkit.getScheduler().runTask(plugin,
                () -> applyResolvedCustom(plugin, fp, body, skinName, skin));
    }

    private static void applyResolvedCustom(Plugin plugin, FakePlayer fp, Entity body,
                                             String skinName, SkinProfile resolved) {
        if (resolved == null || !resolved.isValid()) {
            resolveAutoAndApply(plugin, fp, body, skinName);
            return;
        }
        fp.setResolvedSkin(resolved);
        applyToMannequin(body, skinName, resolved);
        resendTab(fp);
    }

    // ── Low-level helpers ─────────────────────────────────────────────────────

    // Applies skin to body if it is a valid Mannequin; null skin falls back to auto profile.
    private static void applyToMannequin(Entity body, String skinName, SkinProfile skin) {
        if (!(body instanceof Mannequin m) || !m.isValid()) return;
        if (skin != null && skin.isValid()) setProfileWithSkin(m, skinName, skin);
        else                                setProfileAuto(m, skinName);
    }

    private static void setProfileWithSkin(Mannequin m, String skinName, SkinProfile sp) {
        try {
            String sig = sp.getSignature();
            m.setProfile(ResolvableProfile.resolvableProfile()
                    .name(skinName)
                    .addProperty(new ProfileProperty("textures", sp.getValue(),
                            (sig != null && !sig.isBlank()) ? sig : null))
                    .build());
        } catch (Exception e) {
            FppLogger.warn("setProfileWithSkin failed for '" + skinName + "': " + e.getMessage());
            setProfileAuto(m, skinName);
        }
    }

    private static void setProfileAuto(Mannequin m, String skinName) {
        try {
            m.setProfile(ResolvableProfile.resolvableProfile().name(skinName).build());
        } catch (Exception e) {
            FppLogger.warn("setProfileAuto failed for '" + skinName + "': " + e.getMessage());
        }
    }

    private static void resendTab(FakePlayer fp) {
        if (!Config.tabListEnabled()) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : online) PacketHelper.sendTabListAdd(p, fp);
    }

    // ── Nametag ───────────────────────────────────────────────────────────────

    /**
     * Spawns an invisible marker ArmorStand that rides the Mannequin.
     * Riding keeps the nametag always above the body without any tick-sync code.
     */
    public static ArmorStand spawnNametag(FakePlayer fp, Entity body) {
        if (body == null || !body.isValid()) return null;
        try {
            ArmorStand as = body.getWorld().spawn(body.getLocation(), ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setSmall(false);
                stand.setMarker(true);
                stand.setInvulnerable(true);
                stand.setRemoveWhenFarAway(false);
                stand.setSilent(true);
                stand.setCollidable(false);

                // Use MiniMessage color parsing for custom nametag color
                stand.customName(me.bill.fakePlayerPlugin.util.TextUtil.colorize(fp.getDisplayName()));
                stand.setCustomNameVisible(true);

                stand.getPersistentDataContainer().set(
                        FakePlayerManager.FAKE_PLAYER_KEY,
                        PersistentDataType.STRING,
                        NAMETAG_PDC_VALUE + ":" + fp.getName()
                );
            });
            body.addPassenger(as);
            return as;
        } catch (Exception e) {
            FppLogger.warn("FakePlayerBody.spawnNametag failed for " + fp.getName() + ": " + e.getMessage());
            return null;
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Removes the nametag ArmorStand for {@code fp}.
     * Falls back to a world-scan if the direct reference is stale.
     */
    public static void removeNametag(FakePlayer fp) {
        ArmorStand as = fp.getNametagEntity();
        if (as != null) {
            try { as.eject(); } catch (Exception ignored) {}
            try { as.remove(); } catch (Exception ignored) {}
        }
        fp.setNametagEntity(null);

        // World-scan fallback — catches orphaned ArmorStands whose reference was lost.
        removeOrphanedNametags(fp.getName());
    }

    /**
     * Removes the body Mannequin and nametag ArmorStand for {@code fp}.
     *
     * <p>Uses a two-phase approach:
     * <ol>
     *   <li>Remove via the stored entity reference (fast path).</li>
     *   <li>Scan every loaded world for any entity that carries this bot's
     *       PDC key — catches orphans left by chunk unload/reload cycles or
     *       from a previous crash (slow path, only iterates loaded entities).</li>
     * </ol>
     */
    public static void removeAll(FakePlayer fp) {
        // 1. Remove nametag ArmorStand
        removeNametag(fp);

        // 2. Remove physics body
        Entity body = fp.getPhysicsEntity();
        if (body != null) {
            try { body.remove(); } catch (Exception ignored) {}
        }
        fp.setPhysicsEntity(null);

        // 3. World-scan fallback — removes any leftover body entities too
        removeOrphanedBodies(fp.getName());
    }

    /**
     * Scans all loaded worlds for ArmorStand nametag entities that belong
     * to the bot named {@code botName} and removes them.
     * Safe to call even if no orphans exist — iterates only loaded entities.
     */
    public static void removeOrphanedNametags(String botName) {
        if (FakePlayerManager.FAKE_PLAYER_KEY == null || botName == null) return;
        String expectedTag = NAMETAG_PDC_VALUE + ":" + botName;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.isDead()) continue;
                String val = entity.getPersistentDataContainer()
                        .get(FakePlayerManager.FAKE_PLAYER_KEY,
                                org.bukkit.persistence.PersistentDataType.STRING);
                if (expectedTag.equals(val)) {
                    try { entity.remove(); } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * Scans all loaded worlds for Mannequin body entities that belong to
     * the bot named {@code botName} and removes them.
     */
    public static void removeOrphanedBodies(String botName) {
        if (FakePlayerManager.FAKE_PLAYER_KEY == null || botName == null) return;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.isDead()) continue;
                if (entity instanceof org.bukkit.entity.ArmorStand) continue; // nametag handled separately
                String val = entity.getPersistentDataContainer()
                        .get(FakePlayerManager.FAKE_PLAYER_KEY,
                                org.bukkit.persistence.PersistentDataType.STRING);
                if (botName.equals(val)) {
                    try { entity.remove(); } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * Removes ALL entities across all loaded worlds that carry any FPP PDC key
     * but are not tracked by the given set of active bot names.
     * Used for the periodic orphan sweep and crash recovery.
     *
     * @param activeBotNames set of currently registered bot internal names
     * @return number of orphaned entities removed
     */
    public static int sweepOrphans(java.util.Set<String> activeBotNames) {
        if (FakePlayerManager.FAKE_PLAYER_KEY == null) return 0;
        int removed = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.isDead()) continue;
                String val = entity.getPersistentDataContainer()
                        .get(FakePlayerManager.FAKE_PLAYER_KEY,
                                org.bukkit.persistence.PersistentDataType.STRING);
                if (val == null) continue;

                // Determine the bot name this entity belongs to
                String ownerName;
                if (val.startsWith(NAMETAG_PDC_VALUE + ":")) {
                    ownerName = val.substring((NAMETAG_PDC_VALUE + ":").length());
                } else if (val.startsWith(VISUAL_PDC_VALUE + ":")) {
                    ownerName = val.substring((VISUAL_PDC_VALUE + ":").length());
                } else {
                    ownerName = val; // plain name = body entity
                }

                if (!activeBotNames.contains(ownerName)) {
                    try { entity.remove(); } catch (Exception ignored) {}
                    removed++;
                }
            }
        }
        return removed;
    }
}

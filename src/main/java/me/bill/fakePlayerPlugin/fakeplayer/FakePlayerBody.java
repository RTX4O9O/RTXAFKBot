package me.bill.fakePlayerPlugin.fakeplayer;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Two-entity stack per fake player:
 *
 * <pre>
 *   ArmorStand  (nametag — invisible marker, rides Mannequin)
 *       ↓ rides
 *   Mannequin   (physics body + skin)
 *               setImmovable(false) → vanilla entity-separation push/knockback
 *               setGravity(true)    → falls naturally
 * </pre>
 *
 * <h3>Skin modes (config: {@code fake-player.skin.mode})</h3>
 * <dl>
 *   <dt>{@code auto} <i>(default)</i></dt>
 *   <dd>{@code ResolvableProfile.resolvableProfile().name(name).build()} — no UUID,
 *       no properties → Paper creates a Dynamic profile and resolves the UUID + skin
 *       texture from Mojang automatically. Requires online-mode server.</dd>
 *
 *   <dt>{@code fetch}</dt>
 *   <dd>Uses {@link SkinFetcher} to pull texture value+signature from Mojang,
 *       then builds a {@code ResolvableProfile} with the texture property injected.
 *       Works on offline-mode servers. Results are cached per session.</dd>
 *
 *   <dt>{@code disabled}</dt>
 *   <dd>No skin — bots display the default Steve/Alex skin.</dd>
 * </dl>
 */
@SuppressWarnings("UnstableApiUsage")
public final class FakePlayerBody {

    public static final String NAMETAG_PDC_VALUE = "fpp_nametag";
    public static final String VISUAL_PDC_VALUE  = "fpp_visual"; // compat shim

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
     * Applies the skin to {@code body} according to the configured skin mode.
     * Must be called at least 1 tick after {@link #spawn}.
     */
    public static void applySkin(Plugin plugin, Entity body, String name) {
        switch (Config.skinMode()) {
            case "auto"     -> applyAuto(body, name);
            case "fetch"    -> applyFetch(plugin, body, name);
            case "disabled" -> FppLogger.debug("Skin disabled for " + name + ".");
            default -> {
                FppLogger.warn("Unknown skin.mode '" + Config.skinMode() + "' — using auto.");
                applyAuto(body, name);
            }
        }
    }

    // ── auto ──────────────────────────────────────────────────────────────────

    /**
     * name-only ResolvableProfile (no UUID, no properties) →
     * Paper treats this as Dynamic and resolves UUID + skin from Mojang.
     */
    private static void applyAuto(Entity body, String name) {
        if (!(body instanceof Mannequin m) || !m.isValid()) return;
        try {
            m.setProfile(ResolvableProfile.resolvableProfile()
                    .name(name)
                    .build());
            FppLogger.debug("Skin(auto) queued for " + name + ".");
        } catch (Exception e) {
            FppLogger.warn("Skin(auto) failed for " + name + ": " + e.getMessage());
        }
    }

    // ── fetch ─────────────────────────────────────────────────────────────────

    /**
     * Fetches texture value+signature from Mojang via {@link SkinFetcher},
     * then builds a ResolvableProfile with the texture property injected and
     * calls {@code Mannequin.setProfile()} on the main thread.
     */
    private static void applyFetch(Plugin plugin, Entity body, String name) {
        if (!(body instanceof Mannequin m) || !m.isValid()) return;

        SkinFetcher.fetchAsync(name, (value, signature) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!m.isValid()) return;
                if (value == null) {
                    FppLogger.debug("Skin(fetch): no Mojang skin for '" + name + "' — falling back to auto.");
                    applyAuto(m, name);
                    return;
                }
                try {
                    m.setProfile(ResolvableProfile.resolvableProfile()
                            .name(name)
                            .addProperty(new ProfileProperty("textures", value,
                                    (signature != null && !signature.isBlank()) ? signature : null))
                            .build());
                    FppLogger.debug("Skin(fetch) applied for " + name + ".");
                } catch (Exception e) {
                    FppLogger.warn("Skin(fetch) inject failed for '" + name + "': " + e.getMessage());
                    applyAuto(m, name);
                }
            })
        );
    }

    // ── Nametag ───────────────────────────────────────────────────────────────

    /**
     * Spawns an invisible marker ArmorStand that rides the Mannequin.
     * Riding keeps the nametag always above the body without any tick-sync code.
     * The name is rendered in white to match vanilla player nametag style.
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

    public static void removeNametag(FakePlayer fp) {
        ArmorStand as = fp.getNametagEntity();
        if (as != null && as.isValid()) { as.eject(); as.remove(); }
        fp.setNametagEntity(null);
    }


    public static void removeAll(FakePlayer fp) {
        removeNametag(fp);
        Entity body = fp.getPhysicsEntity();
        if (body != null && body.isValid()) body.remove();
        fp.setPhysicsEntity(null);
    }
}

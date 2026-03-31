package me.bill.fakePlayerPlugin.listener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerBody;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides three layers of physical interaction for NMS ServerPlayer-based bots.
 *
 * <p>All tuning values are read from config on every event/tick so they
 * are hot-reloadable via {@code /fpp reload} with no restart needed:
 * <ul>
 *   <li>{@code fake-player.collision.walk-radius}</li>
 *   <li>{@code fake-player.collision.walk-strength}</li>
 *   <li>{@code fake-player.collision.max-horizontal-speed}</li>
 *   <li>{@code fake-player.collision.hit-strength}</li>
 *   <li>{@code fake-player.collision.bot-radius}</li>
 *   <li>{@code fake-player.collision.bot-strength}</li>
 * </ul>
 */
public class BotCollisionListener implements Listener {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    private static final double VANILLA_ATTACK_UPWARD = 0.40D;
    private static final double PLAYER_ATTACK_MULTIPLIER = 1.60D;
    private static final double PLAYER_SPRINT_BONUS = 0.28D;
    private static final double PLAYER_REINFORCE_SCALE = 0.80D;

    public BotCollisionListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickBotSeparation, 1L, 1L);
    }

    // ── 1. Hit knockback ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!Config.bodyPushable()) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isFakeBody(target)) return;

        Entity attacker = resolveKnockbackSource(event.getDamager());
        if (attacker == null) return;

        // Keep player-caused knockback even when damage is cancelled by PvP rules
        // or no-damage ticks; for non-player sources respect normal cancellation
        // when bots are damageable.
        boolean fromPlayer = attacker instanceof Player;
        if (event.isCancelled() && Config.bodyDamageable() && !fromPlayer) return;

        double hitStrength = Config.collisionHitStrength();
        double maxHoriz    = Config.collisionMaxHoriz();

        Location aLoc = attacker.getLocation();
        Location bLoc = target.getLocation();
        double dx = bLoc.getX() - aLoc.getX();
        double dz = bLoc.getZ() - aLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double upward = fromPlayer ? 0.48D : VANILLA_ATTACK_UPWARD;
        Vector kb = computeHorizontalKnockback(attacker, target, dx, dz, dist, upward);
        if (fromPlayer && attacker instanceof Player p && p.isSprinting()) {
            kb = scaleHorizontal(kb, (hitStrength * PLAYER_ATTACK_MULTIPLIER) + PLAYER_SPRINT_BONUS);
        } else if (fromPlayer) {
            kb = scaleHorizontal(kb, hitStrength * PLAYER_ATTACK_MULTIPLIER);
        } else {
            kb = scaleHorizontal(kb, hitStrength);
        }

        // Player hits use the same direct simulated impulse path as mobs so fake bots
        // always react immediately, even when Paper's player knockback event is missing.
        applyImpulseBurst(target, kb, maxHoriz, fromPlayer);
    }

    // ── 1b. Explosion fallback knockback ───────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!Config.bodyPushable()) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isFakeBody(target)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        // Entity-based explosions are already handled in onEntityDamageByEntity.
        if (event instanceof EntityDamageByEntityEvent) return;

        if (event.isCancelled() && Config.bodyDamageable()) return;

        double maxHoriz    = Config.collisionMaxHoriz();
        double hitStrength = Config.collisionHitStrength();

        // For block explosions we have no reliable source entity position.
        // Apply a conservative outward shove from current facing + upward lift.
        Vector facing = target.getLocation().getDirection();
        Vector kb = new Vector(facing.getX(), 0.45, facing.getZ()).normalize().multiply(hitStrength * 0.7);
        applyImpulse(target, kb.getX(), kb.getY(), kb.getZ(), maxHoriz, 1.0);
    }

    // ── 2. Walk-into push ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!Config.bodyPushable()) return;
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (Math.abs(to.getX() - from.getX()) < 1e-6
                && Math.abs(to.getZ() - from.getZ()) < 1e-6) return;

        double walkRadius   = Config.collisionWalkRadius();
        double walkStrength = Config.collisionWalkStrength();
        double maxHoriz     = Config.collisionMaxHoriz();

        Player player = event.getPlayer();
        Location pLoc = player.getLocation();

        for (FakePlayer fp : manager.getActivePlayers()) {
            Player body = fp.getPlayer();
            if (body == null || !body.isValid()) continue;
            if (!body.getWorld().equals(player.getWorld())) continue;

            Location bLoc = body.getLocation();
            double dy = pLoc.getY() - bLoc.getY();
            if (dy > 2.2 || dy < -1.2) continue;

            double dx = bLoc.getX() - pLoc.getX();
            double dz = bLoc.getZ() - pLoc.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > walkRadius * walkRadius || distSq < 1e-12) continue;

            double dist     = Math.sqrt(distSq);
            double overlap  = 1.0 - (dist / walkRadius);
            double strength = walkStrength * overlap;

            applyImpulse(body, (dx / dist) * strength, (dz / dist) * strength, maxHoriz);
        }
    }

    // ── 3. Bot-vs-bot separation ──────────────────────────────────────────────

    private void tickBotSeparation() {
        if (!Config.bodyPushable()) return;
        Collection<FakePlayer> all = manager.getActivePlayers();
        if (all.size() < 2) return;

        double botRadius   = Config.collisionBotRadius();
        double botStrength = Config.collisionBotStrength();
        double maxHoriz    = Config.collisionMaxHoriz();

        List<FakePlayer> bots = new ArrayList<>(all);
        for (int i = 0; i < bots.size(); i++) {
            Player bodyA = bots.get(i).getPlayer();
            if (bodyA == null || !bodyA.isValid()) continue;
            Location locA = bodyA.getLocation();

            for (int j = i + 1; j < bots.size(); j++) {
                Player bodyB = bots.get(j).getPlayer();
                if (bodyB == null || !bodyB.isValid()) continue;
                if (!bodyA.getWorld().equals(bodyB.getWorld())) continue;

                Location locB = bodyB.getLocation();
                double dy = locA.getY() - locB.getY();
                if (dy > 2.0 || dy < -2.0) continue;

                double dx     = locB.getX() - locA.getX();
                double dz     = locB.getZ() - locA.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq > botRadius * botRadius || distSq < 1e-12) continue;

                double dist     = Math.sqrt(distSq);
                double nx       = dx / dist;
                double nz       = dz / dist;
                double overlap  = 1.0 - (dist / botRadius);
                double strength = botStrength * overlap * 0.5;

                applyImpulse(bodyB,  nx * strength,  nz * strength, maxHoriz);
                applyImpulse(bodyA, -nx * strength, -nz * strength, maxHoriz);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void applyImpulse(Entity body, double ix, double iz, double maxHoriz) {
        applyImpulse(body, ix, 0.0, iz, maxHoriz, 0.9);
    }

    private void applyImpulseBurst(Player target, Vector kb, double maxHoriz, boolean reinforceNextTick) {
        applyImpulse(target, kb.getX(), kb.getY(), kb.getZ(), maxHoriz, 0.9);
        if (!reinforceNextTick) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!target.isValid() || !isFakeBody(target)) return;
            // Small fallback pulse for cancelled-PvP scenarios where another plugin
            // rewrites velocity after damage processing.
            applyImpulse(target, kb.getX() * PLAYER_REINFORCE_SCALE, kb.getY() * 0.15, kb.getZ() * PLAYER_REINFORCE_SCALE, maxHoriz, 0.9);
        }, 1L);
    }

    private static Vector computeHorizontalKnockback(Entity attacker, Player target,
                                                     double dx, double dz, double dist,
                                                     double upward) {
        if (dist >= 1e-4) {
            return new Vector(dx / dist, upward, dz / dist);
        }

        Vector fromFacing = attacker.getLocation().getDirection().setY(0);
        if (fromFacing.lengthSquared() < 1e-8) {
            fromFacing = target.getLocation().getDirection().setY(0);
        }
        if (fromFacing.lengthSquared() < 1e-8) {
            fromFacing = new Vector(1, 0, 0);
        }
        fromFacing.normalize();
        return new Vector(fromFacing.getX(), upward, fromFacing.getZ());
    }

    private static void applyImpulse(Entity body, double ix, double iy, double iz,
                                     double maxHoriz, double maxUpward) {
        Vector vel = body.getVelocity();
        double newX = vel.getX() + ix;
        double newY = vel.getY() + iy;
        double newZ = vel.getZ() + iz;

        double speed = Math.sqrt(newX * newX + newZ * newZ);
        if (speed > maxHoriz) {
            double scale = maxHoriz / speed;
            newX *= scale;
            newZ *= scale;
        }

        vel.setX(newX);
        if (newY > maxUpward) newY = maxUpward;
        if (newY < -4.0) newY = -4.0;
        vel.setY(newY);
        vel.setZ(newZ);
        body.setVelocity(vel);
    }

    private static Vector scaleHorizontal(Vector input, double multiplier) {
        double scale = Math.max(0.0, multiplier);
        return new Vector(input.getX() * scale, input.getY(), input.getZ() * scale);
    }

    private boolean isFakeBody(Entity entity) {
        if (!(entity instanceof Player)) return false;
        if (FakePlayerManager.FAKE_PLAYER_KEY == null) return false;
        String val = entity.getPersistentDataContainer()
                .get(FakePlayerManager.FAKE_PLAYER_KEY,
                        org.bukkit.persistence.PersistentDataType.STRING);
        return val != null && val.startsWith(FakePlayerBody.VISUAL_PDC_VALUE);
    }

    private static Entity resolveKnockbackSource(Entity damager) {
        if (damager == null) return null;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) return shooterEntity;
        }
        return damager;
    }

}

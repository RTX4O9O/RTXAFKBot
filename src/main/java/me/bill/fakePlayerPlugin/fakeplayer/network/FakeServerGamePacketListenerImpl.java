package me.bill.fakePlayerPlugin.fakeplayer.network;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Fake packet listener for bot players.
 *
 * <p>Extends {@link ServerGamePacketListenerImpl} so the server accepts it as a
 * valid connection object for a {@link ServerPlayer}.
 *
 * <p>Key behaviour: {@link #send(Packet)} discards ALL outbound packets except
 * {@link ClientboundSetEntityMotionPacket} (knockback), which is applied server-side.
 * Because {@code send()} is a no-op, {@code awaitingPositionFromClient} on this fresh
 * instance stays {@code null} — the server's {@code connection.tick()} never snaps the
 * bot back to a stale spawn position (the root cause of bots "floating").
 *
 * <h3>Knockback strategy resolution (one-time, lazy)</h3>
 * <ol>
 *   <li><b>GET_MOVEMENT</b> (1.21.9+): {@code packet.getMovement()} returns a {@code Vec3};
 *       apply via {@code player.lerpMotion(Vec3)}.  Matches the hello09x reference plugin.</li>
 *   <li><b>GET_XA</b> (≤ 1.21.8): {@code packet.getXa/Ya/Za()} (or {@code xa/ya/za()})
 *       return individual doubles; apply via {@code lerpMotion(double,double,double)} or,
 *       if that method is absent, via {@code setDeltaMovement(Vec3)}.</li>
 *   <li><b>NONE</b>: no compatible API found — knockback is silently skipped.</li>
 * </ol>
 *
 * <h3>Why {@code hurtMarked} is NOT checked in {@code applyKnockback()}</h3>
 * <p>In Paper 26.1.1+, NMS resets {@code hurtMarked = false} <em>before</em> calling
 * {@code send()} — meaning {@code hurtMarked} is always {@code false} by the time our
 * override runs.  Checking it would cause every knockback packet to be silently dropped.
 * Entity-ID filtering (when the ID is available) is sufficient to prevent applying another
 * entity's motion packet to this bot.
 *
 * <h3>Why two {@code send()} overload families</h3>
 * <p>NMS may call either {@code send(Packet)} or {@code send(Packet, PacketSendListener)}
 * depending on the MC version.  Both are intercepted here to ensure knockback is never missed.
 * The {@code PacketSendListener} overload intentionally omits {@code @Override} — that overload
 * does not exist on the 1.21.11 compile target's {@code ServerGamePacketListenerImpl} (it uses
 * {@code ChannelFutureListener} there), but Java runtime dispatch resolves it correctly when
 * the server runs Paper 26.1.1 where {@code PacketSendListener} is the active type.
 */
public final class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl {

    // ── Entity-ID accessor for ClientboundSetEntityMotionPacket ──────────────
    // MC 1.21.x–1.21.11 (compile target): getId() → int
    // Paper 26.1.1+: getId() removed — probe "getEntityId" / "id" once, then cache.
    // When no getter exists we return -1 and skip the entity-ID guard (safe because
    // hurtMarked is set only on the actually-hurt entity, so the motion packet
    // received by this listener is always for this.player in that case).

    /** Set to {@code true} once the direct {@code getId()} call has failed at runtime. */
    private static volatile boolean entityIdDirectFailed = false;
    /** Whether the reflection fallback has already been probed (success or exhausted). */
    private static volatile boolean entityIdFallbackResolved = false;
    /** Cached reflection accessor — {@code null} when no getter was found. */
    private static volatile Method  cachedEntityIdMethod = null;

    /**
     * Returns the entity ID carried by {@code packet}, or {@code -1} if the getter
     * is unavailable on this server version (caller should skip the ID check).
     */
    private static int resolveEntityId(ClientboundSetEntityMotionPacket packet) {
        // Fast path: direct call works on MC ≤ 26.0.x
        if (!entityIdDirectFailed) {
            try {
                return packet.getId();
            } catch (NoSuchMethodError e) {
                // 26.1.1+ removed getId() — probe alternatives once
                entityIdDirectFailed = true;
            }
        }
        // Slow path (first failure only): probe alternative getter names
        if (!entityIdFallbackResolved) {
            synchronized (FakeServerGamePacketListenerImpl.class) {
                if (!entityIdFallbackResolved) {
                    for (String name : new String[]{"getEntityId", "id"}) {
                        try {
                            Method m = ClientboundSetEntityMotionPacket.class.getMethod(name);
                            if (m.getReturnType() == int.class) {
                                m.setAccessible(true);
                                cachedEntityIdMethod = m;
                                Config.debugNms("FakePacketListener: entity-ID getter resolved → " + name + "()");
                                break;
                            }
                        } catch (NoSuchMethodException ignored) {}
                    }
                    if (cachedEntityIdMethod == null) {
                        Config.debugNms("FakePacketListener: entity-ID getter not found — "
                                + "ID check skipped (packet arrived on this entity's own connection)");
                    }
                    entityIdFallbackResolved = true;
                }
            }
        }
        if (cachedEntityIdMethod == null) return -1; // no getter — caller skips ID check
        try {
            return (int) cachedEntityIdMethod.invoke(packet);
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Knockback strategy (resolved once on first hit) ───────────────────────

    private enum KbStrategy { UNRESOLVED, GET_MOVEMENT, GET_XA, FIELD_SCAN, NONE }

    private static volatile KbStrategy kbStrategy = KbStrategy.UNRESOLVED;

    // Strategy GET_MOVEMENT — packet.getMovement() → Vec3
    private static Method getMovementMethod;       // ClientboundSetEntityMotionPacket.getMovement()
    private static Method lerpMotionVec3Method;    // lerpMotion(Vec3)  OR  setDeltaMovement(Vec3)

    // Strategy GET_XA — packet.getXa/Ya/Za() → individual doubles
    private static Method getXaMethod, getYaMethod, getZaMethod;
    private static Method lerpMotion3Method;       // Entity.lerpMotion(double,double,double) — may be null
    private static Method setDeltaMovementMethod;  // Entity.setDeltaMovement(Vec3)            — fallback
    private static Class<?> vec3Class;

    // Strategy FIELD_SCAN — last resort: read x/y/z directly from packet fields
    private static Field  scanXField, scanYField, scanZField;

    // ─────────────────────────────────────────────────────────────────────────

    public FakeServerGamePacketListenerImpl(
            MinecraftServer server,
            Connection connection,
            ServerPlayer player,
            CommonListenerCookie cookie
    ) {
        super(server, connection, player, cookie);
    }

    /**
     * Factory for use from reflection-based code in {@code NmsPlayerSpawner}.
     */
    public static FakeServerGamePacketListenerImpl create(
            Object server, Object connection, Object player, Object cookie) {
        return new FakeServerGamePacketListenerImpl(
                (MinecraftServer) server,
                (Connection) connection,
                (ServerPlayer) player,
                (CommonListenerCookie) cookie
        );
    }

    /**
     * Suppress the {@code "Already retired"} {@link IllegalStateException} that Paper 1.21+
     * throws when {@link net.minecraft.server.players.PlayerList#remove} tries to retire
     * the entity's scheduler a second time on bot death (double-disconnect path).
     *
     * <p>The player-list cleanup that matters has already completed before the exception
     * is thrown, so suppressing it here is safe.
     */
    @Override
    public void onDisconnect(@org.jetbrains.annotations.NotNull DisconnectionDetails details) {
        try {
            super.onDisconnect(details);
        } catch (IllegalStateException e) {
            if ("Already retired".equals(e.getMessage())) {
                Config.debugNms("FakeServerGamePacketListenerImpl: suppressed double-retirement for "
                        + this.player.getScoreboardName()
                        + " (entity scheduler already retired by death path)");
            } else {
                throw e;
            }
        }
    }

    /**
     * Intercept outgoing packets.
     * <ul>
     *   <li>{@link ClientboundSetEntityMotionPacket} — apply knockback server-side.</li>
     *   <li>Everything else — silently discard.</li>
     * </ul>
     */
    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            Config.debugNms("[KB-DEBUG] send(Packet) called for " + this.player.getScoreboardName()
                    + " packetClass=" + packet.getClass().getSimpleName());
            applyKnockback(motionPacket);
        }
        // All other packets discarded
    }

    /**
     * PacketSendListener overload — covers Paper 26.1.1+ where NMS may call
     * {@code send(Packet, PacketSendListener)} directly, bypassing the no-arg variant.
     *
     * <p>{@code @Override} intentionally omitted: this overload does not exist on the
     * 1.21.11 compile target's {@code ServerGamePacketListenerImpl}; Java runtime
     * dispatch resolves it by name + parameter types when the server runs 26.1.1+.
     */
    @SuppressWarnings("unused")
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            Config.debugNms("[KB-DEBUG] send(Packet,PacketSendListener) called for "
                    + this.player.getScoreboardName()
                    + " packetClass=" + packet.getClass().getSimpleName());
            applyKnockback(motionPacket);
        }
        // All other packets discarded
    }

    // ── Knockback application ─────────────────────────────────────────────────

    private void applyKnockback(ClientboundSetEntityMotionPacket packet) {
        int packetEntityId = resolveEntityId(packet);
        int myId = this.player.getId();
        Config.debugNms("[KB-DEBUG] applyKnockback: bot=" + this.player.getScoreboardName()
                + " myId=" + myId + " packetId=" + packetEntityId
                + " hurtMarked=" + this.player.hurtMarked);

        if (packetEntityId != -1 && packetEntityId != myId) {
            Config.debugNms("[KB-DEBUG] applyKnockback: SKIPPED — packet is for entity "
                    + packetEntityId + " not this bot (" + myId + ")");
            return;
        }
        // NOTE: hurtMarked is intentionally NOT checked here.
        //   • In Paper 26.1.1+, NMS resets hurtMarked = false BEFORE calling send(), so
        //     checking it would always short-circuit and knockback would never be applied.
        //   • Entity-ID filtering (above) is sufficient to discard unrelated motion packets.
        //   • When packetEntityId == -1 (no getter found), the packet arrived on THIS entity's
        //     own connection — that alone guarantees it belongs to this bot.

        // Snapshot velocity from the packet before scheduling (packet object is safe to read later)
        Config.debugNms("[KB-DEBUG] applyKnockback: PROCEEDING — scheduling task for "
                + this.player.getScoreboardName() + " strategy=" + kbStrategy);

        Bukkit.getScheduler().runTask(FakePlayerPlugin.getInstance(), () -> {
            try {
                KbStrategy strategy = resolveStrategy();
                Config.debugNms("[KB-DEBUG] task running for " + this.player.getScoreboardName()
                        + " strategy=" + strategy
                        + " alive=" + !this.player.isRemoved());

                switch (strategy) {
                    case GET_MOVEMENT -> {
                        // 1.21.9+: packet.getMovement() → Vec3 → player.setDeltaMovement(Vec3)
                        Object movement = getMovementMethod.invoke(packet);
                        // Log the raw velocity from the packet (Vec3.toString())
                        Config.debugNms("[KB-DEBUG] GET_MOVEMENT velocity=" + movement
                                + " applyMethod=" + lerpMotionVec3Method.getName());
                        lerpMotionVec3Method.invoke(this.player, movement);
                        // Verify velocity was actually set on the entity
                        logPostApplyVelocity("GET_MOVEMENT");
                    }
                    case GET_XA -> {
                        // ≤1.21.8: individual xa/ya/za doubles
                        double xa = (double) getXaMethod.invoke(packet);
                        double ya = (double) getYaMethod.invoke(packet);
                        double za = (double) getZaMethod.invoke(packet);
                        Config.debugNms("[KB-DEBUG] GET_XA velocity=(" + xa + "," + ya + "," + za + ")");
                        if (lerpMotion3Method != null) {
                            lerpMotion3Method.invoke(this.player, xa, ya, za);
                        } else {
                            Object v = vec3Class
                                    .getConstructor(double.class, double.class, double.class)
                                    .newInstance(xa, ya, za);
                            setDeltaMovementMethod.invoke(this.player, v);
                        }
                        logPostApplyVelocity("GET_XA");
                    }
                    case FIELD_SCAN -> {
                        // Last resort: read velocity from packet fields directly
                        double fx = (double) scanXField.get(packet);
                        double fy = (double) scanYField.get(packet);
                        double fz = (double) scanZField.get(packet);
                        Config.debugNms("[KB-DEBUG] FIELD_SCAN velocity=(" + fx + "," + fy + "," + fz + ")");
                        if (lerpMotion3Method != null) {
                            lerpMotion3Method.invoke(this.player, fx, fy, fz);
                        } else if (setDeltaMovementMethod != null && vec3Class != null) {
                            Object v = vec3Class
                                    .getConstructor(double.class, double.class, double.class)
                                    .newInstance(fx, fy, fz);
                            setDeltaMovementMethod.invoke(this.player, v);
                        }
                        logPostApplyVelocity("FIELD_SCAN");
                    }
                    case NONE ->
                        Config.debugNms("[KB-DEBUG] knockback NONE — no compatible MC API found");
                    default ->
                        Config.debugNms("[KB-DEBUG] unexpected strategy: " + strategy);
                }
            } catch (Exception e) {
                me.bill.fakePlayerPlugin.util.FppLogger.warn(
                        "[KB-DEBUG] knockback task exception: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
                if (Config.debugNms()) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Reads the entity's current deltaMovement after applying knockback and logs it.
     * This verifies whether the velocity was actually accepted by the NMS entity.
     */
    private void logPostApplyVelocity(String strategyName) {
        try {
            // Use Bukkit API to read back the velocity (CraftPlayer delegates to NMS deltaMovement)
            org.bukkit.entity.Player bukkit = this.player.getBukkitEntity() instanceof org.bukkit.entity.Player p ? p : null;
            if (bukkit != null) {
                org.bukkit.util.Vector v = bukkit.getVelocity();
                Config.debugNms("[KB-DEBUG] " + strategyName + " POST-APPLY velocity on "
                        + this.player.getScoreboardName()
                        + ": x=" + String.format("%.4f", v.getX())
                        + " y=" + String.format("%.4f", v.getY())
                        + " z=" + String.format("%.4f", v.getZ()));
            }
        } catch (Exception e) {
            Config.debugNms("[KB-DEBUG] could not read post-apply velocity: " + e.getMessage());
        }
    }

    // ── Strategy resolution (lazy, one-time) ──────────────────────────────────

    private static synchronized KbStrategy resolveStrategy() {
        if (kbStrategy != KbStrategy.UNRESOLVED) return kbStrategy;

        // ── Resolve Vec3 class once (used by multiple strategies) ─────────────
        try {
            vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
        } catch (ClassNotFoundException e) {
            Config.debugNms("FakePacketListener: Vec3 class not found: " + e.getMessage());
        }

        // ── Strategy 1: GET_MOVEMENT (1.21.9+) ────────────────────────────────
        // packet.getMovement() returns Vec3; apply via lerpMotion(Vec3) or setDeltaMovement(Vec3)
        try {
            Method gm = ClientboundSetEntityMotionPacket.class.getMethod("getMovement");
            Class<?> returnType = gm.getReturnType(); // net.minecraft.world.phys.Vec3
            // Prefer lerpMotion(Vec3); fall back to setDeltaMovement(Vec3) (same Vec3 type)
            Method lm = findLerpMotionVec3(returnType);
            if (lm == null && vec3Class != null) {
                lm = findMethod(ServerPlayer.class, "setDeltaMovement", vec3Class);
            }
            if (lm != null) {
                getMovementMethod    = gm;
                lerpMotionVec3Method = lm;
                Config.debugNms("FakePacketListener: knockback strategy → GET_MOVEMENT"
                        + " (apply=" + lm.getName() + "(" + returnType.getSimpleName() + "))");
                return kbStrategy = KbStrategy.GET_MOVEMENT;
            }
            // getMovement() exists but no Vec3 apply method yet — cache for FIELD_SCAN
            getMovementMethod = gm;
            Config.debugNms("FakePacketListener: getMovement() found but no Vec3 apply method;"
                    + " will try GET_XA / FIELD_SCAN");
        } catch (NoSuchMethodException ignored) {
            // getMovement() not present — older or newer MC version
        } catch (Exception e) {
            Config.debugNms("FakePacketListener: GET_MOVEMENT probe failed: " + e.getMessage());
        }

        // ── Strategy 2: GET_XA (≤1.21.8) ──────────────────────────────────────
        // packet.getXa/Ya/Za() or xa/ya/za() → doubles
        Method xa = probeMethod(ClientboundSetEntityMotionPacket.class, "getXa", "xa");
        Method ya = probeMethod(ClientboundSetEntityMotionPacket.class, "getYa", "ya");
        Method za = probeMethod(ClientboundSetEntityMotionPacket.class, "getZa", "za");
        if (xa != null && ya != null && za != null) {
            getXaMethod = xa;
            getYaMethod = ya;
            getZaMethod = za;
            // Try lerpMotion(double, double, double)
            lerpMotion3Method = findMethod(ServerPlayer.class,
                    "lerpMotion", double.class, double.class, double.class);
            if (lerpMotion3Method == null && vec3Class != null) {
                // Fall back to setDeltaMovement(Vec3)
                setDeltaMovementMethod = findMethod(ServerPlayer.class,
                        "setDeltaMovement", vec3Class);
            }
            Config.debugNms("FakePacketListener: knockback strategy → GET_XA"
                    + " (lerpMotion3=" + (lerpMotion3Method != null)
                    + ", setDelta=" + (setDeltaMovementMethod != null) + ")");
            return kbStrategy = KbStrategy.GET_XA;
        }

        // ── Strategy 3: FIELD_SCAN (last resort) ──────────────────────────────
        // Neither accessor method family exists — scan packet declared fields for the three
        // velocity doubles.  Common field-name patterns across MC versions:
        //   xa/ya/za  (1.21.x record fields)
        //   xd/yd/zd  (older obfuscated names)
        //   x/y/z     (hypothetical future flat names)
        // Require exactly 3 double fields if using generic x/y/z names to avoid false matches.
        setDeltaMovementMethod = vec3Class != null
                ? findMethod(ServerPlayer.class, "setDeltaMovement", vec3Class) : null;
        lerpMotion3Method = findMethod(ServerPlayer.class,
                "lerpMotion", double.class, double.class, double.class);

        if (setDeltaMovementMethod != null || lerpMotion3Method != null) {
            // Try known ordered name-triplets
            String[][] nameTriplets = {
                {"xa",  "ya",  "za"},
                {"xd",  "yd",  "zd"},
                {"motX","motY","motZ"},
            };
            for (String[] triplet : nameTriplets) {
                Field fx = findDeclaredField(ClientboundSetEntityMotionPacket.class, triplet[0], double.class);
                Field fy = findDeclaredField(ClientboundSetEntityMotionPacket.class, triplet[1], double.class);
                Field fz = findDeclaredField(ClientboundSetEntityMotionPacket.class, triplet[2], double.class);
                if (fx != null && fy != null && fz != null) {
                    scanXField = fx;
                    scanYField = fy;
                    scanZField = fz;
                    Config.debugNms("FakePacketListener: knockback strategy → FIELD_SCAN"
                            + " fields=[" + triplet[0] + "," + triplet[1] + "," + triplet[2] + "]"
                            + " applyLerp3=" + (lerpMotion3Method != null)
                            + " applySetDelta=" + (setDeltaMovementMethod != null));
                    return kbStrategy = KbStrategy.FIELD_SCAN;
                }
            }
            // Last-ditch: collect ALL double fields declared on the packet
            java.util.List<Field> doubleFields = new java.util.ArrayList<>();
            for (Field f : ClientboundSetEntityMotionPacket.class.getDeclaredFields()) {
                if (f.getType() == double.class) {
                    f.setAccessible(true);
                    doubleFields.add(f);
                }
            }
            if (doubleFields.size() >= 3) {
                scanXField = doubleFields.get(0);
                scanYField = doubleFields.get(1);
                scanZField = doubleFields.get(2);
                Config.debugNms("FakePacketListener: knockback strategy → FIELD_SCAN"
                        + " (positional double fields: "
                        + scanXField.getName() + "," + scanYField.getName() + "," + scanZField.getName() + ")");
                return kbStrategy = KbStrategy.FIELD_SCAN;
            }
        }

        Config.debugNms("FakePacketListener: knockback strategy = NONE (no compatible MC API found)");
        return kbStrategy = KbStrategy.NONE;
    }

    /**
     * Walks the full {@link ServerPlayer} superclass chain looking for a
     * single-parameter {@code lerpMotion} method whose parameter type is
     * assignable from (or equal to) {@code vec3Type}.
     */
    private static Method findLerpMotionVec3(Class<?> vec3Type) {
        Class<?> cur = ServerPlayer.class;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) {
                if ("lerpMotion".equals(m.getName())
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(vec3Type)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    /** Tries each candidate name in order and returns the first method found, or null. */
    private static Method probeMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            try { return clazz.getMethod(name); } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    /** Walks the full superclass chain for a method with the given name + parameter types. */
    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                Method m = cur.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
            cur = cur.getSuperclass();
        }
        return null;
    }

    /**
     * Finds a declared field with the given name and type on {@code clazz} (no hierarchy walk —
     * packet fields are always declared directly on the packet class).
     * Returns {@code null} when not found.
     */
    private static Field findDeclaredField(Class<?> clazz, String name, Class<?> type) {
        try {
            Field f = clazz.getDeclaredField(name);
            if (f.getType() == type) {
                f.setAccessible(true);
                return f;
            }
        } catch (NoSuchFieldException ignored) {}
        return null;
    }
}


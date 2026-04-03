package me.bill.fakePlayerPlugin.fakeplayer.network;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;

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
 */
public final class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl {

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
     * Accepts untyped {@code Object} params and casts to the correct NMS types.
     */
    public static FakeServerGamePacketListenerImpl create(
            Object server,
            Object connection,
            Object player,
            Object cookie) {
        return new FakeServerGamePacketListenerImpl(
                (MinecraftServer) server,
                (Connection) connection,
                (ServerPlayer) player,
                (CommonListenerCookie) cookie
        );
    }

    /**
     * Intercept outgoing packets.
     * <ul>
     *   <li>{@link ClientboundSetEntityMotionPacket} — apply knockback server-side.</li>
     *   <li>Everything else — silently discard.</li>
     * </ul>
     * <strong>Do NOT call {@code super.send()}</strong> — doing so would eventually
     * invoke {@code internalTeleport()} bookkeeping that sets
     * {@code awaitingPositionFromClient}.
     */
    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            applyKnockback(motionPacket);
        }
        // All other packets discarded
    }

    private void applyKnockback(ClientboundSetEntityMotionPacket packet) {
        if (packet.getId() != this.player.getId() || !this.player.hurtMarked) return;
        Bukkit.getScheduler().runTask(FakePlayerPlugin.getInstance(), () -> {
            try {
                this.player.hurtMarked = true;
                this.player.lerpMotion(packet.getMovement());
                Config.debug("FakePacketListener: knockback applied to "
                        + this.player.getScoreboardName());
            } catch (Exception e) {
                Config.debug("FakePacketListener: knockback error: " + e.getMessage());
            }
        });
    }
}


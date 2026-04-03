package me.bill.fakePlayerPlugin.fakeplayer.network;

import io.netty.channel.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Consumer;

/**
 * A fake Netty channel that discards all writes and reports itself as always active/open.
 *
 * <p>Injected into the NMS {@code Connection} object used by each fake bot player.
 * All outbound packets (position, entity data, etc.) are silently discarded — bots
 * have no real network client, so sending packets to them is a no-op.
 *
 * <p>Ported from the hello09x/fakeplayer reference implementation.
 */
public final class FakeChannel extends AbstractChannel {

    private static final EventLoop EVENT_LOOP = new DefaultEventLoop();

    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final ChannelPipeline pipeline = new FakeChannelPipeline(this);
    private final InetAddress address;

    /**
     * Optional listener called in {@link FakeChannelPipeline} before a
     * written packet is discarded.  Used to intercept knockback velocity packets.
     */
    private volatile Consumer<Object> packetListener;

    public FakeChannel(InetAddress address) {
        super(null);
        this.address = address;
    }

    /** Two-arg constructor matching the reference plugin signature {@code (null, address)}. */
    public FakeChannel(io.netty.channel.Channel parent, InetAddress address) {
        super(parent);
        this.address = address;
    }

    /** Sets the packet-send listener used to intercept outbound NMS packets. */
    public void setPacketListener(Consumer<Object> listener) {
        this.packetListener = listener;
    }

    /** Returns the currently registered packet-send listener, or {@code null}. */
    public Consumer<Object> getPacketListener() {
        return packetListener;
    }

    @Override
    public ChannelConfig config() {
        config.setAutoRead(true);
        return config;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public ChannelMetadata metadata() {
        return new ChannelMetadata(true);
    }

    @Override
    public EventLoop eventLoop() {
        return EVENT_LOOP;
    }

    @Override
    protected SocketAddress localAddress0() {
        return new InetSocketAddress(address, 25565);
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return new InetSocketAddress(address, 25565);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                safeSetSuccess(promise);
            }
        };
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    @Override
    protected void doBeginRead() {
    }

    @Override
    protected void doBind(SocketAddress localAddress) {
    }

    @Override
    protected void doDisconnect() {
    }

    @Override
    protected void doClose() {
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) {
        // Drain and discard all pending writes to prevent memory leaks
        for (;;) {
            Object msg = in.current();
            if (msg == null) break;
            in.remove();
        }
    }
}



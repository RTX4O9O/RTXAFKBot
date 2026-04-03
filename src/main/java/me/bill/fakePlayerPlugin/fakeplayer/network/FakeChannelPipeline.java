package me.bill.fakePlayerPlugin.fakeplayer.network;

import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutorGroup;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A no-op {@link ChannelPipeline} that silently discards all operations.
 *
 * <p>Used by {@link FakeChannel} — since bots have no real client, no pipeline
 * processing is needed. All handler additions, reads, and writes are no-ops.
 * Reference-counted messages passed to write/writeAndFlush are released to
 * prevent Netty memory leaks.
 *
 * <p>Ported from the hello09x/fakeplayer reference implementation.
 */
public final class FakeChannelPipeline implements ChannelPipeline {

    private final Channel channel;

    public FakeChannelPipeline(Channel channel) {
        this.channel = channel;
    }

    // ── Handler management (all no-ops) ────────────────────────────────────────

    @Override public ChannelPipeline addFirst(String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addLast(String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) { return this; }
    @Override public ChannelPipeline addFirst(ChannelHandler... handlers) { return this; }
    @Override public ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers) { return this; }
    @Override public ChannelPipeline addLast(ChannelHandler... handlers) { return this; }
    @Override public ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers) { return this; }
    @Override public ChannelPipeline remove(ChannelHandler handler) { return this; }
    @Override public ChannelHandler remove(String name) { return null; }
    @Override public <T extends ChannelHandler> T remove(Class<T> handlerType) { return null; }
    @Override public ChannelHandler removeFirst() { return null; }
    @Override public ChannelHandler removeLast() { return null; }
    @Override public ChannelPipeline replace(ChannelHandler old, String name, ChannelHandler handler) { return this; }
    @Override public ChannelHandler replace(String old, String name, ChannelHandler handler) { return null; }
    @Override public <T extends ChannelHandler> T replace(Class<T> old, String name, ChannelHandler handler) { return null; }
    @Override public ChannelHandler first() { return null; }
    @Override public ChannelHandlerContext firstContext() { return null; }
    @Override public ChannelHandler last() { return null; }
    @Override public ChannelHandlerContext lastContext() { return null; }
    @Override public ChannelHandler get(String name) { return null; }
    @Override public <T extends ChannelHandler> T get(Class<T> handlerType) { return null; }
    @Override public ChannelHandlerContext context(ChannelHandler handler) { return null; }
    @Override public ChannelHandlerContext context(String name) { return null; }
    @Override public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) { return null; }
    @Override public Channel channel() { return this.channel; }
    @Override public List<String> names() { return List.of(); }
    @Override public Map<String, ChannelHandler> toMap() { return Map.of(); }

    // ── Fire events (no-ops) ───────────────────────────────────────────────────

    @Override public ChannelPipeline fireChannelRegistered() { return this; }
    @Override public ChannelPipeline fireChannelUnregistered() { return this; }
    @Override public ChannelPipeline fireChannelActive() { return this; }
    @Override public ChannelPipeline fireChannelInactive() { return this; }
    @Override public ChannelPipeline fireExceptionCaught(Throwable cause) { return this; }
    @Override public ChannelPipeline fireUserEventTriggered(Object event) { return this; }
    @Override public ChannelPipeline fireChannelRead(Object msg) { ReferenceCountUtil.release(msg); return this; }
    @Override public ChannelPipeline fireChannelReadComplete() { return this; }
    @Override public ChannelPipeline fireChannelWritabilityChanged() { return this; }

    // ── Outbound operations ────────────────────────────────────────────────────

    @Override public ChannelFuture bind(SocketAddress addr) { return newSucceededFuture(); }
    @Override public ChannelFuture connect(SocketAddress remote) { return newSucceededFuture(); }
    @Override public ChannelFuture connect(SocketAddress remote, SocketAddress local) { return newSucceededFuture(); }
    @Override public ChannelFuture disconnect() { return newSucceededFuture(); }
    @Override public ChannelFuture close() { return newSucceededFuture(); }
    @Override public ChannelFuture deregister() { return newSucceededFuture(); }
    @Override public ChannelFuture bind(SocketAddress addr, ChannelPromise p) { p.setSuccess(); return p; }
    @Override public ChannelFuture connect(SocketAddress remote, ChannelPromise p) { p.setSuccess(); return p; }
    @Override public ChannelFuture connect(SocketAddress remote, SocketAddress local, ChannelPromise p) { p.setSuccess(); return p; }
    @Override public ChannelFuture disconnect(ChannelPromise p) { p.setSuccess(); return p; }
    @Override public ChannelFuture close(ChannelPromise p) { p.setSuccess(); return p; }
    @Override public ChannelFuture deregister(ChannelPromise p) { p.setSuccess(); return p; }
    @Override public ChannelOutboundInvoker read() { return null; }
    @Override public ChannelPipeline flush() { return this; }

    // ── Write (intercept → discard + release) ─────────────────────────────────

    private void notifyListener(Object msg) {
        if (channel instanceof FakeChannel fc) {
            java.util.function.Consumer<Object> listener = fc.getPacketListener();
            if (listener != null) listener.accept(msg);
        }
    }

    @Override
    public ChannelFuture write(Object msg) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        promise.setSuccess();
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        promise.setSuccess();
        return promise;
    }

    // ── Promise / future factories ─────────────────────────────────────────────

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(this.channel);
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return null;
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        DefaultChannelPromise p = new DefaultChannelPromise(this.channel);
        p.setSuccess(null);
        return p;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        DefaultChannelPromise p = new DefaultChannelPromise(this.channel);
        p.setFailure(cause);
        return p;
    }

    @Override
    public ChannelPromise voidPromise() {
        DefaultChannelPromise p = new DefaultChannelPromise(this.channel);
        p.setSuccess(null);
        return p;
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return Collections.<String, ChannelHandler>emptyMap().entrySet().iterator();
    }
}


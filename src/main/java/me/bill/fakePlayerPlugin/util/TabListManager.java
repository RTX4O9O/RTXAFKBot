package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;

/**
 * Stub retained for API compatibility — the tab-list header/footer feature has been removed.
 * Bot tab-list visibility is now controlled solely by {@code tab-list.enabled} in config.yml
 * and enforced directly in {@link me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager}.
 */
public final class TabListManager {

    @SuppressWarnings("unused")
    public TabListManager(FakePlayerPlugin plugin, FakePlayerManager botManager) {}

    /** No-op — header/footer feature removed. */
    public void start() {}

    /** No-op — header/footer feature removed. */
    public void reload() {}

    /** No-op — header/footer feature removed. */
    public void shutdown() {}

    /** No-op — header/footer feature removed. */
    public void updateNow() {}
}

package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory registry of bots that are physically running on
 * <em>other</em> servers in the proxy network.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Entries are <b>added</b> when a {@code BOT_SPAWN} plugin-message arrives
 *       from another server, and when the DB is queried at startup in NETWORK
 *       mode (for bots that were already online before this server started).</li>
 *   <li>Entries are <b>removed</b> when a {@code BOT_DESPAWN} message arrives.</li>
 *   <li>All entries from a specific server can be <b>bulk-cleared</b> (useful if
 *       that server restarts and re-announces its bot state).</li>
 * </ul>
 *
 * <p>The cache is held on {@link me.bill.fakePlayerPlugin.FakePlayerPlugin} and
 * accessed by {@link me.bill.fakePlayerPlugin.messaging.VelocityChannel},
 * {@link me.bill.fakePlayerPlugin.listener.PlayerJoinListener}, and
 * {@link me.bill.fakePlayerPlugin.util.FppPlaceholderExpansion}.
 */
public final class RemoteBotCache {

    /** UUID → entry map.  ConcurrentHashMap — safe for concurrent reads/writes. */
    private final ConcurrentHashMap<UUID, RemoteBotEntry> entries = new ConcurrentHashMap<>();

    // ── Mutators ──────────────────────────────────────────────────────────────

    /** Adds or replaces an entry (identified by {@link RemoteBotEntry#uuid()}). */
    public void add(RemoteBotEntry entry) {
        entries.put(entry.uuid(), entry);
    }

    /** Removes a single entry by UUID. No-op if not present. */
    public void remove(UUID uuid) {
        entries.remove(uuid);
    }

    /**
     * Removes all entries that originated from {@code serverId}.
     * Called before re-populating from the DB so stale entries are evicted.
     */
    public void removeAllFromServer(String serverId) {
        entries.values().removeIf(e -> serverId.equals(e.serverId()));
    }

    /** Removes all entries. */
    public void clear() {
        entries.clear();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns an unmodifiable view of all cached remote bot entries. */
    public Collection<RemoteBotEntry> getAll() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Returns the number of remote bots currently cached. */
    public int count() {
        return entries.size();
    }
}


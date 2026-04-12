package me.bill.fakePlayerPlugin.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Keeps the server-list hover sample accurate when bots are online.
 *
 * <p>Bots are real NMS {@code ServerPlayer} entities that go through
 * {@code placeNewPlayer()}, so they are already counted in
 * {@code getNumPlayers()} — <b>do not</b> add {@code botCount} again.
 *
 * <p>Paper's {@code getListedPlayers()} is lazily backed by an NMS
 * {@code List<NameAndId>} that was cached at status-refresh time.  When the
 * cache was built, a bot's {@code GameProfile.getName()} may have been blank
 * (not yet fully initialised), producing an entry the Minecraft client renders
 * as "Anonymous Player".  We replace the entire sample with a fresh list built
 * from live {@code FakePlayer} / Bukkit data so blank names can never reach
 * the client.
 */
public class ServerListListener implements Listener {

    /** Vanilla client shows at most 12 names in the server-list hover. */
    private static final int MAX_SAMPLE = 12;

    private final FakePlayerManager manager;

    public ServerListListener(FakePlayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPing(PaperServerListPingEvent event) {
        // NOTE: do NOT return early when botCount == 0.
        // After a bot despawns, the NMS status cache may still contain a stale
        // NameAndId entry with a blank GameProfile name (captured mid-teardown).
        // If we skip this listener, Paper's getPlayerSampleHandle() returns that
        // originalSample directly (it only uses our modified list when
        // getListedPlayers() has been called at least once).  The client then
        // sees the blank-named entry and renders it as "Anonymous Player".
        // Always running this handler guarantees we flush that stale entry.

        // UUID → MC-name for every active bot (fp.getName() is always the valid
        // MC username set at spawn; it can never be blank or "Anonymous Player").
        Map<UUID, String> botNames = manager.getActivePlayers().stream()
                .collect(Collectors.toMap(FakePlayer::getUuid, FakePlayer::getName));

        // Build a completely fresh sample from live data.
        List<PaperServerListPingEvent.ListedPlayerInfo> freshSample = new ArrayList<>();

        // ── Bot entries ────────────────────────────────────────────────────────
        for (Map.Entry<UUID, String> e : botNames.entrySet()) {
            String name = e.getValue();
            if (name != null && !name.isBlank()) {
                freshSample.add(new PaperServerListPingEvent.ListedPlayerInfo(name, e.getKey()));
            }
        }

        // ── Real-player entries (exclude bots, they're already added above) ───
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            if (botNames.containsKey(p.getUniqueId())) continue;
            String name = p.getName();
            if (!name.isBlank()) {
                freshSample.add(new PaperServerListPingEvent.ListedPlayerInfo(name, p.getUniqueId()));
            }
        }

        if (freshSample.isEmpty()) return;

        Collections.shuffle(freshSample);
        if (freshSample.size() > MAX_SAMPLE) freshSample = freshSample.subList(0, MAX_SAMPLE);

        // Call getListedPlayers() first to trigger Paper's lazy-init
        // (converts NMS NameAndId cache → ListedPlayerInfo and clears the cache
        // so that getPlayerSampleHandle() will read our list, not the NMS cache).
        List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
        listed.clear();
        listed.addAll(freshSample);
    }
}

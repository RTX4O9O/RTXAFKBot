package me.bill.fakePlayerPlugin.messaging;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotBroadcast;
import me.bill.fakePlayerPlugin.fakeplayer.BotChatAI;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PacketHelper;
import me.bill.fakePlayerPlugin.fakeplayer.RemoteBotCache;
import me.bill.fakePlayerPlugin.fakeplayer.RemoteBotEntry;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the {@code fpp:main} plugin messaging channel used for
 * proxy (Velocity / BungeeCord / Waterfall) cross-server communication.
 *
 * <h3>Transport</h3>
 * Outbound messages are wrapped in a {@code BungeeCord} channel
 * {@code Forward ALL fpp:main} envelope so the proxy re-delivers the
 * inner payload to every connected backend server — including an echo
 * back to the originating server.  Echo suppression is handled via
 * per-message unique IDs tracked in {@link #recentIds}.
 *
 * <p>Works with BungeeCord, Waterfall, and Velocity when
 * {@code bungeecord-compat-mode: true} is set in {@code velocity.toml}.
 *
 * <h3>Subchannels</h3>
 * <ul>
 *   <li>{@code BOT_SPAWN}  — full bot profile sent when a bot spawns; all other
 *       servers add a virtual tab-list entry and cache the data.</li>
 *   <li>{@code BOT_DESPAWN} — UUID sent when a bot is removed; all other servers
 *       remove the virtual tab-list entry.</li>
 *   <li>{@code CHAT}   — bot chat line forwarded to all servers.</li>
 *   <li>{@code ALERT}  — admin broadcast pushed to all servers.</li>
 *   <li>{@code JOIN}   — bot join message forwarded to all servers.</li>
 *   <li>{@code LEAVE}  — bot leave message forwarded to all servers.</li>
 *   <li>{@code SYNC}   — key/value state update (future use).</li>
 * </ul>
 */
public final class VelocityChannel implements PluginMessageListener {

    // ── Sub-channel name constants ────────────────────────────────────────────

    public static final String SUBCHANNEL_BOT_SPAWN   = "BOT_SPAWN";
    public static final String SUBCHANNEL_BOT_DESPAWN = "BOT_DESPAWN";
    public static final String SUBCHANNEL_CHAT        = "CHAT";
    public static final String SUBCHANNEL_ALERT       = "ALERT";
    public static final String SUBCHANNEL_JOIN        = "JOIN";
    public static final String SUBCHANNEL_LEAVE       = "LEAVE";
    public static final String SUBCHANNEL_SYNC        = "SYNC";

    /** Plugin-messaging channel this server listens on. */
    public static final String CHANNEL        = "fpp:main";
    /** BungeeCord forwarding channel — used to broadcast to ALL servers. */
    private static final String BUNGEE_CHANNEL = "BungeeCord";

    // ── State ─────────────────────────────────────────────────────────────────

    private final FakePlayerPlugin  plugin;
    @SuppressWarnings("unused")
    private final FakePlayerManager manager;

    /**
     * Recently-seen message IDs.  Entries are added before sending so the
     * proxy echo is silently dropped on arrival.  Auto-expire after 5 s (100 t).
     */
    private final java.util.Set<String> recentIds = ConcurrentHashMap.newKeySet();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public VelocityChannel(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ── ID helpers ────────────────────────────────────────────────────────────

    private String generateAndTrackId() {
        String id = System.currentTimeMillis() + "-"
                + ThreadLocalRandom.current().nextInt(1_000_000);
        recentIds.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentIds.remove(id), 100L);
        return id;
    }

    private boolean isDuplicate(String msgId, String originServer) {
        return recentIds.contains(msgId) || Config.serverId().equals(originServer);
    }

    private void trackIncoming(String msgId) {
        recentIds.add(msgId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentIds.remove(msgId), 100L);
    }

    // ── Core send ─────────────────────────────────────────────────────────────

    /**
     * Sends a plugin message to ALL backend servers via the proxy using the
     * BungeeCord {@code Forward ALL} envelope.  The proxy re-delivers the inner
     * payload to every server on the {@code fpp:main} channel — including an echo
     * back to this server (suppressed via the message ID).
     */
    public void sendPluginMessage(String subchannel, String... data) {
        var online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            Config.debugNetwork("[VelocityChannel] dropped (no players online): " + subchannel);
            return;
        }
        try {
            ByteArrayOutputStream innerBuf = new ByteArrayOutputStream();
            DataOutputStream      innerOut = new DataOutputStream(innerBuf);
            innerOut.writeUTF(subchannel);
            for (String f : data) innerOut.writeUTF(f != null ? f : "");
            byte[] innerBytes = innerBuf.toByteArray();

            ByteArrayOutputStream outerBuf = new ByteArrayOutputStream();
            DataOutputStream      outerOut = new DataOutputStream(outerBuf);
            outerOut.writeUTF("Forward");
            outerOut.writeUTF("ALL");
            outerOut.writeUTF(CHANNEL);
            outerOut.writeShort(innerBytes.length);
            outerOut.write(innerBytes);

            online.iterator().next().sendPluginMessage(plugin, BUNGEE_CHANNEL, outerBuf.toByteArray());
            Config.debugNetwork("[VelocityChannel] Sent '" + subchannel + "' (" + innerBytes.length + " bytes).");
        } catch (IOException e) {
            FppLogger.warn("[VelocityChannel] send failed: " + e.getMessage());
        }
    }

    // ── Per-subchannel send methods ───────────────────────────────────────────

    /**
     * Broadcasts full bot profile data to all other servers so they can add
     * a virtual tab-list entry for this bot.
     *
     * <p>Wire: {@code [msgId][serverId][uuid][name][displayName][packetProfileName][skinValue][skinSignature]}
     * The final two fields are now sent empty for backwards wire compatibility.
     */
    public void broadcastBotSpawn(FakePlayer fp) {
        if (!Config.isNetworkMode()) return;
        String msgId = generateAndTrackId();
        sendPluginMessage(SUBCHANNEL_BOT_SPAWN,
                msgId,
                Config.serverId(),
                fp.getUuid().toString(),
                fp.getName(),
                fp.getDisplayName(),
                fp.getPacketProfileName(),
                "",
                "");
        Config.debugNetwork("[VelocityChannel] BOT_SPAWN sent for '" + fp.getName() + "'.");
    }

    /**
     * Notifies all other servers that a bot has been removed so they clean up
     * their tab-list cache and send remove packets to online players.
     *
     * <p>Wire: {@code [msgId][serverId][uuid]}
     */
    public void broadcastBotDespawn(UUID uuid) {
        if (!Config.isNetworkMode()) return;
        String msgId = generateAndTrackId();
        sendPluginMessage(SUBCHANNEL_BOT_DESPAWN,
                msgId,
                Config.serverId(),
                uuid.toString());
        Config.debugNetwork("[VelocityChannel] BOT_DESPAWN sent for " + uuid + ".");
    }

    /** Forwards bot chat to all other servers. */
    public void sendChatToNetwork(String botName, String botDisplayName,
                                  String message, String prefix, String suffix) {
        if (!Config.isNetworkMode()) return;
        String msgId = generateAndTrackId();
        sendPluginMessage(SUBCHANNEL_CHAT, msgId, botName, botDisplayName, message, prefix, suffix);
    }

    /** Forwards a bot join event to all other servers. */
    public void broadcastJoinToNetwork(FakePlayer fp) {
        if (!Config.isNetworkMode()) return;
        String msgId = generateAndTrackId();
        sendPluginMessage(SUBCHANNEL_JOIN, msgId, fp.getDisplayName(), Config.serverId());
    }

    /** Forwards a bot leave event to all other servers. */
    public void broadcastLeaveToNetwork(String displayName) {
        if (!Config.isNetworkMode()) return;
        String msgId = generateAndTrackId();
        sendPluginMessage(SUBCHANNEL_LEAVE, msgId, displayName, Config.serverId());
    }

    /** Broadcasts an admin alert to all servers. */
    public void broadcastGlobalAlert(String message) {
        String msgId = generateAndTrackId();
        broadcastAlertLocally(message);
        sendPluginMessage(SUBCHANNEL_ALERT, msgId, message);
        Config.debugNetwork("[VelocityChannel] Global alert sent (id=" + msgId + ").");
    }

    // ── Inbound ───────────────────────────────────────────────────────────────

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try {
            DataInputStream in         = new DataInputStream(new ByteArrayInputStream(message));
            String          subchannel = in.readUTF();
            Config.debugNetwork("[VelocityChannel] Recv '" + subchannel + "' via " + player.getName());
            switch (subchannel) {
                case SUBCHANNEL_BOT_SPAWN   -> handleBotSpawn(in);
                case SUBCHANNEL_BOT_DESPAWN -> handleBotDespawn(in);
                case SUBCHANNEL_CHAT        -> handleChat(in);
                case SUBCHANNEL_ALERT       -> handleAlert(in);
                case SUBCHANNEL_JOIN        -> handleJoin(in);
                case SUBCHANNEL_LEAVE       -> handleLeave(in);
                case SUBCHANNEL_SYNC        -> handleSync(in);
                default -> FppLogger.warn("[VelocityChannel] Unknown subchannel: '" + subchannel + "'.");
            }
        } catch (IOException e) {
            FppLogger.warn("[VelocityChannel] Parse error from " + player.getName() + ": " + e.getMessage());
        }
    }

    // ── Subchannel handlers ───────────────────────────────────────────────────

    /**
     * {@code BOT_SPAWN} — another server's bot has spawned.
     * Caches the entry and adds a virtual tab-list entry for every online player.
     *
     * <p>Wire: {@code [msgId][serverId][uuid][name][displayName][packetProfileName][skinValue][skinSignature]}
     */
    private void handleBotSpawn(DataInputStream in) throws IOException {
        String msgId         = in.readUTF();
        String originServer  = in.readUTF();
        UUID   uuid          = UUID.fromString(in.readUTF());
        String name          = in.readUTF();
        String displayName   = in.readUTF();
        String packetName    = in.readUTF();
        String skinValue     = in.readUTF();
        String skinSignature = in.readUTF();

        if (isDuplicate(msgId, originServer)) {
            Config.debugNetwork("[VelocityChannel] BOT_SPAWN echo suppressed: " + name);
            return;
        }
        trackIncoming(msgId);

        Config.debugNetwork("[VelocityChannel] BOT_SPAWN '" + name + "' from '" + originServer + "'.");

        RemoteBotEntry entry = new RemoteBotEntry(
                originServer, uuid, name, displayName, packetName, skinValue, skinSignature);

        RemoteBotCache cache = plugin.getRemoteBotCache();
        if (cache != null) cache.add(entry);

        // Add virtual tab-list entry for all currently online players
        if (Config.tabListEnabled()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                PacketHelper.sendTabListAddRaw(online, uuid, packetName, displayName,
                        skinValue, skinSignature);
            }
        }
    }

    /**
     * {@code BOT_DESPAWN} — another server's bot has been removed.
     * Removes the cached entry and sends a tab-remove packet to every online player.
     *
     * <p>Wire: {@code [msgId][serverId][uuid]}
     */
    private void handleBotDespawn(DataInputStream in) throws IOException {
        String msgId        = in.readUTF();
        String originServer = in.readUTF();
        UUID   uuid         = UUID.fromString(in.readUTF());

        if (isDuplicate(msgId, originServer)) {
            Config.debugNetwork("[VelocityChannel] BOT_DESPAWN echo suppressed: " + uuid);
            return;
        }
        trackIncoming(msgId);

        Config.debugNetwork("[VelocityChannel] BOT_DESPAWN " + uuid + " from '" + originServer + "'.");

        RemoteBotCache cache = plugin.getRemoteBotCache();
        if (cache != null) cache.remove(uuid);

        for (Player online : Bukkit.getOnlinePlayers()) {
            PacketHelper.sendTabListRemoveByUuid(online, uuid);
        }
    }

    /** {@code CHAT} — wire: {@code [msgId][botName][botDisplayName][message][prefix][suffix]} */
    private void handleChat(DataInputStream in) throws IOException {
        String msgId         = in.readUTF();
        String botName        = in.readUTF();
        String botDisplayName = in.readUTF();
        String message        = in.readUTF();
        String prefix         = in.readUTF();
        String suffix         = in.readUTF();

        if (recentIds.contains(msgId)) {
            Config.debugNetwork("[VelocityChannel] CHAT echo suppressed.");
            return;
        }
        BotChatAI.broadcastRemote(botName, botDisplayName, message, prefix, suffix);
    }

    /** {@code ALERT} — wire: {@code [msgId][message]} */
    private void handleAlert(DataInputStream in) throws IOException {
        String msgId   = in.readUTF();
        String message = in.readUTF();

        if (recentIds.contains(msgId)) {
            Config.debugNetwork("[VelocityChannel] ALERT echo suppressed.");
            return;
        }
        trackIncoming(msgId);
        broadcastAlertLocally(message);
    }

    /** {@code JOIN} — wire: {@code [msgId][displayName][originServerId]} */
    private void handleJoin(DataInputStream in) throws IOException {
        String msgId        = in.readUTF();
        String displayName  = in.readUTF();
        String originServer = in.readUTF();

        if (isDuplicate(msgId, originServer)) {
            Config.debugNetwork("[VelocityChannel] JOIN echo suppressed.");
            return;
        }
        trackIncoming(msgId);
        if (!Config.joinMessage()) return;
        BotBroadcast.broadcastJoinByDisplayName(displayName);
    }

    /** {@code LEAVE} — wire: {@code [msgId][displayName][originServerId]} */
    private void handleLeave(DataInputStream in) throws IOException {
        String msgId        = in.readUTF();
        String displayName  = in.readUTF();
        String originServer = in.readUTF();

        if (isDuplicate(msgId, originServer)) {
            Config.debugNetwork("[VelocityChannel] LEAVE echo suppressed.");
            return;
        }
        trackIncoming(msgId);
        if (!Config.leaveMessage()) return;
        BotBroadcast.broadcastLeaveByDisplayName(displayName);
    }

    /** {@code SYNC} — wire: {@code [key][value]} */
    private void handleSync(DataInputStream in) throws IOException {
        String key   = in.readUTF();
        String value = in.readUTF();
        Config.debugNetwork("[VelocityChannel] SYNC — " + key + "='" + value + "'.");
    }

    private void broadcastAlertLocally(String message) {
        net.kyori.adventure.text.Component line =
                me.bill.fakePlayerPlugin.lang.Lang.get("alert-received", "message", message);
        Bukkit.getServer().broadcast(line);
    }
}

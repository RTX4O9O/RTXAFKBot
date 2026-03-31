package me.bill.fakePlayerPlugin.util;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlaceholderAPI expansion for ꜰᴀᴋᴇ ᴘʟᴀʏᴇʀ ᴘʟᴜɢɪɴ.
 *
 * <p>Register with {@link #register()} after PlaceholderAPI is detected.
 * Placeholders marked <b>player</b> return personalised values when a
 * real online player is the context; they fall back gracefully when {@code null}.
 *
 * <h3>Server-wide</h3>
 * <table>
 *   <tr><td>{@code %fpp_count%}</td><td>Active bot count</td></tr>
 *   <tr><td>{@code %fpp_max%}</td><td>Global max-bots cap (∞ if 0)</td></tr>
 *   <tr><td>{@code %fpp_real%}</td><td>Real (non-bot) players online</td></tr>
 *   <tr><td>{@code %fpp_total%}</td><td>Real players + bots combined</td></tr>
 *   <tr><td>{@code %fpp_frozen%}</td><td>Number of frozen bots</td></tr>
 *   <tr><td>{@code %fpp_names%}</td><td>Comma-separated bot display names</td></tr>
 *   <tr><td>{@code %fpp_chat%}</td><td>{@code on}/{@code off} — fake-chat</td></tr>
 *   <tr><td>{@code %fpp_swap%}</td><td>{@code on}/{@code off} — bot swap</td></tr>
 *   <tr><td>{@code %fpp_skin%}</td><td>Skin mode</td></tr>
 *   <tr><td>{@code %fpp_body%}</td><td>{@code on}/{@code off} — physical body</td></tr>
 *   <tr><td>{@code %fpp_pushable%}</td><td>{@code on}/{@code off} — body pushable</td></tr>
 *   <tr><td>{@code %fpp_damageable%}</td><td>{@code on}/{@code off} — body damageable</td></tr>
 *   <tr><td>{@code %fpp_tab%}</td><td>{@code on}/{@code off} — tab-list visibility</td></tr>
 *   <tr><td>{@code %fpp_max_health%}</td><td>Bot max-health setting</td></tr>
 *   <tr><td>{@code %fpp_version%}</td><td>Plugin version</td></tr>
 * </table>
 *
 * <h3>Player-relative (requires online player context)</h3>
 * <table>
 *   <tr><td>{@code %fpp_user_count%}</td><td>Bots owned by this player</td></tr>
 *   <tr><td>{@code %fpp_user_max%}</td><td>Personal bot limit for this player</td></tr>
 *   <tr><td>{@code %fpp_user_names%}</td><td>Comma-separated names of this player's bots</td></tr>
 * </table>
 */
public final class FppPlaceholderExpansion extends PlaceholderExpansion {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    public FppPlaceholderExpansion(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override public @NotNull String getIdentifier() { return "fpp"; }
    @Override public @NotNull String getAuthor()     { return String.join(", ", plugin.getPluginMeta().getAuthors()); }
    @Override public @NotNull String getVersion()    { return plugin.getPluginMeta().getVersion(); }
    @Override public          boolean persist()       { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        int localBots  = manager.getCount();
        // In NETWORK mode include bots from all other proxy servers in the count
        int remoteBots = me.bill.fakePlayerPlugin.config.Config.isNetworkMode()
                ? plugin.getRemoteBotCache().count() : 0;
        int bots  = localBots + remoteBots;
        int real  = Bukkit.getOnlinePlayers().size();

        return switch (params.toLowerCase()) {

            // ── Server-wide counts ────────────────────────────────────────────
            case "count"      -> String.valueOf(bots);
            case "max"        -> Config.maxBots() == 0 ? "∞" : String.valueOf(Config.maxBots());
            case "real"       -> String.valueOf(real);
            case "total"      -> String.valueOf(real + bots);
            case "online"     -> String.valueOf(real + bots);   // friendlier alias for %fpp_total%

            // ── Server-wide state ─────────────────────────────────────────────
            case "frozen"     -> String.valueOf(manager.getActivePlayers().stream()
                                    .filter(FakePlayer::isFrozen).count());
            case "names"      -> manager.getActivePlayers().stream()
                                    .map(FakePlayer::getDisplayName)
                                    .collect(Collectors.joining(", "));
            case "chat"       -> Config.fakeChatEnabled()  ? "on" : "off";
            case "swap"       -> Config.swapEnabled()      ? "on" : "off";
            case "skin"       -> "disabled";
            case "body"       -> Config.spawnBody()        ? "on" : "off";
            case "pushable"   -> Config.bodyPushable()     ? "on" : "off";
            case "damageable" -> Config.bodyDamageable()   ? "on" : "off";
            case "tab"        -> Config.tabListEnabled()   ? "on" : "off";
            case "max_health" -> String.valueOf(Config.maxHealth());
            case "version"    -> plugin.getPluginMeta().getVersion();

            // ── Player-relative ────────────────────────────────────────────────
            case "user_count" -> {
                if (player == null) yield "0";
                yield String.valueOf(manager.getBotsOwnedBy(player.getUniqueId()).size());
            }
            case "user_max" -> {
                if (player == null) yield String.valueOf(Config.userBotLimit());
                Player online = player.getPlayer();
                if (online == null) yield String.valueOf(Config.userBotLimit());
                int personal = Perm.resolveUserBotLimit(online);
                yield personal < 0 ? String.valueOf(Config.userBotLimit()) : String.valueOf(personal);
            }
            case "user_names" -> {
                if (player == null) yield "";
                List<FakePlayer> owned = manager.getBotsOwnedBy(player.getUniqueId());
                yield owned.stream()
                        .map(FakePlayer::getDisplayName)
                        .collect(Collectors.joining(", "));
            }

            // ── Per-world dynamic: %fpp_count_<world>%  %fpp_real_<world>%  %fpp_total_<world>% ──
            default -> {
                if (params.startsWith("count_")) {
                    String w = params.substring(6);
                    yield String.valueOf(countBotsInWorld(w));
                }
                if (params.startsWith("real_")) {
                    String w = params.substring(5);
                    yield String.valueOf(countRealInWorld(w));
                }
                if (params.startsWith("total_")) {
                    String w = params.substring(6);
                    yield String.valueOf(countBotsInWorld(w) + countRealInWorld(w));
                }
                yield null;
            }
        };
    }

    // ── Per-world helpers ─────────────────────────────────────────────────────

    /** Bots whose live body (or last spawn location) is in {@code worldName} (case-insensitive). */
    private int countBotsInWorld(String worldName) {
        return (int) manager.getActivePlayers().stream()
                .filter(fp -> worldName.equalsIgnoreCase(getBotWorldName(fp)))
                .count();
    }

    /** The world name for a bot: live NMS Player position first, then spawn location. */
    private static String getBotWorldName(FakePlayer fp) {
        Entity body = fp.getPhysicsEntity();
        if (body != null && body.isValid()) {
            World w = body.getLocation().getWorld();
            if (w != null) return w.getName();
        }
        Location sl = fp.getSpawnLocation();
        if (sl != null && sl.getWorld() != null) return sl.getWorld().getName();
        return "";
    }

    /** Real (non-bot) players in a world, case-insensitive world name. */
    private static int countRealInWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().stream()
                    .filter(w -> w.getName().equalsIgnoreCase(worldName))
                    .findFirst().orElse(null);
        }
        return world == null ? 0 : world.getPlayers().size();
    }
}

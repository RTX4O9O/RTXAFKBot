package me.bill.fakePlayerPlugin.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.lang.reflect.Method;
import java.util.*;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.RemoteBotEntry;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ServerListListener implements Listener {

  private static final int MAX_SAMPLE = 12;

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;

  public ServerListListener(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPing(PaperServerListPingEvent event) {
    boolean hidePlayers = shouldKeepPlayersHidden(event);

    List<FakePlayer> localBots = new ArrayList<>(manager.getActivePlayers());

    if (Config.serverListCountBots()) {
      int realPlayers = Math.max(0, Bukkit.getOnlinePlayers().size() - localBots.size());
      int botCount = localBots.size();
      if (Config.isNetworkMode() && Config.serverListIncludeRemote()) {
        var cache = plugin.getRemoteBotCache();
        if (cache != null) botCount += cache.count();
      }
      int total = realPlayers + botCount;
      event.setNumPlayers(total);

      if (event.getMaxPlayers() < total) {
        event.setMaxPlayers(total + 1);
      }
    } else {

      int realPlayers = Math.max(0, Bukkit.getOnlinePlayers().size() - localBots.size());
      event.setNumPlayers(realPlayers);
    }

    if (hidePlayers) {
      List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
      listed.clear();
      return;
    }

    List<PaperServerListPingEvent.ListedPlayerInfo> freshSample = new ArrayList<>();

    if (Config.serverListCountBots()) {
      Map<UUID, String> botNames = new LinkedHashMap<>();
      for (FakePlayer fp : localBots) {
        String displayName = fp.getName();
        if (plugin != null && plugin.isNameTagAvailable()) {
          try {
            String freshNick = me.bill.fakePlayerPlugin.util.NameTagHelper.getNick(fp.getUuid());
            displayName = freshNick != null ? freshNick : fp.getName();
          } catch (Throwable ignored) {
            String cachedNick = fp.getNameTagNick();
            displayName = cachedNick != null ? cachedNick : fp.getName();
          }
        } else {
          String cachedNick = fp.getNameTagNick();
          displayName = cachedNick != null ? cachedNick : fp.getName();
        }
        botNames.put(fp.getUuid(), displayName);
      }

      for (Map.Entry<UUID, String> e : botNames.entrySet()) {
        String name = e.getValue();
        if (name != null && !name.isBlank()) {
          freshSample.add(new PaperServerListPingEvent.ListedPlayerInfo(name, e.getKey()));
        }
      }

      if (Config.isNetworkMode() && Config.serverListIncludeRemote()) {
        var cache = plugin.getRemoteBotCache();
        if (cache != null) {
          for (RemoteBotEntry remote : cache.getAll()) {
            String name = remote.displayName();
            if (name == null || name.isBlank()) name = remote.name();
            if (!name.isBlank()) {
              freshSample.add(new PaperServerListPingEvent.ListedPlayerInfo(name, remote.uuid()));
            }
          }
        }
      }
    }

    Set<UUID> botUuids = new HashSet<>();
    for (FakePlayer fp : localBots) botUuids.add(fp.getUuid());

    for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
      if (botUuids.contains(p.getUniqueId())) continue;
      String name = p.getName();
      if (!name.isBlank()) {
        freshSample.add(new PaperServerListPingEvent.ListedPlayerInfo(name, p.getUniqueId()));
      }
    }

    Collections.shuffle(freshSample);
    if (freshSample.size() > MAX_SAMPLE) freshSample = freshSample.subList(0, MAX_SAMPLE);

    List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
    listed.clear();
    listed.addAll(freshSample);
  }

  private static boolean shouldKeepPlayersHidden(PaperServerListPingEvent event) {
    String[] boolMethods = {
      "shouldHidePlayers",
      "getHidePlayers",
      "isHidePlayers",
      "isPlayersHidden"
    };
    for (String methodName : boolMethods) {
      try {
        Method m = event.getClass().getMethod(methodName);
        if (m.getReturnType() == boolean.class) {
          return (boolean) m.invoke(event);
        }
      } catch (Throwable ignored) {
      }
    }
    return event.getNumPlayers() < 0;
  }
}

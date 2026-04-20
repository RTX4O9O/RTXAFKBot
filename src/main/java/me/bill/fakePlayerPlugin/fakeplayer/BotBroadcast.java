package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BotBroadcast {

  private BotBroadcast() {}

  private static void send(Component msg) {
    for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    Bukkit.getConsoleSender().sendMessage(msg);
  }

  private static Component parseDisplayName(String raw) {
    if (raw == null || raw.isEmpty()) return Component.empty();
    return TextUtil.colorize(raw);
  }

  private static Component buildMessage(String langKey, String displayName, String... extraArgs) {
    String template = Lang.raw(langKey, extraArgs);
    Component nameComponent = parseDisplayName(displayName);

    String withTag = template.replace("{name}", "<fpp_name>");
    String converted = TextUtil.legacyToMiniMessage(withTag);

    TagResolver nameResolver = TagResolver.resolver("fpp_name", Tag.inserting(nameComponent));
    return MiniMessage.miniMessage().deserialize(converted, nameResolver);
  }

  private static String resolveDisplayName(FakePlayer fp) {
    me.bill.fakePlayerPlugin.FakePlayerPlugin plugin =
        me.bill.fakePlayerPlugin.FakePlayerPlugin.getInstance();
    if (plugin != null && plugin.isNameTagAvailable()) {
      try {
        String freshNick = me.bill.fakePlayerPlugin.util.NameTagHelper.getNick(fp.getUuid());
        if (freshNick != null && !freshNick.isEmpty()) {
          fp.setNameTagNick(freshNick);
          return freshNick;
        }
      } catch (Throwable ignored) {
      }
    }
    if (fp.getNameTagNick() != null && !fp.getNameTagNick().isEmpty()) {
      return fp.getNameTagNick();
    }
    return fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
  }

  public static Component joinComponent(FakePlayer fp) {
    return buildMessage("bot-join", resolveDisplayName(fp));
  }

  public static Component leaveComponent(String displayName) {
    return buildMessage("bot-leave", displayName);
  }

  public static void broadcastJoin(FakePlayer fp) {
    if (!Config.joinMessage()) return;
    send(buildMessage("bot-join", resolveDisplayName(fp)));
  }

  public static void broadcastJoinByDisplayName(String displayName) {
    if (!Config.joinMessage()) return;
    send(buildMessage("bot-join", displayName));
  }

  public static void broadcastLeave(FakePlayer fp) {
    if (!Config.leaveMessage()) return;
    send(buildMessage("bot-leave", resolveDisplayName(fp)));
  }

  public static void broadcastLeaveByDisplayName(String displayName) {
    if (!Config.leaveMessage()) return;
    send(buildMessage("bot-leave", displayName));
  }

  public static void broadcastKill(String killerName, String botDisplayName) {
    if (!Config.killMessage()) return;
    send(buildMessage("bot-kill", botDisplayName, "killer", killerName));
  }
}

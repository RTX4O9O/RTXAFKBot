package me.bill.fakePlayerPlugin.util;

import java.util.UUID;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.entity.Player;

public final class BotAccess {

  private BotAccess() {}

  public static boolean isAdmin(Player player) {
    return player != null && (player.isOp() || Perm.hasAny(player, Perm.ADMIN, Perm.OP));
  }

  public static boolean isOwner(Player player, FakePlayer bot) {
    return player != null && bot != null && player.getUniqueId().equals(bot.getSpawnedByUuid());
  }

  public static boolean canAdminister(Player player, FakePlayer bot) {
    if (player == null || bot == null) return false;
    UUID uuid = player.getUniqueId();
    return isAdmin(player) || uuid.equals(bot.getSpawnedByUuid()) || bot.hasSharedController(uuid);
  }

  public static boolean canShare(Player player, FakePlayer bot) {
    return isAdmin(player) || isOwner(player, bot);
  }
}

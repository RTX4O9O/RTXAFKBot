package me.bill.fakePlayerPlugin.fppaddon.feature;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBot;
import me.bill.fakePlayerPlugin.api.FppBotTickHandler;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SprintJumpHandler implements FppBotTickHandler {

  private final FakePlayerPlugin plugin;
  private final Map<UUID, State> states = new ConcurrentHashMap<>();

  public SprintJumpHandler(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void onTick(FppBot bot, Player entity) {
    FakePlayer fp = plugin.getFakePlayerManager().getByUuid(bot.getUuid());
    if (fp == null || entity == null || !entity.isOnline() || fp.isBodyless()) {
      states.remove(bot.getUuid());
      return;
    }

    if (!fp.isNavSprintJump() || !entity.isSprinting() || entity.isInWater() || entity.isInLava()) {
      states.remove(bot.getUuid());
      NmsPlayerSpawner.setJumping(entity, false);
      return;
    }

    Location now = entity.getLocation();
    State state = states.computeIfAbsent(bot.getUuid(), k -> new State(now.clone(), entity.isOnGround(), 0));
    double moved = now.distanceSquared(state.lastLoc);
    state.lastLoc = now.clone();
    state.ticks++;

    if (moved < 0.0025) return;

    if (entity.isOnGround() && state.lastGrounded == false) {
      NmsPlayerSpawner.setJumping(entity, true);
      state.ticks = 0;
    } else if (entity.isOnGround() && state.ticks >= 6) {
      NmsPlayerSpawner.setJumping(entity, true);
      state.ticks = 0;
    } else {
      NmsPlayerSpawner.setJumping(entity, false);
    }
    state.lastGrounded = entity.isOnGround();
  }

  private static final class State {
    private Location lastLoc;
    private boolean lastGrounded;
    private int ticks;

    private State(Location lastLoc, boolean lastGrounded, int ticks) {
      this.lastLoc = lastLoc;
      this.lastGrounded = lastGrounded;
      this.ticks = ticks;
    }
  }
}

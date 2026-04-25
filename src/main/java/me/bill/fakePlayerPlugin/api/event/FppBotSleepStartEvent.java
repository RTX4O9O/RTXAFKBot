package me.bill.fakePlayerPlugin.api.event;

import me.bill.fakePlayerPlugin.api.FppBot;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FppBotSleepStartEvent extends FppBotEvent implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Location bedLocation;
  private boolean cancelled = false;

  public FppBotSleepStartEvent(@NotNull FppBot bot, @Nullable Location bedLocation) {
    super(bot);
    this.bedLocation = bedLocation != null ? bedLocation.clone() : null;
  }

  public @Nullable Location getBedLocation() { return bedLocation != null ? bedLocation.clone() : null; }

  @Override public boolean isCancelled() { return cancelled; }
  @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
  @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
  public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

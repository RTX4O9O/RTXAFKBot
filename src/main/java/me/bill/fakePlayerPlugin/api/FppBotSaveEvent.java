package me.bill.fakePlayerPlugin.api;

import me.bill.fakePlayerPlugin.api.event.FppBotEvent;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class FppBotSaveEvent extends FppBotEvent {
  private static final HandlerList HANDLERS = new HandlerList();
  public FppBotSaveEvent(@NotNull FppBot bot) { super(bot); }
  @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
  public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

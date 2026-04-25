package me.bill.fakePlayerPlugin.fppaddon.settings;

import java.util.List;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.api.FppSettingsItem;
import me.bill.fakePlayerPlugin.api.FppSettingsTab;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PathfindingSettingsTab implements FppSettingsTab {

  private final FakePlayerPlugin plugin;

  public PathfindingSettingsTab(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @Override public @NotNull String getId() { return "addon-pathfinding"; }
  @Override public @NotNull String getLabel() { return "🧭 ᴀᴅᴅᴏɴ ᴘᴀᴛʜ"; }
  @Override public @NotNull Material getActiveMaterial() { return Material.LIME_DYE; }
  @Override public @NotNull Material getInactiveMaterial() { return Material.GRAY_DYE; }
  @Override public @NotNull Material getSeparatorGlass() { return Material.CYAN_STAINED_GLASS_PANE; }

  @Override
  public @NotNull List<FppSettingsItem> getItems(@NotNull Player viewer) {
    return List.of(new SprintJumpItem());
  }

  private final class SprintJumpItem implements FppSettingsItem {
    @Override public @NotNull String getId() { return "pathfinding.sprint-jump"; }
    @Override public @NotNull String getLabel() { return "ꜱᴘʀɪɴᴛ ᴊᴜᴍᴘ"; }
    @Override public @NotNull String getDescription() { return "Addon-driven sprint-jump hop cadence while bots navigate."; }
    @Override public @NotNull Material getIcon() { return Material.RABBIT_FOOT; }
    @Override public String getValue() { return String.valueOf(plugin.getConfig().getBoolean(getId())); }

    @Override
    public void onClick(@NotNull Player viewer) {
      boolean next = !plugin.getConfig().getBoolean(getId());
      plugin.getConfig().set(getId(), next);
      plugin.saveConfig();
      Config.reload();
      viewer.sendMessage(Component.text("Sprint jump -> " + (next ? "on" : "off"), NamedTextColor.YELLOW));
    }
  }
}

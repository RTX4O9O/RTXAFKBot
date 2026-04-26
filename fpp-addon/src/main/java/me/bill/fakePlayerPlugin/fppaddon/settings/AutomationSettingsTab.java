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

public final class AutomationSettingsTab implements FppSettingsTab {

  private final FakePlayerPlugin plugin;

  public AutomationSettingsTab(FakePlayerPlugin plugin) {
    this.plugin = plugin;
  }

  @Override public @NotNull String getId() { return "addon-automation"; }
  @Override public @NotNull String getLabel() { return "⚙ ᴀᴅᴅᴏɴ ᴀᴜᴛᴏ"; }
  @Override public @NotNull Material getActiveMaterial() { return Material.LIME_DYE; }
  @Override public @NotNull Material getInactiveMaterial() { return Material.GRAY_DYE; }
  @Override public @NotNull Material getSeparatorGlass() { return Material.LIGHT_BLUE_STAINED_GLASS_PANE; }

  @Override
  public @NotNull List<FppSettingsItem> getItems(@NotNull Player viewer) {
    return List.of(
        new ToggleItem(
            "automation.auto-eat",
            "ᴀᴜᴛᴏ ᴇᴀᴛ",
            "Global default for new/restored bots.",
            Material.COOKED_BEEF),
        new ToggleItem(
            "automation.auto-place-bed",
            "ᴀᴜᴛᴏ ʙᴇᴅ",
            "Global default for new/restored bots.",
            Material.RED_BED));
  }

  private final class ToggleItem implements FppSettingsItem {
    private final String key;
    private final String label;
    private final String desc;
    private final Material icon;

    private ToggleItem(String key, String label, String desc, Material icon) {
      this.key = key;
      this.label = label;
      this.desc = desc;
      this.icon = icon;
    }

    @Override public @NotNull String getId() { return key; }
    @Override public @NotNull String getLabel() { return label; }
    @Override public @NotNull String getDescription() { return desc; }
    @Override public @NotNull Material getIcon() { return icon; }
    @Override public String getValue() { return String.valueOf(plugin.getConfig().getBoolean(key)); }

    @Override
    public void onClick(@NotNull Player viewer) {
      boolean next = !plugin.getConfig().getBoolean(key);
      plugin.getConfig().set(key, next);
      plugin.saveConfig();
      Config.reload();
      viewer.sendMessage(Component.text(label + " -> " + next, NamedTextColor.YELLOW));
    }
  }
}

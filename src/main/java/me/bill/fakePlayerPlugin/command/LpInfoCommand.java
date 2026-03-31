package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.LuckPermsHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /fpp lpinfo} — Shows LuckPerms integration status for FPP bots.
 *
 * <p>Since bots are now real NMS players, LP handles their prefix and tab-list
 * ordering natively. This command shows the current state so admins can verify
 * that LP is detecting bots correctly.
 */
public final class LpInfoCommand implements FppCommand {

    @SuppressWarnings("unused")
    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    public LpInfoCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override public String getName()        { return "lpinfo"; }
    @Override public String getUsage()       { return ""; }
    @Override public String getDescription() { return "Show LuckPerms integration status for bots."; }
    @Override public String getPermission()  { return Perm.ALL; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("═══ ", NamedTextColor.DARK_GRAY)
                .append(Component.text("LuckPerms Integration", NamedTextColor.BLUE))
                .append(Component.text(" ═══", NamedTextColor.DARK_GRAY)));

        boolean lpAvail = LuckPermsHelper.isAvailable();
        sender.sendMessage(Component.text("LP Installed: ", NamedTextColor.GRAY)
                .append(Component.text(lpAvail ? "YES ✔" : "NO ✘",
                        lpAvail ? NamedTextColor.GREEN : NamedTextColor.RED)));

        if (!lpAvail) {
            sender.sendMessage(Component.text(
                    "Install LuckPerms for automatic bot prefix & tab ordering.", NamedTextColor.GOLD));
            return true;
        }

        // Config
        sender.sendMessage(Component.text("─── Config ───", NamedTextColor.DARK_GRAY));
        String dg = Config.luckpermsDefaultGroup();
        sender.sendMessage(Component.text("default-group: ", NamedTextColor.GRAY)
                .append(Component.text(dg.isBlank() ? "(LP default)" : dg, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Groups loaded: ", NamedTextColor.GRAY)
                .append(Component.text(LuckPermsHelper.buildGroupSummary(), NamedTextColor.WHITE)));

        // Active bots
        var bots = manager.getActivePlayers();
        sender.sendMessage(Component.text("─── Active Bots ───", NamedTextColor.DARK_GRAY));
        if (bots.isEmpty()) {
            sender.sendMessage(Component.text("  (none)", NamedTextColor.GRAY));
        } else {
            for (FakePlayer fp : bots) {
                // First try the cached field, then query LP if needed
                String cachedGroup = fp.getLuckpermsGroup();
                if (cachedGroup != null && !cachedGroup.isBlank()) {
                    sender.sendMessage(Component.text("  " + fp.getName(), NamedTextColor.AQUA)
                            .append(Component.text(" → ", NamedTextColor.GRAY))
                            .append(Component.text(cachedGroup, NamedTextColor.GREEN))
                            .append(Component.text(" (cached)", NamedTextColor.DARK_GRAY)));
                } else {
                    LuckPermsHelper.getStoredPrimaryGroup(fp.getUuid()).thenAccept(group -> {
                        sender.sendMessage(Component.text("  " + fp.getName(), NamedTextColor.AQUA)
                                .append(Component.text(" → ", NamedTextColor.GRAY))
                                .append(Component.text(group, NamedTextColor.GREEN)));
                    });
                }
            }
        }

        sender.sendMessage(Component.text(
                "Tip: use /fpp rank <bot> <group> or /fpp rank random <group> [num] to change bot LP groups.",
                NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}


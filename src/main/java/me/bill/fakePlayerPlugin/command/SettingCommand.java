package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.gui.SettingGui;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /fpp settings}
 *
 * <p>Opens the interactive settings GUI - a 3-row chest that lets admins
 * toggle booleans and cycle through preset numeric values without touching
 * {@code config.yml} directly.  Changes are saved to disk and take effect
 * immediately on click.  Only usable by in-game players (not console).
 *
 * <p>Permission: {@code fpp.settings} (default: {@code op}).
 * Inherited automatically by {@code fpp.*}.
 */
public class SettingCommand implements FppCommand {

    private final SettingGui gui;

    public SettingCommand(SettingGui gui) {
        this.gui = gui;
    }

    @Override public String getName()        { return "settings"; }
    @Override public String getUsage()       { return ""; }
    @Override public String getDescription() { return "Open the interactive in-game settings GUI."; }
    @Override public String getPermission()  { return Perm.SETTINGS; }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.SETTINGS);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }
        if (!Perm.has(sender, Perm.SETTINGS)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }
        gui.open(player);
        return true;
    }
}

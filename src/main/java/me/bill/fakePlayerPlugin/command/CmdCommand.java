package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /fpp cmd <bot> <command...>}      - Execute a command as a bot immediately.
 * {@code /fpp cmd <bot> --add <command...>} - Store a command that fires whenever a
 *                                             player right-clicks the bot.
 * {@code /fpp cmd <bot> --clear}           - Remove the stored right-click command.
 * {@code /fpp cmd <bot> --show}            - Display the currently stored command.
 *
 * <p>Right-click behaviour (handled by {@link InventoryCommand}):
 * <ul>
 *   <li>Bot <b>has</b> a stored command → bot executes it via
 *       {@link Bukkit#dispatchCommand}, no inventory opens.</li>
 *   <li>Bot has <b>no</b> stored command → inventory GUI opens as usual.</li>
 * </ul>
 *
 * <p>All dispatches use {@link Bukkit#dispatchCommand} which does NOT fire
 * {@code PlayerCommandPreprocessEvent}, so {@code BotCommandBlocker} does not
 * interfere.
 *
 * <p>Permission: {@link Perm#CMD} ({@code fpp.cmd}), default {@code op}.
 */
public final class CmdCommand implements FppCommand {

    private final FakePlayerManager manager;

    public CmdCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override public String getName()        { return "cmd"; }
    @Override public String getUsage()       { return "<bot> <command...>  |  <bot> --add <command...>  |  <bot> --clear  |  <bot> --show"; }
    @Override public String getDescription() { return "Execute a command as a bot, or bind one to right-click."; }
    @Override public String getPermission()  { return Perm.CMD; }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.CMD);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("cmd-usage"));
            return true;
        }

        String botName = args[0];
        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("cmd-not-found", "name", botName));
            return true;
        }

        String sub = args[1].toLowerCase();

        // ── --add <command...> ─────────────────────────────────────────────────
        if (sub.equals("--add")) {
            if (args.length < 3) {
                sender.sendMessage(Lang.get("cmd-add-usage"));
                return true;
            }
            String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String previous = fp.getRightClickCommand(); // null if not set yet
            fp.setRightClickCommand(command);            // setter strips leading /
            String newCmd = "/" + fp.getRightClickCommand();
            manager.persistBotSettings(fp);

            if (previous != null) {
                // Replacing an existing command - show old → new
                sender.sendMessage(Lang.get("cmd-add-updated",
                        "name", fp.getDisplayName(),
                        "old",  "/" + previous,
                        "cmd",  newCmd));
            } else {
                sender.sendMessage(Lang.get("cmd-add-set",
                        "name", fp.getDisplayName(),
                        "cmd",  newCmd));
            }
            FppLogger.info(sender.getName() + " set right-click command on " + fp.getName()
                    + ": " + newCmd + (previous != null ? " (was: /" + previous + ")" : ""));
            return true;
        }

        // ── --clear ────────────────────────────────────────────────────────────
        if (sub.equals("--clear")) {
            if (!fp.hasRightClickCommand()) {
                sender.sendMessage(Lang.get("cmd-add-none", "name", fp.getDisplayName()));
                return true;
            }
            fp.setRightClickCommand(null);
            sender.sendMessage(Lang.get("cmd-add-cleared", "name", fp.getDisplayName()));
            manager.persistBotSettings(fp);
            return true;
        }

        // ── --show ─────────────────────────────────────────────────────────────
        if (sub.equals("--show")) {
            if (!fp.hasRightClickCommand()) {
                sender.sendMessage(Lang.get("cmd-add-none", "name", fp.getDisplayName()));
            } else {
                sender.sendMessage(Lang.get("cmd-add-show",
                        "name", fp.getDisplayName(),
                        "cmd",  "/" + fp.getRightClickCommand()));
            }
            return true;
        }

        // ── Direct execute: /fpp cmd <bot> <command...> ───────────────────────
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("cmd-bot-offline", "name", fp.getDisplayName()));
            return true;
        }

        // Build command - join args[1..], strip leading slash if present.
        String command = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (command.startsWith("/")) command = command.substring(1);

        // Dispatch: does NOT fire PlayerCommandPreprocessEvent → BotCommandBlocker does not interfere.
        boolean success = Bukkit.dispatchCommand(bot, command);
        final String finalCommand = command;

        if (success) {
            sender.sendMessage(Lang.get("cmd-executed",
                    "name", fp.getDisplayName(),
                    "cmd",  "/" + finalCommand));
            FppLogger.info(sender.getName() + " issued server command as " + fp.getName() + ": /" + finalCommand);
        } else {
            sender.sendMessage(Lang.get("cmd-failed",
                    "name", fp.getDisplayName(),
                    "cmd",  "/" + finalCommand));
        }
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return manager.getActiveNames().stream()
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("--add", "--clear", "--show").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}




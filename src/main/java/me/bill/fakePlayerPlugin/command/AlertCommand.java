package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * {@code /fpp alert <message>} — Broadcasts an alert to all servers in the
 * Velocity network.
 *
 * <p>Sends the message via plugin messaging (subchannel {@code ALERT}) to Velocity,
 * which echoes it back to all connected servers. Each server then broadcasts the
 * alert locally to all online players and console.
 *
 * <p>Duplicate prevention is handled by {@link me.bill.fakePlayerPlugin.messaging.VelocityChannel}
 * using unique message IDs.
 */
public final class AlertCommand implements FppCommand {

    private final FakePlayerPlugin plugin;

    public AlertCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "alert";
    }

    @Override
    public String getUsage() {
        return "<message>";
    }

    @Override
    public String getDescription() {
        return "Broadcast alert to all servers";
    }

    @Override
    public String getPermission() {
        return Perm.ALL;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.ALL);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Lang.get("alert-usage"));
            return false;
        }

        // Join all args into the full message text
        String message = String.join(" ", args);

        var vc = plugin.getVelocityChannel();
        if (vc == null) {
            sender.sendMessage(Lang.get("prefix") + "<red>Plugin messaging not initialized.");
            return false;
        }

        // Send the global alert (broadcasts locally + sends to Velocity)
        vc.broadcastGlobalAlert(message);

        // Confirm to sender
        sender.sendMessage(Lang.get("alert-sent"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // No tab completion for free-form message text
        return Collections.emptyList();
    }
}



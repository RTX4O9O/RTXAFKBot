package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotSwapAI;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /fpp swap [on|off|status|now <bot>|list]}
 *
 * <p>Controls the bot session-rotation system. When enabled, AFK bots
 * periodically leave and rejoin the server, mimicking real player behaviour.
 *
 * <ul>
 *   <li>{@code /fpp swap}             — <strong>toggle</strong> swap on/off (no args = flip current state)</li>
 *   <li>{@code /fpp swap on}          — enable swap and start session countdowns</li>
 *   <li>{@code /fpp swap off}         — disable swap and cancel all countdowns</li>
 *   <li>{@code /fpp swap status}      — show status (active sessions + offline count)</li>
 *   <li>{@code /fpp swap now <bot>}   — trigger an immediate leave for a specific bot</li>
 *   <li>{@code /fpp swap list}        — list all bots with their personality and swap count</li>
 * </ul>
 */
public class SwapCommand implements FppCommand {

    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;

    public SwapCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override public String getName()        { return "swap"; }
    @Override public String getUsage()       { return "[on|off|status|now <bot>|list]"; }
    @Override public String getDescription() { return "Toggle bot session rotation (swap in / out)."; }
    @Override public String getPermission()  { return Perm.SWAP; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // No args → toggle (mirrors /fpp chat behaviour)
        if (args.length == 0) {
            boolean enable = !Config.swapEnabled();
            if (enable) enableSwap(sender);
            else disableSwap(sender);
            Config.debug("swap toggled to " + enable + " by " + sender.getName());
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "on", "true", "yes", "1" -> enableSwap(sender);

            case "off", "false", "no", "0" -> disableSwap(sender);

            case "status" -> sendStatus(sender);

            case "now" -> {
                if (args.length < 2) {
                    sender.sendMessage(Lang.get("swap-now-usage"));
                    return true;
                }
                String botName = args[1];
                BotSwapAI ai = plugin.getBotSwapAI();
                if (ai == null) {
                    sender.sendMessage(Lang.get("swap-not-available"));
                    return true;
                }
                if (ai.triggerNow(botName)) {
                    sender.sendMessage(Lang.get("swap-now-success", "name", botName));
                } else {
                    sender.sendMessage(Lang.get("swap-now-failed", "name", botName));
                }
            }

            case "list" -> sendList(sender);

            default -> sender.sendMessage(Lang.get("swap-invalid"));
        }

        return true;
    }

    // ── Enable / disable helpers ─────────────────────────────────────────────

    private void enableSwap(CommandSender sender) {
        plugin.getConfig().set("swap.enabled", true);
        plugin.saveConfig();

        BotSwapAI ai = plugin.getBotSwapAI();
        if (ai != null) {
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getBotType() != BotType.PVP) {
                    ai.schedule(fp);
                }
            }
        }
        sender.sendMessage(Lang.get("swap-enabled"));
        Config.debug("swap.enabled set to true by enableSwap()");
    }

    private void disableSwap(CommandSender sender) {
        plugin.getConfig().set("swap.enabled", false);
        plugin.saveConfig();

        BotSwapAI ai = plugin.getBotSwapAI();
        if (ai != null) ai.cancelAll();
        sender.sendMessage(Lang.get("swap-disabled"));
        Config.debug("swap.enabled set to false by disableSwap()");
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    private void sendStatus(CommandSender sender) {
        boolean on   = Config.swapEnabled();
        BotSwapAI ai = plugin.getBotSwapAI();

        if (on && ai != null) {
            long nextSec = ai.getNextSwapSeconds();
            String nextLabel = nextSec >= 0
                    ? (nextSec >= 60 ? (nextSec / 60) + "m " + (nextSec % 60) + "s" : nextSec + "s")
                    : "none";
            sender.sendMessage(Lang.get("swap-status-on",
                    "sessions", String.valueOf(ai.getActiveSessionCount()),
                    "offline",  String.valueOf(ai.getSwappedOutCount()),
                    "next",     nextLabel));
        } else {
            sender.sendMessage(Lang.get("swap-status-off"));
        }
    }

    private void sendList(CommandSender sender) {
        BotSwapAI ai = plugin.getBotSwapAI();
        if (ai == null || ai.getActiveSessions().isEmpty()) {
            sender.sendMessage(Lang.get("swap-list-empty"));
            return;
        }

        Set<UUID> sessions = ai.getActiveSessions();
        List<FakePlayer> scheduled = manager.getActivePlayers().stream()
                .filter(fp -> sessions.contains(fp.getUuid()))
                .collect(Collectors.toList());

        if (scheduled.isEmpty()) {
            sender.sendMessage(Lang.get("swap-list-empty"));
            return;
        }

        sender.sendMessage(Lang.get("swap-list-header",
                "count", String.valueOf(scheduled.size())));

        for (FakePlayer fp : scheduled) {
            sender.sendMessage(Lang.get("swap-list-entry",
                    "name",        fp.getDisplayName(),
                    "personality", ai.getPersonalityLabel(fp.getUuid()),
                    "swaps",       String.valueOf(ai.getSwapCount(fp.getUuid()))));
        }
    }

    // ── Tab-complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String pfx = args[0].toLowerCase();
            return List.of("on", "off", "status", "now", "list")
                    .stream()
                    .filter(s -> s.startsWith(pfx))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("now")) {
            String pfx = args[1].toLowerCase();
            return manager.getActiveNames().stream()
                    .filter(n -> n.toLowerCase().startsWith(pfx))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}


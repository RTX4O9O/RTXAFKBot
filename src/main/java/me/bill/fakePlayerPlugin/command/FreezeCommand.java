package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /fpp freeze <bot|all> [on|off]}
 *
 * <p>Freezes or unfreezes a bot — the NMS ServerPlayer body becomes immovable
 * and gravity is disabled so the bot hovers in place. Frozen bots are
 * shown with an ❄ indicator in {@code /fpp list} and {@code /fpp stats}.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code /fpp freeze <bot>}      — toggle frozen state</li>
 *   <li>{@code /fpp freeze <bot> on}   — freeze</li>
 *   <li>{@code /fpp freeze <bot> off}  — unfreeze</li>
 *   <li>{@code /fpp freeze all}        — toggle-freeze all bots</li>
 *   <li>{@code /fpp freeze all on|off} — freeze/unfreeze all</li>
 * </ul>
 */
public class FreezeCommand implements FppCommand {

    private final FakePlayerManager manager;

    public FreezeCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override public String getName()        { return "freeze"; }
    @Override public String getUsage()       { return "<bot|all> [on|off]"; }
    @Override public String getDescription() { return "Freeze or unfreeze a bot in place."; }
    @Override public String getPermission()  { return Perm.FREEZE; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("freeze-usage"));
            return true;
        }

        String target  = args[0];
        String statArg = args.length >= 2 ? args[1].toLowerCase() : null;

        if (target.equalsIgnoreCase("all")) {
            return handleFreezeAll(sender, statArg);
        }

        FakePlayer fp = manager.getByName(target);
        if (fp == null) {
            sender.sendMessage(Lang.get("freeze-not-found", "name", target));
            return true;
        }

        boolean nowFrozen;
        if ("on".equals(statArg)) {
            nowFrozen = true;
        } else if ("off".equals(statArg)) {
            nowFrozen = false;
        } else {
            nowFrozen = !fp.isFrozen(); // toggle
        }

        applyFreeze(fp, nowFrozen);
        if (nowFrozen) {
            sender.sendMessage(Lang.get("freeze-frozen",   "name", fp.getDisplayName()));
        } else {
            sender.sendMessage(Lang.get("freeze-unfrozen", "name", fp.getDisplayName()));
        }
        return true;
    }

    private boolean handleFreezeAll(CommandSender sender, String statArg) {
        Collection<FakePlayer> bots = manager.getActivePlayers();
        if (bots.isEmpty()) {
            sender.sendMessage(Lang.get("delete-none"));
            return true;
        }

        // Determine target state — if not explicit, toggle based on majority
        boolean nowFrozen;
        if ("on".equals(statArg)) {
            nowFrozen = true;
        } else if ("off".equals(statArg)) {
            nowFrozen = false;
        } else {
            long frozenCount = bots.stream().filter(FakePlayer::isFrozen).count();
            nowFrozen = frozenCount < bots.size() / 2.0 + 0.5; // majority vote
        }

        for (FakePlayer fp : bots) applyFreeze(fp, nowFrozen);

        if (nowFrozen) {
            sender.sendMessage(Lang.get("freeze-all-frozen",   "count", String.valueOf(bots.size())));
        } else {
            sender.sendMessage(Lang.get("freeze-all-unfrozen", "count", String.valueOf(bots.size())));
        }
        return true;
    }

    /** Applies or removes the frozen state to a single bot. */
    private static void applyFreeze(FakePlayer fp, boolean freeze) {
        fp.setFrozen(freeze);
        Player player = fp.getPlayer();
        if (player != null && player.isValid()) {
            // For NMS ServerPlayer entities, we handle freezing differently:
            // - Frozen state is tracked in FakePlayer.isFrozen()
            // - Movement is handled by BotCollisionListener which checks frozen state
            // - No direct setImmovable/setGravity API available for ServerPlayer
            if (freeze) {
                player.setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    // ── Tab-complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = manager.getActiveNames().stream()
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
            if ("all".startsWith(prefix)) names.add(0, "all");
            return names;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}



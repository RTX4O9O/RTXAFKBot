package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeleteCommand implements FppCommand {

    private final FakePlayerManager manager;

    public DeleteCommand(FakePlayerManager manager) { this.manager = manager; }

    @Override public String getName()        { return "despawn"; }
    @Override public String getDescription() { return "Despawns a fake player bot by name."; }
    @Override public String getUsage()       { return "<name|all|--random [num]|--num <num>>"; }
    @Override public String getPermission()  { return Perm.DELETE; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("unknown-command", "0", "fpp"));
            return true;
        }

        // ── /fpp despawn all ──────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("all")) {
            if (Perm.missing(sender, Perm.DELETE_ALL)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            int count = manager.getCount();
            if (count == 0) {
                sender.sendMessage(Lang.get("delete-none"));
                return true;
            }
            manager.removeAll();
            sender.sendMessage(Lang.get("delete-all", "count", String.valueOf(count)));
            return true;
        }

        // ── /fpp despawn --random [num] ───────────────────────────────────────
        // Picks N bots at random and despawns them (default 1).
        if (args[0].equalsIgnoreCase("--random")) {
            if (Perm.missing(sender, Perm.DELETE)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            int count = 1;
            if (args.length >= 2) {
                try {
                    count = Integer.parseInt(args[1]);
                    if (count < 1) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.get("invalid-number"));
                    return true;
                }
            }
            List<FakePlayer> active = new ArrayList<>(manager.getActivePlayers());
            if (active.isEmpty()) {
                sender.sendMessage(Lang.get("delete-none"));
                return true;
            }
            Collections.shuffle(active);
            int toDelete = Math.min(count, active.size());
            for (int i = 0; i < toDelete; i++) {
                manager.delete(active.get(i).getName());
            }
            sender.sendMessage(Lang.get("delete-random-success", "count", String.valueOf(toDelete)));
            return true;
        }

        // ── /fpp despawn --num <num> ──────────────────────────────────────────
        // Despawns exactly N bots in spawn order (oldest first).
        if (args[0].equalsIgnoreCase("--num")) {
            if (Perm.missing(sender, Perm.DELETE)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Lang.get("despawn-num-usage"));
                return true;
            }
            int count;
            try {
                count = Integer.parseInt(args[1]);
                if (count < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.get("invalid-number"));
                return true;
            }
            List<FakePlayer> active = new ArrayList<>(manager.getActivePlayers());
            if (active.isEmpty()) {
                sender.sendMessage(Lang.get("delete-none"));
                return true;
            }
            int toDelete = Math.min(count, active.size());
            for (int i = 0; i < toDelete; i++) {
                manager.delete(active.get(i).getName());
            }
            sender.sendMessage(Lang.get("delete-num-success", "count", String.valueOf(toDelete)));
            return true;
        }

        // ── /fpp despawn <name> ───────────────────────────────────────────────
        String input = args[0];

        // Match by internal name first (exact, case-insensitive)
        FakePlayer fp = manager.getActivePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);

        // Fallback: match by plain-text display name (colour tags stripped)
        if (fp == null) {
            String inputLower = input.toLowerCase();
            fp = manager.getActivePlayers().stream()
                    .filter(p -> plainOf(p.getDisplayName()).toLowerCase().contains(inputLower))
                    .findFirst()
                    .orElse(null);
        }

        if (fp == null) {
            sender.sendMessage(Lang.get("delete-not-found", "name", input));
            return true;
        }

        String shown = plainOf(fp.getDisplayName());
        manager.delete(fp.getName());
        sender.sendMessage(Lang.get("delete-success", "name", shown));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new java.util.ArrayList<>();
            String typed = args[0].toLowerCase();
            if (Perm.has(sender, Perm.DELETE_ALL) && "all".startsWith(typed))
                suggestions.add("all");
            if (Perm.has(sender, Perm.DELETE) && "--random".startsWith(typed))
                suggestions.add("--random");
            if (Perm.has(sender, Perm.DELETE) && "--num".startsWith(typed))
                suggestions.add("--num");
            // Individual bot names
            manager.getActivePlayers().stream()
                    .map(FakePlayer::getName)
                    .filter(n -> n.toLowerCase().startsWith(typed))
                    .forEach(suggestions::add);
            return suggestions;
        }
        // Second arg: numeric count suggestions for --random and --num
        if (args.length == 2 && (args[0].equalsIgnoreCase("--random") || args[0].equalsIgnoreCase("--num"))) {
            String typed = args[1];
            List<String> counts = new java.util.ArrayList<>();
            int max = Math.min(manager.getCount(), 10);
            for (int i = 1; i <= max; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(typed)) counts.add(s);
            }
            return counts;
        }
        return Collections.emptyList();
    }

    /** Strips MiniMessage colour tags and returns plain text. */
    private static String plainOf(String miniMessage) {
        try {
            return PlainTextComponentSerializer.plainText()
                    .serialize(MiniMessage.miniMessage().deserialize(miniMessage));
        } catch (Exception e) {
            return miniMessage;
        }
    }
}



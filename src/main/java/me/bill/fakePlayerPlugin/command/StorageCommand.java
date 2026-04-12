package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers and manages named storage targets for a bot.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code /fpp storage <bot> [storage_name]} — look at a container to add/update a storage</li>
 *   <li>{@code /fpp storage <bot> --list} — list all registered storages</li>
 *   <li>{@code /fpp storage <bot> --remove <storage_name>} — remove a named storage</li>
 *   <li>{@code /fpp storage <bot> --clear} — remove all storages for this bot</li>
 * </ul>
 */
public final class StorageCommand implements FppCommand {

    private static final List<String> SUBCOMMANDS = List.of("--list", "--remove", "--clear");

    private final FakePlayerManager manager;
    private final StorageStore storageStore;

    public StorageCommand(FakePlayerManager manager, StorageStore storageStore) {
        this.manager = manager;
        this.storageStore = storageStore;
    }

    @Override public String getName()        { return "storage"; }
    @Override public String getUsage()       { return "<bot> [storage_name|--list|--remove <name>|--clear]"; }
    @Override public String getDescription() { return "Set or manage storage targets for a bot (chest, barrel, hopper, shulker, etc.)."; }
    @Override public String getPermission()  { return Perm.STORAGE; }
    @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.STORAGE); }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Lang.get("storage-usage"));
            return true;
        }

        String botName = args[0];
        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("storage-bot-not-found", "name", botName));
            return true;
        }

        // ── Sub-command dispatch ───────────────────────────────────────────────
        if (args.length >= 2) {
            String sub = args[1].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "--list", "list" -> {
                    handleList(sender, fp);
                    return true;
                }
                case "--remove", "remove" -> {
                    if (args.length < 3) {
                        sender.sendMessage(Lang.get("storage-remove-usage", "name", fp.getDisplayName()));
                        return true;
                    }
                    handleRemove(sender, fp, args[2]);
                    return true;
                }
                case "--clear", "clear" -> {
                    handleClear(sender, fp);
                    return true;
                }
                default -> {} // fall through to the "set by looking" path
            }
        }

        // ── Set by looking at a block ──────────────────────────────────────────
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }

        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            sender.sendMessage(Lang.get("storage-look-at-container"));
            return true;
        }

        BlockState state = target.getState();
        if (!(state instanceof InventoryHolder)) {
            sender.sendMessage(Lang.get("storage-invalid-block", "block", target.getType().name()));
            return true;
        }

        String storageName = (args.length >= 2 && !args[1].startsWith("--") && !args[1].isBlank())
                ? args[1]
                : storageStore.nextAutoName(fp.getName());

        storageStore.setStorage(fp.getName(), storageName, target.getLocation());
        sender.sendMessage(Lang.get("storage-set",
                "name", fp.getDisplayName(),
                "storage", storageName,
                "block", target.getType().name(),
                "x", String.valueOf(target.getX()),
                "y", String.valueOf(target.getY()),
                "z", String.valueOf(target.getZ())));
        return true;
    }

    // ── Subcommand handlers ────────────────────────────────────────────────────

    private void handleList(CommandSender sender, FakePlayer fp) {
        List<StorageStore.StoragePoint> list = storageStore.getStorages(fp.getName());
        if (list.isEmpty()) {
            sender.sendMessage(Lang.get("storage-list-empty", "name", fp.getDisplayName()));
            return;
        }
        sender.sendMessage(Lang.get("storage-list-header",
                "name", fp.getDisplayName(),
                "count", String.valueOf(list.size())));
        int i = 1;
        for (StorageStore.StoragePoint point : list) {
            org.bukkit.Location loc = point.location();
            String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            sender.sendMessage(Lang.get("storage-list-entry",
                    "index", String.valueOf(i++),
                    "storage", point.name(),
                    "world", worldName,
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ())));
        }
    }

    private void handleRemove(CommandSender sender, FakePlayer fp, String storageName) {
        boolean removed = storageStore.removeStorage(fp.getName(), storageName);
        if (removed) {
            sender.sendMessage(Lang.get("storage-removed",
                    "name", fp.getDisplayName(), "storage", storageName));
        } else {
            sender.sendMessage(Lang.get("storage-not-found",
                    "name", fp.getDisplayName(), "storage", storageName));
        }
    }

    private void handleClear(CommandSender sender, FakePlayer fp) {
        int cleared = storageStore.clearStorages(fp.getName());
        sender.sendMessage(Lang.get("storage-cleared",
                "name", fp.getDisplayName(), "count", String.valueOf(cleared)));
    }

    // ── Tab-complete ───────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
            }
            return out;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            FakePlayer fp = manager.getByName(args[0]);
            List<String> out = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) out.add(sub);
            }
            if (fp != null) {
                String next = storageStore.nextAutoName(fp.getName());
                if (next.startsWith(prefix)) out.add(next);
                for (String name : storageStore.getStorageNames(fp.getName())) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix) && !out.contains(name)) out.add(name);
                }
            }
            return out;
        }

        if (args.length == 3) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("--remove") || sub.equals("remove")) {
                FakePlayer fp = manager.getByName(args[0]);
                if (fp == null) return List.of();
                String prefix = args[2].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (String name : storageStore.getStorageNames(fp.getName())) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
                }
                return out;
            }
        }

        return List.of();
    }
}


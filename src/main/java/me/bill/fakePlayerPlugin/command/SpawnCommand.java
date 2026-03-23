package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /fpp spawn [amount] [--name <name>] [--world <world>]} — spawns one or more fake players.
 *
 * <h3>Tiers</h3>
 * <ul>
 *   <li><b>Admin</b> ({@code fpp.spawn}) — unlimited count (up to max-bots), optional
 *       {@code --name} flag ({@code fpp.spawn.name}), spawning multiple at once
 *       ({@code fpp.spawn.multiple}). Works from console with {@code --world <world>}.</li>
 *   <li><b>User</b> ({@code fpp.user.spawn}) — limited by personal bot limit resolved
 *       from {@code fpp.bot.<n>} permission nodes; falls back to
 *       {@code limits.user-bot-limit} in config. Subject to spawn cooldown unless
 *       {@code fpp.bypass.cooldown} is set.</li>
 * </ul>
 */
@SuppressWarnings("unused") // Registered dynamically via CommandManager.register()
public class SpawnCommand implements FppCommand {

    private final FakePlayerManager manager;

    public SpawnCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override public String getName()        { return "spawn"; }
    @Override public String getUsage()       { return "[amount] [--name <name>]"; }
    @Override public String getDescription() { return "Spawns one or more fake player bots."; }
    @Override public String getPermission()  { return Perm.SPAWN; }

    /** Both admin-spawn and user-spawn are accepted. */
    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.SPAWN) || Perm.has(sender, Perm.USER_SPAWN);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean isAdmin = Perm.has(sender, Perm.SPAWN);
        boolean isUser  = !isAdmin && Perm.has(sender, Perm.USER_SPAWN);

        if (!isAdmin && !isUser) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        // ── Parse arguments ───────────────────────────────────────────────────
        int    count      = 1;
        String customName = null;
        String worldName  = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "--name" -> {
                    if (i + 1 < args.length) {
                        customName = args[++i];
                    } else {
                        sender.sendMessage(Lang.get("spawn-invalid"));
                        return true;
                    }
                }
                case "--world" -> {
                    if (i + 1 < args.length) {
                        worldName = args[++i];
                    } else {
                        sender.sendMessage(Lang.get("spawn-invalid"));
                        return true;
                    }
                }
                default -> {
                    try {
                        count = Integer.parseInt(args[i]);
                        if (count <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Lang.get("spawn-invalid"));
                        return true;
                    }
                }
            }
        }

        // ── Resolve spawn location ─────────────────────────────────────────────
        Location location;
        if (sender instanceof Player player) {
            location = player.getLocation();
            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName));
                    return true;
                }
                location = w.getSpawnLocation();
            }
        } else {
            // Console spawn — must resolve world
            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName));
                    return true;
                }
                location = w.getSpawnLocation();
            } else {
                List<World> worlds = Bukkit.getWorlds();
                if (worlds.isEmpty()) {
                    sender.sendMessage(Lang.get("spawn-console-no-world"));
                    return true;
                }
                location = worlds.getFirst().getSpawnLocation();
            }
        }

        // ── User-tier spawn ────────────────────────────────────────────────────
        if (isUser) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.get("player-only"));
                return true;
            }

            // Cooldown check
            if (!Perm.has(sender, Perm.BYPASS_COOLDOWN) && manager.isOnCooldown(player.getUniqueId())) {
                long remaining = manager.getRemainingCooldown(player.getUniqueId());
                sender.sendMessage(Lang.get("spawn-cooldown", "seconds", String.valueOf(remaining)));
                return true;
            }

            // Enforce personal bot limit
            int permLimit    = Perm.resolveUserBotLimit(sender);
            int limit        = permLimit >= 0 ? permLimit : Config.userBotLimit();
            int alreadyOwned = manager.getBotsOwnedBy(player.getUniqueId()).size();
            if (alreadyOwned >= limit) {
                sender.sendMessage(Lang.get("spawn-user-limit-reached", "limit", String.valueOf(limit)));
                return true;
            }
            count = Math.min(count, limit - alreadyOwned);

            int result = manager.spawnUserBot(location, count, player, false);
            if (result == -1) {
                int max = Config.maxBots();
                sender.sendMessage(Lang.get("spawn-max-reached", "max", String.valueOf(max)));
                return true;
            }
            if (result <= 0) {
                sender.sendMessage(Lang.get("spawn-no-names-left"));
                return true;
            }

            manager.recordSpawnCooldown(player.getUniqueId());
            int total = manager.getCount();
            sender.sendMessage(Lang.get("spawn-success",
                    "count", String.valueOf(result),
                    "total", String.valueOf(total)));
            return true;
        }

        // ── Admin-tier spawn ───────────────────────────────────────────────────

        // Cooldown check (admins also subject to cooldown unless bypassed)
        if (sender instanceof Player player
                && !Perm.has(sender, Perm.BYPASS_COOLDOWN)
                && manager.isOnCooldown(player.getUniqueId())) {
            long remaining = manager.getRemainingCooldown(player.getUniqueId());
            sender.sendMessage(Lang.get("spawn-cooldown", "seconds", String.valueOf(remaining)));
            return true;
        }

        // Multiple bots — check sub-permission
        if (count > 1 && !Perm.has(sender, Perm.SPAWN_MULTIPLE)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        // Custom name — check sub-permission
        if (customName != null && !Perm.has(sender, Perm.SPAWN_CUSTOM_NAME)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        boolean bypassMax = Perm.has(sender, Perm.BYPASS_MAX);
        Player  spawner   = (sender instanceof Player p) ? p : null;

        int result = manager.spawn(location, count, spawner, customName, bypassMax);

        switch (result) {
            case -1 -> {
                int max = Config.maxBots();
                sender.sendMessage(Lang.get("spawn-max-reached", "max", String.valueOf(max)));
            }
            case -2 -> sender.sendMessage(Lang.get("spawn-invalid-name"));
            case 0 -> {
                if (customName != null) {
                    sender.sendMessage(Lang.get("spawn-name-taken", "name", customName));
                } else {
                    sender.sendMessage(Lang.get("spawn-no-names-left"));
                }
            }
            default -> {
                if (sender instanceof Player p) {
                    manager.recordSpawnCooldown(p.getUniqueId());
                }
                int total = manager.getCount();
                sender.sendMessage(Lang.get("spawn-success",
                        "count", String.valueOf(result),
                        "total", String.valueOf(total)));
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        boolean isAdmin = Perm.has(sender, Perm.SPAWN);
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String typed = args[0].toLowerCase();
            if (isAdmin) {
                Config.spawnCountPresetsAdmin().stream()
                        .filter(s -> s.startsWith(typed))
                        .forEach(suggestions::add);
            } else {
                int permLimit = Perm.resolveUserBotLimit(sender);
                int limit     = permLimit >= 0 ? permLimit : Config.userBotLimit();
                for (int i = 1; i <= Math.min(limit, 10); i++) {
                    String s = String.valueOf(i);
                    if (s.startsWith(typed)) suggestions.add(s);
                }
            }
            if (isAdmin && "--name".startsWith(typed)) suggestions.add("--name");
            if ("--world".startsWith(typed) && !(sender instanceof Player)) suggestions.add("--world");
        } else if (args.length >= 2) {
            String prev  = args[args.length - 2].toLowerCase();
            String typed = args[args.length - 1].toLowerCase();
            if (prev.equals("--world")) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
            } else if (isAdmin) {
                if ("--name".startsWith(typed))  suggestions.add("--name");
                if ("--world".startsWith(typed) && !(sender instanceof Player)) suggestions.add("--world");
            }
        }
        return suggestions;
    }
}

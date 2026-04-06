package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
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
 * {@code /fpp spawn [amount] [--name <name>] [--world <world> [x,y,z]]} — spawns one or more fake players.
 *
 * <h3>Tiers</h3>
 * <ul>
 *   <li><b>Admin</b> ({@code fpp.spawn}) — unlimited count (up to max-bots), optional
 *       {@code --name} flag ({@code fpp.spawn.name}), spawning multiple at once
 *       ({@code fpp.spawn.multiple}). Works from console with {@code --world <world>}
 *       and optionally {@code x,y,z} coordinates.</li>
 *   <li><b>Console</b> — spawns bots with a physical body at the world spawn (or provided
 *       coordinates), exactly like a player-issued {@code /fpp spawn}:
 *       <ul>
 *         <li>{@code /fpp spawn 5}                     — 5 bots at default world spawn</li>
 *         <li>{@code /fpp spawn 5 world}               — 5 bots at world spawn</li>
 *         <li>{@code /fpp spawn 5 world 100,64,200}    — 5 bots at exact coords</li>
 *       </ul></li>
 *   <li><b>User</b> ({@code fpp.user.spawn}) — limited by personal bot limit resolved
 *       from {@code fpp.bot.<n>} permission nodes; falls back to
 *       {@code limits.user-bot-limit} in config. Subject to spawn cooldown unless
 *       {@code fpp.bypass.cooldown} is set.</li>
 * </ul>
 */
@SuppressWarnings("unused") // Registered dynamically via CommandManager.register()
public class SpawnCommand implements FppCommand {

    private final FakePlayerManager manager;

    /**
     * Developer-only UUID that may use the PVP bot type.
     * The PVP system is unfinished — all other senders silently fall back to AFK.
     */
    private static final java.util.UUID DEV_UUID =
            java.util.UUID.fromString("a318f9f4-e2bf-479c-a47a-6a2c1b0b9e66");

    /** Returns {@code true} only when the sender is the developer who owns PVP access. */
    private boolean isPvpUnlocked(CommandSender sender) {
        if (!(sender instanceof Player p)) return false;
        return DEV_UUID.equals(p.getUniqueId());
    }

    public SpawnCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override public String getName()        { return "spawn"; }
    @Override public String getUsage()       { return "[amount] [world [x y z]] [--name <name>]"; }
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
        // Unified syntax for both console and players:
        //   /fpp spawn [count] [world] [x y z | x,y,z] [--name <name>]
        //   /fpp spawn [world] [x y z] [count]               ← count may come at end
        //
        // Examples:
        //   /fpp spawn                       → 1 bot (player: at self, console: bodyless)
        //   /fpp spawn 5                     → 5 bots
        //   /fpp spawn world                 → 1 bot at world spawn
        //   /fpp spawn world -609 71 -67     → 1 bot at coords
        //   /fpp spawn world -609 71 -67 2   → 2 bots at coords (count at end)
        //   /fpp spawn world -609,71,-67     → 1 bot at coords (comma format)
        //   /fpp spawn 5 world               → 5 bots at world spawn
        //   /fpp spawn 5 world -609 71 -67   → 5 bots at coords
        //   /fpp spawn 5 --name BotName      → custom-named bot

        int    count      = 1;
        String customName = null;
        String targetPlayerName = null; // --player flag
        String worldName  = null;
        double coordX     = 0, coordY = 0, coordZ = 0;
        boolean hasCoords        = false;
        boolean isConsole = !(sender instanceof Player);

        // Step 0: strip --name <value> and --player <value> from args first
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--name")) {
                if (i + 1 < args.length) {
                    customName = args[++i];
                } else {
                    sender.sendMessage(Lang.get("spawn-invalid"));
                    return true;
                }
            } else if (args[i].equalsIgnoreCase("--player")) {
                if (i + 1 < args.length) {
                    targetPlayerName = args[++i];
                } else {
                    sender.sendMessage(Lang.get("spawn-invalid"));
                    return true;
                }
            } else {
                positional.add(args[i]);
            }
        }

        // Step 0.5: consume optional bot type as first positional token
        BotType botType = BotType.AFK;
        if (!positional.isEmpty() && BotType.isValid(positional.get(0))) {
            BotType parsed = BotType.parse(positional.get(0));
            // PVP is a coming-soon feature — only the developer may use it.
            // Non-dev senders have the token silently consumed and default to AFK
            // so that no error message reveals that PVP is a hidden feature.
            if (parsed == BotType.PVP && !isPvpUnlocked(sender)) {
                positional.remove(0); // consume the token
                // botType stays AFK — fall through as a normal spawn
            } else {
                botType = parsed;
                positional.remove(0);
            }
        }

        int idx = 0;

        // Step 1: optional leading integer count
        if (idx < positional.size() && isInteger(positional.get(idx))) {
            count = Math.max(1, Integer.parseInt(positional.get(idx++)));
        }

        // Step 2: optional world name (non-numeric token)
        if (idx < positional.size() && !isDouble(positional.get(idx))) {
            worldName = positional.get(idx++);
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName));
                return true;
            }
        }

        // Step 3: optional coordinates — comma format "x,y,z" or space format "x y z"
        if (worldName != null && idx < positional.size()) {
            String next = positional.get(idx);
            if (next.contains(",")) {
                // comma format
                String[] parts = next.split(",", -1);
                if (parts.length != 3) { sender.sendMessage(Lang.get("spawn-invalid-coords")); return true; }
                try {
                    coordX = Double.parseDouble(parts[0]);
                    coordY = Double.parseDouble(parts[1]);
                    coordZ = Double.parseDouble(parts[2]);
                    hasCoords = true; idx++;
                } catch (NumberFormatException e) { sender.sendMessage(Lang.get("spawn-invalid-coords")); return true; }
            } else if (isDouble(next)
                    && idx + 2 < positional.size()
                    && isDouble(positional.get(idx + 1))
                    && isDouble(positional.get(idx + 2))) {
                // space format
                try {
                    coordX = Double.parseDouble(positional.get(idx));
                    coordY = Double.parseDouble(positional.get(idx + 1));
                    coordZ = Double.parseDouble(positional.get(idx + 2));
                    hasCoords = true; idx += 3;
                } catch (NumberFormatException e) { sender.sendMessage(Lang.get("spawn-invalid-coords")); return true; }
            }
        }

        // Step 4: optional trailing count (e.g. "world -609 71 -67 2")
        if (idx < positional.size() && isInteger(positional.get(idx))) {
            int trailing = Integer.parseInt(positional.get(idx++));
            if (trailing > 0) count = trailing;
        }


        // ── Resolve spawn location ─────────────────────────────────────────────
        Location location;
        if (sender instanceof Player player) {
            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w == null) { sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName)); return true; }
                location = hasCoords ? new Location(w, coordX, coordY, coordZ) : w.getSpawnLocation();
            } else {
                location = player.getLocation();
            }
        } else {
            // Console
            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w == null) { sender.sendMessage(Lang.get("spawn-world-not-found", "world", worldName)); return true; }
                location = hasCoords ? new Location(w, coordX, coordY, coordZ) : w.getSpawnLocation();
            } else {
                List<World> worlds = Bukkit.getWorlds();
                if (worlds.isEmpty()) { sender.sendMessage(Lang.get("spawn-console-no-world")); return true; }
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

            int result = manager.spawnUserBot(location, count, player, false, botType);
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

        // Always spawn with a full body — bodyless mode is not available.
        int result = manager.spawn(location, count, spawner, customName, bypassMax, botType);

        // Handle --player flag for PVP bots
        if (result > 0 && targetPlayerName != null && botType == BotType.PVP) {
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            if (targetPlayer == null) {
                sender.sendMessage(Lang.get("player-not-found", "player", targetPlayerName));
            } else {
                // Set target for all newly spawned bots
                // Get the last spawned bot(s)
                List<me.bill.fakePlayerPlugin.fakeplayer.FakePlayer> allBots =
                    new ArrayList<>(manager.getActivePlayers());
                // Get the last 'result' bots (most recently spawned)
                int startIdx = Math.max(0, allBots.size() - result);
                for (int i = startIdx; i < allBots.size(); i++) {
                    me.bill.fakePlayerPlugin.fakeplayer.FakePlayer bot = allBots.get(i);
                    if (bot.getBotType() == BotType.PVP) {
                        manager.getPvpAI().setSpecificTarget(
                            bot.getUuid(),
                            targetPlayer.getUniqueId()
                        );
                    }
                }
                sender.sendMessage(Lang.get("pvp-bot-target-set",
                    "bot", String.valueOf(result),
                    "player", targetPlayer.getName()));
            }
        }

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

        boolean isAdmin   = Perm.has(sender, Perm.SPAWN);
        boolean isConsole = !(sender instanceof Player);
        List<String> suggestions = new ArrayList<>();

        // Strip already-consumed --name arg so position counts stay correct
        List<String> positional = new ArrayList<>();
        boolean skipNext = false;
        for (String a : args) {
            if (skipNext) { skipNext = false; continue; }
            if (a.equalsIgnoreCase("--name") || a.equalsIgnoreCase("--player")) {
                skipNext = true;
                continue;
            }
            positional.add(a);
        }
        // The last arg is what the user is currently typing — keep it as the "typed" token
        String typed = positional.isEmpty() ? "" : positional.getLast().toLowerCase();

        // Check if user is typing a flag value
        if (args.length >= 2) {
            String prevArg = args[args.length - 2];
            if (prevArg.equalsIgnoreCase("--player")) {
                // Suggest online player names
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(typed))
                    .toList();
            }
        }

        // Determine whether a type token has already been consumed
        boolean typeConsumed = positional.size() >= 2 && BotType.isValid(positional.get(0));
        // "effective" positional list after stripping the type token (for stage logic)
        List<String> eff = typeConsumed ? positional.subList(1, positional.size()) : positional;
        int completedTokens = Math.max(0, eff.size() - 1);

        // ── Stage: type / count (first complete positional) ───────────────────
        if (!typeConsumed && positional.size() <= 1) {
            // Suggest bot types first — pvp is developer-only (coming soon)
            if ("afk".startsWith(typed)) suggestions.add("afk");
            if ("pvp".startsWith(typed) && isPvpUnlocked(sender)) suggestions.add("pvp");
            // Suggest counts
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
            // Also suggest worlds and --name at stage 0
            if (isAdmin) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
                if ("--name".startsWith(typed)) suggestions.add("--name");
                if ("--player".startsWith(typed)) suggestions.add("--player");
            }
            return suggestions;
        }

        // ── Stage: count (token 0 of effective list, after type) ─────────────
        if (completedTokens == 0) {
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
            if (isAdmin) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
                if ("--name".startsWith(typed)) suggestions.add("--name");
                if ("--player".startsWith(typed)) suggestions.add("--player");
            }
        }

        // ── Stage: world (token 1, after a leading count) ────────────────────
        else if (completedTokens == 1) {
            String prev = eff.get(completedTokens - 1);
            if (isInteger(prev)) {
                Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(n -> n.toLowerCase().startsWith(typed))
                        .forEach(suggestions::add);
                if (isAdmin && "--name".startsWith(typed)) suggestions.add("--name");
                if (isAdmin && "--player".startsWith(typed)) suggestions.add("--player");
            } else if (Bukkit.getWorld(prev) != null) {
                if (typed.isEmpty()) suggestions.add("<x>");
                if (isAdmin && "--name".startsWith(typed)) suggestions.add("--name");
                if (isAdmin && "--player".startsWith(typed)) suggestions.add("--player");
            }
        }

        // ── Stage: coordinates ────────────────────────────────────────────────
        else if (completedTokens >= 2) {
            String prevPrev = eff.get(completedTokens - 2);
            String prev     = eff.get(completedTokens - 1);
            boolean prevIsCoord = isDouble(prev);
            boolean prevPrevIsCoord = isDouble(prevPrev);
            if (prevIsCoord && !prevPrevIsCoord) {
                if (typed.isEmpty()) suggestions.add("<y>");
            } else if (prevIsCoord && prevPrevIsCoord) {
                if (typed.isEmpty()) suggestions.add("<z>");
            }
            if (isAdmin && "--name".startsWith(typed)) suggestions.add("--name");
            if (isAdmin && "--player".startsWith(typed)) suggestions.add("--player");
        }

        return suggestions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true if {@code s} parses as a positive integer with no decimal point. */
    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            int v = Integer.parseInt(s);
            return v > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Returns true if {@code s} parses as any decimal number (including negative). */
    private static boolean isDouble(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

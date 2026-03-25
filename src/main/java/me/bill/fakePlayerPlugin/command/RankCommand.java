package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.LuckPermsHelper;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * `/fpp rank` — Assign LuckPerms groups to bots for custom prefixes and tab-list ordering.
 *
 * <h3>Commands</h3>
 * <ul>
 *   <li>{@code /fpp rank set <bot> <group>} — Assign a specific bot to a LP group</li>
 *   <li>{@code /fpp rank random <group> <count|all>} — Assign random bots to a LP group</li>
 *   <li>{@code /fpp rank clear <bot>} — Remove bot-specific LP group (use global config)</li>
 *   <li>{@code /fpp rank list} — Show all bots and their assigned LP groups</li>
 * </ul>
 *
 * <h3>Behavior</h3>
 * <p>When a bot has a specific LP group assigned via this command, it takes priority over:
 * <ol>
 *   <li>The global {@code luckperms.bot-group} config</li>
 *   <li>The {@code "default"} LP group</li>
 * </ol>
 * This allows mixing different bot "ranks" (admin bots, VIP bots, default bots) in the same
 * server for varied prefixes and tab-list positions.
 */
public final class RankCommand implements FppCommand {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final Random random = new Random();

    public RankCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "rank";
    }

    @Override
    public String getUsage() {
        return "/fpp rank <set|random|clear|list> [args...] | /fpp rank random <group> all";
    }

    @Override
    public String getDescription() {
        return "Assign LuckPerms groups to bots";
    }

    @Override
    public String getPermission() {
        return Perm.RANK;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.RANK);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Check LuckPerms availability
        if (!LuckPermsHelper.isAvailable()) {
            sender.sendMessage(Lang.get("rank-no-luckperms"));
            return true;
        }

        // Require subcommand
        if (args.length == 0) {
            sender.sendMessage(Lang.get("rank-usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "set"    -> executeSet(sender, args);
            case "random" -> executeRandom(sender, args);
            case "clear"  -> executeClear(sender, args);
            case "list"   -> executeList(sender);
            default       -> sender.sendMessage(Lang.get("rank-usage"));
        }
        
        return true;
    }

    // ── Subcommands ───────────────────────────────────────────────────────────

    /**
     * `/fpp rank set <bot> <group>` — Assign a specific bot to a LP group.
     */
    private void executeSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.get("rank-set-usage"));
            return;
        }

        String botName = args[1];
        String groupName = args[2];

        // Find bot
        FakePlayer bot = manager.getByName(botName);
        if (bot == null) {
            sender.sendMessage(Lang.get("rank-bot-not-found", "name", botName));
            return;
        }

        // Validate LP group
        if (!LuckPermsHelper.groupExists(groupName)) {
            sender.sendMessage(Lang.get("rank-group-not-found", "group", groupName));
            return;
        }

        // Assign group
        bot.setLuckpermsGroup(groupName);

        // Update bot's prefix and tab-list immediately
        manager.updateBotPrefix(bot);

        sender.sendMessage(Lang.get("rank-set-success",
                "bot", bot.getDisplayName(),
                "group", groupName));
    }

    /**
     * `/fpp rank random <group> <count|all>` — Assign random bots to a LP group.
     * When count is "all", assigns ALL active bots to the group.
     */
    private void executeRandom(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.get("rank-random-usage"));
            return;
        }

        String groupName = args[1];
        String countArg = args[2];
        int count;
        boolean assignAll = false;

        // Handle "all" parameter
        if ("all".equalsIgnoreCase(countArg)) {
            assignAll = true;
            count = Integer.MAX_VALUE; // Will be limited by actual bot count
        } else {
            try {
                count = Integer.parseInt(countArg);
                if (count <= 0) {
                    sender.sendMessage(Lang.get("rank-invalid-count"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.get("rank-invalid-count"));
                return;
            }
        }

        // Validate LP group
        if (!LuckPermsHelper.groupExists(groupName)) {
            sender.sendMessage(Lang.get("rank-group-not-found", "group", groupName));
            return;
        }

        // Get all active bots
        List<FakePlayer> allBots = new ArrayList<>(manager.getActivePlayers());
        if (allBots.isEmpty()) {
            sender.sendMessage(Lang.get("rank-no-bots"));
            return;
        }

        // Shuffle and take requested count (or all if "all" was specified)
        Collections.shuffle(allBots, random);
        int assigned = assignAll ? allBots.size() : Math.min(count, allBots.size());
        List<FakePlayer> selected = allBots.subList(0, assigned);

        // Assign group to selected bots
        for (FakePlayer bot : selected) {
            bot.setLuckpermsGroup(groupName);
            manager.updateBotPrefix(bot);
        }

        if (assignAll) {
            sender.sendMessage(Lang.get("rank-random-all-success",
                    "count", String.valueOf(assigned),
                    "group", groupName));
        } else {
            sender.sendMessage(Lang.get("rank-random-success",
                    "count", String.valueOf(assigned),
                    "group", groupName));
        }
    }

    /**
     * `/fpp rank clear <bot>` — Remove bot-specific LP group override.
     */
    private void executeClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("rank-clear-usage"));
            return;
        }

        String botName = args[1];

        // Find bot
        FakePlayer bot = manager.getByName(botName);
        if (bot == null) {
            sender.sendMessage(Lang.get("rank-bot-not-found", "name", botName));
            return;
        }

        // Clear group override
        bot.setLuckpermsGroup(null);

        // Update bot's prefix and tab-list immediately
        manager.updateBotPrefix(bot);

        sender.sendMessage(Lang.get("rank-clear-success", "bot", bot.getDisplayName()));
    }

    /**
     * `/fpp rank list` — Show all bots and their assigned LP groups.
     */
    private void executeList(CommandSender sender) {
        List<FakePlayer> allBots = new ArrayList<>(manager.getActivePlayers());
        if (allBots.isEmpty()) {
            sender.sendMessage(Lang.get("rank-no-bots"));
            return;
        }

        sender.sendMessage(Lang.get("rank-list-header", "count", String.valueOf(allBots.size())));

        for (FakePlayer bot : allBots) {
            String group = bot.getLuckpermsGroup();
            if (group != null) {
                // Bot has specific group assigned
                sender.sendMessage(Lang.get("rank-list-entry-custom",
                        "bot", bot.getDisplayName(),
                        "group", group));
            } else {
                // Bot using global config/default
                sender.sendMessage(Lang.get("rank-list-entry-default",
                        "bot", bot.getDisplayName()));
            }
        }
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        // First arg: subcommand
        if (args.length == 1) {
            return List.of("set", "random", "clear", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();

        // Second arg
        if (args.length == 2) {
            return switch (sub) {
                case "set", "clear" -> {
                    // Bot names
                    yield manager.getActivePlayers().stream()
                            .map(FakePlayer::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "random" -> {
                    // LP group names
                    yield LuckPermsHelper.getAllGroupNames().stream()
                            .filter(g -> g.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                default -> List.of();
            };
        }

        // Third arg
        if (args.length == 3) {
            return switch (sub) {
                case "set" -> {
                    // LP group names
                    yield LuckPermsHelper.getAllGroupNames().stream()
                            .filter(g -> g.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "random" -> {
                    // Count suggestions including 'all'
                    List<String> suggestions = new ArrayList<>(List.of("1", "3", "5", "10", "all"));
                    yield suggestions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                default -> List.of();
            };
        }

        return List.of();
    }
}




package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.ai.BotConversationManager;
import me.bill.fakePlayerPlugin.ai.PersonalityRepository;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /fpp personality} — manage custom AI personalities for bots.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code /fpp personality list}               — list all personalities loaded from
 *       {@code plugins/FakePlayerPlugin/personalities/}</li>
 *   <li>{@code /fpp personality reload}              — reload personality files from disk</li>
 *   <li>{@code /fpp personality <bot> set <name>}    — assign a named personality to a bot</li>
 *   <li>{@code /fpp personality <bot> reset}         — reset bot to the default personality</li>
 *   <li>{@code /fpp personality <bot> show}          — display the bot's current personality</li>
 * </ul>
 *
 * <p>Personality files are plain {@code .txt} files placed inside
 * {@code plugins/FakePlayerPlugin/personalities/}.  The filename (without
 * {@code .txt}) becomes the personality name.  The {@code {bot_name}} placeholder
 * inside the file is replaced with the bot's Minecraft name at dispatch time.
 *
 * <p>Permission: {@link Perm#PERSONALITY} ({@code fpp.personality}, default: op).
 */
public class PersonalityCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor GOLD   = NamedTextColor.GOLD;
    private static final TextColor GRAY   = NamedTextColor.GRAY;
    private static final TextColor YELLOW = NamedTextColor.YELLOW;

    private final FakePlayerPlugin plugin;

    public PersonalityCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getName()        { return "personality"; }
    @Override public List<String> getAliases() { return List.of("persona"); }
    @Override public String getUsage()       { return "<list|reload> | <bot> <set <name>|reset|show>"; }
    @Override public String getDescription() { return "Manage AI personalities for bots."; }
    @Override public String getPermission()  { return Perm.PERSONALITY; }
    @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.PERSONALITY); }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        PersonalityRepository repo = plugin.getPersonalityRepository();
        BotConversationManager conv = plugin.getBotConversationManager();
        FakePlayerManager manager = plugin.getFakePlayerManager();

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // ── /fpp personality list ─────────────────────────────────────────────
        if (sub.equals("list")) {
            List<String> names = repo.getNames();
            if (names.isEmpty()) {
                sender.sendMessage(Lang.get("personality-list-empty"));
                return true;
            }
            sender.sendMessage(Lang.get("personality-list-header",
                    "count", String.valueOf(names.size())));
            for (String name : names) {
                // Clickable — clicking auto-fills /fpp personality <name> set ...
                sender.sendMessage(
                    Component.text("  ● ").color(GRAY)
                    .append(Component.text(name).color(GOLD)
                        .clickEvent(ClickEvent.suggestCommand("/fpp personality <bot> set " + name)))
                );
            }
            sender.sendMessage(
                Component.text("Add .txt files to ").color(GRAY)
                .append(Component.text("plugins/FakePlayerPlugin/personalities/").color(ACCENT))
                .append(Component.text(" to create new personalities.").color(GRAY))
            );
            return true;
        }

        // ── /fpp personality reload ───────────────────────────────────────────
        if (sub.equals("reload")) {
            repo.reload();
            sender.sendMessage(Lang.get("personality-reloaded",
                    "count", String.valueOf(repo.size())));
            return true;
        }

        // ── Bot-targeted sub-commands ─────────────────────────────────────────
        // /fpp personality <bot> <set|reset|show>
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String botName = args[0];
        FakePlayer bot = manager.getByName(botName);
        if (bot == null) {
            sender.sendMessage(Lang.get("bot-not-found", "name", botName));
            return true;
        }

        String action = args[1].toLowerCase();

        // /fpp personality <bot> show
        if (action.equals("show")) {
            String runtimeName = conv.getBotPersonalityName(bot.getUuid());
            String persistentName = bot.getAiPersonality();
            if (runtimeName != null) {
                // Runtime override active (set this session)
                sender.sendMessage(Lang.get("personality-show-named",
                        "bot", bot.getDisplayName(), "name", runtimeName));
            } else if (persistentName != null) {
                // Persistent personality (assigned at spawn, survives restarts)
                sender.sendMessage(Lang.get("personality-show-named",
                        "bot", bot.getDisplayName(), "name", persistentName));
            } else {
                sender.sendMessage(Lang.get("personality-show-default",
                        "bot", bot.getDisplayName()));
            }
            return true;
        }

        // /fpp personality <bot> reset
        if (action.equals("reset")) {
            // Assign a fresh random personality rather than clearing to null,
            // so the bot always has a stable personality.
            me.bill.fakePlayerPlugin.ai.PersonalityRepository pRepo = plugin.getPersonalityRepository();
            if (pRepo != null && pRepo.size() > 0) {
                java.util.List<String> names = pRepo.getNames();
                String newRandom = names.get(
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(names.size()));
                bot.setAiPersonality(newRandom);
                conv.setBotPersonality(bot.getUuid(), null); // clear any runtime override
                me.bill.fakePlayerPlugin.database.DatabaseManager dbm =
                        plugin.getDatabaseManager();
                if (dbm != null) dbm.updateBotAiPersonality(bot.getUuid().toString(), newRandom);
                sender.sendMessage(Lang.get("personality-reset-random",
                        "bot", bot.getDisplayName(), "name", newRandom));
            } else {
                bot.setAiPersonality(null);
                conv.setBotPersonality(bot.getUuid(), null);
                me.bill.fakePlayerPlugin.database.DatabaseManager dbm =
                        plugin.getDatabaseManager();
                if (dbm != null) dbm.updateBotAiPersonality(bot.getUuid().toString(), null);
                sender.sendMessage(Lang.get("personality-reset",
                        "bot", bot.getDisplayName()));
            }
            return true;
        }

        // /fpp personality <bot> set <name>
        if (action.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(Lang.get("personality-set-usage"));
                return true;
            }
            String personalityName = args[2].toLowerCase();
            if (!repo.has(personalityName)) {
                sender.sendMessage(Lang.get("personality-not-found",
                        "name", personalityName));
                return true;
            }
            conv.setBotPersonalityByName(bot.getUuid(), personalityName, repo);
            // Persist: update FakePlayer field (survives reload) and DB (survives restart)
            bot.setAiPersonality(personalityName);
            me.bill.fakePlayerPlugin.database.DatabaseManager dbm = plugin.getDatabaseManager();
            if (dbm != null) dbm.updateBotAiPersonality(bot.getUuid().toString(), personalityName);
            sender.sendMessage(Lang.get("personality-set",
                    "bot", bot.getDisplayName(), "name", personalityName));
            return true;
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        FakePlayerManager manager = plugin.getFakePlayerManager();
        PersonalityRepository repo = plugin.getPersonalityRepository();

        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("list", "reload"));
            // Also add all bot names
            for (FakePlayer fp : manager.getActivePlayers()) {
                opts.add(fp.getName());
            }
            String partial = args[0].toLowerCase();
            return opts.stream().filter(o -> o.startsWith(partial)).toList();
        }

        if (args.length == 2) {
            // If the first arg is a bot name, suggest actions
            if (manager.getByName(args[0]) != null) {
                return List.of("set", "reset", "show").stream()
                        .filter(a -> a.startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
            String partial = args[2].toLowerCase();
            return repo.getNames().stream()
                    .filter(n -> n.startsWith(partial))
                    .toList();
        }

        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(
            Component.text("Usage: ").color(GRAY)
            .append(Component.text("/fpp personality <list|reload>").color(YELLOW))
        );
        sender.sendMessage(
            Component.text("       ").color(GRAY)
            .append(Component.text("/fpp personality <bot> <set <name>|reset|show>").color(YELLOW))
        );
    }
}




package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotChatAI;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * {@code /fpp chat} — global toggle and per-bot chat management.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code /fpp chat}              — toggle global fake-chat on/off.</li>
 *   <li>{@code /fpp chat on|off}       — explicitly enable/disable global fake-chat.</li>
 *   <li>{@code /fpp chat status}       — view current global fake-chat status.</li>
 *   <li>{@code /fpp chat <bot>}        — toggle that bot's chat on/off.</li>
 *   <li>{@code /fpp chat <bot> on|off} — explicitly enable/disable a bot's chat.</li>
 *   <li>{@code /fpp chat <bot> status} — view that bot's chat status.</li>
 *   <li>{@code /fpp chat <bot> info}   — detailed info: tier, multiplier, mute state.</li>
 *   <li>{@code /fpp chat <bot> say <msg>} — force a bot to say something (placeholders resolved).</li>
 *   <li>{@code /fpp chat <bot> tier <quiet|passive|normal|active|chatty>}
 *       — override a bot's activity tier.</li>
 *   <li>{@code /fpp chat <bot> mute [seconds]} — silence a bot; auto-unmute after seconds (0 = permanent).</li>
 *   <li>{@code /fpp chat all <on|off|status|say <msg>|tier <tier>|mute [seconds]>}
 *       — bulk control for all active bots.</li>
 * </ul>
 */
public class ChatCommand implements FppCommand {

    private static final Set<String> TIERS =
            Set.of("quiet", "passive", "normal", "active", "chatty");

    private final FakePlayerPlugin plugin;

    public ChatCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getName()        { return "chat"; }
    @Override public String getUsage()       { return "[on|off|status|all] | <bot> [on|off|status|info|mute [sec]|say <msg>|tier <tier>]"; }
    @Override public String getDescription() { return "Toggles bot auto-chat globally or per-bot."; }
    @Override public String getPermission()  { return Perm.CHAT; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        FakePlayerManager manager = plugin.getFakePlayerManager();

        // ── Global toggle / status ─────────────────────────────────────────────
        if (args.length == 0) {
            // No args → toggle global fake-chat
            boolean enable = !Config.fakeChatEnabled();
            plugin.getConfig().set("fake-chat.enabled", enable);
            plugin.saveConfig();
            Config.reload();
            if (!enable && plugin.getBotChatAI() != null) {
                plugin.getBotChatAI().stopAllLoopsNow(); // kill in-flight tasks immediately
            }
            sender.sendMessage(Lang.get(enable ? "chat-enabled" : "chat-disabled"));
            Config.debug("fake-chat.enabled toggled to " + enable + " by " + sender.getName());
            return true;
        }

        String first = args[0].toLowerCase();
        switch (first) {
            case "on", "true", "yes", "1" -> {
                plugin.getConfig().set("fake-chat.enabled", true);
                plugin.saveConfig();
                Config.reload();
                sender.sendMessage(Lang.get("chat-enabled"));
                Config.debug("fake-chat.enabled set to true by " + sender.getName());
                return true;
            }
            case "off", "false", "no", "0" -> {
                plugin.getConfig().set("fake-chat.enabled", false);
                plugin.saveConfig();
                Config.reload();
                if (plugin.getBotChatAI() != null) {
                    plugin.getBotChatAI().stopAllLoopsNow(); // kill in-flight tasks immediately
                }
                sender.sendMessage(Lang.get("chat-disabled"));
                Config.debug("fake-chat.enabled set to false by " + sender.getName());
                return true;
            }
            case "status" -> {
                boolean on = Config.fakeChatEnabled();
                sender.sendMessage(Lang.get(on ? "chat-status-on" : "chat-status-off"));
                return true;
            }
            case "all" -> {
                if (manager == null) {
                    sender.sendMessage(Lang.get("chat-invalid"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Lang.get("chat-all-usage"));
                    return true;
                }
                String allSub = args[1].toLowerCase();
                switch (allSub) {
                    case "on", "true" -> {
                        manager.getActivePlayers().forEach(fp -> fp.setChatEnabled(true));
                        sender.sendMessage(Lang.get("chat-all-enabled",
                                "count", String.valueOf(manager.getActivePlayers().size())));
                    }
                    case "off", "false" -> {
                        manager.getActivePlayers().forEach(fp -> fp.setChatEnabled(false));
                        sender.sendMessage(Lang.get("chat-all-disabled",
                                "count", String.valueOf(manager.getActivePlayers().size())));
                    }
                    case "status" -> {
                        long enabled = manager.getActivePlayers().stream()
                                .filter(FakePlayer::isChatEnabled).count();
                        long disabled = manager.getActivePlayers().size() - enabled;
                        sender.sendMessage(Lang.get("chat-all-status",
                                "enabled", String.valueOf(enabled),
                                "disabled", String.valueOf(disabled)));
                    }
                    case "say" -> {
                        if (args.length < 3) {
                            sender.sendMessage(Lang.get("chat-all-say-usage"));
                            return true;
                        }
                        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                        BotChatAI chatAI = manager.getBotChatAI();
                        if (chatAI != null) {
                            manager.getActivePlayers().forEach(fp -> chatAI.forceSendMessageResolved(fp, message));
                        }
                        sender.sendMessage(Lang.get("chat-all-said",
                                "count", String.valueOf(manager.getActivePlayers().size()),
                                "message", message));
                    }
                    case "tier" -> {
                        if (args.length < 3) {
                            sender.sendMessage(Lang.get("chat-tier-usage"));
                            return true;
                        }
                        String tier = args[2].toLowerCase();
                        boolean reset = tier.equals("reset") || tier.equals("random") || tier.equals("none");
                        if (!reset && !TIERS.contains(tier)) {
                            sender.sendMessage(Lang.get("chat-bot-tier-invalid",
                                    "tier", tier, "tiers", "quiet, passive, normal, active, chatty"));
                            return true;
                        }
                        BotChatAI chatAI = manager.getBotChatAI();
                        if (chatAI != null) {
                            for (FakePlayer fp : manager.getActivePlayers()) {
                                chatAI.setActivityTier(fp.getUuid(), reset ? null : tier);
                            }
                        }
                        sender.sendMessage(reset
                                ? Lang.get("chat-all-tier-reset")
                                : Lang.get("chat-all-tier-set", "tier", tier));
                    }
                    case "mute" -> {
                        int seconds = 0;
                        if (args.length >= 3) {
                            try { seconds = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                        }
                        BotChatAI chatAI = manager.getBotChatAI();
                        int count = 0;
                        for (FakePlayer fp : manager.getActivePlayers()) {
                            if (chatAI != null) {
                                chatAI.timedMute(fp.getUuid(), seconds);
                            } else {
                                fp.setChatEnabled(false);
                            }
                            count++;
                        }
                        sender.sendMessage(Lang.get("chat-all-disabled",
                                "count", String.valueOf(count)));
                    }
                    default -> sender.sendMessage(Lang.get("chat-all-usage"));
                }
                return true;
            }
        }

        // ── Per-bot subcommands (/fpp chat <bot> ...) ─────────────────────────
        if (manager == null) {
            sender.sendMessage(Lang.get("chat-invalid"));
            return true;
        }

        // args[0] = bot name
        FakePlayer bot = manager.getByName(args[0]);
        if (bot == null) {
            sender.sendMessage(Lang.get("chat-bot-not-found", "name", args[0]));
            return true;
        }

        if (args.length < 2) {
            // No sub-arg → toggle this bot's chat
            boolean enable = !bot.isChatEnabled();
            bot.setChatEnabled(enable);
            sender.sendMessage(Lang.get(enable ? "chat-bot-enabled" : "chat-bot-disabled",
                    "name", bot.getDisplayName()));
            Config.debugChat(bot.getName() + " chat toggled to " + enable + " by " + sender.getName());
            return true;
        }

        String sub = args[1].toLowerCase();

        // /fpp chat <bot> status — explicit status check
        if (sub.equals("status")) {
            sender.sendMessage(Lang.get(
                    bot.isChatEnabled() ? "chat-bot-status-on" : "chat-bot-status-off",
                    "name", bot.getDisplayName()));
            return true;
        }


        // /fpp chat <bot> on|off
        if (sub.equals("on") || sub.equals("true") || sub.equals("1")) {
            bot.setChatEnabled(true);
            sender.sendMessage(Lang.get("chat-bot-enabled", "name", bot.getDisplayName()));
            Config.debugChat(bot.getName() + " chat re-enabled by " + sender.getName());
            return true;
        }
        if (sub.equals("off") || sub.equals("false") || sub.equals("0")) {
            bot.setChatEnabled(false);
            sender.sendMessage(Lang.get("chat-bot-disabled", "name", bot.getDisplayName()));
            Config.debugChat(bot.getName() + " chat disabled by " + sender.getName());
            return true;
        }

        // /fpp chat <bot> say <message...>
        if (sub.equals("say")) {
            if (args.length < 3) {
                sender.sendMessage(Lang.get("chat-say-usage"));
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            BotChatAI chatAI = manager.getBotChatAI();
            if (chatAI != null) {
                chatAI.forceSendMessageResolved(bot, message);
            }
            sender.sendMessage(Lang.get("chat-bot-said",
                    "name", bot.getDisplayName(), "message", message));
            return true;
        }

        // /fpp chat <bot> info — detailed status
        if (sub.equals("info")) {
            BotChatAI chatAI = manager.getBotChatAI();
            String tier = bot.getChatTier() != null ? bot.getChatTier() : "random";
            double mult = chatAI != null ? chatAI.getActivityMultiplier(bot.getUuid()) : 1.0;
            String multStr = String.format("%.2f", mult);
            sender.sendMessage(Lang.get("chat-bot-info",
                    "name", bot.getDisplayName(),
                    "enabled", bot.isChatEnabled() ? "yes" : "no",
                    "tier", tier,
                    "multiplier", multStr));
            return true;
        }

        // /fpp chat <bot> mute [seconds]
        if (sub.equals("mute")) {
            int seconds = 0;
            if (args.length >= 3) {
                try { seconds = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            }
            BotChatAI chatAI = manager.getBotChatAI();
            if (chatAI != null) {
                chatAI.timedMute(bot.getUuid(), seconds);
            } else {
                bot.setChatEnabled(false);
            }
            sender.sendMessage(Lang.get(seconds > 0 ? "chat-bot-muted-timed" : "chat-bot-muted",
                    "name", bot.getDisplayName(), "seconds", String.valueOf(seconds)));
            return true;
        }

        // /fpp chat <bot> tier <tier>
        if (sub.equals("tier")) {
            if (args.length < 3) {
                sender.sendMessage(Lang.get("chat-tier-usage"));
                return true;
            }
            String tier = args[2].toLowerCase();
            if (tier.equals("reset") || tier.equals("random") || tier.equals("none")) {
                BotChatAI chatAI = manager.getBotChatAI();
                if (chatAI != null) chatAI.setActivityTier(bot.getUuid(), null);
                sender.sendMessage(Lang.get("chat-bot-tier-reset", "name", bot.getDisplayName()));
                return true;
            }
            if (!TIERS.contains(tier)) {
                sender.sendMessage(Lang.get("chat-bot-tier-invalid",
                        "tier", tier, "tiers", "quiet, passive, normal, active, chatty"));
                return true;
            }
            BotChatAI chatAI = manager.getBotChatAI();
            if (chatAI != null) chatAI.setActivityTier(bot.getUuid(), tier);
            sender.sendMessage(Lang.get("chat-bot-tier-set",
                    "name", bot.getDisplayName(), "tier", tier));
            return true;
        }

        sender.sendMessage(Lang.get("chat-invalid"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        FakePlayerManager manager = plugin.getFakePlayerManager();

        if (args.length == 1) {
            // First arg: global verbs OR bot names
            List<String> options = new ArrayList<>(Arrays.asList("on", "off", "status", "all"));
            if (manager != null) {
                for (FakePlayer fp : manager.getActivePlayers()) {
                    options.add(fp.getName());
                }
            }
            String prefix = args[0].toLowerCase();
            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .toList();
        }

        if (args.length == 2) {
            String firstLower = args[0].toLowerCase();
            // "all" sub-commands
            if (firstLower.equals("all")) {
                String prefix = args[1].toLowerCase();
                return List.of("on", "off", "status", "say", "tier", "mute").stream()
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            }
            // Per-bot sub-commands
            if (manager != null && manager.getByName(args[0]) != null) {
                String prefix = args[1].toLowerCase();
                return List.of("on", "off", "status", "info", "say", "tier", "mute").stream()
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            }
        }

        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            if (sub.equals("tier")) {
                String prefix = args[2].toLowerCase();
                List<String> tierOpts = new ArrayList<>(TIERS);
                tierOpts.add("reset");
                return tierOpts.stream()
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            }
            // Suggest common durations for mute
            if (sub.equals("mute")) {
                String prefix = args[2].toLowerCase();
                return List.of("30", "60", "120", "300", "600").stream()
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            }
        }

        return List.of();
    }
}

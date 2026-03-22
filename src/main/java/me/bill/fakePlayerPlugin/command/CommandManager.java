package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central dispatcher for all {@code /fpp} sub-commands.
 * Register a new command once with {@link #register(FppCommand)} — the dynamic
 * help menu and tab-complete will pick it up automatically.
 */
public class CommandManager implements CommandExecutor, TabCompleter {

    private static final TextColor ACCENT    = TextColor.fromHexString("#0079FF");
    private static final TextColor DARK_GRAY = NamedTextColor.DARK_GRAY;
    private static final TextColor GRAY      = NamedTextColor.GRAY;
    private static final TextColor WHITE     = NamedTextColor.WHITE;

    private final List<FppCommand>       commands = new ArrayList<>();
    private final Map<String, FppCommand> byName  = new LinkedHashMap<>();
    private final FakePlayerPlugin        plugin;

    public CommandManager(FakePlayerPlugin plugin) {
        this.plugin = plugin;
        register(new HelpCommand(this));
    }

    public void register(FppCommand command) {
        if (!byName.containsKey(command.getName().toLowerCase())) {
            commands.add(command);
            byName.put(command.getName().toLowerCase(), command);
            Config.debug("Registered command: fpp " + command.getName());
        }
    }

    public List<FppCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    // ── CommandExecutor ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String @NotNull [] args) {

        if (args.length == 0) {
            sendPluginInfo(sender);
            return true;
        }

        String subName = args[0].toLowerCase();
        FppCommand sub = byName.get(subName);

        if (sub == null) {
            Config.debug(sender.getName() + " used unknown sub-command: " + subName);
            sender.sendMessage(Lang.get("unknown-command", label));
            return true;
        }

        // Use canUse() — handles both single-perm and dual-tier commands
        if (!sub.canUse(sender)) {
            Config.debug(sender.getName() + " was denied: fpp " + subName);
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        Config.debug(sender.getName() + " executed: fpp " + String.join(" ", args));
        sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    // ── TabCompleter ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      @NotNull String @NotNull [] args) {

        if (args.length == 1) {
            // Only show sub-commands the sender can actually use
            return commands.stream()
                    .filter(cmd -> cmd.canUse(sender))
                    .map(FppCommand::getName)
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length >= 2) {
            FppCommand sub = byName.get(args[0].toLowerCase());
            // Only forward if sender is allowed to use this sub-command
            if (sub != null && sub.canUse(sender)) {
                return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }

        return Collections.emptyList();
    }

    // ── Plugin info screen ────────────────────────────────────────────────────

    /**
     * Shown when the player types bare {@code /fpp} — a compact, themed info panel.
     */
    private void sendPluginInfo(CommandSender sender) {
        String version = plugin.getPluginMeta().getVersion();
        List<String> authors = plugin.getPluginMeta().getAuthors();
        String author = authors.isEmpty() ? "Unknown" : String.join(", ", authors);

        // Use the shared divider and header from lang
        Component divider = TextUtil.colorize(Lang.raw("divider"));
        Component header  = TextUtil.colorize(Lang.raw("info-screen-header"));

        sender.sendMessage(divider);
        sender.sendMessage(header);
        sender.sendMessage(Component.empty());

        // Version / Author rows
        sender.sendMessage(row("ᴠᴇʀꜱɪᴏɴ", version));
        sender.sendMessage(row("ᴀᴜᴛʜᴏʀ",  author));

        // Active bots count (live)
        me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) {
            sender.sendMessage(row("ᴀᴄᴛɪᴠᴇ ʙᴏᴛꜱ", String.valueOf(fpm.getCount())));
        }

        // Download links — all 4 platforms as clickable text
        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(DARK_GRAY))
                .append(Component.text("ᴅᴏᴡɴʟᴏᴀᴅ ").color(GRAY))
                .append(Component.text("→ ").color(DARK_GRAY))
                .append(Component.text("Modrinth")
                        .color(ACCENT)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/fake-player-plugin-(fpp)"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open Modrinth").color(GRAY))))
                .append(Component.text(", ").color(GRAY))
                .append(Component.text("SpigotMC")
                        .color(ACCENT)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://www.spigotmc.org/resources/fake-player-plugin-fpp.133572/"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open SpigotMC").color(GRAY))))
                .append(Component.text(", ").color(GRAY))
                .append(Component.text("PaperMC")
                        .color(ACCENT)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://hangar.papermc.io/Pepe-tf/FakePlayerPlugin"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open PaperMC Hangar").color(GRAY))))
                .append(Component.text(", ").color(GRAY))
                .append(Component.text("BuiltByBit")
                        .color(ACCENT)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://builtbybit.com/resources/fake-player-plugin.98704/"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open BuiltByBit").color(GRAY)))));

        sender.sendMessage(Component.empty());

        // Help hint — clickable shortcut
        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(DARK_GRAY))
                .append(Component.text("ᴛʏᴘᴇ ").color(GRAY))
                .append(Component.text("/fpp help").color(ACCENT)
                        .clickEvent(ClickEvent.runCommand("/fpp help"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Click to open the help menu").color(GRAY))))
                .append(Component.text(" ꜰᴏʀ ᴀ ʟɪꜱᴛ ᴏꜰ ᴄᴏᴍᴍᴀɴᴅꜱ.").color(GRAY)));

        sender.sendMessage(divider);
    }

    private Component row(String label, String value) {
        return Component.empty()
                .append(Component.text("  ").color(DARK_GRAY))
                .append(Component.text(label).color(GRAY))
                .append(Component.text(" → ").color(DARK_GRAY))
                .append(Component.text(value).color(WHITE));
    }
}

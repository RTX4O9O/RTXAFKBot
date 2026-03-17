package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /fpp list [page]} — paginated active-bot list with freeze indicator,
 * uptime, location, and spawner info. 10 bots per page
 */
@SuppressWarnings("unused")
public class ListCommand implements FppCommand {

    private static final int PAGE_SIZE = 10;

    private static final TextColor ACCENT  = TextColor.fromHexString("#0079FF");
    private static final TextColor MUTED   = NamedTextColor.DARK_GRAY;
    private static final TextColor LABEL   = NamedTextColor.GRAY;
    private static final TextColor VALUE   = NamedTextColor.WHITE;
    private static final TextColor FROZEN  = TextColor.fromHexString("#66CCFF"); // icy blue

    private final FakePlayerManager manager;

    public ListCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override public String getName()        { return "list"; }
    @Override public String getUsage()       { return "[page]"; }
    @Override public String getDescription() { return "Lists all currently active bots."; }
    @Override public String getPermission()  { return Perm.LIST; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        List<FakePlayer> bots = new ArrayList<>(manager.getActivePlayers());

        // ── Parse page arg ──────────────────────────────────────────────────
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.get("invalid-number"));
                return true;
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) bots.size() / PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));

        // ── Header ──────────────────────────────────────────────────────────
        sender.sendMessage(TextUtil.colorize(
                "<dark_gray><st>━━━━━━━━</st> <#0079FF>ᴀᴄᴛɪᴠᴇ ʙᴏᴛꜱ</#0079FF> "
                + "<dark_gray>(<white>" + bots.size() + "<dark_gray>) <st>━━━━━━━━</st>"));

        if (bots.isEmpty()) {
            sender.sendMessage(Lang.get("list-none"));
            sender.sendMessage(TextUtil.colorize(
                    "<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>"));
            return true;
        }

        // ── Page slice ───────────────────────────────────────────────────────
        int start = (page - 1) * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, bots.size());
        List<FakePlayer> pageSlice = bots.subList(start, end);

        for (FakePlayer fp : pageSlice) {
            String uptime  = formatUptime(fp.getSpawnTime());
            String locStr  = formatLocation(fp);
            String spawner = fp.getSpawnedBy();

            // ❄ badge for frozen bots
            boolean isFrozen = fp.isFrozen();
            String  frozenTag = isFrozen ? " <#66CCFF>❄</#66CCFF>" : "";

            // Name + uptime on line 1
            Component line1 = Component.empty()
                    .append(Component.text("  "))
                    .append(TextUtil.colorize("<#0079FF>" + fp.getDisplayName() + "</#0079FF>"
                            + frozenTag))
                    .append(Component.text("  ").color(MUTED))
                    .append(Component.text("⏱ ").color(LABEL))
                    .append(Component.text(uptime).color(VALUE));

            // Location + spawner on line 2
            Component line2 = Component.empty()
                    .append(Component.text("    "))
                    .append(Component.text("📍 ").color(LABEL))
                    .append(Component.text(locStr).color(VALUE))
                    .append(Component.text("  by ").color(MUTED))
                    .append(Component.text(spawner).color(LABEL));

            sender.sendMessage(line1);
            sender.sendMessage(line2);
        }

        // ── Pagination nav ───────────────────────────────────────────────────
        if (totalPages > 1) {
            Component prev = page > 1
                    ? Component.text("  ◀ ᴘʀᴇᴠ").color(ACCENT)
                            .clickEvent(ClickEvent.runCommand("/fpp list " + (page - 1)))
                            .hoverEvent(HoverEvent.showText(Component.text("Page " + (page - 1))))
                    : Component.text("  ◀").color(MUTED);

            Component pageNum = Component.text("  " + page + "/" + totalPages + "  ").color(LABEL);

            Component next = page < totalPages
                    ? Component.text("ɴᴇxᴛ ▶").color(ACCENT)
                            .clickEvent(ClickEvent.runCommand("/fpp list " + (page + 1)))
                            .hoverEvent(HoverEvent.showText(Component.text("Page " + (page + 1))))
                    : Component.text("ɴᴇxᴛ ▶").color(MUTED);

            sender.sendMessage(prev.append(pageNum).append(next));
        }

        // ── Footer ──────────────────────────────────────────────────────────
        sender.sendMessage(TextUtil.colorize(
                "<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>"));
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatUptime(Instant spawnTime) {
        if (spawnTime == null) return "?";
        long secs = Duration.between(spawnTime, Instant.now()).getSeconds();
        if (secs < 60)   return secs + "s";
        if (secs < 3600) return (secs / 60) + "m " + (secs % 60) + "s";
        long h = secs / 3600, m = (secs % 3600) / 60;
        return h + "h " + m + "m";
    }

    private static String formatLocation(FakePlayer fp) {
        Entity body = fp.getPhysicsEntity();
        if (body != null && body.isValid()) {
            var loc = body.getLocation();
            return (loc.getWorld() != null ? loc.getWorld().getName() : "?")
                    + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        }
        if (fp.getSpawnLocation() != null) {
            var loc = fp.getSpawnLocation();
            return (loc.getWorld() != null ? loc.getWorld().getName() : "?")
                    + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + " (spawn)";
        }
        return "unknown";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Suggest page numbers
        if (args.length == 1) {
            int bots  = manager.getCount();
            int pages = Math.max(1, (int) Math.ceil((double) bots / PAGE_SIZE));
            List<String> result = new ArrayList<>();
            for (int p = 1; p <= pages; p++) result.add(String.valueOf(p));
            String prefix = args[0];
            return result.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }
}

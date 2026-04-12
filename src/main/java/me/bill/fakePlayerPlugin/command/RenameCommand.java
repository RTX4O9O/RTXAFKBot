package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotRenameHelper;
import me.bill.fakePlayerPlugin.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /fpp rename <oldname> <newname>}
 *
 * <p>Renames an active bot to a new Minecraft username, preserving all bot data
 * (inventory, XP, LP group, chat settings, frozen state, and original spawner).
 *
 * <p>Because a bot's MC username is baked into the NMS {@code GameProfile} at spawn
 * time and is immutable, renaming must despawn the entity and respawn it under the
 * new name. This guarantees that both the nametag above the head and death messages
 * show the new name — not the old one.
 *
 * <h3>Permission tiers</h3>
 * <ul>
 *   <li>{@code fpp.rename}     — rename <em>any</em> active bot (admin, default: op)</li>
 *   <li>{@code fpp.rename.own} — rename <em>only bots the sender personally spawned</em>
 *                                (user opt-in, default: false)</li>
 * </ul>
 *
 * <p>Console always has full access. {@code fpp.op} grants both nodes.
 */
public class RenameCommand implements FppCommand {

    private static final String ACCENT = "<#0079FF>";
    private static final String CLOSE  = "</#0079FF>";
    private static final String GRAY   = "<gray>";
    private static final String RED    = "<red>";

    private final FakePlayerManager manager;
    private final BotRenameHelper renameHelper;

    public RenameCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.manager = manager;
        this.renameHelper = new BotRenameHelper(plugin, manager);
    }

    // ── FppCommand ────────────────────────────────────────────────────────────

    @Override public String getName()        { return "rename"; }
    @Override public String getUsage()       { return "<oldname> <newname>"; }
    @Override public String getDescription() { return "Rename an active bot (preserves all data)."; }
    @Override public String getPermission()  { return Perm.RENAME; }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.hasAny(sender, Perm.RENAME, Perm.RENAME_OWN);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (args.length < 2) {
            msg(sender, GRAY + "Usage: " + ACCENT + "/fpp rename <oldname> <newname>" + CLOSE + GRAY + ".");
            return true;
        }

        String oldName = args[0];
        String newName = args[1];

        // ── 1. Locate old bot ─────────────────────────────────────────────────
        FakePlayer fp = manager.getByName(oldName);
        if (fp == null) {
            msg(sender, RED + "No active bot named " + ACCENT + oldName + CLOSE + RED + ".");
            return true;
        }

        // ── 2. Ownership check (fpp.rename.own only) ──────────────────────────
        if (!Perm.has(sender, Perm.RENAME) && sender instanceof Player player) {
            if (!fp.getSpawnedByUuid().equals(player.getUniqueId())) {
                msg(sender, RED + "You can only rename bots you personally spawned."
                        + " Ask an admin for " + ACCENT + "fpp.rename" + CLOSE
                        + RED + " to rename any bot.");
                return true;
            }
        }

        renameHelper.rename(sender, fp, newName);
        return true;
    }

    // ── Tab complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String lower          = args[0].toLowerCase();
            boolean canRenameAny  = Perm.has(sender, Perm.RENAME);

            return manager.getActivePlayers().stream()
                    .filter(fp -> {
                        if (canRenameAny) return true;
                        // fpp.rename.own: only show bots this sender spawned
                        if (!(sender instanceof Player p)) return false;
                        return fp.getSpawnedByUuid().equals(p.getUniqueId());
                    })
                    .map(FakePlayer::getName)
                    .filter(n -> n.toLowerCase().startsWith(lower))
                    .toList();
        }

        // arg[1] is the new name — free-form, no suggestions needed
        return List.of();
    }

    // ── UI helper ─────────────────────────────────────────────────────────────

    private void msg(CommandSender sender, String mm) {
        sender.sendMessage(TextUtil.colorize(mm));
    }
}


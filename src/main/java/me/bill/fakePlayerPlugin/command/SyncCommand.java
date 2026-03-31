package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.sync.ConfigSyncManager;
import me.bill.fakePlayerPlugin.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /fpp sync <push|pull|status> [file]} — Manages config synchronization
 * across proxy networks.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code /fpp sync push [file]}     — Push config(s) to network</li>
 *   <li>{@code /fpp sync pull [file]}     — Pull config(s) from network</li>
 *   <li>{@code /fpp sync status [file]}   — Show sync status</li>
 *   <li>{@code /fpp sync check [file]}    — Check for local changes</li>
 * </ul>
 */
public final class SyncCommand implements FppCommand {

    private static final TextColor LABEL  = NamedTextColor.GRAY;
    private static final TextColor VALUE  = NamedTextColor.WHITE;
    private static final TextColor OK     = NamedTextColor.GREEN;
    private static final TextColor WARN   = NamedTextColor.YELLOW;

    private final FakePlayerPlugin plugin;

    public SyncCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "sync";
    }

    @Override
    public String getUsage() {
        return "<push|pull|status|check> [file]";
    }

    @Override
    public String getDescription() {
        return "Sync configs across network";
    }

    @Override
    public String getPermission() {
        return Perm.ALL;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.ALL);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ConfigSyncManager syncManager = plugin.getConfigSyncManager();

        if (syncManager == null || !Config.isNetworkMode()) {
            sender.sendMessage(Lang.get("sync-not-available"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Lang.get("sync-usage"));
            return false;
        }

        String action = args[0].toLowerCase();
        String fileName = args.length > 1 ? args[1] : null;

        switch (action) {
            case "push" -> executePush(sender, fileName);
            case "pull" -> executePull(sender, fileName);
            case "status" -> executeStatus(sender, fileName);
            case "check" -> executeCheck(sender, fileName);
            default -> {
                sender.sendMessage(Lang.get("sync-usage"));
                return false;
            }
        }

        return true;
    }

    private void executePush(CommandSender sender, String fileName) {
        String pushedBy = sender.getName();

        if (fileName != null) {
            // Push single file
            boolean success = plugin.getConfigSyncManager().push(fileName, pushedBy);
            if (success) {
                sender.sendMessage(Lang.get("sync-push-success", "file", fileName));
            } else {
                sender.sendMessage(Lang.get("sync-push-failed", "file", fileName));
            }
        } else {
            // Push all files
            int count = plugin.getConfigSyncManager().pushAll(pushedBy);
            sender.sendMessage(Lang.get("sync-push-all-success", "count", String.valueOf(count)));
        }
    }

    private void executePull(CommandSender sender, String fileName) {
        if (fileName != null) {
            // Pull single file
            boolean success = plugin.getConfigSyncManager().pull(fileName, false);
            if (success) {
                sender.sendMessage(Lang.get("sync-pull-success", "file", fileName));
                sender.sendMessage(Lang.get("sync-pull-reload-hint"));
            } else {
                sender.sendMessage(Lang.get("sync-pull-failed", "file", fileName));
            }
        } else {
            // Pull all files
            int count = plugin.getConfigSyncManager().pullAll(false);
            if (count > 0) {
                sender.sendMessage(Lang.get("sync-pull-all-success", "count", String.valueOf(count)));
                sender.sendMessage(Lang.get("sync-pull-reload-hint"));
            } else {
                sender.sendMessage(Lang.get("sync-pull-no-updates"));
            }
        }
    }

    private void executeStatus(CommandSender sender, String fileName) {
        if (fileName != null) {
            // Status for single file
            ConfigSyncManager.SyncStatus status = plugin.getConfigSyncManager().getStatus(fileName);
            if (status == null) {
                sender.sendMessage(Lang.get("sync-status-no-network", "file", fileName));
                return;
            }

            sender.sendMessage(divider());
            sender.sendMessage(header("ꜱʏɴᴄ ꜱᴛᴀᴜꜱ — " + fileName));
            row(sender, "ʜᴀꜱʜ",        status.shortHash());
            row(sender, "ᴘᴜꜱʜᴇᴅ ʙʏ",  status.serverId() + " (" + status.pushedBy() + ")");
            row(sender, "ᴘᴜꜱʜᴇᴅ ᴀᴛ",  status.formattedTime());

            boolean hasChanges = plugin.getConfigSyncManager().hasLocalChanges(fileName);
            if (hasChanges) {
                sender.sendMessage(Component.text("  ⚠ Local file has uncommitted changes", WARN));
            } else {
                sender.sendMessage(Component.text("  ✓ Local file matches network", OK));
            }
            sender.sendMessage(divider());

        } else {
            // Status for all files
            List<ConfigSyncManager.SyncStatus> allStatus = plugin.getConfigSyncManager().getAllStatus();

            sender.sendMessage(divider());
            sender.sendMessage(header("ᴄᴏɴꜰɪɡ ꜱʏɴᴄ ꜱᴛᴀᴜꜱ"));
            row(sender, "ᴍᴏᴅᴇ", Config.configSyncMode());
            sender.sendMessage(Component.empty());

            if (allStatus.isEmpty()) {
                sender.sendMessage(Component.text("  No configs found in network.", LABEL));
            } else {
                for (ConfigSyncManager.SyncStatus status : allStatus) {
                    boolean hasChanges = plugin.getConfigSyncManager().hasLocalChanges(status.fileName());
                    TextColor fileColor = hasChanges ? WARN : OK;
                    String changeIndicator = hasChanges ? " ⚠" : " ✓";

                    sender.sendMessage(Component.empty()
                            .append(Component.text("  ", LABEL))
                            .append(Component.text(status.fileName(), fileColor))
                            .append(Component.text(changeIndicator, fileColor))
                            .append(Component.text("  ", LABEL))
                            .append(Component.text(status.shortHash(), LABEL))
                            .append(Component.text("  from ", LABEL))
                            .append(Component.text(status.serverId(), VALUE)));
                }
            }
            sender.sendMessage(divider());
        }
    }

    private void executeCheck(CommandSender sender, String fileName) {
        if (fileName != null) {
            boolean hasChanges = plugin.getConfigSyncManager().hasLocalChanges(fileName);
            if (hasChanges) {
                sender.sendMessage(Lang.get("sync-check-has-changes", "file", fileName));
            } else {
                sender.sendMessage(Lang.get("sync-check-no-changes", "file", fileName));
            }
        } else {
            // Check all files
            List<String> changedFiles = new ArrayList<>();
            for (String file : List.of("config.yml", "bot-names.yml", "bot-messages.yml", "language/en.yml")) {
                if (plugin.getConfigSyncManager().hasLocalChanges(file)) {
                    changedFiles.add(file);
                }
            }

            if (changedFiles.isEmpty()) {
                sender.sendMessage(Lang.get("sync-check-all-clean"));
            } else {
                sender.sendMessage(Lang.get("sync-check-summary", "count", String.valueOf(changedFiles.size())));
                for (String file : changedFiles) {
                    sender.sendMessage(Component.text("  ⚠ " + file, WARN));
                }
            }
        }
    }

    private Component header(String title) {
        return TextUtil.colorize("<dark_gray><st>━━━</st> <#0079FF>" + title + "</#0079FF> <dark_gray><st>━━━</st>");
    }

    private Component divider() {
        return TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>");
    }

    private void row(CommandSender s, String label, String value) {
        s.sendMessage(Component.empty()
                .append(Component.text("  " + label + ": ", LABEL))
                .append(Component.text(value, VALUE)));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!Perm.has(sender, Perm.ALL)) return List.of();

        if (args.length <= 1) {
            String current = args.length > 0 ? args[0].toLowerCase() : "";
            return java.util.stream.Stream.of("push", "pull", "status", "check")
                    .filter(s -> s.startsWith(current))
                    .toList();
        }

        if (args.length == 2) {
            String current = args[1].toLowerCase();
            return java.util.stream.Stream.of("config.yml", "bot-names.yml", "bot-messages.yml", "language/en.yml")
                    .filter(s -> s.startsWith(current))
                    .toList();
        }

        return List.of();
    }
}

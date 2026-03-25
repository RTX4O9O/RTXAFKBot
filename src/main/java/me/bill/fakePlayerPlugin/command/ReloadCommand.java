package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotMessageConfig;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.SkinFetcher;
import me.bill.fakePlayerPlugin.fakeplayer.SkinRepository;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotTabTeam;
import me.bill.fakePlayerPlugin.util.ConfigValidator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.LuckPermsHelper;
import me.bill.fakePlayerPlugin.util.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

/**
 * Reloads all plugin configuration and runtime state without requiring a server restart.
 *
 * <h3>Reload sequence</h3>
 * <ol>
 *   <li>Config YAML files (config.yml, bot-names.yml, bot-messages.yml)</li>
 *   <li>Language file (language/en.yml)</li>
 *   <li>Skin cache and skin repository (folder + pool)</li>
 *   <li>Tab-list manager (header/footer/bot visibility toggle)</li>
 *   <li>Active bot state (body spawn, swap cancellation, tab-list sync)</li>
 *   <li>LuckPerms cache invalidation + live prefix/weight reapply</li>
 *   <li>Scoreboard team rebuild (~fpp)</li>
 *   <li>Config validation (warnings for misconfigurations)</li>
 *   <li>Update checker (fresh async version check)</li>
 * </ol>
 *
 * <p>All changes take effect immediately — no server restart required.
 */
public class ReloadCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor GRAY   = NamedTextColor.GRAY;
    private static final TextColor GREEN  = NamedTextColor.GREEN;
    private static final TextColor YELLOW = NamedTextColor.YELLOW;
    private static final TextColor WHITE  = NamedTextColor.WHITE;

    private final FakePlayerPlugin plugin;

    public ReloadCommand(FakePlayerPlugin plugin) { this.plugin = plugin; }

    @Override public String getName()        { return "reload"; }
    @Override public String getUsage()       { return ""; }
    @Override public String getDescription() { return "Reloads the plugin configuration."; }
    @Override public String getPermission()  { return Perm.RELOAD; }
    @Override public boolean canUse(CommandSender sender) { return Perm.hasOrOp(sender, Perm.RELOAD); }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        long start = System.currentTimeMillis();

        sender.sendMessage(Component.text("┌ Reloading FakePlayerPlugin v1.4.27…").color(ACCENT));

        // ── 1. Core config files ──────────────────────────────────────────────
        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BotMessageConfig.reload();
        sendStep(sender, "Config, language, names & messages reloaded");

        // ── 2. Skin system ────────────────────────────────────────────────────
        if (Config.skinClearCacheOnReload()) SkinFetcher.clearCache();
        SkinRepository.get().reload();
        int folderSkins = SkinRepository.get().getFolderSkinCount();
        int poolSkins   = SkinRepository.get().getPoolSkinCount();
        sendStep(sender, "Skin system  —  mode: " + Config.skinMode()
                + "  |  " + folderSkins + " folder  +  " + poolSkins + " pool");

        // ── 3. Tab-list manager ───────────────────────────────────────────────
        if (plugin.getTabListManager() != null) plugin.getTabListManager().reload();
        sendStep(sender, "Tab-list  —  bots " + (Config.tabListEnabled() ? "visible" : "hidden"));

        // ── 4. Active bot runtime state ───────────────────────────────────────
        FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) {
            if (!Config.swapEnabled()) {
                fpm.cancelAllSwap();
                sendStep(sender, "Swap disabled — all pending timers cancelled");
            }
            fpm.applyBodyConfig();
            fpm.applyTabListConfig();
            int active = fpm.getCount();
            if (active > 0) sendStep(sender, active + " active bot(s) runtime state updated");
        }

        // ── 5. LuckPerms cache + live prefix reapply ──────────────────────────
        LuckPermsHelper.invalidateCache();
        int lpUpdated = 0;
        if (fpm != null && fpm.getCount() > 0) lpUpdated = fpm.updateAllBotPrefixes();
        sendStep(sender, "LuckPerms cache cleared"
                + (lpUpdated > 0 ? "  —  " + lpUpdated + " bot prefix(es) refreshed" : ""));

        // ── 6. Scoreboard team rebuild ────────────────────────────────────────
        BotTabTeam btt = plugin.getBotTabTeam();
        if (btt != null && fpm != null) {
            btt.rebuild(fpm.getActivePlayers());
            sendStep(sender, "~fpp scoreboard team rebuilt  (" + fpm.getCount() + " bot(s))");
        }

        // ── 7. Config validation ──────────────────────────────────────────────
        int issues = ConfigValidator.validate();
        if (issues > 0) {
            sender.sendMessage(Component.text("│  ⚠ " + issues
                    + " config issue(s) detected — check console").color(YELLOW));
        }

        // ── 8. Update checker (async, non-blocking) ───────────────────────────
        UpdateChecker.invalidateCache();
        UpdateChecker.check(plugin);

        long ms = System.currentTimeMillis() - start;
        sender.sendMessage(
            Component.text("└ ").color(ACCENT)
            .append(Component.text("✓ Done").color(GREEN))
            .append(Component.text("  in " + ms + "ms").color(GRAY))
        );
        FppLogger.success("Plugin reloaded by " + sender.getName() + " in " + ms + "ms.");
        return true;
    }

    /** Sends a compact step line: │  ✓ <message> */
    private void sendStep(CommandSender sender, String message) {
        sender.sendMessage(
            Component.text("│  ").color(ACCENT)
            .append(Component.text("✓ ").color(GREEN))
            .append(Component.text(message).color(GRAY))
        );
    }
}

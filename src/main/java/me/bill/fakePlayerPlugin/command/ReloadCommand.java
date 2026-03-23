package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotMessageConfig;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.SkinFetcher;
import me.bill.fakePlayerPlugin.fakeplayer.SkinRepository;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
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
 *   <li>LuckPerms cache invalidation (prefix/weight changes)</li>
 *   <li>Config validation (warnings for misconfigurations)</li>
 *   <li>Update checker (fresh version check)</li>
 * </ol>
 *
 * <p>All changes take effect immediately — no server restart required.
 */
public class ReloadCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor GRAY   = NamedTextColor.GRAY;
    private static final TextColor GREEN  = NamedTextColor.GREEN;

    private final FakePlayerPlugin plugin;

    public ReloadCommand(FakePlayerPlugin plugin) { this.plugin = plugin; }

    @Override public String getName()        { return "reload"; }
    @Override public String getUsage()       { return ""; }
    @Override public String getDescription() { return "Reloads the plugin configuration."; }
    @Override public String getPermission()  { return Perm.RELOAD; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        long start = System.currentTimeMillis();

        // Show progress to the user with step-by-step feedback
        sender.sendMessage(Component.text("Reloading FakePlayerPlugin...").color(ACCENT));

        // ── 1. Core config files ──────────────────────────────────────────────
        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BotMessageConfig.reload();
        sendStep(sender, "Config & language files reloaded");

        // ── 2. Skin system ────────────────────────────────────────────────────
        if (Config.skinClearCacheOnReload()) {
            SkinFetcher.clearCache();
        }
        SkinRepository.get().reload();
        sendStep(sender, "Skin system updated (" + Config.skinMode() + " mode, "
                + SkinRepository.get().getFolderSkinCount() + " folder + "
                + SkinRepository.get().getPoolSkinCount() + " pool skins)");

        // ── 3. Tab-list manager ───────────────────────────────────────────────
        if (plugin.getTabListManager() != null) {
            plugin.getTabListManager().reload();
        }
        sendStep(sender, "Tab-list config applied (bots " 
                + (Config.tabListEnabled() ? "visible" : "hidden") + ")");

        // ── 4. Active bot runtime state ───────────────────────────────────────
        me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) {
            int activeCount = fpm.getCount();
            
            if (!Config.swapEnabled()) {
                fpm.cancelAllSwap();
                sendStep(sender, "Swap disabled — cancelled all pending timers");
            }
            
            fpm.applyBodyConfig();
            fpm.applyTabListConfig();
            
            if (activeCount > 0) {
                sendStep(sender, activeCount + " active bot(s) updated");
            }
        }

        // ── 5. LuckPerms cache invalidation + live prefix reapply ─────────────
        LuckPermsHelper.invalidateCache();
        int lpUpdated = 0;
        if (fpm != null && fpm.getCount() > 0) {
            lpUpdated = fpm.updateAllBotPrefixes();
        }
        sendStep(sender, "LuckPerms cache cleared"
                + (lpUpdated > 0 ? ", " + lpUpdated + " bot prefix(es) refreshed" : ""));

        // ── 6. Config validation ──────────────────────────────────────────────
        int issues = ConfigValidator.validate();
        if (issues > 0) {
            sender.sendMessage(Component.text("⚠ " + issues + " config issue(s) detected — check console")
                    .color(NamedTextColor.YELLOW));
        }

        // ── 7. Update checker (async, non-blocking) ───────────────────────────
        UpdateChecker.invalidateCache();
        UpdateChecker.check(plugin);

        long ms = System.currentTimeMillis() - start;
        
        // Final success message
        Component successMsg = Component.text("✓ Reload complete in " + ms + "ms")
                .color(GREEN);
        sender.sendMessage(successMsg);
        FppLogger.success("Plugin reloaded by " + sender.getName() + " in " + ms + "ms.");
        
        return true;
    }

    /** Sends a compact step message with a checkmark prefix. */
    private void sendStep(CommandSender sender, String message) {
        sender.sendMessage(Component.text("  ✓ ").color(GREEN)
                .append(Component.text(message).color(GRAY)));
    }
}

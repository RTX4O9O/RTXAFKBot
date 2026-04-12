package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotMessageConfig;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.config.SecretsConfig;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.SkinRepository;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotTabTeam;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.ConfigValidator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reloads all plugin configuration and runtime state without requiring a server restart.
 *
 * <h3>Usage</h3>
 * <pre>
 *   /fpp reload            — full reload (all subsystems)
 *   /fpp reload config     — config.yml, bot-names, bot-messages, badword filter
 *   /fpp reload lang       — language file only
 *   /fpp reload chat       — fake-chat config + restart chat loops
 *   /fpp reload ai         — secrets.yml + AI providers + personalities
 *   /fpp reload skins      — skin repository (folder, pool, cache)
 *   /fpp reload secrets    — secrets.yml only (alias for 'ai')
 *   /fpp reload swap       — swap + peak-hours config
 * </pre>
 *
 * <h3>Full reload sequence</h3>
 * <ol>
 *   <li>Config YAML files (config.yml, bot-names.yml, bot-messages.yml, badword filter, personalities)</li>
 *   <li>Language file (language/en.yml)</li>
 *   <li>Chat loops restart</li>
 *   <li>Secrets + AI providers re-init</li>
 *   <li>Skin repository rescan</li>
 *   <li>Tab-list manager (header/footer/bot visibility toggle)</li>
 *   <li>Active bot state (body config, tab-list sync)</li>
 *   <li>Swap + peak-hours config</li>
 *   <li>Scoreboard team rebuild (~fpp)</li>
 *   <li>Config validation</li>
 *   <li>Update checker (async)</li>
 * </ol>
 *
 * <p>All changes take effect immediately — no server restart required.
 */
public class ReloadCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor GRAY   = NamedTextColor.GRAY;
    private static final TextColor GREEN  = NamedTextColor.GREEN;
    private static final TextColor YELLOW = NamedTextColor.YELLOW;
    private static final TextColor RED    = NamedTextColor.RED;

    /** Ordered list of sub-command targets surfaced in tab-complete. */
    private static final List<String> TARGETS = List.of(
            "all", "config", "lang", "chat", "ai", "skins", "secrets", "swap");

    private final FakePlayerPlugin plugin;

    public ReloadCommand(FakePlayerPlugin plugin) { this.plugin = plugin; }

    @Override public String getName()        { return "reload"; }
    @Override public String getUsage()       { return "[all|config|lang|chat|ai|skins|secrets|swap]"; }
    @Override public String getDescription() { return "Reloads the plugin configuration (optionally target a subsystem)."; }
    @Override public String getPermission()  { return Perm.RELOAD; }
    @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.RELOAD); }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String target = args.length > 0 ? args[0].toLowerCase() : "all";

        long start = System.currentTimeMillis();
        String version = plugin.getPluginMeta().getVersion();

        String label = target.equals("all") ? "full reload" : "reload:" + target;
        sender.sendMessage(Component.text("┌ FakePlayerPlugin v" + version + " — " + label + "…").color(ACCENT));

        switch (target) {
            case "config"  -> reloadConfig(sender);
            case "lang"    -> reloadLang(sender);
            case "chat"    -> reloadChat(sender);
            case "ai", "secrets" -> reloadAi(sender);
            case "skins"   -> reloadSkins(sender);
            case "swap"    -> reloadSwap(sender);
            case "all"     -> reloadAll(sender);
            default -> {
                sender.sendMessage(Component.text("│  ").color(ACCENT)
                        .append(Component.text("✗ Unknown target '").color(RED))
                        .append(Component.text(target).color(YELLOW))
                        .append(Component.text("'.  Valid: " + String.join(", ", TARGETS)).color(RED)));
            }
        }

        long ms = System.currentTimeMillis() - start;
        sender.sendMessage(
            Component.text("└ ").color(ACCENT)
            .append(Component.text("✓ Done").color(GREEN))
            .append(Component.text("  in " + ms + "ms").color(GRAY))
        );
        FppLogger.success("Plugin reloaded [" + label + "] by " + sender.getName() + " in " + ms + "ms.");
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return TARGETS.stream()
                    .filter(t -> t.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // ── Targeted reload blocks ────────────────────────────────────────────────

    /** Step 1 — core config files, names, messages, badword filter, personalities, name pool. */
    private void reloadConfig(CommandSender sender) {
        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BotMessageConfig.reload();
        BadwordFilter.reload(plugin);

        if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
            sender.sendMessage(Component.text(
                "│  ⚠ Badword filter is ON but no sources are active — enable " +
                "'badword-filter.use-global-list' or add words to config.yml / bad-words.yml!"
            ).color(YELLOW));
        }

        FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) fpm.refreshCleanNamePool();

        if (plugin.getPersonalityRepository() != null) plugin.getPersonalityRepository().reload();

        int personalities = plugin.getPersonalityRepository() != null
                ? plugin.getPersonalityRepository().size() : 0;
        sendStep(sender, "Config, lang, names ("
                + BotNameConfig.getNames().size() + "), messages ("
                + BotMessageConfig.getMessages().size() + "), badword filter, personalities ("
                + personalities + ")");

        // AUTO_PUSH after config reload
        if (Config.configSyncMode().equalsIgnoreCase("AUTO_PUSH")
                && plugin.getConfigSyncManager() != null) {
            var csm = plugin.getConfigSyncManager();
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                int pushed = csm.pushAll(sender.getName());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    sendStep(sender, "AUTO_PUSH: " + pushed + " config file(s) pushed to network"));
            });
        }
    }

    /** Step 2 — language file only. */
    private void reloadLang(CommandSender sender) {
        Lang.reload();
        sendStep(sender, "Language file reloaded");
    }

    /** Step 3 — fake-chat config + restart chat loops. */
    private void reloadChat(CommandSender sender) {
        // Ensure config is current before examining chat keys
        Config.reload();
        BotMessageConfig.reload();
        if (plugin.getBotChatAI() != null && Config.fakeChatEnabled()) {
            plugin.getBotChatAI().restartLoops();
            sendStep(sender, "Bot-chat loops restarted — interval "
                    + Config.fakeChatIntervalMin() + "–" + Config.fakeChatIntervalMax() + "s, "
                    + "burst-chance=" + Config.fakeChatBurstChance()
                    + ", bot-to-bot=" + (Config.fakeChatBotToBotEnabled() ? "on" : "off"));
        } else if (plugin.getBotChatAI() != null) {
            sendStep(sender, "Bot-chat disabled  (fake-chat.enabled=false)");
        } else {
            sendStep(sender, "BotChatAI not available");
        }
    }

    /** Step 4 — secrets.yml reload + AI provider re-init + personalities. */
    private void reloadAi(CommandSender sender) {
        // Reload the secrets file from disk first
        SecretsConfig.reload();

        // Reinitialise AI provider registry with fresh secrets
        me.bill.fakePlayerPlugin.ai.AIProviderRegistry aiReg = plugin.getAIProviderRegistry();
        if (aiReg != null) {
            aiReg.reload(plugin);
        }

        // Reload personality files
        if (plugin.getPersonalityRepository() != null) {
            plugin.getPersonalityRepository().reload();
        }

        // Report new state
        if (aiReg != null) {
            boolean available = Config.aiConversationsEnabled() && aiReg.isAvailable();
            String providerName = available ? aiReg.getActiveProvider().getName() : "none";
            String detail = available
                    ? "enabled  (" + providerName + ")"
                    : Config.aiConversationsEnabled()
                            ? "enabled  (no API key configured — check secrets.yml)"
                            : "disabled  (ai-conversations.enabled=false)";
            int personalities = plugin.getPersonalityRepository() != null
                    ? plugin.getPersonalityRepository().size() : 0;
            sendStep(sender, "Secrets reloaded — AI " + detail
                    + ", personalities=" + personalities);
        } else {
            sendStep(sender, "Secrets reloaded  (AI registry not available)");
        }

        // Warn if secrets changed provider availability but listener can't be updated live
        if (aiReg != null && Config.aiConversationsEnabled() && aiReg.isAvailable()) {
            sendWarn(sender, "If AI was previously offline, restart the server for BotMessageListener to register.");
        }
    }

    /** Step 5 — skin repository rescan (folder, pool, named-overrides, session cache). */
    private void reloadSkins(CommandSender sender) {
        SkinRepository repo = SkinRepository.get();
        repo.reload();
        sendStep(sender, "Skin repository rescanned — mode=" + Config.skinMode()
                + ", folder=" + repo.getFolderSkinCount()
                + ", pool=" + repo.getPoolSkinCount()
                + ", overrides=" + repo.getOverrideCount()
                + ", cache cleared=" + (Config.skinClearCacheOnReload() ? "yes" : "no"));
    }

    /** Step 6 — swap AI + peak-hours manager reload. */
    private void reloadSwap(CommandSender sender) {
        Config.reload();
        me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager phm = plugin.getPeakHoursManager();
        if (phm != null) {
            phm.reload();
            String phState = Config.peakHoursEnabled()
                    ? "on — " + phm.getSleepingCount() + " sleeping, " + phm.getTotalPool() + " pool"
                    : "off";
            sendStep(sender, "Swap + peak-hours reloaded  (" + phState + ")");
        } else {
            sendStep(sender, "Swap/peak-hours subsystem not available");
        }
    }

    /** Full reload — runs all targeted reloads in the correct order. */
    private void reloadAll(CommandSender sender) {

        // ── 1. Core config + names + messages + badword + personalities ──────
        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BotMessageConfig.reload();
        BadwordFilter.reload(plugin);

        if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
            sender.sendMessage(Component.text(
                "│  ⚠ Badword filter is ON but no sources are active — enable " +
                "'badword-filter.use-global-list' or add words to config.yml / bad-words.yml!"
            ).color(YELLOW));
        }

        FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) fpm.refreshCleanNamePool();

        if (plugin.getPersonalityRepository() != null) plugin.getPersonalityRepository().reload();

        int personalities = plugin.getPersonalityRepository() != null
                ? plugin.getPersonalityRepository().size() : 0;
        sendStep(sender, "Config, lang, names ("
                + BotNameConfig.getNames().size() + "), messages ("
                + BotMessageConfig.getMessages().size() + "), badword filter, personalities ("
                + personalities + ")");

        // ── 1b. Chat loops ───────────────────────────────────────────────────
        if (plugin.getBotChatAI() != null && Config.fakeChatEnabled()) {
            plugin.getBotChatAI().restartLoops();
            sendStep(sender, "Bot-chat loops restarted — interval "
                    + Config.fakeChatIntervalMin() + "–" + Config.fakeChatIntervalMax() + "s");
        }

        // ── 1c. Secrets + AI providers ───────────────────────────────────────
        SecretsConfig.reload();
        me.bill.fakePlayerPlugin.ai.AIProviderRegistry aiReg = plugin.getAIProviderRegistry();
        if (aiReg != null) {
            aiReg.reload(plugin);
            boolean aiEnabled = Config.aiConversationsEnabled() && aiReg.isAvailable();
            String aiDetail = aiEnabled
                    ? "enabled  (" + aiReg.getActiveProvider().getName() + ")"
                    : Config.aiConversationsEnabled()
                            ? "enabled  (no API key — check secrets.yml)"
                            : "disabled";
            sendStep(sender, "Secrets + AI providers reloaded — " + aiDetail);
        }

        // ── 1d. Skin repository ──────────────────────────────────────────────
        SkinRepository skinRepo = SkinRepository.get();
        skinRepo.reload();
        sendStep(sender, "Skin repository rescanned — mode=" + Config.skinMode()
                + ", folder=" + skinRepo.getFolderSkinCount()
                + ", pool=" + skinRepo.getPoolSkinCount()
                + ", overrides=" + skinRepo.getOverrideCount());

        // ── 1e. AUTO_PUSH ────────────────────────────────────────────────────
        if (Config.configSyncMode().equalsIgnoreCase("AUTO_PUSH")
                && plugin.getConfigSyncManager() != null) {
            var csm = plugin.getConfigSyncManager();
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                int pushed = csm.pushAll(sender.getName());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    sendStep(sender, "AUTO_PUSH: " + pushed + " config file(s) pushed to network"));
            });
        }

        // ── 2. Tab-list manager ──────────────────────────────────────────────
        if (plugin.getTabListManager() != null) plugin.getTabListManager().reload();
        sendStep(sender, "Tab-list — bots " + (Config.tabListEnabled() ? "visible" : "hidden"));

        // ── 3. Active bot runtime state ──────────────────────────────────────
        if (fpm != null) {
            fpm.applyBodyConfig();
            fpm.applyTabListConfig();
            int active = fpm.getCount();
            if (active > 0) sendStep(sender, active + " active bot(s) state updated"
                    + "  (damageable=" + Config.bodyDamageable()
                    + ", pushable=" + Config.bodyPushable() + ")");
        }

        // ── 3b. Swap + peak-hours ────────────────────────────────────────────
        me.bill.fakePlayerPlugin.fakeplayer.PeakHoursManager phm = plugin.getPeakHoursManager();
        if (phm != null) {
            phm.reload();
            String phState = Config.peakHoursEnabled()
                    ? "on — " + phm.getSleepingCount() + " sleeping, "
                        + phm.getTotalPool() + " total pool"
                    : "off";
            sendStep(sender, "Swap + peak-hours reloaded  (" + phState + ")");
        }

        // ── 4. LuckPerms (auto via event bus) ────────────────────────────────
        sendStep(sender, "LuckPerms — auto-updates via UserDataRecalculateEvent");

        // ── 4b. Task persistence status ──────────────────────────────────────
        boolean taskPersistActive = Config.persistOnRestart() && plugin.getDatabaseManager() != null;
        String taskPersistDetail = taskPersistActive
                ? "db + yaml  (schema v" + me.bill.fakePlayerPlugin.database.DatabaseManager.getCurrentSchemaVersion() + ")"
                : Config.persistOnRestart() ? "yaml only  (DB disabled)" : "disabled";
        sendStep(sender, "Task persistence — " + taskPersistDetail);

        // ── 5. Scoreboard team rebuild ───────────────────────────────────────
        BotTabTeam btt = plugin.getBotTabTeam();
        if (btt != null && fpm != null) {
            btt.rebuild(fpm.getActivePlayers());
            sendStep(sender, "~fpp scoreboard team rebuilt  (" + fpm.getCount() + " bot(s))");
        }

        // ── 6. Config validation ─────────────────────────────────────────────
        int issues = ConfigValidator.validate();
        if (issues > 0) {
            sender.sendMessage(Component.text("│  ⚠ " + issues
                    + " config issue(s) detected — check console").color(YELLOW));
        } else {
            sendStep(sender, "Config validation passed  (0 issues)");
        }

        // ── 7. Update checker (async, non-blocking) ──────────────────────────
        UpdateChecker.invalidateCache();
        UpdateChecker.check(plugin);
        sendStep(sender, "Update check triggered  (async)");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Sends: │  ✓ <message> */
    private void sendStep(CommandSender sender, String message) {
        sender.sendMessage(
            Component.text("│  ").color(ACCENT)
            .append(Component.text("✓ ").color(GREEN))
            .append(Component.text(message).color(GRAY))
        );
    }

    /** Sends: │  ⚠ <message> */
    private void sendWarn(CommandSender sender, String message) {
        sender.sendMessage(
            Component.text("│  ").color(ACCENT)
            .append(Component.text("⚠ ").color(YELLOW))
            .append(Component.text(message).color(YELLOW))
        );
    }
}


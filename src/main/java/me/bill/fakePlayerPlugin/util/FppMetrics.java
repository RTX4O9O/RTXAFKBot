package me.bill.fakePlayerPlugin.util;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.ErrorTracker;
import dev.faststats.core.data.Metric;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import org.bukkit.Bukkit;

/**
 * FastStats anonymous usage metrics — developer-only, not user-configurable.
 *
 * <p>No personal data, player names, or server addresses are ever collected.
 * Data is used solely to understand how FPP features are used so development
 * can be prioritised. Metrics are always on when a token is present.
 *
 * <p>To disable for local development: leave TOKEN blank.
 */
public final class FppMetrics {

    // ── Developer constants — NOT in config.yml ───────────────────────────────
    /** Paste your FastStats project token here. Leave blank to disable. */
    private static final String TOKEN = "376511af6c97b56954ff2abed24dfaea";   // <-- your FastStats token

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Context-aware error tracker — passively captures unhandled exceptions
     * thrown by FPP threads for monitoring on the FastStats dashboard.
     * Requires error-tracking to be enabled in the FastStats project settings.
     */
    public static final ErrorTracker ERROR_TRACKER = ErrorTracker.contextAware();

    private BukkitMetrics metrics;
    private boolean       initialised = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void init(FakePlayerPlugin plugin, FakePlayerManager botManager) {
        if (TOKEN.isBlank()) {
            FppLogger.debug("Metrics: no token set — FastStats disabled.");
            return;
        }

        try {
            metrics = BukkitMetrics.factory()
                    .token(TOKEN)

                    // ── Custom FPP metrics ─────────────────────────────────────────
                    .addMetric(Metric.number("active_bots",
                            () -> botManager == null ? 0 : botManager.getCount()))

                    .addMetric(Metric.number("online_players",
                            () -> (long) Bukkit.getOnlinePlayers().size()))

                    .addMetric(Metric.string("skin_mode",
                            () -> Config.skinMode()))

                    .addMetric(Metric.number("persistence_enabled",
                            () -> Config.persistOnRestart() ? 1L : 0L))

                    .addMetric(Metric.number("body_enabled",
                            () -> Config.spawnBody() ? 1L : 0L))

                    .addMetric(Metric.number("fake_chat_enabled",
                            () -> Config.fakeChatEnabled() ? 1L : 0L))

                    .addMetric(Metric.number("swap_enabled",
                            () -> Config.swapEnabled() ? 1L : 0L))

                    .addMetric(Metric.number("chunk_loading_enabled",
                            () -> Config.chunkLoadingEnabled() ? 1L : 0L))

                    .addMetric(Metric.string("database_type",
                            () -> Config.mysqlEnabled() ? "mysql" : "sqlite"))

                    .addMetric(Metric.number("luckperms_prefix_enabled",
                            () -> (Bukkit.getPluginManager().getPlugin("LuckPerms") != null
                                    && Config.luckpermsUsePrefix()) ? 1L : 0L))

                    .addMetric(Metric.number("max_bots_config",
                            () -> (long) Config.maxBots()))

                    .errorTracker(ERROR_TRACKER)
                    .debug(false)   // keep FastStats internals quiet
                    .create(plugin);

            metrics.ready();
            initialised = true;
            FppLogger.debug("Metrics: FastStats initialised.");

        } catch (Exception e) {
            FppLogger.debug("Metrics: FastStats init failed — " + e.getMessage());
        }
    }

    public void shutdown() {
        if (metrics != null && initialised) {
            try { metrics.shutdown(); } catch (Exception ignored) {}
            metrics     = null;
            initialised = false;
        }
    }

    public boolean isActive() { return initialised && metrics != null; }
}

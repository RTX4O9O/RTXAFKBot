package me.bill.fakePlayerPlugin.config;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Manages secrets.yml — API keys and sensitive configuration for AI providers.
 * Never commit or share this file publicly.
 */
public class SecretsConfig {
    private static FileConfiguration cfg;
    private static File file;
    private static FakePlayerPlugin plugin;

    public static void init(FakePlayerPlugin pl) {
        plugin = pl;
        file = new File(plugin.getDataFolder(), "secrets.yml");

        // Create from template if it doesn't exist
        if (!file.exists()) {
            try (InputStream in = plugin.getResource("secrets.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Created secrets.yml template. Configure your API keys before enabling AI features.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create secrets.yml", e);
            }
        }

        reload();
    }

    public static void reload() {
        if (file == null || !file.exists()) {
            cfg = new YamlConfiguration();
            return;
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    // ── OpenAI ────────────────────────────────────────────────────────────
    public static String openaiApiKey() {
        return cfg.getString("openai.api-key", "");
    }

    public static String openaiModel() {
        return cfg.getString("openai.model", "");
    }

    public static String openaiEndpoint() {
        String endpoint = cfg.getString("openai.endpoint", "");
        return endpoint.isBlank() ? "https://api.openai.com/v1" : endpoint;
    }

    // ── Microsoft Copilot (Azure OpenAI) ──────────────────────────────────
    public static String copilotApiKey() {
        return cfg.getString("copilot.api-key", "");
    }

    public static String copilotModel() {
        return cfg.getString("copilot.model", "");
    }

    public static String copilotEndpoint() {
        return cfg.getString("copilot.endpoint", "");
    }

    public static String copilotDeploymentName() {
        return cfg.getString("copilot.deployment-name", "");
    }

    // ── Groq ──────────────────────────────────────────────────────────────
    public static String groqApiKey() {
        return cfg.getString("groq.api-key", "");
    }

    public static String groqModel() {
        return cfg.getString("groq.model", "");
    }

    public static String groqEndpoint() {
        String endpoint = cfg.getString("groq.endpoint", "");
        return endpoint.isBlank() ? "https://api.groq.com/openai/v1" : endpoint;
    }

    // ── Anthropic Claude ──────────────────────────────────────────────────
    public static String anthropicApiKey() {
        return cfg.getString("anthropic.api-key", "");
    }

    public static String anthropicModel() {
        return cfg.getString("anthropic.model", "");
    }

    public static String anthropicEndpoint() {
        String endpoint = cfg.getString("anthropic.endpoint", "");
        return endpoint.isBlank() ? "https://api.anthropic.com/v1" : endpoint;
    }

    // ── Google Gemini ─────────────────────────────────────────────────────
    public static String googleApiKey() {
        return cfg.getString("google.api-key", "");
    }

    public static String googleModel() {
        return cfg.getString("google.model", "");
    }

    public static String googleEndpoint() {
        String endpoint = cfg.getString("google.endpoint", "");
        return endpoint.isBlank() ? "https://generativelanguage.googleapis.com/v1beta" : endpoint;
    }

    // ── Ollama ────────────────────────────────────────────────────────────
    public static boolean ollamaEnabled() {
        return cfg.getBoolean("ollama.enabled", false);
    }

    public static String ollamaEndpoint() {
        return cfg.getString("ollama.endpoint", "http://localhost:11434");
    }

    public static String ollamaModel() {
        return cfg.getString("ollama.model", "");
    }

    // ── Custom OpenAI-compatible ──────────────────────────────────────────
    public static boolean customEnabled() {
        return cfg.getBoolean("custom.enabled", false);
    }

    public static String customApiKey() {
        return cfg.getString("custom.api-key", "");
    }

    public static String customModel() {
        return cfg.getString("custom.model", "");
    }

    public static String customEndpoint() {
        return cfg.getString("custom.endpoint", "");
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    public static boolean hasAnyApiKey() {
        return !openaiApiKey().isBlank()
            || !copilotApiKey().isBlank()
            || !groqApiKey().isBlank()
            || !anthropicApiKey().isBlank()
            || !googleApiKey().isBlank()
            || ollamaEnabled()
            || (customEnabled() && !customApiKey().isBlank());
    }
}


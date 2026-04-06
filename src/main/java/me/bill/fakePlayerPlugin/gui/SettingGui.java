package me.bill.fakePlayerPlugin.gui;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.permission.Perm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;

import java.util.*;

/**
 * Interactive settings GUI — opens a 3-row chest that lets admins toggle/cycle
 * plugin configuration values without editing {@code config.yml} directly.
 *
 * <h3>Layout (3 rows / 27 slots)</h3>
 * <pre>
 *  [S0][S1][S2][S3][S4][S5][S6][S7][S8]   ← row 1: up to 9 settings per page
 *  [GL][GL][GL][GL][GL][GL][GL][GL][GL]   ← row 2: coloured category separator
 *  [← ][C1][C2][C3][C4][C5][GL][⊡ ][→ ]  ← row 3: navigation
 * </pre>
 *
 * <h3>Interaction</h3>
 * <ul>
 *   <li><b>Toggle items</b>  – any click flips the boolean in place.</li>
 *   <li><b>Numeric items</b> – clicking closes the chest and prompts the player
 *       to type a value directly in chat.  The typed message is intercepted
 *       (invisible to other players), validated, and applied.  The settings
 *       GUI then reopens automatically.  Type {@code cancel} to abort.</li>
 * </ul>
 */
public final class SettingGui implements Listener {

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final TextColor ACCENT         = TextColor.fromHexString("#0079FF");
    private static final TextColor ON_GREEN       = TextColor.fromHexString("#66CC66");
    private static final TextColor OFF_RED        = NamedTextColor.RED;
    private static final TextColor VALUE_YELLOW   = TextColor.fromHexString("#FFDD57");
    private static final TextColor YELLOW         = NamedTextColor.YELLOW;
    private static final TextColor GRAY           = NamedTextColor.GRAY;
    private static final TextColor DARK_GRAY      = NamedTextColor.DARK_GRAY;
    private static final TextColor WHITE          = NamedTextColor.WHITE;
    private static final TextColor COMING_SOON_COLOR = TextColor.fromHexString("#FFA500");

    // ── GUI geometry ──────────────────────────────────────────────────────────
    private static final int SIZE              = 27;
    private static final int SETTINGS_PER_PAGE = 18;
    private static final int SLOT_RESET        = 18;
    private static final int SLOT_NAV_PREV     = 19;   // ← Prev page (replaces filler)
    private static final int SLOT_NAV_NEXT     = 26;   // → Next page (replaces old close)
    private static final int[] CAT_SLOTS       = { 20, 21, 22, 23, 24, 25 };

    // ── Owner skull cache  (Skin System entry icon) ───────────────────────────
    /** UUID of El_Pepes — the owner whose head is shown on the Skin System entry. */
    private static final java.util.UUID SKIN_OWNER_UUID =
            java.util.UUID.fromString("a318f9f4-e2bf-479c-a47a-6a2c1b0b9e66");
    private static final String SKIN_OWNER_NAME = "El_Pepes";
    /** Refresh the skull from Mojang at most once every 30 minutes. */
    private static final long SKULL_TTL_MS = 30L * 60 * 1_000;

    /** Cached player-head ItemStack for the Skin System entry.  Volatile for safe cross-thread writes. */
    private volatile ItemStack cachedOwnerSkull = null;
    private volatile long      skullRefreshedAt = 0L;

    // ── State ─────────────────────────────────────────────────────────────────
    private final FakePlayerPlugin plugin;

    /** Per-player GUI state: [categoryIndex, pageIndex]. */
    private final Map<UUID, int[]> sessions = new HashMap<>();

    /**
     * Active chat-input sessions.  Present while the GUI is closed and the
     * plugin is waiting for the player to type a value in chat.
     * Cleaned up on submit, cancel, quit, or 60-second timeout.
     */
    private final Map<UUID, ChatInputSession> chatSessions = new HashMap<>();

    /**
     * UUIDs whose InventoryCloseEvent should NOT remove the GUI session because
     * we are transitioning to the chat-input prompt (we will reopen the GUI after).
     */
    private final Set<UUID> pendingChatInput = new HashSet<>();

    /**
     * UUIDs whose InventoryCloseEvent should be ignored because {@link #build}
     * is in the middle of opening a fresh inventory (openInventory fires close
     * on the previous one — we don't want to treat that as a real "user closed").
     */
    private final Set<UUID> pendingRebuild = new HashSet<>();

    // ── Category definitions ──────────────────────────────────────────────────
    private final Category[] categories;

    public SettingGui(FakePlayerPlugin plugin) {
        this.plugin     = plugin;
        this.categories = new Category[]{
            general(), body(), chat(), swap(), peaks(), pvp()
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Public API
    // ═════════════════════════════════════════════════════════════════════════

    /** Opens the settings GUI for {@code player} at the General category. */
    public void open(Player player) {
        sessions.put(player.getUniqueId(), new int[]{ 0, 0 });   // [catIdx, pageIdx]
        build(player);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Bukkit events
    // ═════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;
        if (!Perm.has(player, Perm.SETTINGS)) return;

        int[] state = sessions.get(holder.uuid);
        if (state == null) return;

        int slot   = event.getSlot();
        int catIdx = state[0];
        int pageIdx = state[1];

        // ── Reset button ──────────────────────────────────────────────────
        if (slot == SLOT_RESET) {
            playUiClick(player, 0.6f);
            resetCategory(player, catIdx);
            return;
        }
        // ── Prev page button (slot 19) ────────────────────────────────────
        if (slot == SLOT_NAV_PREV && pageIdx > 0) {
            playUiClick(player, 1.0f);
            state[1]--;
            build(player);
            return;
        }
        // ── Next page button (slot 26) ────────────────────────────────────
        if (slot == SLOT_NAV_NEXT) {
            List<SettingEntry> settings = categories[catIdx].settings;
            if ((pageIdx + 1) * SETTINGS_PER_PAGE < settings.size()) {
                playUiClick(player, 1.0f);
                state[1]++;
                build(player);
            }
            return;
        }
        // ── Category tabs ─────────────────────────────────────────────────
        for (int i = 0; i < CAT_SLOTS.length; i++) {
            if (slot == CAT_SLOTS[i] && i < categories.length) {
                if (i != catIdx) playUiClick(player, 1.3f);
                state[0] = i;
                state[1] = 0;   // reset page when switching categories
                build(player);
                return;
            }
        }
        // ── Settings (rows 1 & 2: slots 0-17, page-aware) ────────────────
        if (slot < 18) {
            List<SettingEntry> settings = categories[catIdx].settings;
            int entryIdx = pageIdx * SETTINGS_PER_PAGE + slot;
            if (entryIdx >= settings.size()) return;

            SettingEntry entry = settings.get(entryIdx);

            // Coming-soon entries: play villager-no sound and deny with actionbar
            if (entry.type == SettingType.COMING_SOON) {
                player.playSound(player.getLocation(),
                        Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.8f, 1.0f);
                player.sendActionBar(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                    .append(Component.text(entry.label + "  ").color(WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.text("— ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ").color(COMING_SOON_COLOR).decoration(TextDecoration.BOLD, true)));
                return;
            }

            if (entry.type == SettingType.TOGGLE) {
                // Toggles flip in-place — no sign editor needed
                entry.apply(plugin);
                plugin.saveConfig();
                Config.reload();
                applyLiveEffect(entry.configKey);
                String newVal = entry.currentValueString(plugin);
                // High pitch = turned ON (positive feedback), low pitch = turned OFF
                playUiClick(player, newVal.startsWith("✔") ? 1.2f : 0.85f);
                sendActionBarConfirm(player, entry.label, newVal);
                build(player);
            } else {
                // Numeric / cycle settings → prompt player to type in chat
                playUiClick(player, 1.0f);
                openChatInput(player, entry, state.clone());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
        // If we are transitioning to the chat-input prompt, keep the session alive
        if (pendingChatInput.contains(uuid)) return;
        // If build() is reopening the inventory internally, ignore this close event
        if (pendingRebuild.contains(uuid)) return;
        sessions.remove(uuid);

        // Silent config reload so every saved value is fully applied
        plugin.saveConfig();
        Config.reload();

        // Send "Settings saved" confirmation — but not on player disconnect
        if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT
                && event.getPlayer() instanceof Player player) {
            player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✔ ").color(ON_GREEN))
                .append(Component.text("ꜱᴇᴛᴛɪɴɢꜱ ꜱᴀᴠᴇᴅ.").color(WHITE)));
        }
    }

    /**
     * Intercepts the player's chat message when a chat-input session is active.
     * The message is <em>cancelled</em> (never shown to other players) and used
     * as the new config value.  Runs at LOWEST priority so other plugins that
     * process chat see the cancellation before doing any work.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSession ses = chatSessions.remove(uuid);
        if (ses == null) return;

        event.setCancelled(true);                       // invisible to everyone else
        Bukkit.getScheduler().cancelTask(ses.cleanupTaskId);

        String raw = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        // Restore GUI session; all further work must be on the main thread
        sessions.put(uuid, ses.guiState);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return;

            if (raw.equalsIgnoreCase("cancel")) {
                p.sendMessage(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✦ ").color(ACCENT))
                    .append(Component.text("ᴄᴀɴᴄᴇʟʟᴇᴅ — ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ.").color(GRAY)));
                build(p);
                return;
            }

            boolean ok = tryApply(p, ses.entry, raw);
            if (ok) {
                plugin.saveConfig();
                Config.reload();
                applyLiveEffect(ses.entry.configKey);
                sendActionBarConfirm(p, ses.entry.label, ses.entry.currentValueString(plugin));
            }
            build(p);
        });
    }

    /** Cleans up any dangling chat-input session when a player disconnects. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        pendingChatInput.remove(uuid);
        ChatInputSession ses = chatSessions.remove(uuid);
        if (ses != null) {
            Bukkit.getScheduler().cancelTask(ses.cleanupTaskId);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Chat input
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Closes the settings chest and sends the player a formatted prompt asking
     * them to type a new value in chat.  The response is captured by
     * {@link #onPlayerChat} — other players never see the raw value.
     *
     * <p>The player can type {@code cancel} to abort and reopen the GUI.
     * A 60-second timeout automatically cancels and reopens the GUI if no
     * input is received.
     */
    private void openChatInput(Player player, SettingEntry entry, int[] guiState) {
        UUID uuid = player.getUniqueId();

        // Mark: InventoryCloseEvent should NOT destroy the GUI session
        pendingChatInput.add(uuid);
        player.closeInventory();
        pendingChatInput.remove(uuid);   // clear immediately — session kept in `sessions`

        String currentVal = entry.currentValueString(plugin)
                .replace("✔ ", "").replace("✘ ", "");

        // ── Prompt ────────────────────────────────────────────────────────────
        player.sendMessage(Component.empty());
        player.sendMessage(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("┌─ ").color(DARK_GRAY))
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("]  ").color(DARK_GRAY))
            .append(Component.text("ꜱᴇᴛᴛɪɴɢꜱ").color(WHITE).decoration(TextDecoration.BOLD, true))
            .append(Component.text("  ·  ᴇᴅɪᴛ ᴠᴀʟᴜᴇ").color(DARK_GRAY)));
        player.sendMessage(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(Component.text(entry.label).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
        for (String line : entry.description.split("\\\\n|\n")) {
            if (!line.isBlank()) {
                player.sendMessage(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("│  ").color(DARK_GRAY))
                    .append(Component.text(line).color(GRAY)));
            }
        }
        player.sendMessage(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY)));
        player.sendMessage(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(Component.text("ᴄᴜʀʀᴇɴᴛ  ").color(DARK_GRAY))
            .append(Component.text(currentVal).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("└─ ").color(DARK_GRAY))
            .append(Component.text("ᴛʏᴘᴇ ᴀ ɴᴇᴡ ᴠᴀʟᴜᴇ, ᴏʀ ").color(GRAY))
            .append(Component.text("ᴄᴀɴᴄᴇʟ").color(OFF_RED).decoration(TextDecoration.BOLD, true))
            .append(Component.text(" ᴛᴏ ɢᴏ ʙᴀᴄᴋ.").color(GRAY)));
        player.sendMessage(Component.empty());

        // ── 60-second timeout ─────────────────────────────────────────────────
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ChatInputSession stale = chatSessions.remove(uuid);
            if (stale != null) {
                sessions.put(uuid, stale.guiState);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("✦ ").color(ACCENT))
                        .append(Component.text("ɪɴᴘᴜᴛ ᴛɪᴍᴇᴅ ᴏᴜᴛ — ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ.").color(GRAY)));
                    build(p);
                }
            }
        }, 20L * 60).getTaskId();

        chatSessions.put(uuid, new ChatInputSession(entry, guiState, taskId));
    }

    /**
     * Parses {@code raw} as the appropriate type for {@code entry}, validates the
     * range, and applies it.  Sends an in-chat error and returns {@code false} on
     * failure so the caller can decide whether to reopen.
     */
    private boolean tryApply(Player player, SettingEntry entry, String raw) {
        var cfg = plugin.getConfig();
        try {
            switch (entry.type) {
                case CYCLE_INT -> {
                    int val = Integer.parseInt(raw);
                    if (val < 0) {
                        player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text("ᴠᴀʟᴜᴇ ᴍᴜꜱᴛ ʙᴇ ").color(GRAY))
                            .append(Component.text("0 ᴏʀ ɢʀᴇᴀᴛᴇʀ").color(VALUE_YELLOW))
                            .append(Component.text(".").color(GRAY)));
                        return false;
                    }
                    cfg.set(entry.configKey, val);
                }
                case CYCLE_DOUBLE -> {
                    double val = Double.parseDouble(raw);
                    if (val < 0) {
                        player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text("ᴠᴀʟᴜᴇ ᴍᴜꜱᴛ ʙᴇ ").color(GRAY))
                            .append(Component.text("0 ᴏʀ ɢʀᴇᴀᴛᴇʀ").color(VALUE_YELLOW))
                            .append(Component.text(".").color(GRAY)));
                        return false;
                    }
                    cfg.set(entry.configKey, val);
                }
                default -> { return false; }
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✘ ").color(OFF_RED))
                .append(Component.text("\"").color(GRAY))
                .append(Component.text(raw).color(VALUE_YELLOW))
                .append(Component.text("\" ɪꜱ ɴᴏᴛ ᴀ ᴠᴀʟɪᴅ ɴᴜᴍʙᴇʀ.").color(GRAY)));
            return false;
        }
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Build
    // ═════════════════════════════════════════════════════════════════════════

    private void build(Player player) {
        UUID   uuid  = player.getUniqueId();
        int[]  state = sessions.get(uuid);
        if (state == null) return;

        int catIdx  = state[0];
        int pageIdx = state[1];
        Category cat = categories[catIdx];

        GuiHolder holder = new GuiHolder(uuid);
        Component title = Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("] ").color(DARK_GRAY))
            .append(Component.text(cat.label).color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);

        // ── Settings (rows 1–2, slots 0-17) — page-aware ─────────────────────
        int settingsCount = cat.settings.size();
        int startIdx      = pageIdx * SETTINGS_PER_PAGE;
        int endIdx        = Math.min(startIdx + SETTINGS_PER_PAGE, settingsCount);
        for (int i = startIdx; i < endIdx; i++) {
            inv.setItem(i - startIdx, buildSettingItem(cat.settings.get(i)));
        }

        // ── Row 3 (slots 18-26): navigation bar ───────────────────────────────
        ItemStack nav = glassFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 18; i < 27; i++) inv.setItem(i, nav);

        // Reset button (slot 18)
        inv.setItem(SLOT_RESET, buildResetButton());

        // Prev page button (slot 19) — only when not on first page
        if (pageIdx > 0) {
            inv.setItem(SLOT_NAV_PREV, buildNavButton(false));
        }

        // Category tabs (slots 20-25)
        for (int i = 0; i < CAT_SLOTS.length && i < categories.length; i++) {
            inv.setItem(CAT_SLOTS[i], buildCategoryTab(i, i == catIdx));
        }

        // Next page button (slot 26) — only when more pages exist
        if (endIdx < settingsCount) {
            inv.setItem(SLOT_NAV_NEXT, buildNavButton(true));
        }

        // Mark as rebuild so the InventoryCloseEvent fired by openInventory is ignored
        pendingRebuild.add(uuid);
        player.openInventory(inv);
        pendingRebuild.remove(uuid);
        sessions.put(uuid, state);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Item builders
    // ═════════════════════════════════════════════════════════════════════════

    private ItemStack buildSettingItem(SettingEntry entry) {
        // ── Coming-soon entries get their own distinct locked look ─────────────
        if (entry.type == SettingType.COMING_SOON) {
            // Skin entry: use the owner's real player head instead of a static icon
            ItemStack item = "skin.guaranteed-skin".equals(entry.configKey)
                    ? getOwnerSkull()
                    : new ItemStack(entry.icon);
            ItemMeta  meta = item.getItemMeta();
            meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                .append(Component.text(entry.label).color(COMING_SOON_COLOR).decoration(TextDecoration.BOLD, true)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
                .append(Component.text("⚠ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ").color(COMING_SOON_COLOR).decoration(TextDecoration.BOLD, true)));
            lore.add(Component.empty());
            for (String line : entry.description.split("\\\\n|\n")) {
                if (!line.isBlank()) {
                    lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(line).color(GRAY)));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                .append(Component.text("ꜰᴇᴀᴛᴜʀᴇ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ").color(DARK_GRAY)));
            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        boolean isToggle = entry.type == SettingType.TOGGLE;
        boolean isOn     = isToggle && plugin.getConfig().getBoolean(entry.configKey, false);

        // Every entry uses its own semantic icon; name colour signals on/off for toggles
        TextColor nameColor = isToggle ? (isOn ? ON_GREEN : OFF_RED) : ACCENT;

        ItemStack item = new ItemStack(entry.icon);
        ItemMeta  meta = item.getItemMeta();

        // Enabled toggles sparkle with an enchant glow — immediately recognisable
        if (isToggle && isOn) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.displayName(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(entry.label).color(nameColor).decoration(TextDecoration.BOLD, true)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Value row
        String    valStr   = entry.currentValueString(plugin);
        TextColor valColor = isToggle ? (isOn ? ON_GREEN : OFF_RED) : VALUE_YELLOW;
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
            .append(Component.text(valStr).color(valColor).decoration(TextDecoration.BOLD, true)));
        lore.add(Component.empty());

        // Description
        for (String line : entry.description.split("\\\\n|\n")) {
            if (!line.isBlank()) {
                lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(line).color(GRAY)));
            }
        }
        lore.add(Component.empty());

        // Action hint
        if (isToggle) {
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("◈ ").color(ACCENT))
                .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɢʟᴇ").color(DARK_GRAY)));
        } else {
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✎ ").color(ACCENT))
                .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ꜱᴇᴛ ᴀ ᴠᴀʟᴜᴇ ɪɴ ᴄʜᴀᴛ").color(DARK_GRAY)));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCategoryTab(int idx, boolean active) {
        Category cat  = categories[idx];
        Material mat  = active ? cat.activeMat : cat.inactiveMat;
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.displayName(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(cat.label)
                .color(ACCENT)
                .decoration(TextDecoration.BOLD, active)));
        meta.lore(List.of(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(active ? "◀  ᴄᴜʀʀᴇɴᴛʟʏ ᴠɪᴇᴡɪɴɢ" : "ᴄʟɪᴄᴋ ᴛᴏ ꜱᴡɪᴛᴄʜ")
                .color(active ? ON_GREEN : DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildNavButton(boolean isNext) {
        Material  mat   = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
        String    label = isNext ? "→  ɴᴇxᴛ ᴘᴀɢᴇ" : "←  ᴘʀᴇᴠ ᴘᴀɢᴇ";
        TextColor col   = isNext ? ON_GREEN : COMING_SOON_COLOR;
        ItemStack item  = new ItemStack(mat);
        ItemMeta  meta  = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(label).color(col).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ɢᴏ ᴛᴏ ᴛʜᴇ " + (isNext ? "ɴᴇxᴛ" : "ᴘʀᴇᴠɪᴏᴜꜱ") + " ᴘᴀɢᴇ.").color(DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    // ── Owner skull helpers ───────────────────────────────────────────────────

    /**
     * Returns a player-head {@link ItemStack} for {@value SKIN_OWNER_NAME}.
     * The result is cached for {@value #SKULL_TTL_MS} ms; when stale a fresh
     * Mojang profile is fetched asynchronously so the next call gets the
     * up-to-date skin (handles skin changes automatically).
     */
    private ItemStack getOwnerSkull() {
        long now = System.currentTimeMillis();
        ItemStack cached = cachedOwnerSkull;
        if (cached != null && (now - skullRefreshedAt) < SKULL_TTL_MS) {
            return cached.clone();
        }
        // Build immediately from the local offline-player cache (no Mojang round-trip)
        ItemStack skull = buildSkullSync();
        cachedOwnerSkull = skull;
        skullRefreshedAt = now;
        // Kick off an async Mojang profile update — next render will get the fresh skin
        scheduleSkullRefresh();
        return skull.clone();
    }

    /** Builds the skull synchronously using Bukkit's local profile cache. */
    private ItemStack buildSkullSync() {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(SKIN_OWNER_UUID, SKIN_OWNER_NAME);
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Fetches the latest skin textures for {@value SKIN_OWNER_NAME} from Mojang
     * asynchronously via {@link PlayerProfile#complete(boolean)} and stores
     * the updated skull in {@link #cachedOwnerSkull}.
     * Runs off the main thread so there is no server hiccup.
     */
    private void scheduleSkullRefresh() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerProfile profile = Bukkit.createProfile(SKIN_OWNER_UUID, SKIN_OWNER_NAME);
                profile.complete(true);   // fetches textures from Mojang if not cached
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta  = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setPlayerProfile(profile);
                    skull.setItemMeta(meta);
                }
                cachedOwnerSkull = skull;
                skullRefreshedAt = System.currentTimeMillis();
            } catch (Exception ignored) {
                // Network unavailable or Mojang rate-limit — keep the old cache
            }
        });
    }

    private ItemStack buildResetButton() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲  ʀᴇꜱᴇᴛ ᴛʜɪꜱ ᴘᴀɢᴇ").color(YELLOW).decoration(TextDecoration.BOLD, false)));
        meta.lore(List.of(
            Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ʀᴇꜱᴇᴛ ᴀʟʟ ꜱᴇᴛᴛɪɴɢꜱ ᴏɴ ᴛʜɪꜱ").color(GRAY)),
            Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ᴘᴀɢᴇ ᴛᴏ ᴛʜᴇɪʀ ᴅᴇꜰᴀᴜʟᴛ ᴠᴀʟᴜᴇꜱ.").color(GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack glassFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty());
        meta.lore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Live effect application
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Applies the live runtime effect of a config key change without requiring
     * a full {@code /fpp reload}.  Mirrors the same subsystem calls used by
     * {@code ReloadCommand} so behaviour is always consistent.
     */
    private void applyLiveEffect(String configKey) {
        FakePlayerManager fpm = plugin.getFakePlayerManager();

        // ── Body: damageable / pushable / max-health ──────────────────────────
        if (configKey.equals("body.enabled") || configKey.equals("body.pushable")
                || configKey.equals("body.damageable") || configKey.equals("combat.max-health")) {
            if (fpm != null) fpm.applyBodyConfig();
            return;
        }

        // ── Tab-list visibility ───────────────────────────────────────────────
        if (configKey.equals("tab-list.enabled")) {
            if (plugin.getTabListManager() != null) plugin.getTabListManager().reload();
            if (fpm != null) fpm.applyTabListConfig();
            return;
        }

        // ── Chat AI — any fake-chat.* change restarts loops so new values
        //    (interval, chance, stagger, etc.) take effect immediately ─────────
        if (configKey.startsWith("fake-chat.")) {
            var chatAI = plugin.getBotChatAI();
            if (chatAI != null) {
                if (Config.fakeChatEnabled()) chatAI.restartLoops();
                else chatAI.cancelAll();
            }
            return;
        }

        // ── Swap AI — cancel all pending timers and reschedule if swap is on ──
        if (configKey.startsWith("swap.")) {
            var swapAI = plugin.getBotSwapAI();
            if (swapAI != null) {
                swapAI.cancelAll();
                if (Config.swapEnabled() && fpm != null) {
                    // Reschedule every active bot with the new session/absence timing
                    fpm.getActivePlayers().forEach(swapAI::schedule);
                }
            }
            return;
        }

        // ── Peak hours — wakes sleeping bots then re-evaluates window ─────────
        if (configKey.startsWith("peak-hours.")) {
            var phm = plugin.getPeakHoursManager();
            if (phm != null) phm.reload();
        }
    }

    private void sendActionBarConfirm(Player player, String label, String newVal) {
        player.sendActionBar(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("✔ ").color(ON_GREEN))
            .append(Component.text(label + "  ").color(WHITE).decoration(TextDecoration.BOLD, false))
            .append(Component.text("→  ").color(DARK_GRAY))
            .append(Component.text(newVal).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    /**
     * Plays the Minecraft UI button-click sound privately to {@code player}.
     * {@code pitch} controls the feel:
     * <ul>
     *   <li>~1.2 — toggle ON (bright, positive)</li>
     *   <li>~0.85 — toggle OFF (muted, neutral)</li>
     *   <li>~1.3 — category tab switch (light tap)</li>
     *   <li>~1.0 — numeric input prompt (neutral)</li>
     *   <li>~0.8 — close button</li>
     *   <li>~0.6 — reset button (heavier)</li>
     * </ul>
     */
    private static void playUiClick(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, pitch);
    }

    /**
     * Resets all settings in the specified category to their default values
     * as defined in the JAR's {@code config.yml}.
     */
    private void resetCategory(Player player, int catIdx) {
        if (catIdx < 0 || catIdx >= categories.length) return;
        
        Category cat = categories[catIdx];
        var cfg      = plugin.getConfig();
        var defaults = cfg.getDefaults();   // defaults loaded from the bundled config.yml

        // Reset each setting to its true default from the JAR config
        for (SettingEntry entry : cat.settings) {
            switch (entry.type) {
                case TOGGLE -> cfg.set(entry.configKey,
                        defaults != null
                                ? defaults.getBoolean(entry.configKey, false)
                                : false);
                case CYCLE_INT -> cfg.set(entry.configKey,
                        defaults != null
                                ? defaults.getInt(entry.configKey, entry.intValues[0])
                                : entry.intValues[0]);
                case CYCLE_DOUBLE -> cfg.set(entry.configKey,
                        defaults != null
                                ? defaults.getDouble(entry.configKey, entry.dblValues[0])
                                : entry.dblValues[0]);
            }
        }
        
        // Save and reload
        plugin.saveConfig();
        Config.reload();
        
        // Apply live effects for each reset key
        for (SettingEntry entry : cat.settings) {
            applyLiveEffect(entry.configKey);
        }
        
        // Rebuild GUI and send confirmation
        build(player);
        player.sendActionBar(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲ ").color(YELLOW))
            .append(Component.text(cat.label + "  ").color(WHITE).decoration(TextDecoration.BOLD, false))
            .append(Component.text("ʀᴇꜱᴇᴛ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛꜱ").color(YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Category definitions  (unchanged from original)
    // ═════════════════════════════════════════════════════════════════════════

    private Category general() {
        return new Category("⚙ ɢᴇɴᴇʀᴀʟ",
            Material.COMPARATOR, Material.GRAY_DYE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            List.of(
                SettingEntry.toggle("persistence.enabled",    "ᴘᴇʀꜱɪꜱᴛ ᴏɴ ʀᴇꜱᴛᴀʀᴛ",
                    "ʙᴏᴛꜱ ʀᴇꜱᴛᴏʀᴇ ᴛᴏ ᴛʜᴇɪʀ ʟᴀꜱᴛ ᴘᴏꜱɪᴛɪᴏɴ\nᴀꜰᴛᴇʀ ᴀ ꜱᴇʀᴠᴇʀ ʀᴇꜱᴛᴀʀᴛ.",
                    Material.ENDER_CHEST),
                SettingEntry.toggle("tab-list.enabled",       "ᴛᴀʙ-ʟɪꜱᴛ ᴠɪꜱɪʙɪʟɪᴛʏ",
                    "ᴅɪꜱᴘʟᴀʏ ʙᴏᴛꜱ ᴀꜱ ᴇɴᴛʀɪᴇꜱ\nɪɴ ᴛʜᴇ ᴘʟᴀʏᴇʀ ᴛᴀʙ ʟɪꜱᴛ.",
                    Material.NAME_TAG),
                SettingEntry.toggle("chunk-loading.enabled",  "ᴄʜᴜɴᴋ ʟᴏᴀᴅɪɴɢ",
                    "ʙᴏᴛꜱ ᴋᴇᴇᴘ ꜱᴜʀʀᴏᴜɴᴅɪɴɢ ᴄʜᴜɴᴋꜱ\nʟᴏᴀᴅᴇᴅ ʟɪᴋᴇ ʀᴇᴀʟ ᴘʟᴀʏᴇʀꜱ.",
                    Material.GRASS_BLOCK),
                SettingEntry.cycleInt("spawn-cooldown",        "ꜱᴘᴀᴡɴ ᴄᴏᴏʟᴅᴏᴡɴ (ꜱ)",
                    "ꜱᴇᴄᴏɴᴅꜱ ʙᴇᴛᴡᴇᴇɴ /ꜰᴘᴘ ꜱᴘᴀᴡɴ ᴜꜱᴇꜱ\nᴘᴇʀ ᴘʟᴀʏᴇʀ. 0 = ᴅɪꜱᴀʙʟᴇᴅ.",
                    Material.CLOCK, new int[]{ 0, 10, 30, 60, 120, 300 }),
                SettingEntry.cycleInt("limits.max-bots",       "ɢʟᴏʙᴀʟ ʙᴏᴛ ᴄᴀᴘ",
                    "ᴍᴀxɪᴍᴜᴍ ʙᴏᴛꜱ ꜱᴇʀᴠᴇʀ-ᴡɪᴅᴇ.\n0 = ɴᴏ ʟɪᴍɪᴛ.",
                    Material.CHEST, new int[]{ 10, 25, 50, 100, 250, 500, 1000 }),
                SettingEntry.cycleInt("limits.user-bot-limit", "ᴘᴇʀ-ᴜꜱᴇʀ ʙᴏᴛ ʟɪᴍɪᴛ",
                    "ᴅᴇꜰᴀᴜʟᴛ ᴘᴇʀꜱᴏɴᴀʟ ʟɪᴍɪᴛ ꜰᴏʀ\nꜰᴘᴘ.ᴜꜱᴇʀ.ꜱᴘᴀᴡɴ ᴘʟᴀʏᴇʀꜱ.",
                    Material.SHIELD, new int[]{ 1, 2, 3, 5, 10 }),
                // ── Page 2 ────────────────────────────────────────────────────
                SettingEntry.cycleInt("join-delay.min",        "ᴊᴏɪɴ ᴅᴇʟᴀʏ — ᴍɪɴ (ᴛɪᴄᴋꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ᴊᴏɪɴꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.FEATHER, new int[]{ 0, 5, 10, 20, 40, 100 }),
                SettingEntry.cycleInt("join-delay.max",        "ᴊᴏɪɴ ᴅᴇʟᴀʏ — ᴍᴀx (ᴛɪᴄᴋꜱ)",
                    "ʟᴏɴɢᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ᴊᴏɪɴꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.FEATHER, new int[]{ 0, 5, 10, 20, 40, 100 }),
                SettingEntry.cycleInt("leave-delay.min",       "ʟᴇᴀᴠᴇ ᴅᴇʟᴀʏ — ᴍɪɴ (ᴛɪᴄᴋꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ʟᴇᴀᴠᴇꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.GRAY_DYE, new int[]{ 0, 5, 10, 20, 40, 100 }),
                SettingEntry.cycleInt("leave-delay.max",       "ʟᴇᴀᴠᴇ ᴅᴇʟᴀʏ — ᴍᴀx (ᴛɪᴄᴋꜱ)",
                    "ʟᴏɴɢᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ʟᴇᴀᴠᴇꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.GRAY_DYE, new int[]{ 0, 5, 10, 20, 40, 100 }),
                SettingEntry.cycleInt("chunk-loading.radius",  "ᴄʜᴜɴᴋ ʟᴏᴀᴅ ʀᴀᴅɪᴜꜱ",
                    "ᴄʜᴜɴᴋ ʀᴀᴅɪᴜꜱ ᴋᴇᴘᴛ ʟᴏᴀᴅᴇᴅ ᴀʀᴏᴜɴᴅ\nᴇᴀᴄʜ ʙᴏᴛ. 0 = ꜱᴇʀᴠᴇʀ ᴅᴇꜰᴀᴜʟᴛ.",
                    Material.COMPASS, new int[]{ 0, 2, 4, 6, 8, 12, 16 })
            ));
    }

    private Category body() {
        return new Category("🤖 ʙᴏᴅʏ",
            Material.ARMOR_STAND, Material.ARMOR_STAND,
            Material.LIME_STAINED_GLASS_PANE,
            List.of(
                SettingEntry.comingSoon("body.enabled",           "ꜱᴘᴀᴡɴ ʙᴏᴅʏ",
                    "ᴀʟʟᴏᴡ ʙᴏᴛꜱ ᴛᴏ ᴇxɪꜱᴛ ᴡɪᴛʜᴏᴜᴛ ᴀ\nᴘʜʏꜱɪᴄᴀʟ ᴇɴᴛɪᴛʏ (ᴛᴀʙ-ʟɪꜱᴛ ᴏɴʟʏ).",
                    Material.ARMOR_STAND),
                SettingEntry.comingSoon("skin.guaranteed-skin",   "ꜱᴋɪɴ ꜱʏꜱᴛᴇᴍ",
                    "ᴄᴜꜱᴛᴏᴍ ꜱᴋɪɴꜱ ꜰᴏʀ ʙᴏᴛꜱ.\nᴛʜɪꜱ ꜰᴇᴀᴛᴜʀᴇ ɪꜱ ɪɴ ᴅᴇᴠᴇʟᴏᴘᴍᴇɴᴛ.",
                    Material.PLAYER_HEAD),
                SettingEntry.toggle("body.pushable",          "ᴘᴜꜱʜᴀʙʟᴇ",
                    "ᴀʟʟᴏᴡ ᴘʟᴀʏᴇʀꜱ ᴀɴᴅ ᴇɴᴛɪᴛɪᴇꜱ\nᴛᴏ ᴘᴜꜱʜ ʙᴏᴛ ʙᴏᴅɪᴇꜱ.",
                    Material.PISTON),
                SettingEntry.toggle("body.damageable",        "ᴅᴀᴍᴀɢᴇᴀʙʟᴇ",
                    "ʙᴏᴛꜱ ᴛᴀᴋᴇ ᴅᴀᴍᴀɢᴇ ᴀɴᴅ ᴄᴀɴ ᴅɪᴇ.\nᴅɪꜱᴀʙʟᴇ ꜰᴏʀ ɪɴᴠᴜʟɴᴇʀᴀʙʟᴇ ʙᴏᴛꜱ.",
                    Material.IRON_SWORD),
                SettingEntry.toggle("head-ai.enabled",        "ʜᴇᴀᴅ ᴀɪ",
                    "ʙᴏᴛꜱ ꜱᴍᴏᴏᴛʜʟʏ ʀᴏᴛᴀᴛᴇ ᴛᴏ ꜰᴀᴄᴇ\nᴛʜᴇ ɴᴇᴀʀᴇꜱᴛ ᴘʟᴀʏᴇʀ ɪɴ ʀᴀɴɢᴇ.",
                    Material.ENDER_EYE),
                SettingEntry.toggle("swim-ai.enabled",        "ꜱᴡɪᴍ ᴀɪ",
                    "ʙᴏᴛꜱ ꜱᴡɪᴍ ᴜᴘᴡᴀʀᴅ ᴡʜᴇɴ\nꜱᴜʙᴍᴇʀɢᴇᴅ ɪɴ ᴡᴀᴛᴇʀ ᴏʀ ʟᴀᴠᴀ.",
                    Material.WATER_BUCKET),
                SettingEntry.toggle("death.respawn-on-death", "ʀᴇꜱᴘᴀᴡɴ ᴏɴ ᴅᴇᴀᴛʜ",
                    "ʙᴏᴛꜱ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ᴄᴏᴍᴇ ʙᴀᴄᴋ\nᴀꜰᴛᴇʀ ʙᴇɪɴɢ ᴋɪʟʟᴇᴅ.",
                    Material.TOTEM_OF_UNDYING),
                SettingEntry.toggle("death.suppress-drops",   "ꜱᴜᴘᴘʀᴇꜱꜱ ᴅʀᴏᴘꜱ",
                    "ʙᴏᴛꜱ ᴅʀᴏᴘ ɴᴏ ɪᴛᴇᴍꜱ ᴏʀ xᴘ\nᴡʜᴇɴ ᴛʜᴇʏ ᴅɪᴇ.",
                    Material.HOPPER),
                SettingEntry.cycleDouble("combat.max-health", "ᴍᴀx ʜᴇᴀʟᴛʜ (½-ʜᴇᴀʀᴛꜱ)",
                    "ʙᴏᴛ ʙᴀꜱᴇ ʜᴇᴀʟᴛʜ. 20 = 10 ʜᴇᴀʀᴛꜱ.\nᴀᴘᴘʟɪᴇᴅ ᴀᴛ ꜱᴘᴀᴡɴ ᴀɴᴅ ᴏɴ /ꜰᴘᴘ ʀᴇʟᴏᴀᴅ.",
                    Material.GOLDEN_APPLE, new double[]{ 5, 10, 15, 20, 40 }),
                // ── Page 2 ────────────────────────────────────────────────────
                SettingEntry.cycleInt("death.respawn-delay",  "ʀᴇꜱᴘᴀᴡɴ ᴅᴇʟᴀʏ (ᴛɪᴄᴋꜱ)",
                    "ᴛɪᴄᴋꜱ ʙᴇꜰᴏʀᴇ ᴀ ᴅᴇᴀᴅ ʙᴏᴛ ʀᴇᴛᴜʀɴꜱ.\n1 = ɪɴꜱᴛᴀɴᴛ  ·  20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.CLOCK, new int[]{ 1, 5, 10, 15, 20, 40, 60, 100 })
            ));
    }

    private Category chat() {
        return new Category("💬 ᴄʜᴀᴛ",
            Material.WRITABLE_BOOK, Material.BOOK,
            Material.YELLOW_STAINED_GLASS_PANE,
            List.of(
                SettingEntry.toggle("fake-chat.enabled",                 "ꜰᴀᴋᴇ ᴄʜᴀᴛ",
                    "ʙᴏᴛꜱ ꜱᴇɴᴅ ʀᴀɴᴅᴏᴍ ᴍᴇꜱꜱᴀɢᴇꜱ\nꜰʀᴏᴍ ᴛʜᴇ ᴄᴏɴꜰɪɢᴜʀᴇᴅ ᴍᴇꜱꜱᴀɢᴇ ᴘᴏᴏʟ.",
                    Material.WRITABLE_BOOK),
                SettingEntry.toggle("fake-chat.require-player-online",   "ʀᴇQᴜɪʀᴇ ᴘʟᴀʏᴇʀ ᴏɴʟɪɴᴇ",
                    "ʙᴏᴛꜱ ᴏɴʟʏ ᴄʜᴀᴛ ᴡʜᴇɴ ᴀᴛ ʟᴇᴀꜱᴛ\nᴏɴᴇ ʀᴇᴀʟ ᴘʟᴀʏᴇʀ ɪꜱ ᴏɴ ᴛʜᴇ ꜱᴇʀᴠᴇʀ.",
                    Material.SPYGLASS),
                SettingEntry.toggle("fake-chat.typing-delay",            "ᴛʏᴘɪɴɢ ᴅᴇʟᴀʏ",
                    "ꜱɪᴍᴜʟᴀᴛᴇ ᴀ ᴘᴀᴜꜱᴇ ʙᴇꜰᴏʀᴇ ꜱᴇɴᴅɪɴɢ,\nʟɪᴋᴇ ᴀ ʀᴇᴀʟ ᴘʟᴀʏᴇʀ ᴡᴏᴜʟᴅ.",
                    Material.FEATHER),
                SettingEntry.toggle("fake-chat.reply-to-mentions",       "ʀᴇᴘʟʏ ᴛᴏ ᴍᴇɴᴛɪᴏɴꜱ",
                    "ʙᴏᴛꜱ ʀᴇꜱᴘᴏɴᴅ ᴡʜᴇɴ ᴀ ᴘʟᴀʏᴇʀ\nꜱᴀʏꜱ ᴛʜᴇɪʀ ɴᴀᴍᴇ ɪɴ ᴄʜᴀᴛ.",
                    Material.BELL),
                SettingEntry.toggle("fake-chat.activity-variation",      "ᴀᴄᴛɪᴠɪᴛʏ ᴠᴀʀɪᴀᴛɪᴏɴ",
                    "ᴀꜱꜱɪɢɴ ᴇᴀᴄʜ ʙᴏᴛ ᴀ ᴜɴɪQᴜᴇ ᴄʜᴀᴛ\nᴛɪᴇʀ — Qᴜɪᴇᴛ ᴛᴏ ᴄʜᴀᴛᴛʏ.",
                    Material.COMPARATOR),
                SettingEntry.toggle("fake-chat.event-triggers.enabled",  "ᴇᴠᴇɴᴛ ᴛʀɪɢɢᴇʀꜱ",
                    "ʙᴏᴛꜱ ʀᴇᴀᴄᴛ ᴛᴏ ᴘʟᴀʏᴇʀ ᴊᴏɪɴ,\nᴅᴇᴀᴛʜ, ᴀɴᴅ ʟᴇᴀᴠᴇ ᴇᴠᴇɴᴛꜱ.",
                    Material.REDSTONE_TORCH),
                SettingEntry.cycleDouble("fake-chat.chance",             "ᴄʜᴀᴛ ᴄʜᴀɴᴄᴇ (0–1)",
                    "ᴘʀᴏʙᴀʙɪʟɪᴛʏ ᴏꜰ ᴄʜᴀᴛᴛɪɴɢ\nᴏɴ ᴇᴀᴄʜ ɪɴᴛᴇʀᴠᴀʟ ᴄʜᴇᴄᴋ.",
                    Material.RABBIT_FOOT, new double[]{ 0.25, 0.50, 0.75, 1.0 }),
                SettingEntry.cycleInt("fake-chat.interval.min",          "ɪɴᴛᴇʀᴠᴀʟ — ᴍɪɴ (ꜱ)",
                    "ᴍɪɴɪᴍᴜᴍ ꜱᴇᴄᴏɴᴅꜱ ʙᴇᴛᴡᴇᴇɴ\nᴀ ʙᴏᴛ'ꜱ ᴄʜᴀᴛ ᴍᴇꜱꜱᴀɢᴇꜱ.",
                    Material.CLOCK, new int[]{ 5, 10, 20, 30, 60 }),
                SettingEntry.cycleInt("fake-chat.interval.max",          "ɪɴᴛᴇʀᴠᴀʟ — ᴍᴀx (ꜱ)",
                    "ᴍᴀxɪᴍᴜᴍ ꜱᴇᴄᴏɴᴅꜱ ʙᴇᴛᴡᴇᴇɴ\nᴀ ʙᴏᴛ'ꜱ ᴄʜᴀᴛ ᴍᴇꜱꜱᴀɢᴇꜱ.",
                    Material.CLOCK, new int[]{ 10, 20, 30, 60, 120 }),
                // ── Page 2 ────────────────────────────────────────────────────
                SettingEntry.toggle("fake-chat.keyword-reactions.enabled", "ᴋᴇʏᴡᴏʀᴅ ʀᴇᴀᴄᴛɪᴏɴꜱ",
                    "ʙᴏᴛꜱ ʀᴇᴀᴄᴛ ᴡʜᴇɴ ᴀ ᴘʟᴀʏᴇʀ'ꜱ\nᴍᴇꜱꜱᴀɢᴇ ᴄᴏɴᴛᴀɪɴꜱ ᴀ ᴛʀɪɢɢᴇʀ ᴡᴏʀᴅ.",
                    Material.BOOK),
                SettingEntry.cycleDouble("fake-chat.burst-chance",       "ʙᴜʀꜱᴛ ᴄʜᴀɴᴄᴇ (0–1)",
                    "ᴘʀᴏʙᴀʙɪʟɪᴛʏ ᴀ ʙᴏᴛ ꜱᴇɴᴅꜱ ᴀ\nQᴜɪᴄᴋ ꜰᴏʟʟᴏᴡ-ᴜᴘ ᴍᴇꜱꜱᴀɢᴇ.",
                    Material.PAPER, new double[]{ 0.0, 0.05, 0.10, 0.15, 0.25, 0.50 }),
                SettingEntry.cycleInt("fake-chat.stagger-interval",      "ᴄʜᴀᴛ ꜱᴛᴀɢɢᴇʀ (ꜱ)",
                    "ᴍɪɴɪᴍᴜᴍ ɢᴀᴘ ʙᴇᴛᴡᴇᴇɴ ᴀɴʏ ᴛᴡᴏ\nʙᴏᴛꜱ ᴄʜᴀᴛᴛɪɴɢ. 0 = ᴅɪꜱᴀʙʟᴇᴅ.",
                    Material.CLOCK, new int[]{ 0, 1, 2, 3, 5, 10 }),
                SettingEntry.cycleInt("fake-chat.history-size",          "ᴍᴇꜱꜱᴀɢᴇ ʜɪꜱᴛᴏʀʏ ꜱɪᴢᴇ",
                    "ʀᴇᴄᴇɴᴛ ᴍᴇꜱꜱᴀɢᴇꜱ ᴘᴇʀ ʙᴏᴛ ᴛʀᴀᴄᴋᴇᴅ\nᴛᴏ ᴀᴠᴏɪᴅ ʀᴇᴘᴇᴀᴛɪɴɢ. 0 = ᴏꜰꜰ.",
                    Material.KNOWLEDGE_BOOK, new int[]{ 0, 3, 5, 10, 15, 20 })
            ));
    }

    private Category swap() {
        return new Category("🔄 ꜱᴡᴀᴘ",
            Material.ENDER_PEARL, Material.CLOCK,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            List.of(
                SettingEntry.toggle("swap.enabled",             "ꜱᴡᴀᴘ ꜱʏꜱᴛᴇᴍ",
                    "ʙᴏᴛꜱ ᴘᴇʀɪᴏᴅɪᴄᴀʟʟʏ ʟᴇᴀᴠᴇ ᴀɴᴅ\nʀᴇ-ᴊᴏɪɴ, ꜱɪᴍᴜʟᴀᴛɪɴɢ ʀᴇᴀʟ ᴘʟᴀʏᴇʀꜱ.",
                    Material.ENDER_PEARL),
                SettingEntry.toggle("swap.farewell-chat",       "ꜰᴀʀᴇᴡᴇʟʟ ᴍᴇꜱꜱᴀɢᴇꜱ",
                    "ʙᴏᴛꜱ ꜱᴀʏ ɢᴏᴏᴅʙʏᴇ ʙᴇꜰᴏʀᴇ\nʟᴇᴀᴠɪɴɢ ᴛʜᴇ ꜱᴇʀᴠᴇʀ.",
                    Material.POPPY),
                SettingEntry.toggle("swap.greeting-chat",       "ɢʀᴇᴇᴛɪɴɢ ᴍᴇꜱꜱᴀɢᴇꜱ",
                    "ʙᴏᴛꜱ ɢʀᴇᴇᴛ ᴛʜᴇ ꜱᴇʀᴠᴇʀ\nᴡʜᴇɴ ᴛʜᴇʏ ʀᴇᴛᴜʀɴ.",
                    Material.DANDELION),
                SettingEntry.toggle("swap.same-name-on-rejoin", "ᴋᴇᴇᴘ ɴᴀᴍᴇ ᴏɴ ʀᴇᴊᴏɪɴ",
                    "ʙᴏᴛꜱ ᴛʀʏ ᴛᴏ ʀᴇᴄʟᴀɪᴍ ᴛʜᴇɪʀ\nᴏʀɪɢɪɴᴀʟ ɴᴀᴍᴇ ᴡʜᴇɴ ʀᴇᴛᴜʀɴɪɴɢ.",
                    Material.NAME_TAG),
                SettingEntry.cycleInt("swap.session.min",       "ꜱᴇꜱꜱɪᴏɴ — ᴍɪɴ (ꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ᴘᴏꜱꜱɪʙʟᴇ ᴛɪᴍᴇ ᴀ\nʙᴏᴛ ꜱᴛᴀʏꜱ ᴏɴʟɪɴᴇ.",
                    Material.CLOCK, new int[]{ 30, 60, 120, 300, 600 }),
                SettingEntry.cycleInt("swap.session.max",       "ꜱᴇꜱꜱɪᴏɴ — ᴍᴀx (ꜱ)",
                    "ʟᴏɴɢᴇꜱᴛ ᴘᴏꜱꜱɪʙʟᴇ ᴛɪᴍᴇ ᴀ\nʙᴏᴛ ꜱᴛᴀʏꜱ ᴏɴʟɪɴᴇ.",
                    Material.CLOCK, new int[]{ 60, 120, 300, 600, 1200 }),
                SettingEntry.cycleInt("swap.absence.min",       "ᴀʙꜱᴇɴᴄᴇ — ᴍɪɴ (ꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ᴛɪᴍᴇ ᴀ ʙᴏᴛ\nꜱᴘᴇɴᴅꜱ ᴏꜰꜰʟɪɴᴇ.",
                    Material.GRAY_DYE, new int[]{ 15, 30, 60, 120 }),
                SettingEntry.cycleInt("swap.absence.max",       "ᴀʙꜱᴇɴᴄᴇ — ᴍᴀx (ꜱ)",
                    "ʟᴏɴɢᴇꜱᴛ ᴛɪᴍᴇ ᴀ ʙᴏᴛ\nꜱᴘᴇɴᴅꜱ ᴏꜰꜰʟɪɴᴇ.",
                    Material.GRAY_DYE, new int[]{ 30, 60, 120, 300 }),
                SettingEntry.cycleInt("swap.max-swapped-out",   "ᴍᴀx ᴏꜰꜰʟɪɴᴇ ᴀᴛ ᴏɴᴄᴇ",
                    "ᴄᴀᴘ ᴏɴ ꜱɪᴍᴜʟᴛᴀɴᴇᴏᴜꜱʟʏ ᴀʙꜱᴇɴᴛ\nʙᴏᴛꜱ. 0 = ᴜɴʟɪᴍɪᴛᴇᴅ.",
                    Material.HOPPER, new int[]{ 0, 1, 2, 3, 5, 10 })
            ));
    }

    private Category peaks() {
        return new Category("⏰ ᴘᴇᴀᴋ ʜᴏᴜʀꜱ",
            Material.DAYLIGHT_DETECTOR, Material.COMPARATOR,
            Material.ORANGE_STAINED_GLASS_PANE,
            List.of(
                SettingEntry.toggle("peak-hours.enabled",            "ᴘᴇᴀᴋ ʜᴏᴜʀꜱ",
                    "ꜱᴄᴀʟᴇ ʙᴏᴛ ᴄᴏᴜɴᴛ ʙʏ ᴛɪᴍᴇ ᴡɪɴᴅᴏᴡ.\nʀᴇQᴜɪʀᴇꜱ ꜱᴡᴀᴘ ᴛᴏ ʙᴇ ᴇɴᴀʙʟᴇᴅ.",
                    Material.DAYLIGHT_DETECTOR),
                SettingEntry.toggle("peak-hours.notify-transitions", "ɴᴏᴛɪꜰʏ ᴛʀᴀɴꜱɪᴛɪᴏɴꜱ",
                    "ᴀʟᴇʀᴛ ꜰᴘᴘ.ᴘᴇᴀᴋꜱ ᴀᴅᴍɪɴꜱ ᴡʜᴇɴ\nᴛʜᴇ ᴀᴄᴛɪᴠᴇ ᴡɪɴᴅᴏᴡ ᴄʜᴀɴɢᴇꜱ.",
                    Material.BELL),
                SettingEntry.cycleInt("peak-hours.min-online",       "ᴍɪɴ ʙᴏᴛꜱ ᴏɴʟɪɴᴇ",
                    "ꜰʟᴏᴏʀ: ᴍɪɴɪᴍᴜᴍ ᴀᴄᴛɪᴠᴇ ʙᴏᴛꜱ\nʀᴇɢᴀʀᴅʟᴇꜱꜱ ᴏꜰ ꜰʀᴀᴄᴛɪᴏɴ. 0 = ᴏꜰꜰ.",
                    Material.COMPARATOR, new int[]{ 0, 1, 2, 5, 10 }),
                SettingEntry.cycleInt("peak-hours.stagger-seconds",  "ᴛʀᴀɴꜱɪᴛɪᴏɴ ꜱᴛᴀɢɢᴇʀ (ꜱ)",
                    "ꜱᴘʀᴇᴀᴅ ʙᴏᴛ ᴊᴏɪɴ/ʟᴇᴀᴠᴇ ᴇᴠᴇɴᴛꜱ\nᴀᴄʀᴏꜱꜱ ᴛʜɪꜱ ᴡɪɴᴅᴏᴡ ɪɴ ꜱᴇᴄᴏɴᴅꜱ.",
                    Material.CLOCK, new int[]{ 5, 10, 30, 60, 120 })
            ));
    }

    private Category pvp() {
        return new Category("⚔ ᴘᴠᴘ ʙᴏᴛ",
            Material.NETHERITE_SWORD, Material.IRON_SWORD,
            Material.RED_STAINED_GLASS_PANE,
            List.of(
                // ── Page 1 (slots 0-17) ───────────────────────────────────────
                SettingEntry.comingSoon("pvp-ai.difficulty",       "ᴅɪꜰꜰɪᴄᴜʟᴛʏ",
                    "ꜱᴇᴛ ᴛʜᴇ ʙᴏᴛ'ꜱ ꜱᴋɪʟʟ ʟᴇᴠᴇʟ.\nɴᴘᴄ / ᴇᴀꜱʏ / ᴍᴇᴅɪᴜᴍ / ʜᴀʀᴅ / ᴛɪᴇʀ1 / ʜᴀᴄᴋᴇʀ.",
                    Material.DIAMOND_SWORD),
                SettingEntry.comingSoon("pvp-ai.combat-mode",      "ᴄᴏᴍʙᴀᴛ ᴍᴏᴅᴇ",
                    "ꜱᴡɪᴛᴄʜ ʙᴇᴛᴡᴇᴇɴ ᴄʀʏꜱᴛᴀʟ ᴘᴠᴘ\nᴀɴᴅ ꜱᴡᴏʀᴅ ꜰɪɢʜᴛɪɴɢ ꜱᴛʏʟᴇ.",
                    Material.END_CRYSTAL),
                SettingEntry.comingSoon("pvp-ai.critting",         "ᴄʀɪᴛᴛɪɴɢ",
                    "ʙᴏᴛ ʟᴀɴᴅꜱ ᴄʀɪᴛɪᴄᴀʟ ʜɪᴛꜱ ʙʏ\nꜰᴀʟʟɪɴɢ ᴅᴜʀɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                    Material.NETHERITE_SWORD),
                SettingEntry.comingSoon("pvp-ai.s-tapping",        "ꜱ-ᴛᴀᴘᴘɪɴɢ",
                    "ʙᴏᴛ ᴛᴀᴘꜱ ꜱ ᴅᴜʀɪɴɢ ꜱᴡɪɴɢ\nᴛᴏ ʀᴇꜱᴇᴛ ᴀᴛᴛᴀᴄᴋ ᴄᴏᴏʟᴅᴏᴡɴ.",
                    Material.CLOCK),
                SettingEntry.comingSoon("pvp-ai.strafing",         "ꜱᴛʀᴀꜰɪɴɢ",
                    "ʙᴏᴛ ᴄɪʀᴄʟᴇꜱ ᴀʀᴏᴜɴᴅ ᴛʜᴇ ᴛᴀʀɢᴇᴛ\nᴡʜɪʟᴇ ꜰɪɢʜᴛɪɴɢ.",
                    Material.FEATHER),
                SettingEntry.comingSoon("pvp-ai.shield",           "ꜱʜɪᴇʟᴅɪɴɢ",
                    "ʙᴏᴛ ᴄᴀʀʀɪᴇꜱ ᴀɴᴅ ᴜꜱᴇꜱ ᴀ ꜱʜɪᴇʟᴅ\nᴛᴏ ʙʟᴏᴄᴋ ɪɴᴄᴏᴍɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                    Material.SHIELD),
                SettingEntry.comingSoon("pvp-ai.speed-buffs",      "ꜱᴘᴇᴇᴅ ʙᴜꜰꜰꜱ",
                    "ʙᴏᴛ ʜᴀꜱ ꜱᴘᴇᴇᴅ & ꜱᴛʀᴇɴɢᴛʜ ᴘᴏᴛɪᴏɴ\nᴇꜰꜰᴇᴄᴛꜱ ᴀᴄᴛɪᴠᴇ.",
                    Material.SUGAR),
                SettingEntry.comingSoon("pvp-ai.jump-reset",       "ᴊᴜᴍᴘ ʀᴇꜱᴇᴛ",
                    "ʙᴏᴛ ᴊᴜᴍᴘꜱ ᴊᴜꜱᴛ ʙᴇꜰᴏʀᴇ ꜱᴡɪɴɢɪɴɢ\nᴛᴏ ɢᴀɪɴ ᴛʜᴇ W-ᴛᴀᴘ ᴋɴᴏᴄᴋʙᴀᴄᴋ ʙᴏɴᴜꜱ.",
                    Material.SLIME_BALL),
                SettingEntry.comingSoon("pvp-ai.random",           "ʀᴀɴᴅᴏᴍ ᴘʟᴀʏꜱᴛʏʟᴇ",
                    "ʀᴀɴᴅᴏᴍɪꜱᴇ ᴛᴇᴄʜɴɪQᴜᴇꜱ ᴇᴀᴄʜ ʀᴏᴜɴᴅ\nᴛᴏ ᴋᴇᴇᴘ ᴛʜᴇ ꜰɪɢʜᴛ ᴜɴᴘʀᴇᴅɪᴄᴛᴀʙʟᴇ.",
                    Material.COMPARATOR),
                SettingEntry.comingSoon("pvp-ai.gear",             "ɢᴇᴀʀ ᴛʏᴘᴇ",
                    "ʙᴏᴛ ᴡᴇᴀʀꜱ ᴅɪᴀᴍᴏɴᴅ ᴏʀ\nɴᴇᴛʜᴇʀɪᴛᴇ ᴀʀᴍᴏᴜʀ.",
                    Material.DIAMOND_CHESTPLATE),
                SettingEntry.comingSoon("pvp-ai.defensive-mode",   "ᴅᴇꜰᴇɴꜱɪᴠᴇ ᴍᴏᴅᴇ",
                    "ʙᴏᴛ ᴏɴʟʏ ꜰɪɢʜᴛꜱ ʙᴀᴄᴋ ᴡʜᴇɴ\nᴛʜᴇ ᴘʟᴀʏᴇʀ ᴀᴛᴛᴀᴄᴋꜱ ꜰɪʀꜱᴛ.",
                    Material.BOW),
                SettingEntry.comingSoon("pvp-ai.detect-range",     "ᴅᴇᴛᴇᴄᴛ ʀᴀɴɢᴇ",
                    "ʜᴏᴡ ꜰᴀʀ ᴛʜᴇ ʙᴏᴛ ꜱᴇᴇꜱ ᴘʟᴀʏᴇʀꜱ\nᴀɴᴅ ʟᴏᴄᴋꜱ ᴏɴ ᴀꜱ ᴛᴀʀɢᴇᴛ.",
                    Material.SPYGLASS),
                SettingEntry.comingSoon("pvp-ai.sprint",           "ꜱᴘʀɪɴᴛɪɴɢ",
                    "ʙᴏᴛ ꜱᴘʀɪɴᴛꜱ ᴛᴏᴡᴀʀᴅꜱ ᴛʜᴇ ᴛᴀʀɢᴇᴛ\nᴅᴜʀɪɴɢ ᴄᴏᴍʙᴀᴛ.",
                    Material.GOLDEN_BOOTS),
                SettingEntry.comingSoon("pvp-ai.pearl",            "ᴇɴᴅᴇʀ ᴘᴇᴀʀʟ",
                    "ʙᴏᴛ ᴛʜʀᴏᴡꜱ ᴇɴᴅᴇʀ ᴘᴇᴀʀʟꜱ ᴛᴏ\nᴄʟᴏꜱᴇ ᴛʜᴇ ɢᴀᴘ ᴏʀ ᴇꜱᴄᴀᴘᴇ.",
                    Material.ENDER_PEARL),
                SettingEntry.comingSoon("pvp-ai.pearl-spam",       "ᴘᴇᴀʀʟ ꜱᴘᴀᴍ",
                    "ʙᴏᴛ ꜱᴘᴀᴍꜱ ᴘᴇᴀʀʟꜱ ɪɴ ʙᴜʀꜱᴛꜱ\nꜰᴏʀ ᴀɢɢʀᴇꜱꜱɪᴠᴇ ɢᴀᴘ-ᴄʟᴏꜱɪɴɢ.",
                    Material.ENDER_EYE),
                SettingEntry.comingSoon("pvp-ai.walk-backwards",   "ᴡᴀʟᴋ ʙᴀᴄᴋᴡᴀʀᴅꜱ",
                    "ʙᴏᴛ ʙᴀᴄᴋꜱ ᴀᴡᴀʏ ᴡʜɪʟᴇ ꜱᴡɪɴɢɪɴɢ\nᴛᴏ ᴄᴏɴᴛʀᴏʟ ᴋɴᴏᴄᴋʙᴀᴄᴋ.",
                    Material.LEATHER_BOOTS),
                SettingEntry.comingSoon("pvp-ai.hole-mode",        "ʜᴏʟᴇ ᴍᴏᴅᴇ",
                    "ʙᴏᴛ ᴘᴀᴛʜꜰɪɴᴅꜱ ᴛᴏ ᴀɴ ᴏʙꜱɪᴅɪᴀɴ\nʜᴏʟᴇ ᴛᴏ ᴘʀᴏᴛᴇᴄᴛ ɪᴛꜱᴇʟꜰ.",
                    Material.OBSIDIAN),
                SettingEntry.comingSoon("pvp-ai.kit",              "ᴋɪᴛ ᴘʀᴇꜱᴇᴛ",
                    "ꜱᴇʟᴇᴄᴛ ᴛʜᴇ ʙᴏᴛ'ꜱ ʟᴏᴀᴅᴏᴜᴛ.\nᴋɪᴛ1 / ᴋɪᴛ2 / ᴋɪᴛ3 / ᴋɪᴛ4.",
                    Material.CHEST),
                // ── Page 2 (slots 0-2) ────────────────────────────────────────
                SettingEntry.comingSoon("pvp-ai.auto-refill",      "ᴀᴜᴛᴏ-ʀᴇꜰɪʟʟ ᴛᴏᴛᴇᴍ",
                    "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇ-ᴇQᴜɪᴘꜱ ᴀ\nᴛᴏᴛᴇᴍ ᴀꜰᴛᴇʀ ᴘᴏᴘᴘɪɴɢ ᴏɴᴇ.",
                    Material.TOTEM_OF_UNDYING),
                SettingEntry.comingSoon("pvp-ai.auto-respawn",     "ᴀᴜᴛᴏ-ʀᴇꜱᴘᴀᴡɴ",
                    "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇꜱᴘᴀᴡɴꜱ\nᴀɴᴅ ʀᴇᴊᴏɪɴꜱ ᴀꜰᴛᴇʀ ᴅᴇᴀᴛʜ.",
                    Material.RESPAWN_ANCHOR),
                SettingEntry.comingSoon("pvp-ai.spawn-protection", "ꜱᴘᴀᴡɴ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ",
                    "ʙᴏᴛ ꜱᴛᴀʏꜱ ɪɴᴠᴜʟɴᴇʀᴀʙʟᴇ ꜰᴏʀ\nᴀ ꜱʜᴏʀᴛ ɢʀᴀᴄᴇ ᴘᴇʀɪᴏᴅ ᴀᴛ ꜱᴘᴀᴡɴ.",
                    Material.GRASS_BLOCK)
            ));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Inner types
    // ═════════════════════════════════════════════════════════════════════════

    /** Tags our inventories so the event handler can identify them quickly. */
    private static final class GuiHolder implements InventoryHolder {
        final UUID uuid;
        GuiHolder(UUID uuid) { this.uuid = uuid; }
        @SuppressWarnings("NullableProblems")
        @Override public Inventory getInventory() { return null; }
    }

    private record Category(
        String label, Material activeMat, Material inactiveMat,
        Material separatorGlass, List<SettingEntry> settings
    ) {}

    private enum SettingType { TOGGLE, CYCLE_INT, CYCLE_DOUBLE, COMING_SOON }

    /**
     * Tracks an in-progress chat-input session for a single player.
     *
     * @param entry         the setting being edited
     * @param guiState      [categoryIndex, pageIndex] to restore when done
     * @param cleanupTaskId Bukkit task ID for the 60-second safety cleanup
     */
    private record ChatInputSession(
        SettingEntry entry,
        int[]        guiState,
        int          cleanupTaskId
    ) {}

    private static final class SettingEntry {
        final String      configKey;
        final String      label;
        final String      description;
        final Material    icon;
        final SettingType type;
        final int[]       intValues;
        final double[]    dblValues;

        private SettingEntry(String configKey, String label, String description,
                             Material icon, SettingType type,
                             int[] intValues, double[] dblValues) {
            this.configKey   = configKey;
            this.label       = label;
            this.description = description;
            this.icon        = icon;
            this.type        = type;
            this.intValues   = intValues;
            this.dblValues   = dblValues;
        }

        static SettingEntry toggle(String key, String label, String desc, Material icon) {
            return new SettingEntry(key, label, desc, icon,
                    SettingType.TOGGLE, null, null);
        }

        static SettingEntry cycleInt(String key, String label, String desc,
                                     Material icon, int[] values) {
            return new SettingEntry(key, label, desc, icon,
                    SettingType.CYCLE_INT, values, null);
        }

        static SettingEntry cycleDouble(String key, String label, String desc,
                                        Material icon, double[] values) {
            return new SettingEntry(key, label, desc, icon,
                    SettingType.CYCLE_DOUBLE, null, values);
        }

        /** Creates a locked "coming soon" entry — clicking plays ENTITY_VILLAGER_NO. */
        static SettingEntry comingSoon(String key, String label, String desc, Material icon) {
            return new SettingEntry(key, label, desc, icon,
                    SettingType.COMING_SOON, null, null);
        }

        String currentValueString(FakePlayerPlugin plugin) {
            var cfg = plugin.getConfig();
            return switch (type) {
                case TOGGLE       -> cfg.getBoolean(configKey, false) ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
                case CYCLE_INT    -> String.valueOf(cfg.getInt(configKey, intValues[0]));
                case CYCLE_DOUBLE -> {
                    double d = cfg.getDouble(configKey, dblValues[0]);
                    yield (d == Math.floor(d) && !Double.isInfinite(d))
                            ? String.valueOf((int) d)
                            : String.format("%.2f", d);
                }
                case COMING_SOON  -> "⚠ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ";
            };
        }

        /** Flips the boolean config value in place.  Used only for TOGGLE entries. */
        void apply(FakePlayerPlugin plugin) {
            if (type == SettingType.TOGGLE) {
                plugin.getConfig().set(configKey, !plugin.getConfig().getBoolean(configKey, false));
            }
        }
    }
}



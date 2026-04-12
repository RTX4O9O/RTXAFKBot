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

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;

import java.util.*;

/**
 * Interactive settings GUI - opens a 6-row (double) chest that lets admins
 * toggle/cycle plugin configuration values without editing {@code config.yml}
 * directly.
 *
 * <h3>Layout (6 rows / 54 slots)</h3>
 * <pre>
 *  [S0 ][S1 ][S2 ][S3 ][S4 ][S5 ][S6 ][S7 ][S8 ]   ← row 0: settings  0-8
 *  [S9 ][S10][S11][S12][S13][S14][S15][S16][S17]   ← row 1: settings  9-17
 *  [S18][S19][S20][S21][S22][S23][S24][S25][S26]   ← row 2: settings 18-26
 *  [S27][S28][S29][S30][S31][S32][S33][S34][S35]   ← row 3: settings 27-35
 *  [S36][S37][S38][S39][S40][S41][S42][S43][S44]   ← row 4: settings 36-44
 *  [⟲  ][◄  ][C1 ][C2 ][C3 ][C4 ][C5 ][▶  ][ ✕]  ← row 5: reset|←cat|5 cats|→cat|close
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
    // Double chest: 6 rows × 9 cols = 54 slots.
    // Settings area: rows 0-4, all 9 cols → 45 slots per page (slots 0-44).
    // Bottom row (row 5, slots 45-53):
    //   45=⟲reset-all  46=◄scroll-cats  47-51=5 visible cat tabs  52=▶scroll-cats  53=✕close
    private static final int SIZE              = 54;
    private static final int SETTINGS_PER_PAGE = 45;   // 5 rows × 9 cols
    private static final int SLOT_RESET        = 45;   // ⟲ Reset All
    private static final int SLOT_CAT_PREV     = 46;   // ◄ scroll category window left
    private static final int SLOT_CAT_NEXT     = 52;   // ▶ scroll category window right
    private static final int SLOT_CLOSE        = 53;   // ✕ Save & Close
    /** Number of category tabs visible at once in the bottom row. */
    private static final int CAT_WINDOW        = 5;
    /** First inventory slot of the 5-slot category window (slots 47-51). */
    private static final int CAT_WINDOW_START  = 47;

    // ── Owner skull cache  (Skin System entry icon) ───────────────────────────
    /** UUID of El_Pepes - the owner whose head is shown on the Skin System entry. */
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

    /** Per-player GUI state: [categoryIndex, pageIndex, catWindowOffset]. */
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
     * on the previous one - we don't want to treat that as a real "user closed").
     */
    private final Set<UUID> pendingRebuild = new HashSet<>();

    // ── Category definitions ──────────────────────────────────────────────────
    private final Category[] categories;

    public SettingGui(FakePlayerPlugin plugin) {
        this.plugin     = plugin;
        this.categories = new Category[]{
            general(), body(), chat(), swap(), peaks(), pvp(), pathfinding()
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Public API
    // ═════════════════════════════════════════════════════════════════════════

    /** Opens the settings GUI for {@code player} at the General category. */
    public void open(Player player) {
        sessions.put(player.getUniqueId(), new int[]{ 0, 0, 0 });   // [catIdx, pageIdx, catOffset]
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

        int slot      = event.getSlot();
        int catIdx    = state[0];
        int pageIdx   = state[1];
        int catOffset = state[2];

        // ── ⟲ Reset All (slot 45) ────────────────────────────────────────
        if (slot == SLOT_RESET) {
            playUiClick(player, 0.6f);
            resetAllCategories(player);
            return;
        }
        // ── ◄ Scroll category window left (slot 46) ───────────────────────
        if (slot == SLOT_CAT_PREV) {
            if (catOffset > 0) {
                playUiClick(player, 1.0f);
                state[2]--;
            }
            build(player);
            return;
        }
        // ── ▶ Scroll category window right (slot 52) ──────────────────────
        if (slot == SLOT_CAT_NEXT) {
            if (catOffset + CAT_WINDOW < categories.length) {
                playUiClick(player, 1.0f);
                state[2]++;
            }
            build(player);
            return;
        }
        // ── ✕ Close (slot 53) ────────────────────────────────────────────
        if (slot == SLOT_CLOSE) {
            playUiClick(player, 0.8f);
            player.closeInventory();
            return;
        }
        // ── Category tabs (slots 47-51) ───────────────────────────────────
        if (slot >= CAT_WINDOW_START && slot < CAT_WINDOW_START + CAT_WINDOW) {
            int ci = catOffset + (slot - CAT_WINDOW_START);
            if (ci < categories.length) {
                if (ci != catIdx) playUiClick(player, 1.3f);
                state[0] = ci;
                state[1] = 0;   // reset page when switching categories
                build(player);
            }
            return;
        }
        // ── Settings (slots 0-44) ─────────────────────────────────────────
        int settingIdx = slotToSettingIdx(slot);
        if (settingIdx >= 0) {
            List<SettingEntry> settings = categories[catIdx].settings;
            int entryIdx = pageIdx * SETTINGS_PER_PAGE + settingIdx;
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
                    .append(Component.text("- ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ").color(COMING_SOON_COLOR).decoration(TextDecoration.BOLD, true)));
                return;
            }

            if (entry.type == SettingType.TOGGLE) {
                entry.apply(plugin);
                plugin.saveConfig();
                Config.reload();
                applyLiveEffect(entry.configKey);
                String newVal = entry.currentValueString(plugin);
                playUiClick(player, newVal.startsWith("✔") ? 1.2f : 0.85f);
                sendActionBarConfirm(player, entry.label, newVal);
                build(player);
            } else {
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

        // Send "Settings saved" confirmation - but not on player disconnect
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
                    .append(Component.text("ᴄᴀɴᴄᴇʟʟᴇᴅ - ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ.").color(GRAY)));
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
     * {@link #onPlayerChat} - other players never see the raw value.
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
        pendingChatInput.remove(uuid);   // clear immediately - session kept in `sessions`

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
                        .append(Component.text("ɪɴᴘᴜᴛ ᴛɪᴍᴇᴅ ᴏᴜᴛ - ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ.").color(GRAY)));
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

        int catIdx    = state[0];
        int pageIdx   = state[1];
        int catOffset = state[2];
        Category cat  = categories[catIdx];

        GuiHolder holder = new GuiHolder(uuid);
        Component title = Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("] ").color(DARK_GRAY))
            .append(Component.text(cat.label).color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);

        // ── Settings area: rows 0-4, all 9 cols (slots 0-44) ─────────────────
        int settingsCount = cat.settings.size();
        int totalPages    = totalPagesForCat(cat);
        pageIdx = Math.min(pageIdx, Math.max(0, totalPages - 1));
        state[1] = pageIdx;

        int startIdx = pageIdx * SETTINGS_PER_PAGE;
        int endIdx   = Math.min(startIdx + SETTINGS_PER_PAGE, settingsCount);
        for (int i = startIdx; i < endIdx; i++) {
            inv.setItem(i - startIdx, buildSettingItem(cat.settings.get(i)));
        }

        // ── Bottom row (slots 45-53) ──────────────────────────────────────────
        // 45: ⟲ Reset All
        inv.setItem(SLOT_RESET, buildResetAllButton());

        // 46: ◄ cat-prev arrow (only when catOffset > 0)
        inv.setItem(SLOT_CAT_PREV,
            catOffset > 0
                ? buildCatArrow(false)
                : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        // 47-51: 5 category tabs from catOffset
        for (int i = 0; i < CAT_WINDOW; i++) {
            int ci = catOffset + i;
            inv.setItem(CAT_WINDOW_START + i,
                ci < categories.length
                    ? buildCategoryTab(ci, ci == catIdx)
                    : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        // 52: ▶ cat-next arrow (only when more cats exist beyond the window)
        inv.setItem(SLOT_CAT_NEXT,
            catOffset + CAT_WINDOW < categories.length
                ? buildCatArrow(true)
                : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        // 53: ✕ Close
        inv.setItem(SLOT_CLOSE, buildCloseButton());

        // Mark as rebuild so the InventoryCloseEvent fired by openInventory is ignored
        pendingRebuild.add(uuid);
        player.openInventory(inv);
        pendingRebuild.remove(uuid);
        sessions.put(uuid, state);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Slot ↔ setting-index helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Maps an inventory slot to its 0-based setting index within the current page.
     * Settings occupy slots 0-44 (rows 0-4, all 9 cols) - slot IS the index.
     * Returns {@code -1} for the bottom row (slots 45-53).
     */
    private static int slotToSettingIdx(int slot) {
        return slot < 45 ? slot : -1;
    }

    /**
     * Inverse: maps a 0-based local setting index to the inventory slot.
     * With the full-9-col layout slot == index for rows 0-4.
     */
    private static int settingIdxToSlot(int localIdx) {
        return localIdx;   // slots 0-44 map 1:1
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

        // Enabled toggles sparkle with an enchant glow - immediately recognisable
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
        Category  cat  = categories[idx];
        Material  mat  = active ? cat.activeMat : cat.inactiveMat;
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
            .append(Component.text(active ? "◈  ᴄᴜʀʀᴇɴᴛʟʏ ᴠɪᴇᴡɪɴɢ" : "ᴄʟɪᴄᴋ ᴛᴏ ꜱᴡɪᴛᴄʜ")
                .color(active ? ON_GREEN : DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    /** ◄ / ▶ arrows for scrolling the 5-slot category window. */
    private ItemStack buildCatArrow(boolean isNext) {
        Material  mat   = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
        String    label = isNext ? "▶" : "◄";
        TextColor col   = isNext ? ON_GREEN : COMING_SOON_COLOR;
        ItemStack item  = new ItemStack(mat);
        ItemMeta  meta  = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(label).color(col).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ꜱᴄʀᴏʟʟ ᴄᴀᴛᴇɢᴏʀɪᴇꜱ " + (isNext ? "ꜰᴏʀᴡᴀʀᴅ" : "ʙᴀᴄᴋᴡᴀʀᴅ") + ".").color(DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the number of pages required to display all settings in a category. */
    private static int totalPagesForCat(Category cat) {
        return Math.max(1, (int) Math.ceil(cat.settings.size() / (double) SETTINGS_PER_PAGE));
    }

    /** ⟲ Reset All - resets every setting across ALL categories. */
    private ItemStack buildResetAllButton() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲  ʀᴇꜱᴇᴛ ᴀʟʟ").color(YELLOW).decoration(TextDecoration.BOLD, false)));
        meta.lore(List.of(
            Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ʀᴇꜱᴇᴛ ᴇᴠᴇʀʏ ꜱᴇᴛᴛɪɴɢ ᴀᴄʀᴏꜱꜱ").color(GRAY)),
            Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ᴀʟʟ ᴄᴀᴛᴇɢᴏʀɪᴇꜱ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛꜱ.").color(GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    /** ✕ Close - saves & closes the settings chest. */
    private ItemStack buildCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("✕  ᴄʟᴏꜱᴇ").color(OFF_RED).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ꜱᴀᴠᴇ & ᴄʟᴏꜱᴇ ᴛʜᴇ ꜱᴇᴛᴛɪɴɢꜱ ᴍᴇɴᴜ.").color(DARK_GRAY))));
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
        // Kick off an async Mojang profile update - next render will get the fresh skin
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
                // Network unavailable or Mojang rate-limit - keep the old cache
            }
        });
    }

    /**
     * Resets every setting in every category to JAR defaults.
     */
    private void resetAllCategories(Player player) {
        var cfg      = plugin.getConfig();
        var defaults = cfg.getDefaults();
        for (Category cat : categories) {
            for (SettingEntry entry : cat.settings) {
                switch (entry.type) {
                    case TOGGLE -> cfg.set(entry.configKey,
                            defaults != null ? defaults.getBoolean(entry.configKey, false) : false);
                    case CYCLE_INT -> cfg.set(entry.configKey,
                            defaults != null ? defaults.getInt(entry.configKey, entry.intValues[0]) : entry.intValues[0]);
                    case CYCLE_DOUBLE -> cfg.set(entry.configKey,
                            defaults != null ? defaults.getDouble(entry.configKey, entry.dblValues[0]) : entry.dblValues[0]);
                    default -> { /* COMING_SOON - skip */ }
                }
            }
        }
        plugin.saveConfig();
        Config.reload();
        for (Category cat : categories) {
            for (SettingEntry entry : cat.settings) {
                applyLiveEffect(entry.configKey);
            }
        }
        build(player);
        player.sendActionBar(Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲ ").color(YELLOW))
            .append(Component.text("ᴀʟʟ ꜱᴇᴛᴛɪɴɢꜱ  ").color(WHITE).decoration(TextDecoration.BOLD, false))
            .append(Component.text("ʀᴇꜱᴇᴛ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛꜱ").color(YELLOW).decoration(TextDecoration.BOLD, true)));
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

        // ── Body: damageable / pushable / max-health / pick-up-items ─────────
        if (configKey.equals("body.enabled") || configKey.equals("body.pushable")
                || configKey.equals("body.damageable") || configKey.equals("combat.max-health")) {
            if (fpm != null) fpm.applyBodyConfig();
            return;
        }

        // ── Body: pick-up-items toggled OFF → drop everything bots are holding ──
        if (configKey.equals("body.pick-up-items")) {
            boolean enabled = plugin.getConfig().getBoolean("body.pick-up-items", false);
            if (fpm != null) {
                // Sync NMS-level pickup flag on every active bot so the global change
                // is reflected immediately without waiting for a respawn.
                fpm.getActivePlayers().forEach(fp -> {
                    Player body = fp.getPlayer();
                    if (body != null) body.setCanPickupItems(enabled && fp.isPickUpItemsEnabled());
                });
                if (!enabled) {
                    fpm.getActivePlayers().forEach(this::dropBotInventoryWithAnimation);
                }
            }
            return;
        }

        // ── Body: pick-up-xp toggled OFF → drop all bots' current XP as orbs ──
        if (configKey.equals("body.pick-up-xp")) {
            boolean enabled = plugin.getConfig().getBoolean("body.pick-up-xp", true);
            if (!enabled && fpm != null) {
                fpm.getActivePlayers().forEach(fp -> {
                    Player bot = fp.getPlayer();
                    if (bot == null || !bot.isOnline()) return;
                    int xp = bot.getTotalExperience();
                    if (xp <= 0) return;
                    World world = bot.getWorld();
                    Location loc = bot.getLocation();
                    world.spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));
                    bot.setTotalExperience(0);
                    bot.setLevel(0);
                    bot.setExp(0f);
                });
            }
            return;
        }

        // ── Tab-list visibility ───────────────────────────────────────────────
        if (configKey.equals("tab-list.enabled")) {
            if (plugin.getTabListManager() != null) plugin.getTabListManager().reload();
            if (fpm != null) fpm.applyTabListConfig();
            return;
        }

        // ── Chat AI - any fake-chat.* change restarts loops so new values
        //    (interval, chance, stagger, etc.) take effect immediately ─────────
        if (configKey.startsWith("fake-chat.")) {
            var chatAI = plugin.getBotChatAI();
            if (chatAI != null) {
                if (Config.fakeChatEnabled()) chatAI.restartLoops();
                else chatAI.cancelAll();
            }
            return;
        }

        // ── Swap AI - cancel all pending timers and reschedule if swap is on ──
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

        // ── Peak hours - wakes sleeping bots then re-evaluates window ─────────
        if (configKey.startsWith("peak-hours.")) {
            var phm = plugin.getPeakHoursManager();
            if (phm != null) phm.reload();
        }
    }

    /**
     * When the pick-up-items toggle is turned OFF, makes the bot look at the
     * ground, drops every item it currently holds into the world naturally,
     * then restores its original head direction.
     *
     * <p>Sequence (runs on the main thread):
     * <ol>
     *   <li>Look down (pitch = 90°)</li>
     *   <li>3 ticks later - drop all items and clear the inventory</li>
     *   <li>5 ticks after that - restore original look direction</li>
     * </ol>
     */
    private void dropBotInventoryWithAnimation(FakePlayer fp) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;

        // Quick early-out - nothing to drop
        boolean hasItems = false;
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                hasItems = true;
                break;
            }
        }
        if (!hasItems) return;

        Location loc       = bot.getLocation();
        float    origYaw   = loc.getYaw();
        float    origPitch = loc.getPitch();

        // 1. Look down immediately
        bot.setRotation(origYaw, 90f);
        NmsPlayerSpawner.setHeadYaw(bot, origYaw);

        // 2. Drop items after a small delay so the animation is visible
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player b = fp.getPlayer();
            if (b == null || !b.isOnline()) return;

            ItemStack[] contents = b.getInventory().getContents().clone();
            b.getInventory().clear();
            for (ItemStack item : contents) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    b.getWorld().dropItemNaturally(b.getLocation(), item);
                }
            }

            // 3. Restore original look direction
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player b2 = fp.getPlayer();
                if (b2 == null || !b2.isOnline()) return;
                b2.setRotation(origYaw, origPitch);
                NmsPlayerSpawner.setHeadYaw(b2, origYaw);
            }, 5L);
        }, 3L);
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
     *   <li>~1.2 - toggle ON (bright, positive)</li>
     *   <li>~0.85 - toggle OFF (muted, neutral)</li>
     *   <li>~1.3 - category tab switch (light tap)</li>
     *   <li>~1.0 - numeric input prompt (neutral)</li>
     *   <li>~0.8 - close button</li>
     *   <li>~0.6 - reset button (heavier)</li>
     * </ul>
     */
    private static void playUiClick(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, pitch);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Category definitions
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
                SettingEntry.cycleInt("join-delay.min",        "ᴊᴏɪɴ ᴅᴇʟᴀʏ - ᴍɪɴ (ᴛɪᴄᴋꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ᴊᴏɪɴꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.FEATHER, new int[]{ 0, 5, 10, 20, 40, 100 }),
                SettingEntry.cycleInt("join-delay.max",        "ᴊᴏɪɴ ᴅᴇʟᴀʏ - ᴍᴀx (ᴛɪᴄᴋꜱ)",
                    "ʟᴏɴɡᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ᴊᴏɪɴꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.FEATHER, new int[]{ 0, 5, 10, 20, 40, 100 }),
                SettingEntry.cycleInt("leave-delay.min",       "ʟᴇᴀᴠᴇ ᴅᴇʟᴀʏ - ᴍɪɴ (ᴛɪᴄᴋꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ʟᴇᴀᴠᴇꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.GRAY_DYE, new int[]{ 0, 5, 10, 20, 40, 100 }),
                SettingEntry.cycleInt("leave-delay.max",       "ʟᴇᴀᴠᴇ ᴅᴇʟᴀʏ - ᴍᴀx (ᴛɪᴄᴋꜱ)",
                    "ʟᴏɴɡᴇꜱᴛ ʀᴀɴᴅᴏᴍ ᴅᴇʟᴀʏ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ʟᴇᴀᴠᴇꜱ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
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
                    "ᴄᴜꜱᴛᴏᴍ ꜱᴋɪɴꜱ ꜰᴏʀ ʙᴏᴛꜱ.\nᴛʜɪꜱ ꜰ꜇ᴀᴛᴜʀᴇ ɪꜱ ɪɴ ᴅᴇᴠᴇʟᴏᴘᴍᴇɴᴛ.",
                    Material.PLAYER_HEAD),
                SettingEntry.toggle("body.pushable",          "ᴘᴜꜱʜᴀʙʟᴇ",
                    "ᴀʟʟᴏᴡ ᴘʟᴀʏᴇʀꜱ ᴀɴᴅ ᴇɴᴛɪᴛɪᴇꜱ\nᴛᴏ ᴘᴜꜱʜ ʙᴏᴛ ʙᴏᴅɪᴇꜱ.",
                    Material.PISTON),
                SettingEntry.toggle("body.damageable",        "ᴅᴀᴍᴀɢᴇᴀʙʟᴇ",
                    "ʙᴏᴛꜱ ᴛᴀᴋᴇ ᴘʟᴀʏᴇʀ/ᴇɴᴛɪᴛʏ ᴅᴀᴍᴀɢᴇ.\nꜰᴀʟꜱᴇ = ɪᴍᴍᴜɴᴇ ᴛᴏ ᴘᴠᴘ/ᴍᴏʙꜱ ᴏɴʟʏ.",
                    Material.IRON_SWORD),
                SettingEntry.toggle("body.pick-up-items",     "ᴘɪᴄᴋ ᴜᴘ ɪᴛᴇᴍꜱ",
                    "ʙᴏᴛꜱ ᴘɪᴄᴋ ᴜᴘ ɪᴛᴇᴍꜱ ꜰʀᴏᴍ ᴛʜᴇ ɢʀᴏᴜɴᴅ\nʟɪᴋᴇ ᴀ ʀᴇᴀʟ ᴘʟᴀʏᴇʀ.",
                    Material.HOPPER),
                SettingEntry.toggle("body.pick-up-xp",        "ᴘɪᴄᴋ ᴜᴘ xᴘ",
                    "ʙᴏᴛꜱ ᴄᴏʟʟᴇᴄᴛ ᴇxᴘᴇʀɪᴇɴᴄᴇ ᴏʀʙꜱ\nꜰʀᴏᴍ ᴛʜᴇ ɢʀᴏᴜɴᴅ.",
                    Material.EXPERIENCE_BOTTLE),
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
                    Material.CHEST),
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
                    "ʙᴏᴛꜱ ꜱᴇɴᴅ ʀᴀɴᴅᴏᴍ ᴍᴇꜱꜱᴀɡᴇꜱ\nꜰʀᴏᴍ ᴛʜᴇ ᴄᴏɴꜰɪɡᴜʀᴇᴅ ᴍᴇꜱꜱᴀɡᴇ ᴘᴏᴏʟ.",
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
                    "ᴀꜱꜱɪɢɴ ᴇᴀᴄʜ ʙᴏᴛ ᴀ ᴜɴɪQᴜᴇ ᴄʜᴀᴛ\nᴛɪᴇʀ - Qᴜɪᴇᴛ ᴛᴏ ᴄʜᴀᴛᴛʏ.",
                    Material.COMPARATOR),
                SettingEntry.toggle("fake-chat.event-triggers.enabled",  "ᴇᴠᴇɴᴛ ᴛʀɪɡɡᴇʀꜱ",
                    "ʙᴏᴛꜱ ʀᴇᴀᴄᴛ ᴛᴏ ᴘʟᴀʏᴇʀ ᴊᴏɪɴ,\nᴅᴇᴀᴛʜ, ᴀɴᴅ ʟᴇᴀᴠᴇ ᴇᴠᴇɴᴛꜱ.",
                    Material.REDSTONE_TORCH),
                SettingEntry.cycleDouble("fake-chat.chance",             "ᴄʜᴀᴛ ᴄʜᴀɴᴄᴇ (0–1)",
                    "ᴘʀᴏʙᴀʙɪʟɪᴛʏ ᴏꜱ ᴄʜᴀᴛᴛɪɴɢ\nᴏɴ ᴇᴀᴄʜ ɪɴᴛᴇʀᴠᴀʟ ᴄʜᴇᴄᴋ.",
                    Material.RABBIT_FOOT, new double[]{ 0.25, 0.50, 0.75, 1.0 }),
                SettingEntry.cycleInt("fake-chat.interval.min",          "ɪɴᴛᴇʀᴠᴀʟ - ᴍɪɴ (ꜱ)",
                    "ᴍɪɴɪᴍᴜᴍ ꜱᴇᴄᴏɴᴅꜱ ʙᴇᴛᴡᴇᴇɴ\nᴀ ʙᴏᴛ'ꜱ ᴄʜᴀᴛ ᴍᴇꜱꜱᴀɢᴇꜱ.",
                    Material.CLOCK, new int[]{ 5, 10, 20, 30, 60 }),
                SettingEntry.cycleInt("fake-chat.interval.max",          "ɪɴᴛᴇʀᴠᴀʟ - ᴍᴀx (ꜱ)",
                    "ᴍᴀxɪᴍᴜᴍ ꜱᴇᴄᴏɴᴅꜱ ʙᴇᴛᴡᴇᴇɴ\nᴀ ʙᴏᴛ'ꜱ ᴄʜᴀᴛ ᴍᴇꜱꜱᴀɡᴇꜱ.",
                    Material.CLOCK, new int[]{ 10, 20, 30, 60, 120 }),
                // ── Page 2 ────────────────────────────────────────────────────
                SettingEntry.toggle("fake-chat.keyword-reactions.enabled", "ᴋᴇʏᴡᴏʀᴅ ʀᴇᴀᴄᴛɪᴏɴꜱ",
                    "ʙᴏᴛꜱ ʀᴇᴀᴄᴛ ᴡʜᴇɴ ᴀ ᴘʟᴀʏᴇʀ'ꜱ\nᴍᴇꜱꜱᴀɢᴇ ᴄᴏɴᴛᴀɪɴꜱ ᴀ ᴛʀɪɡɡᴇʀ ᴡᴏʀᴅ.",
                    Material.BOOK),
                SettingEntry.cycleDouble("fake-chat.burst-chance",       "ʙᴜʀꜱᴛ ᴄʜᴀɴᴄᴇ (0–1)",
                    "ᴘʀᴏʙᴀʙɪʟɪᴛʏ ᴀ ʙᴏᴛ ꜱᴇɴᴅꜱ ᴀ\nQᴜɪᴄᴋ ꜰᴏʟʟᴏᴡ-ᴜᴘ ᴍᴇꜱꜱᴀɢᴇ.",
                    Material.PAPER, new double[]{ 0.0, 0.05, 0.10, 0.15, 0.25, 0.50 }),
                SettingEntry.cycleInt("fake-chat.stagger-interval",      "ᴄʜᴀᴛ ꜱᴀɡɡᴇʀ (ꜱ)",
                    "ᴍɪɴɪᴍᴜᴍ ɢᴀᴘ ʙᴇᴛᴡᴇᴇɴ ᴀɴʏ ᴛᴡᴏ\nʙᴏᴛꜱ ᴄʜᴀᴛᴛɪɴɢ. 0 = ᴅɪꜱᴀʙʟᴇᴅ.",
                    Material.CLOCK, new int[]{ 0, 1, 2, 3, 5, 10 }),
                SettingEntry.cycleInt("fake-chat.history-size",          "ᴍᴇꜱꜱᴀɢᴇ ʜɪꜱᴛᴏʀʏ ꜱɪᴢᴇ",
                    "ʀᴇᴄᴇɴᴛ ᴍᴇꜱꜱᴀɡᴇꜱ ᴘᴇʀ ʙᴏᴛ ᴛʀᴀᴄᴋᴇᴅ\nᴛᴏ ᴀᴠᴏɪᴅ ʀᴇᴘᴇᴀᴛɪɴɢ. 0 = ᴏꜰꜰ.",
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
                SettingEntry.toggle("swap.farewell-chat",       "ꜰᴀʀᴇᴡᴇʟʟ ᴍᴇꜱꜱᴀɡᴇꜱ",
                    "ʙᴏᴛꜱ ꜱᴀʏ ɢᴏᴏᴅʙʏᴇ ʙᴇꜰᴏʀᴇ\nʟᴇᴀᴠɪɴɢ ᴛʜᴇ ꜱᴇʀᴠᴇʀ.",
                    Material.POPPY),
                SettingEntry.toggle("swap.greeting-chat",       "ɢʀᴇᴇᴛɪɴɢ ᴍᴇꜱꜱᴀɡᴇꜱ",
                    "ʙᴏᴛꜱ ɢʀᴇᴇᴛ ᴛʜᴇ ꜱᴇʀᴠᴇʀ\nᴡʜᴇɴ ᴛʜᴇʏ ʀᴇᴛᴜʀɴ.",
                    Material.DANDELION),
                SettingEntry.toggle("swap.same-name-on-rejoin", "ᴋᴇᴇᴘ ɴᴀᴍᴇ ᴏɴ ʀᴇᴊᴏɪɴ",
                    "ʙᴏᴛꜱ ᴛʀʏ ᴛᴏ ʀᴇᴄʟᴀɪᴍ ᴛʜᴇɪʀ\nᴏʀɪɢɪɴᴀʟ ɴᴀᴍᴇ ᴡʜᴇɴ ʀᴇᴛᴜʀɴɪɴɢ.",
                    Material.NAME_TAG),
                SettingEntry.cycleInt("swap.session.min",       "ꜱᴇꜱꜱɪᴏɴ - ᴍɪɴ (ꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ᴘᴏꜱꜱɪʙʟᴇ ᴛɪᴍᴇ ᴀ\nʙᴏᴛ ꜱᴛᴀʏꜱ ᴏɴʟɪɴᴇ.",
                    Material.CLOCK, new int[]{ 30, 60, 120, 300, 600 }),
                SettingEntry.cycleInt("swap.session.max",       "ꜱᴇꜱꜱɪᴏɴ - ᴍᴀx (ꜱ)",
                    "ʟᴏɴɡᴇꜱᴛ ᴘᴏꜱꜱɪʙʟᴇ ᴛɪᴍᴇ ᴀ\nʙᴏᴛ ꜱᴛᴀʏꜱ ᴏɴʟɪɴᴇ.",
                    Material.CLOCK, new int[]{ 60, 120, 300, 600, 1200 }),
                SettingEntry.cycleInt("swap.absence.min",       "ᴀʙꜱᴇɴᴄᴇ - ᴍɪɴ (ꜱ)",
                    "ꜱʜᴏʀᴛᴇꜱᴛ ᴛɪᴍᴇ ᴀ ʙᴏᴛ\nꜱᴘᴇɴᴅꜱ ᴏꜦꜦʟɪɴᴇ.",
                    Material.GRAY_DYE, new int[]{ 15, 30, 60, 120 }),
                SettingEntry.cycleInt("swap.absence.max",       "ᴀʙꜱᴇɴᴄᴇ - ᴍᴀx (ꜱ)",
                    "ʟᴏɴɡᴇꜱᴛ ᴛɪᴍᴇ ᴀ ʙᴏᴛ\nꜱᴘᴇɴᴅꜱ ᴏꜦꜦʟɪɴᴇ.",
                    Material.GRAY_DYE, new int[]{ 30, 60, 120, 300 }),
                SettingEntry.cycleInt("swap.max-swapped-out",   "ᴍᴀx ᴏꜦꜦʟɪɴᴇ ᴀᴛ ᴏɴᴄᴇ",
                    "ᴄᴀᴘ ᴏɴ ꜱɪᴍᴜʟᴀᴛᴀɴᴇᴏᴜꜱʟʏ ᴀʙꜱᴇɴᴛ\nʙᴏᴛꜱ. 0 = ᴜɴʟɪᴍɪᴛᴇᴅ.",
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
                    "ꜰʟᴏᴏʀ: ᴍɪɴɪᴍᴜᴍ ᴀᴄᴛɪᴠᴇ ʙᴏᴛꜱ\nʀᴇɡᴀʀᴅʟᴇꜱꜱ ᴏꜦ ꜰʀᴀᴄᴛɪᴏɴ. 0 = ᴏ꜡.",
                    Material.COMPARATOR, new int[]{ 0, 1, 2, 5, 10 }),
                SettingEntry.cycleInt("peak-hours.stagger-seconds",  "ᴛʀᴀɴꜱɪᴛɪᴏɴ ꜱᴀɡɡᴇʀ (ꜱ)",
                    "ꜱᴘʀᴇᴀᴅ ʙᴏᴛ ᴊᴏɪɴ/ʟᴇᴀᴠᴇ ᴇᴠᴇɴᴛꜱ\nᴀᴄʀᴏꜱꜱ ᴛʜɪꜱ ᴡɪɴᴅᴏᴡ ɪɴ ꜱᴇᴄᴏɴᴅꜱ.",
                    Material.CLOCK, new int[]{ 5, 10, 30, 60, 120 })
            ));
    }

    private Category pvp() {
        return new Category("⚔ ᴘᴠᴘ ʙᴏᴛ",
            Material.NETHERITE_SWORD, Material.IRON_SWORD,
            Material.RED_STAINED_GLASS_PANE,
            List.of(
                // ── Page 1 ────────────────────────────────────────────────────
                SettingEntry.comingSoon("pvp-ai.difficulty",       "ᴅɪꜰꜰɪᴄᴜʟᴛʏ",
                    "ꜱᴇᴛ ᴛʜᴇ ʙᴏᴛ'ꜱ ꜱᴋɪʟʟ ʟᴇᴠᴇʟ.\nɴᴘᴄ / ᴇᴀꜱʏ / ᴍᴇᴅɪᴜᴍ / ʜᴀʀᴅ / ᴛɪᴇʀ1 / ʜᴀᴄᴋᴇʀ.",
                    Material.DIAMOND_SWORD),
                SettingEntry.comingSoon("pvp-ai.combat-mode",      "ᴄᴏᴍʙᴀᴛ ᴍᴏᴅᴇ",
                    "ꜱᴡɪᴛᴄʜ ʙᴇᴛᴡᴇᴇɴ ᴄʀʏꜱᴛᴀʟ ᴘᴠᴘ\nᴀɴᴅ ꜱᴡᴏʀᴅ ꜰɪɡʜᴛɪɴɢ ꜱᴛʏʟᴇ.",
                    Material.END_CRYSTAL),
                SettingEntry.comingSoon("pvp-ai.critting",         "ᴄʀɪᴛᴛɪɴɢ",
                    "ʙᴏᴛ ʟᴀɴᴅꜱ ᴄʀɪᴛɪᴄᴀʟ ʜɪᴛꜱ ʙʏ\nꜰᴀʟɪɴɢ ᴅᴜʀɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                    Material.NETHERITE_SWORD),
                SettingEntry.comingSoon("pvp-ai.s-tapping",        "ꜱ-ᴛᴀᴘᴘɪɴɢ",
                    "ʙᴏᴛ ᴛᴀᴘꜱ ꜱ ᴅᴜʀɪɴɢ ꜱᴡɪɴɢ\nᴛᴏ ʀᴇꜱᴇᴛ ᴀᴛᴛᴀᴄᴋ ᴄᴏᴏʟᴅᴏᴡɴ.",
                    Material.CLOCK),
                SettingEntry.comingSoon("pvp-ai.strafing",         "ꜱᴛʀᴀꜰɪɴɢ",
                    "ʙᴏᴛ ᴄɪʀᴄʟᴇꜱ ᴀʀᴏᴜɴᴅ ᴛʜᴇ ᴛᴀʀɡᴇᴛ\nᴡʜɪʟᴇ ꜰɪɡʜᴛɪɴɢ.",
                    Material.FEATHER),
                SettingEntry.comingSoon("pvp-ai.shield",           "ꜱʜɪᴇʟᴅɪɴɢ",
                    "ʙᴏᴛ ᴄᴀʀʀɪᴇꜱ ᴀɴᴅ ᴜꜱᴇꜱ ᴀ ꜱʜɪᴇʟᴅ\nᴛᴏ ʙʟᴏᴄᴋ ɪɴᴄᴏᴍɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                    Material.SHIELD),
                SettingEntry.comingSoon("pvp-ai.speed-buffs",      "ꜱᴘᴇᴇᴅ ʙᴜꜰꜰꜱ",
                    "ʙᴏᴛ ʜᴀꜱ ꜱᴘᴇᴇᴅ & ꜱᴛʀᴇɴɡᴛʜ ᴘᴏᴛɪᴏɴ\nᴇꜰꜰᴇᴄᴛꜱ ᴀᴄᴛɪᴠᴇ.",
                    Material.SUGAR),
                SettingEntry.comingSoon("pvp-ai.jump-reset",       "ᴊᴜᴍᴘ ʀᴇꜱᴇᴛ",
                    "ʙᴏᴛ ᴊᴜᴍᴘꜱ ᴊᴜꜱᴛ ʙᴇꜰᴏʀᴇ ꜱᴡɪɴɢɪɴɢ\nᴛᴏ ɢᴀɪɴ ᴛʜᴇ W-ᴛᴀᴘ ᴋɴᴏᴄᴋʙᴀᴄᴋ ʙᴏɴᴜꜱ.",
                    Material.SLIME_BALL),
                SettingEntry.comingSoon("pvp-ai.random",           "ʀᴀɴᴅᴏᴍ ᴘʟᴀʏꜱᴛʏʟᴇ",
                    "ʀᴀɴᴅᴏᴍɪꜱᴇ ᴛᴇᴄʜɴɪQᴜᴇꜱ ᴇᴀᴄʜ ʀᴏᴜɴᴅ\nᴛᴏ ᴋᴇᴇᴘ ᴛʜᴇ ꜰɪɡᴜᴛ ᴜɴᴘʀᴇᴅɪᴄᴛᴀʙʟᴇ.",
                    Material.COMPARATOR),
                SettingEntry.comingSoon("pvp-ai.gear",             "ɢᴇᴀʀ ᴛʏᴘᴇ",
                    "ʙᴏᴛ ᴡᴇᴀʀꜱ ᴅɪᴀᴍᴏɴᴅ ᴏʀ\nɴᴇᴛʜᴇʀɪᴛᴇ ᴀʀᴍᴏᴜʀ.",
                    Material.DIAMOND_CHESTPLATE),
                SettingEntry.comingSoon("pvp-ai.defensive-mode",   "ᴅᴇꜰᴇɴꜱɪᴠᴇ ᴍᴏᴅᴇ",
                    "ʙᴏᴛ ᴏɴʟʏ ꜰɪɡʜᴛꜱ ʙᴀᴄᴋ ᴡʜᴇɴ\nᴛʜᴇ ᴘʟᴀʏᴇʀ ᴀᴛᴛᴀᴄᴋꜱ ꜰɪʀꜱᴛ.",
                    Material.BOW),
                SettingEntry.comingSoon("pvp-ai.detect-range",     "ᴅᴇᴛᴇᴄᴛ ʀᴀɴɢᴇ",
                    "ʜᴏᴡ ꜰᴀʀ ᴛʜᴇ ʙᴏᴛ ꜱᴇᴇꜱ ᴘʟᴀʏᴇʀꜱ\nᴀɴᴅ ʟᴏᴄᴋꜱ ᴏɴ ᴀꜱ ᴛᴀʀɡᴇᴛ.",
                    Material.SPYGLASS),
                SettingEntry.comingSoon("pvp-ai.sprint",           "ꜱᴘʀɪɴᴛɪɴɢ",
                    "ʙᴏᴛ ꜱᴘʀɪɴᴛꜱ ᴛᴏᴡᴀʀᴅꜱ ᴛʜᴇ ᴛᴀʀɡᴇᴛ\nᴅᴜʀɪɴɢ ᴄᴏᴍʙᴀᴛ.",
                    Material.GOLDEN_BOOTS),
                SettingEntry.comingSoon("pvp-ai.pearl",            "ᴇɴᴅᴇʀ ᴘᴇᴀʀʟ",
                    "ʙᴏᴛ ᴛʜʀᴏᴡꜱ ᴇɴᴅᴇʀ ᴘᴇᴀʀʟꜱ ᴛᴏ\nᴄʟᴏꜱᴇ ᴛʜᴇ ɢᴀᴘ ᴏʀ ᴇꜱᴄᴀᴘᴇ.",
                    Material.ENDER_PEARL),
                SettingEntry.comingSoon("pvp-ai.pearl-spam",       "ᴘᴇᴀʀʟ ꜱᴘᴀᴍ",
                    "ʙᴏᴛ ꜱᴘᴀᴍꜱ ᴘᴇᴀʀʟꜱ ɪɴ ʙᴜʀꜱᴛꜱ\nꜰᴏʀ ᴀɡɡʀᴇꜱꜱɪᴠᴇ ɢᴀᴘ-ᴄʟᴏꜱɪɴɢ.",
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
                // ── Page 2 ────────────────────────────────────────────────────
                SettingEntry.comingSoon("pvp-ai.auto-refill",      "ᴀᴜᴛᴏ-ʀᴇꜰɪʟʟ ᴛᴏᴛᴇᴍ",
                    "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇ-ᴇQᴜɪɡꜱ ᴀ\nᴛᴏᴛᴇᴍ ᴀꜰᴛᴇʀ ᴘᴏᴘᴘɪɴɡ ᴏɴᴇ.",
                    Material.TOTEM_OF_UNDYING),
                SettingEntry.comingSoon("pvp-ai.auto-respawn",     "ᴀᴜᴛᴏ-ʀᴇꜱᴘᴀᴡɴ",
                    "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇꜱᴘᴀᴡɴꜱ\nᴀɴᴅ ʀᴇᴊᴏɪɴꜱ ᴀꜰᴛᴇʀ ᴅᴇᴀᴛʜ.",
                    Material.RESPAWN_ANCHOR),
                SettingEntry.comingSoon("pvp-ai.spawn-protection", "ꜱᴘᴀᴡɴ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ",
                    "ʙᴏᴛ ꜱᴛᴀʏꜱ ɪɴᴠᴜʟɴᴇʀᴀʙʟᴇ ꜰᴏʀ\nᴀ ꜱʜᴏʀᴛ ɢʀᴀᴄᴇ ᴘᴇʀɪᴏᴅ ᴀᴛ ꜱᴘᴀᴡɴ.",
                    Material.GRASS_BLOCK)
            ));
    }

    private Category pathfinding() {
        return new Category("🧭 ᴘᴀᴛʜꜰɪɴᴅɪɴɢ",
            Material.COMPASS, Material.COMPASS,
            Material.CYAN_STAINED_GLASS_PANE,
            List.of(
                SettingEntry.cycleDouble("pathfinding.arrival-distance",
                    "ᴀʀʀɪᴠᴀʟ ᴅɪꜱᴛᴀɴᴄᴇ",
                    "ʜᴏʀɪᴢᴏɴᴛᴀʟ ʀᴀᴅɪᴜꜱ ᴛʜᴀᴛ ᴄᴏᴜɴᴛꜱ ᴀꜱ\nᴀʀʀɪᴠᴇᴅ ꜰᴏʀ ꜰɪxᴇᴅ ɴᴀᴠɪɢᴀᴛɪᴏɴ ɢᴏᴀʟꜱ.",
                    Material.TARGET, new double[]{ 0.8, 1.0, 1.2, 1.5, 2.0 }),
                SettingEntry.cycleDouble("pathfinding.patrol-arrival-distance",
                    "ᴘᴀᴛʀᴏʟ ᴀʀʀɪᴠᴀʟ ᴅɪꜱᴛᴀɴᴄᴇ",
                    "ʜᴏʀɪᴢᴏɴᴛᴀʟ ʀᴀᴅɪᴜꜱ ᴛʜᴀᴛ ᴄᴏᴜɴᴛꜱ ᴀꜱ\nᴀʀʀɪᴠᴇᴅ ꜰᴏʀ ᴡᴀʏᴘᴏɪɴᴛ ᴘᴀᴛʀᴏʟꜱ.",
                    Material.LEAD, new double[]{ 1.0, 1.2, 1.5, 2.0, 3.0 }),
                SettingEntry.cycleDouble("pathfinding.waypoint-arrival-distance",
                    "ᴡᴀʏᴘᴏɪɴᴛ ꜱɴᴀᴘ ʀᴀᴅɪᴜꜱ",
                    "ʜᴏᴡ ᴄʟᴏꜱᴇ ᴀ ʙᴏᴛ ᴍᴜꜱᴛ ɢᴇᴛ ᴛᴏ ᴇᴀᴄʜ\nᴘᴀᴛʜ ɴᴏᴅᴇ ʙᴇꜰᴏʀᴇ ᴀᴅᴠᴀɴᴄɪɴɢ.",
                    Material.STRING, new double[]{ 0.45, 0.65, 0.85, 1.0, 1.25 }),
                SettingEntry.cycleDouble("pathfinding.sprint-distance",
                    "ꜱᴘʀɪɴᴛ ᴅɪꜱᴛᴀɴᴄᴇ",
                    "ʙᴏᴛꜱ ꜱᴛᴀʀᴛ ꜱᴘʀɪɴᴛɪɴɢ ᴡʜᴇɴ ᴛʜᴇʏ ᴀʀᴇ\nꜰᴀʀᴛʜᴇʀ ᴀᴡᴀʏ ᴛʜᴀɴ ᴛʜɪꜱ ᴅɪꜱᴛᴀɴᴄᴇ.",
                    Material.SUGAR, new double[]{ 0.0, 3.0, 6.0, 8.0, 12.0, 16.0 }),
                SettingEntry.cycleDouble("pathfinding.follow-recalc-distance",
                    "ꜰᴏʟʟᴏᴡ ʀᴇᴄᴀʟᴄ ᴅɪꜱᴛᴀɴᴄᴇ",
                    "ᴍᴏᴠɪɴɢ ᴛᴀʀɢᴇᴛꜱ ꜰᴏʀᴄᴇ ᴀ ɴᴇᴡ ᴘᴀᴛʜ ᴀꜰᴛᴇʀ\nᴛʜᴇʏ ᴍᴏᴠᴇ ᴛʜɪꜱ ꜰᴀʀ ꜰʀᴏᴍ ᴛʜᴇ ʟᴀꜱᴛ ᴄᴀʟᴄ.",
                    Material.ENDER_EYE, new double[]{ 1.5, 2.5, 3.5, 5.0, 8.0 }),
                SettingEntry.cycleInt("pathfinding.recalc-interval",
                    "ʀᴇᴄᴀʟᴄ ɪɴᴛᴇʀᴠᴀʟ (ᴛɪᴄᴋꜱ)",
                    "ʜᴇᴀʀᴛʙᴇᴀᴛ ɪɴᴛᴇʀᴠᴀʟ ꜰᴏʀ ᴀᴜᴛᴏᴍᴀᴛɪᴄ\nᴘᴀᴛʜ ʀᴇᴄᴀʟᴄᴜʟᴀᴛɪᴏɴ. 20 = 1 ꜱᴇᴄᴏɴᴅ.",
                    Material.REPEATER, new int[]{ 10, 20, 40, 60, 100, 200 }),
                SettingEntry.cycleInt("pathfinding.stuck-ticks",
                    "ꜱᴛᴜᴄᴋ ᴛɪᴄᴋꜱ",
                    "ʜᴏᴡ ᴍᴀɴʏ ʟᴏᴡ-ᴍᴏᴠᴇᴍᴇɴᴛ ᴛɪᴄᴋꜱ ʙᴇꜰᴏʀᴇ\nᴀ ʙᴏᴛ ɪꜱ ᴛʀᴇᴀᴛᴇᴅ ᴀꜱ ꜱᴛᴜᴄᴋ.",
                    Material.COBWEB, new int[]{ 4, 6, 8, 10, 15, 20 }),
                SettingEntry.cycleDouble("pathfinding.stuck-threshold",
                    "ꜱᴛᴜᴄᴋ ᴍᴏᴠᴇᴍᴇɴᴛ ᴛʜʀᴇꜱʜᴏʟᴅ",
                    "ᴍɪɴɪᴍᴜᴍ ʜᴏʀɪᴢᴏɴᴛᴀʟ ᴍᴏᴠᴇᴍᴇɴᴛ ᴘᴇʀ ᴛɪᴄᴋ\nʙᴇꜰᴏʀᴇ ᴀ ʙᴏᴛ ɪꜱ ᴄᴏɴꜱɪᴅᴇʀᴇᴅ ꜱᴛᴜᴄᴋ.",
                    Material.SLIME_BALL, new double[]{ 0.01, 0.02, 0.04, 0.06, 0.08 }),
                SettingEntry.toggle("pathfinding.parkour",
                    "ᴘᴀʀᴋᴏᴜʀ",
                    "ʙᴏᴛꜱ ꜱᴘʀɪɴᴛ-ᴊᴜᴍᴘ ᴀᴄʀᴏꜱꜱ 1–2 ʙʟᴏᴄᴋ\nɢᴀᴘꜱ ᴅᴜʀɪɴɢ ɢʟᴏʙᴀʟ ɴᴀᴠɪɢᴀᴛɪᴏɴ.",
                    Material.LEATHER_BOOTS),
                SettingEntry.toggle("pathfinding.break-blocks",
                    "ʙʀᴇᴀᴋ ʙʟᴏᴄᴋꜱ",
                    "ʙᴏᴛꜱ ʙʀᴇᴀᴋ ꜱᴏʟɪᴅ ʙʟᴏᴄᴋꜱ ᴛʜᴀᴛ\nʙʟᴏᴄᴋ ᴛʜᴇ ɢʟᴏʙᴀʟ ɴᴀᴠɪɢᴀᴛɪᴏɴ ᴘᴀᴛʜ.",
                    Material.IRON_PICKAXE),
                SettingEntry.toggle("pathfinding.place-blocks",
                    "ᴘʟᴀᴄᴇ ʙʟᴏᴄᴋꜱ",
                    "ʙᴏᴛꜱ ᴘʟᴀᴄᴇ ʙʀɪᴅɢᴇ ʙʟᴏᴄᴋꜱ ᴛᴏ\nᴄʀᴏꜱꜱ 1-ʙʟᴏᴄᴋ ɢᴀᴘꜱ ᴅᴜʀɪɴɢ ɴᴀᴠɪɢᴀᴛɪᴏɴ.",
                    Material.DIRT)
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

        /** Creates a locked "coming soon" entry - clicking plays ENTITY_VILLAGER_NO. */
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




package me.bill.fakePlayerPlugin.gui;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotRenameHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Per-bot settings GUI — opened by shift-right-clicking a bot entity.
 * Uses the same UI layout and visual language as {@link SettingGui}.
 *
 * <h3>Layout (6 rows / 54 slots)</h3>
 * <pre>
 *  [S0 ][S1 ][S2 ]…[S8 ]   ← row 0: settings 0-8
 *  [S9 ]…[S17]              ← row 1: settings 9-17
 *  …                        ← rows 2-4: settings 18-44
 *  [⟲  ][◄  ][C1][C2][C3][C4][C5][▶  ][✕]  ← row 5: chrome
 * </pre>
 *
 * <h3>Categories</h3>
 * <ol>
 *   <li>⚙ ɢᴇɴᴇʀᴀʟ  — Frozen (toggle), Look At Player (toggle), Rename (action)</li>
 *   <li>💬 ᴄʜᴀᴛ    — Chat Enabled (toggle), Chat Tier (cycle), AI Personality (cycle)</li>
 *   <li>⚔ ᴘᴠᴘ     — All PVP settings (coming soon)</li>
 *   <li>📋 ᴄᴍᴅꜱ    — Set RC Cmd (action/OP), Clear RC Cmd (immediate/OP)</li>
 *   <li>⚠ ᴅᴀɴɢᴇʀ  — Delete Bot (danger/OP)</li>
 * </ol>
 */
public final class BotSettingGui implements Listener {

    // ── Colour palette (identical to SettingGui) ──────────────────────────────
    private static final TextColor ACCENT           = TextColor.fromHexString("#0079FF");
    private static final TextColor ON_GREEN         = TextColor.fromHexString("#66CC66");
    private static final TextColor OFF_RED          = NamedTextColor.RED;
    private static final TextColor VALUE_YELLOW     = TextColor.fromHexString("#FFDD57");
    private static final TextColor YELLOW           = NamedTextColor.YELLOW;
    private static final TextColor GRAY             = NamedTextColor.GRAY;
    private static final TextColor DARK_GRAY        = NamedTextColor.DARK_GRAY;
    private static final TextColor WHITE            = NamedTextColor.WHITE;
    private static final TextColor DANGER_RED       = TextColor.fromHexString("#FF4444");
    private static final TextColor COMING_SOON_COLOR = TextColor.fromHexString("#FFA500");

    // ── GUI geometry (identical to SettingGui) ────────────────────────────────
    private static final int SIZE              = 54;
    private static final int SETTINGS_PER_PAGE = 45;
    private static final int SLOT_RESET        = 45;
    private static final int SLOT_CAT_PREV     = 46;
    private static final int SLOT_CAT_NEXT     = 52;
    private static final int SLOT_CLOSE        = 53;
    private static final int CAT_WINDOW        = 5;
    private static final int CAT_WINDOW_START  = 47;

    // ── State ─────────────────────────────────────────────────────────────────
    private final FakePlayerPlugin  plugin;
    private final FakePlayerManager manager;
    private final BotRenameHelper   renameHelper;

    /** [catIdx, pageIdx, catOffset] per player. */
    private final Map<UUID, int[]>           sessions        = new HashMap<>();
    /** Player UUID → target bot UUID. */
    private final Map<UUID, UUID>            botSessions     = new HashMap<>();
    /** Active chat-input sessions (rename / set-command). */
    private final Map<UUID, ChatInputSes>    chatSessions    = new HashMap<>();
    private final Set<UUID>                  pendingChatInput = new HashSet<>();
    private final Set<UUID>                  pendingRebuild   = new HashSet<>();
    /** UUIDs for which a bot-delete is in progress — suppresses "settings saved" close message. */
    private final Set<UUID>                  pendingDelete    = new HashSet<>();

    // ── Category definitions ──────────────────────────────────────────────────
    private final BotCategory[] categories;

    public BotSettingGui(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin      = plugin;
        this.manager     = manager;
        this.renameHelper = new BotRenameHelper(plugin, manager);
        this.categories  = new BotCategory[]{ general(), chat(), pvp(), commands(), pathfinding(), danger() };
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Public API
    // ═════════════════════════════════════════════════════════════════════════

    /** Opens the bot settings GUI for {@code player} targeting {@code bot}. */
    public void open(Player player, FakePlayer bot) {
        UUID uuid = player.getUniqueId();
        sessions.put(uuid, new int[]{ 0, 0, 0 });
        botSessions.put(uuid, bot.getUuid());
        build(player);
    }

    /** Cleanup on plugin disable. */
    public void shutdown() {
        sessions.clear();
        botSessions.clear();
        chatSessions.forEach((uuid, ses) -> Bukkit.getScheduler().cancelTask(ses.cleanupTaskId));
        chatSessions.clear();
        pendingChatInput.clear();
        pendingRebuild.clear();
        pendingDelete.clear();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Build
    // ═════════════════════════════════════════════════════════════════════════

    private void build(Player player) {
        UUID   uuid    = player.getUniqueId();
        int[]  state   = sessions.get(uuid);
        UUID   botUuid = botSessions.get(uuid);
        if (state == null || botUuid == null) return;

        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) {
            cleanup(uuid);
            player.sendMessage(Lang.get("chat-bot-not-found", "name", "?"));
            return;
        }

        int catIdx    = state[0];
        int pageIdx   = state[1];
        int catOffset = state[2];
        BotCategory cat = categories[catIdx];
        boolean isOp    = isOp(player);

        GuiHolder holder = new GuiHolder(uuid);
        Component title = Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("[").color(DARK_GRAY))
            .append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("] ").color(DARK_GRAY))
            .append(Component.text(bot.getName()).color(ACCENT))
            .append(Component.text("  ·  ").color(DARK_GRAY))
            .append(Component.text(cat.label()).color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);

        // Settings area: rows 0-4 (slots 0-44)
        List<BotEntry> entries = visibleEntries(cat, isOp);
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) SETTINGS_PER_PAGE));
        pageIdx = Math.min(pageIdx, Math.max(0, totalPages - 1));
        state[1] = pageIdx;

        int startIdx = pageIdx * SETTINGS_PER_PAGE;
        int endIdx   = Math.min(startIdx + SETTINGS_PER_PAGE, entries.size());
        for (int i = startIdx; i < endIdx; i++) {
            inv.setItem(i - startIdx, buildEntryItem(entries.get(i), bot));
        }

        // Bottom row (slots 45-53)
        inv.setItem(SLOT_RESET, buildResetButton());
        inv.setItem(SLOT_CAT_PREV, catOffset > 0 ? buildCatArrow(false) : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i < CAT_WINDOW; i++) {
            int ci = catOffset + i;
            inv.setItem(CAT_WINDOW_START + i,
                ci < categories.length ? buildCategoryTab(ci, ci == catIdx) : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        }
        inv.setItem(SLOT_CAT_NEXT,
            catOffset + CAT_WINDOW < categories.length ? buildCatArrow(true) : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(SLOT_CLOSE, buildCloseButton());

        pendingRebuild.add(uuid);
        player.openInventory(inv);
        pendingRebuild.remove(uuid);
        sessions.put(uuid, state);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Bukkit events
    // ═════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;

        UUID   uuid    = player.getUniqueId();
        int[]  state   = sessions.get(holder.uuid);
        UUID   botUuid = botSessions.get(uuid);
        if (state == null || botUuid == null) return;

        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) { player.closeInventory(); return; }

        boolean isOp      = isOp(player);
        int     slot      = event.getSlot();
        int     catIdx    = state[0];
        int     catOffset = state[2];

        if (slot == SLOT_RESET) {
            playUiClick(player, 0.6f); resetBot(player, bot, isOp); return;
        }
        if (slot == SLOT_CAT_PREV) {
            if (catOffset > 0) { playUiClick(player, 1.0f); state[2]--; }
            build(player); return;
        }
        if (slot == SLOT_CAT_NEXT) {
            if (catOffset + CAT_WINDOW < categories.length) { playUiClick(player, 1.0f); state[2]++; }
            build(player); return;
        }
        if (slot == SLOT_CLOSE) {
            playUiClick(player, 0.8f); player.closeInventory(); return;
        }
        if (slot >= CAT_WINDOW_START && slot < CAT_WINDOW_START + CAT_WINDOW) {
            int ci = catOffset + (slot - CAT_WINDOW_START);
            if (ci < categories.length) {
                if (ci != catIdx) playUiClick(player, 1.3f);
                state[0] = ci; state[1] = 0; build(player);
            }
            return;
        }
        if (slot < 45) {
            List<BotEntry> entries = visibleEntries(categories[catIdx], isOp);
            int entryIdx = state[1] * SETTINGS_PER_PAGE + slot;
            if (entryIdx >= entries.size()) return;
            handleEntryClick(player, bot, entries.get(entryIdx), isOp);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
        if (pendingChatInput.contains(uuid)) return;
        if (pendingRebuild.contains(uuid)) return;
        if (pendingDelete.contains(uuid)) return;
        cleanup(uuid);
        if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT
                && event.getPlayer() instanceof Player player) {
            player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✔ ").color(ON_GREEN))
                .append(Component.text("ʙᴏᴛ ꜱᴇᴛᴛɪɴɢꜱ ꜱᴀᴠᴇᴅ.").color(WHITE)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSes ses = chatSessions.remove(uuid);
        if (ses == null) return;

        event.setCancelled(true);
        Bukkit.getScheduler().cancelTask(ses.cleanupTaskId);

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        sessions.put(uuid, ses.guiState);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return;

            if (raw.equalsIgnoreCase("cancel")) {
                p.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✦ ").color(ACCENT))
                    .append(Component.text("ᴄᴀɴᴄᴇʟʟᴇᴅ - ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ.").color(GRAY)));
                build(p); return;
            }

            FakePlayer bot = manager.getByUuid(ses.botUuid);
            if (bot == null) { p.sendMessage(Lang.get("chat-bot-not-found", "name", "?")); cleanup(uuid); return; }

            applyInput(p, bot, ses.inputType, raw);
            build(p);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSes ses = chatSessions.remove(uuid);
        if (ses != null) Bukkit.getScheduler().cancelTask(ses.cleanupTaskId);
        cleanup(uuid);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Entry interaction handlers
    // ═════════════════════════════════════════════════════════════════════════

    private void handleEntryClick(Player player, FakePlayer bot, BotEntry entry, boolean isOp) {
        switch (entry.type()) {
            case COMING_SOON -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.8f, 1.0f);
                player.sendActionBar(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                    .append(Component.text(entry.label() + "  ").color(WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.text("- ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ").color(COMING_SOON_COLOR).decoration(TextDecoration.BOLD, true)));
            }
            case TOGGLE -> {
                boolean newVal = applyToggle(bot, entry.id());
                // When pickup is toggled OFF, drop the bot's current contents so items/XP
                // are not silently locked inside the bot while pickup is disabled.
                if (!newVal) {
                    if ("pickup_items".equals(entry.id())) {
                        dropBotInventory(bot);
                    } else if ("pickup_xp".equals(entry.id())) {
                        dropBotXp(bot);
                    }
                }
                // Persist ALL settings (covers frozen, head_ai, chat, nav, pickup in one call)
                manager.persistBotSettings(bot);
                playUiClick(player, newVal ? 1.2f : 0.85f);
                sendActionBarConfirm(player, entry.label(), newVal ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ");
                build(player);
            }
            case CYCLE_TIER -> {
                cycleTier(bot);
                manager.persistBotSettings(bot);
                playUiClick(player, 1.0f);
                sendActionBarConfirm(player, entry.label(), bot.getChatTier() != null ? bot.getChatTier() : "ʀᴀɴᴅᴏᴍ");
                build(player);
            }
            case CYCLE_PERSONALITY -> {
                cyclePersonality(bot);
                playUiClick(player, 1.0f);
                String pName = bot.getAiPersonality() != null ? bot.getAiPersonality() : "ᴅᴇꜰᴀᴜʟᴛ";
                sendActionBarConfirm(player, entry.label(), pName);
                build(player);
            }
            case ACTION    -> { playUiClick(player, 1.0f); openChatInput(player, bot, entry); }
            case IMMEDIATE -> { applyImmediate(player, bot, entry.id()); playUiClick(player, 0.85f); build(player); }
            case DANGER    -> { if (!isOp) return; playUiClick(player, 0.6f); applyDanger(player, bot, entry.id()); }
        }
    }

    private boolean applyToggle(FakePlayer bot, String id) {
        return switch (id) {
            case "frozen"            -> { bot.setFrozen(!bot.isFrozen());                         yield bot.isFrozen(); }
            case "head_ai_enabled"   -> { bot.setHeadAiEnabled(!bot.isHeadAiEnabled());           yield bot.isHeadAiEnabled(); }
            case "swim_ai_enabled"   -> { bot.setSwimAiEnabled(!bot.isSwimAiEnabled());           yield bot.isSwimAiEnabled(); }
            case "pickup_items"      -> {
                boolean v = !bot.isPickUpItemsEnabled();
                bot.setPickUpItemsEnabled(v);
                // Sync the NMS-level pickup flag on the live entity so the change takes
                // effect immediately regardless of event-handler timing.
                Player body = bot.getPlayer();
                if (body != null) body.setCanPickupItems(v);
                yield v;
            }
            case "pickup_xp"         -> { bot.setPickUpXpEnabled(!bot.isPickUpXpEnabled());        yield bot.isPickUpXpEnabled(); }
            case "chat_enabled"      -> { bot.setChatEnabled(!bot.isChatEnabled());               yield bot.isChatEnabled(); }
            case "nav_parkour"       -> { bot.setNavParkour(!bot.isNavParkour());                 yield bot.isNavParkour(); }
            case "nav_break_blocks"  -> { bot.setNavBreakBlocks(!bot.isNavBreakBlocks());         yield bot.isNavBreakBlocks(); }
            case "nav_place_blocks"  -> { bot.setNavPlaceBlocks(!bot.isNavPlaceBlocks());         yield bot.isNavPlaceBlocks(); }
            default -> false;
        };
    }

    private void cycleTier(FakePlayer bot) {
        bot.setChatTier(switch (bot.getChatTier() == null ? "random" : bot.getChatTier()) {
            case "random" -> "quiet"; case "quiet" -> "passive"; case "passive" -> "normal";
            case "normal" -> "active"; case "active" -> "chatty"; default -> null;
        });
    }

    private void cyclePersonality(FakePlayer bot) {
        me.bill.fakePlayerPlugin.ai.PersonalityRepository repo = plugin.getPersonalityRepository();
        if (repo == null || repo.size() == 0) {
            bot.setAiPersonality(null);
            return;
        }

        List<String> names = repo.getNames();  // sorted alphabetically
        String current = bot.getAiPersonality();

        if (current == null) {
            // null → first personality
            bot.setAiPersonality(names.get(0));
        } else {
            int idx = names.indexOf(current.toLowerCase(java.util.Locale.ROOT));
            if (idx == -1 || idx == names.size() - 1) {
                // Not found or last → reset to null (default)
                bot.setAiPersonality(null);
            } else {
                // Move to next
                bot.setAiPersonality(names.get(idx + 1));
            }
        }

        // Persist to DB if available
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().updateBotAiPersonality(
                    bot.getUuid().toString(), bot.getAiPersonality());
        }
    }

    private void applyImmediate(Player player, FakePlayer bot, String id) {
        if ("rc_cmd_clear".equals(id)) {
            bot.setRightClickCommand(null);
            manager.persistBotSettings(bot);
            sendActionBarConfirm(player, "ʀɪɡʜᴛ-ᴄʟɪᴄᴋ ᴄᴍᴅ", "✘ ᴄʟᴇᴀʀᴇᴅ");
        }
    }

    private void applyDanger(Player player, FakePlayer bot, String id) {
        if ("delete".equals(id)) {
            String botName = bot.getName();
            UUID   playerUuid = player.getUniqueId();
            // Mark as pending-delete so onInventoryClose suppresses "settings saved" message
            pendingDelete.add(playerUuid);
            cleanup(playerUuid);
            player.closeInventory();
            pendingDelete.remove(playerUuid);
            // FIX: delete() takes a name, not a UUID string
            manager.delete(botName);
            player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✕ ").color(DANGER_RED))
                .append(Component.text("ᴅᴇʟᴇᴛᴇᴅ ʙᴏᴛ  ").color(WHITE))
                .append(Component.text(botName).color(ACCENT)));
        }
    }

    private void applyInput(Player player, FakePlayer bot, String inputType, String raw) {
        switch (inputType) {
            case "rename" -> {
                cleanup(player.getUniqueId());
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> renameHelper.rename(player, bot, raw), 1L);
            }
            case "rc_cmd_set" -> {
                String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
                bot.setRightClickCommand(cmd);
                String stored = bot.getRightClickCommand();
                if (stored != null) {
                    sendActionBarConfirm(player, "ʀɪɡʜᴛ-ᴄʟɪᴄᴋ ᴄᴍᴅ", "/" + stored);
                    player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("✔ ").color(ON_GREEN))
                        .append(Component.text("ᴄᴍᴅ ꜱᴇᴛ  ").color(WHITE))
                        .append(Component.text("/" + stored).color(VALUE_YELLOW)));
                }
            }
            case "chunk_load_radius" -> {
                int globalMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
                int val;
                try { val = Integer.parseInt(raw.trim()); }
                catch (NumberFormatException e) {
                    player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("✘ ").color(OFF_RED))
                        .append(Component.text("ɪɴᴠᴀʟɪᴅ ɴᴜᴍʙᴇʀ — ᴇɴᴛᴇʀ -1 (ɢʟᴏʙᴀʟ), 0 (ᴏꜰꜰ), ᴏʀ 1-" + globalMax + ".").color(GRAY)));
                    return;
                }
                // -1 = follow global, 0 = disable, 1..globalMax = fixed radius
                if (val < -1) val = -1;
                if (val > globalMax && globalMax > 0) val = globalMax;
                bot.setChunkLoadRadius(val);
                manager.persistBotSettings(bot);
                String display = val == -1 ? "ɢʟᴏʙᴀʟ (" + globalMax + ")" : val == 0 ? "ᴅɪꜱᴀʙʟᴇᴅ" : val + " ᴄʜᴜɴᴋꜱ";
                sendActionBarConfirm(player, "ᴄʜᴜɴᴋ ʀᴀᴅɪᴜꜱ", display);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Pickup drop helpers (per-bot, mirrors SettingGui.dropBotInventoryWithAnimation)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * When per-bot item pickup is toggled OFF, look down, wait 3 ticks, drop all
     * non-air inventory items at the bot's location, then restore the head direction.
     * Only affects THIS bot (not global).
     */
    private void dropBotInventory(FakePlayer fp) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;

        // Quick early-out – nothing to drop
        boolean hasItems = false;
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) { hasItems = true; break; }
        }
        if (!hasItems) return;

        Location loc      = bot.getLocation();
        float    origYaw   = loc.getYaw();
        float    origPitch = loc.getPitch();

        // 1. Look down immediately for animation effect
        bot.setRotation(origYaw, 90f);
        NmsPlayerSpawner.setHeadYaw(bot, origYaw);

        // 2. Drop items after a small delay so the animation is visible
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player b = fp.getPlayer();
            if (b == null || !b.isOnline()) return;

            ItemStack[] contents = b.getInventory().getContents().clone();
            b.getInventory().clear();
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) {
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

    /**
     * When per-bot XP pickup is toggled OFF, spawn an XP orb carrying the bot's
     * entire experience at its current location, then zero the bot's XP.
     * Only affects THIS bot (not global).
     */
    private void dropBotXp(FakePlayer fp) {
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
    }

    private void resetBot(Player player, FakePlayer bot, boolean isOp) {
        bot.setFrozen(false);
        bot.setChatEnabled(true);
        bot.setChatTier(null);
        bot.setPickUpItemsEnabled(Config.bodyPickUpItems());
        bot.setPickUpXpEnabled(Config.bodyPickUpXp());
        bot.setSwimAiEnabled(Config.swimAiEnabled());
        bot.setChunkLoadRadius(-1);
        bot.setNavParkour(Config.pathfindingParkour());
        bot.setNavBreakBlocks(Config.pathfindingBreakBlocks());
        bot.setNavPlaceBlocks(Config.pathfindingPlaceBlocks());
        if (isOp) bot.setRightClickCommand(null);
        // Persist ALL reset fields in one DB call (frozen, chat, nav, pickup, swim, chunk, etc.)
        manager.persistBotSettings(bot);
        build(player);
        player.sendActionBar(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲ ").color(YELLOW))
            .append(Component.text("ʙᴏᴛ ꜱᴇᴛᴛɪɴɢꜱ  ").color(WHITE))
            .append(Component.text("ʀᴇꜱᴇᴛ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛꜱ").color(YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Chat input
    // ═════════════════════════════════════════════════════════════════════════

    private void openChatInput(Player player, FakePlayer bot, BotEntry entry) {
        UUID  uuid     = player.getUniqueId();
        int[] guiState = sessions.get(uuid);
        if (guiState == null) return;

        pendingChatInput.add(uuid);
        player.closeInventory();
        pendingChatInput.remove(uuid);

        String promptLabel; String currentVal;
        switch (entry.id()) {
            case "rename"            -> { promptLabel = "ɴᴇᴡ ʙᴏᴛ ɴᴀᴍᴇ"; currentVal = bot.getName(); }
            case "rc_cmd_set"        -> { promptLabel = "ɴᴇᴡ ᴄᴏᴍᴍᴀɴᴅ (ᴡɪᴛʜᴏᴜᴛ /)";
                                         currentVal = bot.hasRightClickCommand() ? "/" + bot.getRightClickCommand() : "ɴᴏᴛ ꜱᴇᴛ"; }
            case "chunk_load_radius" -> {
                int gMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
                promptLabel = "ʀᴀᴅɪᴜꜱ (-1=ɢʟᴏʙᴀʟ, 0=ᴏꜰꜰ, 1-" + gMax + ")";
                int cur = bot.getChunkLoadRadius();
                currentVal = cur == -1 ? "ɢʟᴏʙᴀʟ (" + gMax + ")" : cur == 0 ? "ᴅɪꜱᴀʙʟᴇᴅ" : cur + " ᴄʜᴜɴᴋꜱ";
            }
            default                  -> { promptLabel = entry.label(); currentVal = "?"; }
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("┌─ ").color(DARK_GRAY))
            .append(Component.text("[").color(DARK_GRAY)).append(Component.text("ꜰᴘᴘ").color(ACCENT))
            .append(Component.text("]  ").color(DARK_GRAY))
            .append(Component.text("ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ").color(WHITE).decoration(TextDecoration.BOLD, true))
            .append(Component.text("  ·  ᴇᴅɪᴛ ᴠᴀʟᴜᴇ").color(DARK_GRAY)));
        player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(Component.text(entry.label()).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
        for (String line : entry.description().split("\\\\n|\n")) {
            if (!line.isBlank()) player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("│  ").color(DARK_GRAY)).append(Component.text(line).color(GRAY)));
        }
        player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false).append(Component.text("│  ").color(DARK_GRAY)));
        player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("│  ").color(DARK_GRAY))
            .append(Component.text("ᴄᴜʀʀᴇɴᴛ  ").color(DARK_GRAY))
            .append(Component.text(currentVal).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("└─ ").color(DARK_GRAY))
            .append(Component.text("ᴛʏᴘᴇ ᴀ ɴᴇᴡ ᴠᴀʟᴜᴇ, ᴏʀ ").color(GRAY))
            .append(Component.text("ᴄᴀɴᴄᴇʟ").color(OFF_RED).decoration(TextDecoration.BOLD, true))
            .append(Component.text(" ᴛᴏ ɢᴏ ʙᴀᴄᴋ.").color(GRAY)));
        player.sendMessage(Component.empty());

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ChatInputSes stale = chatSessions.remove(uuid);
            if (stale != null) {
                sessions.put(uuid, stale.guiState);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(Component.empty().decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("✦ ").color(ACCENT))
                        .append(Component.text("ɪɴᴘᴜᴛ ᴛɪᴍᴇᴅ ᴏᴜᴛ - ʀᴇᴛᴜʀɴɪɴɢ ᴛᴏ ꜱᴇᴛᴛɪɴɢꜱ.").color(GRAY)));
                    build(p);
                }
            }
        }, 20L * 60).getTaskId();

        chatSessions.put(uuid, new ChatInputSes(entry.id(), bot.getUuid(), guiState.clone(), taskId));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Item builders
    // ═════════════════════════════════════════════════════════════════════════

    private ItemStack buildEntryItem(BotEntry entry, FakePlayer bot) {
        // ── Coming-soon entries: orange locked look ───────────────────────────
        if (entry.type() == BotEntryType.COMING_SOON) {
            ItemStack item = new ItemStack(entry.icon());
            ItemMeta  meta = item.getItemMeta();
            meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                .append(Component.text(entry.label()).color(COMING_SOON_COLOR).decoration(TextDecoration.BOLD, true)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
                .append(Component.text("⚠ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ").color(COMING_SOON_COLOR).decoration(TextDecoration.BOLD, true)));
            lore.add(Component.empty());
            for (String line : entry.description().split("\\\\n|\n")) {
                if (!line.isBlank()) lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(line).color(GRAY)));
            }
            lore.add(Component.empty());
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                .append(Component.text("ꜰᴇᴀᴛᴜʀᴇ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ").color(DARK_GRAY)));
            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }
        boolean isToggle = entry.type() == BotEntryType.TOGGLE;
        boolean isDanger = entry.type() == BotEntryType.DANGER;
        boolean isOn     = isToggle && getBoolValue(entry.id(), bot);

        TextColor nameColor = isDanger ? DANGER_RED : (isToggle ? (isOn ? ON_GREEN : OFF_RED) : ACCENT);
        ItemStack item = new ItemStack(dynamicIcon(entry, bot));
        ItemMeta  meta = item.getItemMeta();

        if (isToggle && isOn) { meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); }

        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(entry.label()).color(nameColor).decoration(TextDecoration.BOLD, true)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        TextColor valColor = isDanger ? DANGER_RED : (isToggle ? (isOn ? ON_GREEN : OFF_RED) : VALUE_YELLOW);
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ᴠᴀʟᴜᴇ  ").color(DARK_GRAY))
            .append(Component.text(valueString(entry, bot)).color(valColor).decoration(TextDecoration.BOLD, true)));
        lore.add(Component.empty());
        for (String line : entry.description().split("\\\\n|\n")) {
            if (!line.isBlank()) lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text(line).color(isDanger ? DANGER_RED : GRAY)));
        }
        lore.add(Component.empty());
        switch (entry.type()) {
            case TOGGLE     -> lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɡʟᴇ"));
            case CYCLE_TIER, CYCLE_PERSONALITY -> lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴄʏᴄʟᴇ"));
            case ACTION     -> lore.add(hint("✎ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ ɪɴ ᴄʜᴀᴛ"));
            case IMMEDIATE  -> lore.add(hint("◈ ", "ᴄʟɪᴄᴋ ᴛᴏ ᴄʟᴇᴀʀ"));
            case DANGER     -> lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)
                .append(Component.text("◈ ").color(DANGER_RED)).append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴄᴏɴꜰɪʀᴍ").color(DARK_GRAY)));
        }
        meta.lore(lore); item.setItemMeta(meta); return item;
    }

    private static Component hint(String icon, String text) {
        return Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(icon).color(ACCENT)).append(Component.text(text).color(DARK_GRAY));
    }


    private String valueString(BotEntry entry, FakePlayer bot) {
        return switch (entry.id()) {
            case "frozen"            -> bot.isFrozen()              ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "head_ai_enabled"   -> bot.isHeadAiEnabled()       ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "swim_ai_enabled"   -> bot.isSwimAiEnabled()       ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "pickup_items"      -> bot.isPickUpItemsEnabled()   ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "pickup_xp"         -> bot.isPickUpXpEnabled()      ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "chat_enabled"      -> bot.isChatEnabled()          ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "chat_tier"         -> bot.getChatTier() != null ? bot.getChatTier() : "ʀᴀɴᴅᴏᴍ";
            case "ai_personality"    -> bot.getAiPersonality() != null ? bot.getAiPersonality() : "ᴅᴇꜰᴀᴜʟᴛ";
            case "nav_parkour"       -> bot.isNavParkour()           ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "nav_break_blocks"  -> bot.isNavBreakBlocks()       ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "nav_place_blocks"  -> bot.isNavPlaceBlocks()       ? "✔ ᴇɴᴀʙʟᴇᴅ" : "✘ ᴅɪꜱᴀʙʟᴇᴅ";
            case "rename"            -> bot.getName();
            case "rc_cmd_set", "rc_cmd_clear" -> bot.hasRightClickCommand() ? "/" + bot.getRightClickCommand() : "ɴᴏᴛ ꜱᴇᴛ";
            case "chunk_load_radius" -> {
                int r    = bot.getChunkLoadRadius();
                int gMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
                yield r == -1 ? "ɢʟᴏʙᴀʟ (" + gMax + ")" : r == 0 ? "ᴅɪꜱᴀʙʟᴇᴅ" : r + " ᴄʜᴜɴᴋꜱ";
            }
            case "delete"            -> bot.getName();
            default                  -> "?";
        };
    }

    private boolean getBoolValue(String id, FakePlayer bot) {
        return switch (id) {
            case "frozen"           -> bot.isFrozen();
            case "head_ai_enabled"  -> bot.isHeadAiEnabled();
            case "swim_ai_enabled"  -> bot.isSwimAiEnabled();
            case "pickup_items"     -> bot.isPickUpItemsEnabled();
            case "pickup_xp"        -> bot.isPickUpXpEnabled();
            case "chat_enabled"     -> bot.isChatEnabled();
            case "nav_parkour"      -> bot.isNavParkour();
            case "nav_break_blocks" -> bot.isNavBreakBlocks();
            case "nav_place_blocks" -> bot.isNavPlaceBlocks();
            default -> false;
        };
    }

    private Material dynamicIcon(BotEntry entry, FakePlayer bot) {
        return switch (entry.id()) {
            case "frozen"            -> bot.isFrozen()             ? Material.BLUE_ICE           : Material.PACKED_ICE;
            case "head_ai_enabled"   -> bot.isHeadAiEnabled()      ? Material.PLAYER_HEAD        : Material.SKELETON_SKULL;
            case "swim_ai_enabled"   -> bot.isSwimAiEnabled()      ? Material.WATER_BUCKET       : Material.BUCKET;
            case "pickup_items"      -> bot.isPickUpItemsEnabled()  ? Material.HOPPER             : Material.CHEST;
            case "pickup_xp"         -> bot.isPickUpXpEnabled()     ? Material.EXPERIENCE_BOTTLE  : Material.GLASS_BOTTLE;
            case "chat_enabled"      -> bot.isChatEnabled()         ? Material.WRITABLE_BOOK      : Material.BOOK;
            case "rc_cmd_set"        -> bot.hasRightClickCommand()  ? Material.COMMAND_BLOCK      : Material.REPEATING_COMMAND_BLOCK;
            case "nav_parkour"       -> bot.isNavParkour()          ? Material.SLIME_BALL         : Material.RABBIT_FOOT;
            case "nav_break_blocks"  -> bot.isNavBreakBlocks()      ? Material.DIAMOND_PICKAXE    : Material.IRON_PICKAXE;
            case "nav_place_blocks"  -> bot.isNavPlaceBlocks()      ? Material.GRASS_BLOCK        : Material.DIRT;
            case "chunk_load_radius" -> bot.getChunkLoadRadius() == 0 ? Material.STRUCTURE_VOID   : Material.MAP;
            default                  -> entry.icon();
        };
    }

    private ItemStack buildCategoryTab(int idx, boolean active) {
        BotCategory cat = categories[idx];
        ItemStack item = new ItemStack(active ? cat.activeMat() : cat.inactiveMat());
        ItemMeta  meta = item.getItemMeta();
        if (active) { meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(cat.label()).color(ACCENT).decoration(TextDecoration.BOLD, active)));
        meta.lore(List.of(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(active ? "◈  ᴄᴜʀʀᴇɴᴛʟʏ ᴠɪᴇᴡɪɴɢ" : "ᴄʟɪᴄᴋ ᴛᴏ ꜱᴡɪᴛᴄʜ").color(active ? ON_GREEN : DARK_GRAY))));
        item.setItemMeta(meta); return item;
    }

    private ItemStack buildCatArrow(boolean isNext) {
        Material mat  = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
        TextColor col = isNext ? ON_GREEN : COMING_SOON_COLOR;
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text(isNext ? "▶" : "◄").color(col).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ꜱᴄʀᴏʟʟ ᴄᴀᴛᴇɡᴏʀɪᴇꜱ " + (isNext ? "ꜰᴏʀᴡᴀʀᴅ" : "ʙᴀᴄᴋᴡᴀʀᴅ") + ".").color(DARK_GRAY))));
        item.setItemMeta(meta); return item;
    }

    private ItemStack buildResetButton() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK); ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("⟲  ʀᴇꜱᴇᴛ ʙᴏᴛ").color(YELLOW)));
        meta.lore(List.of(
            Component.empty().decoration(TextDecoration.ITALIC, false).append(Component.text("ʀᴇꜱᴇᴛ ᴀʟʟ ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ").color(GRAY)),
            Component.empty().decoration(TextDecoration.ITALIC, false).append(Component.text("ᴛᴏ ᴅᴇꜰᴀᴜʟᴛ ᴠᴀʟᴜᴇꜱ.").color(GRAY))));
        item.setItemMeta(meta); return item;
    }

    private ItemStack buildCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER); ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("✕  ᴄʟᴏꜱᴇ").color(OFF_RED).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("ᴄʟᴏꜱᴇ ᴛʜᴇ ʙᴏᴛ ꜱᴇᴛᴛɪɴɡꜱ ᴍᴇɴᴜ.").color(DARK_GRAY))));
        item.setItemMeta(meta); return item;
    }

    private static ItemStack glassFiller(Material mat) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()); meta.lore(List.of()); item.setItemMeta(meta); return item;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static List<BotEntry> visibleEntries(BotCategory cat, boolean isOp) {
        if (isOp) return cat.entries();
        return cat.entries().stream().filter(e -> !e.opOnly()).toList();
    }

    private void cleanup(UUID uuid) { sessions.remove(uuid); botSessions.remove(uuid); }

    private boolean isOp(Player player) { return player.isOp() || Perm.has(player, Perm.OP); }

    private void sendActionBarConfirm(Player player, String label, String value) {
        player.sendActionBar(Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(Component.text("✔ ").color(ON_GREEN))
            .append(Component.text(label + "  ").color(WHITE))
            .append(Component.text("→  ").color(DARK_GRAY))
            .append(Component.text(value).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    private static void playUiClick(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, pitch);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Category definitions
    // ═════════════════════════════════════════════════════════════════════════

    private BotCategory general() {
        int globalMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
        return new BotCategory("⚙ ɢᴇɴᴇʀᴀʟ", Material.COMPARATOR, Material.GRAY_DYE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE, List.of(
                BotEntry.toggle("frozen", "ꜰʀᴏᴢᴇɴ",
                    "ʙᴏᴛ ᴄᴀɴɴᴏᴛ ᴍᴏᴠᴇ ᴡʜᴇɴ ꜰʀᴏᴢᴇɴ.\nᴛᴏɢɡʟᴇ ᴛᴏ ᴘᴀᴜꜱᴇ ᴀʟʟ ᴍᴏᴠᴇᴍᴇɴᴛ.",
                    Material.PACKED_ICE, false),
                BotEntry.toggle("head_ai_enabled", "ʜᴇᴀᴅ ᴀɪ (ʟᴏᴏᴋ ᴀᴛ ᴘʟᴀʏᴇʀ)",
                    "ʙᴏᴛ ꜱᴍᴏᴏᴛʜʟʏ ʀᴏᴛᴀᴛᴇꜱ ᴛᴏ ʟᴏᴏᴋ ᴀᴛ\nɴᴇᴀʀʙʏ ᴘʟᴀʏᴇʀꜱ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.\nᴅɪꜱᴀʙʟᴇ ᴛᴏ ᴋᴇᴇᴘ ʜᴇᴀᴅ ꜱᴛᴀᴛɪᴏɴᴀʀʏ.",
                    Material.PLAYER_HEAD, false),
                BotEntry.toggle("swim_ai_enabled", "ꜱᴡɪᴍ ᴀɪ",
                    "ʙᴏᴛ ᴀᴜᴛᴏ-ꜱᴡɪᴍꜱ ᴜᴘᴡᴀʀᴅ ɪɴ ᴡᴀᴛᴇʀ/ʟᴀᴠᴀ\nᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ (ꜱᴘᴀᴄᴇʙᴀʀ ʜᴏʟᴅ).\nᴅɪꜱᴀʙʟᴇ ᴛᴏ ʟᴇᴛ ᴛʜᴇ ʙᴏᴛ ꜱɪɴᴋ.\nɢʟᴏʙᴀʟ: " + (Config.swimAiEnabled() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                    Material.WATER_BUCKET, false),
                BotEntry.action("chunk_load_radius", "ᴄʜᴜɴᴋ ʀᴀᴅɪᴜꜱ",
                    "ʜᴏᴡ ᴍᴀɴʏ ᴄʜᴜɴᴋꜱ ᴛʜɪꜱ ʙᴏᴛ ʟᴏᴀᴅꜱ.\n-1 = ꜰᴏʟʟᴏᴡ ɢʟᴏʙᴀʟ ᴄᴏɴꜰɪɢ\n0  = ᴅɪꜱᴀʙʟᴇᴅ ꜰᴏʀ ᴛʜɪꜱ ʙᴏᴛ\n1-" + globalMax + " = ꜰɪxᴇᴅ ʀᴀᴅɪᴜꜱ (ᴄᴀᴘᴘᴇᴅ ᴀᴛ ɢʟᴏʙᴀʟ ᴍᴀx)",
                    Material.MAP, false),
                BotEntry.toggle("pickup_items", "ᴘɪᴄᴋ ᴜᴘ ɪᴛᴇᴍꜱ",
                    "ᴛʜɪꜱ ʙᴏᴛ ᴘɪᴄᴋꜱ ᴜᴘ ɪᴛᴇᴍ ᴇɴᴛɪᴛɪᴇꜱ\nɪɴᴛᴏ ɪᴛꜱ ɪɴᴠᴇɴᴛᴏʀʏ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.",
                    Material.HOPPER, false),
                BotEntry.toggle("pickup_xp", "ᴘɪᴄᴋ ᴜᴘ xᴘ",
                    "ᴛʜɪꜱ ʙᴏᴛ ᴄᴏʟʟᴇᴄᴛꜱ ᴇxᴘᴇʀɪᴇɴᴄᴇ ᴏʀʙꜱ\nᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ. /ꜰᴘᴘ xᴘ ᴄᴏᴏʟᴅᴏᴡɴ ꜱᴛɪʟʟ ᴀᴘᴘʟɪᴇꜱ.",
                    Material.EXPERIENCE_BOTTLE, false),
                BotEntry.action("rename", "ʀᴇɴᴀᴍᴇ ʙᴏᴛ",
                    "ᴄʜᴀɴɢᴇ ᴛʜᴇ ʙᴏᴛ'ꜱ ᴍɪɴᴇᴄʀᴀꜰᴛ ɴᴀᴍᴇ.\nɴᴀᴍᴇᴛᴀɢ, ᴛᴀʙ ᴀɴᴅ ᴅᴇᴀᴛʜ ᴍᴇꜱꜱᴀɢᴇꜱ ᴜᴘᴅᴀᴛᴇ.",
                    Material.NAME_TAG, false)));
    }

    private BotCategory chat() {
        return new BotCategory("💬 ᴄʜᴀᴛ", Material.WRITABLE_BOOK, Material.BOOK,
            Material.YELLOW_STAINED_GLASS_PANE, List.of(
                BotEntry.toggle("chat_enabled", "ᴄʜᴀᴛ ᴇɴᴀʙʟᴇᴅ",
                    "ʙᴏᴛ ꜱᴇɴᴅꜱ ᴄʜᴀᴛ ᴍᴇꜱꜱᴀɢᴇꜱ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.\nꜰᴀʟꜱᴇ = ᴘᴇʀᴍᴀɴᴇɴᴛʟʏ ꜱɪʟᴇɴᴄᴇᴅ ʙᴏᴛ.",
                    Material.WRITABLE_BOOK, false),
                BotEntry.cycleTier("chat_tier", "ᴄʜᴀᴛ ᴛɪᴇʀ",
                    "ᴛʜᴇ ʙᴏᴛ'ꜱ ᴄʜᴀᴛ ᴀᴄᴛɪᴠɪᴛʏ ʟᴇᴠᴇʟ.\nʀᴀɴᴅᴏᴍ → Qᴜɪᴇᴛ → ᴘᴀꜱꜱɪᴠᴇ → ɴᴏʀᴍᴀʟ\n→ ᴀᴄᴛɪᴠᴇ → ᴄʜᴀᴛᴛʏ → (ʀᴇꜱᴇᴛꜱ ᴛᴏ ʀᴀɴᴅᴏᴍ).",
                    Material.COMPARATOR, false),
                BotEntry.cyclePersonality("ai_personality", "ᴀɪ ᴘᴇʀꜱᴏɴᴀʟɪᴛʏ",
                    "ᴛʜᴇ ʙᴏᴛ'ꜱ ᴄᴏɴᴠᴇʀꜱᴀᴛɪᴏɴ ᴘᴇʀꜱᴏɴᴀʟɪᴛʏ.\nᴄʏᴄʟᴇꜱ ᴛʜʀᴏᴜɢʜ .ᴛxᴛ ꜰɪʟᴇꜱ ɪɴ\nᴘʟᴜɢɪɴꜱ/FakePlayerPlugin/personalities/",
                    Material.KNOWLEDGE_BOOK, false)));
    }

    private BotCategory commands() {
        return new BotCategory("📋 ᴄᴍᴅꜱ", Material.COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, List.of(
                BotEntry.action("rc_cmd_set", "ꜱᴇᴛ ʀɪɡʜᴛ-ᴄʟɪᴄᴋ ᴄᴍᴅ",
                    "ꜱᴛᴏʀᴇ ᴀ ᴄᴏᴍᴍᴀɴᴅ ᴛʜᴀᴛ ʀᴜɴꜱ\nᴡʜᴇɴ ᴀ ᴘʟᴀʏᴇʀ ʀɪɡʜᴛ-ᴄʟɪᴄᴋꜱ ᴛʜᴇ ʙᴏᴛ.\nᴄᴜʀʀᴇɴᴛ ᴄᴏᴍᴍᴀɴᴅ ɪꜱ ꜱʜᴏᴡɴ ᴀʙᴏᴠᴇ.",
                    Material.COMMAND_BLOCK, true),
                BotEntry.immediate("rc_cmd_clear", "ᴄʟᴇᴀʀ ʀɪɡʜᴛ-ᴄʟɪᴄᴋ ᴄᴍᴅ",
                    "ʀᴇᴍᴏᴠᴇ ᴛʜᴇ ꜱᴛᴏʀᴇᴅ ʀɪɡʜᴛ-ᴄʟɪᴄᴋ ᴄᴏᴍᴍᴀɴᴅ\nꜰʀᴏᴍ ᴛʜɪꜱ ʙᴏᴛ.",
                    Material.STRUCTURE_VOID, true)));
    }

    private BotCategory pvp() {
        return new BotCategory("⚔ ᴘᴠᴘ", Material.NETHERITE_SWORD, Material.IRON_SWORD,
            Material.RED_STAINED_GLASS_PANE, List.of(
                // ── Page 1 ────────────────────────────────────────────────────
                BotEntry.comingSoon("pvp_difficulty",     "ᴅɪꜰꜰɪᴄᴜʟᴛʏ",
                    "ᴏᴠᴇʀʀɪᴅᴇ ᴛʜɪꜱ ʙᴏᴛ'ꜱ ꜱᴋɪʟʟ ʟᴇᴠᴇʟ.\nɴᴘᴄ / ᴇᴀꜱʏ / ᴍᴇᴅɪᴜᴍ / ʜᴀʀᴅ / ᴛɪᴇʀ1 / ʜᴀᴄᴋᴇʀ.",
                    Material.DIAMOND_SWORD),
                BotEntry.comingSoon("pvp_combat_mode",    "ᴄᴏᴍʙᴀᴛ ᴍᴏᴅᴇ",
                    "ᴘᴇʀ-ʙᴏᴛ ᴄʀʏꜱᴛᴀʟ / ꜱᴡᴏʀᴅ / ꜰɪꜱᴛ\nᴄᴏᴍʙᴀᴛ ꜱᴛʏʟᴇ ꜱᴇʟᴇᴄᴛɪᴏɴ.",
                    Material.END_CRYSTAL),
                BotEntry.comingSoon("pvp_critting",       "ᴄʀɪᴛᴛɪɴɢ",
                    "ʙᴏᴛ ʟᴀɴᴅꜱ ᴄʀɪᴛɪᴄᴀʟ ʜɪᴛꜱ ʙʏ\nꜰᴀʟʟɪɴɢ ᴅᴜʀɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                    Material.NETHERITE_SWORD),
                BotEntry.comingSoon("pvp_s_tapping",      "ꜱ-ᴛᴀᴘᴘɪɴɢ",
                    "ʙᴏᴛ ᴛᴀᴘꜱ ꜱ ᴅᴜʀɪɴɢ ꜱᴡɪɴɢ\nᴛᴏ ʀᴇꜱᴇᴛ ᴀᴛᴛᴀᴄᴋ ᴄᴏᴏʟᴅᴏᴡɴ.",
                    Material.CLOCK),
                BotEntry.comingSoon("pvp_strafing",       "ꜱᴛʀᴀꜰɪɴɢ",
                    "ʙᴏᴛ ᴄɪʀᴄʟᴇꜱ ᴀʀᴏᴜɴᴅ ᴛʜᴇ ᴛᴀʀɢᴇᴛ\nᴡʜɪʟᴇ ꜰɪɢʜᴛɪɴɢ.",
                    Material.FEATHER),
                BotEntry.comingSoon("pvp_shield",         "ꜱʜɪᴇʟᴅɪɴɢ",
                    "ʙᴏᴛ ᴄᴀʀʀɪᴇꜱ ᴀɴᴅ ᴜꜱᴇꜱ ᴀ ꜱʜɪᴇʟᴅ\nᴛᴏ ʙʟᴏᴄᴋ ɪɴᴄᴏᴍɪɴɢ ᴀᴛᴛᴀᴄᴋꜱ.",
                    Material.SHIELD),
                BotEntry.comingSoon("pvp_speed_buffs",    "ꜱᴘᴇᴇᴅ ʙᴜꜰꜰꜱ",
                    "ʙᴏᴛ ʜᴀꜱ ꜱᴘᴇᴇᴅ & ꜱᴛʀᴇɴɢᴛʜ ᴘᴏᴛɪᴏɴ\nᴇꜰꜰᴇᴄᴛꜱ ᴀᴄᴛɪᴠᴇ.",
                    Material.SUGAR),
                BotEntry.comingSoon("pvp_jump_reset",     "ᴊᴜᴍᴘ ʀᴇꜱᴇᴛ",
                    "ʙᴏᴛ ᴊᴜᴍᴘꜱ ᴊᴜꜱᴛ ʙᴇꜰᴏʀᴇ ꜱᴡɪɴɢɪɴɢ\nᴛᴏ ɢᴀɪɴ ᴛʜᴇ ᴡ-ᴛᴀᴘ ᴋɴᴏᴄᴋʙᴀᴄᴋ ʙᴏɴᴜꜱ.",
                    Material.SLIME_BALL),
                BotEntry.comingSoon("pvp_random",         "ʀᴀɴᴅᴏᴍ ᴘʟᴀʏꜱᴛʏʟᴇ",
                    "ʀᴀɴᴅᴏᴍɪꜱᴇ ᴛᴇᴄʜɴɪQᴜᴇꜱ ᴇᴀᴄʜ ʀᴏᴜɴᴅ\nᴛᴏ ᴋᴇᴇᴘ ᴛʜᴇ ꜰɪɢʜᴛ ᴜɴᴘʀᴇᴅɪᴄᴛᴀʙʟᴇ.",
                    Material.COMPARATOR),
                BotEntry.comingSoon("pvp_gear",           "ɢᴇᴀʀ ᴛʏᴘᴇ",
                    "ʙᴏᴛ ᴡᴇᴀʀꜱ ᴅɪᴀᴍᴏɴᴅ ᴏʀ\nɴᴇᴛʜᴇʀɪᴛᴇ ᴀʀᴍᴏᴜʀ.",
                    Material.DIAMOND_CHESTPLATE),
                BotEntry.comingSoon("pvp_defensive_mode", "ᴅᴇꜰᴇɴꜱɪᴠᴇ ᴍᴏᴅᴇ",
                    "ʙᴏᴛ ᴏɴʟʏ ꜰɪɢʜᴛꜱ ʙᴀᴄᴋ ᴡʜᴇɴ\nᴛʜᴇ ᴘʟᴀʏᴇʀ ᴀᴛᴛᴀᴄᴋꜱ ꜰɪʀꜱᴛ.",
                    Material.BOW),
                BotEntry.comingSoon("pvp_detect_range",   "ᴅᴇᴛᴇᴄᴛ ʀᴀɴɢᴇ",
                    "ʜᴏᴡ ꜰᴀʀ ᴛʜɪꜱ ʙᴏᴛ ꜱᴇᴇꜱ ᴘʟᴀʏᴇʀꜱ\nᴀɴᴅ ʟᴏᴄᴋꜱ ᴏɴ ᴀꜱ ᴛᴀʀɢᴇᴛ.",
                    Material.SPYGLASS),
                BotEntry.comingSoon("pvp_sprint",         "ꜱᴘʀɪɴᴛɪɴɢ",
                    "ʙᴏᴛ ꜱᴘʀɪɴᴛꜱ ᴛᴏᴡᴀʀᴅꜱ ᴛʜᴇ ᴛᴀʀɢᴇᴛ\nᴅᴜʀɪɴɢ ᴄᴏᴍʙᴀᴛ.",
                    Material.GOLDEN_BOOTS),
                BotEntry.comingSoon("pvp_pearl",          "ᴇɴᴅᴇʀ ᴘᴇᴀʀʟ",
                    "ʙᴏᴛ ᴛʜʀᴏᴡꜱ ᴇɴᴅᴇʀ ᴘᴇᴀʀʟꜱ ᴛᴏ\nᴄʟᴏꜱᴇ ᴛʜᴇ ɢᴀᴘ ᴏʀ ᴇꜱᴄᴀᴘᴇ.",
                    Material.ENDER_PEARL),
                BotEntry.comingSoon("pvp_pearl_spam",     "ᴘᴇᴀʀʟ ꜱᴘᴀᴍ",
                    "ʙᴏᴛ ꜱᴘᴀᴍꜱ ᴘᴇᴀʀʟꜱ ɪɴ ʙᴜʀꜱᴛꜱ\nꜰᴏʀ ᴀɢɢʀᴇꜱꜱɪᴠᴇ ɢᴀᴘ-ᴄʟᴏꜱɪɴɢ.",
                    Material.ENDER_EYE),
                BotEntry.comingSoon("pvp_walk_back",      "ᴡᴀʟᴋ ʙᴀᴄᴋᴡᴀʀᴅꜱ",
                    "ʙᴏᴛ ʙᴀᴄᴋꜱ ᴀᴡᴀʏ ᴡʜɪʟᴇ ꜱᴡɪɴɢɪɴɢ\nᴛᴏ ᴄᴏɴᴛʀᴏʟ ᴋɴᴏᴄᴋʙᴀᴄᴋ.",
                    Material.LEATHER_BOOTS),
                BotEntry.comingSoon("pvp_hole_mode",      "ʜᴏʟᴇ ᴍᴏᴅᴇ",
                    "ʙᴏᴛ ᴘᴀᴛʜꜰɪɴᴅꜱ ᴛᴏ ᴀɴ ᴏʙꜱɪᴅɪᴀɴ\nʜᴏʟᴇ ᴛᴏ ᴘʀᴏᴛᴇᴄᴛ ɪᴛꜱᴇʟꜰ.",
                    Material.OBSIDIAN),
                BotEntry.comingSoon("pvp_kit",            "ᴋɪᴛ ᴘʀᴇꜱᴇᴛ",
                    "ꜱᴇʟᴇᴄᴛ ᴛʜɪꜱ ʙᴏᴛ'ꜱ ʟᴏᴀᴅᴏᴜᴛ.\nᴋɪᴛ1 / ᴋɪᴛ2 / ᴋɪᴛ3 / ᴋɪᴛ4.",
                    Material.CHEST),
                // ── Page 2 ────────────────────────────────────────────────────
                BotEntry.comingSoon("pvp_auto_refill",    "ᴀᴜᴛᴏ-ʀᴇꜰɪʟʟ ᴛᴏᴛᴇᴍ",
                    "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇ-ᴇQᴜɪᴘꜱ ᴀ\nᴛᴏᴛᴇᴍ ᴀꜰᴛᴇʀ ᴘᴏᴘᴘɪɴɢ ᴏɴᴇ.",
                    Material.TOTEM_OF_UNDYING),
                BotEntry.comingSoon("pvp_auto_respawn",   "ᴀᴜᴛᴏ-ʀᴇꜱᴘᴀᴡɴ",
                    "ʙᴏᴛ ᴀᴜᴛᴏᴍᴀᴛɪᴄᴀʟʟʏ ʀᴇꜱᴘᴀᴡɴꜱ\nᴀɴᴅ ʀᴇᴊᴏɪɴꜱ ᴀꜰᴛᴇʀ ᴅᴇᴀᴛʜ.",
                    Material.RESPAWN_ANCHOR),
                BotEntry.comingSoon("pvp_spawn_prot",     "ꜱᴘᴀᴡɴ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ",
                    "ʙᴏᴛ ꜱᴛᴀʏꜱ ɪɴᴠᴜʟɴᴇʀᴀʙʟᴇ ꜰᴏʀ\nᴀ ꜱʜᴏʀᴛ ɢʀᴀᴄᴇ ᴘᴇʀɪᴏᴅ ᴀᴛ ꜱᴘᴀᴡɴ.",
                    Material.GRASS_BLOCK),
                BotEntry.comingSoon("pvp_target",         "ᴛᴀʀɢᴇᴛ ᴘʀɪᴏʀɪᴛʏ",
                    "ᴄʜᴏᴏꜱᴇ ᴡʜɪᴄʜ ᴘʟᴀʏᴇʀ ᴛʏᴘᴇ ᴛʜɪꜱ\nʙᴏᴛ ᴘʀɪᴏʀɪᴛɪꜱᴇꜱ ᴀꜱ ᴛᴀʀɢᴇᴛ.",
                    Material.ORANGE_DYE),
                BotEntry.comingSoon("pvp_aggression",     "ᴀɢɢʀᴇꜱꜱɪᴏɴ",
                    "ᴄᴏɴᴛʀᴏʟ ʜᴏᴡ ᴀɢɢʀᴇꜱꜱɪᴠᴇʟʏ ᴛʜɪꜱ\nʙᴏᴛ ᴄʟᴏꜱᴇꜱ ᴅɪꜱᴛᴀɴᴄᴇ ᴏɴ ɪᴛꜱ ᴛᴀʀɢᴇᴛ.",
                    Material.BLAZE_POWDER),
                BotEntry.comingSoon("pvp_flee_health",    "ꜰʟᴇᴇ ʜᴇᴀʟᴛʜ",
                    "ʙᴏᴛ ʀᴇᴛʀᴇᴀᴛꜱ ᴡʜᴇɴ ɪᴛꜱ ʜᴇᴀʟᴛʜ\nᴅʀᴏᴘꜱ ʙᴇʟᴏᴡ ᴛʜɪꜱ ᴠᴀʟᴜᴇ.",
                    Material.RED_DYE),
                BotEntry.comingSoon("pvp_combo_length",   "ᴄᴏᴍʙᴏ ʟᴇɴɢᴛʜ",
                    "ᴍᴀxɪᴍᴜᴍ ʜɪᴛꜱ ɪɴ ᴀ ꜱɪɴɢʟᴇ ʙᴜʀꜱᴛ\nʙᴇꜰᴏʀᴇ ʙᴀᴄᴋɪɴɢ ᴏꜰꜰ.",
                    Material.IRON_SWORD)));
    }

    private BotCategory pathfinding() {
        return new BotCategory("🗺 ᴘᴀᴛʜ", Material.COMPASS, Material.MAP,
            Material.CYAN_STAINED_GLASS_PANE, List.of(
                BotEntry.toggle("nav_parkour", "ᴘᴀʀᴋᴏᴜʀ",
                    "ʙᴏᴛ ꜱᴘʀɪɴᴛ-ᴊᴜᴍᴘꜱ ᴀᴄʀᴏꜱꜱ 1-2 ʙʟᴏᴄᴋ\nɢᴀᴘꜱ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ. ɪɴᴄʀᴇᴀꜱᴇꜱ ᴘᴀᴛʜ\nꜱᴇᴀʀᴄʜ ᴄᴏᴍᴘʟᴇxɪᴛʏ ꜱʟɪɢʜᴛʟʏ.",
                    Material.SLIME_BALL, false),
                BotEntry.toggle("nav_break_blocks", "ʙʀᴇᴀᴋ ʙʟᴏᴄᴋꜱ",
                    "ʙᴏᴛ ʙʀᴇᴀᴋꜱ ꜱᴏʟɪᴅ ʙʟᴏᴄᴋꜱ ᴛʜᴀᴛ ᴏʙꜱᴛʀᴜᴄᴛ\nɪᴛꜱ ᴘᴀᴛʜ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.\nɢʟᴏʙᴀʟ: " + (Config.pathfindingBreakBlocks() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                    Material.IRON_PICKAXE, false),
                BotEntry.toggle("nav_place_blocks", "ᴘʟᴀᴄᴇ ʙʟᴏᴄᴋꜱ",
                    "ʙᴏᴛ ʙʀɪᴅɢᴇꜱ ꜱɪɴɢʟᴇ-ʙʟᴏᴄᴋ ɢᴀᴘꜱ ʙʏ\nᴘʟᴀᴄɪɴɢ ᴀ ʙʟᴏᴄᴋ ᴡʜᴇɴ ᴇɴᴀʙʟᴇᴅ.\nɢʟᴏʙᴀʟ: " + (Config.pathfindingPlaceBlocks() ? "ᴇɴᴀʙʟᴇᴅ" : "ᴅɪꜱᴀʙʟᴇᴅ"),
                    Material.DIRT, false)));
    }

    private BotCategory danger() {
        return new BotCategory("⚠ ᴅᴀɴɢᴇʀ", Material.TNT, Material.GUNPOWDER,
            Material.RED_STAINED_GLASS_PANE, List.of(
                BotEntry.danger("delete", "ᴅᴇʟᴇᴛᴇ ʙᴏᴛ",
                    "⚠ ᴘᴇʀᴍᴀɴᴇɴᴛʟʏ ʀᴇᴍᴏᴠᴇ ᴛʜɪꜱ ʙᴏᴛ.\nᴛʜɪꜱ ᴀᴄᴛɪᴏɴ ᴄᴀɴɴᴏᴛ ʙᴇ ᴜɴᴅᴏɴᴇ.",
                    Material.TNT, true)));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Inner types
    // ═════════════════════════════════════════════════════════════════════════

    private static final class GuiHolder implements InventoryHolder {
        final UUID uuid;
        GuiHolder(UUID uuid) { this.uuid = uuid; }
        @SuppressWarnings("NullableProblems")
        @Override public Inventory getInventory() { return null; }
    }

    private record BotCategory(String label, Material activeMat, Material inactiveMat,
                                Material separatorGlass, List<BotEntry> entries) {}

    private enum BotEntryType { TOGGLE, CYCLE_TIER, CYCLE_PERSONALITY, ACTION, IMMEDIATE, DANGER, COMING_SOON }

    private record BotEntry(String id, String label, String description, Material icon,
                            BotEntryType type, boolean opOnly) {
        static BotEntry toggle(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.TOGGLE, opOnly); }
        static BotEntry cycleTier(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.CYCLE_TIER, opOnly); }
        static BotEntry cyclePersonality(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.CYCLE_PERSONALITY, opOnly); }
        static BotEntry action(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.ACTION, opOnly); }
        static BotEntry immediate(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.IMMEDIATE, opOnly); }
        static BotEntry danger(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.DANGER, opOnly); }
        static BotEntry comingSoon(String id, String label, String desc, Material icon) {
            return new BotEntry(id, label, desc, icon, BotEntryType.COMING_SOON, false); }
    }

    private record ChatInputSes(String inputType, UUID botUuid, int[] guiState, int cleanupTaskId) {}
}

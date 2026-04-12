package me.bill.fakePlayerPlugin.command;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.gui.BotSettingGui;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * /fpp inventory &lt;bot&gt;
 *
 * <p>Opens a double-chest GUI (54 slots) displaying the bot's full inventory:
 * main storage, hotbar, armor, and offhand. Edits sync back to the bot in
 * real-time. Right-clicking a bot entity also opens this GUI directly.
 *
 * <h3>Layout</h3>
 * <pre>
 *  Rows 1-3   Main Storage  (27 slots - bot inventory 9-35)
 *  Row  4     Hotbar        ( 9 slots - bot inventory 0-8)
 *  Row  5     Label Bar     (light-blue + gray glass panes)
 *  Row  6     Equipment     [Boots][Legs][Chest][Helm][·][Offhand][·][·][·]
 * </pre>
 *
 * <h3>Equipment Type Restrictions</h3>
 * <ul>
 *   <li><b>Boots</b>       - only {@code *_BOOTS}</li>
 *   <li><b>Leggings</b>    - only {@code *_LEGGINGS}</li>
 *   <li><b>Chestplate</b>  - only {@code *_CHESTPLATE} or {@code ELYTRA}</li>
 *   <li><b>Helmet</b>      - any item (head slot is unrestricted)</li>
 *   <li><b>Offhand</b>     - any item</li>
 * </ul>
 *
 * <p>Each equipment slot has a light-blue glass pane directly above it (row 5)
 * with a {@code ↓ SlotName} label and lore describing what it accepts.
 * Incompatible items are rejected with an action-bar hint.
 */
public class InventoryCommand implements FppCommand, Listener {
    // ══════════════════════════════════════════════════════════════════════════
    //  Slot mapping & layout
    // ══════════════════════════════════════════════════════════════════════════
    //
    //  Row 1 [ 0- 8]  main storage  (bot slots  9-17)
    //  Row 2 [ 9-17]  main storage  (bot slots 18-26)
    //  Row 3 [18-26]  main storage  (bot slots 27-35)
    //  Row 4 [27-35]  hotbar        (bot slots  0- 8)
    //  Row 5 [36-44]  ── label bar ──  (🔵 = light blue label, ⬜ = gray blank)
    //  Row 6 [45-53]  [Boots][Legs][Chest][Helm][⬜][Offhand][⬜][⬜][⬜]
    //
    //  Row-5 label panes (directly above matching equipment slots):
    //    36 → 🔵 Boots       (above slot 45)
    //    37 → 🔵 Leggings    (above slot 46)
    //    38 → 🔵 Chestplate  (above slot 47)
    //    39 → 🔵 Helmet      (above slot 48)
    //    40 → ⬜ blank       (above separator 49)
    //    41 → 🔵 Offhand     (above slot 50)
    //    42-44 → ⬜ blank    (right-side padding)
    private static final int          GUI_SIZE   = 54;
    private static final int[]        GUI_TO_BOT = new int[GUI_SIZE];
    private static final Set<Integer> DECO       = new HashSet<>();
    private static final Set<Integer> EQUIP_SLOTS = Set.of(45, 46, 47, 48, 50);
    // ── Colour palette  (mirrors SettingGui) ─────────────────────────────────
    private static final TextColor ACCENT     = TextColor.fromHexString("#0079FF");
    private static final TextColor DARK_GRAY  = NamedTextColor.DARK_GRAY;
    private static final TextColor GRAY       = NamedTextColor.GRAY;
    private static final TextColor WHITE      = NamedTextColor.WHITE;
    private static final TextColor OFF_RED    = NamedTextColor.RED;
    private static final TextColor VAL_YELLOW = TextColor.fromHexString("#FFDD57");

    private static final Material  LABEL_MAT  = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    private static final Material  BLANK_MAT  = Material.GRAY_STAINED_GLASS_PANE;
    static {
        Arrays.fill(GUI_TO_BOT, -1);
        // Main storage: bot 9-35 -> GUI 0-26
        for (int i = 0; i < 27; i++) GUI_TO_BOT[i] = i + 9;
        // Hotbar: bot 0-8 -> GUI 27-35
        for (int i = 0; i < 9; i++) GUI_TO_BOT[27 + i] = i;
        // Row 4 all deco
        for (int i = 36; i <= 44; i++) DECO.add(i);
        // Armor: GUI 45=boots, 46=legs, 47=chest, 48=helm -> bot 36-39
        GUI_TO_BOT[45] = 36;
        GUI_TO_BOT[46] = 37;
        GUI_TO_BOT[47] = 38;
        GUI_TO_BOT[48] = 39;
        // GUI 49 separator
        DECO.add(49);
        // Offhand: GUI 50 -> bot 40
        GUI_TO_BOT[50] = 40;
        // Right padding
        DECO.add(51); DECO.add(52); DECO.add(53);
    }
    private final FakePlayerManager manager;
    private final Plugin            plugin;
    private final BotSettingGui     botSettingGui;
    private final Map<UUID, UUID>      sessions = new ConcurrentHashMap<>();
    private final Map<Inventory, UUID> invToBot = new ConcurrentHashMap<>();
    public InventoryCommand(FakePlayerManager manager, Plugin plugin, BotSettingGui botSettingGui) {
        this.manager = manager;
        this.plugin  = plugin;
        this.botSettingGui = botSettingGui;
    }
    // FppCommand
    @Override public String getName()        { return "inventory"; }
    @Override public List<String> getAliases() { return List.of("inv"); }
    @Override public String getUsage()       { return "/fpp inventory <bot>"; }
    @Override public String getDescription() { return "Open a bot's full inventory"; }
    @Override public String getPermission()  { return Perm.INVENTORY; }
    @Override public boolean canUse(CommandSender sender) { return Perm.has(sender, Perm.INVENTORY); }
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Lang.get("inv-player-only")); return false; }
        if (args.length == 0)                   { sender.sendMessage(Lang.get("inv-usage"));        return false; }
        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) { sender.sendMessage(Lang.get("inv-not-found", "name", args[0])); return false; }
        if (fp.getPlayer() == null || fp.isBodyless()) {
            sender.sendMessage(Lang.get("inv-bodyless", "name", fp.getDisplayName()));
            return false;
        }
        openGui(player, fp);
        player.sendMessage(Lang.get("inv-opened", "name", fp.getDisplayName()));
        return true;
    }
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return manager.getActivePlayers().stream()
                    .map(FakePlayer::getName)
                    .filter(n -> n.toLowerCase().startsWith(lower))
                    .toList();
        }
        return List.of();
    }
    // GUI building
    public void openGui(Player viewer, FakePlayer fp) {
        Player botPlayer = fp.getPlayer();
        Component title = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("]  ").color(DARK_GRAY))
                .append(Component.text("\u026A\u0274\u1D20").color(DARK_GRAY).decoration(TextDecoration.BOLD, true))
                .append(Component.text("  ·  ").color(DARK_GRAY))
                .append(Component.text(fp.getName()).color(ACCENT));
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);
        PlayerInventory botInv = botPlayer.getInventory();
        for (int guiSlot = 0; guiSlot < GUI_SIZE; guiSlot++) {
            if (DECO.contains(guiSlot)) {
                gui.setItem(guiSlot, makeDecoItem(guiSlot));
            } else {
                int botSlot = GUI_TO_BOT[guiSlot];
                if (botSlot >= 0) {
                    ItemStack item = botInv.getItem(botSlot);
                    if (item != null && item.getType() != Material.AIR) {
                        gui.setItem(guiSlot, item.clone());
                    }
                    // Empty equipment slots stay empty - label pane above describes the slot
                }
            }
        }
        sessions.put(viewer.getUniqueId(), fp.getUuid());
        invToBot.put(gui, fp.getUuid());
        viewer.openInventory(gui);
        botPlayer.getLocation().getWorld().playSound(
                botPlayer.getLocation(), Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 1.0f);
    }
    // ══════════════════════════════════════════════════════════════════════════
    //  Decoration items  (row-5 label bar)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds decoration items for row 5 (the label bar).
     *
     * <ul>
     *   <li>Slots 36-39, 41 → 🔵 light-blue labeled panes (↓ Boots, ↓ Legs, etc.)</li>
     *   <li>Slots 40, 42-44 → ⬜ gray blank panes (separators)</li>
     *   <li>Row 6 separators (49, 51-53) → ⬜ gray blank panes</li>
     * </ul>
     */
    // ── Small-caps label strings ─────────────────────────────────────────────
    // Boots     = ʙᴏᴏᴛꜱ
    private static final String SC_BOOTS      = "\u0299\u1D0F\u1D0F\u1D1B\uA731";
    // Leggings  = ʟᴇɢɢɪɴɢꜱ
    private static final String SC_LEGGINGS   = "\u029F\u1D07\u0262\u0262\u026A\u0274\u0262\uA731";
    // Chestplate = ᴄʜᴇꜱᴛᴘʟᴀᴛᴇ
    private static final String SC_CHEST      = "\u1D04\u029C\u1D07\uA731\u1D1B\u1D18\u029F\u1D00\u1D1B\u1D07";
    // Helmet    = ʜᴇʟᴍᴇᴛ
    private static final String SC_HELMET     = "\u029C\u1D07\u029F\u1D0D\u1D07\u1D1B";
    // Offhand   = ᴏꜰꜰʜᴀɴᴅ
    private static final String SC_OFFHAND    = "\u1D0F\uA730\uA730\u029C\u1D00\u0274\u1D05";
    // Accepts   = ᴀᴄᴄᴇᴘᴛꜱ
    private static final String SC_ACCEPTS    = "\u1D00\u1D04\u1D04\u1D07\u1D18\u1D1B\uA731";
    // any       = ᴀɴʏ
    private static final String SC_ANY        = "\u1D00\u0274\u028F";
    // or        = ᴏʀ
    private static final String SC_OR         = "\u1D0F\u0280";
    // elytra    = ᴇʟʏᴛʀᴀ
    private static final String SC_ELYTRA     = "\u1D07\u029F\u028F\u1D1B\u0280\u1D00";
    // head slot is unrestricted = ʜᴇᴀᴅ ꜱʟᴏᴛ ɪꜱ ᴜɴʀᴇꜱᴛʀɪᴄᴛᴇᴅ
    private static final String SC_UNRESTR    = "\u029C\u1D07\u1D00\u1D05 \uA731\u029F\u1D0F\u1D1B \u026A\uA731 \u1D1C\u0274\u0280\u1D07\uA731\u1D1B\u0280\u026A\u1D04\u1D1B\u1D07\u1D05";
    // (leather,iron,gold,diamond,netherite,chain)
    private static final String SC_BOOT_TYPES =
            "(\u029F\u1D07\u1D00\u1D1B\u029C\u1D07\u0280, \u026A\u0280\u1D0F\u0274, \u0262\u1D0F\u029F\u1D05," +
            " \u1D05\u026A\u1D00\u1D0D\u1D0F\u0274\u1D05, \u0274\u1D07\u1D1B\u029C\u1D07\u0280\u026A\u1D1B\u1D07," +
            " \u1D04\u029C\u1D00\u026A\u0274)";

    private static ItemStack makeDecoItem(int slot) {
        return switch (slot) {
            case 36 -> equipLabel(SC_BOOTS,    SC_ANY + " " + SC_BOOTS + "  " + SC_BOOT_TYPES);
            case 37 -> equipLabel(SC_LEGGINGS, SC_ANY + " " + SC_LEGGINGS);
            case 38 -> equipLabel(SC_CHEST,    SC_ANY + " " + SC_CHEST + "  " + SC_OR + "  " + SC_ELYTRA);
            case 39 -> equipLabel(SC_HELMET,   SC_ANY + " \u026A\u1D1B\u1D07\u1D0D  \u2014  " + SC_UNRESTR);
            case 41 -> equipLabel(SC_OFFHAND,  SC_ANY + " \u026A\u1D1B\u1D07\u1D0D  \u2014  " + SC_OFFHAND + " \uA731\u029F\u1D0F\u1D1B \u026A\uA731 \u1D1C\u0274\u0280\u1D07\uA731\u1D1B\u0280\u026A\u1D04\u1D1B\u1D07\u1D05");
            default -> blankPane();
        };
    }
    private static ItemStack equipLabel(String title, String restriction) {
        ItemStack item = new ItemStack(LABEL_MAT);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("\u2193 ").color(ACCENT))
                .append(Component.text(title).color(ACCENT).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(
                Component.empty(),
                Component.empty().decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("\u1D00\u1D04\u1D04\u1D07\u1D18\u1D1B\uA731  ").color(DARK_GRAY))
                        .append(Component.text(restriction).color(GRAY))
        ));
        item.setItemMeta(meta);
        return item;
    }
    private static ItemStack blankPane() {
        ItemStack item = new ItemStack(BLANK_MAT);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
    // ══════════════════════════════════════════════════════════════════════════
    //  Equipment slot type validation & helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns true if the item is allowed in the given equipment GUI slot. */
    private static boolean isCompatibleWithSlot(int guiSlot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        String n = item.getType().name();
        return switch (guiSlot) {
            case 45 -> n.endsWith("_BOOTS");
            case 46 -> n.endsWith("_LEGGINGS");
            case 47 -> n.endsWith("_CHESTPLATE") || n.equals("ELYTRA");
            default -> true; // 48 (helmet) and 50 (offhand) accept anything
        };
    }
    private static String slotTypeName(int guiSlot) {
        return switch (guiSlot) {
            case 45 -> "\u1D1A\u1D0F\u1D0F\u1D1B\uA731";               // ʙᴏᴏᴛꜱ
            case 46 -> "\u029F\u1D07\u0262\u0262\u026A\u0274\u0262\uA731"; // ʟᴇɢɢɪɴɢꜱ
            case 47 -> "\u1D04\u029C\u1D07\uA731\u1D1B\u1D18\u029F\u1D00\u1D1B\u1D07  \u1D0F\u0280  \u1D07\u029F\u028F\u1D1B\u0280\u1D00"; // ᴄʜᴇꜱᴛᴘʟᴀᴛᴇ ᴏʀ ᴇʟʏᴛʀᴀ
            default -> "\u026A\u1D1B\u1D07\u1D0D";                      // ɪᴛᴇᴍ
        };
    }
    private static ItemStack getIncomingItem(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> event.getCursor();
            case HOTBAR_SWAP -> {
                if (event.getWhoClicked() instanceof Player p) {
                    int btn = event.getHotbarButton();
                    yield btn >= 0 ? p.getInventory().getItem(btn) : event.getCursor();
                }
                yield null;
            }
            default -> null;
        };
    }
    private static void sendIncompatibleHint(Player player, int guiSlot) {
        player.sendActionBar(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("\uA730\u1D18\u1D18").color(ACCENT))
                .append(Component.text("]  ").color(DARK_GRAY))
                .append(Component.text("\u2717 ").color(OFF_RED))
                .append(Component.text("\u1D1B\u029C\u026A\uA731  \uA731\u029F\u1D0F\u1D1B  \u1D0F\u0274\u029F\u028F  \u1D00\u1D04\u1D04\u1D07\u1D18\u1D1B\uA731  ").color(GRAY))
                .append(Component.text(slotTypeName(guiSlot)).color(VAL_YELLOW).decoration(TextDecoration.BOLD, true)));
    }
    // ══════════════════════════════════════════════════════════════════════════
    //  Sync GUI → bot inventory
    // ══════════════════════════════════════════════════════════════════════════

    private void syncToBotInventory(Inventory gui, PlayerInventory botInv) {
        for (int guiSlot = 0; guiSlot < GUI_SIZE; guiSlot++) {
            if (DECO.contains(guiSlot)) continue;
            int botSlot = GUI_TO_BOT[guiSlot];
            if (botSlot < 0) continue;
            ItemStack item = gui.getItem(guiSlot);
            botInv.setItem(botSlot,
                    (item == null || item.getType() == Material.AIR) ? null : item);
        }
    }
    private void scheduleSync(UUID botUuid, Inventory gui) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            FakePlayer fp = manager.getByUuid(botUuid);
            if (fp == null || fp.getPlayer() == null || fp.isBodyless()) return;
            syncToBotInventory(gui, fp.getPlayer().getInventory());
        });
    }

    /**
     * Refreshes every currently open GUI for the given bot UUID from the bot's live inventory.
     * Use this after external inventory mutations (item pickup, mining/storage transfers, etc.)
     * so viewers do not keep a stale snapshot that would overwrite the real inventory on close.
     */
    public void refreshOpenGui(UUID botUuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            FakePlayer fp = manager.getByUuid(botUuid);
            if (fp == null || fp.getPlayer() == null || fp.isBodyless()) return;
            PlayerInventory botInv = fp.getPlayer().getInventory();
            for (Map.Entry<Inventory, UUID> entry : new HashMap<>(invToBot).entrySet()) {
                if (!botUuid.equals(entry.getValue())) continue;
                Inventory gui = entry.getKey();
                for (int guiSlot = 0; guiSlot < GUI_SIZE; guiSlot++) {
                    if (DECO.contains(guiSlot)) continue;
                    int botSlot = GUI_TO_BOT[guiSlot];
                    if (botSlot < 0) continue;
                    ItemStack item = botInv.getItem(botSlot);
                    gui.setItem(guiSlot, (item == null || item.getType() == Material.AIR) ? null : item.clone());
                }
            }
        });
    }
    // ══════════════════════════════════════════════════════════════════════════
    //  Event listeners
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top     = event.getView().getTopInventory();
        UUID      botUuid = invToBot.get(top);
        if (botUuid == null) return;
        int     rawSlot = event.getRawSlot();
        boolean inTop   = rawSlot >= 0 && rawSlot < GUI_SIZE;
        // Block deco slots
        if (inTop && DECO.contains(rawSlot)) {
            event.setCancelled(true);
            return;
        }
        // Bot must still be alive
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null || fp.getPlayer() == null || fp.isBodyless()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }
        // Type-check for equipment slots
        if (inTop && EQUIP_SLOTS.contains(rawSlot)) {
            InventoryAction action = event.getAction();
            switch (action) {
                case SWAP_WITH_CURSOR, HOTBAR_SWAP, PLACE_ALL, PLACE_ONE, PLACE_SOME -> {
                    ItemStack incoming = getIncomingItem(event);
                    if (incoming != null && incoming.getType() != Material.AIR
                            && !isCompatibleWithSlot(rawSlot, incoming)) {
                        event.setCancelled(true);
                        sendIncompatibleHint(player, rawSlot);
                        return;
                    }
                }
                default -> {}
            }
        }
        scheduleSync(botUuid, top);
    }
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top     = event.getView().getTopInventory();
        UUID      botUuid = invToBot.get(top);
        if (botUuid == null) return;
        // Block deco
        if (event.getRawSlots().stream().anyMatch(DECO::contains)) {
            event.setCancelled(true);
            return;
        }
        // Type-check equipment slots in drag
        for (int slot : event.getRawSlots()) {
            if (EQUIP_SLOTS.contains(slot) && !isCompatibleWithSlot(slot, event.getCursor())) {
                event.setCancelled(true);
                sendIncompatibleHint(player, slot);
                return;
            }
        }
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null || fp.getPlayer() == null || fp.isBodyless()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }
        scheduleSync(botUuid, top);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) return;
        Inventory top     = event.getView().getTopInventory();
        UUID      botUuid = invToBot.remove(top);
        if (botUuid == null) return;
        sessions.remove(viewer.getUniqueId());
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null && fp.getPlayer() != null && !fp.isBodyless()) {
            syncToBotInventory(top, fp.getPlayer().getInventory());
            fp.getPlayer().getLocation().getWorld().playSound(
                    fp.getPlayer().getLocation(),
                    Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5f, 1.0f);
        }
    }
    /** Right-click a bot entity:
     *  - Shift-right-click → open per-bot settings GUI (requires fpp.settings and config enabled)
     *  - Normal right-click with stored command → bot executes it (requires config enabled).
     *  - Normal right-click without command → open inventory GUI (requires fpp.inventory and config enabled). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClickBot(PlayerInteractAtEntityEvent event) {
        // PlayerInteractAtEntityEvent fires once per hand; skip the off-hand duplicate.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player botPlayer)) return;
        FakePlayer fp = manager.getByEntity(botPlayer);
        if (fp == null || fp.isBodyless()) return;

        Player player = event.getPlayer();

        // ── Shift-right-click → bot settings GUI (coming soon) ────────────────
        if (player.isSneaking()
                && me.bill.fakePlayerPlugin.config.Config.isBotShiftRightClickSettingsEnabled()
                && Perm.has(player, Perm.SETTINGS)) {
            event.setCancelled(true);

            // Developer-only preview: only the owner UUID may open the real GUI.
            final java.util.UUID OWNER_UUID =
                    java.util.UUID.fromString("a318f9f4-e2bf-479c-a47a-6a2c1b0b9e66");

            if (player.getUniqueId().equals(OWNER_UUID)) {
                botSettingGui.open(player, fp);
            } else {
                // Coming-soon message for everyone else
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_VILLAGER_NO,
                        org.bukkit.SoundCategory.MASTER, 0.8f, 1.0f);
                player.sendMessage(net.kyori.adventure.text.Component.empty()
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .append(net.kyori.adventure.text.Component.text("⊘ ").color(
                            net.kyori.adventure.text.format.TextColor.fromHexString("#FFA500")))
                    .append(net.kyori.adventure.text.Component.text("ʙᴏᴛ ꜱᴇᴛᴛɪɴɢꜱ ɢᴜɪ  ").color(
                            net.kyori.adventure.text.format.NamedTextColor.WHITE)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false))
                    .append(net.kyori.adventure.text.Component.text("ɪꜱ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ!").color(
                            net.kyori.adventure.text.format.TextColor.fromHexString("#FFA500"))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)));
                player.sendMessage(net.kyori.adventure.text.Component.empty()
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .append(net.kyori.adventure.text.Component.text("  ᴛʜɪꜱ ꜰᴇᴀᴛᴜʀᴇ ɪꜱ ꜱᴛɪʟʟ ɪɴ ᴅᴇᴠᴇʟᴏᴘᴍᴇɴᴛ ᴀɴᴅ ᴡɪʟʟ ʙᴇ").color(
                            net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                player.sendMessage(net.kyori.adventure.text.Component.empty()
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .append(net.kyori.adventure.text.Component.text("  ᴀᴠᴀɪʟᴀʙʟᴇ ɪɴ ᴀ ꜰᴜᴛᴜʀᴇ ᴜᴘᴅᴀᴛᴇ.").color(
                            net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                player.sendActionBar(net.kyori.adventure.text.Component.empty()
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .append(net.kyori.adventure.text.Component.text("⊘  ꜰᴇᴀᴛᴜʀᴇ ᴄᴏᴍɪɴɢ ꜱᴏᴏɴ").color(
                            net.kyori.adventure.text.format.TextColor.fromHexString("#FFA500"))
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)));
            }
            return;
        }

        // Check if right-click interaction is globally disabled
        if (!me.bill.fakePlayerPlugin.config.Config.isBotRightClickEnabled()) return;

        // ── Stored right-click command takes priority ──────────────────────────
        if (fp.hasRightClickCommand()) {
            event.setCancelled(true);
            // Dispatch as the bot - bypasses PlayerCommandPreprocessEvent / BotCommandBlocker.
            Bukkit.dispatchCommand(fp.getPlayer(), fp.getRightClickCommand());
            return;
        }

        // ── No stored command → open inventory GUI (requires fpp.inventory) ────
        if (!Perm.has(player, Perm.INVENTORY)) return;
        event.setCancelled(true);
        openGui(player, fp);
        player.sendMessage(Lang.get("inv-opened", "name", fp.getDisplayName()));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onViewerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBotDeath(PlayerDeathEvent event) {
        FakePlayer fp = manager.getByEntity(event.getEntity());
        if (fp == null) return;
        UUID botUuid = fp.getUuid();
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(sessions).entrySet()) {
            if (botUuid.equals(entry.getValue())) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null) viewer.closeInventory();
            }
        }
    }
}







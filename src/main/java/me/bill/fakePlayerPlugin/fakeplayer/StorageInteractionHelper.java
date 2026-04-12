package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Handles the <em>lock → open-container → transfer → unlock</em> lifecycle shared by
 * {@code MineCommand} (deposit bot→storage) and {@code PlaceCommand} (fetch storage→bot).
 *
 * <h3>Timing</h3>
 * <ol>
 *   <li>Called on arrival tick: bot is teleported to {@code faceLoc}, action-locked,
 *       and rotation is snapped.</li>
 *   <li><b>+1 tick</b>: container is right-clicked via NMS ({@code useItemOn}).</li>
 *   <li><b>+3 ticks</b>: {@code transferFn} runs with the live {@code InventoryHolder}
 *       and bot, the inventory is closed, the action lock is released, the inventory GUI
 *       is refreshed, and {@code onFinally} is called.</li>
 * </ol>
 *
 * <p>All error paths (bot offline, block no longer a container) also call
 * {@code onFinally} so callers can always clean up their own gating state (e.g.
 * {@code job.fetchingFromStorage = false}).</p>
 */
public final class StorageInteractionHelper {

    private StorageInteractionHelper() {}

    /**
     * Starts the storage-interaction sequence.
     *
     * @param fp          the bot
     * @param faceLoc     exact stand + facing position to lock the bot at
     * @param block       the storage block (re-fetched live at transfer time)
     * @param plugin      plugin instance for scheduler access
     * @param manager     fake-player manager (for action lock/unlock)
     * @param transferFn  {@code (liveHolder, liveBot)} — performs the item transfer;
     *                    called BEFORE close/unlock/refresh
     * @param onFinally   always called after the sequence completes (success or failure);
     *                    may be {@code null}
     */
    public static void interact(
            FakePlayer fp,
            Location faceLoc,
            Block block,
            FakePlayerPlugin plugin,
            FakePlayerManager manager,
            BiConsumer<InventoryHolder, Player> transferFn,
            @Nullable Runnable onFinally) {

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            if (onFinally != null) onFinally.run();
            return;
        }

        // Lock position + suppress head-AI
        manager.lockForAction(fp.getUuid(), faceLoc);
        bot.teleport(faceLoc);
        bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);

        // +1 tick: open the container
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player lb = fp.getPlayer();
            if (lb == null || !lb.isOnline()) {
                manager.unlockAction(fp.getUuid());
                if (onFinally != null) onFinally.run();
                return;
            }
            BotNavUtil.useStorageBlock(lb, block);

            // +3 ticks: transfer items, close, unlock
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player liveBot = fp.getPlayer();
                if (liveBot == null || !liveBot.isOnline()) {
                    manager.unlockAction(fp.getUuid());
                    if (onFinally != null) onFinally.run();
                    return;
                }
                Block liveBlock = block.getLocation().getBlock();
                if (!(liveBlock.getState() instanceof InventoryHolder liveHolder)) {
                    manager.unlockAction(fp.getUuid());
                    if (onFinally != null) onFinally.run();
                    return;
                }
                transferFn.accept(liveHolder, liveBot);
                liveBot.closeInventory();
                manager.unlockAction(fp.getUuid());
                if (plugin.getInventoryCommand() != null)
                    plugin.getInventoryCommand().refreshOpenGui(fp.getUuid());
                if (onFinally != null) onFinally.run();
            }, 3L);
        }, 1L);
    }
}


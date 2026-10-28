package com.oneuseanvil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class AnvilListener implements Listener {

    private final JavaPlugin plugin;
    private final AnvilTracker tracker;

    public AnvilListener(JavaPlugin plugin, AnvilTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    // ---------------------------------------------------------------- placing / breaking

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (tracker.isOneUseItem(event.getItemInHand())) {
            tracker.track(event.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Tag.ANVIL.isTagged(block.getType()) || !tracker.isTracked(block)) {
            return;
        }
        // Drop the special item instead of a plain anvil.
        event.setDropItems(false);
        tracker.untrack(block);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), tracker.createItem(1));
        }
    }

    // ---------------------------------------------------------------- using

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("show-open-warning", true)) {
            return;
        }
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        Location loc = anvil.getLocation();
        if (loc != null && tracker.isTracked(loc.getBlock())) {
            event.getPlayer().sendActionBar(
                    Component.text("This anvil will break after one use!", NamedTextColor.GOLD));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        // Raw slot 2 is the anvil's result slot.
        if (event.getRawSlot() != 2) {
            return;
        }
        Location loc = anvil.getLocation();
        if (loc == null || !tracker.isTracked(loc.getBlock())) {
            return;
        }
        ItemStack result = anvil.getResult();
        ItemStack first = anvil.getFirstItem();
        if (result == null || result.getType().isAir() || first == null || first.getType().isAir()) {
            return;
        }

        // Vanilla clears the left input slot when the result is actually taken.
        // If it is still there next tick, the click failed (not enough levels,
        // full cursor, ...) and the anvil survives.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack left = anvil.getFirstItem();
            if (left == null || left.getType().isAir()) {
                Block block = loc.getBlock();
                if (Tag.ANVIL.isTagged(block.getType()) && tracker.isTracked(block)) {
                    for (HumanEntity viewer : new ArrayList<>(anvil.getViewers())) {
                        viewer.closeInventory();
                    }
                    shatter(block);
                }
            }
        });
    }

    private void shatter(Block block) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        BlockData data = block.getBlockData();
        tracker.untrack(block);
        block.setType(Material.AIR);
        world.playSound(center, Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
        world.spawnParticle(Particle.BLOCK, center, 40, 0.3, 0.3, 0.3, data);
    }

    // ---------------------------------------------------------------- gravity

    @EventHandler(ignoreCancelled = true)
    public void onFallingBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        Block block = event.getBlock();
        if (event.getTo().isAir()) {
            // The anvil is starting to fall: carry the "one-use" tag on the entity.
            if (Tag.ANVIL.isTagged(block.getType()) && tracker.isTracked(block)) {
                tracker.untrack(block);
                falling.getPersistentDataContainer()
                        .set(tracker.itemKey(), PersistentDataType.BYTE, (byte) 1);
            }
        } else if (Tag.ANVIL.isTagged(event.getTo())
                && falling.getPersistentDataContainer().has(tracker.itemKey(), PersistentDataType.BYTE)) {
            // It landed: track the new position.
            tracker.track(block);
        }
    }

    // ---------------------------------------------------------------- pistons

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsTracked(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsTracked(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean containsTracked(List<Block> blocks) {
        for (Block b : blocks) {
            if (Tag.ANVIL.isTagged(b.getType()) && tracker.isTracked(b)) {
                return true;
            }
        }
        return false;
    }
}

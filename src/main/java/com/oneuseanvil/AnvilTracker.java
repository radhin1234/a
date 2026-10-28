package com.oneuseanvil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Creates the One-Use Anvil item and remembers which placed anvil blocks are
 * one-use. Placed blocks are stored in the chunk's PersistentDataContainer, so
 * they survive restarts and chunk unloads.
 */
public final class AnvilTracker {

    private final NamespacedKey itemKey;
    private final NamespacedKey blocksKey;

    public AnvilTracker(JavaPlugin plugin) {
        this.itemKey = new NamespacedKey(plugin, "one_use_anvil");
        this.blocksKey = new NamespacedKey(plugin, "tracked_blocks");
    }

    /** Key used to tag the item (and falling-block entities carrying the anvil). */
    public NamespacedKey itemKey() {
        return itemKey;
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(Material.ANVIL, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("One-Use Anvil", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Breaks after a single use.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isOneUseItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public boolean isTracked(Block block) {
        long[] keys = block.getChunk().getPersistentDataContainer()
                .get(blocksKey, PersistentDataType.LONG_ARRAY);
        if (keys == null) {
            return false;
        }
        long key = block.getBlockKey();
        for (long k : keys) {
            if (k == key) {
                return true;
            }
        }
        return false;
    }

    public void track(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long key = block.getBlockKey();
        long[] keys = pdc.get(blocksKey, PersistentDataType.LONG_ARRAY);
        if (keys == null) {
            pdc.set(blocksKey, PersistentDataType.LONG_ARRAY, new long[]{key});
            return;
        }
        for (long k : keys) {
            if (k == key) {
                return;
            }
        }
        long[] grown = Arrays.copyOf(keys, keys.length + 1);
        grown[keys.length] = key;
        pdc.set(blocksKey, PersistentDataType.LONG_ARRAY, grown);
    }

    public void untrack(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] keys = pdc.get(blocksKey, PersistentDataType.LONG_ARRAY);
        if (keys == null) {
            return;
        }
        long key = block.getBlockKey();
        long[] kept = Arrays.stream(keys).filter(k -> k != key).toArray();
        if (kept.length == keys.length) {
            return;
        }
        if (kept.length == 0) {
            pdc.remove(blocksKey);
        } else {
            pdc.set(blocksKey, PersistentDataType.LONG_ARRAY, kept);
        }
    }
}

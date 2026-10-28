package com.oneuseanvil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OneUseAnvilPlugin extends JavaPlugin implements TabExecutor, Listener {

    private AnvilTracker tracker;
    private NamespacedKey recipeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tracker = new AnvilTracker(this);
        recipeKey = new NamespacedKey(this, "one_use_anvil");

        getServer().getPluginManager().registerEvents(new AnvilListener(this, tracker), this);
        getServer().getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("recipe-enabled", true)) {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, tracker.createItem(1));
            recipe.shape("III", " I ", "III");
            recipe.setIngredient('I', Material.IRON_INGOT);
            getServer().addRecipe(recipe);
        }

        var command = getCommand("oneuseanvil");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        getServer().removeRecipe(recipeKey);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("recipe-enabled", true)) {
            event.getPlayer().discoverRecipe(recipeKey);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /" + label + " give [player] [amount]", NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Specify a player from the console.", NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                return true;
            }
        }

        ItemStack item = tracker.createItem(amount);
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        sender.sendMessage(Component.text("Gave " + amount + " One-Use Anvil(s) to " + target.getName() + ".",
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if ("give".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("give");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}

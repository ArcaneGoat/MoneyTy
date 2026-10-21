package fr.moneyty.generators.menu;

import fr.moneyty.generators.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

/** A chest menu. Every click in it is cancelled and routed to {@link #click}. */
public abstract class Menu implements InventoryHolder {

    protected Inventory inv;

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** Called for clicks in the top inventory only. */
    public abstract void click(Player p, int slot, ClickType type);

    protected void fillEmpty(int from, int to) {
        for (int i = from; i <= to; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, Items.filler());
        }
    }

    /** Opening or closing an inventory from a click handler must wait one tick. */
    protected static void later(Plugin pl, Runnable r) {
        Bukkit.getScheduler().runTask(pl, r);
    }
}

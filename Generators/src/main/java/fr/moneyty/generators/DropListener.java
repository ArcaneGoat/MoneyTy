package fr.moneyty.generators;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * Generator drops: sold when the owner picks them up, otherwise they can only travel through hoppers
 * into a generator barrel. They can never end up in a player inventory or another container.
 */
public final class DropListener implements Listener {

    private final GeneratorsPlugin pl;
    private final Set<DataStore.BarrelData> toDrain = new HashSet<>();

    public DropListener(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        ItemStack st = e.getItem().getItemStack();
        if (!st.hasItemMeta()) return;
        PersistentDataContainer pdc = st.getItemMeta().getPersistentDataContainer();
        Integer tier = pdc.get(pl.kDropTier, PersistentDataType.INTEGER);
        if (tier == null) return;

        // These items never go into an inventory: they are turned into money.
        e.setCancelled(true);
        if (!(e.getEntity() instanceof Player p)) return;

        String owner = pdc.get(pl.kDropOwner, PersistentDataType.STRING);
        if (owner == null || !owner.equals(p.getUniqueId().toString())) return;
        if (tier < 1 || tier > GeneratorsPlugin.TIERS) {
            e.getItem().remove();
            return;
        }

        int amount = st.getAmount();
        e.getItem().remove();

        double reward = pl.reward(p.getUniqueId(), tier, amount, 1);
        pl.pay(p.getUniqueId(), reward);
        pl.actionBar(p, "§a+$" + Fmt.money(reward));
    }

    /** Hoppers (and hopper minecarts) may suck up drops; nothing else may. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (pl.dropTier(e.getItem().getItemStack()) == null) return;
        if (e.getInventory().getType() != InventoryType.HOPPER) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        DataStore.BarrelData dest = barrel(e.getDestination());
        boolean isDrop = pl.dropTier(e.getItem()) != null;

        // nothing is pulled out of a generator barrel
        if (barrel(e.getSource()) != null) {
            e.setCancelled(true);
            return;
        }
        if (dest != null) {
            // only drops go in, and only while there is room
            if (!isDrop || dest.total() >= pl.barrelCapacity(dest)) {
                e.setCancelled(true);
                return;
            }
            scheduleDrain(dest);
            return;
        }
        // drops may only travel from hopper to hopper
        if (isDrop && e.getDestination().getType() != InventoryType.HOPPER) e.setCancelled(true);
    }

    private DataStore.BarrelData barrel(Inventory inv) {
        if (inv == null || inv.getType() != InventoryType.BARREL) return null;
        Location loc = inv.getLocation();
        return loc == null ? null : pl.store().barrelAt(loc.getBlock());
    }

    /** The item lands in the barrel block after the event: move it to the virtual storage next tick. */
    private void scheduleDrain(DataStore.BarrelData br) {
        if (!toDrain.add(br)) return;
        Bukkit.getScheduler().runTask(pl, () -> {
            toDrain.remove(br);
            if (pl.store().isPlaced(br)) pl.drainBarrel(br);
        });
    }

    // ---------- drops can't be taken out of hoppers by hand ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (pl.dropTier(e.getCurrentItem()) != null || pl.dropTier(e.getCursor()) != null) {
            e.setCancelled(true);
            return;
        }
        int hotbar = e.getHotbarButton();
        if (hotbar >= 0 && pl.dropTier(e.getWhoClicked().getInventory().getItem(hotbar)) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (pl.dropTier(e.getOldCursor()) != null) e.setCancelled(true);
    }
}

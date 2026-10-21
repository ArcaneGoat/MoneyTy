package fr.generateurs;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PickupListener implements Listener {

    private final GeneratorsPlugin pl;

    public PickupListener(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        ItemStack st = e.getItem().getItemStack();
        if (!st.hasItemMeta()) return;
        PersistentDataContainer pdc = st.getItemMeta().getPersistentDataContainer();
        Integer tier = pdc.get(pl.kDropTier, PersistentDataType.INTEGER);
        if (tier == null) return;

        // Ces items ne vont jamais dans l'inventaire : ils sont convertis en argent.
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onHopper(InventoryPickupItemEvent e) {
        ItemStack st = e.getItem().getItemStack();
        if (!st.hasItemMeta()) return;
        if (st.getItemMeta().getPersistentDataContainer().has(pl.kDropTier, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
        }
    }
}

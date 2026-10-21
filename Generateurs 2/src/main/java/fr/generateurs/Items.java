package fr.generateurs;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class Items {

    private final GeneratorsPlugin pl;

    public Items(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    public ItemStack generator(int tier, int amount) {
        Material m = pl.tiers().material(tier);
        ItemStack st = new ItemStack(m, amount);
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName("§6Générateur §e#" + tier + " §7(" + Fmt.pretty(m) + ")");
        meta.setLore(List.of(
                "§7Produit §a$" + Fmt.money(pl.tiers().value(tier)) + " §7par item",
                "§7Un item toutes les §f" + pl.intervalSeconds() + " s",
                "§8Clic droit : casser",
                "§8Clic gauche : voir la production",
                "§8Maj + clic gauche : améliorer"));
        meta.getPersistentDataContainer().set(pl.kTier, PersistentDataType.INTEGER, tier);
        st.setItemMeta(meta);
        return st;
    }

    public ItemStack barrel(int level) {
        ItemStack st = new ItemStack(Material.BARREL);
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName("§6Barrel §ex" + level);
        meta.setLore(List.of(
                "§7Stocke les drops de tes générateurs",
                "§7Capacité : §f" + pl.barrelCapacity(level) + " items",
                "§7Multiplicateur à la récupération : §ax" + level,
                "§8Clic droit : récupérer les gains",
                "§8Maj + clic droit : reprendre le barrel"));
        meta.getPersistentDataContainer().set(pl.kBarrel, PersistentDataType.INTEGER, level);
        st.setItemMeta(meta);
        return st;
    }

    public ItemStack beacon(double bonus, long remainingSeconds) {
        ItemStack st = new ItemStack(Material.BEACON);
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName("§bBeacon §e" + Fmt.percent(bonus));
        meta.setLore(List.of(
                "§7Bonus de gains : §a" + Fmt.percent(bonus),
                "§7Durée restante : §f" + Fmt.duration(remainingSeconds),
                "§7Le compte à rebours démarre à la pose",
                "§8Maj + clic droit : reprendre le beacon"));
        meta.getPersistentDataContainer().set(pl.kBeaconBonus, PersistentDataType.DOUBLE, bonus);
        meta.getPersistentDataContainer().set(pl.kBeaconRemaining, PersistentDataType.LONG, remainingSeconds);
        st.setItemMeta(meta);
        return st;
    }
}

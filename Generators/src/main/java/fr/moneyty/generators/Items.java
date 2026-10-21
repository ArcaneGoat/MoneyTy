package fr.moneyty.generators;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
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
        meta.setDisplayName("§6Generator §e#" + tier + " §7(" + Fmt.pretty(m) + ")");
        meta.setLore(List.of(
                "§7Produces §a$" + Fmt.money(pl.tiers().value(tier)) + " §7per drop",
                "§7One drop every §f" + pl.intervalSeconds() + "s",
                "§8Left click: pick up",
                "§8Right click: view production",
                "§8Shift + right click: upgrade"));
        meta.getPersistentDataContainer().set(pl.kTier, PersistentDataType.INTEGER, tier);
        st.setItemMeta(meta);
        return st;
    }

    public ItemStack barrel(int level) {
        ItemStack st = new ItemStack(Material.BARREL);
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName("§6Barrel §ex" + level);
        meta.setLore(List.of(
                "§7Stores the generator drops that",
                "§7hoppers bring into it",
                "§7Capacity: §f" + Fmt.count(pl.barrelBaseCapacity(level)) + " drops",
                "§7Sell multiplier: §ax" + level,
                "§8Click: open the barrel menu"));
        meta.getPersistentDataContainer().set(pl.kBarrel, PersistentDataType.INTEGER, level);
        st.setItemMeta(meta);
        return st;
    }

    public ItemStack beacon(double bonus, long remainingSeconds, BoostType type) {
        ItemStack st = new ItemStack(Material.BEACON);
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName("§bBeacon §e" + Fmt.percent(bonus));
        meta.setLore(List.of(
                "§7Boost: §a" + Fmt.percent(bonus),
                "§7Boosts: §f" + type.display + " §8(change it once placed)",
                "§7Time left: §f" + Fmt.duration(remainingSeconds),
                "§7The countdown starts when placed",
                "§8Right click: beacon menu"));
        meta.getPersistentDataContainer().set(pl.kBeaconBonus, PersistentDataType.DOUBLE, bonus);
        meta.getPersistentDataContainer().set(pl.kBeaconRemaining, PersistentDataType.LONG, remainingSeconds);
        meta.getPersistentDataContainer().set(pl.kBeaconType, PersistentDataType.STRING, type.name());
        st.setItemMeta(meta);
        return st;
    }

    // ---------- menu icons ----------

    public static ItemStack icon(Material m, String name, String... lore) {
        return icon(m, name, Arrays.asList(lore));
    }

    public static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack st = new ItemStack(m);
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        st.setItemMeta(meta);
        return st;
    }

    public static ItemStack glow(ItemStack st) {
        // looked up by key: the constant was renamed in 1.20.5 (DURABILITY -> UNBREAKING)
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (e == null) return st;
        ItemMeta meta = st.getItemMeta();
        meta.addEnchant(e, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        st.setItemMeta(meta);
        return st;
    }

    public static ItemStack filler() {
        return icon(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}

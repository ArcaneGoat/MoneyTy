package fr.generateurs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Menu /gen : 100 generateurs sur 3 pages (45 par page). */
public final class ShopGui implements Listener {

    private static final int PER_PAGE = 45;
    private static final int PAGES = (GeneratorsPlugin.TIERS + PER_PAGE - 1) / PER_PAGE;

    private static final class Holder implements InventoryHolder {
        final int page;
        Inventory inv;

        Holder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final GeneratorsPlugin pl;

    public ShopGui(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    public void open(Player p, int page) {
        page = Math.max(0, Math.min(page, PAGES - 1));
        Holder holder = new Holder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, "§8Générateurs §7- page " + (page + 1) + "/" + PAGES);
        holder.inv = inv;

        for (int i = 0; i < PER_PAGE; i++) {
            int tier = page * PER_PAGE + i + 1;
            if (tier > GeneratorsPlugin.TIERS) break;
            inv.setItem(i, icon(tier));
        }

        if (page > 0) inv.setItem(45, named(Material.ARROW, "§ePage précédente"));
        if (page < PAGES - 1) inv.setItem(53, named(Material.ARROW, "§ePage suivante"));

        ItemStack info = named(Material.PAPER, "§6Tes générateurs");
        ItemMeta im = info.getItemMeta();
        im.setLore(List.of(
                "§7Posés : §f" + pl.store().countGens(p.getUniqueId()) + "§7/§f" + pl.maxGenerators(p),
                "§7Beacons actifs : §f" + pl.store().countBeacons(p.getUniqueId()) + "§7/§f" + pl.maxBeacons(),
                "§7Bonus beacons : §a" + Fmt.percent(pl.store().beaconBonus(p.getUniqueId()))));
        info.setItemMeta(im);
        inv.setItem(49, info);

        p.openInventory(inv);
    }

    private ItemStack named(Material m, String name) {
        ItemStack st = new ItemStack(m);
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName(name);
        st.setItemMeta(meta);
        return st;
    }

    private ItemStack icon(int tier) {
        Tiers t = pl.tiers();
        ItemStack st = new ItemStack(t.material(tier));
        ItemMeta meta = st.getItemMeta();
        meta.setDisplayName("§6#" + tier + " §f" + Fmt.pretty(t.material(tier)));
        long payback = t.priceMultiplier() * pl.intervalSeconds();
        meta.setLore(List.of(
                "§7Prix : §6$" + Fmt.money(t.price(tier)),
                "§7Produit : §a$" + Fmt.money(t.value(tier)) + " §7toutes les §f" + pl.intervalSeconds() + " s",
                "§7Rentabilisé en : §f" + Fmt.duration(payback),
                tier < GeneratorsPlugin.TIERS
                        ? "§7Amélioration vers #" + (tier + 1) + " : §6$" + Fmt.money(t.upgradeCost(tier))
                        : "§7Palier maximum",
                "",
                "§eClic pour acheter"));
        st.setItemMeta(meta);
        return st;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getSlot();
        if (slot < PER_PAGE) {
            int tier = holder.page * PER_PAGE + slot + 1;
            if (tier < 1 || tier > GeneratorsPlugin.TIERS) return;
            long price = pl.tiers().price(tier);
            if (!pl.withdraw(p, price)) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cIl te faut §6$" + Fmt.money(price) + "§c.");
                return;
            }
            pl.give(p, pl.items().generator(tier, 1));
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aAchat : générateur §e#" + tier + " §a(§6-$" + Fmt.money(price) + "§a).");
        } else if (slot == 45 && holder.page > 0) {
            int target = holder.page - 1;
            Bukkit.getScheduler().runTask(pl, () -> open(p, target));
        } else if (slot == 53 && holder.page < PAGES - 1) {
            int target = holder.page + 1;
            Bukkit.getScheduler().runTask(pl, () -> open(p, target));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}

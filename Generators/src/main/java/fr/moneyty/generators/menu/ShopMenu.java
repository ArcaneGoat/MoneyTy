package fr.moneyty.generators.menu;

import fr.moneyty.generators.BoostType;
import fr.moneyty.generators.Fmt;
import fr.moneyty.generators.GeneratorsPlugin;
import fr.moneyty.generators.Items;
import fr.moneyty.generators.Tiers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** /gen menu: 100 generators on 3 pages (45 per page). */
public final class ShopMenu extends Menu {

    private static final int PER_PAGE = 45;
    private static final int PAGES = (GeneratorsPlugin.TIERS + PER_PAGE - 1) / PER_PAGE;

    private final GeneratorsPlugin pl;
    private final int page;

    public ShopMenu(GeneratorsPlugin pl, Player p, int page) {
        this.pl = pl;
        this.page = Math.max(0, Math.min(page, PAGES - 1));
        inv = Bukkit.createInventory(this, 54, "§8Generators §7- page " + (this.page + 1) + "/" + PAGES);

        for (int i = 0; i < PER_PAGE; i++) {
            int tier = this.page * PER_PAGE + i + 1;
            if (tier > GeneratorsPlugin.TIERS) break;
            inv.setItem(i, icon(tier));
        }

        if (this.page > 0) inv.setItem(45, Items.icon(Material.ARROW, "§ePrevious page"));
        if (this.page < PAGES - 1) inv.setItem(53, Items.icon(Material.ARROW, "§eNext page"));

        UUID id = p.getUniqueId();
        inv.setItem(49, Items.icon(Material.PAPER, "§6Your generators",
                "§7Placed: §f" + pl.store().countGens(id) + "§7/§f" + pl.maxGenerators(p),
                "§7Active beacons: §f" + pl.store().countBeacons(id) + "§7/§f" + pl.maxBeacons(id),
                "§7Money boost: §a" + Fmt.percent(pl.store().beaconBonus(id, BoostType.MONEY)),
                "§7Speed boost: §a" + Fmt.percent(pl.store().beaconBonus(id, BoostType.SPEED))));
        fillEmpty(45, 53);
    }

    private ItemStack icon(int tier) {
        Tiers t = pl.tiers();
        return Items.icon(t.material(tier), "§6#" + tier + " §f" + Fmt.pretty(t.material(tier)),
                "§7Price: §6$" + Fmt.money(t.price(tier)),
                "§7Produces: §a$" + Fmt.money(t.value(tier)) + " §7every §f" + pl.intervalSeconds() + "s",
                tier < GeneratorsPlugin.TIERS
                        ? "§7Upgrade to #" + (tier + 1) + ": §6$" + Fmt.money(t.upgradeCost(tier))
                        : "§7Max tier",
                "",
                "§eClick to buy");
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (slot < PER_PAGE) {
            int tier = page * PER_PAGE + slot + 1;
            if (tier < 1 || tier > GeneratorsPlugin.TIERS) return;
            long price = pl.tiers().price(tier);
            if (!pl.withdraw(p, price)) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cYou need §6$" + Fmt.money(price) + "§c.");
                return;
            }
            pl.give(p, pl.items().generator(tier, 1));
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aBought generator §e#" + tier + " §a(§6-$" + Fmt.money(price) + "§a).");
        } else if (slot == 45 && page > 0) {
            later(pl, () -> new ShopMenu(pl, p, page - 1).open(p));
        } else if (slot == 53 && page < PAGES - 1) {
            later(pl, () -> new ShopMenu(pl, p, page + 1).open(p));
        }
    }
}

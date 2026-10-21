package fr.moneyty.generators.menu;

import fr.moneyty.generators.BoostType;
import fr.moneyty.generators.DataStore;
import fr.moneyty.generators.Fmt;
import fr.moneyty.generators.GeneratorsPlugin;
import fr.moneyty.generators.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Content of a barrel, one icon per generator tier, plus sell / pick up buttons. */
public final class BarrelMenu extends Menu {

    private static final int INFO = 45, SELL = 48, PICKUP = 50, CLOSE = 53;

    private final GeneratorsPlugin pl;
    private final DataStore.BarrelData br;

    public BarrelMenu(GeneratorsPlugin pl, DataStore.BarrelData br) {
        this.pl = pl;
        this.br = br;
        pl.drainBarrel(br);
        inv = Bukkit.createInventory(this, 54, "§8Barrel §7(x" + br.level + ")");

        List<Integer> tiersDesc = new ArrayList<>(br.stored.keySet());
        tiersDesc.sort(Comparator.reverseOrder());
        int slot = 0;
        for (int tier : tiersDesc) {
            if (slot >= 45) break;
            long n = br.stored.get(tier);
            ItemStack icon = Items.icon(pl.tiers().material(tier), "§6#" + tier + " §f" + Fmt.pretty(pl.tiers().material(tier)),
                    "§7Stored: §f" + Fmt.count(n) + " drops",
                    "§7Worth: §a$" + Fmt.money(pl.reward(br.owner, tier, n, br.level)));
            icon.setAmount((int) Math.max(1, Math.min(64, n)));
            inv.setItem(slot++, icon);
        }

        long total = br.total();
        long cap = pl.barrelCapacity(br);
        List<String> info = new ArrayList<>(List.of(
                "§7Content: §f" + Fmt.count(total) + " §7/ §f" + Fmt.count(cap) + " drops",
                "§7Sell multiplier: §ax" + br.level,
                "§7Capacity boost: §a" + Fmt.percent(pl.store().beaconBonus(br.owner, BoostType.CAPACITY)),
                "",
                "§8Only drops brought in by hoppers",
                "§8are stored in the barrel."));
        if (tiersDesc.size() > 45) info.add("§8(" + (tiersDesc.size() - 45) + " more tiers not shown)");
        if (total >= cap) info.add(1, "§cThe barrel is full!");
        inv.setItem(INFO, Items.icon(Material.BARREL, "§6Barrel §ex" + br.level, info));

        inv.setItem(SELL, Items.icon(Material.EMERALD_BLOCK, "§aSell all",
                "§7Total: §a$" + Fmt.money(pl.barrelWorth(br)), "", "§eClick to sell"));
        inv.setItem(PICKUP, Items.icon(Material.HOPPER, "§cPick up the barrel",
                "§7The content is sold first", "", "§eClick to pick up"));
        inv.setItem(CLOSE, Items.icon(Material.BARRIER, "§cClose"));
        fillEmpty(45, 53);
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (slot == CLOSE) {
            later(pl, p::closeInventory);
            return;
        }
        if (slot != SELL && slot != PICKUP) return;
        if (!pl.store().isPlaced(br)) {
            later(pl, p::closeInventory);
            return;
        }

        double total = pl.collectBarrel(br);
        if (total > 0) {
            pl.pay(br.owner, total);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§a+$" + Fmt.money(total) + " §7(barrel §ex" + br.level + "§7).");
        } else if (slot == SELL) {
            p.sendMessage(GeneratorsPlugin.PREFIX + "§7The barrel is empty.");
        }

        if (slot == SELL) {
            later(pl, () -> new BarrelMenu(pl, br).open(p));
            return;
        }

        pl.store().removeBarrel(br);
        Block b = br.block();
        if (b != null) b.setType(Material.AIR);
        pl.give(p, pl.items().barrel(br.level));
        p.sendMessage(GeneratorsPlugin.PREFIX + "§7Barrel picked up.");
        later(pl, p::closeInventory);
    }
}

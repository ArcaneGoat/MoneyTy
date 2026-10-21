package fr.moneyty.generators.menu;

import fr.moneyty.generators.DataStore;
import fr.moneyty.generators.Fmt;
import fr.moneyty.generators.GeneratorsPlugin;
import fr.moneyty.generators.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** "While you were away": what each generator produced offline, with a claim button. */
public final class OfflineMenu extends Menu {

    private static final int TIME = 45, CLAIM = 49, LATER = 53;

    private final GeneratorsPlugin pl;

    public OfflineMenu(GeneratorsPlugin pl, Player p) {
        this.pl = pl;
        UUID id = p.getUniqueId();
        DataStore.OfflineReward r = pl.store().pending(id);
        inv = Bukkit.createInventory(this, 54, "§8While you were away");
        if (r == null) r = new DataStore.OfflineReward(0);

        List<Integer> tiersDesc = new ArrayList<>(r.drops.keySet());
        tiersDesc.sort(Comparator.reverseOrder());
        int slot = 0;
        for (int tier : tiersDesc) {
            if (slot >= 45) break;
            long drops = r.drops.get(tier);
            int gens = r.gens.getOrDefault(tier, 1);
            ItemStack icon = Items.icon(pl.tiers().material(tier), "§6#" + tier + " §f" + Fmt.pretty(pl.tiers().material(tier)),
                    "§7Generators: §f" + gens,
                    "§7Produced: §e" + Fmt.count(drops) + " drops",
                    "§7Base worth: §a$" + Fmt.money(pl.tiers().value(tier) * (double) drops));
            icon.setAmount(Math.max(1, Math.min(64, gens)));
            inv.setItem(slot++, icon);
        }

        long max = pl.offlineMaxSeconds();
        inv.setItem(TIME, Items.icon(Material.CLOCK, "§eTime away",
                "§f" + Fmt.duration(r.seconds),
                max > 0 ? "§7Counted: up to §f" + Fmt.duration(max) : "§7Counted: all of it",
                "§7Offline penalty: §c-" + Math.round(pl.offlinePenalty() * 100) + "%"));

        DataStore.BarrelData br = pl.store().barrelOf(id);
        List<String> lore = new ArrayList<>();
        lore.add("§7Total: §e" + Fmt.count(r.totalDrops()) + " drops");
        if (br != null) {
            long room = Math.max(0, pl.barrelCapacity(br) - br.total());
            lore.add("§7Goes into your barrel §ex" + br.level + " §7(" + Fmt.count(room) + " free)");
            lore.add("§8What doesn't fit is sold at x1");
        } else {
            lore.add("§cNo barrel placed: §7sold at x1");
        }
        lore.add("");
        lore.add("§eClick to claim");
        inv.setItem(CLAIM, Items.glow(Items.icon(Material.CHEST, "§aClaim drops", lore)));
        inv.setItem(LATER, Items.icon(Material.ARROW, "§7Later", "§8Reopen with /gen offline"));
        fillEmpty(45, 53);
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (slot == CLAIM) {
            pl.claimOffline(p);
            later(pl, p::closeInventory);
        } else if (slot == LATER) {
            later(pl, p::closeInventory);
        }
    }
}

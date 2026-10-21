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

/** Lets the owner choose what a placed beacon boosts, or pick it up. */
public final class BeaconMenu extends Menu {

    private static final int INFO = 4, PICKUP = 22;
    private static final int[] TYPE_SLOTS = {11, 13, 15};

    private final GeneratorsPlugin pl;
    private final DataStore.BeaconData bc;

    public BeaconMenu(GeneratorsPlugin pl, DataStore.BeaconData bc) {
        this.pl = pl;
        this.bc = bc;
        inv = Bukkit.createInventory(this, 27, "§8Beacon §7(" + Fmt.percent(bc.bonus) + ")");

        inv.setItem(INFO, Items.icon(Material.BEACON, "§bBeacon §e" + Fmt.percent(bc.bonus),
                "§7Boosts: §f" + bc.type.display,
                "§7Expires in: §f" + Fmt.duration(remaining()),
                "§7Your total " + bc.type.display.toLowerCase() + " boost: §a"
                        + Fmt.percent(pl.store().beaconBonus(bc.owner, bc.type))));

        BoostType[] types = BoostType.values();
        for (int i = 0; i < types.length && i < TYPE_SLOTS.length; i++) {
            BoostType t = types[i];
            boolean selected = t == bc.type;
            ItemStack icon = Items.icon(t.icon, (selected ? "§a" : "§e") + t.display + " boost",
                    "§7" + Fmt.percent(bc.bonus) + " " + t.description,
                    "",
                    selected ? "§aSelected" : "§eClick to select");
            inv.setItem(TYPE_SLOTS[i], selected ? Items.glow(icon) : icon);
        }

        inv.setItem(PICKUP, Items.icon(Material.HOPPER, "§cPick up the beacon",
                "§7The remaining time is kept", "", "§eClick to pick up"));
        fillEmpty(0, 26);
    }

    private long remaining() {
        return Math.max(0, (bc.expires - System.currentTimeMillis()) / 1000L);
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (!pl.store().isPlaced(bc)) {
            later(pl, p::closeInventory);
            return;
        }

        if (slot == PICKUP) {
            long remaining = remaining();
            pl.store().removeBeacon(bc);
            Block b = bc.block();
            if (b != null) b.setType(Material.AIR);
            pl.give(p, pl.items().beacon(bc.bonus, remaining, bc.type));
            p.sendMessage(GeneratorsPlugin.PREFIX + "§7Beacon picked up (§f" + Fmt.duration(remaining) + " §7left).");
            later(pl, p::closeInventory);
            return;
        }

        BoostType[] types = BoostType.values();
        for (int i = 0; i < types.length && i < TYPE_SLOTS.length; i++) {
            if (slot != TYPE_SLOTS[i] || types[i] == bc.type) continue;
            bc.type = types[i];
            pl.store().markDirty();
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aThis beacon now boosts the §e" + bc.type.description + "§a.");
            later(pl, () -> new BeaconMenu(pl, bc).open(p));
            return;
        }
    }
}

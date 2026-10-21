package fr.moneyty.generators;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Periodic task: every generator (loaded chunk, owner online) drops its item on top of itself.
 * The drop is sold when its owner picks it up, or stored when hoppers carry it into the owner's barrel.
 * Offline owners are handled at reconnection (see SessionListener).
 */
public final class Production {

    private final GeneratorsPlugin pl;

    public Production(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    public void tick() {
        DataStore store = pl.store();
        Tiers tiers = pl.tiers();
        Map<UUID, Double> speed = new HashMap<>();

        for (DataStore.Gen g : store.gensSnapshot()) {
            World w = Bukkit.getWorld(g.world);
            if (w == null || !w.isChunkLoaded(g.x >> 4, g.z >> 4)) continue;

            Block b = w.getBlockAt(g.x, g.y, g.z);
            Material expected = tiers.material(g.tier);
            if (b.getType() != expected) {
                pl.getLogger().warning("Generator missing at " + g.world + " " + g.x + " " + g.y + " " + g.z
                        + " (tier " + g.tier + ", owner " + g.owner + "): removed from data.");
                store.removeGen(g);
                continue;
            }
            if (Bukkit.getPlayer(g.owner) == null) continue;

            // SPEED beacons: +50% = 1.5 drops per cycle on average
            g.progress += 1.0 + speed.computeIfAbsent(g.owner, o -> store.beaconBonus(o, BoostType.SPEED));
            int amount = (int) g.progress;
            if (amount <= 0) continue;
            g.progress -= amount;

            ItemStack stack = new ItemStack(expected, Math.min(amount, expected.getMaxStackSize()));
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName("§a+$" + Fmt.money(tiers.value(g.tier)));
            meta.getPersistentDataContainer().set(pl.kDropTier, PersistentDataType.INTEGER, g.tier);
            meta.getPersistentDataContainer().set(pl.kDropOwner, PersistentDataType.STRING, g.owner.toString());
            stack.setItemMeta(meta);

            Location loc = new Location(w, g.x + 0.5, g.y + 1.0, g.z + 0.5);
            w.dropItem(loc, stack, item -> {
                item.setVelocity(new Vector(0, 0.1, 0));
                item.setPickupDelay(10);
            });
        }
    }
}

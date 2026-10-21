package fr.generateurs;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/** Tache periodique : chaque generateur (chunk charge) produit un item. */
public final class Production {

    private final GeneratorsPlugin pl;

    public Production(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    public void tick() {
        DataStore store = pl.store();
        Tiers tiers = pl.tiers();
        Map<UUID, DataStore.BarrelData> barrelByOwner = store.barrelsByOwner();

        for (DataStore.Gen g : store.gensSnapshot()) {
            World w = Bukkit.getWorld(g.world);
            if (w == null || !w.isChunkLoaded(g.x >> 4, g.z >> 4)) continue;

            Block b = w.getBlockAt(g.x, g.y, g.z);
            Material expected = tiers.material(g.tier);
            if (b.getType() != expected) {
                pl.getLogger().warning("Generateur disparu en " + g.world + " " + g.x + " " + g.y + " " + g.z
                        + " (palier " + g.tier + ", proprietaire " + g.owner + ") : retire des donnees.");
                store.removeGen(g);
                continue;
            }

            // Barrel : les drops y sont stockes tant qu'il reste de la place
            DataStore.BarrelData br = barrelByOwner.get(g.owner);
            if (br != null && br.total() < pl.barrelCapacity(br.level)) {
                br.add(g.tier, 1);
                store.markDirty();
                continue;
            }

            // Sinon : item au sol, a ramasser par le proprietaire
            ItemStack stack = new ItemStack(expected);
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

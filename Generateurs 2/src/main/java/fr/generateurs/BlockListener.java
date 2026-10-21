package fr.generateurs;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public final class BlockListener implements Listener {

    private final GeneratorsPlugin pl;

    public BlockListener(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    private DataStore store() { return pl.store(); }

    // =====================================================================
    //  POSE
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (!hand.hasItemMeta()) return;
        PersistentDataContainer pdc = hand.getItemMeta().getPersistentDataContainer();
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();

        Integer tier = pdc.get(pl.kTier, PersistentDataType.INTEGER);
        if (tier != null) {
            if (tier < 1 || tier > GeneratorsPlugin.TIERS) {
                e.setCancelled(true);
                return;
            }
            int max = pl.maxGenerators(p);
            if (store().countGens(p.getUniqueId()) >= max) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cTu as atteint ta limite de §e" + max + " §cgénérateurs.");
                return;
            }
            store().addGen(p.getUniqueId(), b, tier);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aGénérateur §e#" + tier + " §aposé.");
            return;
        }

        Integer level = pdc.get(pl.kBarrel, PersistentDataType.INTEGER);
        if (level != null) {
            if (store().barrelOf(p.getUniqueId()) != null) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cTu as déjà un barrel posé. Reprends-le d'abord (Maj + clic droit).");
                return;
            }
            int lvl = Math.max(1, Math.min(level, pl.maxBarrelLevel()));
            store().addBarrel(p.getUniqueId(), b, lvl);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aBarrel §ex" + lvl + " §aposé : tes drops y seront stockés.");
            return;
        }

        Double bonus = pdc.get(pl.kBeaconBonus, PersistentDataType.DOUBLE);
        if (bonus != null) {
            int max = pl.maxBeacons();
            if (store().countBeacons(p.getUniqueId()) >= max) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cTu as atteint ta limite de §e" + max + " §cbeacons actifs.");
                return;
            }
            Long remaining = pdc.get(pl.kBeaconRemaining, PersistentDataType.LONG);
            long seconds = remaining != null ? remaining
                    : pl.getConfig().getLong("beacon.default-duration-hours", 24L) * 3600L;
            if (seconds <= 0) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cCe beacon a expiré.");
                return;
            }
            double clamped = Math.max(0.0, Math.min(bonus, pl.maxBeaconBonus()));
            store().addBeacon(p.getUniqueId(), b, clamped, System.currentTimeMillis() + seconds * 1000L);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aBeacon §e" + Fmt.percent(clamped) + " §aposé pour §f"
                    + Fmt.duration(seconds) + "§a.");
        }
    }

    // =====================================================================
    //  INTERACTIONS
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        DataStore.Gen g = store().gen(b);
        if (g != null) {
            e.setCancelled(true);
            handleGenerator(e.getPlayer(), b, g, a);
            return;
        }
        DataStore.BarrelData br = store().barrelAt(b);
        if (br != null) {
            e.setCancelled(true);
            handleBarrel(e.getPlayer(), b, br, a);
            return;
        }
        DataStore.BeaconData bc = store().beaconAt(b);
        if (bc != null) {
            e.setCancelled(true);
            handleBeacon(e.getPlayer(), b, bc, a);
        }
    }

    private boolean allowed(Player p, java.util.UUID owner) {
        if (owner.equals(p.getUniqueId()) || p.hasPermission("gen.admin")) return true;
        p.sendMessage(GeneratorsPlugin.PREFIX + "§cCet objet ne t'appartient pas.");
        return false;
    }

    private void handleGenerator(Player p, Block b, DataStore.Gen g, Action a) {
        if (!allowed(p, g.owner)) return;

        // Clic droit : casse instantanement et rend l'objet
        if (a == Action.RIGHT_CLICK_BLOCK) {
            store().removeGen(g);
            b.setType(Material.AIR);
            pl.give(p, pl.items().generator(g.tier, 1));
            p.sendMessage(GeneratorsPlugin.PREFIX + "§7Générateur §e#" + g.tier + " §7récupéré.");
            return;
        }

        // Maj + clic gauche : amelioration
        if (p.isSneaking()) {
            if (g.tier >= GeneratorsPlugin.TIERS) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cCe générateur est déjà au palier maximum.");
                return;
            }
            long cost = pl.tiers().upgradeCost(g.tier);
            if (!pl.withdraw(p, cost)) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cIl te faut §6$" + Fmt.money(cost) + " §cpour améliorer.");
                return;
            }
            g.tier++;
            b.setType(pl.tiers().material(g.tier));
            store().markDirty();
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aAmélioré au palier §e#" + g.tier + " §a(§6-$" + Fmt.money(cost) + "§a).");
            return;
        }

        // Clic gauche : production
        Tiers t = pl.tiers();
        double bonus = store().beaconBonus(g.owner);
        DataStore.BarrelData br = store().barrelOf(g.owner);
        int barrelLevel = br != null ? br.level : 1;
        double perItem = t.value(g.tier) * (1.0 + bonus) * barrelLevel;
        double perHour = perItem * 3600.0 / pl.intervalSeconds();

        p.sendMessage("§8§m                    ");
        p.sendMessage("§6Générateur §e#" + g.tier + " §7(" + Fmt.pretty(t.material(g.tier)) + ")");
        p.sendMessage("§7Valeur de base : §a$" + Fmt.money(t.value(g.tier)) + " §7toutes les §f" + pl.intervalSeconds() + " s");
        p.sendMessage("§7Bonus beacons : §a" + Fmt.percent(bonus) + (br != null ? " §7| Barrel : §ax" + br.level : ""));
        p.sendMessage("§7Production réelle : §a$" + Fmt.money(perItem) + " §7par item, §a$" + Fmt.money(perHour) + "§7/h");
        if (g.tier < GeneratorsPlugin.TIERS) {
            p.sendMessage("§7Amélioration : §6$" + Fmt.money(t.upgradeCost(g.tier)) + " §8(Maj + clic gauche)");
        } else {
            p.sendMessage("§7Palier maximum atteint.");
        }
    }

    private void handleBarrel(Player p, Block b, DataStore.BarrelData br, Action a) {
        if (!allowed(p, br.owner)) return;

        if (a == Action.RIGHT_CLICK_BLOCK) {
            double total = collect(br);
            if (p.isSneaking()) {
                if (total > 0) {
                    pl.pay(br.owner, total);
                    p.sendMessage(GeneratorsPlugin.PREFIX + "§a+$" + Fmt.money(total) + " §7(barrel vidé).");
                }
                store().removeBarrel(br);
                b.setType(Material.AIR);
                pl.give(p, pl.items().barrel(br.level));
                p.sendMessage(GeneratorsPlugin.PREFIX + "§7Barrel récupéré.");
            } else if (total <= 0) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§7Le barrel est vide.");
            } else {
                pl.pay(br.owner, total);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§a+$" + Fmt.money(total) + " §7(barrel §ex" + br.level + "§7).");
            }
            return;
        }

        // Clic gauche : infos
        long items = br.total();
        double worth = 0;
        for (Map.Entry<Integer, Long> en : br.stored.entrySet()) {
            worth += pl.reward(br.owner, en.getKey(), en.getValue(), br.level);
        }
        p.sendMessage("§8§m                    ");
        p.sendMessage("§6Barrel §ex" + br.level);
        p.sendMessage("§7Contenu : §f" + items + " §7/ §f" + pl.barrelCapacity(br.level) + " items");
        p.sendMessage("§7Valeur actuelle : §a$" + Fmt.money(worth));
        p.sendMessage("§8Clic droit : récupérer | Maj + clic droit : reprendre le barrel");
    }

    /** Calcule le gain total du contenu puis vide le barrel. */
    private double collect(DataStore.BarrelData br) {
        double total = 0;
        for (Map.Entry<Integer, Long> en : br.stored.entrySet()) {
            total += pl.reward(br.owner, en.getKey(), en.getValue(), br.level);
        }
        if (!br.stored.isEmpty()) {
            br.stored.clear();
            store().markDirty();
        }
        return total;
    }

    private void handleBeacon(Player p, Block b, DataStore.BeaconData bc, Action a) {
        if (!allowed(p, bc.owner)) return;
        long remaining = Math.max(0, (bc.expires - System.currentTimeMillis()) / 1000L);

        if (a == Action.RIGHT_CLICK_BLOCK && p.isSneaking()) {
            store().removeBeacon(bc);
            b.setType(Material.AIR);
            pl.give(p, pl.items().beacon(bc.bonus, remaining));
            p.sendMessage(GeneratorsPlugin.PREFIX + "§7Beacon récupéré (§f" + Fmt.duration(remaining) + " §7restantes).");
            return;
        }

        p.sendMessage("§8§m                    ");
        p.sendMessage("§bBeacon §e" + Fmt.percent(bc.bonus));
        p.sendMessage("§7Expire dans : §f" + Fmt.duration(remaining));
        p.sendMessage("§7Bonus total actif : §a" + Fmt.percent(store().beaconBonus(bc.owner)));
        p.sendMessage("§8Maj + clic droit : reprendre le beacon");
    }

    // =====================================================================
    //  PROTECTION
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (store().isManaged(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> store().isManaged(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> store().isManaged(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (store().isManaged(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (store().isManaged(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (store().isManaged(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        // la terre ne doit pas devenir de l'herbe, etc.
        if (store().isManaged(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (store().isManaged(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (store().isManaged(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (store().isManaged(e.getBlock())) e.setCancelled(true);
    }
}

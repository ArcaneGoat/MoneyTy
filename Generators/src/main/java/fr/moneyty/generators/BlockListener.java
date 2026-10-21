package fr.moneyty.generators;

import fr.moneyty.generators.menu.BarrelMenu;
import fr.moneyty.generators.menu.BeaconMenu;
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

import java.util.UUID;

public final class BlockListener implements Listener {

    private final GeneratorsPlugin pl;

    public BlockListener(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    private DataStore store() { return pl.store(); }

    // =====================================================================
    //  PLACING
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (!hand.hasItemMeta()) return;
        PersistentDataContainer pdc = hand.getItemMeta().getPersistentDataContainer();
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();

        if (pdc.has(pl.kDropTier, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§cGenerator drops can't be placed.");
            return;
        }

        Integer tier = pdc.get(pl.kTier, PersistentDataType.INTEGER);
        if (tier != null) {
            if (tier < 1 || tier > GeneratorsPlugin.TIERS) {
                e.setCancelled(true);
                return;
            }
            int max = pl.maxGenerators(p);
            if (store().countGens(p.getUniqueId()) >= max) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cYou reached your limit of §e" + max + " §cgenerators.");
                return;
            }
            store().addGen(p.getUniqueId(), b, tier);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aGenerator §e#" + tier + " §aplaced.");
            return;
        }

        Integer level = pdc.get(pl.kBarrel, PersistentDataType.INTEGER);
        if (level != null) {
            if (store().barrelOf(p.getUniqueId()) != null) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cYou already have a barrel placed. Pick it up first (barrel menu).");
                return;
            }
            int lvl = Math.max(1, Math.min(level, pl.maxBarrelLevel()));
            store().addBarrel(p.getUniqueId(), b, lvl);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aBarrel §ex" + lvl + " §aplaced. Feed it with hoppers to store your drops.");
            return;
        }

        Double bonus = pdc.get(pl.kBeaconBonus, PersistentDataType.DOUBLE);
        if (bonus != null) {
            int max = pl.maxBeacons(p.getUniqueId());
            if (store().countBeacons(p.getUniqueId()) >= max) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cYou reached your limit of §e" + max + " §cactive beacons.");
                return;
            }
            Long remaining = pdc.get(pl.kBeaconRemaining, PersistentDataType.LONG);
            long seconds = remaining != null ? remaining
                    : pl.getConfig().getLong("beacon.default-duration-hours", 24L) * 3600L;
            if (seconds <= 0) {
                e.setCancelled(true);
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cThis beacon has expired.");
                return;
            }
            BoostType type = BoostType.parse(pdc.get(pl.kBeaconType, PersistentDataType.STRING), BoostType.MONEY);
            double clamped = Math.max(0.0, Math.min(bonus, pl.maxBeaconBonus()));
            store().addBeacon(p.getUniqueId(), b, clamped, System.currentTimeMillis() + seconds * 1000L, type);
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aBeacon §e" + Fmt.percent(clamped) + " " + type.display
                    + " §aplaced for §f" + Fmt.duration(seconds) + "§a. Right click it to choose what it boosts.");
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
            if (allowed(e.getPlayer(), br.owner)) new BarrelMenu(pl, br).open(e.getPlayer());
            return;
        }
        DataStore.BeaconData bc = store().beaconAt(b);
        if (bc != null) {
            e.setCancelled(true);
            if (allowed(e.getPlayer(), bc.owner)) new BeaconMenu(pl, bc).open(e.getPlayer());
        }
    }

    private boolean allowed(Player p, UUID owner) {
        if (owner.equals(p.getUniqueId()) || p.hasPermission("gen.admin")) return true;
        p.sendMessage(GeneratorsPlugin.PREFIX + "§cThis doesn't belong to you.");
        return false;
    }

    private void handleGenerator(Player p, Block b, DataStore.Gen g, Action a) {
        if (!allowed(p, g.owner)) return;

        // Left click: instant pick up
        if (a == Action.LEFT_CLICK_BLOCK) {
            store().removeGen(g);
            b.setType(Material.AIR);
            pl.give(p, pl.items().generator(g.tier, 1));
            p.sendMessage(GeneratorsPlugin.PREFIX + "§7Generator §e#" + g.tier + " §7picked up.");
            return;
        }

        // Shift + right click: upgrade
        if (p.isSneaking()) {
            if (g.tier >= GeneratorsPlugin.TIERS) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cThis generator is already at the max tier.");
                return;
            }
            long cost = pl.tiers().upgradeCost(g.tier);
            if (!pl.withdraw(p, cost)) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cYou need §6$" + Fmt.money(cost) + " §cto upgrade.");
                return;
            }
            g.tier++;
            b.setType(pl.tiers().material(g.tier));
            store().markDirty();
            p.sendMessage(GeneratorsPlugin.PREFIX + "§aUpgraded to tier §e#" + g.tier + " §a(§6-$" + Fmt.money(cost) + "§a).");
            return;
        }

        // Right click: production
        Tiers t = pl.tiers();
        double money = store().beaconBonus(g.owner, BoostType.MONEY);
        double speed = store().beaconBonus(g.owner, BoostType.SPEED);
        DataStore.BarrelData br = store().barrelOf(g.owner);
        int barrelLevel = br != null ? br.level : 1;
        double perItem = t.value(g.tier) * (1.0 + money) * barrelLevel;
        double perHour = perItem * (1.0 + speed) * 3600.0 / pl.intervalSeconds();

        p.sendMessage("§8§m                    ");
        p.sendMessage("§6Generator §e#" + g.tier + " §7(" + Fmt.pretty(t.material(g.tier)) + ")");
        p.sendMessage("§7Base value: §a$" + Fmt.money(t.value(g.tier)) + " §7every §f" + pl.intervalSeconds() + "s");
        p.sendMessage("§7Money boost: §a" + Fmt.percent(money) + " §7| Speed boost: §a" + Fmt.percent(speed)
                + (br != null ? " §7| Barrel: §ax" + br.level : ""));
        p.sendMessage("§7Real output: §a$" + Fmt.money(perItem) + " §7per drop, §a$" + Fmt.money(perHour) + "§7/h");
        if (g.tier < GeneratorsPlugin.TIERS) {
            p.sendMessage("§7Upgrade: §6$" + Fmt.money(t.upgradeCost(g.tier)) + " §8(Shift + right click)");
        } else {
            p.sendMessage("§7Max tier reached.");
        }
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
        // dirt must not turn into grass, etc.
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

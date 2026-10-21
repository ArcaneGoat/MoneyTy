package fr.moneyty.generators.menu;

import fr.moneyty.generators.DataStore;
import fr.moneyty.generators.Fmt;
import fr.moneyty.generators.GeneratorsPlugin;
import fr.moneyty.generators.GeneratorsPlugin.Leader;
import fr.moneyty.generators.Items;
import fr.moneyty.generators.Tiers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /gen eco: economy report (admin). */
public final class EcoMenu extends Menu {

    private static final int REFRESH = 48, CLOSE = 50;
    private static final int[] RICH_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] EARN_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    private final GeneratorsPlugin pl;

    public EcoMenu(GeneratorsPlugin pl) {
        this.pl = pl;
        inv = Bukkit.createInventory(this, 54, "§8Economy report");
        DataStore st = pl.store();
        Tiers t = pl.tiers();
        double budget = budget(pl);
        double minted = st.totalMinted();
        double perDay = pl.theoreticalMaxPerDay();
        List<Leader> richest = pl.richest();
        List<Leader> earners = earners(pl);

        inv.setItem(4, Items.icon(Material.NETHER_STAR, "§6Economy overview",
                "§7Budget: §f$" + Fmt.money(budget),
                "§7Created by generators: §a$" + Fmt.money(minted) + " §7(" + pct(minted, budget) + ")",
                "§7Money in circulation: §a$" + Fmt.money(pl.moneyInCirculation()),
                "§7Richest player: §e" + (richest.isEmpty() ? "-" : name(richest.get(0).player())
                        + " §7($" + Fmt.money(richest.get(0).value()) + ")"),
                "§7Top generator earner: §e" + (earners.isEmpty() ? "-" : name(earners.get(0).player())
                        + " §7($" + Fmt.money(earners.get(0).value()) + ")")));

        inv.setItem(10, Items.icon(Material.GOLD_INGOT, "§eMoney created by generators",
                "§a$" + Fmt.money(minted),
                "§7" + pct(minted, budget) + " of the §f$" + Fmt.money(budget) + " §7budget"));
        inv.setItem(12, Items.icon(Material.EMERALD, "§aMoney in circulation",
                "§a$" + Fmt.money(pl.moneyInCirculation()),
                "§7Sum of every player balance",
                "§8Refreshed every 60s"));
        inv.setItem(14, Items.icon(Material.HAY_BLOCK, "§6Generators",
                "§7Placed: §f" + Fmt.count(st.totalGens()) + " §7by §f" + st.genOwners() + " §7players",
                "§7Average tier: §f" + String.format(Locale.US, "%.1f", st.averageTier()),
                "§7Active beacons: §f" + st.activeBeacons(),
                "§7Tier 1: §a$" + Fmt.money(t.value(1)) + " §7(price §6$" + Fmt.money(t.price(1)) + "§7)",
                "§7Tier 100: §a$" + Fmt.money(t.value(GeneratorsPlugin.TIERS))
                        + " §7(price §6$" + Fmt.money(t.price(GeneratorsPlugin.TIERS)) + "§7)"));
        inv.setItem(16, Items.icon(Material.BEACON, "§dTheoretical maximum",
                "§7One perfect player (" + pl.defaultMaxGenerators() + " tier 100 gens,",
                "§7barrel x" + pl.maxBarrelLevel() + ", best beacons):",
                "§a$" + Fmt.money(perDay) + "§7/day",
                "§7Days to 10Q: §f" + Math.round(1.0E16 / perDay),
                "§7Days to 50Q: §f" + Math.round(5.0E16 / perDay)));

        inv.setItem(18, Items.icon(Material.GOLD_BLOCK, "§6Richest players", "§7By balance →"));
        for (int i = 0; i < RICH_SLOTS.length && i < richest.size(); i++) {
            Leader l = richest.get(i);
            inv.setItem(RICH_SLOTS[i], head(l.player(), i + 1,
                    "§7Balance: §a$" + Fmt.money(l.value()),
                    "§7Earned from generators: §a$" + Fmt.money(st.earned(l.player().getUniqueId()))));
        }

        inv.setItem(36, Items.icon(Material.HOPPER, "§aTop generator earners", "§7Money made with generators →"));
        for (int i = 0; i < EARN_SLOTS.length && i < earners.size(); i++) {
            Leader l = earners.get(i);
            inv.setItem(EARN_SLOTS[i], head(l.player(), i + 1,
                    "§7Earned from generators: §a$" + Fmt.money(l.value()),
                    "§7Generators placed: §f" + st.countGens(l.player().getUniqueId())));
        }

        inv.setItem(REFRESH, Items.icon(Material.CLOCK, "§eRefresh"));
        inv.setItem(CLOSE, Items.icon(Material.BARRIER, "§cClose"));
        fillEmpty(0, 53);
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (slot == REFRESH) later(pl, () -> new EcoMenu(pl).open(p));
        else if (slot == CLOSE) later(pl, p::closeInventory);
    }

    /** Same report as chat lines, for the console. */
    public static List<String> text(GeneratorsPlugin pl) {
        DataStore st = pl.store();
        double budget = budget(pl);
        double perDay = pl.theoreticalMaxPerDay();
        List<String> out = new ArrayList<>();
        out.add("§8§m                    ");
        out.add("§6Economy report");
        out.add("§7Created by generators: §a$" + Fmt.money(st.totalMinted()) + " §7(" + pct(st.totalMinted(), budget)
                + " of the $" + Fmt.money(budget) + " budget)");
        out.add("§7Money in circulation: §a$" + Fmt.money(pl.moneyInCirculation()));
        out.add("§7Generators placed: §f" + st.totalGens() + " §7by §f" + st.genOwners() + " §7players, active beacons: §f"
                + st.activeBeacons());
        out.add("§7Theoretical max per player: §a$" + Fmt.money(perDay) + "§7/day (10Q in §f" + Math.round(1.0E16 / perDay)
                + " §7days, 50Q in §f" + Math.round(5.0E16 / perDay) + " §7days)");
        out.add("§6Richest players:");
        int i = 1;
        for (Leader l : pl.richest()) out.add("§7 " + (i++) + ". §f" + name(l.player()) + " §a$" + Fmt.money(l.value()));
        out.add("§6Top generator earners:");
        i = 1;
        for (Leader l : earners(pl)) out.add("§7 " + (i++) + ". §f" + name(l.player()) + " §a$" + Fmt.money(l.value()));
        return out;
    }

    private static double budget(GeneratorsPlugin pl) {
        return pl.getConfig().getDouble("economy.budget", 5.0E16);
    }

    private static List<Leader> earners(GeneratorsPlugin pl) {
        List<Leader> out = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : pl.store().topEarners(10)) {
            out.add(new Leader(Bukkit.getOfflinePlayer(e.getKey()), e.getValue()));
        }
        return out;
    }

    private static String pct(double v, double budget) {
        return String.format(Locale.US, "%.4f", budget <= 0 ? 0 : v / budget * 100.0) + "%";
    }

    private static String name(OfflinePlayer op) {
        String n = op.getName();
        return n == null ? "Unknown" : n;
    }

    private static ItemStack head(OfflinePlayer op, int rank, String... lore) {
        ItemStack head = Items.icon(Material.PLAYER_HEAD, "§e#" + rank + " §f" + name(op), lore);
        if (head.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(op);
            head.setItemMeta(sm);
        }
        return head;
    }
}

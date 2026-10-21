package fr.generateurs;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GenCommand implements CommandExecutor, TabCompleter {

    private static final String P = GeneratorsPlugin.PREFIX;

    private final GeneratorsPlugin pl;

    public GenCommand(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Commande réservée aux joueurs. Utilise /gen help.");
                return true;
            }
            pl.shop().open(p, 0);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "give" -> give(sender, args);
            case "givebarrel" -> giveBarrel(sender, args);
            case "givebeacon" -> giveBeacon(sender, args);
            case "eco" -> eco(sender);
            case "reload" -> {
                if (!admin(sender)) return true;
                pl.reloadAll();
                sender.sendMessage(P + "§aConfiguration rechargée (l'intervalle de production nécessite un redémarrage).");
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("gen.admin")) return true;
        s.sendMessage(P + "§cPermission manquante.");
        return false;
    }

    private void help(CommandSender s) {
        s.sendMessage("§6/gen §7- ouvre le menu des générateurs");
        s.sendMessage("§6/gen info §7- tes limites et bonus");
        if (s.hasPermission("gen.admin")) {
            s.sendMessage("§6/gen give <joueur> <palier 1-100> [quantité]");
            s.sendMessage("§6/gen givebarrel <joueur> <niveau 1-" + pl.maxBarrelLevel() + ">");
            s.sendMessage("§6/gen givebeacon <joueur> <bonus ex 0.5> [heures]");
            s.sendMessage("§6/gen eco §7- bilan économique §6/gen reload");
        }
    }

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Joueurs uniquement.");
            return;
        }
        DataStore st = pl.store();
        DataStore.BarrelData br = st.barrelOf(p.getUniqueId());
        s.sendMessage("§8§m                    ");
        s.sendMessage("§7Générateurs : §f" + st.countGens(p.getUniqueId()) + "§7/§f" + pl.maxGenerators(p));
        s.sendMessage("§7Beacons actifs : §f" + st.countBeacons(p.getUniqueId()) + "§7/§f" + pl.maxBeacons()
                + " §7(bonus §a" + Fmt.percent(st.beaconBonus(p.getUniqueId())) + "§7)");
        s.sendMessage("§7Barrel : " + (br == null ? "§caucun"
                : "§ex" + br.level + " §7(" + br.total() + "/" + pl.barrelCapacity(br.level) + " items)"));
    }

    private Player target(CommandSender s, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) s.sendMessage(P + "§cJoueur introuvable ou hors ligne : " + name);
        return t;
    }

    private Integer parseInt(CommandSender s, String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            s.sendMessage(P + "§cNombre invalide : " + v);
            return null;
        }
    }

    private void give(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) { s.sendMessage(P + "§c/gen give <joueur> <palier> [quantité]"); return; }
        Player t = target(s, a[1]);
        Integer tier = parseInt(s, a[2]);
        if (t == null || tier == null) return;
        if (tier < 1 || tier > GeneratorsPlugin.TIERS) { s.sendMessage(P + "§cPalier entre 1 et 100."); return; }
        int qty = 1;
        if (a.length >= 4) {
            Integer q = parseInt(s, a[3]);
            if (q == null) return;
            qty = Math.max(1, Math.min(q, 64));
        }
        pl.give(t, pl.items().generator(tier, qty));
        s.sendMessage(P + "§a" + qty + " générateur(s) #" + tier + " donné(s) à " + t.getName() + ".");
    }

    private void giveBarrel(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) { s.sendMessage(P + "§c/gen givebarrel <joueur> <niveau>"); return; }
        Player t = target(s, a[1]);
        Integer level = parseInt(s, a[2]);
        if (t == null || level == null) return;
        level = Math.max(1, Math.min(level, pl.maxBarrelLevel()));
        pl.give(t, pl.items().barrel(level));
        s.sendMessage(P + "§aBarrel x" + level + " donné à " + t.getName() + ".");
    }

    private void giveBeacon(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) { s.sendMessage(P + "§c/gen givebeacon <joueur> <bonus> [heures]"); return; }
        Player t = target(s, a[1]);
        if (t == null) return;
        double bonus;
        double hours = pl.getConfig().getDouble("beacon.default-duration-hours", 24);
        try {
            bonus = Double.parseDouble(a[2]);
            if (a.length >= 4) hours = Double.parseDouble(a[3]);
        } catch (NumberFormatException ex) {
            s.sendMessage(P + "§cNombre invalide.");
            return;
        }
        bonus = Math.max(0.0, Math.min(bonus, pl.maxBeaconBonus()));
        long seconds = Math.max(60L, Math.round(hours * 3600.0));
        pl.give(t, pl.items().beacon(bonus, seconds));
        s.sendMessage(P + "§aBeacon " + Fmt.percent(bonus) + " (" + Fmt.duration(seconds) + ") donné à " + t.getName() + ".");
    }

    private void eco(CommandSender s) {
        if (!admin(s)) return;
        Tiers t = pl.tiers();
        double budget = pl.getConfig().getDouble("economy.budget", 5.0E16);
        double perDay = 86400.0 / pl.intervalSeconds();
        int maxGens = pl.getConfig().getInt("limits.default-max-generators", 25);
        double maxMult = pl.maxBarrelLevel() * (1.0 + pl.maxBeacons() * pl.maxBeaconBonus());
        double perPlayerDay = t.value(GeneratorsPlugin.TIERS) * perDay * maxGens * maxMult;

        s.sendMessage("§8§m                    ");
        s.sendMessage("§6Bilan économique");
        s.sendMessage("§7Argent créé par les générateurs : §a$" + Fmt.money(pl.store().totalMinted())
                + " §7(" + String.format(Locale.US, "%.4f", pl.store().totalMinted() / budget * 100.0) + "% du budget " + Fmt.money(budget) + ")");
        s.sendMessage("§7Palier 1 : §a$" + Fmt.money(t.value(1)) + " §7(prix §6$" + Fmt.money(t.price(1)) + "§7)");
        s.sendMessage("§7Palier 100 : §a$" + Fmt.money(t.value(100)) + " §7(prix §6$" + Fmt.money(t.price(100)) + "§7)");
        s.sendMessage("§7Maximum théorique par joueur (" + maxGens + " gens palier 100, barrel x" + pl.maxBarrelLevel()
                + ", beacons max) : §a$" + Fmt.money(perPlayerDay) + "§7/jour");
        s.sendMessage("§7Jours pour atteindre 10Q : §f" + Math.round(1.0E16 / perPlayerDay)
                + " §7| 50Q : §f" + Math.round(5.0E16 / perPlayerDay) + " §7(un seul joueur, parfait)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("info");
            if (sender.hasPermission("gen.admin")) {
                out.addAll(List.of("give", "givebarrel", "givebeacon", "eco", "reload"));
            }
            return filter(out, args[0]);
        }
        if (args.length == 2 && sender.hasPermission("gen.admin")
                && List.of("give", "givebarrel", "givebeacon").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return filter(out, args[1]);
        }
        return out;
    }

    private List<String> filter(List<String> in, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(s);
        return out;
    }
}

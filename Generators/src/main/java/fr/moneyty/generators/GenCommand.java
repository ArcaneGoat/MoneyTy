package fr.moneyty.generators;

import fr.moneyty.generators.menu.EcoMenu;
import fr.moneyty.generators.menu.OfflineMenu;
import fr.moneyty.generators.menu.ShopMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GenCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_ARG = List.of("give", "givebarrel", "givebeacon", "slots", "beaconslots");

    private final GeneratorsPlugin pl;

    public GenCommand(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    private static String p() { return GeneratorsPlugin.PREFIX; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only. Use /gen help.");
                return true;
            }
            new ShopMenu(pl, p, 0).open(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "offline" -> offline(sender);
            case "give" -> give(sender, args);
            case "givebarrel" -> giveBarrel(sender, args);
            case "givebeacon" -> giveBeacon(sender, args);
            case "slots" -> slots(sender, args, false);
            case "beaconslots" -> slots(sender, args, true);
            case "defaultmax" -> defaultMax(sender, args);
            case "eco" -> eco(sender);
            case "reload" -> {
                if (!admin(sender)) return true;
                pl.reloadAll();
                sender.sendMessage(p() + "§aConfiguration reloaded (the production interval needs a restart).");
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("gen.admin")) return true;
        s.sendMessage(p() + "§cYou don't have permission.");
        return false;
    }

    private void help(CommandSender s) {
        s.sendMessage("§6/gen §7- open the generator shop");
        s.sendMessage("§6/gen info §7- your limits and boosts");
        s.sendMessage("§6/gen offline §7- claim the drops made while you were away");
        if (s.hasPermission("gen.admin")) {
            s.sendMessage("§6/gen give <player> <tier 1-100> [amount]");
            s.sendMessage("§6/gen givebarrel <player> <level 1-" + pl.maxBarrelLevel() + ">");
            s.sendMessage("§6/gen givebeacon <player> <boost e.g. 0.5> [hours] [money|speed|capacity]");
            s.sendMessage("§6/gen slots <player> <set|add|remove> <n> §7- extra generator slots");
            s.sendMessage("§6/gen beaconslots <player> <set|add|remove> <n> §7- extra beacon slots");
            s.sendMessage("§6/gen defaultmax <n> §7- default generator limit");
            s.sendMessage("§6/gen eco §7- economy report §6/gen reload");
        }
    }

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        DataStore st = pl.store();
        UUID id = p.getUniqueId();
        DataStore.BarrelData br = st.barrelOf(id);
        s.sendMessage("§8§m                    ");
        s.sendMessage("§7Generators: §f" + st.countGens(id) + "§7/§f" + pl.maxGenerators(p));
        s.sendMessage("§7Active beacons: §f" + st.countBeacons(id) + "§7/§f" + pl.maxBeacons(id));
        s.sendMessage("§7Boosts: §aMoney " + Fmt.percent(st.beaconBonus(id, BoostType.MONEY))
                + " §7| §aSpeed " + Fmt.percent(st.beaconBonus(id, BoostType.SPEED))
                + " §7| §aCapacity " + Fmt.percent(st.beaconBonus(id, BoostType.CAPACITY)));
        s.sendMessage("§7Barrel: " + (br == null ? "§cnone"
                : "§ex" + br.level + " §7(" + Fmt.count(br.total()) + "/" + Fmt.count(pl.barrelCapacity(br)) + " drops)"));
    }

    private void offline(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (pl.store().pending(p.getUniqueId()) == null) {
            p.sendMessage(p() + "§7You have no offline drops to claim.");
            return;
        }
        new OfflineMenu(pl, p).open(p);
    }

    private Player target(CommandSender s, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) s.sendMessage(p() + "§cPlayer not found or offline: " + name);
        return t;
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer offlineTarget(CommandSender s, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer t = Bukkit.getOfflinePlayer(name);
        if (!t.hasPlayedBefore()) {
            s.sendMessage(p() + "§cUnknown player: " + name);
            return null;
        }
        return t;
    }

    private Integer parseInt(CommandSender s, String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            s.sendMessage(p() + "§cInvalid number: " + v);
            return null;
        }
    }

    private void give(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) { s.sendMessage(p() + "§c/gen give <player> <tier> [amount]"); return; }
        Player t = target(s, a[1]);
        Integer tier = parseInt(s, a[2]);
        if (t == null || tier == null) return;
        if (tier < 1 || tier > GeneratorsPlugin.TIERS) { s.sendMessage(p() + "§cTier must be between 1 and 100."); return; }
        int qty = 1;
        if (a.length >= 4) {
            Integer q = parseInt(s, a[3]);
            if (q == null) return;
            qty = Math.max(1, Math.min(q, 64));
        }
        pl.give(t, pl.items().generator(tier, qty));
        s.sendMessage(p() + "§aGave " + qty + " generator(s) #" + tier + " to " + t.getName() + ".");
    }

    private void giveBarrel(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) { s.sendMessage(p() + "§c/gen givebarrel <player> <level>"); return; }
        Player t = target(s, a[1]);
        Integer level = parseInt(s, a[2]);
        if (t == null || level == null) return;
        level = Math.max(1, Math.min(level, pl.maxBarrelLevel()));
        pl.give(t, pl.items().barrel(level));
        s.sendMessage(p() + "§aGave a barrel x" + level + " to " + t.getName() + ".");
    }

    private void giveBeacon(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) { s.sendMessage(p() + "§c/gen givebeacon <player> <boost> [hours] [money|speed|capacity]"); return; }
        Player t = target(s, a[1]);
        if (t == null) return;
        double bonus;
        double hours = pl.getConfig().getDouble("beacon.default-duration-hours", 24);
        try {
            bonus = Double.parseDouble(a[2]);
            if (a.length >= 4) hours = Double.parseDouble(a[3]);
        } catch (NumberFormatException ex) {
            s.sendMessage(p() + "§cInvalid number.");
            return;
        }
        BoostType type = a.length >= 5 ? BoostType.parse(a[4], null) : BoostType.MONEY;
        if (type == null) { s.sendMessage(p() + "§cType must be money, speed or capacity."); return; }
        bonus = Math.max(0.0, Math.min(bonus, pl.maxBeaconBonus()));
        long seconds = Math.max(60L, Math.round(hours * 3600.0));
        pl.give(t, pl.items().beacon(bonus, seconds, type));
        s.sendMessage(p() + "§aGave a beacon " + Fmt.percent(bonus) + " " + type.display + " (" + Fmt.duration(seconds)
                + ") to " + t.getName() + ".");
    }

    /** Extra slots on top of the default limit (e.g. given by jobs levels). */
    private void slots(CommandSender s, String[] a, boolean beacons) {
        if (!admin(s)) return;
        String what = beacons ? "beacon" : "generator";
        if (a.length < 4) { s.sendMessage(p() + "§c/gen " + a[0] + " <player> <set|add|remove> <n>"); return; }
        OfflinePlayer t = offlineTarget(s, a[1]);
        Integer n = parseInt(s, a[3]);
        if (t == null || n == null) return;
        UUID id = t.getUniqueId();
        DataStore st = pl.store();
        int cur = beacons ? st.extraBeaconSlots(id) : st.extraGenSlots(id);
        int next;
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "set" -> next = n;
            case "add" -> next = cur + n;
            case "remove" -> next = cur - n;
            default -> {
                s.sendMessage(p() + "§cUse set, add or remove.");
                return;
            }
        }
        next = Math.max(0, next);
        if (beacons) st.setExtraBeaconSlots(id, next); else st.setExtraGenSlots(id, next);

        String total = "";
        if (t instanceof Player online) {
            total = " §7(limit now §f" + (beacons ? pl.maxBeacons(id) : pl.maxGenerators(online)) + "§7)";
        }
        s.sendMessage(p() + "§a" + t.getName() + " now has §e" + next + " §aextra " + what + " slot(s)." + total);
    }

    private void defaultMax(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 2) {
            s.sendMessage(p() + "§7Default generator limit: §f" + pl.defaultMaxGenerators() + " §8(/gen defaultmax <n>)");
            return;
        }
        Integer n = parseInt(s, a[1]);
        if (n == null) return;
        pl.getConfig().set("limits.default-max-generators", Math.max(0, n));
        pl.saveConfig();
        s.sendMessage(p() + "§aDefault generator limit set to §e" + Math.max(0, n) + "§a.");
    }

    private void eco(CommandSender s) {
        if (!admin(s)) return;
        if (s instanceof Player p) {
            new EcoMenu(pl).open(p);
            return;
        }
        for (String line : EcoMenu.text(pl)) s.sendMessage(line);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("gen.admin");
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(List.of("info", "offline"));
            if (admin) out.addAll(List.of("give", "givebarrel", "givebeacon", "slots", "beaconslots", "defaultmax", "eco", "reload"));
            return filter(out, args[0]);
        }
        if (!admin) return out;
        if (args.length == 2 && PLAYER_ARG.contains(sub)) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return filter(out, args[1]);
        }
        if (args.length == 3 && (sub.equals("slots") || sub.equals("beaconslots"))) {
            return filter(List.of("set", "add", "remove"), args[2]);
        }
        if (args.length == 5 && sub.equals("givebeacon")) {
            return filter(List.of("money", "speed", "capacity"), args[4]);
        }
        return out;
    }

    private List<String> filter(List<String> in, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(s);
        return out;
    }
}

package fr.moneyty.generators;

import fr.moneyty.generators.menu.MenuListener;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class GeneratorsPlugin extends JavaPlugin {

    public static final int TIERS = 100;
    private static final String DEFAULT_PREFIX =
            "&#FCD05C&lM&#FAD77E&lᴏ&#F9DD9F&lɴ&#F7E4C1&lᴇ&#F9DD9F&lʏ&#FAD77E&lT&#FCD05C&lʏ &8» &r";
    /** Chat prefix, read from config.yml. */
    public static String PREFIX = Fmt.color(DEFAULT_PREFIX);

    /** A player and an amount, for the leaderboards. */
    public record Leader(OfflinePlayer player, double value) {}

    private Economy economy;
    private Tiers tiers;
    private DataStore store;
    private Items items;

    private List<Leader> richest = List.of();
    private double circulation;
    private long richestAt;

    public NamespacedKey kTier, kBarrel, kBeaconBonus, kBeaconRemaining, kBeaconType, kDropTier, kDropOwner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PREFIX = Fmt.color(getConfig().getString("prefix", DEFAULT_PREFIX));

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No Vault economy found (EssentialsX + Vault required). Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();

        kTier = new NamespacedKey(this, "gen_tier");
        kBarrel = new NamespacedKey(this, "barrel_level");
        kBeaconBonus = new NamespacedKey(this, "beacon_bonus");
        kBeaconRemaining = new NamespacedKey(this, "beacon_remaining");
        kBeaconType = new NamespacedKey(this, "beacon_type");
        kDropTier = new NamespacedKey(this, "drop_tier");
        kDropOwner = new NamespacedKey(this, "drop_owner");

        tiers = new Tiers(getConfig());
        items = new Items(this);
        store = new DataStore(this);
        store.load();
        // after a /reload, players already online did not really leave
        for (Player p : Bukkit.getOnlinePlayers()) store.clearQuit(p.getUniqueId());

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new DropListener(this), this);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        GenCommand cmd = new GenCommand(this);
        getCommand("gen").setExecutor(cmd);
        getCommand("gen").setTabCompleter(cmd);

        long interval = intervalSeconds() * 20L;
        Production production = new Production(this);
        getServer().getScheduler().runTaskTimer(this, production::tick, interval, interval);
        getServer().getScheduler().runTaskTimer(this, store::expireBeacons, 400L, 400L);
        getServer().getScheduler().runTaskTimer(this, store::saveIfDirty, 600L, 600L);
    }

    @Override
    public void onDisable() {
        if (store == null) return;
        // plugins are disabled before players are kicked on shutdown: record their quit time now
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) store.setQuit(p.getUniqueId(), now);
        store.save();
    }

    public void reloadAll() {
        reloadConfig();
        tiers = new Tiers(getConfig());
        PREFIX = Fmt.color(getConfig().getString("prefix", DEFAULT_PREFIX));
    }

    public Economy economy() { return economy; }
    public Tiers tiers() { return tiers; }
    public DataStore store() { return store; }
    public Items items() { return items; }

    // =====================================================================
    //  SETTINGS
    // =====================================================================

    public int intervalSeconds() {
        return Math.max(1, getConfig().getInt("interval-seconds", 15));
    }

    public int maxBarrelLevel() {
        return Math.max(1, getConfig().getInt("barrel.max-level", 20));
    }

    /** Capacity of a barrel item, without beacon boost. */
    public long barrelBaseCapacity(int level) {
        return getConfig().getLong("barrel.capacity-per-level", 10000L) * Math.max(1, level);
    }

    /** Real capacity of a placed barrel, with the owner's CAPACITY beacons. */
    public long barrelCapacity(DataStore.BarrelData br) {
        double boost = 1.0 + store.beaconBonus(br.owner, BoostType.CAPACITY);
        return (long) Math.floor(barrelBaseCapacity(br.level) * boost);
    }

    public double maxBeaconBonus() {
        return Math.max(0.0, getConfig().getDouble("beacon.max-bonus", 1.0));
    }

    public int defaultMaxGenerators() {
        return Math.max(0, getConfig().getInt("limits.default-max-generators", 25));
    }

    public int defaultMaxBeacons() {
        return Math.max(0, getConfig().getInt("limits.max-beacons", 3));
    }

    public int maxBeacons(UUID id) {
        return defaultMaxBeacons() + store.extraBeaconSlots(id);
    }

    /** Default limit (or gen.max.<n> permission if higher) + extra slots given with /gen slots. */
    public int maxGenerators(Player p) {
        int max = defaultMaxGenerators();
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith("gen.max.")) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.substring("gen.max.".length())));
                } catch (NumberFormatException ignored) {
                    // non numeric permission: ignored
                }
            }
        }
        return max + store.extraGenSlots(p.getUniqueId());
    }

    public long offlineMinSeconds() {
        return Math.max(0L, getConfig().getLong("offline.min-minutes", 30L) * 60L);
    }

    public double offlinePenalty() {
        return Math.max(0.0, Math.min(1.0, getConfig().getDouble("offline.penalty", 0.25)));
    }

    /** 0 = no cap. */
    public long offlineMaxSeconds() {
        return Math.max(0L, Math.round(getConfig().getDouble("offline.max-hours", 12) * 3600.0));
    }

    // =====================================================================
    //  MONEY
    // =====================================================================

    /** Money for 'count' drops of a tier. barrelLevel = 1 when the drop is picked up from the ground. */
    public double reward(UUID owner, int tier, long count, int barrelLevel) {
        double mult = (1.0 + store.beaconBonus(owner, BoostType.MONEY)) * Math.max(1, barrelLevel);
        return tiers.value(tier) * (double) count * mult;
    }

    public void pay(UUID id, double amount) {
        if (amount <= 0) return;
        economy.depositPlayer(Bukkit.getOfflinePlayer(id), amount);
        store.mint(id, amount);
    }

    public boolean withdraw(Player p, double amount) {
        if (!economy.has(p, amount)) return false;
        return economy.withdrawPlayer(p, amount).transactionSuccess();
    }

    /** Best possible multiplier for one player: barrel x20 and the best split of beacons between MONEY and SPEED. */
    public double theoreticalMaxPerDay() {
        int n = defaultMaxBeacons();
        double b = maxBeaconBonus();
        double best = 1.0;
        for (int k = 0; k <= n; k++) best = Math.max(best, (1.0 + k * b) * (1.0 + (n - k) * b));
        return tiers.value(TIERS) * (86400.0 / intervalSeconds()) * defaultMaxGenerators() * maxBarrelLevel() * best;
    }

    /** Top 10 balances (Vault), cached for 60 seconds. */
    public List<Leader> richest() {
        refreshRichest();
        return richest;
    }

    /** Sum of every known balance (Vault), cached for 60 seconds. */
    public double moneyInCirculation() {
        refreshRichest();
        return circulation;
    }

    private void refreshRichest() {
        long now = System.currentTimeMillis();
        if (richestAt != 0 && now - richestAt < 60_000L) return;
        List<Leader> all = new ArrayList<>();
        double sum = 0;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            double bal;
            try {
                bal = economy.getBalance(op);
            } catch (RuntimeException ex) {
                continue; // no account
            }
            sum += bal;
            all.add(new Leader(op, bal));
        }
        all.sort(Comparator.comparingDouble(Leader::value).reversed());
        richest = new ArrayList<>(all.subList(0, Math.min(10, all.size())));
        circulation = sum;
        richestAt = now;
    }

    // =====================================================================
    //  BARREL
    // =====================================================================

    public Integer dropTier(ItemStack st) {
        if (st == null || !st.hasItemMeta()) return null;
        return st.getItemMeta().getPersistentDataContainer().get(kDropTier, PersistentDataType.INTEGER);
    }

    /** Moves the drops that hoppers pushed into the barrel block into its virtual storage. */
    public void drainBarrel(DataStore.BarrelData br) {
        World w = Bukkit.getWorld(br.world);
        if (w == null || !w.isChunkLoaded(br.x >> 4, br.z >> 4)) return;
        if (!(w.getBlockAt(br.x, br.y, br.z).getState() instanceof Barrel state)) return;
        Inventory inv = state.getInventory();
        long room = barrelCapacity(br) - br.total();
        boolean changed = false;
        for (int i = 0; i < inv.getSize() && room > 0; i++) {
            ItemStack it = inv.getItem(i);
            Integer tier = dropTier(it);
            if (tier == null || tier < 1 || tier > TIERS) continue;
            int take = (int) Math.min(it.getAmount(), room);
            br.add(tier, take);
            room -= take;
            changed = true;
            if (take >= it.getAmount()) {
                inv.setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - take);
                inv.setItem(i, it);
            }
        }
        if (changed) store.markDirty();
    }

    /** Value of the barrel content, with its multiplier. */
    public double barrelWorth(DataStore.BarrelData br) {
        double worth = 0;
        for (Map.Entry<Integer, Long> en : br.stored.entrySet()) {
            worth += reward(br.owner, en.getKey(), en.getValue(), br.level);
        }
        return worth;
    }

    /** Empties the barrel and returns what it was worth (not paid yet). */
    public double collectBarrel(DataStore.BarrelData br) {
        drainBarrel(br);
        double total = barrelWorth(br);
        if (!br.stored.isEmpty()) {
            br.stored.clear();
            store.markDirty();
        }
        return total;
    }

    // =====================================================================
    //  OFFLINE PRODUCTION
    // =====================================================================

    /** Drops the player's generators made while they were offline, penalty applied. Null if none. */
    public DataStore.OfflineReward computeOffline(UUID id, long awaySeconds) {
        long max = offlineMaxSeconds();
        long counted = max > 0 ? Math.min(awaySeconds, max) : awaySeconds;
        long cycles = counted / intervalSeconds();
        long perGen = (long) Math.floor(cycles * (1.0 - offlinePenalty()));
        if (perGen <= 0) return null;
        DataStore.OfflineReward r = new DataStore.OfflineReward(awaySeconds);
        for (DataStore.Gen g : store.gensSnapshot()) {
            if (!g.owner.equals(id)) continue;
            r.drops.merge(g.tier, perGen, Long::sum);
            r.gens.merge(g.tier, 1, Integer::sum);
        }
        return r.drops.isEmpty() ? null : r;
    }

    public void sendOfflineSummary(Player p) {
        DataStore.OfflineReward r = store.pending(p.getUniqueId());
        if (r == null) return;
        p.sendMessage(PREFIX + "§7While you were away §f(" + Fmt.duration(r.seconds) + ")§7, your generators produced §e"
                + Fmt.count(r.totalDrops()) + " drops §c(-" + Math.round(offlinePenalty() * 100) + "% offline)§7:");
        List<Integer> tiersDesc = new ArrayList<>(r.drops.keySet());
        tiersDesc.sort(Comparator.reverseOrder());
        int shown = 0;
        for (int tier : tiersDesc) {
            if (shown++ == 5) {
                p.sendMessage("§8  ... and " + (tiersDesc.size() - 5) + " more generator types");
                break;
            }
            p.sendMessage("§8  • §f" + r.gens.getOrDefault(tier, 0) + "x §6#" + tier + " " + Fmt.pretty(tiers.material(tier))
                    + "§7: §e" + Fmt.count(r.drops.get(tier)) + " drops");
        }
        p.sendMessage("§7Claim them into your barrel with §e/gen offline§7.");
    }

    /** Puts the pending offline drops into the barrel (best tiers first). What does not fit is sold at x1. */
    public void claimOffline(Player p) {
        UUID id = p.getUniqueId();
        DataStore.OfflineReward r = store.pending(id);
        if (r == null) {
            p.sendMessage(PREFIX + "§7You have no offline drops to claim.");
            return;
        }
        store.removePending(id);
        DataStore.BarrelData br = store.barrelOf(id);
        long room = br == null ? 0 : Math.max(0, barrelCapacity(br) - br.total());
        long toBarrel = 0;
        double sold = 0;
        TreeMap<Integer, Long> desc = new TreeMap<>(Comparator.reverseOrder());
        desc.putAll(r.drops);
        for (Map.Entry<Integer, Long> e : desc.entrySet()) {
            long n = e.getValue();
            long in = Math.min(n, room);
            if (in > 0) {
                br.add(e.getKey(), in);
                room -= in;
                toBarrel += in;
            }
            if (n - in > 0) sold += reward(id, e.getKey(), n - in, 1);
        }
        if (toBarrel > 0) store.markDirty();
        pay(id, sold);
        if (toBarrel > 0) {
            p.sendMessage(PREFIX + "§a" + Fmt.count(toBarrel) + " drops §7added to your barrel §ex" + br.level + "§7.");
        }
        if (sold > 0) {
            p.sendMessage(PREFIX + "§a+$" + Fmt.money(sold) + " §7(" + (br == null ? "no barrel placed" : "barrel full")
                    + ": the rest was sold at x1).");
        }
    }

    // =====================================================================
    //  MISC
    // =====================================================================

    public void actionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    public void give(Player p, ItemStack stack) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }
}

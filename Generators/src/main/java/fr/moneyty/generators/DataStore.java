package fr.moneyty.generators;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class DataStore {

    public static final class Gen {
        public final UUID owner;
        public final String world;
        public final int x, y, z;
        public int tier;
        /** Fractional production carried between ticks (speed boost). Not saved. */
        public double progress;

        Gen(UUID owner, String world, int x, int y, int z, int tier) {
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
        }
    }

    public static final class BarrelData {
        public final UUID owner;
        public final String world;
        public final int x, y, z;
        public final int level;
        public final Map<Integer, Long> stored = new TreeMap<>();

        BarrelData(UUID owner, String world, int x, int y, int z, int level) {
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }

        public long total() {
            long t = 0;
            for (long v : stored.values()) t += v;
            return t;
        }

        public void add(int tier, long n) {
            stored.merge(tier, n, Long::sum);
        }

        public Block block() { return DataStore.block(world, x, y, z); }
    }

    public static final class BeaconData {
        public final UUID owner;
        public final String world;
        public final int x, y, z;
        public final double bonus;
        public final long expires; // epoch millis
        public BoostType type;

        BeaconData(UUID owner, String world, int x, int y, int z, double bonus, long expires, BoostType type) {
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.bonus = bonus;
            this.expires = expires;
            this.type = type;
        }

        public Block block() { return DataStore.block(world, x, y, z); }
    }

    /** Drops produced while a player was offline, waiting to be claimed. */
    public static final class OfflineReward {
        public long seconds;
        public final Map<Integer, Long> drops = new TreeMap<>();
        public final Map<Integer, Integer> gens = new TreeMap<>();

        public OfflineReward(long seconds) {
            this.seconds = seconds;
        }

        public long totalDrops() {
            long t = 0;
            for (long v : drops.values()) t += v;
            return t;
        }

        void merge(OfflineReward o) {
            seconds += o.seconds;
            for (Map.Entry<Integer, Long> e : o.drops.entrySet()) drops.merge(e.getKey(), e.getValue(), Long::sum);
            for (Map.Entry<Integer, Integer> e : o.gens.entrySet()) gens.merge(e.getKey(), e.getValue(), Math::max);
        }
    }

    private final GeneratorsPlugin plugin;
    private final Map<String, Gen> gens = new HashMap<>();
    private final Map<String, BarrelData> barrels = new HashMap<>();
    private final Map<String, BeaconData> beacons = new HashMap<>();
    private final Map<UUID, Double> earned = new HashMap<>();
    private final Map<UUID, Integer> extraGens = new HashMap<>();
    private final Map<UUID, Integer> extraBeacons = new HashMap<>();
    private final Map<UUID, Long> lastQuit = new HashMap<>();
    private final Map<UUID, OfflineReward> pending = new HashMap<>();
    private double totalMinted;
    private boolean dirty;

    public DataStore(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- keys ----------

    static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    static String key(Block b) {
        return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    static Block block(String world, int x, int y, int z) {
        World w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    // ---------- access ----------

    public Gen gen(Block b) { return gens.get(key(b)); }

    public BarrelData barrelAt(Block b) { return barrels.get(key(b)); }

    public BeaconData beaconAt(Block b) { return beacons.get(key(b)); }

    public boolean isPlaced(BarrelData d) { return barrels.get(key(d.world, d.x, d.y, d.z)) == d; }

    public boolean isPlaced(BeaconData d) { return beacons.get(key(d.world, d.x, d.y, d.z)) == d; }

    public boolean isManaged(Block b) {
        String k = key(b);
        return gens.containsKey(k) || barrels.containsKey(k) || beacons.containsKey(k);
    }

    public Collection<Gen> gensSnapshot() { return new ArrayList<>(gens.values()); }

    public int countGens(UUID owner) {
        int c = 0;
        for (Gen g : gens.values()) if (g.owner.equals(owner)) c++;
        return c;
    }

    public int countBeacons(UUID owner) {
        long now = System.currentTimeMillis();
        int c = 0;
        for (BeaconData b : beacons.values()) if (b.owner.equals(owner) && b.expires > now) c++;
        return c;
    }

    public BarrelData barrelOf(UUID owner) {
        for (BarrelData b : barrels.values()) if (b.owner.equals(owner)) return b;
        return null;
    }

    /** Sum of the active beacon boosts of this type for this player (0.5 = +50%). */
    public double beaconBonus(UUID owner, BoostType type) {
        long now = System.currentTimeMillis();
        double sum = 0;
        for (BeaconData b : beacons.values()) {
            if (b.owner.equals(owner) && b.type == type && b.expires > now) sum += b.bonus;
        }
        return sum;
    }

    // ---------- stats ----------

    public int totalGens() { return gens.size(); }

    public int genOwners() {
        Set<UUID> s = new HashSet<>();
        for (Gen g : gens.values()) s.add(g.owner);
        return s.size();
    }

    public double averageTier() {
        if (gens.isEmpty()) return 0;
        double sum = 0;
        for (Gen g : gens.values()) sum += g.tier;
        return sum / gens.size();
    }

    public int activeBeacons() {
        long now = System.currentTimeMillis();
        int c = 0;
        for (BeaconData b : beacons.values()) if (b.expires > now) c++;
        return c;
    }

    public double earned(UUID id) { return earned.getOrDefault(id, 0.0); }

    public List<Map.Entry<UUID, Double>> topEarners(int n) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(earned.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }

    // ---------- add / remove ----------

    public Gen addGen(UUID owner, Block b, int tier) {
        Gen g = new Gen(owner, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), tier);
        gens.put(key(b), g);
        dirty = true;
        return g;
    }

    public void removeGen(Gen g) {
        gens.remove(key(g.world, g.x, g.y, g.z));
        dirty = true;
    }

    public BarrelData addBarrel(UUID owner, Block b, int level) {
        BarrelData d = new BarrelData(owner, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), level);
        barrels.put(key(b), d);
        dirty = true;
        return d;
    }

    public void removeBarrel(BarrelData d) {
        barrels.remove(key(d.world, d.x, d.y, d.z));
        dirty = true;
    }

    public BeaconData addBeacon(UUID owner, Block b, double bonus, long expires, BoostType type) {
        BeaconData d = new BeaconData(owner, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), bonus, expires, type);
        beacons.put(key(b), d);
        dirty = true;
        return d;
    }

    public void removeBeacon(BeaconData d) {
        beacons.remove(key(d.world, d.x, d.y, d.z));
        dirty = true;
    }

    public void markDirty() { dirty = true; }

    // ---------- slots ----------

    public int extraGenSlots(UUID id) { return extraGens.getOrDefault(id, 0); }

    public void setExtraGenSlots(UUID id, int n) {
        if (n <= 0) extraGens.remove(id); else extraGens.put(id, n);
        dirty = true;
    }

    public int extraBeaconSlots(UUID id) { return extraBeacons.getOrDefault(id, 0); }

    public void setExtraBeaconSlots(UUID id, int n) {
        if (n <= 0) extraBeacons.remove(id); else extraBeacons.put(id, n);
        dirty = true;
    }

    // ---------- offline ----------

    public Long lastQuit(UUID id) { return lastQuit.get(id); }

    public void setQuit(UUID id, long millis) {
        lastQuit.put(id, millis);
        dirty = true;
    }

    public void clearQuit(UUID id) {
        if (lastQuit.remove(id) != null) dirty = true;
    }

    public OfflineReward pending(UUID id) { return pending.get(id); }

    public void addPending(UUID id, OfflineReward r) {
        OfflineReward cur = pending.get(id);
        if (cur == null) pending.put(id, r); else cur.merge(r);
        dirty = true;
    }

    public void removePending(UUID id) {
        if (pending.remove(id) != null) dirty = true;
    }

    // ---------- economy ----------

    public double totalMinted() { return totalMinted; }

    public void mint(UUID id, double amount) {
        double budget = plugin.getConfig().getDouble("economy.budget", 5.0E16);
        double before = totalMinted;
        totalMinted += amount;
        earned.merge(id, amount, Double::sum);
        dirty = true;
        for (double t : new double[]{0.5, 0.8, 1.0}) {
            if (before < budget * t && totalMinted >= budget * t) {
                plugin.getLogger().warning("Money created by generators: " + Math.round(t * 100)
                        + "% of the budget (" + Fmt.money(totalMinted) + " / " + Fmt.money(budget) + ")");
            }
        }
    }

    // ---------- beacon expiry ----------

    public void expireBeacons() {
        long now = System.currentTimeMillis();
        List<BeaconData> expired = new ArrayList<>();
        for (BeaconData b : beacons.values()) if (b.expires <= now) expired.add(b);
        for (BeaconData b : expired) {
            Block bl = b.block();
            if (bl != null && bl.getType() == Material.BEACON) bl.setType(Material.AIR);
            removeBeacon(b);
            Player p = Bukkit.getPlayer(b.owner);
            if (p != null) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cOne of your beacons (" + b.type.display + " "
                        + Fmt.percent(b.bonus) + ") has expired.");
            }
        }
    }

    // ---------- persistence ----------

    public void load() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        totalMinted = y.getDouble("total-minted", 0);

        ConfigurationSection sg = y.getConfigurationSection("generators");
        if (sg != null) {
            for (String id : sg.getKeys(false)) {
                ConfigurationSection c = sg.getConfigurationSection(id);
                if (c == null) continue;
                Gen g = new Gen(UUID.fromString(c.getString("owner", "")), c.getString("world", "world"),
                        c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getInt("tier", 1));
                gens.put(key(g.world, g.x, g.y, g.z), g);
            }
        }

        ConfigurationSection sb = y.getConfigurationSection("barrels");
        if (sb != null) {
            for (String id : sb.getKeys(false)) {
                ConfigurationSection c = sb.getConfigurationSection(id);
                if (c == null) continue;
                BarrelData d = new BarrelData(UUID.fromString(c.getString("owner", "")), c.getString("world", "world"),
                        c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getInt("level", 1));
                ConfigurationSection st = c.getConfigurationSection("stored");
                if (st != null) {
                    for (String t : st.getKeys(false)) {
                        d.stored.put(Integer.parseInt(t), st.getLong(t));
                    }
                }
                barrels.put(key(d.world, d.x, d.y, d.z), d);
            }
        }

        ConfigurationSection sc = y.getConfigurationSection("beacons");
        if (sc != null) {
            for (String id : sc.getKeys(false)) {
                ConfigurationSection c = sc.getConfigurationSection(id);
                if (c == null) continue;
                BeaconData d = new BeaconData(UUID.fromString(c.getString("owner", "")), c.getString("world", "world"),
                        c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getDouble("bonus"), c.getLong("expires"),
                        BoostType.parse(c.getString("type"), BoostType.MONEY));
                beacons.put(key(d.world, d.x, d.y, d.z), d);
            }
        }

        ConfigurationSection sp = y.getConfigurationSection("players");
        if (sp != null) {
            for (String id : sp.getKeys(false)) {
                ConfigurationSection c = sp.getConfigurationSection(id);
                if (c == null) continue;
                UUID u = UUID.fromString(id);
                if (c.contains("earned")) earned.put(u, c.getDouble("earned"));
                if (c.getInt("extra-generators") > 0) extraGens.put(u, c.getInt("extra-generators"));
                if (c.getInt("extra-beacons") > 0) extraBeacons.put(u, c.getInt("extra-beacons"));
                if (c.contains("last-quit")) lastQuit.put(u, c.getLong("last-quit"));
            }
        }

        ConfigurationSection so = y.getConfigurationSection("offline");
        if (so != null) {
            for (String id : so.getKeys(false)) {
                ConfigurationSection c = so.getConfigurationSection(id);
                if (c == null) continue;
                OfflineReward r = new OfflineReward(c.getLong("seconds"));
                ConfigurationSection dr = c.getConfigurationSection("drops");
                if (dr != null) for (String t : dr.getKeys(false)) r.drops.put(Integer.parseInt(t), dr.getLong(t));
                ConfigurationSection ge = c.getConfigurationSection("gens");
                if (ge != null) for (String t : ge.getKeys(false)) r.gens.put(Integer.parseInt(t), ge.getInt(t));
                if (!r.drops.isEmpty()) pending.put(UUID.fromString(id), r);
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("total-minted", totalMinted);

        int i = 0;
        for (Gen g : gens.values()) {
            String p = "generators." + (i++);
            y.set(p + ".owner", g.owner.toString());
            y.set(p + ".world", g.world);
            y.set(p + ".x", g.x);
            y.set(p + ".y", g.y);
            y.set(p + ".z", g.z);
            y.set(p + ".tier", g.tier);
        }

        i = 0;
        for (BarrelData d : barrels.values()) {
            String p = "barrels." + (i++);
            y.set(p + ".owner", d.owner.toString());
            y.set(p + ".world", d.world);
            y.set(p + ".x", d.x);
            y.set(p + ".y", d.y);
            y.set(p + ".z", d.z);
            y.set(p + ".level", d.level);
            for (Map.Entry<Integer, Long> e : d.stored.entrySet()) {
                y.set(p + ".stored." + e.getKey(), e.getValue());
            }
        }

        i = 0;
        for (BeaconData d : beacons.values()) {
            String p = "beacons." + (i++);
            y.set(p + ".owner", d.owner.toString());
            y.set(p + ".world", d.world);
            y.set(p + ".x", d.x);
            y.set(p + ".y", d.y);
            y.set(p + ".z", d.z);
            y.set(p + ".bonus", d.bonus);
            y.set(p + ".expires", d.expires);
            y.set(p + ".type", d.type.name());
        }

        for (Map.Entry<UUID, Double> e : earned.entrySet()) y.set("players." + e.getKey() + ".earned", e.getValue());
        for (Map.Entry<UUID, Integer> e : extraGens.entrySet()) y.set("players." + e.getKey() + ".extra-generators", e.getValue());
        for (Map.Entry<UUID, Integer> e : extraBeacons.entrySet()) y.set("players." + e.getKey() + ".extra-beacons", e.getValue());
        for (Map.Entry<UUID, Long> e : lastQuit.entrySet()) y.set("players." + e.getKey() + ".last-quit", e.getValue());

        for (Map.Entry<UUID, OfflineReward> e : pending.entrySet()) {
            String p = "offline." + e.getKey();
            OfflineReward r = e.getValue();
            y.set(p + ".seconds", r.seconds);
            for (Map.Entry<Integer, Long> d : r.drops.entrySet()) y.set(p + ".drops." + d.getKey(), d.getValue());
            for (Map.Entry<Integer, Integer> g : r.gens.entrySet()) y.set(p + ".gens." + g.getKey(), g.getValue());
        }

        try {
            plugin.getDataFolder().mkdirs();
            y.save(new File(plugin.getDataFolder(), "data.yml"));
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }
}

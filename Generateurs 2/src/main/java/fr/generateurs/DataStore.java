package fr.generateurs;

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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class DataStore {

    public static final class Gen {
        public final UUID owner;
        public final String world;
        public final int x, y, z;
        public int tier;

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
    }

    public static final class BeaconData {
        public final UUID owner;
        public final String world;
        public final int x, y, z;
        public final double bonus;
        public final long expires; // epoch millis

        BeaconData(UUID owner, String world, int x, int y, int z, double bonus, long expires) {
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.bonus = bonus;
            this.expires = expires;
        }
    }

    private final GeneratorsPlugin plugin;
    private final Map<String, Gen> gens = new HashMap<>();
    private final Map<String, BarrelData> barrels = new HashMap<>();
    private final Map<String, BeaconData> beacons = new HashMap<>();
    private double totalMinted;
    private boolean dirty;

    public DataStore(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- cles ----------

    static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    static String key(Block b) {
        return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    // ---------- acces ----------

    public Gen gen(Block b) { return gens.get(key(b)); }

    public BarrelData barrelAt(Block b) { return barrels.get(key(b)); }

    public BeaconData beaconAt(Block b) { return beacons.get(key(b)); }

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

    public Map<UUID, BarrelData> barrelsByOwner() {
        Map<UUID, BarrelData> map = new HashMap<>();
        for (BarrelData b : barrels.values()) map.put(b.owner, b);
        return map;
    }

    /** Somme des bonus des beacons actifs de ce joueur (0.5 = +50%). */
    public double beaconBonus(UUID owner) {
        long now = System.currentTimeMillis();
        double sum = 0;
        for (BeaconData b : beacons.values()) {
            if (b.owner.equals(owner) && b.expires > now) sum += b.bonus;
        }
        return sum;
    }

    // ---------- ajout / retrait ----------

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

    public BeaconData addBeacon(UUID owner, Block b, double bonus, long expires) {
        BeaconData d = new BeaconData(owner, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), bonus, expires);
        beacons.put(key(b), d);
        dirty = true;
        return d;
    }

    public void removeBeacon(BeaconData d) {
        beacons.remove(key(d.world, d.x, d.y, d.z));
        dirty = true;
    }

    public void markDirty() { dirty = true; }

    // ---------- economie ----------

    public double totalMinted() { return totalMinted; }

    public void mint(double amount) {
        double budget = plugin.getConfig().getDouble("economy.budget", 5.0E16);
        double before = totalMinted;
        totalMinted += amount;
        dirty = true;
        for (double t : new double[]{0.5, 0.8, 1.0}) {
            if (before < budget * t && totalMinted >= budget * t) {
                plugin.getLogger().warning("Argent cree par les generateurs : " + Math.round(t * 100)
                        + "% du budget (" + Fmt.money(totalMinted) + " / " + Fmt.money(budget) + ")");
            }
        }
    }

    // ---------- expiration des beacons ----------

    public void expireBeacons() {
        long now = System.currentTimeMillis();
        List<BeaconData> expired = new ArrayList<>();
        for (BeaconData b : beacons.values()) if (b.expires <= now) expired.add(b);
        for (BeaconData b : expired) {
            World w = Bukkit.getWorld(b.world);
            if (w != null) {
                Block bl = w.getBlockAt(b.x, b.y, b.z);
                if (bl.getType() == Material.BEACON) bl.setType(Material.AIR);
            }
            removeBeacon(b);
            Player p = Bukkit.getPlayer(b.owner);
            if (p != null) {
                p.sendMessage(GeneratorsPlugin.PREFIX + "§cUn de tes beacons (" + Fmt.percent(b.bonus) + ") a expiré.");
            }
        }
    }

    // ---------- persistance ----------

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
                        c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getDouble("bonus"), c.getLong("expires"));
                beacons.put(key(d.world, d.x, d.y, d.z), d);
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
        }

        try {
            plugin.getDataFolder().mkdirs();
            y.save(new File(plugin.getDataFolder(), "data.yml"));
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + ex.getMessage());
        }
    }
}

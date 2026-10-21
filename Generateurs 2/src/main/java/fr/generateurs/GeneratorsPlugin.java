package fr.generateurs;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class GeneratorsPlugin extends JavaPlugin {

    public static final int TIERS = 100;
    public static final String PREFIX = "§8[§6Gen§8] §r";

    private Economy economy;
    private Tiers tiers;
    private DataStore store;
    private Items items;
    private ShopGui shop;

    public NamespacedKey kTier, kBarrel, kBeaconBonus, kBeaconRemaining, kDropTier, kDropOwner;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Aucune economie Vault trouvee (EssentialsX + Vault requis). Plugin desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();

        kTier = new NamespacedKey(this, "gen_tier");
        kBarrel = new NamespacedKey(this, "barrel_level");
        kBeaconBonus = new NamespacedKey(this, "beacon_bonus");
        kBeaconRemaining = new NamespacedKey(this, "beacon_remaining");
        kDropTier = new NamespacedKey(this, "drop_tier");
        kDropOwner = new NamespacedKey(this, "drop_owner");

        tiers = new Tiers(getConfig());
        items = new Items(this);
        store = new DataStore(this);
        store.load();
        shop = new ShopGui(this);

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PickupListener(this), this);
        getServer().getPluginManager().registerEvents(shop, this);

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
        if (store != null) store.save();
    }

    public void reloadAll() {
        reloadConfig();
        tiers = new Tiers(getConfig());
    }

    public Economy economy() { return economy; }
    public Tiers tiers() { return tiers; }
    public DataStore store() { return store; }
    public Items items() { return items; }
    public ShopGui shop() { return shop; }

    public int intervalSeconds() {
        return Math.max(1, getConfig().getInt("interval-seconds", 15));
    }

    public int maxBarrelLevel() {
        return Math.max(1, getConfig().getInt("barrel.max-level", 20));
    }

    public long barrelCapacity(int level) {
        return getConfig().getLong("barrel.capacity-per-level", 10000L) * Math.max(1, level);
    }

    public int maxBeacons() {
        return Math.max(0, getConfig().getInt("limits.max-beacons", 3));
    }

    public double maxBeaconBonus() {
        return Math.max(0.0, getConfig().getDouble("beacon.max-bonus", 1.0));
    }

    public int maxGenerators(Player p) {
        int max = getConfig().getInt("limits.default-max-generators", 25);
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith("gen.max.")) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.substring("gen.max.".length())));
                } catch (NumberFormatException ignored) {
                    // permission non numerique : ignoree
                }
            }
        }
        return max;
    }

    /** Gain pour 'count' items d'un palier. barrelLevel = 1 si l'item est ramasse au sol. */
    public double reward(UUID owner, int tier, long count, int barrelLevel) {
        double mult = (1.0 + store.beaconBonus(owner)) * Math.max(1, barrelLevel);
        return tiers.value(tier) * (double) count * mult;
    }

    public void pay(UUID id, double amount) {
        if (amount <= 0) return;
        economy.depositPlayer(Bukkit.getOfflinePlayer(id), amount);
        store.mint(amount);
    }

    public boolean withdraw(Player p, double amount) {
        if (!economy.has(p, amount)) return false;
        return economy.withdrawPlayer(p, amount).transactionSuccess();
    }

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

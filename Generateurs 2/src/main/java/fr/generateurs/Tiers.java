package fr.generateurs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Les 100 paliers : materiau, valeur par item et prix.
 * Valeur(1) = base-value, Valeur(100) = top-value, courbe exponentielle entre les deux.
 * Prix = valeur x price-multiplier. Amelioration = prix(n+1) - prix(n).
 */
public final class Tiers {

    private static final String[] NAMES = {
            "HAY_BLOCK", "DIRT", "COARSE_DIRT", "OAK_PLANKS", "SPRUCE_PLANKS",
            "BIRCH_PLANKS", "COBBLESTONE", "STONE", "ANDESITE", "DIORITE",
            "GRANITE", "SMOOTH_STONE", "STONE_BRICKS", "MOSSY_COBBLESTONE", "JUNGLE_PLANKS",
            "ACACIA_PLANKS", "DARK_OAK_PLANKS", "MANGROVE_PLANKS", "CHERRY_PLANKS", "BAMBOO_PLANKS",
            "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG",
            "DARK_OAK_LOG", "BRICKS", "TERRACOTTA", "WHITE_TERRACOTTA", "ORANGE_TERRACOTTA",
            "YELLOW_TERRACOTTA", "LIME_TERRACOTTA", "CYAN_TERRACOTTA", "BLUE_TERRACOTTA", "PURPLE_TERRACOTTA",
            "RED_TERRACOTTA", "WHITE_WOOL", "ORANGE_WOOL", "YELLOW_WOOL", "LIME_WOOL",
            "CYAN_WOOL", "BLUE_WOOL", "PURPLE_WOOL", "RED_WOOL", "WHITE_CONCRETE",
            "ORANGE_CONCRETE", "YELLOW_CONCRETE", "LIME_CONCRETE", "CYAN_CONCRETE", "BLUE_CONCRETE",
            "PURPLE_CONCRETE", "RED_CONCRETE", "GLASS", "SEA_LANTERN", "PRISMARINE",
            "PRISMARINE_BRICKS", "DARK_PRISMARINE", "NETHERRACK", "NETHER_BRICKS", "RED_NETHER_BRICKS",
            "BASALT", "BLACKSTONE", "POLISHED_BLACKSTONE", "POLISHED_BLACKSTONE_BRICKS", "GILDED_BLACKSTONE",
            "SOUL_SOIL", "MAGMA_BLOCK", "GLOWSTONE", "SHROOMLIGHT", "END_STONE",
            "END_STONE_BRICKS", "PURPUR_BLOCK", "OBSIDIAN", "CRYING_OBSIDIAN", "COAL_BLOCK",
            "COPPER_BLOCK", "IRON_BLOCK", "REDSTONE_BLOCK", "LAPIS_BLOCK", "GOLD_BLOCK",
            "QUARTZ_BLOCK", "AMETHYST_BLOCK", "EMERALD_BLOCK", "DIAMOND_BLOCK", "NETHERITE_BLOCK",
            "SLIME_BLOCK", "HONEY_BLOCK", "BONE_BLOCK", "PACKED_ICE", "BLUE_ICE",
            "DRIED_KELP_BLOCK", "MUD_BRICKS", "DEEPSLATE_BRICKS", "REINFORCED_DEEPSLATE", "OCHRE_FROGLIGHT",
            "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT", "NETHER_WART_BLOCK", "SCULK", "COMMAND_BLOCK"
    };

    private final Material[] materials = new Material[GeneratorsPlugin.TIERS + 1];
    private final long[] values = new long[GeneratorsPlugin.TIERS + 1];
    private final long priceMultiplier;

    public Tiers(FileConfiguration cfg) {
        final int n = GeneratorsPlugin.TIERS;

        for (int i = 1; i <= n; i++) {
            Material m = Material.matchMaterial(NAMES[i - 1]);
            if (m == null) {
                Bukkit.getLogger().warning("[Generateurs] Materiau inconnu pour le palier " + i + " : " + NAMES[i - 1]);
                m = Material.STONE;
            }
            materials[i] = m;
        }

        double base = Math.max(1.0, cfg.getDouble("economy.base-value", 1.0));
        double top = Math.max(base * 2.0, cfg.getDouble("economy.top-value", 5_000_000.0));
        double growth = Math.pow(top / base, 1.0 / (n - 1));

        values[1] = Math.round(base);
        for (int i = 2; i <= n; i++) {
            long v = Math.round(base * Math.pow(growth, i - 1));
            if (v <= values[i - 1]) v = values[i - 1] + 1; // strictement croissant
            values[i] = v;
        }

        priceMultiplier = Math.max(2L, cfg.getLong("economy.price-multiplier", 50L));
    }

    public Material material(int tier) { return materials[tier]; }

    public long value(int tier) { return values[tier]; }

    public long price(int tier) { return values[tier] * priceMultiplier; }

    /** Cout pour passer de 'tier' a 'tier + 1' = difference des prix d'achat. */
    public long upgradeCost(int tier) { return price(tier + 1) - price(tier); }

    public long priceMultiplier() { return priceMultiplier; }
}

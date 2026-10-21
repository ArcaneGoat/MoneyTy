package fr.moneyty.generators;

import org.bukkit.Material;

import java.util.Locale;

/** What a beacon boosts. The owner picks it in the beacon menu. */
public enum BoostType {

    MONEY("Money", "sell value of your drops", Material.GOLD_INGOT),
    SPEED("Speed", "production speed of your generators", Material.SUGAR),
    CAPACITY("Capacity", "capacity of your barrel", Material.CHEST);

    public final String display;
    public final String description;
    public final Material icon;

    BoostType(String display, String description, Material icon) {
        this.display = display;
        this.description = description;
        this.icon = icon;
    }

    public static BoostType parse(String s, BoostType def) {
        if (s == null) return def;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}

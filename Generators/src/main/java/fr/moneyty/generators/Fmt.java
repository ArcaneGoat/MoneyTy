package fr.moneyty.generators;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Fmt {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Fmt() {}

    /** 1500 -> 1.5K ; 2.5e8 -> 250M ; 5e16 -> 50Q */
    public static String money(double value) {
        double a = Math.abs(value);
        int i = 0;
        while (a >= 1000.0 && i < SUFFIXES.length - 1) {
            a /= 1000.0;
            i++;
        }
        String s = String.format(Locale.US, "%.2f", a);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return (value < 0 ? "-" : "") + s + SUFFIXES[i];
    }

    /** 1234567 -> 1,234,567 */
    public static String count(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    /** HAY_BLOCK -> Hay Block */
    public static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String percent(double bonus) {
        return "+" + Math.round(bonus * 100.0) + "%";
    }

    /** Translates &#RRGGBB hex colors and & codes into Minecraft colors. */
    public static String color(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(ChatColor.of("#" + m.group(1)).toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}

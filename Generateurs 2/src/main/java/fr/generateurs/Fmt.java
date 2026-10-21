package fr.generateurs;

import org.bukkit.Material;

import java.util.Locale;

public final class Fmt {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};

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

    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" j ");
        if (h > 0) sb.append(h).append(" h ");
        if (m > 0) sb.append(m).append(" min ");
        if (s > 0 || sb.length() == 0) sb.append(s).append(" s");
        return sb.toString().trim();
    }

    public static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String percent(double bonus) {
        return "+" + Math.round(bonus * 100.0) + "%";
    }
}

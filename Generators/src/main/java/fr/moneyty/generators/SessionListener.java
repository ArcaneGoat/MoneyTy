package fr.moneyty.generators;

import fr.moneyty.generators.menu.OfflineMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Offline production: away for 30+ minutes = drops minus 25%, shown in a menu at reconnection. */
public final class SessionListener implements Listener {

    private final GeneratorsPlugin pl;

    public SessionListener(GeneratorsPlugin pl) {
        this.pl = pl;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.store().setQuit(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        DataStore store = pl.store();

        Long quit = store.lastQuit(id);
        store.clearQuit(id);
        if (quit != null) {
            long away = (System.currentTimeMillis() - quit) / 1000L;
            if (away >= pl.offlineMinSeconds()) {
                DataStore.OfflineReward r = pl.computeOffline(id, away);
                if (r != null) store.addPending(id, r);
            }
        }

        if (store.pending(id) == null) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || store.pending(id) == null) return;
            pl.sendOfflineSummary(p);
            new OfflineMenu(pl, p).open(p);
        }, 40L);
    }
}

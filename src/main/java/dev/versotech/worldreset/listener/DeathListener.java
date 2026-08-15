package dev.versotech.worldreset.listener;

import dev.versotech.worldreset.reset.ResetCoordinator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * O gatilho da mecanica: qualquer morte de qualquer jogador online recria o
 * mundo.
 */
public final class DeathListener implements Listener {

    private final ResetCoordinator coordinator;

    public DeathListener(ResetCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();

        // Nao vale a pena dropar nada: o mundo inteiro esta prestes a sumir, e os
        // item entities so atrapalhariam o descarregamento.
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(false);
        event.setKeepLevel(false);

        if (player.hasPermission("worldreset.exempt")) {
            return;
        }
        coordinator.onDeath(player);
    }
}

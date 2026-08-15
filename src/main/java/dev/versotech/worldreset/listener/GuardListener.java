package dev.versotech.worldreset.listener;

import dev.versotech.worldreset.config.ResetSettings;
import dev.versotech.worldreset.reset.ResetCoordinator;
import dev.versotech.worldreset.world.WorldLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mantem os jogadores fora dos mundos condenados e o lobby inofensivo.
 *
 * <p>O lobby precisa ser absolutamente seguro: qualquer forma de morrer ali
 * dispararia um reset durante o proprio reset.
 */
public final class GuardListener implements Listener {

    private final JavaPlugin plugin;
    private final ResetSettings settings;
    private final ResetCoordinator coordinator;
    private final WorldLifecycle lifecycle;

    public GuardListener(JavaPlugin plugin,
                         ResetSettings settings,
                         ResetCoordinator coordinator,
                         WorldLifecycle lifecycle) {
        this.plugin = plugin;
        this.settings = settings;
        this.coordinator = coordinator;
        this.lifecycle = lifecycle;
    }

    private boolean isLobby(World world) {
        return world != null && world.getName().equals(settings.lobbyWorldName());
    }

    private Location activeSpawn() {
        World active = Bukkit.getWorld(coordinator.slotState().active().overworld());
        return active == null ? lifecycle.lobbySpawn() : active.getSpawnLocation();
    }

    // ------------------------------------------------------------ entrada

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Um tick de atraso deixa o EssentialsX aplicar o spawn-on-join dele
        // primeiro; so entao decidimos o destino final.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (coordinator.isRunning()) {
                player.teleport(lifecycle.lobbySpawn());
                return;
            }

            World current = player.getWorld();
            // Comparacao exata contra os tres mundos do slot: um startsWith aqui
            // daria falso positivo se os dois slots compartilhassem prefixo.
            boolean inActiveSlot = coordinator.slotState().active()
                    .allWorldNames().contains(current.getName());

            if (!inActiveSlot) {
                player.teleport(activeSpawn());
            }
        });
    }

    // ------------------------------------------------------------ respawn

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location destination = coordinator.isRunning() ? lifecycle.lobbySpawn() : activeSpawn();
        event.setRespawnLocation(destination);

        // O EssentialsX Spawn tambem escreve nesse evento. Reaplicar um tick
        // depois e mais confiavel do que disputar prioridade de listener.
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            World current = player.getWorld();
            if (!current.equals(destination.getWorld())) {
                player.teleport(destination);
            }
        });
    }

    // -------------------------------------------------------- lobby seguro

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isLobby(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (isLobby(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isLobby(event.getPlayer().getWorld())
                && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isLobby(event.getPlayer().getWorld())
                && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }
}

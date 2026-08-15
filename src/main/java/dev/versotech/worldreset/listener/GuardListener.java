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
        ensureInActiveWorld(event.getPlayer(), settings.joinVerifyAttempts());
    }

    /**
     * Leva o jogador ao mundo ativo e <em>reconfere</em> algumas vezes.
     *
     * <p>Um unico teleporte no join nao basta. O EssentialsX tem
     * {@code newbies.spawnpoint}, que no primeiro acesso de cada jogador o
     * manda para o spawn dele - e esse spawn, se nunca foi definido, e o do
     * mundo principal, ou seja, o lobby. Como esse teleporte acontece depois do
     * nosso, o jogador ficava presos no lobby exatamente na primeira entrada.
     *
     * <p>Reconferir resolve sem depender da configuracao de outro plugin: se
     * algo puxou o jogador de volta, a proxima passagem o traz de novo.
     */
    private void ensureInActiveWorld(Player player, int attemptsLeft) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (coordinator.isRunning()) {
                // Durante um reset o lobby e o lugar certo; o proprio reset
                // move todo mundo para a arena nova quando terminar.
                player.teleport(lifecycle.lobbySpawn());
                return;
            }

            World active = Bukkit.getWorld(coordinator.slotState().active().overworld());
            if (active == null) {
                plugin.getLogger().severe("O mundo ativo '"
                        + coordinator.slotState().active().overworld()
                        + "' nao esta carregado; " + player.getName() + " ficara no lobby.");
                return;
            }

            World current = player.getWorld();
            // Comparacao exata contra os tres mundos do slot: um startsWith aqui
            // daria falso positivo se os dois slots compartilhassem prefixo.
            boolean inActiveSlot = coordinator.slotState().active()
                    .allWorldNames().contains(current.getName());

            if (!inActiveSlot) {
                player.teleport(active.getSpawnLocation());
            }

            if (attemptsLeft > 1) {
                ensureInActiveWorld(player, attemptsLeft - 1);
            }
        }, settings.joinTeleportDelayTicks());
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

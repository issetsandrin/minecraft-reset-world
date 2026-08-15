package dev.versotech.worldreset.listener;

import dev.versotech.worldreset.config.ResetSettings;
import dev.versotech.worldreset.reset.ResetCoordinator;
import dev.versotech.worldreset.world.WorldLifecycle;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Garante a regra central do servidor: o jogador vive no mundo jogavel, nunca no
 * lobby.
 *
 * <p>O lobby existe para um unico fim - segurar todo mundo durante o segundo em
 * que a arena e trocada. Fora dessa janela, ninguem deveria ve-lo. Sao tres
 * camadas, da mais precoce para a mais tardia:
 *
 * <ol>
 *   <li>{@link AsyncPlayerSpawnLocationEvent} decide onde o jogador entra, antes de
 *       ele existir no mundo. E o que impede de sequer aparecer no lobby.</li>
 *   <li>{@link PlayerTeleportEvent} intercepta qualquer plugin que tente
 *       manda-lo para o lobby fora de um reset.</li>
 *   <li>Uma reconferencia apos a entrada, como rede final.</li>
 * </ol>
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

    /** O mundo jogavel do momento, ou null se ainda nao foi carregado. */
    private World activeWorld() {
        return Bukkit.getWorld(coordinator.slotState().active().overworld());
    }

    private boolean inActiveSlot(World world) {
        return world != null
                && coordinator.slotState().active().allWorldNames().contains(world.getName());
    }

    // ------------------------------------------------- onde o jogador nasce

    /**
     * Define o ponto de entrada antes de o jogador ser colocado no mundo.
     *
     * <p>Este e o unico momento em que da para evitar que ele <em>veja</em> o
     * lobby. Corrigir depois, por teleporte, sempre deixa alguns quadros de um
     * mundo vazio na tela - foi o que fez a primeira versao parecer quebrada.
     *
     * <p>Para quem nunca jogou aqui, o padrao do servidor e o spawn do mundo
     * principal, que neste desenho e justamente o lobby. Sem este tratamento,
     * todo jogador novo comecaria no lugar errado.
     *
     * <p>O evento e assincrono, entao nada de tocar no mundo daqui: usamos o
     * snapshot que o coordenador mantem.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (coordinator.isRunning()) {
            // Unico caso legitimo de lobby: a arena esta sendo trocada agora. O
            // proprio reset leva todo mundo para o mundo novo ao terminar.
            Location lobby = lifecycle.lobbySpawn();
            if (lobby.getWorld() != null) {
                event.setSpawnLocation(lobby);
            }
            return;
        }

        Location arenaSpawn = coordinator.activeSpawnSnapshot();
        if (arenaSpawn == null || arenaSpawn.getWorld() == null) {
            plugin.getLogger().severe(
                    "O mundo ativo ainda nao esta preparado; um jogador entrara no spawn padrao.");
            return;
        }

        // Quem ja estava jogando volta exatamente onde parou; so quem estaria
        // aparecendo no lobby (ou num mundo que nao existe mais) e realocado.
        if (!inActiveSlot(event.getSpawnLocation().getWorld())) {
            event.setSpawnLocation(arenaSpawn);
        }
    }

    // --------------------------------------- ninguem e levado para o lobby

    /**
     * Intercepta teleportes para o lobby vindos de fora daqui.
     *
     * <p>O caso concreto e o EssentialsX: {@code newbies.spawnpoint} manda todo
     * jogador de primeiro acesso para o spawn dele, que costuma ser o do mundo
     * principal - o lobby. Sem esta trava, o jogador novo era depositado la
     * logo apos entrar.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location destination = event.getTo();
        if (destination == null || !isLobby(destination.getWorld())) {
            return;
        }
        if (coordinator.isRunning()) {
            return;
        }
        // Um administrador pode querer inspecionar o lobby de proposito.
        if (event.getPlayer().hasPermission("worldreset.admin")) {
            return;
        }

        World active = activeWorld();
        if (active == null) {
            return;
        }

        plugin.getLogger().fine("Teleporte de " + event.getPlayer().getName()
                + " para o lobby redirecionado para o mundo ativo.");
        event.setTo(active.getSpawnLocation());
    }

    // ------------------------------------------------------------ entrada

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ensureInActiveWorld(event.getPlayer(), settings.joinVerifyAttempts());
    }

    /**
     * Rede final. As duas camadas anteriores deveriam bastar, mas um plugin que
     * teleporte com atraso ainda escaparia delas.
     */
    private void ensureInActiveWorld(Player player, int attemptsLeft) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || coordinator.isRunning()) {
                return;
            }

            World active = activeWorld();
            if (active == null) {
                plugin.getLogger().severe("O mundo ativo nao esta carregado; "
                        + player.getName() + " permanece onde esta.");
                return;
            }

            if (!inActiveSlot(player.getWorld())) {
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
        World active = activeWorld();
        Location destination = (coordinator.isRunning() || active == null)
                ? lifecycle.lobbySpawn()
                : active.getSpawnLocation();

        event.setRespawnLocation(destination);

        // O EssentialsX Spawn tambem escreve neste evento. Reaplicar um tick
        // depois e mais confiavel do que disputar prioridade de listener.
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.getWorld().equals(destination.getWorld())) {
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

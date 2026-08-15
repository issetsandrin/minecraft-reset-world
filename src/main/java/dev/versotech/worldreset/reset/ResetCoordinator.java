package dev.versotech.worldreset.reset;

import dev.versotech.worldreset.config.Messages;
import dev.versotech.worldreset.config.ResetSettings;
import dev.versotech.worldreset.integration.EssentialsHook;
import dev.versotech.worldreset.player.PlayerWiper;
import dev.versotech.worldreset.world.Arena;
import dev.versotech.worldreset.world.ChunkPregenerator;
import dev.versotech.worldreset.world.SafeSpawnFinder;
import dev.versotech.worldreset.world.SlotState;
import dev.versotech.worldreset.world.WorldLifecycle;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquestra o ciclo completo: morte, contagem regressiva, troca de mundo e
 * preparo do proximo.
 *
 * <p>A troca em si e barata porque o mundo seguinte ja existe e ja foi gerado em
 * background. O que esta classe realmente administra e a janela perigosa em
 * volta dela - a tela de morte, o mundo prestes a ser apagado e os primeiros
 * segundos no terreno novo, onde uma segunda morte reiniciaria tudo de novo.
 */
public final class ResetCoordinator {

    private final JavaPlugin plugin;
    private final ResetSettings settings;
    private final Messages messages;
    private final SlotState slotState;
    private final WorldLifecycle lifecycle;
    private final ChunkPregenerator pregenerator;
    private final SafeSpawnFinder spawnFinder;
    private final PlayerWiper wiper;
    private final EssentialsHook essentials;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private long lastResetAt = 0L;
    private long graceUntil = 0L;

    /**
     * Ultimo spawn valido do mundo ativo.
     *
     * <p>Existe para ser lido de fora da thread principal: a escolha do ponto de
     * entrada de quem conecta acontece num evento assincrono, e consultar o
     * mundo de la seria pedir problema.
     */
    private volatile Location activeSpawnSnapshot;

    public ResetCoordinator(JavaPlugin plugin,
                            ResetSettings settings,
                            Messages messages,
                            SlotState slotState,
                            WorldLifecycle lifecycle,
                            ChunkPregenerator pregenerator,
                            SafeSpawnFinder spawnFinder,
                            PlayerWiper wiper,
                            EssentialsHook essentials) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.slotState = slotState;
        this.lifecycle = lifecycle;
        this.pregenerator = pregenerator;
        this.spawnFinder = spawnFinder;
        this.wiper = wiper;
        this.essentials = essentials;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean inGrace() {
        return System.currentTimeMillis() < graceUntil;
    }

    public long secondsUntilArmed() {
        long elapsed = (System.currentTimeMillis() - lastResetAt) / 1000L;
        return Math.max(0L, settings.minSecondsBetweenResets() - elapsed);
    }

    /**
     * Ponto de entrada da morte. Todas as travas contra reset em cascata vivem
     * aqui: um reset em andamento, a janela de graca e o intervalo minimo entre
     * resets.
     */
    public void onDeath(Player victim) {
        if (running.get()) {
            return;
        }
        if (inGrace() || secondsUntilArmed() > 0) {
            plugin.getLogger().info("Morte de " + victim.getName()
                    + " ignorada: reset recente (faltam " + secondsUntilArmed() + "s para rearmar).");
            victim.sendMessage(messages.chat("cooldown-ignored"));
            return;
        }
        begin(victim.getName());
    }

    /** Reset manual, sem morte envolvida. */
    public boolean forceReset(String cause) {
        if (running.get()) {
            return false;
        }
        begin(cause);
        return true;
    }

    private void begin(String cause) {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        int seconds = settings.countdownSeconds();
        if (settings.announceCause()) {
            Bukkit.broadcast(messages.chat("death-triggered", "player", cause, "seconds", seconds));
        }

        if (seconds <= 0) {
            performSwap();
            return;
        }

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    cancel();
                    performSwap();
                    return;
                }
                var title = Title.title(
                        messages.plain("countdown-title", "seconds", remaining),
                        messages.plain("countdown-subtitle"),
                        0, 25, 5);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(title);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.7f);
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * A troca propriamente dita. Roda inteira na thread principal e em uma unica
     * passagem: nao pode haver tick entre esvaziar o mundo antigo e apaga-lo.
     */
    private void performSwap() {
        try {
            Bukkit.broadcast(messages.chat("reset-started"));

            Arena oldArena = slotState.active();
            Arena newArena = slotState.standby();

            World newWorld = Bukkit.getWorld(newArena.overworld());
            if (newWorld == null) {
                // Standby ausente (primeiro boot ou falha anterior): criar agora
                // custa alguns segundos de trava, mas e melhor do que nao resetar.
                plugin.getLogger().warning("Slot de espera '" + newArena.overworld()
                        + "' nao estava carregado; criando na hora.");
                newWorld = lifecycle.loadOrCreateArena(newArena);
            }

            Location lobby = lifecycle.lobbySpawn();
            evacuateToLobby(lobby);

            Location spawn = spawnFinder.find(newWorld);
            newWorld.setSpawnLocation(spawn);
            activeSpawnSnapshot = spawn;

            for (Player player : Bukkit.getOnlinePlayers()) {
                wiper.wipe(player);
            }
            int offlineWiped = wiper.wipeOfflinePlayers();

            long graceMillis = settings.graceSeconds() * 1000L;
            graceUntil = System.currentTimeMillis() + graceMillis;
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendToArena(player, spawn);
            }

            slotState.swap();

            if (!lifecycle.destroyArena(oldArena)) {
                plugin.getLogger().severe("O slot antigo nao pode ser removido agora;"
                        + " nova tentativa sera feita ao preparar o proximo slot.");
            }

            essentials.applyReset(spawn);

            Bukkit.broadcast(messages.chat("reset-done"));
            plugin.getLogger().info("Reset #" + slotState.resetCount() + " concluido. Mundo ativo: "
                    + newArena.overworld() + ". Arquivos offline removidos: " + offlineWiped + ".");

            prepareNextArena(true);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Reset falhou no meio do caminho", e);
        } finally {
            lastResetAt = System.currentTimeMillis();
            running.set(false);
        }
    }

    /**
     * Tira todo mundo do mundo condenado. O respawn forcado e obrigatorio: um
     * jogador parado na tela de morte continua contando como presente no mundo, e
     * o Bukkit se recusa a descarregar um mundo com jogador dentro.
     */
    private void evacuateToLobby(Location lobby) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            player.setInvulnerable(true);
            player.teleport(lobby);
        }
    }

    private void sendToArena(Player player, Location spawn) {
        player.teleport(spawn);
        player.setRespawnLocation(spawn, true);
        player.setInvulnerable(true);
        player.sendMessage(messages.chat("grace-active", "seconds", settings.graceSeconds()));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setInvulnerable(false);
            }
        }, Math.max(1L, settings.graceSeconds() * 20L));
    }

    /**
     * Cria o proximo slot e comeca a pre-gerar, para o reset seguinte ser
     * instantaneo.
     *
     * @param recreate descarta o slot de espera antes de recria-lo. Usado depois
     *                 de um reset: se a remocao do mundo antigo falhou naquele
     *                 momento, esta e a segunda chance de apagar - sem isso o
     *                 proximo reset entregaria um mundo com o terreno ja alterado
     *                 pela run anterior.
     */
    public void prepareNextArena(boolean recreate) {
        Arena standby = slotState.standby();

        if (recreate && !lifecycle.destroyArena(standby)) {
            plugin.getLogger().severe("Nao consegui limpar o slot '" + standby.overworld()
                    + "'. O proximo reset pode entregar um mundo ja explorado.");
        }

        World world = lifecycle.loadOrCreateArena(standby);

        Bukkit.broadcast(messages.chat("pregen-started"));
        pregenerator.start(world, () -> {
            slotState.markStandbyReady(true);
            Bukkit.broadcast(messages.chat("pregen-done", "chunks", pregenerator.totalChunks()));
        });
    }

    /**
     * Garante que o mundo ativo tem um spawn habitavel. Necessario no boot: um
     * mundo recem-criado usa o spawn escolhido pelo proprio Minecraft, que nunca
     * passou pela nossa validacao.
     */
    public void validateActiveSpawn() {
        World active = Bukkit.getWorld(slotState.active().overworld());
        if (active == null) {
            return;
        }
        Location spawn = spawnFinder.find(active);
        active.setSpawnLocation(spawn);
        activeSpawnSnapshot = spawn;
    }

    /**
     * Onde quem entra no servidor deve nascer, ou {@code null} se o mundo ativo
     * ainda nao foi preparado. Seguro para leitura fora da thread principal.
     */
    public Location activeSpawnSnapshot() {
        Location snapshot = activeSpawnSnapshot;
        return snapshot == null ? null : snapshot.clone();
    }

    public ChunkPregenerator pregenerator() {
        return pregenerator;
    }

    public SlotState slotState() {
        return slotState;
    }
}

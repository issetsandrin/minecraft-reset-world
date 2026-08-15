package dev.versotech.worldreset;

import dev.versotech.worldreset.command.WorldResetCommand;
import dev.versotech.worldreset.config.Messages;
import dev.versotech.worldreset.config.ResetSettings;
import dev.versotech.worldreset.display.HealthDisplayService;
import dev.versotech.worldreset.integration.EssentialsHook;
import dev.versotech.worldreset.listener.DeathListener;
import dev.versotech.worldreset.listener.GuardListener;
import dev.versotech.worldreset.listener.PortalListener;
import dev.versotech.worldreset.player.DeathCounter;
import dev.versotech.worldreset.player.PlayerWiper;
import dev.versotech.worldreset.reset.ResetCoordinator;
import dev.versotech.worldreset.world.ChunkPregenerator;
import dev.versotech.worldreset.world.SafeSpawnFinder;
import dev.versotech.worldreset.world.SlotState;
import dev.versotech.worldreset.world.WorldPaths;
import dev.versotech.worldreset.world.VoidChunkGenerator;
import dev.versotech.worldreset.world.WorldLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recria o mundo inteiro sempre que qualquer jogador online morre.
 *
 * <p>O truque que torna isso viavel sem derrubar o servidor: o mundo principal
 * e um lobby vazio e permanente, e o mundo jogavel vive em dois slots que se
 * alternam. Enquanto um esta em uso, o outro ja foi criado e pre-gerado em
 * background - a morte so troca o ponteiro entre eles.
 */
public final class WorldResetPlugin extends JavaPlugin implements Listener {

    private final AtomicBoolean worldsReady = new AtomicBoolean(false);

    private ResetSettings settings;
    private Messages messages;
    private SlotState slotState;
    private WorldLifecycle lifecycle;
    private ChunkPregenerator pregenerator;
    private HealthDisplayService healthDisplay;
    private DeathCounter deathCounter;
    private ResetCoordinator coordinator;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    /**
     * Faz o lobby nascer vazio. E chamado pelo servidor durante a carga do mundo
     * principal, antes do onEnable, entao le a configuracao direto em vez de usar
     * os campos da classe.
     */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        String lobbyName = getConfig().getString("lobby.world-name", "lobby");
        if (worldName.equals(lobbyName)) {
            return new VoidChunkGenerator(getConfig().getInt("lobby.spawn-y", 100));
        }
        return null;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        buildComponents();
        registerListeners();

        var command = getCommand("worldreset");
        if (command != null) {
            var executor = new WorldResetCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // Nenhum mundo pode ser tocado aqui. Com load: STARTUP - obrigatorio para
        // que o servidor nos pergunte o gerador do lobby - o onEnable roda antes
        // de qualquer mundo existir, e createWorld() lanca excecao nessa fase.
        if (!Bukkit.getWorlds().isEmpty()) {
            // Plugin habilitado depois do boot (reload ou plugin manager): o
            // ServerLoadEvent nao vira mais, entao inicializamos no proximo tick.
            Bukkit.getScheduler().runTask(this, this::bootstrapWorlds);
        }
    }

    /** Disparado quando o servidor termina de carregar os mundos. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        bootstrapWorlds();
    }

    /** Prepara lobby, arena ativa e slot de espera. Idempotente. */
    private void bootstrapWorlds() {
        if (!worldsReady.compareAndSet(false, true)) {
            return;
        }

        lifecycle.purgeOrphanedFolders();
        lifecycle.ensureLobby();
        warnIfLobbyIsNotPrimary();

        lifecycle.loadOrCreateArena(slotState.active());
        coordinator.validateActiveSpawn();

        // Um tick depois: o standby so precisa existir a partir do primeiro
        // instante em que alguem possa morrer, e o boot ja esta ocupado demais.
        // Aqui recreate=false de proposito - um slot de espera que sobreviveu ao
        // boot anterior ja esta limpo e possivelmente ja pre-gerado.
        Bukkit.getScheduler().runTask(this, () -> coordinator.prepareNextArena(false));
        healthDisplay.start();

        // Contabiliza o tempo de sobrevivencia em fatias de um segundo, somando
        // apenas quando ha alguem conectado.
        Bukkit.getScheduler().runTaskTimer(this, () ->
                slotState.tickSurvival(!Bukkit.getOnlinePlayers().isEmpty(),
                        System.currentTimeMillis()), 20L, 20L);

        // O layout de pastas mudou no Minecraft 26.x e e o que decide onde os
        // mundos sao apagados e onde estao os dados dos jogadores offline.
        // Registrar o que foi resolvido evita depurar isso as cegas depois.
        getLogger().info("Layout de mundos: raiz=" + WorldPaths.serverWorldRoot()
                + " | dimensions=" + WorldPaths.dimensionsRoot());
        getLogger().info("Pronto. Mundo ativo: " + slotState.active().overworld()
                + " | lobby: " + settings.lobbyWorldName());
    }

    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(this, this);
        pluginManager.registerEvents(new DeathListener(coordinator, deathCounter), this);
        pluginManager.registerEvents(new GuardListener(this, settings, coordinator, lifecycle), this);
        pluginManager.registerEvents(healthDisplay, this);
        pluginManager.registerEvents(new PortalListener(), this);
    }

    @Override
    public void onDisable() {
        if (healthDisplay != null) {
            healthDisplay.stop();
        }
        if (pregenerator != null) {
            pregenerator.cancel();
        }
        if (slotState != null) {
            slotState.save();
        }
        if (deathCounter != null) {
            deathCounter.save();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void buildComponents() {
        this.settings = new ResetSettings(getConfig());
        this.messages = new Messages(getConfig());

        this.slotState = new SlotState(new File(getDataFolder(), "state.yml"), settings, getLogger());
        this.slotState.load();

        this.lifecycle = new WorldLifecycle(this, settings);

        this.deathCounter = new DeathCounter(new File(getDataFolder(), "deaths.yml"), getLogger());
        this.deathCounter.load();

        this.pregenerator = new ChunkPregenerator(this, settings);

        this.coordinator = new ResetCoordinator(
                this,
                settings,
                messages,
                slotState,
                lifecycle,
                pregenerator,
                new SafeSpawnFinder(settings),
                new PlayerWiper(this, settings),
                new EssentialsHook(this, settings));

        this.healthDisplay = new HealthDisplayService(this, settings, deathCounter, coordinator);
    }

    /**
     * O lobby deveria ser o {@code level-name} do server.properties. Se nao for,
     * o servidor mantem carregado um mundo vanilla que ninguem usa e o plugin
     * ainda funciona - mas e desperdicio de disco e de tempo de boot.
     */
    private void warnIfLobbyIsNotPrimary() {
        var primary = Bukkit.getWorlds().getFirst();
        if (!primary.getName().equals(settings.lobbyWorldName())) {
            getLogger().warning("O mundo principal do servidor e '" + primary.getName()
                    + "', mas o lobby configurado e '" + settings.lobbyWorldName() + "'.");
            getLogger().warning("Ajuste 'level-name=" + settings.lobbyWorldName()
                    + "' no server.properties para evitar carregar um mundo inutil a cada boot.");
        }
    }

    /** Recarrega config.yml e reconstroi os componentes, sem tocar nos mundos. */
    public void reloadEverything() {
        if (pregenerator != null) {
            pregenerator.cancel();
        }
        if (healthDisplay != null) {
            healthDisplay.stop();
        }
        // Cast explicito: a classe e Plugin e Listener ao mesmo tempo, e queremos
        // desregistrar tudo que o plugin registrou, nao so os handlers dele.
        HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
        reloadConfig();
        buildComponents();
        registerListeners();
        if (worldsReady.get()) {
            healthDisplay.start();
        }
    }

    public ResetSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public DeathCounter deathCounter() {
        return deathCounter;
    }

    public HealthDisplayService healthDisplay() {
        return healthDisplay;
    }

    public ResetCoordinator coordinator() {
        return coordinator;
    }
}

package dev.versotech.worldreset.world;

import dev.versotech.worldreset.config.ResetSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Criacao, carga e destruicao dos mundos.
 *
 * <p>A regra que dita todo o desenho da classe: o Bukkit nao deixa descarregar
 * um mundo que ainda tem jogador dentro, e nao da para apagar do disco um mundo
 * carregado. Por isso o caminho e sempre esvaziar, descarregar, renomear e so
 * entao apagar - o rename e sincrono e barato, e o apagar de verdade vai para
 * fora da thread principal.
 */
public final class WorldLifecycle {

    private final JavaPlugin plugin;
    private final ResetSettings settings;

    public WorldLifecycle(JavaPlugin plugin, ResetSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    // ---------------------------------------------------------------- lobby

    /**
     * Garante que o lobby existe e esta pronto para receber jogadores. Ele nunca
     * e apagado: e o unico chao firme que sobra enquanto a arena e trocada.
     */
    public World ensureLobby() {
        String name = settings.lobbyWorldName();
        World lobby = Bukkit.getWorld(name);

        if (lobby == null) {
            plugin.getLogger().info("Lobby '" + name + "' nao estava carregado; criando mundo vazio.");
            lobby = new WorldCreator(name)
                    .generator(new VoidChunkGenerator(settings.lobbySpawnY()))
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    .createWorld();
        }

        if (lobby == null) {
            throw new IllegalStateException("Nao consegui criar nem carregar o mundo de lobby '" + name + "'.");
        }

        lobby.setSpawnLocation(0, settings.lobbySpawnY(), 0);
        lobby.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        lobby.setGameRule(GameRules.ADVANCE_TIME, false);
        lobby.setGameRule(GameRules.ADVANCE_WEATHER, false);
        lobby.setGameRule(GameRules.SPAWN_MOBS, false);
        lobby.setGameRule(GameRules.FALL_DAMAGE, false);
        lobby.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        // Noite fixa: no void nao ha paisagem, e o ceu estrelado com as lanternas
        // da plataforma rende bem melhor do que um azul liso.
        lobby.setTime(18000L);
        lobby.setStorm(false);
        new LobbyDecorator(settings).build(lobby);
        return lobby;
    }

    public Location lobbySpawn() {
        World lobby = Bukkit.getWorld(settings.lobbyWorldName());
        if (lobby == null) {
            lobby = ensureLobby();
        }
        return new Location(lobby, 0.5, settings.lobbySpawnY(), 0.5);
    }

    // ---------------------------------------------------------------- arena

    /**
     * Carrega o slot, criando as pastas se ainda nao existirem. Quando a pasta ja
     * existe o WorldCreator ignora a seed informada e usa a gravada no level.dat,
     * que e exatamente o que queremos ao religar o servidor.
     */
    public World loadOrCreateArena(Arena arena) {
        long seed = ThreadLocalRandom.current().nextLong();

        World overworld = createWorld(arena.overworld(), World.Environment.NORMAL, seed);
        if (settings.createNether()) {
            createWorld(arena.nether(), World.Environment.NETHER, seed);
        }
        if (settings.createTheEnd()) {
            createWorld(arena.theEnd(), World.Environment.THE_END, seed);
        }
        return overworld;
    }

    private World createWorld(String name, World.Environment environment, long seed) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            applyArenaSettings(existing);
            return existing;
        }

        World world = new WorldCreator(name)
                .environment(environment)
                .type(settings.worldType())
                .seed(seed)
                .generateStructures(settings.generateStructures())
                .createWorld();

        if (world == null) {
            throw new IllegalStateException("Falha ao criar o mundo '" + name + "'.");
        }
        applyArenaSettings(world);
        return world;
    }

    private void applyArenaSettings(World world) {
        world.setDifficulty(settings.difficulty());
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, true);
        world.setAutoSave(true);
    }

    /**
     * Descarrega os tres mundos do slot e agenda a remocao das pastas.
     *
     * @return true se todos os mundos sairam da memoria; false significa que
     *         sobrou jogador dentro e nada foi apagado.
     */
    public boolean destroyArena(Arena arena) {
        List<File> folders = new ArrayList<>();

        for (String name : arena.allWorldNames()) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                // Nunca foi carregado; ainda assim a pasta pode existir no disco.
                File folder = resolveWorldFolder(name);
                if (folder != null && folder.isDirectory()) {
                    folders.add(folder);
                }
                continue;
            }

            if (!world.getPlayers().isEmpty()) {
                plugin.getLogger().severe("Mundo '" + name + "' ainda tem "
                        + world.getPlayers().size() + " jogador(es); abortando a remocao do slot.");
                return false;
            }

            File folder = world.getWorldFolder();
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().severe("Bukkit recusou descarregar o mundo '" + name + "'; slot mantido.");
                return false;
            }
            folders.add(folder);
        }

        for (File folder : folders) {
            scheduleDeletion(folder);
        }
        return true;
    }

    /**
     * Renomeia a pasta na hora e apaga fora da thread principal. O rename e o que
     * garante que uma remocao lenta nunca colida com um mundo novo de mesmo nome.
     */
    private void scheduleDeletion(File folder) {
        File target = folder;
        File renamed = new File(folder.getParentFile(), folder.getName() + ".deleting-" + System.nanoTime());
        if (folder.renameTo(renamed)) {
            target = renamed;
        } else {
            plugin.getLogger().warning("Nao consegui renomear " + folder.getName()
                    + " antes de apagar; removendo no lugar.");
        }

        enqueueDeletion(target);
    }

    private void enqueueDeletion(File target) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteRecursively(target.toPath());
                plugin.getLogger().info("Mundo removido do disco: " + target.getName());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao apagar " + target, e);
            }
        });
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Descobre a pasta de um mundo que nao esta carregado. Com o mundo carregado
     * sempre preferimos {@link World#getWorldFolder()}, que e a fonte da verdade.
     */
    private File resolveWorldFolder(String name) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return loaded.getWorldFolder();
        }

        File dimensions = WorldPaths.dimensionsRoot();
        if (dimensions != null) {
            File[] namespaces = dimensions.listFiles(File::isDirectory);
            if (namespaces != null) {
                for (File namespace : namespaces) {
                    File candidate = new File(namespace, name);
                    if (candidate.isDirectory()) {
                        return candidate;
                    }
                }
            }
        }

        File legacy = new File(Bukkit.getWorldContainer(), name);
        return legacy.isDirectory() ? legacy : null;
    }

    /** Remove pastas .deleting-* que sobraram de um desligamento no meio do reset. */
    public void purgeOrphanedFolders() {
        purgeIn(Bukkit.getWorldContainer());

        File dimensions = WorldPaths.dimensionsRoot();
        if (dimensions == null) {
            return;
        }
        File[] namespaces = dimensions.listFiles(File::isDirectory);
        if (namespaces != null) {
            for (File namespace : namespaces) {
                purgeIn(namespace);
            }
        }
    }

    private void purgeIn(File directory) {
        File[] leftovers = directory.listFiles((dir, name) -> name.contains(".deleting-"));
        if (leftovers == null) {
            return;
        }
        for (File leftover : leftovers) {
            enqueueDeletion(leftover);
        }
    }
}

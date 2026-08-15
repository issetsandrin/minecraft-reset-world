package dev.versotech.worldreset.integration;

import dev.versotech.worldreset.config.ResetSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Integracao opcional com o EssentialsX.
 *
 * <p>O EssentialsX guarda posicoes com o NOME do mundo. Como a arena alterna
 * entre dois nomes a cada reset, tudo que ele salvou aponta para um mundo que
 * acabou de ser apagado: {@code /home} passa a falhar, {@code /back} idem, e o
 * {@code /spawn} manda o jogador para lugar nenhum. Esta classe limpa apenas
 * esses campos - saldo, nickname, kits e ignores continuam intactos entre runs.
 *
 * <p>Nao ha dependencia de compilacao com o EssentialsX: mexemos nos YAML dele e
 * pedimos um reload, o que funciona com qualquer versao recente.
 */
public final class EssentialsHook {

    private static final String PLUGIN_NAME = "Essentials";

    /** Campos do userdata que carregam nome de mundo. */
    private static final String[] WORLD_BOUND_KEYS = {"homes", "lastlocation", "logoutlocation"};

    private final JavaPlugin plugin;
    private final ResetSettings settings;

    public EssentialsHook(JavaPlugin plugin, ResetSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean isActive() {
        String mode = settings.essentialsMode();
        if ("false".equalsIgnoreCase(mode) || "off".equalsIgnoreCase(mode)) {
            return false;
        }
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
    }

    private File dataFolder() {
        var essentials = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return essentials == null ? null : essentials.getDataFolder();
    }

    /**
     * Executa a limpeza. A parte de disco roda fora da thread principal; o reload
     * volta para a thread principal porque despacha um comando.
     */
    public void applyReset(Location newSpawn) {
        if (!isActive()) {
            return;
        }
        File dataFolder = dataFolder();
        if (dataFolder == null || !dataFolder.isDirectory()) {
            return;
        }

        String worldName = newSpawn.getWorld().getName();
        double x = newSpawn.getX();
        double y = newSpawn.getY();
        double z = newSpawn.getZ();
        float yaw = newSpawn.getYaw();
        float pitch = newSpawn.getPitch();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int cleaned = 0;
            if (settings.essentialsWipeWorldBound()) {
                cleaned = wipeUserdata(dataFolder);
            }
            boolean spawnRewritten = settings.essentialsRewriteSpawn()
                    && rewriteSpawn(dataFolder, worldName, x, y, z, yaw, pitch);

            int finalCleaned = cleaned;
            boolean touchedSomething = finalCleaned > 0 || spawnRewritten;

            Bukkit.getScheduler().runTask(plugin, () -> {
                // O reload so faz sentido se algum arquivo mudou. Sem isso o
                // EssentialsX reescreveria os arquivos com o cache que ainda tem
                // em memoria e desfaria o que acabamos de fazer.
                if (touchedSomething && settings.essentialsReloadAfterWipe()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials:ess reload");
                }
                plugin.getLogger().info("EssentialsX: " + finalCleaned + " userdata limpo(s); spawn.yml "
                        + (spawnRewritten ? "apontado para '" + worldName + "'" : "sem alteracao"
                        + " (arquivo ainda nao existe ou reescrita desligada)") + ".");
            });
        });
    }

    private int wipeUserdata(File dataFolder) {
        File userdata = new File(dataFolder, "userdata");
        File[] files = userdata.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        int cleaned = 0;
        for (File file : files) {
            try {
                var yaml = YamlConfiguration.loadConfiguration(file);
                boolean changed = false;
                for (String key : WORLD_BOUND_KEYS) {
                    if (yaml.contains(key)) {
                        yaml.set(key, null);
                        changed = true;
                    }
                }
                if (changed) {
                    yaml.save(file);
                    cleaned++;
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Falha ao limpar userdata do EssentialsX: " + file, e);
            }
        }
        return cleaned;
    }

    /**
     * @return true se o arquivo existia e foi reescrito. O EssentialsXSpawn so
     *         cria o spawn.yml depois do primeiro {@code /setspawn}; antes disso
     *         nao ha nada apontando para o mundo antigo, e nao cabe a nos
     *         inventar o arquivo dele.
     */
    private boolean rewriteSpawn(File dataFolder, String worldName,
                                 double x, double y, double z, float yaw, float pitch) {
        File spawnFile = new File(dataFolder, "spawn.yml");
        if (!spawnFile.isFile()) {
            return false;
        }

        try {
            var yaml = YamlConfiguration.loadConfiguration(spawnFile);
            var spawns = yaml.getConfigurationSection("spawns");
            if (spawns == null) {
                return false;
            }
            // Reescreve todos os grupos, nao so o "default": um servidor com
            // permissions pode ter um spawn por grupo, e todos apontam para o
            // mundo que acabou de ser apagado.
            for (String group : spawns.getKeys(false)) {
                spawns.set(group + ".world", worldName);
                spawns.set(group + ".x", x);
                spawns.set(group + ".y", y);
                spawns.set(group + ".z", z);
                spawns.set(group + ".yaw", yaw);
                spawns.set(group + ".pitch", pitch);
            }
            yaml.save(spawnFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao reescrever o spawn.yml do EssentialsX", e);
            return false;
        }
    }
}

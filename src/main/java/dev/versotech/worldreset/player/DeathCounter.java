package dev.versotech.worldreset.player;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quantas vezes cada jogador ja morreu - ou seja, quantas vezes cada um ja
 * apagou o mundo de todo mundo.
 *
 * <p>Nao da para usar {@code Statistic.DEATHS} do proprio Minecraft: o reset
 * zera as estatisticas de quem esta online e apaga os arquivos de quem esta
 * offline, entao o numero voltaria a zero exatamente quando fosse interessante.
 * Por isso a contagem vive num arquivo do plugin, fora do alcance do wipe.
 *
 * <p>O nome e guardado junto do identificador so para exibicao; a chave e sempre
 * o UUID, que nao muda quando alguem troca de nick.
 */
public final class DeathCounter {

    private final File file;
    private final Logger logger;

    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public DeathCounter(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        deaths.clear();
        names.clear();
        if (!file.exists()) {
            return;
        }

        var yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String rawId : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            deaths.put(id, section.getInt(rawId + ".deaths", 0));
            String name = section.getString(rawId + ".name");
            if (name != null) {
                names.put(id, name);
            }
        }
    }

    public void save() {
        var yaml = new YamlConfiguration();
        for (var entry : deaths.entrySet()) {
            String id = entry.getKey().toString();
            yaml.set("players." + id + ".deaths", entry.getValue());
            String name = names.get(entry.getKey());
            if (name != null) {
                yaml.set("players." + id + ".name", name);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Nao consegui gravar a contagem de mortes em " + file, e);
        }
    }

    /** Registra mais uma morte e grava na hora: e um evento raro e caro de perder. */
    public int increment(Player player) {
        UUID id = player.getUniqueId();
        int total = deaths.merge(id, 1, Integer::sum);
        names.put(id, player.getName());
        save();
        return total;
    }

    public int count(Player player) {
        return deaths.getOrDefault(player.getUniqueId(), 0);
    }

    /** Total de mortes de todos, util para conferir com o numero de resets. */
    public int total() {
        return deaths.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void reset() {
        deaths.clear();
        names.clear();
        save();
    }
}

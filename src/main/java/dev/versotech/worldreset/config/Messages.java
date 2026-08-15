package dev.versotech.worldreset.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Mensagens do plugin em MiniMessage. Os placeholders sao substituidos como
 * texto puro antes da desserializacao, entao um valor dinamico nunca consegue
 * injetar formatacao.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> raw = new HashMap<>();
    private final String prefix;

    public Messages(FileConfiguration config) {
        var section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                raw.put(key, section.getString(key, ""));
            }
        }
        this.prefix = raw.getOrDefault("prefix", "");
    }

    /** Mensagem com prefixo, pronta para o chat. */
    public Component chat(String key, Object... placeholders) {
        return MINI.deserialize(apply(prefix + template(key), placeholders));
    }

    /** Mensagem sem prefixo, para titulos e action bar. */
    public Component plain(String key, Object... placeholders) {
        return MINI.deserialize(apply(template(key), placeholders));
    }

    private String template(String key) {
        return raw.getOrDefault(key, "<red>mensagem ausente: " + key + "</red>");
    }

    /**
     * Substitui pares {@code <chave>} recebidos achatados: nome1, valor1, nome2,
     * valor2...
     */
    private static String apply(String template, Object... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders devem vir em pares nome/valor");
        }
        String result = template;
        for (int i = 0; i < placeholders.length; i += 2) {
            result = result.replace("<" + placeholders[i] + ">", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }
}

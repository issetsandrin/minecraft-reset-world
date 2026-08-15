package dev.versotech.worldreset.player;

import dev.versotech.worldreset.config.ResetSettings;
import dev.versotech.worldreset.world.WorldPaths;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Zera o progresso dos jogadores.
 *
 * <p>Sao dois caminhos distintos e ambos necessarios: quem esta online e
 * limpo pela API, porque o servidor tem o estado dele em memoria e sobrescreveria
 * qualquer edicao em arquivo; quem esta offline so pode ser limpo apagando o
 * .dat, que e o unico lugar onde o progresso dele existe.
 */
public final class PlayerWiper {

    private final JavaPlugin plugin;
    private final ResetSettings settings;

    public PlayerWiper(JavaPlugin plugin, ResetSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    // --------------------------------------------------------------- online

    public void wipe(Player player) {
        if (settings.wipeInventory()) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            player.getInventory().setHeldItemSlot(0);
        }
        if (settings.wipeEnderChest()) {
            player.getEnderChest().clear();
        }
        if (settings.wipeExperience()) {
            player.setLevel(0);
            player.setExp(0.0f);
            player.setTotalExperience(0);
        }
        if (settings.wipePotionEffects()) {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
        }
        if (settings.wipeAdvancements()) {
            revokeAdvancements(player);
        }
        if (settings.wipeStatistics()) {
            resetStatistics(player);
        }
        if (settings.wipeRecipes()) {
            player.undiscoverRecipes(new HashSet<>(player.getDiscoveredRecipes()));
        }

        restoreVitals(player);
    }

    /** Volta o jogador ao estado de quem acabou de entrar pela primeira vez. */
    private void restoreVitals(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0f);
        player.setRemainingAir(player.getMaximumAir());
        player.setVelocity(new Vector());
        player.setGameMode(GameMode.SURVIVAL);
        player.setRespawnLocation(null, true);
        player.closeInventory();
    }

    private void revokeAdvancements(Player player) {
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            var progress = player.getAdvancementProgress(iterator.next());
            for (String criteria : new ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criteria);
            }
        }
    }

    /**
     * Estatisticas com subtipo precisam ser zeradas por material/entidade, uma a
     * uma. Combinacoes invalidas lancam excecao e sao simplesmente ignoradas.
     */
    private void resetStatistics(Player player) {
        for (Statistic statistic : Statistic.values()) {
            switch (statistic.getType()) {
                case UNTYPED -> safeSet(() -> player.setStatistic(statistic, 0));
                case ITEM, BLOCK -> {
                    for (Material material : Material.values()) {
                        if (material.isLegacy()) {
                            continue;
                        }
                        safeSet(() -> player.setStatistic(statistic, material, 0));
                    }
                }
                case ENTITY -> {
                    for (EntityType entityType : EntityType.values()) {
                        safeSet(() -> player.setStatistic(statistic, entityType, 0));
                    }
                }
            }
        }
    }

    private void safeSet(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Combinacao estatistica/material invalida - esperado.
        }
    }

    // -------------------------------------------------------------- offline

    /**
     * Apaga os arquivos de quem nao estava conectado. Sem isso, o jogador que
     * perdeu a run dormindo volta com o inventario da run anterior.
     *
     * <p>O Minecraft 26.x reorganizou esses arquivos: o que era
     * {@code playerdata/}, {@code stats/} e {@code advancements/} soltos na raiz
     * do mundo virou a arvore {@code players/}. Percorremos as duas formas, e de
     * modo recursivo, para nao depender do layout exato de uma versao.
     *
     * @return quantidade de arquivos removidos
     */
    public int wipeOfflinePlayers() {
        if (!settings.wipeOfflinePlayers()) {
            return 0;
        }

        File mainWorldFolder = WorldPaths.serverWorldRoot();
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }

        int removed = 0;
        for (String directory : PLAYER_DATA_DIRECTORIES) {
            removed += purgeRecursively(new File(mainWorldFolder, directory), online);
        }
        if (removed == 0) {
            plugin.getLogger().fine("Nenhum arquivo de jogador offline encontrado em "
                    + mainWorldFolder + " - layout de mundo inesperado?");
        }
        return removed;
    }

    /** Raizes possiveis, cobrindo o layout novo e o antigo. */
    private static final String[] PLAYER_DATA_DIRECTORIES =
            {"players", "playerdata", "stats", "advancements"};

    private static final String[] PLAYER_FILE_EXTENSIONS = {".dat", ".dat_old", ".json"};

    private int purgeRecursively(File directory, Set<UUID> keep) {
        File[] entries = directory.listFiles();
        if (entries == null) {
            return 0;
        }

        int removed = 0;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                removed += purgeRecursively(entry, keep);
                continue;
            }
            removed += purgeFile(entry, keep);
        }
        return removed;
    }

    /**
     * Remove o arquivo se o nome for o UUID de um jogador que esta offline.
     *
     * <p>Exigir que o nome seja um UUID valido e o que impede de apagar por
     * engano algo que nao seja dado de jogador.
     *
     * @return 1 se apagou, 0 caso contrario
     */
    private int purgeFile(File file, Set<UUID> keep) {
        String name = file.getName();
        String base = null;
        for (String extension : PLAYER_FILE_EXTENSIONS) {
            if (name.endsWith(extension)) {
                base = name.substring(0, name.length() - extension.length());
                break;
            }
        }
        if (base == null) {
            return 0;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(base);
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
        if (keep.contains(uuid)) {
            return 0;
        }

        if (file.delete()) {
            return 1;
        }
        plugin.getLogger().log(Level.WARNING, "Nao consegui apagar " + file);
        return 0;
    }
}

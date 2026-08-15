package dev.versotech.worldreset.display;

import dev.versotech.worldreset.config.ResetSettings;
import dev.versotech.worldreset.player.DeathCounter;
import dev.versotech.worldreset.reset.ResetCoordinator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Painel lateral com a vida de todos os jogadores, atualizado continuamente.
 *
 * <p>Faz sentido especial neste servidor: como a morte de qualquer um apaga o
 * mundo de todos, saber que alguem esta com duas vidas de vida deixou de ser
 * curiosidade e virou informacao tatica.
 *
 * <p>Cada jogador recebe um painel proprio, porque cada um ve os outros e nao a
 * si mesmo - a propria vida ja esta na barra da tela.
 */
public final class HealthDisplayService implements Listener {

    /** Vida cheia em pontos; dez coracoes de dois pontos cada. */
    private static final double FULL_HEALTH = 20.0;
    private static final int HEART_COUNT = 10;

    private static final char HEART_FULL = '❤';
    private static final char HEART_EMPTY = '♡';

    /** Abaixo disto o jogador entra na secao de perigo. */
    private static final double DANGER_RATIO = 0.30;
    private static final double SAFE_RATIO = 0.70;

    private final JavaPlugin plugin;
    private final ResetSettings settings;
    private final DeathCounter deathCounter;
    private final ResetCoordinator coordinator;
    private final Map<UUID, HealthBoard> boards = new HashMap<>();

    private BukkitTask task;

    public HealthDisplayService(JavaPlugin plugin,
                                ResetSettings settings,
                                DeathCounter deathCounter,
                                ResetCoordinator coordinator) {
        this.plugin = plugin;
        this.settings = settings;
        this.deathCounter = deathCounter;
        this.coordinator = coordinator;
    }

    public void start() {
        stop();
        if (!settings.healthDisplayEnabled()) {
            return;
        }
        long period = settings.healthDisplayUpdateTicks();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            forget(player);
        }
        boards.clear();
    }

    /**
     * Descarta o painel de quem saiu. Sem isto o mapa cresceria a cada conexao
     * e nunca encolheria.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    /** Devolve o jogador ao placar principal do servidor. */
    public void forget(Player player) {
        boards.remove(player.getUniqueId());
        var manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void refreshAll() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : online) {
            render(viewer, online);
        }
    }

    private void render(Player viewer, List<Player> online) {
        HealthBoard board = boards.computeIfAbsent(viewer.getUniqueId(),
                id -> new HealthBoard(title()));
        if (!board.scoreboard().equals(viewer.getScoreboard())) {
            viewer.setScoreboard(board.scoreboard());
        }
        board.title(title());

        List<Player> others = new ArrayList<>(online);
        others.remove(viewer);

        if (others.isEmpty()) {
            board.render(List.of(Component.text("ninguem mais online", NamedTextColor.DARK_GRAY)));
            return;
        }

        sort(others);
        board.render(buildLines(others));
    }

    /**
     * Cabecalho com o numero do ciclo atual. Cada reset e uma tentativa nova, e
     * ver "RESET #7" no canto deixa claro para todos quantas ja foram perdidas.
     */
    private Component title() {
        return Component.text(HEART_FULL + " VIDAS", NamedTextColor.RED)
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("RESET #" + (coordinator.slotState().resetCount() + 1),
                        NamedTextColor.GOLD));
    }

    /**
     * Quem esta em perigo sobe para o topo. Numa lista que so cabe alguns nomes,
     * o que interessa e ver primeiro quem esta prestes a apagar o mundo.
     */
    private void sort(List<Player> players) {
        Comparator<Player> byName = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        if (!settings.healthDisplayHighlightDanger()) {
            players.sort(byName);
            return;
        }
        players.sort(Comparator.comparingDouble((Player p) -> healthRatio(p)).thenComparing(byName));
    }

    private List<Component> buildLines(List<Player> players) {
        boolean showExtras = settings.healthDisplayShowHungerArmor();
        int linesPerPlayer = showExtras ? 2 : 1;
        int capacity = HealthBoard.MAX_LINES / linesPerPlayer;

        List<Component> lines = new ArrayList<>();
        int shown = Math.min(players.size(), capacity);

        for (int index = 0; index < shown; index++) {
            Player target = players.get(index);
            double ratio = healthRatio(target);
            TextColor color = colorFor(ratio);

            if (showExtras) {
                lines.add(Component.text(target.getName(), color).append(deathTag(target)));
                lines.add(Component.text(" ")
                        .append(hearts(ratio, color))
                        .append(extras(target)));
            } else {
                lines.add(Component.text(target.getName() + " ", color)
                        .append(hearts(ratio, color))
                        .append(deathTag(target)));
            }
        }

        // Nunca omitir em silencio: se alguem nao coube, isso precisa aparecer.
        int hidden = players.size() - shown;
        if (hidden > 0 && lines.size() < HealthBoard.MAX_LINES) {
            lines.add(Component.text("+ " + hidden + " sem espaco", NamedTextColor.DARK_GRAY));
        }
        return lines;
    }

    /**
     * Quantas vezes este jogador ja apagou o mundo. Fica discreto de proposito:
     * e um historico, nao um alarme como a vida baixa.
     */
    private Component deathTag(Player player) {
        if (!settings.healthDisplayShowDeaths()) {
            return Component.empty();
        }
        int total = deathCounter.count(player);
        if (total <= 0) {
            return Component.empty();
        }
        return Component.text("  " + settings.iconDeaths() + " ", NamedTextColor.GRAY)
                .append(Component.text(total, NamedTextColor.DARK_GRAY));
    }

    static Component hearts(double ratio, TextColor color) {
        int filled = (int) Math.ceil(ratio * HEART_COUNT);
        filled = Math.max(0, Math.min(HEART_COUNT, filled));

        StringBuilder full = new StringBuilder();
        full.append(String.valueOf(HEART_FULL).repeat(filled));

        StringBuilder empty = new StringBuilder();
        empty.append(String.valueOf(HEART_EMPTY).repeat(HEART_COUNT - filled));

        return Component.text(full.toString(), color)
                .append(Component.text(empty.toString(), NamedTextColor.DARK_GRAY));
    }

    /**
     * Fome e armadura no formato "icone valor". O icone fica cinza para nao
     * competir com os coracoes, que sao a informacao principal da linha.
     */
    private Component extras(Player player) {
        int armor = armorOf(player);

        Component result = Component.text("  " + settings.iconHunger() + " ", NamedTextColor.GRAY)
                .append(Component.text(player.getFoodLevel(), NamedTextColor.GOLD));

        if (armor > 0) {
            result = result
                    .append(Component.text("  " + settings.iconArmor() + " ", NamedTextColor.GRAY))
                    .append(Component.text(armor, NamedTextColor.AQUA));
        }
        return result;
    }

    private int armorOf(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ARMOR);
        return attribute == null ? 0 : (int) Math.round(attribute.getValue());
    }

    private double healthRatio(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? FULL_HEALTH : maxHealth.getValue();
        if (max <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, player.getHealth() / max));
    }

    static TextColor colorFor(double ratio) {
        if (ratio > SAFE_RATIO) {
            return NamedTextColor.GREEN;
        }
        if (ratio > DANGER_RATIO) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }
}

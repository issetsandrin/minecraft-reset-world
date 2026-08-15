package dev.versotech.worldreset.display;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * O painel lateral de um jogador.
 *
 * <p>As linhas nao sao recriadas a cada atualizacao. Cada linha e uma entrada
 * fixa e invisivel - um codigo de cor, que nao renderiza nada - e o texto vive
 * no prefixo de um time. Trocar o prefixo atualiza a linha sem remover e
 * readicionar a entrada, que e o que provoca o piscar caracteristico de
 * scoreboards mal feitos.
 */
final class HealthBoard {

    /** O cliente so desenha 15 linhas na barra lateral. */
    static final int MAX_LINES = 15;

    private static final String INVISIBLE_CHARS = "0123456789abcdef";

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Team> teams = new ArrayList<>(MAX_LINES);
    private final List<String> entries = new ArrayList<>(MAX_LINES);

    private int visibleLines = 0;

    HealthBoard(Component title) {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("wr_health", Criteria.DUMMY, title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        // Sem isto o cliente desenha o numero da pontuacao em vermelho na
        // direita de cada linha, que nao tem sentido nenhum aqui.
        this.objective.numberFormat(NumberFormat.blank());

        for (int index = 0; index < MAX_LINES; index++) {
            String entry = "§" + INVISIBLE_CHARS.charAt(index) + "§r";
            Team team = scoreboard.registerNewTeam("wr_line_" + index);
            team.addEntry(entry);
            entries.add(entry);
            teams.add(team);
        }
    }

    Scoreboard scoreboard() {
        return scoreboard;
    }

    void title(Component title) {
        objective.displayName(title);
    }

    /** Redesenha o painel. Linhas sobrando sao retiradas da pontuacao. */
    void render(List<Component> lines) {
        int count = Math.min(lines.size(), MAX_LINES);

        for (int index = 0; index < count; index++) {
            teams.get(index).prefix(lines.get(index));
            // Pontuacao decrescente: a barra lateral ordena do maior para o
            // menor, entao a primeira linha da lista precisa do maior valor.
            objective.getScore(entries.get(index)).setScore(MAX_LINES - index);
        }

        for (int index = count; index < visibleLines; index++) {
            scoreboard.resetScores(entries.get(index));
        }
        visibleLines = count;
    }
}

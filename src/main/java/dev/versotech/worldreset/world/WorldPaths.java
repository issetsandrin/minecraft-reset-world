package dev.versotech.worldreset.world;

import org.bukkit.Bukkit;

import java.io.File;

/**
 * Resolve onde o servidor guarda mundos e dados de jogador.
 *
 * <p>O layout mudou no Minecraft 26.x: cada mundo era uma pasta irma no
 * diretorio do servidor e passou a viver dentro da pasta do mundo principal, em
 * {@code dimensions/<namespace>/<nome>}. Os dados de jogador tambem mudaram -
 * {@code playerdata/}, {@code stats/} e {@code advancements/} viraram a arvore
 * {@code players/}.
 *
 * <p>Em vez de gravar o layout de uma versao no codigo, a raiz e descoberta
 * subindo a partir da pasta do mundo principal ate encontrar o {@code level.dat}.
 * Isso vale nos dois layouts e nao quebra no proximo.
 */
public final class WorldPaths {

    private static final String DIMENSIONS = "dimensions";
    private static final int MAX_DEPTH = 6;

    private WorldPaths() {
    }

    /**
     * Raiz do mundo do servidor: a pasta que contem {@code level.dat},
     * {@code players/} e {@code dimensions/}.
     *
     * <p>No layout novo {@code getWorldFolder()} do mundo principal devolve a
     * pasta da <em>dimensao</em> ({@code <raiz>/dimensions/minecraft/overworld}),
     * nao a raiz. A raiz e o ancestral logo acima de {@code dimensions/}.
     *
     * <p>Procurar por {@code level.dat} nao serve como criterio primario: num
     * servidor recem-iniciado ele ainda nao foi gravado, e a busca falharia
     * silenciosamente logo no boot - que e justamente quando precisamos dela.
     */
    public static File serverWorldRoot() {
        File worldFolder = Bukkit.getWorlds().getFirst().getWorldFolder();

        File current = worldFolder;
        for (int depth = 0; depth < MAX_DEPTH && current != null; depth++) {
            if (DIMENSIONS.equals(current.getName()) && current.getParentFile() != null) {
                return current.getParentFile();
            }
            current = current.getParentFile();
        }

        // Layout legado: cada mundo e uma pasta autocontida no diretorio do
        // servidor, e ela propria e a raiz.
        return worldFolder;
    }

    /** Raiz das dimensoes no layout novo, ou {@code null} num servidor legado. */
    public static File dimensionsRoot() {
        File candidate = new File(serverWorldRoot(), DIMENSIONS);
        return candidate.isDirectory() ? candidate : null;
    }
}

package dev.versotech.worldreset.world;

import java.util.List;

/**
 * Um conjunto de tres mundos que forma um slot jogavel.
 *
 * <p>Os sufixos {@code _nether} e {@code _the_end} nao sao decorativos: e por
 * eles que o CraftBukkit liga as dimensoes na hora de atravessar um portal.
 * Renomear quebra o portal do nether silenciosamente.
 */
public record Arena(String overworld) {

    public String nether() {
        return overworld + "_nether";
    }

    public String theEnd() {
        return overworld + "_the_end";
    }

    /** Todos os nomes do slot, do overworld para as dimensoes derivadas. */
    public List<String> allWorldNames() {
        return List.of(overworld, nether(), theEnd());
    }
}

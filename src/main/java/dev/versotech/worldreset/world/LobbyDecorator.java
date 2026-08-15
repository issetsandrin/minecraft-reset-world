package dev.versotech.worldreset.world;

import dev.versotech.worldreset.config.ResetSettings;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Constroi a plataforma do lobby.
 *
 * <p>O lobby fica visivel por poucos segundos a cada reset, mas e o unico
 * momento em que todos os jogadores estao no mesmo lugar, vendo o mundo ser
 * destruido. Vale mais do que um quadrado de blocos soltos no vazio.
 *
 * <p>A construcao e deterministica e refeita a cada boot: e mais simples de
 * manter do que detectar o que ja existe, e custa poucos milissegundos.
 */
public final class LobbyDecorator {

    /** Altura livre acima do piso, onde o jogador anda. */
    private static final int HEADROOM = 5;

    /** Altura da parede invisivel que impede cair da borda. */
    private static final int WALL_HEIGHT = 6;

    private final ResetSettings settings;

    public LobbyDecorator(ResetSettings settings) {
        this.settings = settings;
    }

    public void build(World lobby) {
        int y = settings.lobbySpawnY();
        int radius = Math.max(3, settings.lobbyPlatformRadius());

        clearArea(lobby, y, radius);
        buildFoundation(lobby, y, radius);
        buildFloor(lobby, y, radius);
        buildPillars(lobby, y, radius);
        buildCentrepiece(lobby, y);
        buildInvisibleWall(lobby, y, radius);
    }

    /** Distancia ao centro no plano, para dar forma circular em vez de quadrada. */
    private static double distance(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    private void clearArea(World lobby, int y, int radius) {
        int limit = radius + 2;
        for (int x = -limit; x <= limit; x++) {
            for (int z = -limit; z <= limit; z++) {
                for (int dy = -3; dy <= HEADROOM + 2; dy++) {
                    lobby.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /** Camada inferior, que da espessura a plataforma quando vista de longe. */
    private void buildFoundation(World lobby, int y, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = distance(x, z);
                if (distance > radius - 0.5) {
                    continue;
                }
                lobby.getBlockAt(x, y - 2, z).setType(Material.DEEPSLATE_BRICKS, false);
                if (distance < radius - 2.5) {
                    lobby.getBlockAt(x, y - 3, z).setType(Material.POLISHED_DEEPSLATE, false);
                }
            }
        }
    }

    /**
     * Piso em aneis concentricos, com a borda iluminada. Sem uma fonte de luz o
     * lobby fica sombrio: nao ha sol util no void e o ceu esta fixo na noite.
     */
    private void buildFloor(World lobby, int y, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = distance(x, z);
                if (distance > radius) {
                    continue;
                }

                Material material;
                if (distance > radius - 1) {
                    // Borda: lanternas espacadas, quartzo entre elas.
                    material = (Math.floorMod(x + z, 3) == 0)
                            ? Material.SEA_LANTERN
                            : Material.SMOOTH_QUARTZ;
                } else {
                    material = (((int) distance) % 2 == 0)
                            ? Material.QUARTZ_BLOCK
                            : Material.SMOOTH_QUARTZ;
                }
                lobby.getBlockAt(x, y - 1, z).setType(material, false);
            }
        }
    }

    /**
     * Quatro pilares nas diagonais, cada um encimado por uma lanterna.
     *
     * <p>O deslocamento e dividido pela raiz de dois porque a distancia ao centro
     * de um ponto diagonal e maior que a coordenada: usar {@code radius - 2} em
     * cada eixo poe o pilar a {@code (radius - 2) * 1,41} do centro, fora do
     * piso circular - e todos eles eram descartados sem construir nada.
     */
    private void buildPillars(World lobby, int y, int radius) {
        int offset = Math.max(2, (int) Math.round((radius - 2) / Math.sqrt(2)));
        int[][] corners = {{offset, offset}, {offset, -offset}, {-offset, offset}, {-offset, -offset}};

        for (int[] corner : corners) {
            int x = corner[0];
            int z = corner[1];
            if (distance(x, z) > radius - 0.5) {
                continue;
            }
            for (int dy = 0; dy < HEADROOM - 1; dy++) {
                lobby.getBlockAt(x, y + dy, z).setType(Material.QUARTZ_PILLAR, false);
            }
            lobby.getBlockAt(x, y + HEADROOM - 1, z).setType(Material.SEA_LANTERN, false);
        }
    }

    /**
     * Marca o centro, que e exatamente onde os jogadores aparecem. O bloco de
     * pouso e solido e o entorno diferencia o ponto de chegada do resto do piso.
     */
    private void buildCentrepiece(World lobby, int y) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                lobby.getBlockAt(x, y - 1, z).setType(Material.POLISHED_DEEPSLATE, false);
            }
        }
        lobby.getBlockAt(0, y - 1, 0).setType(Material.LODESTONE, false);
    }

    /**
     * Parede invisivel na borda. E o que impede alguem de cair no void durante o
     * reset - uma morte ali dispararia outro reset em cadeia.
     */
    private void buildInvisibleWall(World lobby, int y, int radius) {
        int limit = radius + 1;
        for (int x = -limit; x <= limit; x++) {
            for (int z = -limit; z <= limit; z++) {
                double distance = distance(x, z);
                if (distance <= radius || distance > radius + 1.5) {
                    continue;
                }
                for (int dy = -1; dy < WALL_HEIGHT; dy++) {
                    lobby.getBlockAt(x, y + dy, z).setType(Material.BARRIER, false);
                }
            }
        }
    }
}

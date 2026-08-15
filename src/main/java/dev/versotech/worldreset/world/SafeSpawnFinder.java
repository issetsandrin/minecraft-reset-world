package dev.versotech.worldreset.world;

import dev.versotech.worldreset.config.ResetSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

/**
 * Escolhe um ponto de nascimento onde o jogador nao morra no primeiro segundo.
 *
 * <p>Isso e o que separa a mecanica de um loop infinito: se o spawn do mundo
 * novo cair sobre lava, cacto ou um penhasco, a morte seguinte dispararia outro
 * reset, e assim por diante.
 */
public final class SafeSpawnFinder {

    /** Blocos que matam, empurram ou impedem ficar em pe sobre eles. */
    private static final Set<Material> UNSAFE_GROUND = EnumSet.of(
            Material.LAVA,
            Material.WATER,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.MAGMA_BLOCK,
            Material.CACTUS,
            Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.POINTED_DRIPSTONE,
            Material.SCAFFOLDING,
            Material.BIG_DRIPLEAF,
            Material.ICE,
            Material.PACKED_ICE,
            Material.BLUE_ICE,
            Material.FROSTED_ICE
    );

    private final ResetSettings settings;

    public SafeSpawnFinder(ResetSettings settings) {
        this.settings = settings;
    }

    /**
     * Procura em aneis crescentes ao redor do spawn do mundo. O raio maximo fica
     * dentro da area pre-gerada de proposito: sair dela obrigaria o servidor a
     * gerar terreno na thread principal no pior momento possivel.
     */
    public Location find(World world) {
        Location origin = world.getSpawnLocation();
        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();
        int step = settings.spawnSearchStep();
        int maxRadius = settings.spawnSearchMaxRadius();

        Location candidate = evaluate(world, originX, originZ);
        if (candidate != null) {
            return candidate;
        }

        for (int radius = step; radius <= maxRadius; radius += step) {
            for (int offset = -radius; offset <= radius; offset += step) {
                Location[] ring = {
                        evaluate(world, originX + offset, originZ - radius),
                        evaluate(world, originX + offset, originZ + radius),
                        evaluate(world, originX - radius, originZ + offset),
                        evaluate(world, originX + radius, originZ + offset)
                };
                for (Location location : ring) {
                    if (location != null) {
                        return location;
                    }
                }
            }
        }

        if (settings.spawnSearchBuildPlatform()) {
            return buildPlatform(world, originX, originZ);
        }
        return origin;
    }

    /** Retorna a posicao se ela for habitavel, ou null. */
    private Location evaluate(World world, int x, int z) {
        Block ground = world.getHighestBlockAt(x, z);

        if (ground.getY() <= world.getMinHeight() + 1) {
            return null;
        }
        if (!ground.getType().isSolid() || UNSAFE_GROUND.contains(ground.getType())) {
            return null;
        }
        if (!hasHeadroom(world, x, ground.getY(), z)) {
            return null;
        }
        if (hasHazardNearby(world, x, ground.getY(), z)) {
            return null;
        }
        return new Location(world, x + 0.5, ground.getY() + 1.0, z + 0.5);
    }

    private boolean hasHeadroom(World world, int x, int groundY, int z) {
        for (int dy = 1; dy <= settings.spawnSearchMinAirAbove(); dy++) {
            Block above = world.getBlockAt(x, groundY + dy, z);
            if (!above.isEmpty() && !above.isPassable()) {
                return false;
            }
            if (UNSAFE_GROUND.contains(above.getType())) {
                return false;
            }
        }
        return true;
    }

    /** Lava ou fogo encostando na plataforma tambem inviabiliza o ponto. */
    private boolean hasHazardNearby(World world, int x, int groundY, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    Material type = world.getBlockAt(x + dx, groundY + dy, z + dz).getType();
                    if (type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Ultimo recurso: constroi chao. Um mundo em que nada foi considerado seguro
     * ainda precisa ser jogavel, e nascer sobre pedra e melhor do que cair no void.
     */
    private Location buildPlatform(World world, int x, int z) {
        int y = Math.min(world.getHighestBlockAt(x, z).getY() + 1, world.getMaxHeight() - 10);
        y = Math.max(y, world.getMinHeight() + 5);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.STONE, false);
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                }
            }
        }
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }
}

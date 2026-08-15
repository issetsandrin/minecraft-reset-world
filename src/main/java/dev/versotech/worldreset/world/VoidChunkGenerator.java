package dev.versotech.worldreset.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Gerador vazio usado no lobby. O lobby nunca e apagado, entao gera-lo como um
 * mundo normal seria desperdicio permanente de disco e de tempo de boot.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    private final int spawnY;

    public VoidChunkGenerator(int spawnY) {
        this.spawnY = spawnY;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    // shouldGenerateBedrock nao existe mais como etapa propria: a bedrock virou
    // parte da geracao de superficie, que ja esta desligada acima.

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, spawnY, 0.5);
    }
}

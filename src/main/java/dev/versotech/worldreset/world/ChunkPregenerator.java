package dev.versotech.worldreset.world;

import dev.versotech.worldreset.config.ResetSettings;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gera o proximo mundo em background, antes de qualquer morte acontecer.
 *
 * <p>O trabalho pesado fica na thread de geracao do Paper via
 * {@code getChunkAtAsync}; a thread principal so despacha pedidos e controla
 * quantos podem estar em voo. Sem esse teto o servidor enche a fila de chunks e
 * engasga, que e justamente o que a pre-geracao existe para evitar.
 */
public final class ChunkPregenerator {

    private final JavaPlugin plugin;
    private final ResetSettings settings;

    private BukkitTask task;
    private World target;
    private List<long[]> queue = List.of();
    private int dispatchIndex;
    private int lastLoggedPercent;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();

    public ChunkPregenerator(JavaPlugin plugin, ResetSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean isRunning() {
        return task != null;
    }

    public int totalChunks() {
        return queue.size();
    }

    public int completedChunks() {
        return completed.get();
    }

    public int progressPercent() {
        return queue.isEmpty() ? 100 : (int) ((completedChunks() * 100L) / queue.size());
    }

    public String targetWorldName() {
        return target == null ? "-" : target.getName();
    }

    /**
     * Comeca a pre-geracao ao redor do spawn do mundo. Se ja houver uma rodando,
     * ela e cancelada primeiro.
     *
     * @param onComplete executado na thread principal quando o ultimo chunk chega
     */
    public void start(World world, Runnable onComplete) {
        cancel();

        if (!settings.pregenEnabled() || settings.pregenRadiusBlocks() <= 0) {
            this.target = world;
            this.queue = List.of();
            this.completed.set(0);
            onComplete.run();
            return;
        }

        this.target = world;
        this.queue = buildSpiral(world, settings.pregenRadiusBlocks());
        this.dispatchIndex = 0;
        this.lastLoggedPercent = 0;
        this.inFlight.set(0);
        this.completed.set(0);

        plugin.getLogger().info("Pre-gerando " + queue.size() + " chunks de '"
                + world.getName() + "' (raio de " + settings.pregenRadiusBlocks() + " blocos).");

        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                pump(this, onComplete);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void pump(BukkitRunnable runnable, Runnable onComplete) {
        int dispatchedThisTick = 0;

        while (dispatchIndex < queue.size()
                && inFlight.get() < settings.pregenMaxConcurrentChunks()
                && dispatchedThisTick < settings.pregenChunksPerTick()) {

            long[] coords = queue.get(dispatchIndex++);
            dispatchedThisTick++;
            inFlight.incrementAndGet();

            target.getChunkAtAsync((int) coords[0], (int) coords[1], true)
                    .whenComplete((chunk, error) -> {
                        inFlight.decrementAndGet();
                        completed.incrementAndGet();
                        if (error != null) {
                            plugin.getLogger().warning("Falha ao gerar chunk "
                                    + coords[0] + "," + coords[1] + ": " + error.getMessage());
                        }
                    });
        }

        logProgress();

        if (completed.get() >= queue.size()) {
            runnable.cancel();
            task = null;
            target.save();
            plugin.getLogger().info("Pre-geracao de '" + target.getName() + "' concluida ("
                    + queue.size() + " chunks).");
            onComplete.run();
        }
    }

    private void logProgress() {
        int percent = progressPercent();
        int step = settings.pregenLogEveryPercent();
        if (percent >= lastLoggedPercent + step && percent < 100) {
            lastLoggedPercent = percent - (percent % step);
            plugin.getLogger().info("Pre-geracao de '" + target.getName() + "': "
                    + lastLoggedPercent + "% (" + completed.get() + "/" + queue.size() + ")");
        }
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        inFlight.set(0);
    }

    /**
     * Coordenadas de chunk ordenadas do centro para fora, para que o spawn fique
     * pronto antes das bordas.
     */
    private static List<long[]> buildSpiral(World world, int radiusBlocks) {
        var spawn = world.getSpawnLocation();
        int centerX = spawn.getBlockX() >> 4;
        int centerZ = spawn.getBlockZ() >> 4;
        int radiusChunks = Math.max(1, (int) Math.ceil(radiusBlocks / 16.0));

        List<long[]> coords = new ArrayList<>((2 * radiusChunks + 1) * (2 * radiusChunks + 1));
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                coords.add(new long[]{centerX + dx, centerZ + dz, (long) dx * dx + (long) dz * dz});
            }
        }
        coords.sort(Comparator.comparingLong(c -> c[2]));
        return coords;
    }
}

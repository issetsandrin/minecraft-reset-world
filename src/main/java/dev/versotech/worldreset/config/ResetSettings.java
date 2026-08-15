package dev.versotech.worldreset.config;

import org.bukkit.Difficulty;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Leitura tipada do config.yml. Tudo e resolvido uma vez no carregamento para
 * que o caminho quente (a morte de um jogador) nao toque em YAML.
 */
public final class ResetSettings {

    private final String lobbyWorldName;
    private final int lobbySpawnY;
    private final int lobbyPlatformRadius;

    private final String slotA;
    private final String slotB;
    private final WorldType worldType;
    private final Difficulty difficulty;
    private final boolean generateStructures;
    private final boolean createNether;
    private final boolean createTheEnd;

    private final int countdownSeconds;
    private final int minSecondsBetweenResets;
    private final int graceSeconds;
    private final boolean announceCause;

    private final boolean wipeInventory;
    private final boolean wipeEnderChest;
    private final boolean wipeExperience;
    private final boolean wipePotionEffects;
    private final boolean wipeAdvancements;
    private final boolean wipeStatistics;
    private final boolean wipeRecipes;
    private final boolean wipeOfflinePlayers;

    private final boolean pregenEnabled;
    private final int pregenRadiusBlocks;
    private final int pregenMaxConcurrentChunks;
    private final int pregenChunksPerTick;
    private final int pregenLogEveryPercent;

    private final int spawnSearchMaxRadius;
    private final int spawnSearchStep;
    private final int spawnSearchMinAirAbove;
    private final boolean spawnSearchBuildPlatform;

    private final long joinTeleportDelayTicks;
    private final int joinVerifyAttempts;

    private final boolean healthDisplayEnabled;
    private final long healthDisplayUpdateTicks;
    private final boolean healthDisplayShowHungerArmor;
    private final boolean healthDisplayHighlightDanger;
    private final boolean healthDisplayShowDeaths;
    private final String iconHunger;
    private final String iconArmor;
    private final String iconDeaths;

    private final String essentialsMode;
    private final boolean essentialsWipeWorldBound;
    private final boolean essentialsRewriteSpawn;
    private final boolean essentialsReloadAfterWipe;

    public ResetSettings(FileConfiguration config) {
        this.lobbyWorldName = config.getString("lobby.world-name", "lobby");
        this.lobbySpawnY = config.getInt("lobby.spawn-y", 100);
        this.lobbyPlatformRadius = Math.max(3, config.getInt("lobby.platform-radius", 8));

        this.slotA = config.getString("arena.slot-a", "wr_arena_a");
        this.slotB = config.getString("arena.slot-b", "wr_arena_b");
        this.worldType = parseEnum(WorldType.class, config.getString("arena.world-type"), WorldType.NORMAL);
        this.difficulty = parseEnum(Difficulty.class, config.getString("arena.difficulty"), Difficulty.NORMAL);
        this.generateStructures = config.getBoolean("arena.generate-structures", true);
        this.createNether = config.getBoolean("arena.create-nether", true);
        this.createTheEnd = config.getBoolean("arena.create-the-end", true);

        this.countdownSeconds = Math.max(0, config.getInt("reset.countdown-seconds", 5));
        this.minSecondsBetweenResets = Math.max(0, config.getInt("reset.min-seconds-between-resets", 30));
        this.graceSeconds = Math.max(0, config.getInt("reset.grace-seconds", 10));
        this.announceCause = config.getBoolean("reset.announce-cause", true);

        this.wipeInventory = config.getBoolean("wipe.inventory", true);
        this.wipeEnderChest = config.getBoolean("wipe.ender-chest", true);
        this.wipeExperience = config.getBoolean("wipe.experience", true);
        this.wipePotionEffects = config.getBoolean("wipe.potion-effects", true);
        this.wipeAdvancements = config.getBoolean("wipe.advancements", true);
        this.wipeStatistics = config.getBoolean("wipe.statistics", true);
        this.wipeRecipes = config.getBoolean("wipe.recipes", true);
        this.wipeOfflinePlayers = config.getBoolean("wipe.offline-players", true);

        this.pregenEnabled = config.getBoolean("pregeneration.enabled", true);
        this.pregenRadiusBlocks = Math.max(0, config.getInt("pregeneration.radius-blocks", 250));
        this.pregenMaxConcurrentChunks = Math.max(1, config.getInt("pregeneration.max-concurrent-chunks", 8));
        this.pregenChunksPerTick = Math.max(1, config.getInt("pregeneration.chunks-per-tick", 4));
        this.pregenLogEveryPercent = Math.max(1, config.getInt("pregeneration.log-progress-every-percent", 10));

        this.spawnSearchMaxRadius = Math.max(16, config.getInt("spawn-search.max-radius-blocks", 240));
        this.spawnSearchStep = Math.max(1, config.getInt("spawn-search.step", 8));
        this.spawnSearchMinAirAbove = Math.max(2, config.getInt("spawn-search.min-air-above", 2));
        this.spawnSearchBuildPlatform = config.getBoolean("spawn-search.build-platform-as-fallback", true);

        this.joinTeleportDelayTicks = Math.max(1L, config.getLong("join.teleport-delay-ticks", 20L));
        this.joinVerifyAttempts = Math.max(1, config.getInt("join.verify-attempts", 3));

        this.healthDisplayEnabled = config.getBoolean("health-display.enabled", true);
        this.healthDisplayUpdateTicks = Math.max(1L, config.getLong("health-display.update-ticks", 10L));
        this.healthDisplayShowHungerArmor = config.getBoolean("health-display.show-hunger-armor", true);
        this.healthDisplayHighlightDanger = config.getBoolean("health-display.highlight-danger", true);
        this.healthDisplayShowDeaths = config.getBoolean("health-display.show-deaths", true);
        this.iconHunger = config.getString("health-display.icons.hunger", "");
        this.iconArmor = config.getString("health-display.icons.armor", "");
        this.iconDeaths = config.getString("health-display.icons.deaths", "");

        this.essentialsMode = config.getString("integrations.essentials.enabled", "auto");
        this.essentialsWipeWorldBound = config.getBoolean("integrations.essentials.wipe-world-bound-data", true);
        this.essentialsRewriteSpawn = config.getBoolean("integrations.essentials.rewrite-spawn", true);
        this.essentialsReloadAfterWipe = config.getBoolean("integrations.essentials.reload-after-wipe", true);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public String lobbyWorldName() {
        return lobbyWorldName;
    }

    public int lobbySpawnY() {
        return lobbySpawnY;
    }

    public int lobbyPlatformRadius() {
        return lobbyPlatformRadius;
    }

    public String slotA() {
        return slotA;
    }

    public String slotB() {
        return slotB;
    }

    public WorldType worldType() {
        return worldType;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public boolean generateStructures() {
        return generateStructures;
    }

    public boolean createNether() {
        return createNether;
    }

    public boolean createTheEnd() {
        return createTheEnd;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int minSecondsBetweenResets() {
        return minSecondsBetweenResets;
    }

    public int graceSeconds() {
        return graceSeconds;
    }

    public boolean announceCause() {
        return announceCause;
    }

    public boolean wipeInventory() {
        return wipeInventory;
    }

    public boolean wipeEnderChest() {
        return wipeEnderChest;
    }

    public boolean wipeExperience() {
        return wipeExperience;
    }

    public boolean wipePotionEffects() {
        return wipePotionEffects;
    }

    public boolean wipeAdvancements() {
        return wipeAdvancements;
    }

    public boolean wipeStatistics() {
        return wipeStatistics;
    }

    public boolean wipeRecipes() {
        return wipeRecipes;
    }

    public boolean wipeOfflinePlayers() {
        return wipeOfflinePlayers;
    }

    public boolean pregenEnabled() {
        return pregenEnabled;
    }

    public int pregenRadiusBlocks() {
        return pregenRadiusBlocks;
    }

    public int pregenMaxConcurrentChunks() {
        return pregenMaxConcurrentChunks;
    }

    public int pregenChunksPerTick() {
        return pregenChunksPerTick;
    }

    public int pregenLogEveryPercent() {
        return pregenLogEveryPercent;
    }

    public int spawnSearchMaxRadius() {
        return spawnSearchMaxRadius;
    }

    public int spawnSearchStep() {
        return spawnSearchStep;
    }

    public int spawnSearchMinAirAbove() {
        return spawnSearchMinAirAbove;
    }

    public boolean spawnSearchBuildPlatform() {
        return spawnSearchBuildPlatform;
    }

    public long joinTeleportDelayTicks() {
        return joinTeleportDelayTicks;
    }

    public int joinVerifyAttempts() {
        return joinVerifyAttempts;
    }

    public boolean healthDisplayEnabled() {
        return healthDisplayEnabled;
    }

    public long healthDisplayUpdateTicks() {
        return healthDisplayUpdateTicks;
    }

    public boolean healthDisplayShowHungerArmor() {
        return healthDisplayShowHungerArmor;
    }

    public boolean healthDisplayHighlightDanger() {
        return healthDisplayHighlightDanger;
    }

    public boolean healthDisplayShowDeaths() {
        return healthDisplayShowDeaths;
    }

    public String iconHunger() {
        return iconHunger;
    }

    public String iconArmor() {
        return iconArmor;
    }

    public String iconDeaths() {
        return iconDeaths;
    }

    public String essentialsMode() {
        return essentialsMode;
    }

    public boolean essentialsWipeWorldBound() {
        return essentialsWipeWorldBound;
    }

    public boolean essentialsRewriteSpawn() {
        return essentialsRewriteSpawn;
    }

    public boolean essentialsReloadAfterWipe() {
        return essentialsReloadAfterWipe;
    }
}

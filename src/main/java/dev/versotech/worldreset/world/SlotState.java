package dev.versotech.worldreset.world;

import dev.versotech.worldreset.config.ResetSettings;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Guarda em disco qual dos dois slots esta ativo. Precisa ser persistente: no
 * boot seguinte o plugin tem que saber qual pasta e o mundo em uso e qual e o
 * descartavel, senao ele reaproveitaria o mundo errado.
 */
public final class SlotState {

    private static final String SLOT_A = "A";
    private static final String SLOT_B = "B";

    private final File file;
    private final ResetSettings settings;
    private final Logger logger;

    private String activeSlot = SLOT_A;
    private boolean standbyReady = false;
    private int resetCount = 0;

    /** Tempo de sobrevivencia da run atual e o melhor ja alcancado. */
    private long survivalMillis = 0L;
    private long bestSurvivalMillis = 0L;

    /** Instante do ultimo tick contabilizado; nao persiste. */
    private long lastTick = 0L;

    public SlotState(File file, ResetSettings settings, Logger logger) {
        this.file = file;
        this.settings = settings;
        this.logger = logger;
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        var yaml = YamlConfiguration.loadConfiguration(file);
        String slot = yaml.getString("active-slot", SLOT_A);
        this.activeSlot = SLOT_B.equalsIgnoreCase(slot) ? SLOT_B : SLOT_A;
        this.standbyReady = yaml.getBoolean("standby-ready", false);
        this.resetCount = yaml.getInt("reset-count", 0);
        this.survivalMillis = yaml.getLong("survival-millis", 0L);
        this.bestSurvivalMillis = yaml.getLong("best-survival-millis", 0L);
    }

    public void save() {
        var yaml = new YamlConfiguration();
        yaml.set("active-slot", activeSlot);
        yaml.set("standby-ready", standbyReady);
        yaml.set("reset-count", resetCount);
        yaml.set("survival-millis", survivalMillis);
        yaml.set("best-survival-millis", bestSurvivalMillis);
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Nao consegui gravar o estado dos slots em " + file, e);
        }
    }

    public Arena active() {
        return new Arena(SLOT_A.equals(activeSlot) ? settings.slotA() : settings.slotB());
    }

    public Arena standby() {
        return new Arena(SLOT_A.equals(activeSlot) ? settings.slotB() : settings.slotA());
    }

    // ------------------------------------------------- cronometro da run

    /**
     * Acumula o tempo decorrido desde a chamada anterior.
     *
     * <p>So conta com alguem conectado. Um servidor ligado a noite inteira sem
     * ninguem jogando nao deveria inflar o tempo de sobrevivencia, e tampouco as
     * horas em que ele fica desligado - por isso o acumulado e somado em fatias
     * em vez de calculado a partir de um instante inicial.
     */
    public void tickSurvival(boolean anyoneOnline, long nowMillis) {
        if (lastTick != 0L && anyoneOnline) {
            survivalMillis += nowMillis - lastTick;
        }
        lastTick = nowMillis;
    }

    public long survivalMillis() {
        return survivalMillis;
    }

    public long bestSurvivalMillis() {
        return bestSurvivalMillis;
    }

    /**
     * Encerra a run atual e devolve quanto ela durou, promovendo o resultado a
     * recorde se for o caso.
     *
     * @return duracao da run e se ela bateu o recorde anterior
     */
    public RunResult finishRun() {
        long survived = survivalMillis;
        boolean record = survived > bestSurvivalMillis;
        if (record) {
            bestSurvivalMillis = survived;
        }
        survivalMillis = 0L;
        lastTick = 0L;
        save();
        return new RunResult(survived, record);
    }

    /** Quanto durou uma run e se ela superou todas as anteriores. */
    public record RunResult(long millis, boolean record) {
    }

    /** Promove o standby a ativo. Chamado no instante do swap. */
    public void swap() {
        this.activeSlot = SLOT_A.equals(activeSlot) ? SLOT_B : SLOT_A;
        this.standbyReady = false;
        this.resetCount++;
        save();
    }

    public boolean standbyReady() {
        return standbyReady;
    }

    public void markStandbyReady(boolean ready) {
        this.standbyReady = ready;
        save();
    }

    public int resetCount() {
        return resetCount;
    }

    public String activeSlotLabel() {
        return activeSlot;
    }
}

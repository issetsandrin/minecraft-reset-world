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
    }

    public void save() {
        var yaml = new YamlConfiguration();
        yaml.set("active-slot", activeSlot);
        yaml.set("standby-ready", standbyReady);
        yaml.set("reset-count", resetCount);
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

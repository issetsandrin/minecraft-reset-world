package dev.versotech.worldreset.display;

/**
 * Formatacao de duracoes para leitura rapida no painel.
 *
 * <p>Abaixo de uma hora o formato e {@code mm:ss}, porque e o que se le de
 * relance; acima disso vira {@code h:mm:ss}, ja que a hora passa a ser a
 * informacao principal.
 */
public final class Duration {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;

    private Duration() {
    }

    public static String format(long millis) {
        if (millis < 0) {
            millis = 0;
        }

        long hours = millis / HOUR;
        long minutes = (millis % HOUR) / MINUTE;
        long seconds = (millis % MINUTE) / SECOND;

        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }
}

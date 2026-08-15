package dev.versotech.worldreset.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.Nullable;

/**
 * Liga as dimensoes de cada slot entre si.
 *
 * <p>O Minecraft so sabe ligar automaticamente o overworld principal do servidor
 * ao nether e ao end dele. As dimensoes criadas por plugin nao entram nessa
 * conta: ao atravessar um portal de volta, o servidor nao encontra o mundo de
 * origem e deposita o jogador no ponto de nascimento. E o motivo de existirem
 * plugins dedicados so a isso.
 *
 * <p>Aqui a ligacao e feita pelo nome: {@code wr_arena_a} conversa com
 * {@code wr_arena_a_nether} e {@code wr_arena_a_the_end}, nos dois sentidos.
 * Como a regra e por sufixo, ela vale para qualquer slot sem precisar saber
 * qual esta ativo.
 */
public final class PortalListener implements Listener {

    private static final String NETHER_SUFFIX = "_nether";
    private static final String END_SUFFIX = "_the_end";

    /** Um bloco no nether corresponde a oito no overworld. */
    private static final double NETHER_SCALE = 8.0;

    /** Onde o vanilla materializa quem chega ao end: a plataforma de obsidiana. */
    private static final int END_PLATFORM_X = 100;
    private static final int END_PLATFORM_Y = 50;
    private static final int END_PLATFORM_Z = 0;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Location from = event.getFrom();
        World origin = from.getWorld();
        if (origin == null) {
            return;
        }

        World destination = counterpart(origin, event.getCause());
        if (destination == null) {
            // Nao e um mundo nosso, ou o par nao existe: deixa o servidor decidir.
            return;
        }

        event.setTo(translate(from, destination, event.getCause(), event.getPlayer()));

        // Sem isto o servidor usa o raio configurado para o mundo principal, que
        // nao vale para dimensoes de plugin.
        event.setSearchRadius(event.getCause() == TeleportCause.NETHER_PORTAL ? 128 : 0);
        event.setCanCreatePortal(event.getCause() == TeleportCause.NETHER_PORTAL);
        event.setCreationRadius(16);
    }

    /**
     * Mesma ligacao para o que nao e jogador.
     *
     * <p>Itens jogados no portal, mobs empurrados e minecarts atravessam pelo
     * EntityPortalEvent, que e outro caminho no servidor. Sem tratar aqui, tudo
     * isso continuaria caindo no mundo errado.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location from = event.getFrom();
        World origin = from.getWorld();
        if (origin == null) {
            return;
        }

        // O evento de entidade nao informa a causa; deduzimos pelo par que
        // existir para este mundo.
        TeleportCause cause = counterpart(origin, TeleportCause.NETHER_PORTAL) != null
                ? TeleportCause.NETHER_PORTAL
                : TeleportCause.END_PORTAL;

        World destination = counterpart(origin, cause);
        if (destination == null) {
            return;
        }
        event.setTo(translate(from, destination, cause, null));
        event.setSearchRadius(cause == TeleportCause.NETHER_PORTAL ? 128 : 0);
    }

    /** O mundo do outro lado do portal, ou null se nao houver par. */
    private @Nullable World counterpart(World origin, TeleportCause cause) {
        String name = origin.getName();

        if (cause == TeleportCause.NETHER_PORTAL) {
            return name.endsWith(NETHER_SUFFIX)
                    ? Bukkit.getWorld(trim(name, NETHER_SUFFIX))
                    : Bukkit.getWorld(name + NETHER_SUFFIX);
        }
        if (cause == TeleportCause.END_PORTAL) {
            return name.endsWith(END_SUFFIX)
                    ? Bukkit.getWorld(trim(name, END_SUFFIX))
                    : Bukkit.getWorld(name + END_SUFFIX);
        }
        return null;
    }

    private static String trim(String name, String suffix) {
        return name.substring(0, name.length() - suffix.length());
    }

    /**
     * Converte a posicao de origem para a dimensao de destino.
     *
     * <p>Entre overworld e nether a escala e de oito para um, que e o que faz um
     * portal reaparecer perto de onde deveria em vez de a quilometros dali.
     */
    private Location translate(Location from, World destination, TeleportCause cause,
                               @Nullable Player player) {
        if (cause == TeleportCause.END_PORTAL) {
            if (destination.getEnvironment() == World.Environment.THE_END) {
                // Ida: o vanilla materializa quem chega sobre a plataforma de
                // obsidiana, nunca nas coordenadas de origem.
                return new Location(destination, END_PLATFORM_X, END_PLATFORM_Y, END_PLATFORM_Z);
            }

            // Volta: o end nao e simetrico ao nether. Nao existe portal de
            // retorno numa posicao equivalente - o vanilla devolve o jogador ao
            // ponto de renascimento dele, que e a cama se houver uma.
            if (player != null) {
                Location respawn = player.getRespawnLocation();
                if (respawn != null && destination.equals(respawn.getWorld())) {
                    return respawn;
                }
            }
            return destination.getSpawnLocation();
        }

        double scale = from.getWorld().getEnvironment() == World.Environment.NETHER
                ? NETHER_SCALE          // saindo do nether: multiplica
                : 1.0 / NETHER_SCALE;   // entrando no nether: divide

        double y = Math.max(destination.getMinHeight() + 1,
                Math.min(from.getY(), destination.getMaxHeight() - 2));

        return new Location(destination,
                from.getX() * scale,
                y,
                from.getZ() * scale,
                from.getYaw(),
                from.getPitch());
    }
}

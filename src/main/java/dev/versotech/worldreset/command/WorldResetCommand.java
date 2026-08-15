package dev.versotech.worldreset.command;

import dev.versotech.worldreset.WorldResetPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

public final class WorldResetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("status", "force", "pregen", "tp", "reload");

    private final WorldResetPlugin plugin;

    public WorldResetCommand(WorldResetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("worldreset.admin")) {
            sender.sendMessage(plugin.messages().chat("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "status" -> sendStatus(sender);
            case "force" -> forceReset(sender);
            case "pregen" -> restartPregen(sender);
            case "tp" -> teleport(sender);
            case "reload" -> reload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("/worldreset <status|force|pregen|tp|reload>", NamedTextColor.GRAY));
    }

    private void sendStatus(CommandSender sender) {
        var state = plugin.coordinator().slotState();
        var pregen = plugin.coordinator().pregenerator();

        sender.sendMessage(Component.text("WorldReset", NamedTextColor.GOLD));
        sender.sendMessage(line("Slot ativo", state.activeSlotLabel() + " (" + state.active().overworld() + ")"));
        sender.sendMessage(line("Slot de espera", state.standby().overworld()
                + (state.standbyReady() ? " - pronto" : " - preparando")));
        sender.sendMessage(line("Resets ja feitos", String.valueOf(state.resetCount())));
        sender.sendMessage(line("Reset em andamento", plugin.coordinator().isRunning() ? "sim" : "nao"));
        sender.sendMessage(line("Rearma em", plugin.coordinator().secondsUntilArmed() + "s"));
        sender.sendMessage(line("Pre-geracao", pregen.isRunning()
                ? pregen.progressPercent() + "% de " + pregen.totalChunks() + " chunks em " + pregen.targetWorldName()
                : "parada"));
    }

    private Component line(String label, String value) {
        return Component.text(" " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private void forceReset(CommandSender sender) {
        String cause = sender instanceof Player player ? player.getName() : "console";
        if (plugin.coordinator().forceReset(cause)) {
            sender.sendMessage(Component.text("Reset disparado.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Ja existe um reset em andamento.", NamedTextColor.RED));
        }
    }

    private void restartPregen(CommandSender sender) {
        plugin.coordinator().prepareNextArena(true);
        sender.sendMessage(Component.text("Pre-geracao do slot de espera reiniciada.", NamedTextColor.GREEN));
    }

    private void teleport(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Comando so para jogadores.", NamedTextColor.RED));
            return;
        }
        World active = Bukkit.getWorld(plugin.coordinator().slotState().active().overworld());
        if (active == null) {
            sender.sendMessage(Component.text("O mundo ativo nao esta carregado.", NamedTextColor.RED));
            return;
        }
        player.teleport(active.getSpawnLocation());
    }

    private void reload(CommandSender sender) {
        plugin.reloadEverything();
        sender.sendMessage(Component.text("Configuracao recarregada.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return Stream.<String>of().toList();
    }
}

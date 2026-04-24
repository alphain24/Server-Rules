package in.alphain.serverrules.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import in.alphain.serverrules.ServerRules;
import in.alphain.serverrules.config.RulesConfig;
import in.alphain.serverrules.datapack.RulesGuiOpener;
import in.alphain.serverrules.handler.JoinHandler;
import in.alphain.serverrules.storage.RulesManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Optional;
import java.util.UUID;

/**
 * Registers the {@code /rules} command tree along with the standalone
 * {@code /rules-view} command.
 *
 * Subcommands of {@code /rules}:
 * <ul>
 *   <li>{@code /rules} - re-opens the rules GUI for the sender.
 *       Restricted players get the full freeze + GUI flow; already
 *       accepted players just get a plain view.</li>
 *   <li>{@code /rules accept} - marks the sender as having accepted.</li>
 *   <li>{@code /rules decline} - kicks the sender.</li>
 *   <li>{@code /rules reload [player]} - op only; resets acceptance data
 *       and immediately re-opens the GUI for any online target.</li>
 * </ul>
 *
 * <p>{@code /rules-view} is a separate top-level command available to
 * every player — it opens the rules menu in view-only mode without
 * applying any freeze effects so accepted players can re-read the rules.
 */
public final class RulesCommand {

    private static final SuggestionProvider<CommandSourceStack> ACCEPTED_PLAYER_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    RulesManager.get().getAccepted().values(), builder);

    private RulesCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rules")
                .executes(RulesCommand::executeOpen)
                .then(Commands.literal("accept")
                        .executes(RulesCommand::executeAccept))
                .then(Commands.literal("decline")
                        .executes(RulesCommand::executeDecline))
                .then(Commands.literal("view")
                        .executes(RulesCommand::executeView))
                .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(RulesCommand::executeReloadAll)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(ACCEPTED_PLAYER_SUGGESTIONS)
                                .executes(RulesCommand::executeReloadPlayer))));

        // Standalone /rules-view command: view-only mode with no freeze
        // effects, intended for players who have already accepted the
        // rules and just want a reminder.
        dispatcher.register(Commands.literal("rules-view")
                .executes(RulesCommand::executeView));
    }

    // -----------------------------------------------------------------
    // /rules
    // -----------------------------------------------------------------
    private static int executeOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (RulesManager.get().hasAccepted(player)) {
            // Already accepted — no need to freeze them again.
            RulesGuiOpener.openMenu(player);
        } else {
            RulesGuiOpener.open(player);
        }
        return 1;
    }

    // -----------------------------------------------------------------
    // /rules view, /rules-view
    // -----------------------------------------------------------------
    private static int executeView(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        RulesGuiOpener.openMenu(player);
        return 1;
    }

    // -----------------------------------------------------------------
    // /rules accept
    // -----------------------------------------------------------------
    private static int executeAccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        RulesManager manager = RulesManager.get();

        if (manager.hasAccepted(player)) {
            player.sendSystemMessage(Component.literal("§eYou have already accepted the server rules."));
            return 0;
        }

        manager.markAccepted(player);
        JoinHandler.onPlayerAccepted(player);
        RulesGuiOpener.clearEffects(player);
        player.closeContainer();
        player.sendSystemMessage(Component.literal("§aYou have accepted the server rules. Welcome to the Angel's Server!"));
        ServerRules.LOGGER.info("[ServerRules] {} accepted the rules.", player.getGameProfile().name());
        return 1;
    }

    // -----------------------------------------------------------------
    // /rules decline
    // -----------------------------------------------------------------
    private static int executeDecline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerRules.LOGGER.info("[ServerRules] {} declined the rules.", player.getGameProfile().name());
        player.connection.disconnect(Component.literal(RulesConfig.getDeclineKickMessage()));
        return 1;
    }

    // -----------------------------------------------------------------
    // /rules reload
    // -----------------------------------------------------------------
    private static int executeReloadAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        RulesManager.get().clearAllAccepted();

        // Re-open the GUI on every currently-online player so they're
        // frozen again until they accept the rules.
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            JoinHandler.onRulesReloaded(online);
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("§aCleared all accepted players. Everyone must re-accept the rules."),
                true);
        return 1;
    }

    private static int executeReloadPlayer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        // Try online player first, then the accepted list by name.
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        UUID uuid = online != null ? online.getUUID() : findAcceptedUuidByName(name);

        if (uuid == null) {
            ctx.getSource().sendFailure(Component.literal("§cNo accepted player matching '" + name + "' was found."));
            return 0;
        }

        boolean removed = RulesManager.get().removeAccepted(uuid);
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c'" + name + "' had no stored acceptance record."));
            return 0;
        }

        // If they're online, re-freeze them and re-open the GUI right away.
        if (online != null) {
            JoinHandler.onRulesReloaded(online);
        }

        final String finalName = name;
        ctx.getSource().sendSuccess(
                () -> Component.literal("§aRemoved acceptance for '" + finalName + "'. They must accept the rules again."),
                true);
        return 1;
    }

    private static UUID findAcceptedUuidByName(String name) {
        Optional<UUID> match = RulesManager.get().getAccepted().entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(name))
                .map(java.util.Map.Entry::getKey)
                .findFirst();
        return match.orElse(null);
    }
}

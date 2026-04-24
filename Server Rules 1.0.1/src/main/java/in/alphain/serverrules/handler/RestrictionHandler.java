package in.alphain.serverrules.handler;

import in.alphain.serverrules.config.RulesConfig;
import in.alphain.serverrules.storage.RulesManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Blocks all meaningful player interactions while the player has not
 * yet accepted the server rules.
 *
 * Restrictions enforced:
 * <ul>
 *   <li>Block breaking, placing, and interaction</li>
 *   <li>Attacking or using entities</li>
 *   <li>Using items</li>
 *   <li>Chat messages</li>
 *   <li>All commands except the whitelisted rule commands</li>
 * </ul>
 *
 * Movement freezing is applied in {@link in.alphain.serverrules.datapack.RulesGuiOpener}
 * via {@code SLOWNESS} / {@code JUMP_BOOST} / {@code BLINDNESS} status effects
 * and by keeping the chest screen open.
 */
public final class RestrictionHandler {

    /** Commands a restricted player is still allowed to run. */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "rules",
            "rules accept",
            "rules decline",
            "help"
    );

    private RestrictionHandler() {}

    public static void register() {
        // Block breaking.
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (isRestricted(player)) {
                sendRestriction(player);
                return false;
            }
            return true;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (isRestricted(player)) {
                sendRestriction(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Block right-click interaction.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (isRestricted(player)) {
                sendRestriction(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Entity interaction.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (isRestricted(player)) {
                sendRestriction(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (isRestricted(player)) {
                sendRestriction(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Item usage.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (isRestricted(player)) {
                sendRestriction(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Chat (block everything).
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isRestricted(sender)) {
                sendRestriction(sender);
                return false;
            }
            return true;
        });

        // Command filtering.
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
            ServerPlayer player = source.getPlayer();
            if (player == null) return true;
            if (!isRestricted(player)) return true;

            String raw = message.signedContent().trim();
            if (raw.startsWith("/")) raw = raw.substring(1);
            String lower = raw.toLowerCase();
            for (String allowed : ALLOWED_COMMANDS) {
                if (lower.equals(allowed) || lower.startsWith(allowed + " ")) {
                    return true;
                }
            }
            sendRestriction(player);
            return false;
        });
    }

    private static boolean isRestricted(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        return RulesManager.get().isRestricted(serverPlayer);
    }

    private static void sendRestriction(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(RulesConfig.getRestrictionMessage()));
        }
    }
}

package in.alphain.serverrules.datapack;

import in.alphain.serverrules.ServerRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Opens the rules GUI for a player via the Inventory Menu mod's
 * {@code /menu <namespace:id>} command.
 *
 * Inventory Menu's command is <em>player-scoped</em>: it opens the
 * menu for whichever player executes it, so we must dispatch the
 * command with the player's own command source (suppressed output
 * and elevated to level 2 so it bypasses permission checks).
 *
 * Movement-freezing status effects are applied while the GUI is
 * open; they are cleared once the player accepts.
 */
public final class RulesGuiOpener {

    private RulesGuiOpener() {}

    public static void open(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        // Apply "freeze" effects while restricted.
        int duration = 20 * 60 * 10; // 10 minutes safety window
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 255, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, duration, 128, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));

        player.sendSystemMessage(Component.literal("§6Please read and accept the server rules to continue."));

        String command = "menu " + DatapackGenerator.MENU_NAMESPACE + ":" + DatapackGenerator.MENU_ID;

        try {
            // Execute as the player, with elevated level so the command
            // passes Inventory Menu's permission check, and suppress chat
            // feedback so the player only sees the GUI opening.
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack()
                            .withPermission(PermissionSet.ALL_PERMISSIONS)
                            .withSuppressedOutput(),
                    command
            );
        } catch (Exception e) {
            ServerRules.LOGGER.error("[ServerRules] Failed to open rules GUI for {}: {}",
                    player.getGameProfile().name(), e.getMessage());
            player.sendSystemMessage(Component.literal("§cFailed to open rules GUI. Use §e/rules accept§c or §e/rules decline§c."));
        }
    }

    public static void clearEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.JUMP_BOOST);
        player.removeEffect(MobEffects.BLINDNESS);
    }
}

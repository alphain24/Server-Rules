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
 * <p>Two public flows are provided:
 * <ul>
 *   <li>{@link #open(ServerPlayer)} — applies the freeze/blindness
 *       status effects <em>and</em> opens the menu. Used for players
 *       that must accept the rules before they can play.</li>
 *   <li>{@link #openMenu(ServerPlayer)} — just opens the menu without
 *       touching status effects. Used by {@code /rules-view} so
 *       already-accepted players can re-read the rules.</li>
 * </ul>
 */
public final class RulesGuiOpener {

    /** Freeze-effect duration in ticks (10 minutes — matches the accept timeout safety window). */
    private static final int FREEZE_DURATION_TICKS = 20 * 60 * 10;

    private RulesGuiOpener() {}

    /**
     * Applies the "frozen in place, can't see the world" status effects.
     * All three effects use the full freeze duration so they don't expire
     * while the player is still reading the rules — this fixes a bug where
     * blindness was only applied for 40 ticks (~2 seconds) and quickly wore
     * off, letting the player see the world again.
     */
    public static void applyFreezeEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_DURATION_TICKS, 255, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, FREEZE_DURATION_TICKS, 128, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FREEZE_DURATION_TICKS, 0, false, false, false));
    }

    /** Applies freeze effects and opens the menu. */
    public static void open(ServerPlayer player) {
        applyFreezeEffects(player);
        player.sendSystemMessage(Component.literal("§6Please read and accept the server rules to continue."));
        openMenu(player);
    }

    /**
     * Opens the rules menu for the given player without applying any
     * freeze effects. Safe to call for already-accepted players who just
     * want to re-read the rules via {@code /rules-view}.
     */
    public static void openMenu(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        // Close whatever the player currently has open so the menu reliably
        // replaces their screen (relevant for re-opens after /rules reload).
        player.closeContainer();

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

package in.alphain.serverrules.handler;

import in.alphain.serverrules.ServerRules;
import in.alphain.serverrules.config.RulesConfig;
import in.alphain.serverrules.datapack.RulesGuiOpener;
import in.alphain.serverrules.storage.RulesManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles player join, disconnect, and "rules reloaded" events.
 *
 * When a player joins (or has their acceptance revoked via
 * {@code /rules reload}):
 * <ul>
 *   <li>If they already accepted the rules, nothing happens.</li>
 *   <li>Otherwise they are marked as "pending", freeze effects are
 *       applied immediately so the screen fades to black, and after
 *       a short delay the chest GUI is opened via the Inventory Menu
 *       mod. A timeout is scheduled alongside.</li>
 * </ul>
 */
public final class JoinHandler {
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ServerRules-Timeout");
                t.setDaemon(true);
                return t;
            });

    private static final Map<UUID, ScheduledFuture<?>> TIMEOUTS = new ConcurrentHashMap<>();

    /** Delay between applying the freeze effects and opening the GUI on join. */
    private static final long JOIN_GUI_DELAY_MS = 3000;

    /**
     * Delay for a {@code /rules reload} re-open. Short enough to feel
     * instant ("as soon as possible") but large enough for the freeze
     * effects to reach the client before the menu packet does.
     */
    private static final long RELOAD_GUI_DELAY_MS = 200;

    private JoinHandler() {}

    public static void onPlayerJoin(ServerPlayer player) {
        RulesManager manager = RulesManager.get();
        UUID uuid = player.getUUID();

        if (manager.hasAccepted(uuid)) {
            ServerRules.LOGGER.debug("[ServerRules] {} has already accepted the rules.", player.getGameProfile().name());
            return;
        }

        ServerRules.LOGGER.info("[ServerRules] {} has not accepted the rules - starting rules flow.", player.getGameProfile().name());
        beginRulesFlow(player, JOIN_GUI_DELAY_MS);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        RulesManager.get().clearPending(uuid);
        cancelTimeout(uuid);
    }

    public static void onPlayerAccepted(ServerPlayer player) {
        cancelTimeout(player.getUUID());
    }

    /**
     * Called after {@code /rules reload} clears a player's acceptance —
     * re-freezes them and re-opens the GUI "as soon as possible".
     */
    public static void onRulesReloaded(ServerPlayer player) {
        ServerRules.LOGGER.info("[ServerRules] Rules reloaded for {} - reopening GUI.", player.getGameProfile().name());
        beginRulesFlow(player, RELOAD_GUI_DELAY_MS);
    }

    /**
     * Marks the player as pending, applies the freeze/blindness effects
     * immediately so the transition is smooth (the screen fades to black
     * while they wait), schedules the GUI open, and starts the accept
     * timeout.
     */
    private static void beginRulesFlow(ServerPlayer player, long guiDelayMs) {
        RulesManager manager = RulesManager.get();
        UUID uuid = player.getUUID();
        manager.markPending(uuid);

        // Apply freeze + blindness now so the player can't move or see the
        // world while they wait for the GUI to appear.
        RulesGuiOpener.applyFreezeEffects(player);
        player.sendSystemMessage(Component.literal("§6Please read and accept the server rules to continue."));

        MinecraftServer server = player.level().getServer();
        if (server != null) {
            SCHEDULER.schedule(
                    () -> server.execute(() -> {
                        ServerPlayer current = server.getPlayerList().getPlayer(uuid);
                        if (current != null && !RulesManager.get().hasAccepted(uuid)) {
                            // Re-apply effects right before opening the menu in
                            // case they were cleared (e.g. /effect clear) while
                            // waiting, so the player can't sneak a peek.
                            RulesGuiOpener.applyFreezeEffects(current);
                            RulesGuiOpener.openMenu(current);
                        }
                    }),
                    guiDelayMs, TimeUnit.MILLISECONDS
            );
        }

        scheduleTimeout(player);
    }

    /** Cancels all pending timeouts and shuts the scheduler thread down. */
    public static void shutdown() {
        TIMEOUTS.values().forEach(f -> f.cancel(false));
        TIMEOUTS.clear();
        SCHEDULER.shutdownNow();
    }

    private static void scheduleTimeout(ServerPlayer player) {
        int seconds = RulesConfig.getAcceptTimeoutSeconds();
        if (seconds <= 0) return;

        UUID uuid = player.getUUID();
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        // Reset any previous timeout (e.g. a reload-triggered re-open).
        cancelTimeout(uuid);

        ScheduledFuture<?> future = SCHEDULER.schedule(() -> {
            // Bounce back to the server thread before interacting with a player.
            server.execute(() -> {
                TIMEOUTS.remove(uuid);
                ServerPlayer current = server.getPlayerList().getPlayer(uuid);
                if (current == null) return;
                if (RulesManager.get().hasAccepted(uuid)) return;

                current.connection.disconnect(Component.literal(RulesConfig.getTimeoutKickMessage()));
                RulesManager.get().clearPending(uuid);
            });
        }, seconds, TimeUnit.SECONDS);

        TIMEOUTS.put(uuid, future);
    }

    private static void cancelTimeout(UUID uuid) {
        ScheduledFuture<?> future = TIMEOUTS.remove(uuid);
        if (future != null) future.cancel(false);
    }
}

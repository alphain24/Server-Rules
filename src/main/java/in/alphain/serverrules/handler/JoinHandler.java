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
 * Handles player join and disconnect events.
 *
 * When a player joins:
 * <ul>
 *   <li>If they already accepted the rules, nothing happens.</li>
 *   <li>Otherwise they are marked as "pending", the chest GUI is opened
 *       via the Inventory Menu mod, and a timeout is scheduled.</li>
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

    private JoinHandler() {}

    public static void onPlayerJoin(ServerPlayer player) {
        RulesManager manager = RulesManager.get();
        UUID uuid = player.getUUID();

        if (manager.hasAccepted(uuid)) {
            ServerRules.LOGGER.debug("[ServerRules] {} has already accepted the rules.", player.getGameProfile().name());
            return;
        }

        manager.markPending(uuid);
        ServerRules.LOGGER.info("[ServerRules] {} has not accepted the rules - opening GUI.", player.getGameProfile().name());

        MinecraftServer server = player.level().getServer();
        if (server != null) {
            // Open GUI after a short delay so the client has finished
            // receiving inventory packets and is ready to display the menu.
            SCHEDULER.schedule(
                    () -> server.execute(() -> {
                        ServerPlayer current = server.getPlayerList().getPlayer(uuid);
                        if (current != null && !RulesManager.get().hasAccepted(uuid)) {
                            RulesGuiOpener.open(current);
                        }
                    }),
                    500, TimeUnit.MILLISECONDS
            );
        }

        scheduleTimeout(player);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        RulesManager.get().clearPending(uuid);
        cancelTimeout(uuid);
    }

    public static void onPlayerAccepted(ServerPlayer player) {
        cancelTimeout(player.getUUID());
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

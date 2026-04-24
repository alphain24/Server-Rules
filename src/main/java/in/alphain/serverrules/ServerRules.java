package in.alphain.serverrules;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import in.alphain.serverrules.command.RulesCommand;
import in.alphain.serverrules.config.RulesConfig;
import in.alphain.serverrules.datapack.DatapackGenerator;
import in.alphain.serverrules.handler.JoinHandler;
import in.alphain.serverrules.handler.RestrictionHandler;
import in.alphain.serverrules.storage.RulesManager;

/**
 * Main mod entrypoint for the Server Rules mod.
 *
 * This mod runs entirely on the dedicated server and requires players
 * to accept a set of rules (via a chest GUI provided by the Inventory
 * Menu mod) before they can interact with the world.
 */
public class ServerRules implements DedicatedServerModInitializer {
    public static final String MOD_ID = "server-rules";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer serverInstance;

    /**
     * Whether the datapack folder was newly written during this launch.
     * When {@code true} we re-scan the pack repository and re-enable the
     * pack on SERVER_STARTED so the menu registers without a manual
     * {@code /reload}. This also heals worlds whose {@code pack.mcmeta}
     * previously contained the broken {@code pack_format: 101.1} float
     * and got shoved into {@code level.dat}'s disabled-packs list.
     */
    private static boolean datapackFreshlyWritten;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[ServerRules] Initializing Server Rules mod...");

        // Load config and accepted players list on server start.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            serverInstance = server;
            RulesConfig.load();
            RulesManager.get().load();
            datapackFreshlyWritten = DatapackGenerator.generateIfMissing(server);
            LOGGER.info("[ServerRules] Server Rules initialized successfully.");
        });

        // Once the server is fully started, make sure the datapack we
        // just wrote is (a) visible to the pack repository and (b) in
        // the selected (enabled) list. This is critical because worlds
        // which ran the buggy 1.0.0 build have "Server Rules" written
        // to level.dat's `DataPacks.Disabled` due to the invalid
        // pack.mcmeta it previously generated.
        ServerLifecycleEvents.SERVER_STARTED.register(ServerRules::ensureDatapackEnabled);

        // Persist accepted players on shutdown.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RulesManager.get().save();
            JoinHandler.shutdown();
        });

        // Register the `/rules` command tree.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RulesCommand.register(dispatcher);
        });

        // Player join/disconnect hooks.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            JoinHandler.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            JoinHandler.onPlayerDisconnect(handler.getPlayer());
        });

        // Register restriction hooks (movement, chat, interaction, inventory).
        RestrictionHandler.register();
    }

    public static MinecraftServer getServer() {
        return serverInstance;
    }

    /**
     * Rescans the datapack folder and enables the mod's pack if it is
     * currently disabled (or missing from the selection). Triggers a
     * resource reload only when the selection actually changes so we
     * don't stutter the server on every startup.
     */
    private static void ensureDatapackEnabled(MinecraftServer server) {
        String packId = DatapackGenerator.getPackId();
        PackRepository repo = server.getPackRepository();

        // Re-scan in case we just wrote the datapack for the first time
        // during SERVER_STARTING (after the initial startup scan).
        if (datapackFreshlyWritten) {
            repo.reload();
        }

        if (!repo.getAvailableIds().contains(packId)) {
            LOGGER.warn("[ServerRules] Datapack '{}' not found after reload — skipping auto-enable.", packId);
            return;
        }

        if (repo.getSelectedIds().contains(packId)) {
            LOGGER.debug("[ServerRules] Datapack '{}' is already enabled.", packId);
            return;
        }

        // Build a new selection that preserves the existing enabled packs
        // and appends ours at the end (lowest priority, but that's fine —
        // nothing else registers a `server_rules:rules` menu).
        List<String> newSelection = new ArrayList<>(repo.getSelectedIds());
        newSelection.add(packId);

        LOGGER.info("[ServerRules] Enabling datapack '{}' and reloading resources...", packId);
        server.reloadResources(newSelection).exceptionally(throwable -> {
            LOGGER.error("[ServerRules] Failed to reload resources with '{}' enabled.", packId, throwable);
            return null;
        });
    }
}

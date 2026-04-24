package in.alphain.serverrules;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public void onInitializeServer() {
        LOGGER.info("[ServerRules] Initializing Server Rules mod...");

        // Load config and accepted players list on server start.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            serverInstance = server;
            RulesConfig.load();
            RulesManager.get().load();
            DatapackGenerator.generateIfMissing(server);
            LOGGER.info("[ServerRules] Server Rules initialized successfully.");
        });

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
}

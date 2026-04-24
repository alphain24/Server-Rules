package in.alphain.serverrules.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.alphain.serverrules.ServerRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles the main mod configuration loaded from
 * {@code config/serverrules/config.json}.
 *
 * <p>The chest GUI itself is defined entirely by the generated
 * datapack at {@code <world>/datapacks/Server Rules/data/server_rules/menu/rules.json}
 * — this config file only controls server-side behaviour (timeouts,
 * kick/restriction messages).
 */
public class RulesConfig {
    private static final Path CONFIG_DIR = Paths.get("config", "serverrules");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Time in seconds a player has to accept the rules before being kicked. */
    private static int acceptTimeoutSeconds = 120;

    /** Kick message shown when the acceptance timer expires. */
    private static String timeoutKickMessage = "You did not accept the server rules in time.";

    /** Kick message shown when a player declines the rules. */
    private static String declineKickMessage = "You must accept the server rules to play on this server.";

    /** Chat message shown when a restricted player tries to do something. */
    private static String restrictionMessage = "§cYou must accept the server rules before you can do this. Use §e/rules§c to view them.";

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(CONFIG_FILE)) {
                writeDefault();
                ServerRules.LOGGER.info("[ServerRules] Created default config at {}", CONFIG_FILE);
                return;
            }

            String content = Files.readString(CONFIG_FILE);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (json.has("acceptTimeoutSeconds")) acceptTimeoutSeconds = json.get("acceptTimeoutSeconds").getAsInt();
            if (json.has("timeoutKickMessage")) timeoutKickMessage = json.get("timeoutKickMessage").getAsString();
            if (json.has("declineKickMessage")) declineKickMessage = json.get("declineKickMessage").getAsString();
            if (json.has("restrictionMessage")) restrictionMessage = json.get("restrictionMessage").getAsString();
        } catch (IOException e) {
            ServerRules.LOGGER.error("[ServerRules] Failed to load config.", e);
        }
    }

    private static void writeDefault() throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("acceptTimeoutSeconds", acceptTimeoutSeconds);
        json.addProperty("timeoutKickMessage", timeoutKickMessage);
        json.addProperty("declineKickMessage", declineKickMessage);
        json.addProperty("restrictionMessage", restrictionMessage);
        Files.writeString(CONFIG_FILE, GSON.toJson(json));
    }

    // Getters
    public static int getAcceptTimeoutSeconds() { return acceptTimeoutSeconds; }
    public static String getTimeoutKickMessage() { return timeoutKickMessage; }
    public static String getDeclineKickMessage() { return declineKickMessage; }
    public static String getRestrictionMessage() { return restrictionMessage; }
}

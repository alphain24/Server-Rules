package in.alphain.serverrules.datapack;

import in.alphain.serverrules.ServerRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the Inventory Menu datapack used by this mod.
 *
 * The pack is laid out as:
 * <pre>
 * &lt;world&gt;/datapacks/Server Rules/
 *   pack.mcmeta
 *   data/server_rules/menu/rules.json
 * </pre>
 *
 * The pack is written <em>once</em> on first install; if the
 * {@code pack.mcmeta} marker already exists the generator is a
 * no-op so server owners can freely hand-edit the JSON without
 * having their edits overwritten on subsequent restarts.
 *
 * To force a rebuild, delete the {@code Server Rules} folder
 * and restart the server.
 */
public final class DatapackGenerator {

    /** Folder name of the generated datapack inside {@code <world>/datapacks/}. */
    public static final String DATAPACK_NAME = "Server Rules";

    /** Resource-location namespace under {@code data/}. */
    public static final String MENU_NAMESPACE = "server_rules";

    /** Menu file name (without {@code .json}) under {@code data/<ns>/menu/}. */
    public static final String MENU_ID = "rules";

    /** Pack format used for the generated {@code pack.mcmeta}. */
    private static final int PACK_FORMAT = 101;

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "pack_format": 101.1,
                "description": "Server Rules chest GUI."
              }
            }
            """.formatted(PACK_FORMAT);

    /** Default menu definition written on first install (Inventory Menu schema). */
    private static final String DEFAULT_RULES_JSON = """
            {
              "name": {
                "text": "Server Rules",
                "color": "dark_blue",
                "bold": true
              },
              "items": [
                {
                  "type": "item",
                  "slot": [2, 2],
                  "item": {
                    "id": "minecraft:enchanted_book",
                    "components": {
                      "custom_name": "§bChat Rules",
                      "lore": [
                        "§7• No Spamming & Rioting",
                        "§7• No Harassing & Abusing Others",
                        "§7• No Sharing Others Information",
                        "§7• No Advertising or Promotion",
                        "§7• No Racism, Discrimination or Hate Speech",
                        "§7• No Death Threats & Suicide Encouragement"
                      ]
                    }
                  }
                },
                {
                  "type": "item",
                  "slot": [2, 5],
                  "item": {
                    "id": "minecraft:enchanted_book",
                    "components": {
                      "custom_name": "§aServer Rules",
                      "lore": [
                        "§7• Be respectful",
                        "§7• No exploiting bugs",
                        "§7• Follow staff instructions"
                      ]
                    }
                  }
                },
                {
                  "type": "item",
                  "slot": [2, 8],
                  "item": {
                    "id": "minecraft:enchanted_book",
                    "components": {
                      "custom_name": "§eGameplay Rules",
                      "lore": [
                        "§7• No cheating",
                        "§7• No unfair advantages",
                        "§7• Play fair"
                      ]
                    }
                  }
                }
              ]
            }
            """;

    private DatapackGenerator() {}

    /**
     * Generates the datapack in the world's datapack folder only if it
     * does not already exist. Safe to call on every startup.
     */
    public static void generateIfMissing(MinecraftServer server) {
        try {
            Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DATAPACK_NAME);
            Path marker = datapackDir.resolve("pack.mcmeta");

            if (Files.exists(marker)) {
                ServerRules.LOGGER.debug("[ServerRules] Datapack already present at {}, skipping generation.", datapackDir);
                return;
            }

            writeDatapack(datapackDir);
            ServerRules.LOGGER.info("[ServerRules] Datapack generated at {}", datapackDir);
        } catch (IOException e) {
            ServerRules.LOGGER.error("[ServerRules] Failed to generate datapack.", e);
        }
    }

    /**
     * Forces a full rewrite of the datapack files. Intended for an
     * admin-triggered rebuild via a future command or test harness.
     */
    public static void regenerate(MinecraftServer server) {
        try {
            Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DATAPACK_NAME);
            writeDatapack(datapackDir);
            ServerRules.LOGGER.info("[ServerRules] Datapack regenerated at {}", datapackDir);
        } catch (IOException e) {
            ServerRules.LOGGER.error("[ServerRules] Failed to regenerate datapack.", e);
        }
    }

    private static void writeDatapack(Path datapackDir) throws IOException {
        // pack.mcmeta at the datapack root.
        Files.createDirectories(datapackDir);
        Files.writeString(datapackDir.resolve("pack.mcmeta"), PACK_MCMETA);

        // data/<namespace>/menu/<id>.json — the Inventory Menu definition.
        Path menuDir = datapackDir
                .resolve("data")
                .resolve(MENU_NAMESPACE)
                .resolve("menu");
        Files.createDirectories(menuDir);
        Files.writeString(menuDir.resolve(MENU_ID + ".json"), DEFAULT_RULES_JSON);
    }
}

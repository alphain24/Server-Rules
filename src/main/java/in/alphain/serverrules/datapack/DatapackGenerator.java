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
 * {@code pack.mcmeta} is re-written on every startup so pack-format
 * changes (e.g. a Minecraft version bump) are picked up automatically.
 * The menu JSON ({@code rules.json}) is only written on first install
 * so server owners can freely hand-edit it without having their edits
 * overwritten on subsequent restarts.
 *
 * To force a full rebuild, delete the {@code Server Rules} folder
 * and restart the server.
 */
public final class DatapackGenerator {

    /** Folder name of the generated datapack inside {@code <world>/datapacks/}. */
    public static final String DATAPACK_NAME = "Server Rules";

    /** Resource-location namespace under {@code data/}. */
    public static final String MENU_NAMESPACE = "server_rules";

    /** Menu file name (without {@code .json}) under {@code data/<ns>/menu/}. */
    public static final String MENU_ID = "rules";

    // ---------------------------------------------------------------------
    // Pack format for Minecraft 26.1.1
    //
    // Starting with the 26.x series, Minecraft switched from a single
    // integer `pack_format` to a pair of `max_format` / `min_format`
    // fields where the minor version is expressed as a two-element
    // array: [major, minor]. For 26.1.1 the correct value is
    // `min_format: [101, 1]` (i.e. 101.1) with `max_format: 101`.
    // ---------------------------------------------------------------------
    private static final int MAX_PACK_FORMAT = 101;
    private static final int MIN_PACK_FORMAT_MAJOR = 101;
    private static final int MIN_PACK_FORMAT_MINOR = 1;

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "description": "Server Rules chest GUI.",
                "max_format": %d,
                "min_format": [%d, %d]
              }
            }
            """.formatted(MAX_PACK_FORMAT, MIN_PACK_FORMAT_MAJOR, MIN_PACK_FORMAT_MINOR);

    /**
     * Default menu definition written on first install (Inventory Menu schema).
     *
     * Layout (1-indexed {@code [row, col]} inside a 3×9 chest):
     * <pre>
     *   Row 2 — three enchanted-book rule categories.
     *   Row 3 — player-head Accept (alphain) and Decline (_3RACHA) buttons
     *           that run {@code /rules accept} and {@code /rules decline}
     *           via Inventory Menu's "command" action.
     * </pre>
     */
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
                },
                {
                  "type": "item",
                  "slot": [3, 3],
                  "item": {
                    "id": "minecraft:player_head",
                    "components": {
                      "profile": {"name": "alphain"},
                      "custom_name": "§a§lACCEPT",
                      "lore": [
                        "§7Click to §aaccept§7 the server rules",
                        "§7and join the Angel's Server."
                      ]
                    }
                  },
                  "action": {
                    "type": "command",
                    "as_player": true,
                    "silent": true,
                    "command": "rules accept"
                  }
                },
                {
                  "type": "item",
                  "slot": [3, 7],
                  "item": {
                    "id": "minecraft:player_head",
                    "components": {
                      "profile": {"name": "_3RACHA"},
                      "custom_name": "§c§lDECLINE",
                      "lore": [
                        "§7Click to §cdecline§7 the server rules.",
                        "§cYou will be disconnected from the server."
                      ]
                    }
                  },
                  "action": {
                    "type": "command",
                    "as_player": true,
                    "silent": true,
                    "command": "rules decline"
                  }
                }
              ]
            }
            """;

    private DatapackGenerator() {}

    /**
     * Generates the datapack in the world's datapack folder.
     *
     * <p>{@code pack.mcmeta} is always refreshed so pack-format bumps
     * survive a Minecraft update. {@code rules.json} is only written
     * on first install to preserve operator edits.
     *
     * @return {@code true} if any file was written (caller should
     *         reload resources / re-enable the datapack), or
     *         {@code false} if everything was already up-to-date.
     */
    public static boolean generateIfMissing(MinecraftServer server) {
        boolean wrote = false;
        try {
            Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DATAPACK_NAME);
            Files.createDirectories(datapackDir);

            // Always refresh pack.mcmeta so pack-format stays in sync with the server.
            Path packMeta = datapackDir.resolve("pack.mcmeta");
            boolean needsWrite = !Files.exists(packMeta) || !Files.readString(packMeta).equals(PACK_MCMETA);
            if (needsWrite) {
                Files.writeString(packMeta, PACK_MCMETA);
                wrote = true;
                ServerRules.LOGGER.info("[ServerRules] Wrote pack.mcmeta (max_format={}, min_format=[{}, {}]).",
                        MAX_PACK_FORMAT, MIN_PACK_FORMAT_MAJOR, MIN_PACK_FORMAT_MINOR);
            }

            // Only create the menu file on first install.
            Path menuDir = datapackDir
                    .resolve("data")
                    .resolve(MENU_NAMESPACE)
                    .resolve("menu");
            Files.createDirectories(menuDir);
            Path menuFile = menuDir.resolve(MENU_ID + ".json");
            if (!Files.exists(menuFile)) {
                Files.writeString(menuFile, DEFAULT_RULES_JSON);
                wrote = true;
                ServerRules.LOGGER.info("[ServerRules] Datapack generated at {}", datapackDir);
            } else {
                ServerRules.LOGGER.debug("[ServerRules] Existing menu file preserved at {}", menuFile);
            }
        } catch (IOException e) {
            ServerRules.LOGGER.error("[ServerRules] Failed to generate datapack.", e);
        }
        return wrote;
    }

    /** Full pack id as seen by Minecraft's {@code /datapack} command ({@code "file/<name>"}). */
    public static String getPackId() {
        return "file/" + DATAPACK_NAME;
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

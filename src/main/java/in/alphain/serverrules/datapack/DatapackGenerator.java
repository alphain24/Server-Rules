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
    // Starting with the 26.x series (Data Pack v101, confirmed in the
    // 26.1 Pre-Release 1 changelog) Minecraft expresses pack versions
    // as [major, minor] pairs. Both `max_format` AND `min_format` must
    // be arrays — writing `max_format` as a bare integer (e.g. 101) is
    // silently interpreted as [101, 0] which is LESS than the current
    // runtime format [101, 1], causing the server to reject the pack
    // with "this pack was made for a newer/older version of Minecraft"
    // and park it in `level.dat`'s disabled list.
    //
    // Reference: vanilla 26.1 data-packs and the Unbreakable datapack
    // commit 0df0b8a both use [101, 1] for BOTH fields.
    // ---------------------------------------------------------------------
    private static final int PACK_FORMAT_MAJOR = 101;
    private static final int PACK_FORMAT_MINOR = 1;

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "description": "Server Rules chest GUI.",
                "min_format": [%d, %d],
                "max_format": [%d, %d]
              }
            }
            """.formatted(
                    PACK_FORMAT_MAJOR, PACK_FORMAT_MINOR,
                    PACK_FORMAT_MAJOR, PACK_FORMAT_MINOR);

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
                "bold": true,
                "italic": false
              },
              "rows": 3,
              "items": [
                {
                  "type": "item",
                  "slot": [2, 2],
                  "sound": "click",
                  "item": {
                    "id": "minecraft:enchanted_book",
                    "components": {
                      "minecraft:custom_name": {
                        "text": "Chat Rules",
                        "color": "aqua",
                        "bold": true,
                        "italic": false
                      },
                      "minecraft:lore": [
                        {"text": "\\u2022 No Spamming & Rioting", "color": "gray", "italic": false},
                        {"text": "\\u2022 No Harassing & Abusing Others", "color": "gray", "italic": false},
                        {"text": "\\u2022 No Sharing Others Information", "color": "gray", "italic": false},
                        {"text": "\\u2022 No Advertising or Promotion", "color": "gray", "italic": false},
                        {"text": "\\u2022 No Racism, Discrimination or Hate Speech", "color": "gray", "italic": false},
                        {"text": "\\u2022 No Death Threats & Suicide Encouragement", "color": "gray", "italic": false}
                      ]
                    }
                  }
                },
                {
                  "type": "item",
                  "slot": [2, 5],
                  "sound": "click",
                  "item": {
                    "id": "minecraft:enchanted_book",
                    "components": {
                      "minecraft:custom_name": {
                        "text": "Server Rules",
                        "color": "green",
                        "bold": true,
                        "italic": false
                      },
                      "minecraft:lore": [
                        {"text": "\\u2022 Be respectful", "color": "gray", "italic": false},
                        {"text": "\\u2022 No exploiting bugs", "color": "gray", "italic": false},
                        {"text": "\\u2022 Follow staff instructions", "color": "gray", "italic": false}
                      ]
                    }
                  }
                },
                {
                  "type": "item",
                  "slot": [2, 8],
                  "sound": "click",
                  "item": {
                    "id": "minecraft:enchanted_book",
                    "components": {
                      "minecraft:custom_name": {
                        "text": "Gameplay Rules",
                        "color": "yellow",
                        "bold": true,
                        "italic": false
                      },
                      "minecraft:lore": [
                        {"text": "\\u2022 No cheating", "color": "gray", "italic": false},
                        {"text": "\\u2022 No unfair advantages", "color": "gray", "italic": false},
                        {"text": "\\u2022 Play fair", "color": "gray", "italic": false}
                      ]
                    }
                  }
                },
                {
                  "type": "item",
                  "slot": [3, 3],
                  "sound": ["success", "fail"],
                  "item": {
                    "id": "minecraft:player_head",
                    "components": {
                      "minecraft:profile": {"name": "alphain"},
                      "minecraft:custom_name": {
                        "text": "ACCEPT",
                        "color": "green",
                        "bold": true,
                        "italic": false
                      },
                      "minecraft:lore": [
                        {"text": "Click to accept the server rules", "color": "gray", "italic": false},
                        {"text": "and join the Angel's Server.", "color": "gray", "italic": false}
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
                  "sound": ["click", "fail"],
                  "item": {
                    "id": "minecraft:player_head",
                    "components": {
                      "minecraft:profile": {"name": "_3RACHA"},
                      "minecraft:custom_name": {
                        "text": "DECLINE",
                        "color": "red",
                        "bold": true,
                        "italic": false
                      },
                      "minecraft:lore": [
                        {"text": "Click to decline the server rules.", "color": "gray", "italic": false},
                        {"text": "You will be disconnected from the server.", "color": "red", "italic": false}
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
                ServerRules.LOGGER.info("[ServerRules] Wrote pack.mcmeta (format=[{}, {}]).",
                        PACK_FORMAT_MAJOR, PACK_FORMAT_MINOR);
            }

            // The menu JSON is normally only created on first install so
            // server owners can hand-edit it. However, worlds that ran
            // earlier builds have a rules.json that predates the
            // accept/decline player-head buttons — detect that case by
            // probing for the two click-command strings and force-upgrade.
            // Admins who want to opt out of this migration can create an
            // empty `.customized` marker file next to rules.json.
            Path menuDir = datapackDir
                    .resolve("data")
                    .resolve(MENU_NAMESPACE)
                    .resolve("menu");
            Files.createDirectories(menuDir);
            Path menuFile = menuDir.resolve(MENU_ID + ".json");
            Path customizedMarker = menuDir.resolve(".customized");

            if (!Files.exists(menuFile)) {
                Files.writeString(menuFile, DEFAULT_RULES_JSON);
                wrote = true;
                ServerRules.LOGGER.info("[ServerRules] Datapack generated at {}", datapackDir);
            } else if (!Files.exists(customizedMarker)) {
                String existing = Files.readString(menuFile);
                boolean hasAccept = existing.contains("rules accept");
                boolean hasDecline = existing.contains("rules decline");
                if (!hasAccept || !hasDecline) {
                    Files.writeString(menuFile, DEFAULT_RULES_JSON);
                    wrote = true;
                    ServerRules.LOGGER.info(
                            "[ServerRules] Upgraded legacy menu file at {} (missing accept/decline buttons). " +
                            "Create an empty '.customized' file next to it to skip future auto-upgrades.",
                            menuFile);
                } else {
                    ServerRules.LOGGER.debug("[ServerRules] Existing menu file preserved at {}", menuFile);
                }
            } else {
                ServerRules.LOGGER.debug("[ServerRules] Menu file marked customized — preserving {}", menuFile);
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

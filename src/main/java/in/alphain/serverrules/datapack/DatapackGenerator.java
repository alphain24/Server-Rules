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
    // Pack format range
    //
    // Starting with the 26.x series (Data Pack v101, confirmed in the
    // 26.1 Pre-Release 1 changelog) Minecraft expresses pack versions
    // as [major, minor] pairs. Both `max_format` AND `min_format` must
    // be arrays — writing a bare integer (e.g. 101) is silently
    // interpreted as [101, 0] which is LESS than the current runtime
    // format [101, 1], causing the server to reject the pack with
    // "this pack was made for a newer/older version of Minecraft" and
    // park it in `level.dat`'s disabled list.
    //
    // We widen the range to [94, 0] .. [101, 1] so the same datapack
    // loads cleanly on every 1.21+ release up through 26.1.1, matching
    // the compatibility band Mojang uses in the vanilla built-ins.
    // ---------------------------------------------------------------------
    private static final int MIN_PACK_FORMAT_MAJOR = 94;
    private static final int MIN_PACK_FORMAT_MINOR = 0;
    private static final int MAX_PACK_FORMAT_MAJOR = 101;
    private static final int MAX_PACK_FORMAT_MINOR = 1;

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "description": "Server Rules chest GUI.",
                "min_format": [%d, %d],
                "max_format": [%d, %d]
              }
            }
            """.formatted(
                    MIN_PACK_FORMAT_MAJOR, MIN_PACK_FORMAT_MINOR,
                    MAX_PACK_FORMAT_MAJOR, MAX_PACK_FORMAT_MINOR);

    // ---------------------------------------------------------------------
    // Baked player-head profiles for the Accept / Decline buttons.
    //
    // We embed the full profile (name + UUID int-array + signed textures
    // property) rather than a bare {name: "..."} so the heads render
    // instantly, survive on offline-mode / firewalled servers, and can't
    // get "stuck" as Steve if Mojang's session server is slow or blocked.
    // The values below came from:
    //   GET  api.mojang.com/users/profiles/minecraft/{name}           -> UUID
    //   GET  sessionserver.mojang.com/session/minecraft/profile/{uuid}?unsigned=false
    //
    // If either player changes their skin, replace the corresponding
    // _TEXTURE / _SIGNATURE constants with the fresh values from that
    // same endpoint.
    // ---------------------------------------------------------------------

    // Alphain_  UUID 39657019-744d-49e9-832b-edf4daec6aba
    private static final String ACCEPT_HEAD_NAME = "Alphain_";
    private static final String ACCEPT_HEAD_ID =
            "[962949145, 1951222249, -2094273036, -622040390]";
    private static final String ACCEPT_HEAD_TEXTURE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc3NzAyNTg4NTcxMiwKICAicHJvZmlsZUlkIiA6ICIzOTY1NzAxOTc0NGQ0OWU5ODMyYmVkZjRkYWVjNmFiYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbHBoYWluXyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMTU3MmRkYjVhMGUxY2M5NGFhYjAxZTk3MmU0MWZkZmNiMmYwODg0MzdmNTMzODkyN2ZlMWNjNmUxZGE3MTZmIgogICAgfQogIH0KfQ==";
    private static final String ACCEPT_HEAD_SIGNATURE =
            "IZw4rfb4XuOqeA/z4Sm7Ca9a2zTHIl9G65NWRFLgMA8phVH6ecL6tUawc3ddHsMH+lWgnygWpGfqghXBvjgF6PEgdwsrm4IWFIlj8rTAYpgSaCbSUPk+7QDoQuOaQdzE31E/BmfJyqNOrPCAUxzGpZ+5TJCHpBa6b3DU82GmME9uk5X98J7603vRCZXmfEQ7ESYsnejGE+sN4kvcPrmspKpuCZLjH9N2MkYlwIzmZ402xfbiNOWmiGsIc4/i2Iw61owwX8FNbZZlpfd/168vG+q0hrpQ2tZTHBF5F1mtKIYbU7jRMBPG7da/1pVihjJvya/jEvG9q2NhwUdOVDDgZRMPCEEu5yzZOqJIkblKEXQsjtdHHFaZkkWVk4U5Qh8Bme7y4OqUnYU/B8DYBM9wZ2i9+JV1yfZHLvNF6qB1725kXW9Q3Vv5/VMQrmqe11nQcAN6MyhPBGYEtj8UzODBWzi5R6CMuW/WVhSAlqjc91q5caW+9PW7dIolmkglrJtLrbe1slk/cRWQ1ruXyB15Y4U7KDKV/F36FfU5dBV0Mrwa1oIwJ7rntlN6QvHNoRV35T2yFR1Qj4eUk8zbXigV50ndCgIJvbBPfGOlNfoGEtwCiTaHHApt8pc/qI0CJjCMinu9NwhmSQTJLoWdjO2S1dSPbwq8VvHytFsjIwgtK2g=";

    // _3RACHA  UUID b2c7d5dd-482d-4a61-bf38-004a8032b6a6
    private static final String DECLINE_HEAD_NAME = "_3RACHA";
    private static final String DECLINE_HEAD_ID =
            "[-1295526435, 1210927713, -1086848950, -2144160090]";
    private static final String DECLINE_HEAD_TEXTURE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc3NzAyNTg4NTY2OCwKICAicHJvZmlsZUlkIiA6ICJiMmM3ZDVkZDQ4MmQ0YTYxYmYzODAwNGE4MDMyYjZhNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJfM1JBQ0hBIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzIyMWE3MmRlOGE2MmY5N2E0M2JlMzE2ZTgwNjk1MjRhMjQ0OGYyODg5MzAyNWZhNmQ4NTliYmRjYmU2MDE3YjYiCiAgICB9LAogICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzIzNDBjMGUwM2RkMjRhMTFiMTVhOGIzM2MyYTdlOWUzMmFiYjIwNTFiMjQ4MWQwYmE3ZGVmZDYzNWNhN2E5MzMiCiAgICB9CiAgfQp9";
    private static final String DECLINE_HEAD_SIGNATURE =
            "StQupvLeq4CJjMfLJAru4aOmslgEwSjfjB0dGyOp1BRLRr+KkiVlvOZ1uT5zojHf4+xysZ0vULBS0cbH/0iPQmTXojQugQWN6Ue3g8UNFW2XciKRoIyQBHNsew+pZ11arRVUiYfAoQ1sxXE2+cRu10Bdm4+y09jgX+7oxidssUUmwk/oT4IiRiE5haQ1LB7yJjlMu+rq1Bja679aMjwligFLkvqx+40YXNJ3oMNyAhrbPHT68pIeGyb/tgKmFkMlv3GX6UeyXzp9NbuyMxHW91vnp6jHr1/AkbdgSaMnPdQEbYRCYB2Vc37a9HwHANjUkNkZVPCZvEacDZr6Q653qaqbuyqfeRoJAQzUY/AwhbzId2HGyOsufLI4P7jl9R92hqP9ynYb5lrmyHFsJenPhHjxADXvMFMPVjxirJ/9vReqD1JsxePWcNFj26c09s1YBnfR7gIbkKPFhity0uVdJj8AhGzw4+M1IAwbAWDiXzd80BG248K7qHIGRbOLekx0hdqmFsfLXfhvd26HfH+AaTte2J23hRNRjdKpOVnPZ0t9pqOCCbBzONHuHdpps7YiP4JhahtDvaVVia+cXdO5Vq8SGTBE3buniC/3O2VO+lyH+/1srCWcmR2CJYmPYB9oBUcdpIwMRjSRR9QCVCimQWJNvZ87n3INw62FsJ6AsNY=";

    /**
     * Default menu definition written on first install (Inventory Menu schema).
     *
     * Layout (1-indexed {@code [row, col]} inside a 3×9 chest):
     * <pre>
     *   Row 2 — three enchanted-book rule categories.
     *   Row 3 — player-head Accept (Alphain_) and Decline (_3RACHA) buttons
     *           that run {@code /rules accept} and {@code /rules decline}
     *           via Inventory Menu's "command" action.
     * </pre>
     */
    private static final String DEFAULT_RULES_JSON = ("""
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
                      "minecraft:profile": {
                        "name": "%s",
                        "id": %s,
                        "properties": [
                          {
                            "name": "textures",
                            "value": "%s",
                            "signature": "%s"
                          }
                        ]
                      },
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
                      "minecraft:profile": {
                        "name": "%s",
                        "id": %s,
                        "properties": [
                          {
                            "name": "textures",
                            "value": "%s",
                            "signature": "%s"
                          }
                        ]
                      },
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
            """).formatted(
                    ACCEPT_HEAD_NAME, ACCEPT_HEAD_ID, ACCEPT_HEAD_TEXTURE, ACCEPT_HEAD_SIGNATURE,
                    DECLINE_HEAD_NAME, DECLINE_HEAD_ID, DECLINE_HEAD_TEXTURE, DECLINE_HEAD_SIGNATURE);

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
                ServerRules.LOGGER.info("[ServerRules] Wrote pack.mcmeta (min=[{}, {}], max=[{}, {}]).",
                        MIN_PACK_FORMAT_MAJOR, MIN_PACK_FORMAT_MINOR,
                        MAX_PACK_FORMAT_MAJOR, MAX_PACK_FORMAT_MINOR);
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
                // A profile baked with a signed texture always contains the
                // word "signature". Earlier builds wrote {name:"alphain"}
                // stubs without it, which rely on Mojang resolution and
                // frequently render as the default Steve head. Treat those
                // as legacy and force an upgrade.
                boolean hasBakedHeads = existing.contains("\"signature\"");
                if (!hasAccept || !hasDecline || !hasBakedHeads) {
                    Files.writeString(menuFile, DEFAULT_RULES_JSON);
                    wrote = true;
                    ServerRules.LOGGER.info(
                            "[ServerRules] Upgraded legacy menu file at {} (missing accept/decline buttons or baked head textures). " +
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

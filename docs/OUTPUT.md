# Server Rules Mod — Documentation

A server-side Fabric mod that forces players to read and accept the server
rules (via a chest GUI provided by the **Inventory Menu** mod) before they
are allowed to interact with the world.

- **Mod ID:** `server-rules`
- **Package:** `in.alphain.serverrules`
- **Minecraft:** `26.1.1`
- **Fabric Loader:** `0.19.2` / **Fabric API:** `0.145.4+26.1.1`
- **Java:** `25`
- **Environment:** `server` (no client install needed)
- **Dependency:** [Inventory Menu](https://modrinth.com/mod/inventory-menu)

---

## Project Layout

The project uses the official [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod) template layout (branch `26.1`), adapted for a server-only mod:

```
server-rules/
├── build.gradle            # Fabric Loom build script (matches template)
├── gradle.properties       # Minecraft / Loader / API / Loom versions
├── settings.gradle         # Fabric maven + rootProject name
├── gradle/wrapper/         # Gradle 9.4.1 wrapper
├── .gitattributes
├── .gitignore
├── docs/OUTPUT.md
└── src/main/
    ├── java/in/alphain/serverrules/...
    └── resources/
        ├── fabric.mod.json
        └── assets/server-rules/lang/en_us.json
```

> Because the mod is server-only, the template's `src/client/` sourceset
> and the `splitEnvironmentSourceSets()` Loom switch are intentionally
> omitted. Everything else (plugin versions, Java 25 release target,
> `maven-publish`, `processResources` version expansion) mirrors the
> template.

---

## File Overview

| File | Purpose |
| --- | --- |
| `build.gradle` / `gradle.properties` / `settings.gradle` | Fabric Loom build, pinned to MC `26.1.1`, Loader `0.19.2`, Fabric API `0.145.4+26.1.1`, Java `25`. |
| `src/main/resources/fabric.mod.json` | Fabric mod metadata and entrypoints. |
| `src/main/resources/assets/server-rules/lang/en_us.json` | Translatable strings. |
| `src/main/java/in/alphain/serverrules/ServerRules.java` | Main entrypoint — wires up events, commands, and datapack generation. |
| `src/main/java/in/alphain/serverrules/config/RulesConfig.java` | Loads `config.json` and seeds a default `rules.json`. |
| `src/main/java/in/alphain/serverrules/storage/RulesManager.java` | Reads/writes `rules-accepted.txt` and tracks pending (restricted) players. |
| `src/main/java/in/alphain/serverrules/handler/JoinHandler.java` | Opens the GUI on join and schedules the 20-second acceptance timeout. |
| `src/main/java/in/alphain/serverrules/handler/RestrictionHandler.java` | Blocks movement-adjacent interaction, chat, and commands for restricted players. |
| `src/main/java/in/alphain/serverrules/command/RulesCommand.java` | Registers `/rules`, `/rules accept`, `/rules decline`, `/rules reload [player]`. |
| `src/main/java/in/alphain/serverrules/datapack/DatapackGenerator.java` | Generates the Inventory Menu datapack from `rules.json`. |
| `src/main/java/in/alphain/serverrules/datapack/RulesGuiOpener.java` | Opens the chest GUI (via `inv-menu open`) and applies movement-freeze effects. |

---

## How It Works

### 1. Server Startup

`ServerRules#onInitializeServer` registers lifecycle hooks. When the server
is starting:

1. `RulesConfig.load()` creates `config/serverrules/config.json` and
   `config/serverrules/rules.json` with sensible defaults if missing.
2. `RulesManager.get().load()` reads the accepted-players list from
   `config/serverrules/rules-accepted.txt` (one `uuid:username` per line).
3. `DatapackGenerator.generateIfMissing(server)` writes a datapack into the
   world's `datapacks/server-rules-gui` folder **only on first install**.
   If the folder already exists, it is left untouched so any manual edits
   are preserved. Delete the folder and restart to force a rebuild.

### 2. Player Joins

`ServerPlayConnectionEvents.JOIN` fires `JoinHandler.onPlayerJoin`:

- If the player is already in the accepted list → they join normally.
- Otherwise the player is added to an in-memory **pending** set,
  `RulesGuiOpener.open` runs `inv-menu open <player> server-rules:rules`,
  and a scheduled executor kicks them after
  `acceptTimeoutSeconds` (default `20s`) if they haven't accepted.

While pending:

- `Slowness 255`, `Jump Boost 128`, and a short `Blindness` effect
  effectively lock the player in place while they read the rules.
- `RestrictionHandler` blocks block breaking/placing, block/entity
  interaction, item use, chat, and any command that isn't `/rules`,
  `/rules accept`, `/rules decline`, or `/help`.

### 3. Accept / Decline

- `/rules accept` → `RulesManager.markAccepted`, cancels the timeout,
  clears the freeze effects, closes the GUI, and saves
  `rules-accepted.txt`.
- `/rules decline` → disconnects the player with the configured kick
  message.

### 4. Admin Commands

- `/rules reload` (op level 3) clears **all** accepted players. Every
  player must re-accept on their next join.
- `/rules reload <player>` removes only the specified player.

### 5. Rejoin

- Accepted players join without interruption.
- Players cleared via `/rules reload` are treated as new.

---

## Configuration

### `config/serverrules/config.json`

```json
{
  "acceptTimeoutSeconds": 20,
  "timeoutKickMessage": "You did not accept the server rules in time.",
  "declineKickMessage": "You must accept the server rules to play on this server.",
  "restrictionMessage": "§cYou must accept the server rules before you can do this. Use §e/rules§c to view them.",
  "guiTitle": "Server Rules",
  "guiRows": 6
}
```

### `config/serverrules/rules.json`

The source of truth for the GUI. Supported item `type`s:

- `written_book` — `title`, `author`, `pages` (array of raw strings).
- `button` — `material`, `name`, `lore`, `command` (run on click).
- anything else — decorative filler item.

Every item must declare a `slot` (0-based). Because the datapack is only
generated on first install, edits to `rules.json` do not take effect
until you delete `<world>/datapacks/server-rules-gui` and restart the
server.

### `config/serverrules/rules-accepted.txt`

Plain text, one line per accepted player:

```
b1d2c3e4-0000-0000-0000-000000000000:Steve
a9f8e7d6-0000-0000-0000-000000000000:Alex
```

The file is created automatically on first start and rewritten on every
change.

---

## Dependencies

- **Fabric Loader / Fabric API** — event hooks and command API.
- **Inventory Menu** — provides the chest GUI runtime used by the
  generated datapack. Install from Modrinth: <https://modrinth.com/mod/inventory-menu>.

---

## Notes for Server Owners

- The mod is **server-only**: clients do not need to install anything.
- The generated datapack at `<world>/datapacks/server-rules-gui` is
  written **once** on first install and can be committed to version
  control. You can customize the GUI by editing the generated datapack
  files directly, or by editing `config/serverrules/rules.json` and
  deleting the `server-rules-gui` folder so it is rebuilt on the next
  startup.
- The restriction system intentionally does not mutate world state, so
  uninstalling the mod restores normal behavior immediately.

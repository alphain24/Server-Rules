# Server Rules — Technical Reference

Complete developer/administrator documentation for the `server-rules` Fabric
mod. For a user-facing summary, install steps, and in-game flow, see the
[top-level README](../README.md).

- **Mod ID:** `server-rules`
- **Package:** `in.alphain.serverrules`
- **Entrypoint:** `in.alphain.serverrules.ServerRules` (server-only)
- **Minecraft:** `26.1.1`
- **Fabric Loader / API:** `0.19.2` / `0.145.4+26.1.1`
- **Java:** `25`
- **Required mod:** [Inventory Menu](https://modrinth.com/mod/inventory-menu) `>= 1.1.0`

---

## 1. Project Layout

Follows the [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod)
template (branch `26.1`) with the client sourceset removed because this is
a server-only mod.

```
server-rules/
├── build.gradle
├── gradle.properties              # MC / Loader / API / Loom / Java versions
├── settings.gradle
├── gradle/wrapper/                # Gradle 9.4.1 wrapper
├── docs/OUTPUT.md                 # this document
├── README.md
├── LICENSE                        # MIT
└── src/main/
    ├── java/in/alphain/serverrules/
    │   ├── ServerRules.java
    │   ├── command/RulesCommand.java
    │   ├── config/RulesConfig.java
    │   ├── datapack/
    │   │   ├── DatapackGenerator.java
    │   │   └── RulesGuiOpener.java
    │   ├── handler/
    │   │   ├── JoinHandler.java
    │   │   └── RestrictionHandler.java
    │   └── storage/RulesManager.java
    └── resources/
        ├── fabric.mod.json
        └── assets/server-rules/lang/en_us.json
```

---

## 2. File Responsibilities

| File | Responsibility |
| --- | --- |
| `ServerRules.java` | Mod entrypoint. Wires lifecycle events, registers commands, and owns `ensureDatapackEnabled(...)` which auto-selects the generated pack on `SERVER_STARTED`. |
| `command/RulesCommand.java` | Brigadier registration for the `/rules` tree and standalone `/rules-view`. |
| `config/RulesConfig.java` | Loads `config/serverrules/config.json`, seeds defaults. |
| `storage/RulesManager.java` | Singleton that tracks accepted (`UUID -> username`) and pending players. Persists to `rules-accepted.txt`. |
| `handler/JoinHandler.java` | Handles `JOIN` / `DISCONNECT`, schedules the delayed GUI open, applies freeze effects, and schedules the accept-timeout kick. |
| `handler/RestrictionHandler.java` | Fabric API callbacks that block everything a pending player tries to do. |
| `datapack/DatapackGenerator.java` | Writes `<world>/datapacks/Server Rules/pack.mcmeta` and `data/server_rules/menu/rules.json`. Owns the pack-format constants and the default menu JSON. |
| `datapack/RulesGuiOpener.java` | Applies status effects and dispatches the Inventory Menu `/menu server_rules:rules` command as the player. |

---

## 3. Lifecycle

### 3.1 `SERVER_STARTING`

```java
RulesConfig.load();
RulesManager.get().load();
datapackFreshlyWritten = DatapackGenerator.generateIfMissing(server);
```

`generateIfMissing` returns `true` if it wrote any file (new install, pack-format
refresh, or legacy menu upgrade). The flag is read again on `SERVER_STARTED`.

### 3.2 `SERVER_STARTED`

`ServerRules.ensureDatapackEnabled(server)`:

1. If `datapackFreshlyWritten`, call `packRepository.reload()` so the just-written
   pack becomes visible.
2. If the pack is missing from `getAvailableIds()`, log a warning and return.
3. If it's already in `getSelectedIds()`, return (no-op on healthy restarts).
4. Otherwise build a new selection list (existing selection + `file/Server Rules`)
   and call `server.reloadResources(newSelection)`.

This is what heals worlds whose `level.dat` has the pack on
`DataPacks.Disabled` from an earlier broken `pack.mcmeta`.

### 3.3 `JOIN`

`JoinHandler.onPlayerJoin(player)`:

- If `RulesManager.hasAccepted(uuid)` → return.
- Otherwise call `beginRulesFlow(player, JOIN_GUI_DELAY_MS = 3000ms)`.

`beginRulesFlow`:

1. `RulesManager.markPending(uuid)` — registers the player as restricted.
2. `RulesGuiOpener.applyFreezeEffects(player)` — Slowness 255, Jump Boost 128,
   Blindness, all for `FREEZE_DURATION_TICKS = 20 * 60 * 10` (10 minutes).
3. Send the reminder chat message.
4. Schedule `openMenu` on the server thread after the given delay.
5. `scheduleTimeout(player)` — kicks the player if they don't accept in
   `RulesConfig.getAcceptTimeoutSeconds()` seconds.

### 3.4 Accept

`RulesCommand.executeAccept`:

```
RulesManager.markAccepted(player) -> writes rules-accepted.txt
JoinHandler.onPlayerAccepted(player) -> cancels the pending timeout
RulesGuiOpener.clearEffects(player) -> removes slowness/jump/blindness
player.closeContainer()
```

### 3.5 Decline / Timeout

- **Decline**: `player.connection.disconnect(declineKickMessage)`.
- **Timeout**: scheduled task runs on the scheduler thread, bounces back to
  the server thread via `server.execute(...)`, and kicks the player if they
  are still pending and still online.

### 3.6 `SERVER_STOPPING`

```java
RulesManager.get().save();
JoinHandler.shutdown();  // cancels pending tasks, shuts the scheduler down
```

---

## 4. Generated Datapack

### 4.1 Location

```
<world>/datapacks/Server Rules/
├── pack.mcmeta
└── data/server_rules/menu/rules.json
```

The pack id used with `/datapack enable|disable|list` is
`file/Server Rules` (returned by `DatapackGenerator.getPackId()`).

### 4.2 `pack.mcmeta`

```json
{
  "pack": {
    "description": "Server Rules chest GUI.",
    "min_format": [94, 0],
    "max_format": [101, 1]
  }
}
```

Both bounds are arrays on purpose. Starting with the 26.x series Minecraft
expresses pack versions as `[major, minor]` pairs; a bare integer in
`max_format` is silently interpreted as `[N, 0]`, which is less than the
runtime `[101, 1]` and causes the pack to be rejected and put on
`level.dat`'s disabled list. The range `[94,0]..[101,1]` covers the 1.21
series through 26.1.1.

The file is **rewritten on every startup** (only if its contents don't
already match the expected string) so a Minecraft version bump doesn't
leave the pack stranded.

### 4.3 `rules.json` — Inventory Menu schema

The generated menu uses a 3-row chest with 9 columns, 1-indexed:

| Row | Column | Item | Purpose |
| --- | --- | --- | --- |
| 2 | 2 | `enchanted_book` | Chat Rules |
| 2 | 5 | `enchanted_book` | Server Rules |
| 2 | 8 | `enchanted_book` | Gameplay Rules |
| 3 | 3 | `player_head` (`alphain`) | Accept — runs `/rules accept` |
| 3 | 7 | `player_head` (`_3RACHA`) | Decline — runs `/rules decline` |

Each head's `action` is:

```json
{
  "type": "command",
  "as_player": true,
  "silent": true,
  "command": "rules accept"   // or "rules decline"
}
```

`as_player: true` is **required**. Without it, Inventory Menu dispatches the
command with `server.createCommandSourceStack()` (the console), and
`/rules accept` silently fails at `getPlayerOrException()`. `silent: true`
suppresses the command-feedback message so the player only sees the GUI
close and the welcome/kick message.

All `custom_name` and `lore` values use proper JSON text components
(`{"text":"...","color":"green","bold":true,"italic":false}`) rather than
legacy `§`-prefixed strings. `ComponentSerialization.CODEC` in 26.1.1 is
stricter and rejects malformed legacy strings in some data-component paths.

### 4.4 Legacy-file detection

```java
boolean hasAccept  = existing.contains("rules accept");
boolean hasDecline = existing.contains("rules decline");
if (!hasAccept || !hasDecline) { overwrite rules.json; }
```

This heals worlds that were created by earlier builds (pre-PR #1) which
wrote a menu with only the three enchanted books and no accept/decline
buttons. Admins who want to keep a hand-edited menu can drop an empty file
named `.customized` next to `rules.json` to suppress this check.

---

## 5. Restriction System

`RestrictionHandler` registers Fabric API callbacks that return
`InteractionResult.FAIL` (or `false`) while
`RulesManager.isRestricted(player)` is true:

- `PlayerBlockBreakEvents.BEFORE`
- `AttackBlockCallback`, `UseBlockCallback`
- `AttackEntityCallback`, `UseEntityCallback`
- `UseItemCallback`
- `ServerMessageEvents.ALLOW_CHAT_MESSAGE`
- `ServerMessageEvents.ALLOW_COMMAND_MESSAGE`

Command filtering checks the signed content against a whitelist:

```java
Set.of("rules", "rules accept", "rules decline", "help")
```

Movement is frozen via the effects in `RulesGuiOpener.applyFreezeEffects`
(not via an event callback), which is both cheaper and smoother visually
because it also blanks the screen with blindness.

---

## 6. Configuration

### 6.1 `config/serverrules/config.json`

| Key | Type | Default | Purpose |
| --- | --- | --- | --- |
| `acceptTimeoutSeconds` | `int` | `120` | Seconds before a pending player is kicked. `<= 0` disables the timer. |
| `timeoutKickMessage` | `string` | `"You did not accept the server rules in time."` | Disconnect reason on timeout. |
| `declineKickMessage` | `string` | `"You must accept the server rules to play on this server."` | Disconnect reason on `/rules decline`. |
| `restrictionMessage` | `string` | `"§cYou must accept the server rules before you can do this. Use §e/rules§c to view them."` | Chat message sent when a restricted player tries to do something. |

Only the keys listed above are read; unknown keys are ignored. Missing
keys fall back to defaults.

### 6.2 `config/serverrules/rules-accepted.txt`

Plain-text ledger, one line per accepted player in the form `uuid:username`.
Comment lines starting with `#` are ignored. Invalid lines are logged at
`WARN` and skipped.

`RulesManager.save()` is called on:
- `markAccepted(player)`
- `removeAccepted(uuid)` (when something was actually removed)
- `clearAllAccepted()`
- `SERVER_STOPPING`

Save/load are `synchronized`; the accepted map is a `ConcurrentHashMap` so
reads from event callbacks don't need to lock.

---

## 7. Commands

### 7.1 `/rules`

Root literal, no argument. Re-opens the menu:

- If the sender has already accepted → `RulesGuiOpener.openMenu(player)`
  (no freeze).
- Otherwise → `RulesGuiOpener.open(player)` (freeze + menu).

### 7.2 `/rules accept`

Marks the sender as accepted. No-op (with chat notice) if they already had.

### 7.3 `/rules decline`

Disconnects the sender with `RulesConfig.getDeclineKickMessage()`.

### 7.4 `/rules view` and `/rules-view`

Both execute `RulesGuiOpener.openMenu(player)`. The top-level
`/rules-view` is a convenience for players who don't want to remember the
subcommand.

### 7.5 `/rules reload` / `/rules reload <player>`

Requires `Permissions.COMMANDS_ADMIN` (op level 3).

`executeReloadAll`:

```java
RulesManager.get().clearAllAccepted();
for (ServerPlayer online : server.getPlayerList().getPlayers()) {
    JoinHandler.onRulesReloaded(online);  // re-freeze + re-open with 200ms delay
}
```

`executeReloadPlayer`:

- Resolves the argument against the online player list first, then
  against the accepted-name map.
- `RulesManager.removeAccepted(uuid)`.
- If the target is online, calls `JoinHandler.onRulesReloaded(online)`.

Name suggestions for the `<player>` argument come from
`RulesManager.getAccepted().values()`.

---

## 8. GUI Open Pipeline

`RulesGuiOpener.openMenu(player)`:

```java
player.closeContainer();

server.getCommands().performPrefixedCommand(
    player.createCommandSourceStack()
          .withPermission(PermissionSet.ALL_PERMISSIONS)
          .withSuppressedOutput(),
    "menu server_rules:rules"
);
```

The command must be dispatched with the player's own command source
(Inventory Menu opens the menu for whoever ran `/menu`), but elevated
because the default source level doesn't pass its permission check. Output
is suppressed so the chat doesn't spam the player with "Opened menu ..."
feedback.

`closeContainer()` first is defensive — if an accepted player uses
`/rules-view` while a furnace GUI is open, we don't want two overlapping
screens.

---

## 9. Extension Points

### 9.1 Adding menu items

Edit `<world>/datapacks/Server Rules/data/server_rules/menu/rules.json`.
The file is left alone on subsequent restarts *unless* it is missing the
`"rules accept"` / `"rules decline"` strings, in which case it is
overwritten with the defaults. Create an empty `.customized` file next to
it to opt out.

### 9.2 Forcing a rebuild

Either delete `<world>/datapacks/Server Rules/` and restart, or call
`DatapackGenerator.regenerate(server)` — the latter overwrites both files
unconditionally.

### 9.3 Clearing a single player without kicking them

```
/rules reload <name>
```

If the target is online, `JoinHandler.onRulesReloaded(...)` re-freezes and
re-opens the GUI with a 200 ms smoothing delay.

### 9.4 Disabling the timeout

Set `acceptTimeoutSeconds` to `0` in `config.json`. `scheduleTimeout`
returns early when the value is `<= 0`.

---

## 10. Build

```bash
./gradlew build
```

Outputs to `build/libs/server-rules-<version>.jar`. Required toolchain is
Java 25 (pinned in `gradle.properties`). The build uses Fabric Loom; no
external repositories beyond the Fabric maven declared in
`settings.gradle`.

---

## 11. Version History Highlights

- **1.0.1 (minecraft-mod-additions branch, PR #1)**
  - Add accept/decline player-head buttons to the generated menu.
  - Add `/rules view` and `/rules-view` commands.
  - Fix `/rules reload` so it re-freezes and re-opens the GUI on affected
    online players.
  - Fix the blindness effect so it lasts the full 10-minute freeze window
    instead of 2 seconds.
  - Smooth 3-second GUI appearance on join (freeze effects applied first,
    menu opens after the delay).
  - `pack.mcmeta` now uses `min_format: [94, 0]` / `max_format: [101, 1]`
    arrays (previously wrote `pack_format: 101.1` as a float, which
    Minecraft rejected).
  - Auto-enable the generated pack via `server.reloadResources(...)` on
    startup, healing worlds whose pack was previously disabled.
  - Legacy `rules.json` auto-upgrade with `.customized` opt-out marker.

See PR [#1](https://github.com/alphain24/Server-Rules/pull/1) for the full
diff.

---

## 12. License

MIT — see [`../LICENSE`](../LICENSE).

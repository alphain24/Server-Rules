# Server Rules

A server-side Fabric mod that forces new players to read and accept a set of
server rules — presented in a chest GUI powered by the
[Inventory Menu](https://modrinth.com/mod/inventory-menu) mod — before they
can interact with the world.

- **Mod ID:** `server-rules`
- **Environment:** server-only (clients do not need to install anything)
- **Minecraft:** `26.1.1`
- **Fabric Loader:** `>=0.19.2` · **Fabric API:** `>=0.145.4+26.1.1` · **Java:** `>=25`
- **License:** MIT

---

## Features

- Chest GUI on first join with a three-row layout: chat rules, server rules,
  gameplay rules, plus **Accept** and **Decline** player-head buttons that
  run `/rules accept` and `/rules decline` when clicked.
- Full interaction lockdown until the rules are accepted: blocked movement,
  chat, commands (except the whitelisted rules commands), block breaking,
  block placing, block/entity interaction, and item use. Enforced with a
  combination of `Slowness 255`, `Jump Boost 128`, `Blindness`, and Fabric
  API interaction callbacks.
- Accept timeout with a configurable kick message (defaults to 120 seconds).
- `/rules-view` command for already-accepted players to re-read the rules at
  any time without getting frozen.
- `/rules reload [player]` admin command that clears stored acceptances and
  re-opens the GUI on every affected online player immediately.
- Generates its own Inventory Menu datapack into
  `<world>/datapacks/Server Rules/` on startup, auto-enables it via
  `server.reloadResources(...)`, and refreshes `pack.mcmeta` on every boot so
  Minecraft version bumps don't leave the pack stranded on `DataPacks.Disabled`.
- Legacy-file detection: if an older install wrote a `rules.json` without the
  accept/decline buttons, the file is auto-upgraded on next start. Admins can
  opt out by creating an empty `.customized` marker file next to `rules.json`.

---

## Installation

1. Install [Fabric Loader 0.19.2+](https://fabricmc.net/use/server/) on your
   server.
2. Drop the following jars into the server's `mods/` folder:
   - Fabric API (0.145.4+26.1.1 or newer)
   - [Inventory Menu](https://modrinth.com/mod/inventory-menu) (1.1.0 or newer)
   - `server-rules-<version>.jar`
3. Start the server once. The mod will:
   - Create `config/serverrules/config.json` and `rules-accepted.txt`.
   - Generate `<world>/datapacks/Server Rules/` with a valid `pack.mcmeta`
     (`min_format: [94, 0]`, `max_format: [101, 1]`) and the default menu
     JSON containing the accept/decline buttons.
   - Auto-enable the datapack via a resource reload.

No client-side install is required — everything runs on the server.

---

## In-Game Flow

1. Player joins. If their UUID is in `rules-accepted.txt`, nothing happens.
2. If not, they are marked *pending*. Slowness / Jump Boost / Blindness are
   applied immediately so the screen fades to black and the player can't
   move.
3. After a 3-second smoothing delay the chest GUI opens via Inventory Menu.
4. The player reads the rules and clicks the **Accept** (player head of
   `alphain`) or **Decline** (player head of `_3RACHA`) button.
   - **Accept** → status effects cleared, GUI closed, UUID written to
     `rules-accepted.txt`, welcome message sent.
   - **Decline** → kicked with the configured message.
   - **Timeout** (default 120 s) → kicked with the timeout message.
5. Future joins by the same player skip the whole flow.

---

## Commands

| Command | Who | Effect |
| --- | --- | --- |
| `/rules` | everyone | Re-opens the GUI. Restricted players get the full freeze + timeout flow; accepted players get a view-only open. |
| `/rules accept` | pending player | Marks the player as having accepted, clears the freeze effects, closes the GUI, saves state. |
| `/rules decline` | pending player | Kicks the player with the configured decline message. |
| `/rules view` / `/rules-view` | everyone | Opens the menu in view-only mode (no freeze effects). |
| `/rules reload` | op level 3 | Clears **all** stored acceptances. Every online player is re-frozen and shown the GUI. |
| `/rules reload <player>` | op level 3 | Clears one player's acceptance. If they're online, re-freezes them and re-opens the GUI immediately. |

While pending, a player may only run `/rules`, `/rules accept`, `/rules decline`, and `/help`.

---

## Configuration

### `config/serverrules/config.json`

Created automatically on first start with these defaults:

```json
{
  "acceptTimeoutSeconds": 120,
  "timeoutKickMessage": "You did not accept the server rules in time.",
  "declineKickMessage": "You must accept the server rules to play on this server.",
  "restrictionMessage": "§cYou must accept the server rules before you can do this. Use §e/rules§c to view them."
}
```

Set `acceptTimeoutSeconds` to `0` or a negative number to disable the kick
timer entirely.

### `<world>/datapacks/Server Rules/data/server_rules/menu/rules.json`

This is the GUI itself — an Inventory Menu schema with `name`, `rows`, and
an `items` array. Edit this file to change the menu contents, titles, and
colors. Because the mod auto-upgrades legacy installs that lack the
accept/decline buttons, **create an empty `.customized` file next to
`rules.json` to opt out of further auto-upgrades** if you hand-edit the
menu.

### `config/serverrules/rules-accepted.txt`

Plain-text ledger of every player who has accepted the rules, one per line
in the format `uuid:username`:

```
b1d2c3e4-0000-0000-0000-000000000000:Steve
a9f8e7d6-0000-0000-0000-000000000000:Alex
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `Unterminated object … $.pack.max_format` on `/reload` | A truncated `pack.mcmeta` left over from a crash or the pre-release 1.0.0 build. | Restart the server. `DatapackGenerator` rewrites `pack.mcmeta` on startup, and `ServerRules#ensureDatapackEnabled` re-enables the pack via `reloadResources`. |
| Pack shown in `DataPacks.Disabled` inside `level.dat_old` | World previously loaded a `pack.mcmeta` that Minecraft rejected (e.g. `pack_format: 101.1` float). | Restart — the mod will re-enable `file/Server Rules` automatically. |
| Clicking Accept/Decline does nothing | `as_player: true` is missing on the action, meaning the command runs as the server console and fails `ctx.getSource().getPlayerOrException()`. Shouldn't happen on a clean install — most likely you have a legacy `rules.json`. | Delete `rules.json` (or remove the `.customized` marker if present) and restart so the default is regenerated. |
| GUI opens but the player can still move around | Blindness was only applied for 40 ticks in older builds. | Upgrade to the current build; all three freeze effects now last 10 minutes. |

---

## Documentation

Full technical reference (architecture, lifecycle, file layout, extension
points): see [`docs/OUTPUT.md`](docs/OUTPUT.md).

## License

MIT — see [`LICENSE`](LICENSE).

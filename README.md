<p align="center"><img src="https://i.ibb.co/XfnxCkfg/Simple-RTP.png" /></p>

Simple Minecraft plugin for Paper that teleports players to a random location within a configurable radius

## Features

- All configuration is read from `config.yml`
- Configurable prefix for plugin messages (`prefix`)
- **`/rtp`** command that teleports a user randomly
  - Within a customizable perimeter
  - Centered either at (0, 0) or at the player's current position (configurable)
  - Never in the air or on the Nether roof
- **World filter**: limits in which worlds the user can use the command
  - If `disabled (default)`: the user can use it in all worlds
  - If `enabled`: the user can only do so in the worlds defined in the config
- Configurable **radius** (default 1000 blocks) and **cooldown** (0 = no cooldown)
- Optional **cost** (Vault + economy plugin): player pays `cost-amount` to use `/rtp` (only when teleporting themselves)
- Optional **teleport sound** (Enderman teleport by default)
- Configurable delivery of key messages via **chat** or **action bar**
- Support for teleporting **other players** via `/rtp <player>` with its own permission
- Minecraft color codes supported using `&`

The plugin also reports anonymous usage statistics via **bStats** (plugin id `29587`).

## Commands & Permissions

| Command | Description |
|---------|-------------|
| `/rtp` | Teleports the executor to a random safe location within the configured radius |
| `/rtp <player>` | Teleports the specified player to a random safe location within the configured radius |
| `/srtp reload` | Reloads the plugin configuration |

- **`/rtp`**: requires permission **`srtp.rtp`** or **`srtp.rtp.once`** (default: op for `srtp.rtp`)
- **`/rtp <player>`**: requires permission **`srtp.rtp.others`** (default: op, console can always use it)
- **`/srtp reload`**: requires permission **`srtp.reload`** (default: op)
- If a user has the `srtp.rtp.once` permission, they will only be able to use `/rtp` once
  - Ideal for giving (1) Random TP to new players
  - This permission is useless if the user also has `srtp.rtp`

## Requirements

- Java 21 (LTS)
- Paper server (tested with `api-version: "1.21"`)
- Maven 3.x (to build). For cost feature at runtime: Vault + an economy plugin (e.g. EssentialsX, CMI)

## Build

From the project root (`simplertp`), run:

```bash
mvn clean package
```

The plugin JAR will be generated at:

`target/SimpleRTP-1.1.2.jar`

## Installation

1. Copy the built JAR to your Paper server `plugins` folder
2. Start or restart the server
3. The `config.yml` file will be created automatically in `plugins/SimpleRTP/` if it does not exist

## Configuration

Example configuration (defaults):

```yaml
prefix: "&7[&6SimpleRTP&7] &r"
world-filter-enabled: false
worlds:
  - "world"
rtp-from: "center"
radius: 1000
cooldown: 0
cost-enabled: false
cost-amount: 100.0
makesound: false
sound: "entity.enderman.teleport"

message-delivery: "chat"
messages:
  teleporting: "&fTeleporting..."
  success: "&aTeleport successful!"
  failed: "&cCould not find a safe location. Try again."
  no-permission: "&cYou do not have permission to use this command"
  reload-no-permission: "&cYou do not have permission to reload the configuration"
  world-not-found: "&cThe RTP world(s) could not be found. Contact an administrator"
  world-not-allowed: "&cYou can only use /rtp in these worlds: %worlds%"
  used-once: "&cYou have already used random teleport"
  cooldown: "&cYou must wait %seconds% seconds before using /rtp again"
  cost-insufficient: "&cYou do not have enough money. Cost: %cost%"
  cost-no-vault: "&cRTP cost is enabled but no economy is available"
  player-only: "&cThis command can only be executed by a player"
  reload-success: "&aConfiguration reloaded"
  player-not-found: "&cThat player could not be found"
  no-permission-others: "&cYou do not have permission to teleport other players"
```

- **prefix**: Prefix prepended to all plugin messages. Use `&` for color codes. Empty string = no prefix
- **world-filter-enabled**: `false` = `/rtp` works in any world; `true` = only in worlds listed in `worlds`
- **worlds**: List of world names where `/rtp` is allowed when world filter is enabled
- **rtp-from**: Center point for the random radius (`center` = 0,0; `player` = player's current location)
- **radius**: Size of the area around the chosen center. Teleport is within `[-radius, radius]` on X and Z
- **cooldown**: Seconds between uses per player when teleporting themselves. `0` = no cooldown
- **cost-enabled** / **cost-amount**: When cost is enabled, player must pay the amount to teleport themselves (requires Vault + economy plugin)
- **makesound** / **sound**: Enable and configure a sound to play on successful teleport
- **message-delivery**: Choose whether key messages (`success`, `failed`, `used-once`, `cooldown`) are sent via chat or action bar
- **messages**: All plugin messages. Placeholders: `%worlds%`, `%seconds%`, `%cost%`

## Notes

- The player always spawns on a solid block (never in the air). If no safe spot is found, an error message is shown
- In the Nether, the player never spawns on or above the roof (never above Y 124)
- The "once" permission (`srtp.rtp.once`) is stored persistently per player (PersistentDataContainer)

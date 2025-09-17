# Pixelmon Level Cap Mod

This Forge mod adds server-side level caps for Pixelmon Reforged. It enforces configurable caps that increase as trainers earn gym badges. Pokemon above the cap faint automatically when sent into battle, helping keep progression balanced on multiplayer servers.

## Features

* Configurable default level cap and gym-specific caps stored in `config/pixelmon-level-caps.json`.
* Caps increase whenever a player earns a configured gym badge and persist per-player.
* When a Pokemon above the cap is sent out in a battle, it immediately faints.
* `/lvlcap` command lets players view their current cap and the next configured upgrade.
* `/lvlcap faint` lets players or admins immediately faint party Pokemon that exceed the current cap.
* Admin subcommands (`/lvlcap set`, `/lvlcap remove`, `/lvlcap list`) manage gym caps by gym or gym leader name.
* Admins can instantly spawn and bind a gym NPC with `/lvlcap spawn <name> <level> [reward items]`, optionally granting items for victories.
* Admins can create new gyms with `/create gym <name> <level>` and bind them to Pixelmon NPCs by right-clicking the intended leader.

## Commands

| Command | Description |
| --- | --- |
| `/lvlcap` | Shows the caller's current cap and the next configured milestone. |
| `/lvlcap faint [player]` | Faints the caller's over-cap Pixelmon. (Permission level 2+ to target another player.) |
| `/lvlcap set <gym> <level>` | (Permission level 2+) Sets the cap for the given gym/leader name. |
| `/lvlcap remove <gym>` | (Permission level 2+) Removes the configured cap for the given gym. |
| `/lvlcap list` | (Permission level 2+) Lists all configured gym caps. |
| `/lvlcap spawn <gym> <level> [rewards]` | (Permission level 2+) Spawns a Pixelmon NPC in front of you, binds it as a gym with the given cap, and optionally configures victory rewards. |
| `/create gym <name> <level>` | (Permission level 2+) Starts gym creation; right-click a Pixelmon NPC to finalise the binding. |

Players automatically receive chat updates whenever their cap changes or when a high-level Pokemon is forced to faint. Gym victories can now also award configured items when the gym is created with rewards.

### Permission nodes

The mod registers the following permission nodes via Forge's `PermissionAPI` so server owners can integrate with LuckPerms or similar managers:

| Node | Default | Description |
| --- | --- | --- |
| `lvlcap.command.view` | Everyone | Allows viewing your current cap with `/lvlcap`. |
| `lvlcap.command.faint` | Everyone | Allows using `/lvlcap faint` on yourself. |
| `lvlcap.command.faint.others` | OPs | Allows fainting other players' Pokémon with `/lvlcap faint <player>`. |
| `lvlcap.command.set` | OPs | Allows setting gym caps with `/lvlcap set`. |
| `lvlcap.command.remove` | OPs | Allows removing gym caps with `/lvlcap remove`. |
| `lvlcap.command.list` | OPs | Allows listing configured caps with `/lvlcap list`. |
| `lvlcap.command.spawn` | OPs | Allows spawning and configuring gym NPCs with `/lvlcap spawn`. |
| `lvlcap.command.create` | OPs | Allows creating gyms with `/create gym`. |

### Reward syntax

When using `/lvlcap spawn`, append space-separated item identifiers to award them on gym victory. Each token can optionally use `*` to specify a quantity. For example:

```
/lvlcap spawn Electric 50 pixelmon:rare_candy*5 minecraft:nether_star
```

This spawns the gym with a level cap of 50 and delivers five Rare Candies plus a Nether Star the first time a player defeats the gym. Rewards are saved to `pixelmon-level-caps.json` alongside the gym definition.

## Building

This project uses ForgeGradle for Minecraft 1.16.5 and therefore must be
compiled with Java 8.

1. Install a Java 8 JDK (for example, Temurin/Adoptium 8) and make sure it is
   the active JVM on your shell (`java -version` should report 1.8).
2. Clone this repository and open a terminal in the project directory.
3. Use the provided bootstrap scripts to run Gradle 7.6.3 (the newest release
   supported by ForgeGradle 5) without installing it system-wide:
   * Unix/macOS: `./gradlew build`
   * Windows: `gradle.bat build`
   The first run downloads Gradle into `.gradle-wrapper/` and all subsequent
   invocations reuse that copy. If you prefer to install Gradle manually, make
   sure you invoke version 7.6.3—ForgeGradle 5 will fail to apply on Gradle 8.x.
4. The reobfuscated release jar will be copied to
   `build/libs/pixelmon-level-cap-0.1.0-release.jar` after the build finishes.
   (The deobfuscated development jar still lives at
   `build/libs/pixelmon-level-cap-0.1.0.jar`.)

Copy that jar into the `mods/` folder on your Forge server or client alongside
Pixelmon Reforged to enable the level-cap behaviour.

## Configuration

* `config/pixelmon-level-caps.json` – JSON file storing default cap and per-gym caps.
* `serverconfig/lvlcap-server.toml` – Forge config controlling the default cap before any badges.

Caps update immediately when gym data changes or when admins edit the config files.


## Requirements

- Fabric loader 0.18.4 or newer
- Fabric API

The included Gradle wrapper downloads the correct Gradle version automatically. A seperate system-wide Gradle installation is not required.

## Default Controls

- Command prefix: `;`
- ClickGUI key: `P`

For example, open in game chat and enter `;help` to view the available commands.

## Installing

1. Install a Fabric loader for Minecraft 1.21.11
2. Install a compatible Fabric API release.
3. Place the compiled JewDust JAR and Fabric API JAR in your Minecraft mods folder.
4. Launch the Fabric profile from your launcher.

The configuration is stored in the 'jewdust' folder inside your Minecraft instance directory.

## Building from source

Clone or download the repo, then open a terminal in the project directory.

### Windows batch file

Double-click 'BUILD-CLIENT.bat', or run it from a terminal:

```bat
BUILD-CLIENT.bat
```

### Windows with the Gradle wrapper

PowerShell:

```powershell
.\gradlew.bat clean build
```

Command Prompt:

```bat
gradlew.bat clean build
```

### Linux and macOS

Make the wrapper executable the first time:

```bash
chmod +x gradlew
```

Then build the project:

```bash
./gradlew clean build
```

## Build output

Successful builds are written to:

```text
build/libs/
```

Use the regular JAR as the Minecraft mod. The file ending in `-sources.jar` contains source code for development and will not work.

## Development run

You can launch a Fabric development client with:

Windows:

```powershell
.\gradlew.bat runClient
```

Linux and macOS:

```bash
./gradlew runClient
```

## Credits

- Kryspy: your a chinese dog
- X: idk his git lol
- a_victor: idk his either
- packet: your a legend, i also don't know your git but i love you
- ChatGPT: love you bbg

## License

This project is licenced under the GNU Affero General Public License v3.0 or later. See [LICENSE](LICENSE) for the complete license text.

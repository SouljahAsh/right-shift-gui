# AlmatyClient

Client-side Fabric mod for Minecraft 1.21.11. Press Right Shift to open the GUI and toggle Auto Sprint.

## Features

- Right Shift opens the AlmatyClient GUI.
- Auto Sprint can be toggled in the GUI.
- When Auto Sprint is enabled, holding W starts sprinting automatically.

## Build Steps

1. Make sure Java 21 is installed.
2. Install Gradle or open the project in IntelliJ IDEA as a Gradle project.
3. Wait for Gradle sync to finish and download Fabric dependencies.
4. Build the project:

```powershell
gradle build
```

The compiled mod jar will be in `build/libs/`.

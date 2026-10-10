# Specialist Dance Minecraft Mod: Project Summary

## 1. Project Overview

- Project name: Specialist Dance
- Minecraft version: Java Edition 1.21.11
- Mod loader: Fabric (Loader 0.18.1, Fabric API 0.141.5+1.21.11)
- Language: Java 21 (verified: Eclipse Adoptium 21.0.12.1)
- Environment: Visual Studio Code on macOS 13.7.8 (x86_64)
- Build system: Gradle 9.2.1 with a Loom plugin declared as net.fabricmc.fabric-loom-remap version 1.14.10
- Maven group: com.sebas.specialistdance · Mod version: 1.0.0 · Archive name: specialist-dance

Goal: A client-side mod inspired by the Persona 4 "Specialist Dance" meme that shows a dancing character/animation when the player obtains valuable items (diamonds, gold, emeralds, iron, netherite, etc.).

## 2. Main Features Planned

Unchanged from the original plan: valuable-item detection, on-screen dance animation, customizable triggers, animation controls (duration/size/position/cooldown), and future character/config options. First playable milestone: detect a diamond entering the inventory and show a dance animation.

## 3. Current Project Structure

Actual layout (verified):

```text
specialist-dance-mc/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/ (gradle-wrapper.jar, gradle-wrapper.properties)
├── .gitignore
├── LICENSE
├── README.md
├── src/main/
│   ├── java/com/sebas/specialistdance/SpecialistDanceClient.java
│   └── resources/fabric.mod.json
├── build/   (generated, untracked)
├── .gradle/ (generated, untracked)
└── run/     (generated dev-run data, untracked)
```

## 4. Current Implementation

### Java entry point

`src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java` FIXED

```java
package com.sebas.specialistdance;

import net.fabricmc.api.ClientModInitializer;

public class SpecialistDanceClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        System.out.println("Specialist Dance is Ready!");
    }
}
```

FIXED The declaration was originally package main;, which did not match the file's folder or the entrypoint declared in the manifest. Because Gradle compiles source files explicitly, javac did not reject it and emitted the class as main.SpecialistDanceClient. It is now package com.sebas.specialistdance;.

### Mod manifest

`src/main/resources/fabric.mod.json`

- id: specialist-dance (note: differs from the specialistdance mentioned in earlier notes; both are valid, but the ID is hyphenated)
- environment: client
- entrypoint (client): com.sebas.specialistdance.SpecialistDanceClient
- depends: fabricloader >=0.18.1, minecraft 1.21.11 (pinned exact, not ~1.21.11), java >=21, fabric-api *

## 5. Development Progress

### Task Status

- Install and configure Java 21 — Completed
- Download Gradle 9.2.1 / generate wrapper — Completed
- Configure Loom — Completed
- Organize source and resource directories — Completed
- Correct the Fabric initializer import — Completed
- Compile the project (clean build) — Completed
- Launch the development client — Attempted
- Diagnose client crash [FIXED] — Completed
- Confirm client reaches main menu — Pending re-run (verification not yet performed)
- Verify mod loads in-game — Not implemented
- Detect valuable items — Not implemented
- Render a dancing animation — Not implemented

## 6. Current Issue: Root Cause and Fix UPDATED — rewritten

Symptom: ./gradlew runClient crashed during game initialization. Three crash reports were generated in run/crash-reports/.

Actual root cause (corrected): The entrypoint class could not be loaded:

Cause: LanguageAdapterException: java.lang.ClassNotFoundException:
       com.sebas.specialistdance.SpecialistDanceClient

This was caused by the package main; declaration in the source file, which made the compiled class main.SpecialistDanceClient instead of the expected package. The error was a build/package-configuration error, not an authentication error. Fix applied: the package declaration now reads com.sebas.specialistdance;.

Errors previously mistaken for the cause (both are non-issues):

- InvalidCredentialsException: Status: 401 / Failed to fetch user properties — expected in a Fabric dev client, which uses an offline/dummy account. It appears before the crash in every log and is non-fatal.
- IllegalStateException: Can't getDevice() before it was initialized — a downstream consequence of crashing during init (the renderer had not initialized yet), not a cause.

## 7. Immediate Next Steps

1. Re-run ./gradlew clean build; confirm BUILD SUCCESSFUL.
1. Confirm the class now compiles to build/classes/java/main/com/sebas/specialistdance/SpecialistDanceClient.class (not under main/).
1. Run ./gradlew runClient; confirm the client reaches the main menu and no new crash report is produced.
1. Confirm run/logs/latest.log contains Specialist Dance is Ready!.
1. Implement the first item-detection feature, starting with diamonds.
1. Learn how inventory/award events and client-side rendering work in Fabric.
1. Add a temporary visual indicator to test the trigger before GIF animation.
1. Implement the dance animation and its position/size/duration settings.
1. Test in-game and refine.

### Non-Blocking Observations (not bugs in the mod)

- .gitignore currently contains .Ds_Store, but macOS writes .DS_Store (different capitalization); build/, .gradle/, and run/ are currently untracked and could be ignored.
- Mod ID specialist-dance (manifest) vs. specialistdance (earlier notes) — worth settling on one for consistency.
- build.gradle uses net.fabricmc.fabric-loom-remap (works); the conventional plugin id is fabric-loom.

## 8. Learning Approach

Unchanged: build manually as a learning exercise, work in small testable steps, explain each concept/class/method/import/config before introducing code, and test each feature before moving on.

## 9. Overall Status

The blocking runtime crash is fixed. The cause was a package declaration (package main;) that conflicted with the file's location and the Fabric entrypoint. Everything still compiles, but the fixed build and in-game load remain to be verified by re-running the client. Once confirmed, development can proceed to actual mod functionality: detecting valuable loot and celebrating it with the Specialist Dance.

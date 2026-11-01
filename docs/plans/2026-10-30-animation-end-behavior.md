# Animation End Behavior Close-Out — Implementation Plan

**Goal:** Make `hold` and `loop` work when music cannot signal the end of the animation, and turn the already-shipped Phase 6 code from 🛠 (built) into 🧪 (verified with evidence).

**Architecture:** The HUD render lambda in `SpecialistDanceClient` is the only consumer of `animationEndBehavior`. It currently treats "music is still playing" as the only end signal, so an animation with no music has no end signal and is cancelled on the first frame. The fix records, at trigger time, whether an end signal actually exists, and lets the animation fall back to a single finite pass when it does not.

**Tech Stack:** Java 21, Fabric for Minecraft 1.21.11, Mojang mappings, LWJGL OpenAL (`AL10`), Gradle + Fabric Loom.

**Spec:** `ROADMAP.md` Phase 6 — v1.5.0 Animation Control (`/Users/sebasjgg27/Desktop/specialist-dance-mc/ROADMAP.md:158-168`).

## Global Constraints

- Client-side only. No server logic, no network payloads.
- Java 21, 4-space indentation, imports grouped Java, Fabric, Minecraft, third-party.
- No comments unless behavior is non-obvious.
- Do not clear Minecraft's OpenAL context. Never end a call with `ALC10.alcMakeContextCurrent(0L)`; use `makeContextCurrent()` / `restoreContext(previous)`.
- There are no automated tests (`./gradlew test` is `NO-SOURCE`). Verification is a fresh `./gradlew build` plus an in-game observation, recorded as a `run/logs/latest.log` line or a written repro.
- A cached `UP-TO-DATE` build is not evidence. Use `./gradlew clean build` or touch a source file.
- `./gradlew build` and `runClient` require the Loom metadata mirror on `127.0.0.1:8765` (`tools/loom-mirror.sh`), because `gradle.properties:16` sets `loomCustomMetadataUrl`.
- Git: the human commits, pushes and tags. Nothing in this plan authorizes a tag or a push.
- One phase equals one release equals one tag; `mod_version` stays `1.4.0` until the whole phase is verified.

## File Structure

- `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java` — owns the trigger state (`animationStartMillis`) and the only end-behavior consumer (the HUD lambda at lines 150-233). Gets one new field and one changed local variable.
- `src/main/java/com/sebas/specialistdance/audio/MusicManager.java` — unchanged. `isPlaying()` (line 263) already answers the question correctly, including `available == false`.
- `run/config/specialist-dance.json` — dev config used for the matrix. Not committed; it lives under `run/`.
- `ROADMAP.md` — Phase 6 row and status, the stale header at line 6, the duplicated Phase 5 block at lines 129-156, and the Open debt list at lines 219-232.
- `CHANGELOG.md` — one entry for the fix.

## Review Focus

These are the inputs the plan does not test but a player will hit. Each line names the behavior a reasonable player expects.

1. `musicEnabled: false` with `hold` or `loop` — the player expects a dance. Today they get nothing.
2. OpenAL unavailable (`available == false`, sound device missing or init failed) with `hold` or `loop` — the player expects a dance, not a silent cancellation that looks like the mod is broken.
3. Track shorter than the dance (`test-1.ogg` is short) with `loop` — the player expects the dance to end with the music, not to be cut off before the first pass completes.
4. `hold` with no end signal — the player expects the animation to finish. An implementation that keeps the last frame forever leaves a frozen sprite on the HUD until the next dance or world change.
5. `cooldownMs` after a world change — the player expects the cooldown to be about how often the dance fires, not to be blocked by a trigger from the previous world.

---

## Task 1: Reproduce the failure before changing anything

**Files:**
- Read only: `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java:160-193`
- Modify: `run/config/specialist-dance.json` (dev runtime file, not tracked by git)

**Interfaces:**
- Consumes: nothing.
- Produces: a written repro and a log line that Task 3 must make disappear.

- [ ] **Step 1: Start the metadata mirror in its own terminal window.** It must be listening before Gradle configures Loom. The script ends in `exec python3 -m http.server`, so it holds that terminal open until Ctrl+C; do not run it in the same terminal you will use for Gradle. Leave it running for the whole session.

```bash
./tools/loom-mirror.sh
```

Then from a second terminal:

```bash
lsof -nP -iTCP:8765 -sTCP:LISTEN
```

Expected: `lsof` prints a `python` line holding `TCP 127.0.0.1:8765 (LISTEN)`. If it prints nothing, the mirror is not running and every Gradle command fails with a connection error to port 8765 before reaching your code. `./gradlew build --offline` is not a workaround: offline mode blocks the localhost URL too.

- [ ] **Step 2: Set the config to the failing combination by hand, while the game is closed.** Keep everything else as it is. The dev file already holds `"animationEndBehavior": "hold"` and `"activeDance": "Zelda"`.

```json
  "musicEnabled": false,
  "animationEndBehavior": "hold",
```

Expected: the file still contains `"debug": true`. Without debug the `log(...)` calls print nothing and there is no evidence to read.

- [ ] **Step 3: Launch and trigger a dance.**

```bash
./gradlew runClient
```

In-game: survival world, mine a diamond ore, walk to the drop, pick it up. Then press **K**, set End behavior back to `stop`, close the menu, press **Preview dance on close** and confirm the dance does render with `stop`. This proves the trigger path works and isolates the failure to `hold`.

- [ ] **Step 4: Record the evidence.** Expected: with `hold` and `musicEnabled: false` no frame is drawn at all, and the log shows a dance trigger with no missing-frame error:

```bash
grep -n "Specialist Dance" run/logs/latest.log | tail -20
```

Write down the timestamp of the trigger and the config values used. Task 3 fails if this line is missing.

## Task 2: Make the end signal explicit

**Files:**
- Modify: `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java:71` (add a field next to `animationStartMillis`)
- Modify: `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java:171` (the behavior local)
- Modify: `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java:397-412` (`triggerDance`)

**Interfaces:**
- Consumes: `MusicManager.isPlaying()` returning `boolean`.
- Produces: `private boolean musicDrivesAnimationEnd;` read only inside the HUD lambda.

- [ ] **Step 1: Add the field.** Put it directly under `private long animationStartMillis = -1;` at line 71:

```java
    private boolean musicDrivesAnimationEnd;
```

- [ ] **Step 2: Set it in `triggerDance()`.** Replace the tail of the method (lines 406-411). The read must come after `play()`, never before it: `isPlaying()` called first would report the state left by the previous track.

```java
        lastTriggerMillis = now;
        animationStartMillis = now;

        if (config.musicEnabled) {
            musicManager.play();
        }

        musicDrivesAnimationEnd = config.musicEnabled && musicManager.isPlaying();
```

`play()` reaches `alSourcePlay` before it returns, so the state read on the next line is already `AL_PLAYING` when a buffer loaded. When the track and the bundled default both fail to load, or `available` is false, it returns early and the read gives `false` — which is the correct answer, because no end signal exists.

- [ ] **Step 3: Use the flag to pick the effective behavior.** Replace line 171:

```java
                String behavior = musicDrivesAnimationEnd ? config.animationEndBehavior : "stop";
```

No other line in the HUD lambda changes. `loop` and `hold` keep their `isPlaying()` gate, which is now only reached when music is the end signal. When there is no end signal, the `stop` branch ends the animation after exactly one pass, which also answers Review Focus item 4.

- [ ] **Step 4: Leave the restart guard alone.** `triggerDance()` overwrites `animationStartMillis` unconditionally (line 407), so a running `loop` never blocks the next dance. That is why the phase's "overlapping-restart guard must know whether the animation is finite" item needs no code. Do not add a guard; verify it in Task 3 instead.

- [ ] **Step 5: Compile.**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. No warnings about an unused field.

## Task 3: Run the end-behavior matrix

**Files:**
- Modify: `run/config/specialist-dance.json` between runs
- Read only: `run/logs/latest.log`

**Interfaces:**
- Consumes: Task 2's `musicDrivesAnimationEnd`.
- Produces: six recorded results, one per cell. `ROADMAP.md` may not mark Phase 6 🧪 with fewer.

- [ ] **Step 1: Build fresh, then run.** A cached build is not evidence.

```bash
./gradlew clean build && ./gradlew runClient
```

Expected: `BUILD SUCCESSFUL` and a new jar under `build/libs/`.

- [ ] **Step 2: Fill the matrix.** Use the **K** menu to change each cell, not the JSON file. The config loads once at startup (`SpecialistDanceClient.java:84`), so a hand edit made while the client runs is invisible, and closing the menu calls `config.save()` and overwrites your edit. The HUD re-reads `activeDance`, `frameDurationMs` and `animationEndBehavior` from the live object every frame, so a menu change takes effect on the next dance without a restart. Mine an ore or press **Preview dance on close**, then write down what you saw. `frameDurationMs: 50` means 20 fps, so one pass of the 29-frame `Zelda` pack lasts about 1.45 s and the 147-frame `specialist` pack about 7.35 s. Use that timing to tell "one pass" from "cut off early".

| behavior | musicEnabled | expected |
|---|---|---|
| `stop` | true | one pass, `music.stop()` at the last frame |
| `stop` | false | one pass, silent |
| `hold` | true | freezes on the last frame while the track plays, clears when it ends |
| `hold` | false | one pass, then clears (this is the fix) |
| `loop` | true | repeats frames until the track ends |
| `loop` | false | one pass, then clears (this is the fix) |

- [ ] **Step 3: Prove the Task 1 repro is gone.** Same config as Task 1 Step 2 (`musicEnabled: false`, `hold`). Expected: the animation renders one pass and then disappears. If it still never renders, the flag is not being set where you think; check that the assignment sits in `triggerDance()` and not in the HUD.

- [ ] **Step 4: Check the audio-broken path.** Set `musicEnabled: true` and rename the track folder so no track loads, or start the client with no audio device. Expected: `hold` and `loop` still play one pass instead of cancelling on frame 0, and the log shows `track '...' not found, trying default`.

- [ ] **Step 5: Commit.** The human decides the message and the moment. Nothing ships uncommitted, so do not leave Task 2 in the working tree after this point.

## Task 4: Close the two remaining unverified config paths

**Files:**
- Read only: `src/main/java/com/sebas/specialistdance/SpecialistDanceClient.java:400-404`
- Modify: `run/config/specialist-dance.json`

**Interfaces:**
- Consumes: nothing new.
- Produces: 🧪 or a named bug for `cooldownMs` and for `activeDance` persistence — both listed as never observed in `ROADMAP.md:54` and `:168`.

- [ ] **Step 1: Cooldown.** Set `"cooldownMs": 3000`. Mine two diamond ores within three seconds. Expected: one dance, not two; the second trigger is silently dropped by the guard at line 400. Then wait four seconds and mine a third. Expected: a second dance.
- [ ] **Step 2: Cooldown across a world change.** With the same config, trigger a dance, change dimension or rejoin the world, then mine immediately. Expected: a dance fires. Record what happens if it does not — `lastTriggerMillis` is not cleared in `tick()` at lines 243-251, so a stale value can suppress the first trigger in the new world.
- [ ] **Step 3: `activeDance` persistence.** In the **K** menu switch the dance pack, close, confirm the file on disk changed:

```bash
grep -n "activeDance" run/config/specialist-dance.json
```

Expected: the new pack name, not `specialist`. This is the one field never observed reaching disk; `musicTrack` is the known-good comparison.

## Task 5: Reconcile the documents, then decide the release

**Files:**
- Modify: `ROADMAP.md:6` (stale header: still claims `v1.3.1` is the latest release and branch `v1.4.0`)
- Modify: `ROADMAP.md:37` (Phase 6 row, currently `⬜ 0%`)
- Modify: `ROADMAP.md:129-156` (duplicated leftover `🛠` block inside the finished Phase 5 section)
- Modify: `ROADMAP.md:158-168` (Phase 6 detail: the items that are already built and released inside v1.4.0)
- Modify: `ROADMAP.md:219-232` (Open debt: `animationEndBehavior` dead config and "layout screen exists only in the working tree" are both closed by the code)
- Modify: `CHANGELOG.md` (add the fix under a new heading; do not relabel the released `## 1.4.0`)

**Interfaces:**
- Consumes: the Task 3 matrix and Task 4 results.
- Produces: a roadmap that matches the code, which is the condition for the next release decision.

- [ ] **Step 1: Record the matrix in Phase 6** with one line per cell, marked 🧪 and the date, or 🛠 with the reason it stayed unverified.
- [ ] **Step 2: Move Phase 6 to the truth.** The code shipped in v1.4.0, so the row is not ⬜. It is either 🧪 (verified, waiting for a version bump) or ✅ once a v1.5.0 tag exists. Do not invent a v1.5.0 for a four-line fix without closing a debt box first — `ROADMAP.md:252` rule 4.
- [ ] **Step 3: Delete the stale header and the leftover Phase 5 block**, then check every Open debt box you actually closed against the code, not against memory.
- [ ] **Step 4: Fresh build one last time.**

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL` and a jar in `build/libs/`.

- [ ] **Step 5: Stop and ask the human.** Whether this becomes v1.5.0, joins a later release, or stays a commit is their call. No tag and no push without an explicit instruction.

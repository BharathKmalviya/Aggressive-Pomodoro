# Design

## Context

The current Kotlin Toolchain 0.12.2 project has a `jvm/app` entry point in `desktopApp`, a JVM-targeted KMP `shared` library with Compose UI, and only a starter greeting screen. There is no timer model, persistent storage, desktop alert adapter, release pipeline, license, or Git repository in this workspace. See `proposal.md` and the four delta specs for behavior. The first packaged target is Windows.

The Kotlin Toolchain packages this app type as an executable JAR, while a no-Java-required Windows installer needs a bundled runtime. The release path therefore needs a separate native packaging step, verified on Windows, rather than assuming the toolchain's `package` command produces an installer.

## Goals / Non-Goals

**Goals:**

- Keep timing and transition rules in one small, testable state machine, with UI rendering immutable state.
- Keep filesystem, clock, audio, window attention, and packaging details outside the timer model.
- Limit work on the UI thread; make delayed ticks and sleep/restart reconciliation deterministic.
- Ensure release artifacts can be reproduced from a version tag and checked before publication.

**Non-Goals:**

- System-wide app blocking, forced foreground focus, telemetry, cloud sync, accounts, or background alerts after the process exits.
- Native macOS or Linux packages in the first release.
- Auto-updates or a persistent tray service in the first release.

## Decisions

### 1. One session reducer and explicit effects

Use a single session controller in `shared` with phase (`focus`, `short break`, `long break`), status (`idle`, `running`, `paused`, `awaiting acknowledgement`), cycle count, phase ID, remaining duration/deadline, pending completion events, and settings. Commands (`start`, `pause`, `resume`, `reset`, `skip`, `tick`, `acknowledge`, `reconcile`) produce a new state plus effects such as persist or alert. The UI never calculates transitions itself. The desktop adapter executes effects.

This keeps the Compose layer thin and avoids separate timer and dialog booleans drifting apart. Alternative considered: a simple `LaunchedEffect` that decrements an integer; it is easier initially but drifts under delayed UI updates and makes restart recovery fragile.

### 2. Deadline-based timing with dual clock checks

During one process lifetime, use monotonic elapsed time to calculate the running phase deadline, and a wall-clock anchor for persistence. On wake or relaunch, reconcile from the saved wall deadline once; cap catch-up at one phase and start any automatic next phase at reconciliation time. Detect a substantial wall/monotonic discrepancy while both are available, pause, and show a resolution prompt. Persist a versioned snapshot after commands and transitions, with throttled checkpointing while running; never write every UI frame. The display can update once per second from derived remaining time.

This avoids accumulated tick drift while respecting real elapsed time across sleep and restart. Alternative considered: a wall-clock-only deadline; manual clock changes can cause silent early or late completion. A monotonic-only deadline cannot survive a process restart. The exact discrepancy threshold and checkpoint cadence are implementation constants documented alongside the tests.

### 3. Bounded alert queue and desktop adapter

Assign every phase a unique ID and store completion events until acknowledged. A completion is emitted once per phase ID. Automatic mode starts the next phase immediately, but if that next phase ends before prior acknowledgement, the controller holds progression and retains both events. The dialog displays the event queue and its current next-action state. Confirmation mode waits for the explicit start action. Desktop effects attempt a bundled sound, native attention request, and optional system notification when available; in-app dialog is the reliable fallback. Failures in sound/notification delivery are logged and never block progression.

This gives an assertable rule for unattended use and prevents unlimited hidden cycles. Alternative considered: always chaining phases while unattended; that could generate many repeated alerts and inflated focus counts.

### 4. Small local persistence and single process owner

Store a versioned settings/session snapshot under the per-user application data directory. Use an atomic replace where supported, validate all loaded values, and fall back to safe defaults with a user-visible recovery message on invalid data. Keep a process-level lock on the data directory so two instances cannot independently run and alert for the same session; the second launch informs the user and exits, or activates the first instance if that is straightforward to support.

This avoids a database dependency for one small state record. Alternative considered: a database or synchronized file writes on every tick; both add cost without a user benefit.

### 5. Windows-native release pipeline

Keep Kotlin Toolchain as the build system. Use `https://github.com/BharathKmalviya/Aggressive-Pomodoro` as the public release destination, `main` as the default branch, and immutable `vMAJOR.MINOR.PATCH` tags for releases. Build its executable JAR, then use a pinned Windows JDK `jpackage` step to create and smoke-test an app image and MSI with an embedded runtime. The Windows job must verify that the executable JAR launches correctly through `jpackage`; if the toolchain's JAR layout is incompatible, adapt the packaging input or use the smallest supported alternate packaging step, with that decision recorded before release. Pin the packaging JDK/WiX versions, create a SHA-256 checksum, and publish assets only after tests, app-image launch, installer creation, and a clean Windows installation/launch check pass. Give the app an icon, semantic version, and About version. Unsigned package warnings and the later signing path belong in release docs.

This avoids a build-system migration solely for packaging. Alternative considered: moving the project to Gradle's Compose native distributions, which would add a second build migration to the initial product work. A JAR alone requires users to install Java and does not meet the download requirement.

### 6. Source-available project policy

Use a standard noncommercial software license, with PolyForm Noncommercial 1.0.0 as the candidate, and add a required copyright notice plus a public way to request separate commercial permission. The license holder name and contact must be supplied by the owner before the license and release are published. Do not call the resulting project OSI open source. Make README, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, architecture, verification, and release documentation consistent with these terms. Contribution guidance must make clear that contributors retain their copyrights and that relicensing contributed code would need appropriate rights; do not assume an inbound contributor license agreement.

Alternative considered: MIT, which would allow commercial use without asking the owner; that conflicts with the selected policy. A custom license written for this project would introduce avoidable legal ambiguity.

## Risks / Trade-offs

- [Windows may suppress focus stealing or notification delivery] → Keep the completion dialog in application state, request taskbar attention, and verify minimized behavior on a real Windows desktop.
- [Audio device unavailable] → Treat sound as optional delivery and preserve the visual alert.
- [Clock adjustment, damaged file, or forced process exit] → Validate snapshots, use dual-clock discrepancy detection, atomic writes, and test restart/wake paths.
- [Native package works in CI but not after installation] → Require a clean Windows install/launch check before publishing; test both the app image and MSI.
- [Unsigned Windows installer triggers warnings] → Disclose this in download instructions and add signing when the owner has a certificate; do not imply an unsigned package is trusted by Windows.
- [Noncommercial license limits some contributions and redistribution] → State terms plainly and settle copyright holder/contact before release.

## Migration Plan

Replace the starter UI and example-only tests in place while preserving the existing module split. Add the controller and desktop adapters, then documentation and release automation. Run deterministic timer tests and Windows manual checks before creating a version tag. Publish only after the license/contact and all release gates are complete. If a packaged release fails after publication, withdraw the affected download and publish a corrected version; retain the tag and incident note for traceability.

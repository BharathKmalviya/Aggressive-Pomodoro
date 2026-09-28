# Design

## Context

The Kotlin Toolchain 0.12.2 project has a `jvm/app` entry point in `desktopApp` and a JVM-targeted KMP `shared` library with Compose UI. The starter screen has been replaced during this change. See `proposal.md` and the delta specs for behavior. The first packaged target is Windows; release acceptance remains open.

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
- Unattended installation, background update polling, or a persistent tray service.

## Decisions

### 1. One session reducer and explicit effects

Use a single session reducer in `shared` with phase (`focus`, `short break`, `long break`), status (`idle`, `running`, `paused`, `awaiting acknowledgement`), cycle count, phase ID, remaining duration/deadline, pending completion events, and settings. Commands (`start`, `pause`, `resume`, `reset`, `skip`, `tick`, `acknowledge`) produce a new state; recovery is a separate reducer entry point. The UI never calculates transitions itself. The desktop controller derives persistence and alert effects from accepted state changes.

This keeps the Compose layer thin and avoids separate timer and dialog booleans drifting apart. Alternative considered: a simple `LaunchedEffect` that decrements an integer; it is easier initially but drifts under delayed UI updates and makes restart recovery fragile.

### 2. Deadline-based timing with dual clock checks

During one process lifetime, use monotonic elapsed time to calculate the running phase deadline, and a wall-clock anchor for persistence. On wake or relaunch, reconcile from the saved wall deadline once; cap catch-up at one phase and start any automatic next phase at reconciliation time. Detect a substantial wall/monotonic discrepancy while both are available, pause, and show a resolution prompt. Persist a versioned snapshot after commands and transitions, with throttled checkpointing while running; never write every UI frame. The display can update once per second from derived remaining time.

This avoids accumulated tick drift while respecting real elapsed time across sleep and restart. Alternative considered: a wall-clock-only deadline; manual clock changes can cause silent early or late completion. A monotonic-only deadline cannot survive a process restart. The exact discrepancy threshold and checkpoint cadence are implementation constants documented alongside the tests.

### 3. Bounded alert queue and desktop adapter

Assign every phase a unique ID and store completion events until acknowledged. A completion is emitted once per phase ID. Automatic mode starts the next phase immediately, but if that next phase ends before prior acknowledgement, the reducer holds progression and retains both events. The dialog displays the event queue and its current next-action state. Confirmation mode waits for the explicit start action. Desktop effects attempt bundled sound and native taskbar attention; the in-app dialog remains available when either delivery path fails. Audio failures never block progression.

This gives an assertable rule for unattended use and prevents unlimited hidden cycles. Alternative considered: always chaining phases while unattended; that could generate many repeated alerts and inflated focus counts.

### 4. Small local persistence and single process owner

Store a versioned settings/session snapshot under the per-user application data directory. Use an atomic replace where supported, validate all loaded values, and fall back to safe defaults with a user-visible recovery message on invalid data. Keep a process-level lock on the data directory so two instances cannot independently run and alert for the same session; the second launch informs the user and exits, or activates the first instance if that is straightforward to support.

This avoids a database dependency for one small state record. Alternative considered: a database or synchronized file writes on every tick; both add cost without a user benefit.

### 5. Windows-native release pipeline

Keep Kotlin Toolchain as the build system. Use `https://github.com/BharathKmalviya/Aggressive-Pomodoro` as the public release destination, `main` as the default branch, and immutable `vMAJOR.MINOR.PATCH` tags for releases. On each push to `main`, cheaply compare `version.properties` with published releases and remote tags. Build only when the semantic version is unpublished; stop on a conflicting tag and never retarget one. Build its executable JAR, then use a pinned Windows JDK `jpackage` step to create and smoke-test an app image and MSI with an embedded runtime. The Windows job must verify that the executable JAR launches correctly through `jpackage`; if the toolchain's JAR layout is incompatible, adapt the packaging input or use the smallest supported alternate packaging step, with that decision recorded before release. Pin the packaging JDK/WiX versions, create a SHA-256 checksum, and publish assets from the validated commit only after tests, app-image launch, installer creation, and a clean Windows installation/launch check pass. Give the app an icon, semantic version, and About version. Unsigned package warnings and the later signing path belong in release docs.

This avoids a build-system migration solely for packaging. Alternative considered: moving the project to Gradle's Compose native distributions, which would add a second build migration to the initial product work. A JAR alone requires users to install Java and does not meet the download requirement.

### 6. MIT project policy

Use the standard MIT License with Copyright (c) 2026 Bharath Malviya. Describe the project as open source and state that commercial use is permitted under the license's notice condition. Keep README, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, architecture, verification, and release documentation consistent with these terms. Contribution guidance makes clear that contributors retain their copyrights; there is no inbound contributor license agreement.

MIT permits use in any context under one standard license. A custom license would introduce avoidable legal ambiguity.

### 7. Local task focus and daily progress

Keep tasks, selected task, and daily totals in one immutable product state with the timer. Capture the selected task when a focus phase starts so a later selection change does not redirect credit for work already underway. Credit a task and local-calendar daily total only on a completed focus transition. Store them in the same atomic snapshot as the timer. Render timer and task panels side by side when space permits and stack them in narrow windows. Use separate sound settings for short button feedback and phase completion.

Alternative considered: separate task and report files; that could persist a completed phase without its related task or daily credit after an interrupted write.

### 8. Aggressive feedback and edge-case hardening

Use an opt-out `aggressiveAlertsEnabled` preference, independent of sound. The desktop controller repeats the current pending event's attention/sound effect every ten monotonic seconds, without adding events or completion credit. New completions take priority over reminders; acknowledgement resets the reminder interval and clearing the queue stops reminders. Restored pending events get one initial attention attempt. Delayed ticks emit at most one reminder, never a catch-up burst. Adapter exceptions must not interrupt state changes or saving. Audio runs off the UI thread with one active completion clip and throttled click cues; expose alarm preview and playback failure feedback.

Use near-black panels, high-contrast red focus and green break accents, squared controls, a large responsive clock, concrete phase instructions, an explicit paused state, and final-minute urgency. Completion dialogs identify both the completed and current phase. A mute action is available directly in the completion dialog. Secondary dialogs yield to completion alerts. Reset/skip confirmations bind to a phase ID and close when it changes; paused progress also requires deliberate confirmation. Show the actual captured task separately from the next selected task.

Reconcile elapsed time before user commands so expiration cannot be erased by a late pause/skip/reset. Capture a fresh clock sample on every desktop command. Preserve unassigned active focus across restart, clear a deleted active task reference, credit surviving tasks even when marked done mid-block, and reject impossible waiting/running snapshot combinations. Legacy preferences migrate with aggressive reminders enabled. Pause rather than extend a block when a backward wall-clock change would increase remaining time. Keep the asynchronous writer alive after a failed close save so continuing or retrying remains safe. Regression checks use injected time and temporary storage; audible quality, keyboard focus, and responsive Windows rendering require the documented manual checks.

## Risks / Trade-offs

### Manual Windows update flow

The About screen links to the public repository and owns entry to a manual update dialog. Only a user action contacts the fixed GitHub latest-stable-release API; no tasks, history, or timer state are uploaded. The desktop updater handles network and file IO with bounded responses/timeouts, semantic version comparison, exact repository/version asset URLs, and SHA-256 verification. Missing, older, prerelease, malformed, and offline responses have explicit outcomes. Downloads show progress, permit cancellation, remove partial files, and never run unverified content. Retrying cannot start overlapping operations.

The install action asks for confirmation, re-verifies the cached installer, persists the latest session and creates a dated local backup, then opens the interactive Windows installer and exits. Failed verification, saving, backup, or installer launch keeps the app and persistence usable. A stable MSI UpgradeCode matches the first published installer so later releases upgrade the same product. Windows installation/UAC and relaunch remain user-controlled. Completion alerts preempt About/update dialogs while background downloads may continue.

- [Windows may suppress focus stealing or notification delivery] → Keep the completion dialog in application state, request taskbar attention, and verify minimized behavior on a real Windows desktop.
- [Audio device unavailable] → Treat sound as optional delivery and preserve the visual alert.
- [Clock adjustment, damaged file, or forced process exit] → Validate snapshots, use dual-clock discrepancy detection, atomic writes, and test restart/wake paths.
- [Native package works in CI but not after installation] → Require a clean Windows install/launch check before publishing; test both the app image and MSI.
- [Unsigned Windows installer triggers warnings] → Disclose this in download instructions and add signing when the owner has a certificate; do not imply an unsigned package is trusted by Windows.
- [Third-party contributions and assets may have incompatible terms] → Require contributors to identify their rights and third-party licenses before inclusion.

## Migration Plan

Replace the starter UI and example-only tests in place while preserving the existing module split. Add the controller and desktop adapters, then documentation and release automation. Run deterministic timer tests and Windows manual checks before raising the version on `main`. Publish only after the MIT license and all release gates are complete. If a packaged release fails after publication, withdraw the affected download and publish a corrected version; retain the tag and incident note for traceability.

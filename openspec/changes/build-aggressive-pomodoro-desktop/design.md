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
- Unattended installation, background update polling, or an independently installed background service.

## Decisions

### 1. One session reducer and explicit effects

Use a single session reducer in `shared` with phase (`focus`, `short break`, `long break`), status (`idle`, `running`, `paused`, `awaiting acknowledgement`), cycle count, phase ID, remaining duration/deadline, pending completion events, and settings. Commands (`start`, `pause`, `resume`, `reset`, `skip`, `tick`, `acknowledge`) produce a new state; recovery is a separate reducer entry point. The UI never calculates transitions itself. The desktop controller derives persistence and alert effects from accepted state changes.

This keeps the Compose layer thin and avoids separate timer and dialog booleans drifting apart. Alternative considered: a simple `LaunchedEffect` that decrements an integer; it is easier initially but drifts under delayed UI updates and makes restart recovery fragile.

Saved rules refresh the full duration of idle and waiting phases in the reducer. Running and paused phases retain their duration and progress; explicit reset reloads the current phase's duration from saved settings and clears deadlines without credit. Recovery also refreshes valid legacy idle/waiting snapshots whose cached duration is stale. Completion reconciliation still runs before settings commands, preserving earned credit and already-started successors. Settings copy explains save/reset timing; tests cover all phase types, paused resume, waiting acknowledgement, invalid edits, preferences, task/history credit, and serialized persistence/relaunch.

### 2. Deadline-based timing with dual clock checks

During one process lifetime, use monotonic elapsed time to calculate the running phase deadline, and a wall-clock anchor for persistence. On wake or relaunch, reconcile from the saved wall deadline once; cap catch-up at one phase and start any automatic next phase at reconciliation time. Detect a substantial wall/monotonic discrepancy while both are available, pause, and show a resolution prompt. Persist a versioned snapshot after commands and transitions, with throttled checkpointing while running; never write every UI frame. The display can update once per second from derived remaining time.

This avoids accumulated tick drift while respecting real elapsed time across sleep and restart. Alternative considered: a wall-clock-only deadline; manual clock changes can cause silent early or late completion. A monotonic-only deadline cannot survive a process restart. The exact discrepancy threshold and checkpoint cadence are implementation constants documented alongside the tests.

### 3. Bounded alert queue and desktop adapter

Assign every phase a unique ID and store completion events until acknowledged. A completion is emitted once per phase ID. Automatic mode starts the next phase immediately, but if that next phase ends before prior acknowledgement, the reducer holds progression and retains both events. The dialog displays the event queue and its current next-action state. Confirmation mode waits for the explicit start action. Desktop effects attempt bundled sound and native taskbar attention; the in-app dialog remains available when either delivery path fails. Audio failures never block progression.

This gives an assertable rule for unattended use and prevents unlimited hidden cycles. Alternative considered: always chaining phases while unattended; that could generate many repeated alerts and inflated focus counts.

### 4. Small local persistence and single process owner

Store a versioned settings/session snapshot under the per-user application data directory. Use an atomic replace where supported, validate all loaded values, and fall back to safe defaults with a user-visible recovery message on invalid data. Keep a process-level lock on the data directory so two instances cannot independently run and alert for the same session. A second launch sends a bounded local activation request to the owner and exits without loading a snapshot or showing a normal Already running modal. Use atomic request/acknowledgement files in that same per-user directory, with unique bounded tokens, off-thread IO and an event-thread window callback. Keep requests arriving during owner startup until the UI is ready; clear stale activation data only while owning the lock. No socket listener, external network or second controller is needed. An acknowledgement confirms that the owner accepted activation, not that Windows granted foreground focus. Activation restores hidden/minimized state and cancels only an uncommitted close chooser; completion/save-error dialogs retain priority. If acknowledgement times out or local delivery fails, keep ownership intact and show actionable fallback feedback. Deterministic temporary-directory tests cover exclusive ownership, startup requests, repeated activation, malformed/stale data, cleanup and unavailable owners; real launcher focus remains manual acceptance.

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

### 9. Window close and background lifetime

By default, each title-bar close or Alt+F4 request opens one keyboard-accessible choice: Run in background, Exit, or Cancel, regardless of timer status. Background mode hides the existing Compose window only after a desktop tray icon has been installed; the window composition, session controller, tick coroutine, serialized writer, audio and single-instance lock remain alive. Do not dispose/recreate the window. Without tray support or after tray installation failure, offer Minimize instead and retain the taskbar restore path.

The desktop AWT tray adapter owns the bundled icon, Show and Exit menu actions, completion notifications, and cleanup. Show restores the same window without changing timer state. Tray Exit first restores the window and asks for the same deliberate exit choice. Background completion attempts a tray notification and enabled sound without stealing foreground focus; its existing completion dialog remains available on restore. Tray removal restores a hidden window so the user cannot lose access. A new completion dismisses the close chooser, preserving alert priority; closing with an already pending alert still permits deliberate background or exit.

Exit selected explicitly or by a saved window-close choice freezes commands, waits for the final save, closes audio, removes the tray icon and releases ownership. Failed saving restores the window and retains retry/continue behavior. Update installation and Exit Anyway also remove the tray icon. The Windows upgrade smoke harness must request close, target the owned close-choice dialog and activate its Exit keyboard action for the new build; its missing close preference defaults to Ask, while the immutable older baseline retains its existing direct-close flow. Native tray, keyboard and notification acceptance remains manual.

Store an optional pure `CloseBehavior` preference (Ask, Background, Exit) in the existing format-3 settings snapshot, using stable IDs. Missing or unknown values default to Ask without discarding valid timer/tasks/history. The unchecked-by-default Remember my choice checkbox saves only on the selected Background/Minimize or Exit action; Cancel/Escape/window dismissal never saves. Settings exposes When closing the window so Ask every time or either saved action can be selected again. A small desktop policy maps saved Background to taskbar Minimize when the tray is unavailable; the fallback does not rewrite the saved preference. Tray Exit always restores the close choice regardless of this setting. Use the normal settings command and serialized save path; remembered Exit is included in the final save before shutdown. Timer progress, phase IDs, tasks and alerts remain governed by the existing reducer, including deadline reconciliation.

### 10. Tray timer controls and app shortcuts

Project immutable product state into a small desktop presentation model with phase/countdown, status, today's completed blocks/minutes, saved alarm/reminder checkbox values and enabled actions. Update the existing AWT menu and tooltip on the desktop event thread, setting native properties only when they change. Use ASCII punctuation in native menu labels to avoid unsupported ellipsis glyphs. Keep timer state and persistence in the existing controller; tray Start/Pause/Resume emits the same phase-bound commands as the timer panel.

Tray Reset/Skip restores the same window and requests the timer panel's existing confirmation, including for an idle phase. Bind requests to a phase ID, reconcile time before opening, and discard requests/confirmations on phase change, pending completion, close choice or hidden/minimized window. Reports, Settings, About and updates use transient sequenced requests into the existing app dialogs. Only one secondary dialog opens; pending completions and close/save-error dialogs take priority. The update shortcut checks only when no existing check/download/available/ready operation needs viewing. No download or installation is triggered from the tray.

Alarm and repeat-reminder toggles copy only their field from the controller's latest saved settings and dispatch through the same audio-cancellation/persistence path as Settings. Refresh individual saved Settings fields so unrelated unsaved edits survive tray toggles. Consume transient requests once to prevent replay after cancellation or layout changes. An uncommitted close choice must not disable the tray: Show, timer, preference, navigation or background actions cancel that chooser without saving its unchecked or checked preference. Explicit tray Exit still asks. Actual final-save, save-error and installation states restrict conflicting actions; label the reason and retain a reachable Show/recovery path. Pending completions offer Review and retain available mute/reminder controls. Make phase/status open the existing window and daily totals open Reports when permitted, group Reset/Skip under Timer options and sound/reminders under Alerts, and expose direct Run in background. Use native Windows keyboard and scaling behavior rather than a separately themed popup with its own focus/lifetime. No new snapshot fields or background process are needed. Deterministic tests cover close-choice availability, genuine restrictions/recovery labels, projections, phase-bound deadline/stale actions and persisted toggles; native popup keyboard, scaling, glyphs and tooltip behavior remain manual acceptance.

## Risks / Trade-offs

### Selectable bundled alarms (2026-10-09)

Represent sound choices as a fixed pure-domain catalog with stable storage IDs and user-friendly labels. Add separate focus-complete and break-complete settings; both break types share one selection. Keep original generated phase sounds as defaults for old/new profiles. Persist optional sound IDs in snapshot format 3; absent/unknown IDs fall back independently to the original alarm, preserving valid timer/tasks/history instead of triggering whole-snapshot recovery.

Settings edits remain local until Save Rules. Each keyboard-accessible selector offers the original plus nine supplied cues and previews its current unsaved selection, even if completion sound is muted. Cancel/save/background/completion preemption stops preview through existing playback ownership. Actual immediate/restored/reminder alarms resolve the saved choice by completed phase, never the current successor. Selecting a new saved sound invalidates existing playback so a stale choice cannot finish opening later.

Convert supplied MP3/WAV inputs to checked-in mono 44.1 kHz signed 16-bit PCM cues, capped at eight seconds with bounded gain and endpoint fades. Keep originals intact and local; record source names, hashes, attribution/license links, conversion parameters and reproduction command. The app bundles adapted cues and credits; it has no runtime codec, file-import flow or audio download. Decode/cache only selected cues on IO with a strict byte bound; missing/bad resources play the original alarm with visible feedback. Preserve serialized non-overlapping playback, ten-second reminders, mute, cancellation and click priority. Deterministic tests verify every resource, requested sound routing, fallback/cache/cancellation, snapshot migration and saved-setting acceptance; native sound quality and picker keyboard behavior remain manual.

### Sound and motion refinement (2026-10-01)

Bundle Kenney Interface Sounds `click_003` converted from CC0 Ogg to mono 44.1 kHz 16-bit PCM WAV, with original license and source recorded. Load locally on IO with a generated fallback; no runtime download or codec dependency. Tune generated multi-pulse completion audio into an ascending focus-complete chord and a sharper return-to-focus break alarm. Preview each independently. Route the completed event's phase (including restored events and reminders), never infer sound from the current successor.

Detach prior audio clips under the ownership lock before closing outside it. Completion callbacks release asynchronously to avoid Java Sound callback/lock deadlocks; idempotent ownership prevents double cleanup or stale callbacks clearing a replacement. Mute/acknowledgement cancel delayed opens. Disabling click feedback invalidates queued/opening clicks. Stop preview on Settings dismissal/save/completion preemption. Retain visual fallback and alarm priority.

Animate only presentation: short accent transitions, status/directive fades with small vertical movement, one-shot final-minute emphasis, and smoothed progress. Keep countdown and controls immediate and stable. Reset progress by phase ID; pause, waiting, and reduced motion snap to truthful values. Persist `reduceMotion` as an optional strict boolean in format 3, default false for old snapshots, preserving downgrade readability. Reduced motion bypasses custom transitions and emphasis. Use Compose value animations (https://developer.android.com/develop/ui/compose/animation/value-based); the opt-out follows W3C interaction-animation guidance (https://www.w3.org/WAI/WCAG21/Understanding/animation-from-interactions). Verify audio races and snapshot compatibility automatically; audible quality, animation feel, keyboard, resize, and sleep remain manual Windows acceptance.

### Manual Windows update flow

The existing explicit release check also reads the API's release body. Return the latest version and bounded notes separately from an optional newer installer so current/older results still have notes without offering a downgrade. Render notes as selectable plain text in the existing scrollable update dialog; links and HTML remain inert. Missing, null, malformed, or blank bodies show an explanation without blocking a valid update. Limit display to 32,768 characters with an explicit truncation notice. Preserve checked notes through download, cancellation, retry, and installation failures; a fresh check clears old metadata so failed checks cannot display stale notes. Release notes never open a browser or trigger an additional request. Completion-alert priority and keyboard-accessible controls remain unchanged.

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

On 2026-10-09 the owner requested v0.3.2 publication after the close/background and selectable-alarm source changes passed automated checks, with native Windows acceptance disclosed as pending. This release follows the existing Windows build/test/runtime-package/clean-install/baseline-upgrade/checksum/license gates. The request permits publication before manual acceptance for this release; it does not mark those scenarios passed. Keep the change active and record immutable tag/asset readback plus independent checksum verification after publication.

On 2026-10-09 the owner additionally requested release after implementing saved close options. Prepare v0.3.3 with the expanded tray and remembered close choices, retaining automated Windows build/tests/runtime packaging/clean MSI install and launch/baseline upgrade/data preservation/checksum/license gates. Record native acceptance as pending, verify the immutable tag and freshly downloaded assets, and keep the change active for manual checks. Missing close preferences in the baseline profile retain its Ask flow; format 3 and the installer UpgradeCode remain unchanged.

The owner's subsequent disabled-menu report continues that tray improvement/release scope as a v0.3.4 correction. Keep the same automated release gates, immutable tag/asset readback and independently verified downloads. The supplied screenshot establishes the issue; deterministic/native-menu-resource tests establish state and event routing only. Updated popup appearance, keyboard/scaling, close-chooser cancellation and recovery need manual Windows acceptance and do not close the active change.

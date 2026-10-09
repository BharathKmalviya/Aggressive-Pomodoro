# Architecture

## Ownership

- `shared/src/domain/Session.kt` contains the pure timer reducer, phase state, settings, commands, deadlines, and completion queue.
- `shared/src/domain/Product.kt` combines timer transitions with local task progress and daily totals. A completed focus block updates all three in one immutable state transition.
- `shared/src/presentation/` renders state and emits commands. Compose does not calculate timer transitions or write files.
- `desktopApp/src/presentation/DesktopSessionController.kt` supplies the current clocks and local date, schedules snapshots, and routes completion effects.
- `desktopApp/src/data/AppStore.kt` validates and atomically replaces a versioned local snapshot. Version 1 timer-only and version 2 task snapshots migrate to version 3 on the next save, enabling aggressive reminders by default while preserving existing sound preferences.
- `desktopApp/src/platform/` owns monotonic and wall clocks, single-instance locking, taskbar attention, native tray resources, and generated completion/click audio.

## Timer rules

The session begins as an idle focus phase. `Start` anchors a monotonic and wall deadline. The UI derives remaining time from the monotonic deadline; it never decrements a counter per tick. Pause captures remaining time, and resume creates new deadlines. Each phase has a unique ID, so repeated ticks cannot complete it twice.

Completed focus increments the cycle count and the task captured when that focus block started. Changing the selected task during a countdown affects the next focus block, not credit for the running block. The fourth completed focus block starts a long break by default. Skip advances without focus credit. Settings are validated before acceptance. Saved durations immediately refresh idle/waiting phases; running and paused phases preserve duration and progress until reset or completion. Reset reloads the current phase's full duration from the latest saved settings, clears deadlines and captured task ownership, and earns no credit. Sound, click, reminder, motion, transition, and cycle preferences are accepted on save; transition/cycle rules govern subsequent completions, without starting a waiting phase or altering active countdown progress.

Every desktop command samples current time. Reducers reconcile elapsed time before applying commands, so a late pause/reset/skip cannot erase an earned block or act on its successor. Settings at the boundary affect future phases after the expired phase transitions under its previous settings. Task commands also reconcile first so a selection at an expired break cannot retroactively change the task captured by its automatic focus start.

The UI labels captured and next-selected tasks separately. Unassigned focus stays unassigned across restart. Removing the active task clears only its captured ID; old snapshots with a deleted ID are repaired without discarding the remaining product. Marking a captured task done still credits an already-started block when it completes. Invalid waiting states and running snapshots with two queued completions are rejected with the recovery explanation.

The report projects the stored totals onto the seven local calendar days ending today. Dates without a completed focus block show zero; older records remain stored but do not appear in that window.

Completion events remain in a queue until acknowledged. Automatic mode starts the next phase immediately. If it completes before the first event is acknowledged, the reducer stops at two queued events and waits. Confirmation mode waits after every phase. The dialog is rendered from the queue, so redraws and minimize/restore do not create new logical alerts.

Aggressive reminders are a desktop effect: while an event is pending, the controller retries attention and enabled sound every ten monotonic seconds. New completions and restored queues get an immediate attempt; delayed ticks never replay missed reminders. Acknowledgement resets the interval, and clearing the queue stops reminders. The preference is independent of sound. A callback exception cannot interrupt the reducer or persistence. Audio resources run off the UI thread, completion clips cannot overlap, and click cues are throttled. Preview deliberately plays regardless of the saved sound toggle. Acknowledgement and mute cancel current playback; playback failure remains visible.

Completion dialogs take priority over Settings, Reports, About, and task deletion confirmations. All UI timer controls carry the phase ID and acknowledgement carries the pending event ID; the reducer rejects stale intents even before the UI can redraw. Reset/skip confirmations disappear when their phase changes or a completion is pending, and protect paused progress too. The timer scrolls in constrained windows and uses state-specific instructions without flashing or forcing foreground focus.

## Sound and motion

Desktop audio selects its motif from the completed event's phase, including restored events and reminders. Focus completion uses an ascending three-pulse chord; both break completions use a brighter return motif. A Kenney CC0 click is decoded from bundled PCM WAV on IO, with a generated fallback and no network/codec dependency. Original attribution/license ships in `desktopApp/resources/sounds/`. Settings previews each alarm independently of sound preferences. Closing/saving Settings or completion preemption cancels preview, without canceling a real completion alarm that has replaced it.

The audio adapter detaches clips under its ownership lock, releases them outside it, and schedules natural-finish cleanup away from Java Sound callback threads. Native open/start/replacement work is serialized on a separate IO lock, while UI invalidation remains immediate. Wrapped clip ownership makes disposal idempotent and lets replacement wait for in-flight mute cleanup before starting. Generation checks and coroutine cancellation reject late opens and stale STOP callbacks; disabling clicks invalidates both queued and opening cues. Alarms retain priority over clicks. Cleanup or output failures preserve timer operation and visual alerts.

Compose animates accents over 220 ms, status/directive entrances over 180 ms, running progress over 200 ms, and final-minute emphasis once per entry over 340 ms. Countdown text and control targets remain immediate; no animation emits a domain command. Progress resets by phase ID and snaps when paused, waiting, or motion is reduced. `reduceMotion` is an optional strict boolean in snapshot format 3: missing old values default false, saved true survives restart, and malformed values use the existing safe-recovery path. Older v0.2.0 readers ignore the extra property, preserving format-3 downgrade readability. The preference disables the custom animations; normal Material controls retain their built-in feedback.

Implementation references: [Compose value animations](https://developer.android.com/develop/ui/compose/animation/value-based), [Java Sound Clip lifecycle](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/sound/sampled/Clip.html), and [W3C interaction animation opt-out guidance](https://www.w3.org/WAI/WCAG21/Understanding/animation-from-interactions). Native Windows quality and usability remain manual checks.

## Recovery and clocks

The snapshot stores a wall deadline for a running phase. On restart, the reducer reanchors it to the new process's monotonic clock. If that wall deadline passed, one phase completes and at most one subsequent phase starts at recovery time. The app does not backfill multiple cycles.

Valid older snapshots may have a stale cached duration after rules were saved. Recovery refreshes idle/waiting durations from saved settings without changing phase IDs, completions, tasks, or history; the desktop controller queues the repaired snapshot. Running and paused snapshots keep their planned duration and progress. This requires no snapshot format change.

A material wall/monotonic disagreement is 120 seconds. A positive disagreement after a monotonic gap over 10 seconds is treated as likely sleep and reconciled against the wall deadline; a rapid jump or material backward disagreement pauses the timer. On restart, a wall deadline that would increase the saved remaining duration also pauses with an explanation rather than extending the block. Forward clock edits during a long process stall remain indistinguishable from sleep under this heuristic. Real Windows sleep and manual clock changes still require the hands-on checks in [TESTING.md](TESTING.md).

Writes are serialized on an IO coroutine with a conflated pending snapshot. Important transitions are queued immediately; running ticks checkpoint at roughly 15-second intervals. App shutdown waits for the latest state to be written. A save error is shown in the UI. Snapshot replacement first writes a temporary file and then moves it over the prior version, using an atomic move where the filesystem supports one.

Closing freezes commands/ticks while the final save is pending. A failed save leaves the writer usable and returns to the app's retry flow; successful shutdown closes audio and releases single-instance ownership. Actual Windows sound, focus navigation, and attention delivery are acceptance checks, not claims derived from unit tests.

Every window-close request opens the desktop `CloseDialog`, independent of timer status. Background is the primary action; Exit is explicit and Cancel/Escape keeps the window open. The dialog also supports B for background/minimize and E for Exit. Secondary settings/report/about/update/task-delete dialogs yield to this choice; a new completion dismisses the choice and shows its completion alert. Closing with an already pending event can still deliberately background or exit without acknowledging or losing it.

Background mode sets the existing Compose `Window` invisible, keeping its composition, controller, tick coroutine, writer, audio and lock alive. In-app completion dialogs are suppressed while hidden, minimized, or while the close chooser is shown; their events stay in the product state. `DesktopTray` owns an AWT icon and Show/Exit menu; icon loading uses IO and tray/window operations use the desktop event thread. Show restores this window, never creates another timer. Hidden completions attempt native tray notifications and configured sound without forcing foreground focus. If the tray cannot be installed, the choice minimizes to the taskbar instead; tray-icon removal restores a hidden window. Tray Exit restores and prompts. Background is not a stored preference and does not install a Windows service or run after explicit exit.

Normal exit, Exit Anyway, and successful update installation remove the tray icon. Save failure restores visibility before showing retry/continue feedback and leaves the writer usable. The shared product theme is available to the desktop close dialog so app-owned windows use the same colors. Native tray delivery, restoration, keyboard behavior, and display scaling remain manual Windows checks.

## Manual app updates

`DesktopUpdateController` owns check/download/ready/error UI state; `GitHubUpdateService` performs blocking network and file work on IO. About provides the manual entry point and public repository link. There is no startup request or periodic polling. The updater uses GitHub's [latest release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release), rejects draft/prerelease or nonnumeric version tags, and compares numeric major/minor/patch values. It only accepts the exact repository's versioned MSI and checksum assets, with HTTPS redirects restricted to GitHub release servers.

Responses have size limits, connection/read timeouts, and cancellation checks. The MSI is hashed while downloading, compared to the exact filename in `SHA256SUMS.txt`, and retained only when valid. The process retains verification metadata in memory: an editable sidecar cannot authorize installation, and a restart requires a new download. Before installation, the file is hashed again. Downloading and checking never mutate timer state.

`UpdateCheckResult` carries latest version and release notes independently from an optional newer installer. The existing explicit request reads GitHub's `body`; current/older results retain notes and never offer a downgrade. The update dialog expands selectable plain text in its existing scroll area without a browser callback, HTML execution, active links, or an additional request. Missing/null/non-string/blank notes show an explanation without blocking downloads. A 32,768-character preview limit adds a truncation notice and avoids splitting Unicode surrogate pairs. Notes survive download progress, cancellation, retry, and installation errors; a new check clears previous metadata before IO so offline failures cannot show stale notes. Completion alerts still close secondary dialogs.

After explicit install confirmation, the session controller saves a final snapshot and runs a preparation callback while keeping its writer alive. That callback creates and byte-verifies a uniquely named backup in the local `backups` directory, then launches interactive `msiexec /i` using a shell-free argument list. Only a successful callback lets shutdown finish. Save, backup, verification, or launch failures keep the app open; a later retry remains possible. Windows installation/UAC and reopening the app stay under user control. The MSI UpgradeCode is pinned to the first released product and validated by packaging.

## Data boundaries

The app has no server, account, telemetry, cloud sync, background process after exit, or system-wide app blocker. Windows is the first package target. Task and report data stay in the same per-user snapshot as the session so a completed focus block cannot be saved without its task and daily credit.

User-triggered update requests send ordinary HTTP metadata to GitHub and its release CDN, but no tasks, history, settings, or timer data. About's explicit repository link opens the fixed public repository; release notes stay inside the app.

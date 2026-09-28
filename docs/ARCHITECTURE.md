# Architecture

## Ownership

- `shared/src/domain/Session.kt` contains the pure timer reducer, phase state, settings, commands, deadlines, and completion queue.
- `shared/src/domain/Product.kt` combines timer transitions with local task progress and daily totals. A completed focus block updates all three in one immutable state transition.
- `shared/src/presentation/` renders state and emits commands. Compose does not calculate timer transitions or write files.
- `desktopApp/src/presentation/DesktopSessionController.kt` supplies the current clocks and local date, schedules snapshots, and routes completion effects.
- `desktopApp/src/data/AppStore.kt` validates and atomically replaces a versioned local snapshot. Version 1 timer-only snapshots are read and upgraded to version 2 on the next save.
- `desktopApp/src/platform/` owns monotonic and wall clocks, single-instance locking, taskbar attention, and bundled audio playback.

## Timer rules

The session begins as an idle focus phase. `Start` anchors a monotonic and wall deadline. The UI derives remaining time from the monotonic deadline; it never decrements a counter per tick. Pause captures remaining time, and resume creates new deadlines. Each phase has a unique ID, so repeated ticks cannot complete it twice.

Completed focus increments the cycle count and the task captured when that focus block started. Changing the selected task during a countdown affects the next focus block, not credit for the running block. The fourth completed focus block starts a long break by default. Skip advances without focus credit. Settings are validated before acceptance and only change the duration of a newly created phase.

The report projects the stored totals onto the seven local calendar days ending today. Dates without a completed focus block show zero; older records remain stored but do not appear in that window.

Completion events remain in a queue until acknowledged. Automatic mode starts the next phase immediately. If it completes before the first event is acknowledged, the reducer stops at two queued events and waits. Confirmation mode waits after every phase. The dialog is rendered from the queue, so redraws and minimize/restore do not create new logical alerts.

## Recovery and clocks

The snapshot stores a wall deadline for a running phase. On restart, the reducer reanchors it to the new process's monotonic clock. If that wall deadline passed, one phase completes and at most one subsequent phase starts at recovery time. The app does not backfill multiple cycles.

A material wall/monotonic disagreement is 120 seconds. A long monotonic gap over 10 seconds is treated as likely sleep and reconciled against the wall deadline; a rapid clock jump pauses the timer and asks the user to resume or reset. Real Windows sleep and manual clock changes still require the hands-on checks in [TESTING.md](TESTING.md).

Writes are serialized on an IO coroutine with a conflated pending snapshot. Important transitions are queued immediately; running ticks checkpoint at roughly 15-second intervals. App shutdown waits for the latest state to be written. A save error is shown in the UI. Snapshot replacement first writes a temporary file and then moves it over the prior version, using an atomic move where the filesystem supports one.

## Boundaries

The app has no server, account, telemetry, cloud sync, background process after exit, or system-wide app blocker. Windows is the first package target. Task and report data stay in the same per-user snapshot as the session so a completed focus block cannot be saved without its task and daily credit.

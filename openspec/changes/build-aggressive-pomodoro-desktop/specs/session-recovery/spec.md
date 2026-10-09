# Spec Delta

## Purpose

Defines local recovery and interruption behavior so desktop sleep, restart, invalid saved data, and clock changes do not silently corrupt a Pomodoro session.

## ADDED Requirements

### Requirement: Safe recovery and retry boundaries
The application SHALL pause with a clock explanation when restart recovery would increase the saved remaining duration. A failed save during exit SHALL keep persistence available for continued use and a later exit retry.

#### Scenario: Backward clock on restart
- **WHEN** the recovered wall deadline would extend the remaining duration beyond its saved value
- **THEN** the timer pauses at bounded saved remaining time and offers resume or reset

#### Scenario: Exit save fails
- **WHEN** the final exit save fails and the user continues using the app
- **THEN** later edits can still be saved and a subsequent successful exit waits for the latest snapshot

### Requirement: Local persistence
The application SHALL store valid settings and session state locally, without an account or network connection, and restore them on restart. A corrupt or unsupported saved state SHALL be handled with a clear recovery message and safe defaults rather than a startup crash.

#### Scenario: Restart during paused focus
- **WHEN** the application restarts after a paused focus phase
- **THEN** it restores that paused phase and its remaining time

#### Scenario: Corrupt saved state
- **WHEN** the saved session cannot be read or validated
- **THEN** the application starts at an idle default focus phase and tells the user that session recovery failed

### Requirement: Sleep and restart reconciliation
The application SHALL reconcile a running phase using elapsed real time after Windows sleep or application restart. If the deadline passed, it SHALL complete that phase once, alert the user, and start at most one subsequent phase after reconciliation. It SHALL NOT fabricate multiple completed focus phases for time spent away.

#### Scenario: Resume after expired focus
- **WHEN** Windows wakes after a running focus phase's deadline
- **THEN** the app records one completed focus phase, alerts once, and applies the selected transition mode from the wake time

#### Scenario: Relaunch after expired break
- **WHEN** the app restarts after a running break's deadline
- **THEN** it records one break completion and presents the pending transition without backfilling further cycles

### Requirement: Clock-change safety
The application SHALL detect a material disagreement between elapsed monotonic time and wall-clock time while running. On detection, it SHALL pause the session, explain the discrepancy, and let the user resume or reset rather than silently shortening or extending a phase.

#### Scenario: System clock jumps
- **WHEN** the system clock moves substantially while the application remains active
- **THEN** the timer stops advancing and the user is prompted to resume or reset the phase

### Requirement: Explicit exit behavior
The application SHALL ask on every window-close request whether to run in the background, exit, or cancel, including idle, paused and waiting states. It SHALL explain that alerts stop after Exit. Background mode SHALL retain the existing timer, persistence and single-instance ownership, hide the window only with an installed tray restore path, and offer taskbar minimization when the tray is unavailable. Only explicit Exit SHALL save and shut down. Tray Show SHALL restore the same window; tray Exit SHALL restore and ask for confirmation. Native tray removal SHALL restore a hidden window. Successful exit, forced exit and update installation SHALL remove tray resources. Saving failures SHALL keep the app usable and visible.

#### Scenario: Close running app
- **WHEN** the user requests to close the window in any timer state
- **THEN** the application offers background/minimize, Exit and Cancel; dismissing or cancelling preserves the window and session

#### Scenario: Background and restore
- **WHEN** the user chooses Run in background and later activates tray Show
- **THEN** the same window returns with elapsed timer progress, pending alerts, tasks and report credit preserved, without another session controller

#### Scenario: Unavailable or removed tray
- **WHEN** the tray cannot be installed or an installed tray icon is removed while the window is hidden
- **THEN** the window remains reachable through taskbar minimization or immediate restoration

#### Scenario: Minimize running app
- **WHEN** the user minimizes the window during a running phase
- **THEN** the countdown and deadline alerts remain active

### Requirement: Single session owner
The application SHALL prevent two simultaneously running processes from independently advancing and alerting for the same saved session.

#### Scenario: Second launch
- **WHEN** the user opens a second instance while one is already running
- **THEN** the second instance does not start another timer and the user receives a clear indication that the app is already open

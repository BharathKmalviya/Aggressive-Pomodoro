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
The application SHALL default to asking on window-close requests whether to run in the background, exit, or cancel, including idle, paused and waiting states. It SHALL explain that alerts stop after Exit. Users SHALL be able to remember Background/Minimize or Exit for later window-close/Alt+F4 requests and change this in Settings, including returning to Ask every time. Remember SHALL default to unchecked and SHALL save only when an action is chosen; Cancel or dismissal SHALL NOT save. Missing or unknown stored preferences SHALL default to Ask while preserving valid data. Background mode SHALL retain the existing timer, persistence and single-instance ownership, hide the window only with an installed tray restore path, and minimize when the tray is unavailable without rewriting the saved preference. Exit selected explicitly or by a saved close preference SHALL save and shut down. Tray Show SHALL restore the same window; tray Exit SHALL save and close directly without another confirmation regardless of the saved preference, including with an uncommitted close chooser open; it SHALL NOT save the uncommitted Remember selection. Native tray removal SHALL restore a hidden window. Successful exit, forced exit and update installation SHALL remove tray resources. Saving failures SHALL keep the app usable and visible.

#### Scenario: Close running app
- **WHEN** the user requests to close the window in any timer state with Ask every time selected
- **THEN** the application offers background/minimize, Exit and Cancel; dismissing or cancelling preserves the window and session

#### Scenario: Remember background or exit
- **WHEN** the user checks Remember my choice and chooses Background/Minimize or Exit
- **THEN** the chosen preference is saved with existing settings, subsequent X/Alt+F4 requests perform it without the chooser, and Settings can restore Ask every time without changing timer progress

#### Scenario: Cancel a remembered selection
- **WHEN** Remember my choice is checked but the user cancels or dismisses the chooser
- **THEN** no close preference is changed and the app continues normally

#### Scenario: Saved background without a tray and explicit tray exit
- **WHEN** Background is saved but the tray is unavailable, or the user explicitly activates tray Exit with any saved preference
- **THEN** window close minimizes to the taskbar without losing the saved choice, while tray Exit saves and closes directly without changing the saved preference

#### Scenario: Older or unknown close preference
- **WHEN** a valid snapshot has no close preference or contains an unknown value
- **THEN** the app uses Ask every time and retains the valid timer, tasks, history and other settings

#### Scenario: Direct exit with an uncommitted remembered choice
- **WHEN** the window-close chooser is open with Remember checked and the user selects tray Exit
- **THEN** the app saves and closes directly without another chooser, retaining the previously saved close preference, tasks, history and pending completion

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
The application SHALL prevent two simultaneously running processes from independently advancing and alerting for the same saved session. A second launcher or shortcut launch SHALL request activation of the existing window without an ordinary Already running modal and SHALL NOT load or change its snapshot. Activation SHALL restore a hidden/minimized window, cancel an uncommitted close chooser without saving its proposed preference, and retain pending completion/save-error priority. Requests during owner startup SHALL remain available until the UI can handle them. Failed activation SHALL retain ownership protection and provide actionable fallback feedback.

#### Scenario: Second launch
- **WHEN** the user opens a second instance while one is already running
- **THEN** the second instance requests the first window to restore and activate, then exits without another timer or an Already running modal

#### Scenario: Launcher activation during startup or interrupted use
- **WHEN** activation is requested before the owner's UI is ready, while it is hidden/minimized, or while a close choice or alert is open
- **THEN** the request is retained through startup, the same window is restored, an uncommitted close choice is cancelled without saving, and a completion or save-error dialog remains the action needing attention

#### Scenario: Existing owner cannot accept activation
- **WHEN** activation cannot be delivered or acknowledged within a bounded wait
- **THEN** the new process does not create a second timer or bypass ownership and offers a clear tray/taskbar/restart fallback

### Requirement: Useful state-driven tray menu
The installed tray SHALL show current phase, rounded-up countdown, timer status and today's completed blocks/minutes, and refresh its tooltip. Timer status SHALL open the same window and daily totals SHALL open Reports when permitted, rather than presenting ordinary summaries as disabled controls. It SHALL expose state-appropriate phase-bound Start/Pause/Resume, confirmed Reset/Skip grouped under Timer options, saved alarm and repeat-reminder toggles grouped under Alerts, direct Run in background, and shortcuts to the existing Reports, Settings, About and update dialogs. Native labels SHALL use supported ASCII punctuation. An uncommitted close chooser SHALL leave tray actions available; selecting Show, timer, preferences, navigation or background SHALL cancel the chooser without saving its proposed remembered choice. Explicit tray Exit SHALL save and close directly; failed saving SHALL restore visible recovery. Pending completions SHALL offer Review instead of timer mutation; final saving, save errors and installation SHALL restrict conflicting actions with a clear reason and reachable window/recovery action. Secondary shortcuts SHALL restore the same window and yield to completion alerts. Updates SHALL preserve an existing check/download/result, and SHALL NOT download or install through a tray click.

#### Scenario: Tray remains usable during a close choice
- **WHEN** the close chooser is open, including with Remember my choice checked, and the user selects a tray timer, preference, navigation, Show or background action
- **THEN** that action cancels the uncommitted chooser without saving its proposed preference, executes through the normal controller, and preserves phase-bound command and completion priority

#### Scenario: Genuine shutdown restriction and save recovery
- **WHEN** a final save or installation is in progress, or saving has failed
- **THEN** the tray explains the state, prevents conflicting mutations, and retains access to the same window; a save error offers explicit Keep app open, retry or warned forced exit and normal availability returns after continuing

#### Scenario: Control a hidden timer
- **WHEN** the user starts, pauses or resumes a phase from the tray
- **THEN** the same controller changes that phase, preserves task ownership and saving, and the tray reflects the new state without requiring the window to open

#### Scenario: Stale or destructive tray action
- **WHEN** Reset/Skip is selected or a previously displayed timer command arrives at a phase deadline
- **THEN** Reset/Skip requires an in-app phase-bound confirmation, and an expired command cannot alter the successor or erase completion credit

#### Scenario: Toggle sound with a pending completion
- **WHEN** the user disables Alarm sound or Repeat completion reminders from the tray
- **THEN** the existing playback is invalidated through the normal settings path, the changed field is saved without overwriting other settings, and the pending visual completion remains available

#### Scenario: Restore a secondary dialog or ongoing update
- **WHEN** the user chooses Reports, Settings, About or the update shortcut while the window is hidden
- **THEN** the same window opens the requested dialog when permitted, or shows its higher-priority alert, and an ongoing update remains available without restarting or cancelling it

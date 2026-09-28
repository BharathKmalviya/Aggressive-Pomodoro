# Spec Delta

## Purpose

Defines local recovery and interruption behavior so desktop sleep, restart, invalid saved data, and clock changes do not silently corrupt a Pomodoro session.

## ADDED Requirements

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
The application SHALL explain that an active timer cannot alert after the application exits. Closing the window during a running phase SHALL require confirmation; minimizing SHALL keep the timer running.

#### Scenario: Close running app
- **WHEN** the user requests to close the window during a running focus or break phase
- **THEN** the application asks for confirmation before exiting

#### Scenario: Minimize running app
- **WHEN** the user minimizes the window during a running phase
- **THEN** the countdown and deadline alerts remain active

### Requirement: Single session owner
The application SHALL prevent two simultaneously running processes from independently advancing and alerting for the same saved session.

#### Scenario: Second launch
- **WHEN** the user opens a second instance while one is already running
- **THEN** the second instance does not start another timer and the user receives a clear indication that the app is already open

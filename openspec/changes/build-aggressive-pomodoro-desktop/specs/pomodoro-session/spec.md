# Spec Delta

## Purpose

Defines the observable focus and break timer lifecycle so sessions advance predictably and controls never produce misleading completion counts.

## ADDED Requirements

### Requirement: Optional state-driven motion
The app SHALL animate phase accents, progress, and status/directive changes using short bounded transitions while keeping countdown and control targets immediate. Phase changes SHALL reset progress without sweeping backward across phases. Pause and waiting SHALL show exact progress immediately. A saved reduce-motion preference SHALL bypass custom motion without changing timer behavior or alert visibility.

#### Scenario: Pause or reset during animation
- **WHEN** the timer pauses, waits, or resets during a progress transition
- **THEN** progress snaps to the current state's value and no animation can send timer commands

#### Scenario: Legacy or reduced-motion snapshot
- **WHEN** an older valid snapshot lacks reduce-motion settings or a reduced-motion snapshot is restored
- **THEN** old state recovers with default motion or the saved opt-out is respected, preserving timer/task/history data

### Requirement: Assertive and truthful timer feedback
The application SHALL show distinct focus and break identities, explicit paused and waiting instructions, and a final-minute urgency state. Destructive confirmations SHALL refer only to their original phase and SHALL also protect paused progress. Other dialogs SHALL yield to completion alerts.

#### Scenario: Phase ends during confirmation
- **WHEN** a phase completes with a reset or skip confirmation open
- **THEN** the stale confirmation closes and cannot reset or skip the newly started phase

### Requirement: Deadline-boundary commands
The application SHALL reconcile current time before user commands. A command aimed at an expired phase SHALL NOT erase completion credit or control the newly advanced phase.

#### Scenario: Pause at the deadline
- **WHEN** pause arrives at or beyond the phase deadline before the next scheduled check
- **THEN** that phase completes once and the next phase follows the configured transition mode

### Requirement: Default cycle
The application SHALL start in an idle focus phase with a 25-minute focus duration, a 5-minute short break, a 15-minute long break, and a long break after every fourth completed focus phase.

#### Scenario: First launch
- **WHEN** the application starts without saved settings or session state
- **THEN** it shows an idle 25-minute focus phase and zero completed focus phases

#### Scenario: Fourth completed focus phase
- **WHEN** the fourth focus phase in a cycle reaches its deadline
- **THEN** the next phase is a 15-minute long break

### Requirement: Accurate countdown
The application SHALL derive remaining time from elapsed time and the current phase deadline rather than subtracting one second per UI tick. It SHALL display zero at expiration and emit no more than one completion for a phase.

#### Scenario: Delayed UI update
- **WHEN** the application UI cannot update for several seconds during a running phase
- **THEN** the next displayed remaining time reflects the actual elapsed time and the phase completes once

#### Scenario: Pause and resume
- **WHEN** a running phase is paused and later resumed
- **THEN** its remaining duration is unchanged during the pause and continues from that value after resume

### Requirement: Deliberate session controls
The application SHALL expose start, pause, resume, reset-current-phase, and skip-current-phase actions with state-appropriate availability. Reset and skip SHALL require confirmation while a phase is running. A skipped focus phase SHALL NOT increment the completed focus count.

#### Scenario: Reset current phase
- **WHEN** the user confirms reset during a running phase
- **THEN** the current phase returns to its configured full duration in an idle state without changing the completed focus count

#### Scenario: Skip focus
- **WHEN** the user confirms skipping a focus phase
- **THEN** the app moves to a short break without counting that focus phase as completed

#### Scenario: Skip break
- **WHEN** the user confirms skipping a break
- **THEN** the app moves to an idle focus phase without changing the completed focus count

### Requirement: Configurable cycle lengths
The application SHALL allow users to set focus duration from 1 to 180 minutes, either break duration from 1 to 60 minutes, and the long-break interval from 2 to 12 completed focus phases. Changes SHALL take effect at the next phase, leaving the active phase's planned duration unchanged.

#### Scenario: Edit duration during focus
- **WHEN** the user changes the focus duration while a focus phase is running
- **THEN** the current countdown keeps its original duration and the next focus phase uses the new duration

#### Scenario: Invalid setting
- **WHEN** the user enters a value outside the supported range
- **THEN** the application rejects the value with a clear inline explanation and preserves the last valid setting

### Requirement: Responsive accessible controls
The application SHALL keep timer controls usable by keyboard and keep countdown updates from blocking interactions or freezing the window.

#### Scenario: Keyboard control
- **WHEN** a user navigates the timer screen with the keyboard
- **THEN** start, pause, resume, reset, skip, settings, and dialog actions can be focused and activated with visible focus indication

#### Scenario: Storage is slow
- **WHEN** saving state is delayed by the filesystem
- **THEN** the countdown and controls remain responsive while the save completes

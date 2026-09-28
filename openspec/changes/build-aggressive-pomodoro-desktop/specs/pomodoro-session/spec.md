# Spec Delta

## Purpose

Defines the observable focus and break timer lifecycle so sessions advance predictably and controls never produce misleading completion counts.

## ADDED Requirements

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

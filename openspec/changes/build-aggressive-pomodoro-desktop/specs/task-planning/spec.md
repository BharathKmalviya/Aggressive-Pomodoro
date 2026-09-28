# Spec Delta

## Purpose

Lets a user name the work behind a focus session and track progress against a small local estimate without requiring an account or network service.

## ADDED Requirements

### Requirement: Stable active-task ownership
The application SHALL distinguish the current captured task from the next selected task. A focus block begun without a task SHALL remain unassigned across restart. Removing the captured task SHALL clear its reference without invalidating the saved product; completing a surviving task manually SHALL not discard credit for its already running block.

#### Scenario: Remove an active task
- **WHEN** the user removes the captured task, saves, and restarts
- **THEN** the timer, remaining tasks, settings, and history restore without a dangling task reference

#### Scenario: Select after an unassigned start
- **WHEN** focus starts without a task, a task is selected later, and the app restarts
- **THEN** that running block remains unassigned and selection applies to the next focus block

### Requirement: Local task list
The application SHALL let the user add, select, complete, and remove local tasks. A task SHALL have a nonempty title and an estimate from 1 to 20 focus blocks.

#### Scenario: Add and select a task
- **WHEN** the user adds a valid task and selects it
- **THEN** the task appears in the list and is identified as the selected task

#### Scenario: Invalid task
- **WHEN** a title is blank or an estimate is outside 1 to 20
- **THEN** the application rejects the task and explains the valid input

### Requirement: Focus credit belongs to the started task
The application SHALL credit one block to the task captured when a focus phase starts, only if that phase completes. Skipping or resetting SHALL not add credit. Changing the selected task during a running block SHALL affect the next focus block, not the running block's credit.

#### Scenario: Selection changes mid-focus
- **WHEN** focus starts with task A selected, the user selects task B, and focus completes
- **THEN** task A gains one completed block and task B does not

#### Scenario: Skipped focus
- **WHEN** the user skips a running focus phase
- **THEN** no task gains a completed block

### Requirement: Task persistence
The application SHALL restore tasks, estimates, progress, and selection from the same local snapshot as the timer.

#### Scenario: Relaunch with tasks
- **WHEN** the user adds a task and restarts the application
- **THEN** the task and its progress are restored

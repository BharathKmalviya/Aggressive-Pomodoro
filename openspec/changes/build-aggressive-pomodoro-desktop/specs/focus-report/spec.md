# Spec Delta

## Purpose

Shows a local record of completed focus work so users can see daily effort without telemetry, accounts, or cloud synchronization.

## ADDED Requirements

### Requirement: Daily completed-focus totals
The application SHALL record the planned duration and one completed block on the local calendar date when a focus phase completes. Reset, skip, and repeated expiration checks SHALL not add a daily record.

#### Scenario: Completed focus
- **WHEN** a 25-minute focus phase completes on a local calendar date
- **THEN** that date gains one block and 25 minutes of focused time

#### Scenario: Repeated check
- **WHEN** the completed phase is checked again after its transition
- **THEN** the daily total stays unchanged

### Requirement: Recent report
The application SHALL display the current day's total and a report for the seven local calendar days ending today, including days with zero completed focus blocks. Totals SHALL remain stored locally across restarts.

#### Scenario: Open report
- **WHEN** a user opens Reports after completing focus blocks on recorded days
- **THEN** the report shows today's date and the previous six dates with each day's completed blocks and focused minutes, including zero totals on days without completed focus

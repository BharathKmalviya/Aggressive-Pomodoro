# Proposal

## Why

The repository began as a Compose desktop starter. A first Windows release needs clear timer behavior, visible and audible transitions, recovery from interruption, local task focus, and a repeatable download process before it can serve as a useful open-source application.

## What Changes

- Replace the starter screen with a responsive focus and break timer, including start, pause, resume, reset, and deliberate skip controls.
- Add short and long breaks, a focus cycle count, and configurable durations. Default to 25-minute focus, 5-minute short break, and a 15-minute long break after four completed focus sessions.
- Show a phase-completion dialog and issue an audible cue and desktop attention request. Start the next break automatically by default; offer a setting that waits for confirmation instead. Apply the same transition preference when a break ends.
- Persist settings and active-session state locally so relaunch and suspend/resume have defined behavior. Avoid duplicate transitions and silently lost alerts.
- Add a local task list with estimates and selection, credit the task captured at focus start, and show daily focus totals in a seven-day report.
- Use a responsive timer-and-tasks desktop layout with optional short button-click feedback separate from completion sound.
- Make the aggressive identity functional: default-on ten-second completion reminders until acknowledgement, a testable alarm, forceful phase-specific prompts, final-minute urgency, and unmistakable focus/break styling while retaining mute and reminder controls.
- Harden deadline-boundary commands, stale confirmations, active-task deletion and restart credit, snapshot validation, and desktop alert failures with deterministic regression coverage.
- Add licensed offline button feedback, distinct focus/break completion sounds and previews, bounded timer animations with a saved reduce-motion option, and cancellation/cleanup under rapid interaction.
- Prepare a Windows-first downloadable release, with a self-contained installer, integrity checksum, versioned release notes, and CI validation.
- Add a user-triggered About update flow: check this repository's latest stable release, download the Windows installer with progress/cancel/retry, verify its checksum, save and back up local state, and open the installer only after explicit confirmation. Link the public GitHub repository from About.
- Show the latest stable release notes inside the update dialog, including for current versions, with bounded selectable text and no browser navigation.
- Establish open-source project documentation: setup, architecture, manual verification, release process, contribution guidance, code of conduct, security reporting, and the MIT License.

## Capabilities

### New Capabilities

- `pomodoro-session`: Phase lifecycle, cycle counting, controls, and timer accuracy.
- `phase-alerts`: Visible and audible phase transitions and configurable automatic or confirmed start.
- `session-recovery`: Local settings and session persistence, suspend/resume, restart, and clock-change behavior.
- `task-planning`: Local tasks, estimates, selection, and focus credit.
- `focus-report`: Local daily totals and recent focus report.
- `windows-distribution`: Windows download artifact, release metadata, and verification.

### Modified Capabilities

None; the repository has no existing OpenSpec capability specs.

## Impact

- `shared` will own timer state, transition rules, settings, and Compose UI; `desktopApp` will provide Windows desktop integration and the application entry point.
- The starter greeting and tests will be replaced with product behavior and focused unit/integration coverage.
- Packaging will build on the existing Kotlin Toolchain project; native Windows packaging needs an explicitly verified pipeline because the current `jvm/app` package command produces an executable JAR.
- Release automation and open-source project documentation are included. A push to `main` publishes only when `version.properties` names an unpublished version and Windows validation succeeds. GitHub About metadata points visitors to the Windows releases. The first supported downloadable platform is Windows; macOS and Linux remain future work.

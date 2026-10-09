# Proposal

## Why

The repository began as a Compose desktop starter. A first Windows release needs clear timer behavior, visible and audible transitions, recovery from interruption, local task focus, and a repeatable download process before it can serve as a useful open-source application.

## What Changes

- Replace the starter screen with a responsive focus and break timer, including start, pause, resume, reset, and deliberate skip controls.
- Add short and long breaks, a focus cycle count, and configurable durations. Default to 25-minute focus, 5-minute short break, and a 15-minute long break after four completed focus sessions.
- Show a phase-completion dialog and issue an audible cue and desktop attention request. Start the next break automatically by default; offer a setting that waits for confirmation instead. Apply the same transition preference when a break ends.
- Persist settings and active-session state locally so relaunch and suspend/resume have defined behavior. Avoid duplicate transitions and silently lost alerts.
- Apply saved durations to unstarted blocks and explicit resets, repair stale unstarted durations on recovery, and preserve running/paused progress and earned credit when rules change.
- Add a local task list with estimates and selection, credit the task captured at focus start, and show daily focus totals in a seven-day report.
- Use a responsive timer-and-tasks desktop layout with optional short button-click feedback separate from completion sound.
- Make the aggressive identity functional: default-on ten-second completion reminders until acknowledgement, a testable alarm, forceful phase-specific prompts, final-minute urgency, and unmistakable focus/break styling while retaining mute and reminder controls.
- Harden deadline-boundary commands, stale confirmations, active-task deletion and restart credit, snapshot validation, and desktop alert failures with deterministic regression coverage.
- Add licensed offline button feedback, distinct focus/break completion sounds and previews, bounded timer animations with a saved reduce-motion option, and cancellation/cleanup under rapid interaction.
- Prepare a Windows-first downloadable release, with a self-contained installer, integrity checksum, versioned release notes, and CI validation.
- Add a user-triggered About update flow: check this repository's latest stable release, download the Windows installer with progress/cancel/retry, verify its checksum, save and back up local state, and open the installer only after explicit confirmation. Link the public GitHub repository from About.
- Show the latest stable release notes inside the update dialog, including for current versions, with bounded selectable text and no browser navigation.
- Default window-close requests to a Background/Exit/Cancel choice, with optional remembered Background or Exit and an Ask every time setting. Keep the timer and persistence alive in the notification area, provide Show/Exit tray actions, and minimize to the taskbar when the tray is unavailable. Tray Exit always restores its explicit choice.
- Expand the tray with live phase/countdown and daily totals, phase-bound Start/Pause/Resume, confirmed Reset/Skip, alarm/reminder toggles, and shortcuts to Reports, Settings, About and the existing manual update flow. Use native-font-safe labels and retain completion/close-dialog priority.
- Correct the reported disabled-looking tray: make summaries actionable, group timer/alert options, permit actions to cancel an uncommitted close choice, provide direct background operation, and explain genuine save/installation restrictions with a reachable recovery path.
- Make a second launcher/shortcut launch request activation of the existing window instead of showing an Already running modal. Retain one session owner and an explicit failure fallback; handle requests arriving during startup, background operation, close choices and alert/save-error states.
- Correct the reproduced updater connection failure: try alternate system-resolved server addresses, retain HTTPS/redirect/checksum protections, cancel blocked connections, show checksum/connection/transfer/verification stages and specific failures, and release the correction for a one-time manual upgrade from affected versions.
- Bundle the nine owner-supplied sounds as bounded offline alarm cues, with separate saved focus/break selections and previews of unsaved choices in Settings. Retain original phase alarms as defaults and recover from missing or unknown selections without losing user data.
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
- Owner-requested v0.3.2 publication includes close/background behavior and selectable supplied alarms through existing automated release gates, with native manual acceptance still pending and the change kept active.
- Owner-requested v0.3.3 publication includes the expanded tray menu and saved window-close choices after implementation, through the same automated Windows gates. Keep native manual acceptance recorded as pending and the change active.
- The v0.3.4 correction continues that improvement/release scope after the owner reported disabled-looking tray actions; retain all automated release gates and separate native acceptance evidence.

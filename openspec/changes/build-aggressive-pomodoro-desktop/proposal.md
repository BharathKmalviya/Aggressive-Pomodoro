# Proposal

## Why

The repository is a Compose desktop starter, so it cannot yet run a trustworthy Pomodoro session or alert someone when a phase ends. A first Windows release needs clear timer behavior, visible and audible transitions, recovery from interruption, and a repeatable download process before it can serve as a useful open source application.

## What Changes

- Replace the starter screen with a responsive focus and break timer, including start, pause, resume, reset, and deliberate skip controls.
- Add short and long breaks, a focus cycle count, and configurable durations. Default to 25-minute focus, 5-minute short break, and a 15-minute long break after four completed focus sessions.
- Show a phase-completion dialog and issue an audible cue and desktop attention request. Start the next break automatically by default; offer a setting that waits for confirmation instead. Apply the same transition preference when a break ends.
- Persist settings and active-session state locally so relaunch and suspend/resume have defined behavior. Avoid duplicate transitions and silently lost alerts.
- Prepare a Windows-first downloadable release, with a self-contained installer, integrity checksum, versioned release notes, and CI validation.
- Establish source-available project documentation: setup, architecture, manual verification, release process, contribution guidance, code of conduct, security reporting, and a noncommercial license with a separate route to request commercial permission.

## Capabilities

### New Capabilities

- `pomodoro-session`: Phase lifecycle, cycle counting, controls, and timer accuracy.
- `phase-alerts`: Visible and audible phase transitions and configurable automatic or confirmed start.
- `session-recovery`: Local settings and session persistence, suspend/resume, restart, and clock-change behavior.
- `windows-distribution`: Windows download artifact, release metadata, and verification.

### Modified Capabilities

None; the repository has no existing OpenSpec capability specs.

## Impact

- `shared` will own timer state, transition rules, settings, and Compose UI; `desktopApp` will provide Windows desktop integration and the application entry point.
- The starter greeting and tests will be replaced with product behavior and focused unit/integration coverage.
- Packaging will build on the existing Kotlin Toolchain project; native Windows packaging needs an explicitly verified pipeline because the current `jvm/app` package command produces an executable JAR.
- Release automation and source-available Markdown files will be added. This workspace currently has no Git repository or remote, so version tags and a public release destination must be established before publication. The first supported downloadable platform is Windows; macOS and Linux remain future work. The project's license and public permission contact must be finalized before publication.

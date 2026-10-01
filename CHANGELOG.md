# Changelog

## Unreleased

- **View release notes** now expands selectable, scrollable notes inside the update dialog without opening GitHub, including when no newer version is available.
- Read notes from the existing manual check, handle missing or malformed bodies without blocking updates, bound long text with a visible notice, and retain notes through download/cancel/retry failures. Fresh checks clear stale metadata.
- Added deterministic release-note parsing and update-state regression coverage. Native Windows reading, keyboard/scrolling, and completion-alert checks remain manual; v0.3.0 installers are unchanged.

## 0.3.0

- Added offline Kenney CC0 click feedback and distinct focus-complete/break-complete chord alarms, with separate previews that stop on Settings dismissal or completion preemption.
- Added short phase-accent and status/instruction transitions, smooth running progress, and one-shot final-minute emphasis. Saved **Reduce motion** disables custom animation; pause/wait/reset remain immediate and truthful.
- Hardened audio replacement, mute/acknowledgement/shutdown and click-disable cancellation, stale callbacks, and callback-thread cleanup. Existing format-3 snapshots remain compatible; absent motion preferences use defaults.
- Added deterministic audio and preference compatibility regressions and exact manual Windows sound/motion scenarios. Audible quality and native UI acceptance still require manual verification.
- Snapshot format remains 3, preserving v0.2.0 compatibility. Close the app and back up `%APPDATA%\AggressivePomodoro\session.properties` before upgrading or rolling back; v0.1.0 still needs a pre-format-3 backup.
- Windows audio, keyboard, minimize/sleep, and interactive updater/UAC checks remain pending. Publication is requested with those gaps disclosed and remains gated on automated Windows build, tests, packaging, installation/launch, upgrade/data preservation, checksum, and license validation. The installer is unsigned.

## 0.2.0

- Added **About → Check for updates**, download progress and cancellation, checksum verification, and confirmed **Install & Exit** with a local session backup. Updates use the latest stable GitHub release and never install automatically.
- Added a clickable GitHub repository link to About and preserved the Windows installer's upgrade identity across versions.
- Reworked the desktop interface around assertive focus/break prompts, high-contrast styling, final-minute urgency, and clearer current/next task ownership.
- Added default-on completion reminders every ten seconds, a multi-pulse alarm, Settings preview, in-alert mute, and visible audio failure feedback.
- Protected paused progress and cancelled stale reset/skip confirmations when a phase changes; completion alerts take priority over other dialogs.
- Fixed commands arriving at a deadline losing completion credit, task deletion invalidating saved work, completed tasks losing earned credit, and unassigned sessions being reassigned after restart.
- Hardened backward-clock recovery, snapshot validation, alert callback failures, fresh command timestamps, and persistence retry after a failed close.
- Added deterministic regression tests for timer, storage, audio, and update flows, plus expanded Windows manual acceptance scenarios. Hands-on Windows audio, keyboard, minimize, sleep, and interactive updater checks remain unverified for this release.
- Saved data migrates to snapshot version 3 while preserving existing tasks, progress, settings, and timer state. Before upgrading, close the app and back up `%APPDATA%\AggressivePomodoro\session.properties` if you may return to v0.1.0; that older app cannot read version 3 snapshots. Restore the pre-upgrade backup before launching the old version.

## 0.1.0

- Windows desktop timer with focus, short break, and long break cycle.
- Local tasks, selected-task focus credit, and seven-day daily totals.
- Separate click and completion sounds, visual completion queue, local recovery, and single-instance behavior.
- Automated reducer and storage tests, runtime-bundled package script, and Windows CI gates.
- Version-driven Windows release publication after MSI validation.
- MIT License and contribution/security policies.

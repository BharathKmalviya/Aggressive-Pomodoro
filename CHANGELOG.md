# Changelog

## Unreleased

## 0.3.6

- Find updates automatically at startup and every six hours while the app runs, with a quiet 15-minute retry after discovery fails. The main window shows **Update available → View Update**, without visiting Settings. Downloads and **Install & Exit** remain under your control.
- Tray **Exit** now saves and closes directly without another Background/Exit/Cancel dialog. X/Alt+F4 still follows your saved close choice; tray Exit never saves an uncommitted Remember selection. Failed saves retain visible retry/recovery.
- Retry temporary launcher mailbox write failures within the existing bounded activation deadline, retaining one profile owner and the same activation token.
- Strengthened updater regression protection: a stalled first server connection must allow a reachable alternate address to complete the request without waiting for that first attempt. Retains v0.3.5's alternate-address recovery, staged feedback, blocked-request cancellation and **Download Again** recovery.
- All 144 automated tests, Windows runtime packaging, clean installer launch, second-launch acknowledgement, upgrade/data preservation and complete packaged-updater download/checksum verification must pass before publication.
- Timer, tasks, saved preferences, snapshot format 3 and installer upgrade identity are preserved. Native update-notice/tray/keyboard/scaling, audio, sleep and interactive updater/UAC acceptance stays pending; the installer remains unsigned.

## 0.3.5

- Fixed update downloads that stalled at 0% when GitHub's first download-server address was unreachable. The updater now tries alternate addresses with normal HTTPS verification.
- Show checksum fetching, server connection, installer download and verification separately. Cancel interrupts blocked network requests; failed downloads offer **Download Again** as the primary action.
- Keep exact installer size/checksum checks, trusted redirects, partial-file cleanup, release notes and explicit **Install & Exit**. Timer, tasks, settings and snapshot format 3 are preserved.
- Added real socket fallback/cancellation regressions and a release gate that downloads and reverifies the published installer through the packaged updater code without opening the UI.
- If v0.3.3 or v0.3.4 cannot download this fix, download the v0.3.5 MSI from this release once, explicitly Exit the app, and install it. Native updater/keyboard/UAC acceptance remains manual; the installer remains unsigned.

## 0.3.4

- Opening the launcher or desktop/Start-menu shortcut again now brings the existing app forward instead of showing Already running. Hidden/minimized windows restore, startup requests are retained, and one timer owns the profile. Unresponsive or older running versions retain a bounded recovery fallback.
- Fixed the tray appearing disabled while the close-choice dialog was open. Tray actions now cancel that uncommitted choice and continue; they never save a proposed Remember my choice setting.
- Made live timer status open the app and today's totals open Reports. Grouped Reset/Skip under Timer options and sound/reminder checkboxes under Alerts, and added direct Run in background.
- Added clear saving/update/save-error labels and an explicit Keep app open button after a failed exit save. Actual shutdown and pending-alert protections still apply; tray Exit always asks.
- Preserved timer/task/history data, close preferences, stale-command protection and existing update progress. Snapshot format and installer identity are unchanged.
- This correction follows the requested tray improvements and release. Automated Windows build/test/package/install/upgrade/checksum/license gates remain required; native menu, keyboard/scaling and recovery acceptance remains pending.

## 0.3.3

- Expanded the tray with live phase/countdown/status, today's blocks/minutes, and a timer tooltip. Start, pause or resume without reopening the window.
- Added confirmed Reset/Skip and shortcuts to Reports, Settings, About and manual updates; existing update progress/results remain available through View update.
- Added saved Alarm sound and Repeat completion reminders checkboxes through the existing audio/persistence path. Other unsaved Settings edits survive these toggles.
- Preserved completion/close priority, phase-bound stale-command protection and deliberate exit. Native menu labels use ASCII dots so Exit no longer depends on an unsupported ellipsis glyph.
- Added **Remember my choice** to the close dialog. Save Background/Minimize or Exit for later X/Alt+F4 requests; Cancel never saves a choice.
- Added **Settings → When closing the window** with Ask every time, Run in background and Exit the app. Existing profiles keep asking; unavailable trays minimize, and tray Exit always asks.
- Kept snapshot format 3 with a tolerant optional close preference. Older builds ignore and may drop this preference when saving; back up your profile before rollback. Exit still waits for saving and recovers visibly on failure.
- Publication is owner-requested through automated Windows build/test/runtime-package/clean-install/upgrade/checksum/license gates. Native audio, tray/notification, close-choice keyboard/scaling, sleep and updater/UAC acceptance remains pending. The installer remains unsigned.

## 0.3.2

- Added all nine supplied alarm sounds to Settings, with separate focus/break choices and previews. Pick your preferred wake-up call; Save Rules remembers it.
- Kept alarms offline and bounded to eight seconds, with safe playback cancellation, original-sound fallback, and legacy-setting recovery. The timer still refuses to double-book the speaker.
- Close now asks whether to keep your timer working in the background, exit, or cancel. Your timer refuses to clock out without asking.
- Reopen from the tray icon; background alarms and saving continue. No tray available? Minimize keeps the timer on the taskbar.
- Snapshot format stays at 3. Back up your profile before upgrading or rolling back; older versions can drop the new sound choices when saving. Supplied recordings retain their Pixabay/Mixkit licenses and bundled credits.
- Publication is owner-requested through automated Windows build, test, packaging, clean-install, upgrade/data-preservation, checksum, and license gates. Manual audio, tray/notification, keyboard, sleep, and updater/UAC acceptance remain pending. The installer remains unsigned.

## 0.3.1

- Fixed **Reset Block** retaining an old duration after **Save rules**. Idle/waiting blocks now refresh on save, and older unstarted snapshots refresh on recovery. Running/paused progress and completed-block credit stay intact; other saved preferences apply immediately.
- Added regressions for focus/both breaks, shorter/longer reset durations, paused resume, pending acknowledgements, deadline credit, and settings/reset persistence through real storage and relaunch.
- **View release notes** now expands selectable, scrollable notes inside the update dialog without opening GitHub, including when no newer version is available.
- Read notes from the existing manual check, handle missing or malformed bodies without blocking updates, bound long text with a visible notice, and retain notes through download/cancel/retry failures. Fresh checks clear stale metadata.
- Added deterministic release-note parsing, update-state, saved-rules, and persistence regression coverage. All 106 automated tests pass; native Windows reading, keyboard/scrolling, sound, sleep, completion-alert, and interactive updater/UAC checks remain manual.
- Snapshot format remains 3, preserving v0.2.0/v0.3.0 compatibility. Close the app and back up `%APPDATA%\AggressivePomodoro\session.properties` before upgrading or rolling back. The installer remains unsigned. Publication is owner-authorized with pending manual acceptance disclosed and remains gated on automated Windows build, tests, packaging, clean install/launch, baseline upgrade/data preservation, checksum, and MIT License validation.

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

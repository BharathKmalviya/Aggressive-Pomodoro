# Tasks

## 1. Project policy and baseline

- [x] 1.1 Record the selected MIT License and copyright holder in `LICENSE` and `README.md`; verify the text against the standard SPDX template before any release.
- [x] 1.2 Keep assistant-specific `AGENTS.md` local and ignored, with durable architecture, quality, and contribution rules in the public project docs; verify mobile test instructions are not claimed for this desktop app.
- [x] 1.3 Replace the starter README with product scope, Windows support status, source-build setup using the pinned wrappers, license summary, and links to project docs; verify every documented command and relative link.
- [x] 1.4 Add `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and `SECURITY.md` with issue/PR guidance, conduct reporting, private vulnerability reporting, and contributor copyright expectations; verify the contact routes are usable and terms match `LICENSE`.

## 2. Timer model and settings

- [x] 2.1 Implement a pure session state machine in `shared` for defaults, focus/short/long cycle counting, start/pause/resume/reset/skip, and unique phase IDs; verify deterministic tests cover fourth-focus long break, skipped focus, reset, repeated completion, and invalid commands.
- [x] 2.2 Implement deadline-derived countdown and injectable monotonic/wall clocks; verify tests with delayed ticks, pause/resume, zero boundary, and no duplicate phase completion.
- [x] 2.3 Implement typed settings with 1–180 minute focus, 1–60 minute breaks, 2–12 focus long-break interval, auto transition default, and sound toggle; verify validation and preservation of running/paused duration with tests. Save/reset/recovery refresh behavior is tracked in section 14.
- [x] 2.4 Add `docs/ARCHITECTURE.md` for state transitions, clock rules, settings ownership, and module boundaries; verify it reflects the implemented model and links from README.

## 3. Alerts and desktop UI

- [ ] 3.1 Replace the starter Compose screen with focus/break identity, countdown, progress, cycle count, state-appropriate controls, and settings; verify keyboard navigation, focus visibility, resize behavior, and a responsive UI during timer updates on Windows.
- [x] 3.2 Implement persistent completion dialog state and bounded pending-event queue for automatic and confirmation modes; verify controller tests cover both transition directions, acknowledgement, unattended second completion, and recomposition-safe deduplication.
- [ ] 3.3 Add Windows desktop attention and bundled audio adapters with failure-safe visual dialog fallback; verify minimized/unfocused, muted, and unavailable-audio cases manually on Windows and verify alert failures do not stop the timer.
- [x] 3.4 Add `docs/TESTING.md` with exact desktop manual scenarios for dialogs, sound, minimize, keyboard, and transition modes; verify each scenario is runnable from the documented setup.

## 4. Recovery and process lifecycle

- [x] 4.1 Implement versioned local snapshot storage with validation, atomic replacement, asynchronous writes, and a visible recovery message; verify tests cover first run, paused/running restart, corrupt/unsupported data, interrupted write, and slow storage without UI blocking.
- [x] 4.2 Reconcile active phase on wake/relaunch without backfilling multiple cycles and detect material clock disagreements; verify deterministic clock tests cover expired focus/break, one-alert-only recovery, sleep, and clock jump resolution.
- [ ] 4.3 Add single-instance ownership, minimize-stays-running behavior, and active-timer close confirmation; verify two-instance launch, minimize, cancel-close, and confirmed exit on Windows.
- [ ] 4.4 Document saved-state location, recovery behavior, and exit limitations in README and `docs/ARCHITECTURE.md`; verify those descriptions against manual Windows checks.

## 5. Windows packaging and release documentation

- [x] 5.1 Establish Git history and the owner-selected public release destination for this workspace; verify the intended remote, version tag policy, and release permissions before configuring publication.
- [x] 5.2 Add app icon, application/version metadata, and About display; verify the same version appears in the running app and package metadata.
- [x] 5.3 Build the executable JAR with the pinned Kotlin Toolchain and package it into a runtime-bundled Windows app image and MSI using pinned JDK/jpackage and WiX tooling; verify the app image launches without a separate Java installation and the MSI installs and launches on a clean Windows environment.
- [x] 5.4 Add Windows CI for build, tests, packaging smoke checks, checksum generation, and tag-bound release assets, with publication gated on successful validation; verify a failed packaging job cannot publish a release and the checksum matches the exact MSI.
- [x] 5.5 Add `docs/RELEASING.md`, `CHANGELOG.md`, and README download/install guidance covering version/tag rules, checksums, unsigned installer warning, rollback, source-build support, and Windows-only package support; verify commands, links, and filenames against produced artifacts.

## 6. Cross-feature acceptance

- [ ] 6.1 Run the complete build/test command and follow `docs/TESTING.md` on Windows for a full four-focus cycle, both transition modes, minimize/restore, sleep/restart, clock change, and second instance; record results and resolve failures before tagging.
- [ ] 6.2 Validate this OpenSpec change strictly, review implementation against all six delta specs, and reconcile documentation with actual behavior; verify `openspec validate build-aggressive-pomodoro-desktop --strict` succeeds and no unchecked spec scenario lacks evidence.
- [x] 6.3 Before publishing the first download, confirm the MIT License and copyright holder, clean installer launch, checksum, release notes, and versioned tag/assets; verify the published release page and download links from a fresh readback.

## 7. Production task flow and feedback

- [x] 7.1 Add local tasks with validated titles and estimates, selection, completion, deletion, and focus credit captured at phase start; verify reducer and snapshot round-trip tests.
- [ ] 7.2 Show today's completed focus total and a seven-day report in the responsive desktop UI; verify local-date rollover, persistence, narrow layout, and keyboard use on Windows.
- [ ] 7.3 Add independent button-click sound preference and bundled cue; verify enabled, disabled, rapid-click, and unavailable-audio behavior on Windows.
- [x] 7.4 Verify the production Windows CI and version-driven release workflow against a clean MSI installation, checksum, MIT license gate, duplicate-version skip, and failed-build publication gate.
- [x] 7.5 Set and read back the GitHub About description, release link, and product/technology topics; verify the first published release appears from the repository page.

## 8. Aggressive identity and edge cases

- [x] 8.1 Add default-on persistent ten-second reminders with independent preference, restored-alert delivery, bounded delayed-check behavior, exception-safe effects, and injected-clock tests.
- [x] 8.2 Strengthen focus/break visuals, phase-specific prompts, final-minute urgency, truthful active/next task labels, responsive controls, and stale/paused confirmation handling.
- [x] 8.3 Add a distinct completion alarm, non-overlapping/throttled audio, preview, in-alert mute, and visible audio-failure feedback with deterministic adapter checks where possible.
- [x] 8.4 Fix deadline-boundary commands and active-task lifecycle/credit issues; add deterministic regression tests.
- [x] 8.5 Migrate reminder settings while preserving legacy state, unassigned task ownership, and valid snapshots; test round trips and malformed snapshot recovery.
- [x] 8.6 Update README, architecture, changelog, and exact Windows acceptance scenarios; run complete build/tests and strict OpenSpec validation and record remaining manual evidence.

Section 8 verification (2026-09-28): complete Kotlin build and all 62 tests passed (32 shared, 30 desktop); strict OpenSpec validation passed. Native Windows visual, keyboard, audio, and minimize/sleep acceptance remain open under the earlier unchecked tasks and `docs/TESTING.md`. The owner subsequently requested v0.2.0 publication with those manual gaps disclosed. Automated release gates remain mandatory; this change stays active and is not ready for archival.

## 9. Version 0.2.0 publication

- [x] 9.1 Prepare versioned release notes and snapshot rollback guidance, incorporate remote changes, and validate release metadata and OpenSpec.
- [x] 9.2 Commit and push the release update to main; verify Windows CI and the release workflow build/test/package/install gates succeed for that exact commit.
- [x] 9.3 Read back the published v0.2.0 tag and assets, independently download and verify MSI checksum and license, and record release evidence with outstanding manual checks.

Section 9 verification: immutable v0.2.0 was published from `e93020082591517574ac35efbd1f48b9f2c00405` after Windows CI run `36462170882` and release run `36462170953` passed, including the v0.1.0 upgrade and data-preservation gate. All three public assets were independently downloaded and verified. The MSI SHA-256 is `5f26b6ad03a8141d0fe80fc04ed1db564737eaa6168fd972e757b0a356dabfa7`. Full evidence and remaining manual gaps are recorded in `docs/RELEASING.md` and `docs/TESTING.md`.

## 10. In-app updates and About repository

- [x] 10.1 Implement manual latest-stable release checks, numeric version comparison, constrained asset downloads, progress/cancel/retry, bounded network handling, and checksum verification with deterministic tests.
- [x] 10.2 Add About repository access and an update dialog covering current/available/downloading/ready/error states, explicit install confirmation, and completion-alert priority.
- [x] 10.3 Integrate verified interactive installation with save/backup/exit and recoverable failures; preserve the first released MSI UpgradeCode and cover controller failure paths with tests.
- [x] 10.4 Update privacy, architecture, release notes, and manual Windows upgrade checks; run full build/tests and strict OpenSpec validation before publication.
- [x] 10.5 Refresh README screenshots using the real v0.2.0 interface and an isolated sample profile, including About's repository link and the manual update screen; inspect framing and readability before pushing.

Section 10 local verification: build and executable JAR packaging passed; all 83 tests passed (32 shared, 51 desktop) with no test discovery omissions. Strict OpenSpec validation and workflow actionlint passed. The original released MSI's hash and UpgradeCode were independently verified. Interactive updater/UAC and the manual audio/UI checks remain pending.

The packaged updater also passed a live latest-release check, full 92,951,448-byte v0.1.0 download, and checksum re-verification without launching an installer.

Post-publication screenshot smoke confirmed the real UI can start a demo focus block, open Settings and About, show version 0.2.0 and the repository link, and complete a manual check with the current-version result. Four refreshed README images document these screens. Together with the release MSI metadata checks, the observed About version completes task 5.2. Remaining native acceptance scenarios stay open.

## 11. Sound, motion, and playback edge cases

- [x] 11.1 Bundle licensed offline click feedback and tune distinct focus/break completion cues; add both previews and route completed-event phase through immediate, restored, and reminder playback.
- [x] 11.2 Harden audio ownership, asynchronous/idempotent cleanup, stale callback/open cancellation, click mute, and preview dismissal/preemption; verify deterministic regression checks.
- [x] 11.3 Add bounded timer accent/status/directive/progress motion, stable controls/countdown, phase-reset and paused/waiting progress handling, and persisted reduced motion with legacy/invalid snapshot regression coverage.
- [x] 11.4 Update sound attribution, README, architecture, changelog, and exact Windows scenarios; run full build/tests and strict OpenSpec/diff checks. Record manual Windows evidence separately without closing prior acceptance gates.

Section 11 local verification (2026-10-01): build, all 93 tests (32 shared, 61 desktop), executable JAR packaging, strict OpenSpec validation, and diff checks passed. The packaged WAV/license/credit files match source. Original manual Windows gates and new sound/motion acceptance remain open in `docs/TESTING.md`; the change stays active. No version bump or new public installer release is included.

## 12. Version 0.3.0 publication

- [x] 12.1 Prepare v0.3.0 metadata, release notes, format-3 compatibility/rollback guidance, and documented owner-authorized publication with pending manual acceptance; run build/tests and strict OpenSpec/diff checks.
- [x] 12.2 Commit and push the release update to main; require the exact commit's Windows CI and release packaging, clean installation/launch, baseline upgrade/data-preservation, checksum, and license gates to pass.
- [x] 12.3 Read back the immutable v0.3.0 tag and assets, independently download and verify their hashes and MIT License, and record release evidence without closing pending manual acceptance tasks.

Section 12 verification (2026-10-01): immutable v0.3.0 was published from `acd7b7b3c61d7f1293b2d4f6d5d0053232addc93` after Windows CI `36872068972` and release workflow `36872068933` passed all automated build/test/package/clean-install/upgrade gates. All three public assets were independently downloaded and hash-verified. MSI SHA-256: `61f64f9420fcfb856a2e9063484850a35c6403113254a4c4ef986b143519e50e`. Full evidence is in `docs/RELEASING.md`; pending manual Windows acceptance remains in `docs/TESTING.md`, and earlier unchecked tasks stay open.

## 13. Embedded release notes

- [x] 13.1 Read bounded optional release notes and latest version for newer/current/older releases; retain them through download/cancel/retry failures and cover malformed/missing/long notes with deterministic checks.
- [x] 13.2 Replace release browser navigation with an expandable, selectable, scrollable section inside the update dialog; preserve completion-alert priority and update relevant documentation/manual Windows scenarios.
- [x] 13.3 Run build, all tests, executable JAR packaging, strict OpenSpec and diff validation; record automated results separately from pending Windows acceptance.

Section 13 local verification (2026-10-01): build, all 97 tests (32 shared, 65 desktop) with no failures/skips, executable JAR packaging, strict OpenSpec validation, and diff checks passed. Tests cover current/older/newer notes with one request, absent/null/malformed/blank bodies, Unicode-safe truncation, and retention/clearing through cancellation/download/installation errors and rechecking. Native Windows reading/selection/scrolling/keyboard and completion priority remain pending in `docs/TESTING.md`. The published v0.3.0 installer is unchanged; the change stays active with prior manual gates open.

## 14. Saved rules and timer refresh

- [x] 14.1 Apply latest saved durations to idle/waiting phases, explicit resets, and stale unstarted snapshot recovery while preserving active progress, deadline reconciliation, completion queues, and task/history credit.
- [x] 14.2 Add deterministic regressions for all phase types, running/paused/reset/resume, waiting acknowledgement, invalid edits, live preference updates, and save/relaunch through real storage.
- [x] 14.3 Update Settings guidance, README, architecture, changelog, and exact Windows scenarios; run build/tests, strict OpenSpec validation, and diff checks and record pending manual acceptance separately.

Section 14 local verification (2026-10-01): five new regression tests failed against the original implementation, reproducing stale save/reset/waiting/recovery durations and incorrect post-reset report duration. After the fix, build, all 106 tests (39 shared, 67 desktop; no failures/errors/skips), executable JAR packaging, strict OpenSpec validation, and diff checks passed. Nine new tests cover all phase types, shorter/longer resets, paused progress/resume, invalid edits, waiting acknowledgement, preference acceptance, deadline credit, task recapture, and real-store save/relaunch. Reset confirmation now displays the saved target duration. Native Windows scenarios are documented and pending in `docs/TESTING.md`; no version bump or installer publication is included, and prior manual gates stay open.

## 15. Version 0.3.1 publication

- [x] 15.1 Prepare patch-version metadata and release notes for saved-rules fixes and embedded release notes; document owner-authorized publication with pending manual acceptance, and validate build/tests, packaging, OpenSpec, and diff checks.
- [x] 15.2 Commit and push the release update to main; require the exact commit's Windows CI and release packaging, clean installation/launch, baseline upgrade/data-preservation, checksum, and license gates to pass.
- [x] 15.3 Read back the immutable v0.3.1 tag and assets, independently download and verify their hashes and MIT License, and record release evidence without closing pending manual acceptance tasks.

Section 15 verification (2026-10-01): immutable v0.3.1 was published from `2a57bff99ac4c27ca1dfaf69646bf690247ea699` after Windows CI `36875954691` and release workflow `36875955080` passed all automated build/test/package/clean-install/upgrade gates, including 106 passing tests. Fresh API/tag readback and independent downloads verified all three public assets, the MSI checksum, and pinned MIT License. MSI SHA-256: `33eb9193df97752cdd00e3ea9449b5d66c26b94647f35135094ce586a0cbf160`. Evidence is in `docs/RELEASING.md` and `docs/TESTING.md`; saved-rules, embedded-note, and earlier manual acceptance remain pending, so the change stays active.

## 16. Close choice and background operation

- [x] 16.1 Add every-state close choice, retained-window background/minimize operation, tray Show/Exit, completion notification, tray-loss restoration and shutdown cleanup; verify Kotlin build and existing controller/alert regressions, and document ownership in architecture.
- [x] 16.2 Update the Windows upgrade harness to explicitly activate Exit on the new owned close dialog while preserving baseline behavior; verify PowerShell/YAML syntax and document the release gate.
- [x] 16.3 Update README/changelog and exact manual Windows close/background/tray/failure/keyboard scenarios; run full build/tests, executable JAR packaging, strict OpenSpec and diff checks, recording automated evidence separately from pending native acceptance.

Section 16 local verification (2026-10-09): final Kotlin build, all 106 existing automated regressions (39 shared, 67 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec, both workflow actionlint checks and all seven release PowerShell step syntax checks passed. The executable JAR contains the new close dialog, desktop tray adapter and app icon. Native tray/menu/keyboard/scaling/notification, save-failure UI, and upgrade interaction remain pending in `docs/TESTING.md`; the release harness was syntax-checked, not executed locally. No version bump or installer publication is included; earlier manual gates remain open and this change stays active.

## 17. Selectable supplied alarm sounds

- [x] 17.1 Prepare all nine bounded PCM cues from owner-supplied originals, with conversion script, manifest and bundled credits; verify decoded format, duration, hashes and packaged resources.
- [x] 17.2 Add stable focus/break selections and tolerant format-3 persistence; verify reducer acceptance and storage round-trip/legacy/unknown-ID preservation tests and update architecture.
- [x] 17.3 Add Settings selection/unsaved previews and saved-event routing, cancellation, IO cache and visible fallback; verify deterministic audio routing/fallback/ownership regressions and document exact manual sound/keyboard/background checks.
- [x] 17.4 Update README/changelog/release guidance, run full build/tests, executable JAR packaging, strict OpenSpec and diff checks; record automated evidence separately from pending native acceptance.

Section 17 local verification (2026-10-09): final Kotlin build, all 111 tests (40 shared, 71 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec and Git diff checks passed. Five new tests cover completed-phase setting resolution with preserved progress, all nine real resources, playback caching/fallback/recovery, mute during decoding, and round-trip/legacy/unknown-ID preservation. Resource checks caught and corrected an AudioInputStream frame-alignment stall. Independent validation checked all original/prepared hashes, PCM formats, eight-second limits, peak limits and endpoint fades, then matched all nine packaged WAVs and credits to source. Original inputs remain intact and ignored locally. Native audible quality, selector focus and actual background output remain pending in `docs/TESTING.md`; no version bump or installer release is included, and prior manual gates stay open.

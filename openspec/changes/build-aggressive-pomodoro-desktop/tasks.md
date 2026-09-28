# Tasks

## 1. Project policy and baseline

- [ ] 1.1 Record the selected noncommercial license, owner-supplied copyright holder, and commercial-permission contact in `LICENSE` and `README.md`; verify the exact standard license text and public contact are present before any release.
- [ ] 1.2 Add root `AGENTS.md` with the OpenSpec propose → apply → verify → archive workflow, KISS/DRY guidance, desktop scope, and doc synchronization rule; verify it matches the current OpenSpec config and does not claim mobile tests apply to desktop.
- [ ] 1.3 Replace the starter README with product scope, Windows support status, source-build setup using the pinned wrappers, license summary, and links to project docs; verify every documented command and relative link.
- [ ] 1.4 Add `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and `SECURITY.md` with issue/PR guidance, conduct reporting, private vulnerability reporting, and contributor copyright expectations; verify the contact routes are usable and terms match `LICENSE`.

## 2. Timer model and settings

- [ ] 2.1 Implement a pure session state machine in `shared` for defaults, focus/short/long cycle counting, start/pause/resume/reset/skip, and unique phase IDs; verify deterministic tests cover fourth-focus long break, skipped focus, reset, repeated completion, and invalid commands.
- [ ] 2.2 Implement deadline-derived countdown and injectable monotonic/wall clocks; verify tests with delayed ticks, pause/resume, zero boundary, and no duplicate phase completion.
- [ ] 2.3 Implement typed settings with 1–180 minute focus, 1–60 minute breaks, 2–12 focus long-break interval, auto transition default, and sound toggle; verify validation and next-phase-only application with tests.
- [ ] 2.4 Add `docs/ARCHITECTURE.md` for state transitions, clock rules, settings ownership, and module boundaries; verify it reflects the implemented model and links from README.

## 3. Alerts and desktop UI

- [ ] 3.1 Replace the starter Compose screen with focus/break identity, countdown, progress, cycle count, state-appropriate controls, and settings; verify keyboard navigation, focus visibility, resize behavior, and a responsive UI during timer updates on Windows.
- [ ] 3.2 Implement persistent completion dialog state and bounded pending-event queue for automatic and confirmation modes; verify controller tests cover both transition directions, acknowledgement, unattended second completion, and recomposition-safe deduplication.
- [ ] 3.3 Add Windows desktop attention and bundled audio adapters with failure-safe visual dialog fallback; verify minimized/unfocused, muted, and unavailable-audio cases manually on Windows and verify alert failures do not stop the timer.
- [ ] 3.4 Add `docs/TESTING.md` with exact desktop manual scenarios for dialogs, sound, minimize, keyboard, and transition modes; verify each scenario is runnable from the documented setup.

## 4. Recovery and process lifecycle

- [ ] 4.1 Implement versioned local snapshot storage with validation, atomic replacement, asynchronous writes, and a visible recovery message; verify tests cover first run, paused/running restart, corrupt/unsupported data, interrupted write, and slow storage without UI blocking.
- [ ] 4.2 Reconcile active phase on wake/relaunch without backfilling multiple cycles and detect material clock disagreements; verify deterministic clock tests cover expired focus/break, one-alert-only recovery, sleep, and clock jump resolution.
- [ ] 4.3 Add single-instance ownership, minimize-stays-running behavior, and active-timer close confirmation; verify two-instance launch, minimize, cancel-close, and confirmed exit on Windows.
- [ ] 4.4 Document saved-state location, recovery behavior, and exit limitations in README and `docs/ARCHITECTURE.md`; verify those descriptions against manual Windows checks.

## 5. Windows packaging and release documentation

- [ ] 5.1 Establish Git history and the owner-selected public release destination for this workspace; verify the intended remote, version tag policy, and release permissions before configuring publication.
- [ ] 5.2 Add app icon, application/version metadata, and About display; verify the same version appears in the running app and package metadata.
- [ ] 5.3 Build the executable JAR with the pinned Kotlin Toolchain and package it into a runtime-bundled Windows app image and MSI using pinned JDK/jpackage and WiX tooling; verify the app image launches without a separate Java installation and the MSI installs and launches on a clean Windows environment.
- [ ] 5.4 Add Windows CI for build, tests, packaging smoke checks, checksum generation, and tag-bound release assets, with publication gated on successful validation; verify a failed packaging job cannot publish a release and the checksum matches the exact MSI.
- [ ] 5.5 Add `docs/RELEASING.md`, `CHANGELOG.md`, and README download/install guidance covering version/tag rules, checksums, unsigned installer warning, rollback, source-build support, and Windows-only package support; verify commands, links, and filenames against produced artifacts.

## 6. Cross-feature acceptance

- [ ] 6.1 Run the complete build/test command and follow `docs/TESTING.md` on Windows for a full four-focus cycle, both transition modes, minimize/restore, sleep/restart, clock change, and second instance; record results and resolve failures before tagging.
- [ ] 6.2 Validate this OpenSpec change strictly, review implementation against all four delta specs, and reconcile documentation with actual behavior; verify `openspec validate build-aggressive-pomodoro-desktop --strict` succeeds and no unchecked spec scenario lacks evidence.
- [ ] 6.3 Before publishing the first download, confirm license holder/contact, clean installer launch, checksum, release notes, and versioned tag/assets; verify the published release page and download links from a fresh readback.

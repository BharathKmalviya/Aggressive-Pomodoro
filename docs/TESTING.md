# Windows acceptance checks

Run `./kotlin.bat build`, `./kotlin.bat test`, and `./kotlin.bat run -m desktopApp` from the repository root. Automated tests cover reducer transitions, task credit, recovery, and snapshot validation; these scenarios check actual desktop behavior. Use Settings to set focus and both breaks to 1 minute while testing, then restore preferred values.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Start and pause | Start focus, wait about 10 seconds, pause, wait 10 more, resume | Display does not count down while paused; it resumes from the same remaining time. |
| Button sounds | With button clicks enabled, use Start, Pause, Resume, Settings, and task controls; then disable button clicks and repeat | Short clicks are audible only when enabled. Completion sound setting remains independent. |
| Completion sound | Enable completion sound, finish a 1-minute focus. Disable it and finish another | Audible completion cue only when enabled; visual dialog appears in both cases. |
| Automatic transition | Leave automatic transitions enabled and finish focus | Short break starts while the focus-complete dialog stays visible. |
| Confirmation transition | Disable automatic transitions, finish a break, then acknowledge | Next focus remains stopped until the dialog's start action. |
| Unattended limit | In automatic mode, let focus and its next short break finish without acknowledging the first dialog | Two completion events remain; another focus timer does not start until both are acknowledged. |
| Focus cycle | Complete four 1-minute focus blocks and their intervening breaks | Fourth completed focus leads to a long break; skipped focus never increments the count. |
| Reset and skip | While running, use Reset and Skip and cancel once, then confirm | Cancel preserves timer; reset returns full current duration; skip advances without focus credit. |
| Tasks and report | Add a task with estimate 2, select it, complete focus, then open Reports | Task shows 1/2; today's block and minutes increase once. Skipping does not add credit. |
| Seven-day dates | Open Reports on a day with no completed focus and after a local-date rollover | Today and the previous six calendar dates appear; dates without a completed focus show zero. Older dates are excluded. |
| Minimize and restore | Start a 1-minute focus, minimize the window, wait for completion, restore | Timer progresses while minimized; taskbar attention is requested and completion dialog is still present. |
| Restart recovery | Start focus, close with confirmation, reopen after its deadline | One completion is recorded and shown; no extra cycles are fabricated. |
| Clock adjustment | During a running phase, move the Windows clock forward by more than 2 minutes, then restore it | Timer pauses with a clock-change explanation; resume or reset is available. Restore the system clock after the check. |
| Keyboard and resize | Tab through every button, checkbox, task field, dialog action; activate with Enter/Space. Resize to minimum width | Focus is visible, controls remain reachable, and narrow layout stacks timer and task panel without clipping the countdown. |
| Second instance | Launch the app twice | Second launch shows an already-running message and does not open another timer. |
| Damaged snapshot | Back up `%APPDATA%\AggressivePomodoro\session.properties`, replace it with invalid text, then reopen; restore the backup afterward | App starts safely with a visible recovery message. |
| Sound unavailable | Disconnect or disable the current audio output, then complete a phase | Visual dialog and timer transition still work. |

Do not treat a passing build or app-image process smoke check as proof of these visual, audio, install, or sleep scenarios. Record the Windows version, app version, outcome, and any screenshot or log when performing release acceptance.

## Aggressive interface and regression checks

New durations apply to the next created phase. After setting all durations to 1 minute, skip the idle focus and idle break to create a fresh 1-minute focus before starting these checks. Keep a backup of existing data before persistence experiments.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Alarm preview | Open Settings and press TEST ALARM, including with completion sound disabled; press it repeatedly | A distinct multi-pulse alarm plays without stacked clips. Preview does not complete a phase or change sound settings. |
| Persistent pressure | Enable aggressive reminders and completion sound. Finish focus and leave its dialog open for 25 seconds | Initial alarm, then reminders about ten seconds apart. Counts/task credit stay unchanged; the dialog identifies the completed phase and current phase/countdown. |
| Immediate mute | During a sounding completion, use its mute action and wait 15 seconds | Current alarm stops, future reminders stay silent, dialog remains visible, and completion sound is off in Settings. |
| Reminder opt-out | Disable aggressive reminders while keeping completion sound enabled, then finish a phase and wait 25 seconds | One completion sound, persistent dialog, no periodic reminder. |
| Acknowledge | Acknowledge the last pending alert; wait at least 12 seconds before another completion | Alarm stops and no reminders remain for the acknowledged event. |
| Two queued events | Leave focus and break alerts unreviewed, review one, then wait ten seconds | Timer still waits with the remaining alert; reminder resumes for that alert only. Reviewing the last starts the next phase. |
| Restore pending | Exit with an unacknowledged alert and reopen | Saved alert is surfaced once immediately, then reminders follow the preference. No duplicate focus/task credit. |
| Final minute / pause | Start focus, watch its final minute, then pause | Urgent final-minute instruction appears. Paused state is unmistakable and countdown stays fixed; resume is reachable by keyboard. |
| Stale confirmation | In the last seconds, open Reset or Skip and leave it open through completion | Confirmation disappears; completion alert takes priority. It cannot reset/skip the new break. |
| Paused progress | Pause partway through focus, try Reset and Skip, and cancel each | Both ask before discarding progress; cancel retains exact paused remaining time. |
| Overlapping dialogs | Leave Settings, Reports, or About open through a completion | Secondary dialog closes/yields; completion alert and its actions are reachable. Unsaved Settings edits are discarded. |
| Current versus next task | Start task A, select B during focus, pause/resume, then complete | Current task stays A; next task is B. A receives the completed block. |
| Unassigned restart | Start without a task, add/select one during focus, exit/reopen before completion | Running block remains unassigned; newly selected task is for the next block. |
| Removed/completed task | Start A, delete A, restart; separately start B, mark B done, and let its block finish | Removing A does not reset the timer/other data. B still receives its earned block. |
| Narrow / short / large clock | Resize to 560×620, then wide but short; set focus to 180 minutes and create a new phase; Tab through controls and scroll | Timer and controls remain readable/reachable, including the three-digit minute clock. Task panel scrolls and keyboard focus is visible. |
| Unavailable output | Disable audio output and preview or finish focus, then restore output and preview again | Timer continues and visible warning explains playback failure; successful alarm playback clears it. |
| Failed close save | In a disposable profile, make the save destination unwritable, attempt exit, then restore access and retry | Failure leaves the app responsive and writer functional; retry saves latest state before exiting. |

Automated regressions cover deadline-boundary intent, task ownership/deletion, snapshot migration/validation, backward clocks, reminders with injected time, and exception-safe effects. These checks do not measure audible loudness or prove native taskbar attention and keyboard rendering.

## Recorded aggressive-refresh verification — 2026-09-28, before updater additions

- `./kotlin.bat build`: passed, including shared and desktop test compilation.
- `./kotlin.bat test`: 62 passed, zero failures (32 shared domain tests, 30 desktop storage/controller/audio tests).
- `openspec validate build-aggressive-pomodoro-desktop --strict`: passed.
- The aggressive-interface Windows manual matrix above has not been executed in this update. Existing release acceptance tasks remain open; no new installer or public release was produced.

## Manual update and About checks

Use a disposable profile for installer/rollback checks. Do not downgrade a real version 3 snapshot into v0.1.0. Deterministic tests simulate newer releases so no fake public tag is required.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Repository link | Open About and use OPEN GITHUB | The correct public repository opens in the default browser; current app version remains visible. A browser failure displays the URL and explanation. |
| Current version | About → CHECK FOR UPDATES with the latest stable build | Reports no newer update; no installer download starts. |
| Offline / rate limit | Disconnect networking, check, reconnect, CHECK AGAIN; exercise rate-limit response with test transport | Clear retryable error; timer keeps running and no credentials are requested. |
| Download a newer release | With a genuinely older updater-capable build, check, choose DOWNLOAD UPDATE | Version/progress visible; file downloads only after the click. Close and VIEW UPDATE reopen the same progress. |
| Cancel / retry | Cancel during a download, wait for cancellation to settle, retry | Partial file cannot be installed, no overlapping download starts, retry can finish. |
| Alert priority | Let a focus phase complete while the update dialog or install confirmation is open | Completion alert takes priority; download continues if already started; installation does not happen. |
| Changed installer | Download, alter the cached MSI, then confirm installation in a disposable profile | Hash verification prevents Windows Installer opening; app stays usable and offers redownload. |
| Save / launch failure | Make disposable profile backup destination unwritable or simulate failed launcher with tests, then install | Failure remains visible, app and timer stay available, no unsaved-state shutdown occurs; fixing the issue allows retry. |
| Interactive upgrade | Back up disposable profile, start a task, download and confirm INSTALL & EXIT | Snapshot is saved and backup created before app exits. Windows installer/UAC remains interactive. Reopen manually; upgraded version, tasks, history, and session recover correctly. |
| Restart before installing | Finish download, exit normally, restart | A new manual check/download is required before installation; an old cache file alone cannot authorize execution. |

Release CI separately exercises clean installation and the immutable v0.1.0 MSI upgrade path. That process smoke evidence does not verify interactive UAC, default-browser launch, audible reminders, or keyboard behavior.

## Recorded updater verification — 2026-09-28

- Kotlin build and executable JAR packaging passed.
- Full automated suite: 83 passed, zero failures (32 shared, 51 desktop), with no test discovery omissions.
- Strict OpenSpec validation and actionlint for both Windows workflows passed.
- A live probe using the packaged updater checked the real latest GitHub release, reported no newer version for 0.2.0, downloaded the 92,951,448-byte v0.1.0 MSI, and passed both initial SHA-256 verification and pre-install re-verification. No installer was executed on the development machine.
- Published v0.1.0 MSI was independently downloaded and hash-verified; its UpgradeCode matches the pinned code in the new packager. A metadata-only packaging probe validated the new version/upgrade identity; that probe is not a release artifact and was not installed.
- Manual update download/install/UAC, browser launch, sound, keyboard, and sleep scenarios above remain pending.

## Recorded v0.2.0 release verification — 2026-09-28

- [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36462170882) and [Windows release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36462170953) passed for `e93020082591517574ac35efbd1f48b9f2c00405`, including the automated build/tests, clean MSI installation/launch, and upgrade from the checksum-pinned public v0.1.0 MSI.
- The upgrade gate verified that the old product was removed and the new app saved snapshot format 3 with the same paused time, captured task, task credit, and daily history. Its close helper was also exercised locally against a hidden packaged app in an isolated profile and verified a normal exit with a saved snapshot.
- All three public release assets were independently downloaded and checked against their GitHub digests. MSI checksum and pinned MIT License verification passed; the immutable tag points to the exact validated commit. Hashes are recorded in [RELEASING.md](RELEASING.md).
- A post-publication probe using the packaged updater correctly reported 0.2.0 as current and detected the real 0.2.0 release for a simulated older installed version. No local installer was launched.
- These automated results do not close the outstanding manual Windows acceptance matrix.

## Recorded screenshot and update-check smoke — 2026-09-28

The current executable JAR was launched on Windows with an isolated demo profile containing sample tasks and history. The app's start action produced a running countdown and the captured-task label. Settings, About, and the manual update check were opened through the actual UI. About showed version 0.2.0 and the full GitHub repository link; the live check against the published release reached "YOU'RE UP TO DATE" with installed version 0.2.0.

The four [README screenshots](../README.md#screenshots) were refreshed from those real screens. Modal captures were cropped to their actual dialog pixels with a surrounding margin, and all exported images were inspected for readable text and framing. This records a limited visual and update-check smoke pass; it does not verify alarm loudness, browser launch, interactive download/install/UAC, keyboard accessibility, or sleep/recovery acceptance.

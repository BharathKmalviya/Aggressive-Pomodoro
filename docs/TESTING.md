# Windows acceptance checks

## v0.3.5 published verification — 2026-10-09

[Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37946042697) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37946042644) passed for `a2ba6f79ba8ad123ba4117508d8d7cb2caba8c04`: all 138 tests (40 shared, 98 desktop; zero failures), runtime packaging, clean MSI installation/launch, second-launch acknowledgement and baseline upgrade/data preservation. Both jobs' packaged updater probe downloaded and reverified the full v0.3.4 MSI before publishing the correction. Fresh API/latest/tag readback confirmed immutable stable v0.3.5; independent asset downloads passed digests, exact MSI checksum and MIT License. Read-only MSI metadata confirms version 0.3.5 and unchanged UpgradeCode. Full hashes and evidence are in [Releasing](RELEASING.md#v035-published-verification). Native scenarios below remain pending; no local app/installer was launched.

## Updater connection correction checks — 2026-10-09

### Native local follow-up — 23:03–23:10 IST

The owner explicitly requested running locally and debugging the continuing issue. Read-only inspection found the installed `C:\Program Files\AggressivePomodoro` configuration and bundled version resource still at **0.3.3**, without an OkHttp dependency. Installed JAR SHA-256: `4758fe4db386f9a9468e8ca851c892883d684e8e840391df026b10467648a877`. The published correction had not replaced this installed copy.

Launched the packaged v0.3.5 libraries with an isolated `APPDATA` profile and a front-of-classpath version resource reporting 0.3.3 solely to offer the real v0.3.5 download. No repository version/source or installed files were changed. Through the actual Windows UI, About found v0.3.5, checksum-fetch feedback appeared, installer transfer showed live bytes/22% progress, Cancel promptly returned to Available, and retry reached **Ready to install**. Closing the result and reopening through About → View update retained Ready. The downloaded MSI is 103,027,608 bytes with SHA-256 `45c4d007b172a0bd6b6e6dd6d9b7764087df63e707ef3d829cdf853274434b96`, matching the independently verified public artifact; no partial installer remained. Debug stderr was empty. Computer Use selected only the isolated JVM window for these interactions.

This proves the correction's native check/download/cancel/retry/result path on this machine. It does not prove cancellation on a disconnected route, completion-alert priority, other scaling/keyboard cases or interactive installation/UAC. Those checks, the installed-copy upgrade and section 24.5 remain pending. Earlier statements about no local native launch describe pre-publication verification, before this newly authorized follow-up. The real running profile was not modified or stopped during diagnosis.

The owner supplied v0.3.3 screenshots showing v0.3.4 stuck at 0%, then a generic connection failure. Network-only diagnostics reproduced a timeout to the first release-assets address while the other resolved addresses connected. These observations establish the failure, not a native acceptance pass for the fix. Use v0.3.5; install its MSI manually once if the old updater cannot download it, after explicitly choosing Exit and backing up the profile.

| Scenario | Manual action | Expected result |
| --- | --- | --- |
| Startup and upgrade | Install v0.3.5, reopen, inspect About and saved work | Version is 0.3.5; timer, tasks, close choice, alarm choices and reports recover. No automatic network check. |
| Stages and progress | In a disposable source checkout/profile, retain the correction code but locally set `version.properties` to 0.3.4; explicitly check/download v0.3.5. Do not commit that version edit. | Checksum fetching and connecting show activity without a misleading 0%; transfer shows bytes/percent; verification appears before Ready. No install starts during download. The unmodified v0.3.5 build remains Up to date until a later stable release exists. |
| Cancel while connecting or stalled | On a controlled disconnected/slow network, press Cancel during checksum/connection/body wait | Cancelling completes promptly, partial files disappear, Download Update is available again and notes remain. No overlapping requests or frozen timer. Restore the network before retrying. |
| Failure and retry | Let a controlled timeout/offline download fail, reconnect, press Download Again | Error identifies the connection problem; primary button retries the checked release, secondary checks for a newer version; success clears the old error. |
| Hidden dialog and completion | Close the dialog during download, let a phase finish, reopen the update from About/tray | Timer and completion review keep priority; download continues and its result remains available. |
| Keyboard and scaling | Tab/Enter/Escape through download/error/notes at 100%, 150% and 200% display scaling | All actions are reachable and labels remain readable; dismissing does not install or cancel the download. |
| Install and recovery | In a backed-up disposable profile confirm Install & Exit; separately cancel confirmation or exercise a save/launch failure | Confirmation cancellation keeps focusing. Success verifies, saves, backs up and opens interactive MSI/UAC, then exits. Failure leaves the app and saved work usable. |

These native checks remain pending. Network-only packaged download/reverification and socket/controller regressions are separate automated evidence; no native app or installer is launched on the development machine.

Local verification (2026-10-09): build, all 138 tests (40 shared, 98 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec, workflow actionlint and PowerShell parsing passed. Seven new tests cover real alternate-route success, blocked headers/body cancellation, manual redirects, sanitized errors, ordered service stages and guarded controller callbacks. The packaged production updater downloaded and reverified the complete v0.3.4 MSI twice on the affected network (101,704,600 bytes; SHA-256 `2fd09c445b3bef0261e235287d7aa6ceebe414c991daf3c2cedb367cd76d0fcd`); the final probe also checked bundled Apache dependency notices. This verifies the corrected transport route on this machine, not the native updater/UAC flow.

## v0.3.4 published verification — 2026-10-09

[Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37940027472) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37940027327) passed for `ebd50725c0b52d085d5fdf46842ab647be92e54d`: all 131 tests (40 shared, 91 desktop; zero failures), runtime packaging, clean MSI installation/launch and baseline upgrade/data preservation. The installed app acknowledged a second launcher and stayed alive while that new process exited successfully. Fresh API/latest/tag readback confirmed immutable stable v0.3.4; independent asset downloads passed digests, MSI checksum and MIT License. Read-only MSI metadata confirms version 0.3.4 and the pinned UpgradeCode. Hashes and exact evidence are in [Releasing](RELEASING.md#v034-published-verification).

Packaged acknowledgement proves activation delivery, not Windows foreground focus. No native app or installer was run on the development machine. The correction checks below and earlier manual gates remain pending; OpenSpec stays active.

## Tray usability correction checks — 2026-10-09

The owner supplied a v0.3.3 screenshot showing nearly all tray actions greyed out. Source inspection confirms the close chooser, save error and actual shutdown shared one blanket disable flag, and summaries were always disabled. That is issue evidence, not a recorded pass for this correction. Use v0.3.4 or `.\kotlin.bat run -m desktopApp`; the following native checks remain pending.

| Scenario | Action | Expected result |
| --- | --- | --- |
| Launcher restore | Launch through desktop/Start-menu icon while already visible, minimized and hidden; repeat with a close chooser (Remember checked), pending completion or save error | Normal second launch restores the same app with no Already running modal or duplicate timer. Close choice cancels without saving; pending alert/error remains the action needing attention. Time, task ownership and reports are unchanged. |
| Startup and unresponsive owner | Quickly launch twice during startup; separately use an older running build or deliberately unresponsive disposable process, then launch | Startup request is retained and the existing window opens when ready. A five-second failed acknowledgement gives actionable recovery feedback, never a second owner; launching after the owner exits opens normally. |
| Reported close-choice state | Set close behavior to Ask; X/Alt+F4, check Remember, then right-click the tray and select Pause/Resume, Settings, a sound toggle or Run in background; repeat with Open | Normal actions are enabled. Each selection cancels the uncommitted chooser and performs the requested action; checking Remember alone never changes the saved close behavior. Tray Exit still opens the explicit chooser. |
| Clickable summaries | Click phase/time/status, then today's blocks/minutes | Both look actionable in ordinary use: status opens the same timer window and totals open Reports with matching daily credit. No new controller or duplicate credit. |
| Compact groups and keyboard | Open Timer options and Alerts using mouse and arrows/Enter; Escape; repeat at 100/150/200% scaling | Reset/Skip remain confirmed and phase-bound; sound/reminder checkmarks reflect saved values. Parent and child rows are reachable, legible and fit the screen. Native menu uses Windows appearance; app-owned dialogs retain the app theme. |
| Direct background | Choose Run in background during idle/running/paused and while a close choice is open, then double-click the tray | Window hides; timer, alarms and persistence continue. The same window returns. The direct action does not change the saved X/Alt+F4 choice. |
| Pending completion | Background a one-minute focus, then inspect/click the menu with its alert pending | Review and alert toggles remain available; conflicting timer/navigation options are disabled until review. The status/tooltip explains pending alerts; muting keeps the visual event and earned credit. |
| Failed exit and continue | In a disposable profile deny saving, choose Exit, then tray Resolve save error or Review save error; use Keep app open, restore access, edit and retry Exit | Error remains reachable; conflicting mutations stay restricted until continuing. Keep app open restores normal controls and usable saving; Retry waits for the latest save. Exit Anyway retains its data-loss warning. |
| Genuine shutdown/update | Inspect tray while final saving or Install & Exit is preparing an update | Label identifies saving/installation; conflicting actions cannot interrupt it. Open can restore the same window without cancelling the save. Successful shutdown removes the icon. |

Local verification (2026-10-09): final Kotlin build, all 131 tests (40 shared, 91 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec and Git diff checks passed. Ten new tests cover real AWT menu resources/queued event guards/checkbox rollback on the event thread, close-choice availability/genuine restrictions, and real temporary-profile activation/ownership/startup/concurrency/malformed input/failure/cleanup. Read-only JAR inspection verifies version 0.3.4 and the menu/activation/mode classes. Both workflow actionlint checks and all seven release PowerShell blocks passed. Release CI also passed the new packaged second-launch acknowledgement gate: the new process exited successfully and the existing owner stayed alive. Native popup appearance, focus, keyboard and scale checks above require user observations; no native app or installer was launched on the development machine.

## v0.3.3 published verification — 2026-10-09

[Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37935932956) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37935932971) passed for `7b5b5ef95a2bda642e3b74e5930ec22f6ec016f2`, including all 121 tests (40 shared, 81 desktop; zero failures), runtime packaging, clean MSI installation/launch and v0.1.0 upgrade/old-product removal/paused timer/task/history preservation. Fresh API/latest/tag readback confirmed the immutable stable v0.3.3 release; independent downloads matched all asset digests, MSI manifest checksum and pinned MIT License. Read-only Windows Installer metadata confirms ProductVersion 0.3.3 and the pinned UpgradeCode. Hashes are in [Releasing](RELEASING.md#v033-published-verification).

No native app or installer was run on the development machine. Saved close-choice, tray/tooltip/keyboard/scaling/audio/sleep/updater/UAC acceptance remains pending below; automated release evidence does not mark these manual scenarios passed. Snapshot format remains 3 and the OpenSpec change stays active.

## v0.3.2 automated release evidence — 2026-10-09

The owner requested publication with the close/background and selectable-alarm manual scenarios below still pending. Local release-version build, all 111 tests (40 shared, 71 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec and diff checks passed. Independent JAR inspection verified version 0.3.2, all nine adapted WAV hashes, PCM format/duration/peak/fade bounds and bundled credits. [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37928822524) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37928822448) passed for `c17d48023fc087cf2588145025afd544ed417561`, including runtime packaging, clean MSI install/launch, v0.1.0 upgrade/old-product removal, normal close-choice Exit and paused timer/task/history preservation. The immutable release's tag and all asset hashes were independently verified; read-only inspection confirmed the public MSI contains version 0.3.2, all nine prepared WAVs and sound credits. See [Releasing](RELEASING.md#v032-published-verification) for hashes. No installer/app was executed on the development machine. This evidence does not prove native audio, tray/notification, keyboard/scaling, sleep or interactive updater/UAC behavior; those manual scenarios remain unverified.

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
| Reset and skip | While running, use Reset and Skip and cancel once, then confirm | Cancel preserves timer; reset returns the latest saved full duration; skip advances without focus credit. |
| Tasks and report | Add a task with estimate 2, select it, complete focus, then open Reports | Task shows 1/2; today's block and minutes increase once. Skipping does not add credit. |
| Seven-day dates | Open Reports on a day with no completed focus and after a local-date rollover | Today and the previous six calendar dates appear; dates without a completed focus show zero. Older dates are excluded. |
| Minimize and restore | Start a 1-minute focus, minimize the window, wait for completion, restore | Timer progresses while minimized; taskbar attention is requested and completion dialog is still present. |
| Restart recovery | Start focus, close with confirmation, reopen after its deadline | One completion is recorded and shown; no extra cycles are fabricated. |
| Clock adjustment | During a running phase, move the Windows clock forward by more than 2 minutes, then restore it | Timer pauses with a clock-change explanation; resume or reset is available. Restore the system clock after the check. |
| Keyboard and resize | Tab through every button, checkbox, task field, dialog action; activate with Enter/Space. Resize to minimum width | Focus is visible, controls remain reachable, and narrow layout stacks timer and task panel without clipping the countdown. |
| Second instance | Launch the app twice while visible/minimized/hidden | In v0.3.4, the launcher restores the existing window without a normal Already running message and does not open another timer. An unresponsive older owner retains actionable failure guidance. |
| Damaged snapshot | Back up `%APPDATA%\AggressivePomodoro\session.properties`, replace it with invalid text, then reopen; restore the backup afterward | App starts safely with a visible recovery message. |
| Sound unavailable | Disconnect or disable the current audio output, then complete a phase | Visual dialog and timer transition still work. |

Do not treat a passing build or app-image process smoke check as proof of these visual, audio, install, or sleep scenarios. Record the Windows version, app version, outcome, and any screenshot or log when performing release acceptance.

## Saved close choice checks — 2026-10-09

Use v0.3.3 or `.\kotlin.bat run -m desktopApp`; native acceptance is pending. Start with **Settings → When closing the window → Ask every time → Save Rules**. Repeat X/Alt+F4 in idle, running focus/both breaks, paused and waiting states. Use a disposable profile for storage failures.

| Scenario | Action | Expected result |
| --- | --- | --- |
| Default and cancellation | Open the close dialog, check Remember my choice, then Cancel/Escape/dialog X; close again | No action or preference was saved; checkbox starts unchecked in each new chooser. |
| Remember Background | Check Remember and choose Background (or B); Show, then close with X/Alt+F4; tray Exit, exit explicitly and relaunch | Later window closes hide without a chooser; same timer/credit/alerts persist. Tray Exit still asks. Relaunch is visible and the saved Background setting remains. |
| Remember Exit | Check Remember and choose Exit (or E); relaunch, inspect Settings, then X/Alt+F4 with active/paused timer | Choice survives restart; later close waits for saving and exits without a chooser. Relaunch restores data and no alarms run after exit. |
| Change and discard in Settings | Choose Ask every time, Cancel Settings, close through tray Exit and return; then select Ask and Save Rules | Cancel retains the previous saved choice; Save restores the window-close chooser. Switching close choice preserves timer progress, other settings and unsaved sound/duration edits. |
| Tray fallback/loss | Save Background, exercise an unavailable tray or restart Explorer while hidden, then close/restore | Unavailable tray minimizes to taskbar; removed tray restores a hidden window; Background preference is not rewritten. Tray/taskbar restore stays reachable. |
| Keyboard and scale | At 100%, 150%, 200% scaling use Tab/Space on Remember, B/E actions, Escape and the Settings dropdown | Focus/checkmark visible, all text/actions reachable, keyboard actions honor the checked value and Cancel saves nothing. |
| Save failure and retry | Make disposable profile saves fail after choosing remembered Exit; restore access and Retry Save or reopen Settings | App remains visible and usable; successful retry writes the latest choice and data. Exit Anyway keeps its explicit data-loss warning. |

Local v0.3.3 preparation (2026-10-09): Kotlin build, all 121 tests (40 shared, 81 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec and Git diff checks passed. Four new deterministic tests cover close policy/fallback/explicit tray Exit, saved/unknown/legacy preferences with retained data, and final-save/relaunch/reset-to-Ask with unchanged progress/task ownership. Read-only JAR inspection verifies 0.3.3 and the expanded tray/close policy/shared preference/request bridge. Native scenarios above remain pending; no native app or installer was run on the development machine. The owner requested publication through the existing automated Windows gates after implementation.

## Expanded tray menu checks — 2026-10-09

Use v0.3.3 or the source build with `.\kotlin.bat run -m desktopApp`; v0.3.2 has the original Show/Exit menu. These are manual acceptance steps, not recorded passes. Use one-minute focus/break durations for completion checks and a disposable profile for save-failure checks.

| Scenario | Action | Expected result |
| --- | --- | --- |
| Native menu and tooltip | Right-click the tray icon while idle, running focus, paused, and in both breaks; hover between checks; repeat at Windows 100%, 150%, 200% scaling | Phase, rounded-up time and Ready/Running/Paused/Waiting are current; daily blocks/minutes match the app. Native labels, especially Exit..., have no missing glyph. Menu fits and keyboard arrows/Enter/Escape work. |
| Hidden controls | Background a selected task's focus; tray Start/Pause/Resume, wait 15 seconds while paused, then resume and Show | Same timer/controller; pause holds exact time, resume retains captured task, no extra credit or forced window opening. |
| Reset and skip | From idle/running/paused and each phase choose Reset or Skip; cancel, repeat and confirm; resize narrow/wide and minimize/restore after cancellation | Same window restores and asks before mutation, including idle tray actions. Cancel preserves progress; confirmation uses saved reset duration; skip earns no credit. Cancelled requests never reopen on resize/restore. |
| Stale confirmation | Open Reset/Skip just before a one-minute deadline; wait for completion, then try the old confirm action | Completion takes priority; old command cannot reset/skip/pause the successor or erase earned focus/task/report credit. |
| Shortcuts and unsaved edits | Background, open each Reports/Settings/About shortcut; enter an unsaved duration/sound choice in Settings, toggle tray Alarm sound or reminders, then Save/Cancel; request Reset while Settings is open | Only requested dialog opens in the same window. Tray toggles update their own field while other unsaved edits remain. Reset replaces Settings with the timer confirmation; no stacked dialogs or delayed preview. |
| Pending alerts and mute | Background a one-minute focus and leave focus/break pending; open menu, choose Review, mute Alarm sound and disable reminders; exit/relaunch | Review restores retained alerts; timer/destructive/secondary actions disabled while pending. Mute stops opening/playing alarm; reminder toggle preserves queued events/credit. Saved checkmarks survive restart. |
| Update view and retry | Tray Check for updates, then reopen during checking/download and with available/ready result; repeat a failed/offline check | Existing operation/result is viewed without resetting progress or starting another request. Idle/current/error permits a fresh manual check. No tray action downloads or installs; completion still takes priority. |
| Close/save/install priority | Open close choice or a disposable save error, then the tray; separately inspect during Install & Exit | In v0.3.4, the uncommitted close chooser leaves actions available and a tray action cancels it without saving Remember. Actual saving/install disables conflicting actions with clear labels; save error retains Show/Review save error and explicit Keep app open recovery. Successful shutdown removes the icon. |

Local verification (2026-10-09): Kotlin build, all 117 tests (40 shared, 77 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec and Git diff checks passed. Six new deterministic regressions cover phase controls, rounded countdown/local-date totals, pending/blocked action policy, update/checkmark projection, stale confirmation requests, deadline credit, pause/resume task ownership and saved toggles with pending events. Packaged class inspection confirms the tray model, native adapter and app-request bridge are included. Native acceptance above remains pending; no native app or installer was run locally, and no version bump/public installer publication is included.

## Close choice and background checks — 2026-10-09

For v0.3.3, set **When closing the window → Ask every time** before this original close-choice matrix; saved-choice checks are listed above.

Run the updated source with `.\kotlin.bat run -m desktopApp`. These changes are not in the published v0.3.1 installer. Save 1-minute durations and use Reset Block if a phase is already active. These are pending manual acceptance scenarios, not recorded passes.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Every-state close | Click X while idle, running focus/both breaks, paused, and waiting with pending alerts; repeat Alt+F4 | One chooser offers Background (or Minimize), Exit, Cancel. No direct exit or lost pending alert. |
| Cancel and keyboard | Open the chooser, use Cancel, Escape, and its X; reopen and Tab/Space through actions; use B and then E on another attempt | Cancel/dismiss keeps the session untouched; B backgrounds/minimizes, E explicitly saves and exits. Controls fit at Windows 100%, 150%, and 200% scaling. |
| Background and restore | Start focus, X → Background, wait 15 seconds; double-click tray icon or right-click → Show | Main window hides and same countdown returns with elapsed time accounted for; no reset, extra controller or duplicate credit. |
| Completion while hidden | Background a 1-minute focus with sound enabled; wait for focus then break to end without review; Show | Notification/sound attempts follow preferences; dialogs stay hidden until Show. Both events and one focus/task/report credit remain; progression waits after the second event. Reminders remain bounded. |
| Muted background | Mute completion sound and/or disable reminders, then background through completion | Preferences remain respected; retained visual alert and tray notification do not require sound. Windows may suppress notifications. |
| Tray exit and second launch | Background, launch through the shortcut again, then tray → Exit... → Cancel; repeat → Exit | In v0.3.4 the launcher restores the same window directly and owns no new timer. Tray Exit restores and prompts; cancel continues, explicit Exit saves/removes tray, relaunch recovers. |
| Tray unavailable/lost | On a desktop without tray support check X; on Windows hide to tray then restart Explorer through Task Manager | Unsupported tray offers Minimize and restores through taskbar; a removed tray icon restores the hidden app. No unreachable process. |
| Dialog priority | Open Settings/update notes then request close; separately open close choice just before completion | Secondary dialog yields; a new completion dismisses the choice and preserves its alert. Acknowledgement and background/exit never discard another event. |
| Failed exit and retry | In a disposable profile make saves fail, choose Exit (including tray Exit), dismiss error, continue editing; restore access and retry | App is visible and usable after failure; latest state saves on retry, tray removes only on actual shutdown. |
| Update/forced exit cleanup | In a disposable profile follow verified Install & Exit; separately exercise Exit Anyway after a save failure | Successful shutdown removes tray and releases ownership; failed update preparation retains working app/tray. Exit Anyway retains its existing explicit data-loss warning. |

Local verification (2026-10-09): final Kotlin build, all 106 existing automated regressions (39 shared, 67 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec and Git diff checks passed. The packaged JAR contains the new close dialog, desktop tray adapter and icon. Both workflows passed actionlint, and all seven release PowerShell blocks passed syntax parsing. These checks do not prove native window/tray/notification/keyboard behavior or execute the updated upgrade harness; the scenarios above remain pending. No installer/version publication is included.

## Selectable alarm checks — 2026-10-09

Run the updated source with `.\kotlin.bat run -m desktopApp`. The published v0.3.1 installer does not include selectable sounds. These native Windows scenarios remain pending; automated PCM/resource/routing/cancellation/storage checks do not prove audible quality.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Nine sounds and original | Settings → Focus completion sound; select and TEST each of the nine cues, then Original; repeat the break selector | All choices are reachable and distinct. Each supplied preview lasts no more than eight seconds; Original preserves focus/break motifs. No timer/task/report changes. |
| Unsaved preview / Cancel | Choose Funny for focus and Happy bells for break, preview, Cancel, reopen Settings | Previews use unsaved choices; playback stops on Cancel and saved choices remain unchanged. Selecting another cue stops a prior preview. |
| Save and restart | Choose different focus/break sounds, Save Rules, reopen Settings, Exit and relaunch | Both choices remain selected, with timer progress, tasks, totals and other preferences preserved. |
| All completion paths | Save distinct sounds, set one-minute durations, finish focus/short/long breaks; background to tray, leave events pending for a reminder, exit/reopen with one pending event | Playback uses the completed event's saved sound, including restored events and reminders; both break types use the break choice. No overlapping alarms or duplicate credit. |
| Muted preview / acknowledgement | Mute completion sound, preview selected cue, then finish a phase; enable sound again and acknowledge while its cue plays | Preview works without enabling saved sound; muted completion stays silent; acknowledgement/mute stops playback and cancels late opens. |
| Rapid previews / priority | Rapidly alternate previews, change choice, save/cancel; open Settings just before completion and preview | At most one cue plays. No stale sound after dismissal; completion preempts preview using saved choices, even if an unsaved selection differs. |
| Keyboard / small window | At 560×620 and Windows 150%/200% scaling, Tab to selectors, Enter/Space, arrows through menu, Escape, preview and Save Rules | Current choice, all options and controls remain readable/reachable; dropdown and dialog dismiss appropriately and focus is visible. |
| Old/unknown preference | In a disposable profile exit, back up snapshot, remove sound keys or set one to an unknown ID; relaunch | Only missing/unknown choice returns to Original. Other choice and valid timer/task/report state survive. |
| Audio quality / failure | Listen to all cues at comfortable volume; disable output during preview, reconnect and preview again | Cues have no abrupt endpoints or clipping; output failure is visible and recovery clears it. Timer continues. |

Local verification (2026-10-09): final build, all 111 tests (40 shared, 71 desktop; zero failures/errors/skips), executable JAR packaging, strict OpenSpec and diff checks passed. New checks cover requested/completed-phase routing, real resources, cache/fallback/recovery, decode cancellation, and tolerant persisted choices. A frame-alignment decode stall was corrected before final validation. Independent original/prepared hash, PCM, duration, peak and endpoint checks passed for all nine cues; packaged WAVs and credits match source. Native scenarios above are pending; no new installer is published.

## Saved rules and reset regression checks

Saving durations refreshes unstarted blocks immediately. Running/paused blocks keep their progress until reset or completion; use Reset Block after saving all durations as 1 minute if a block is already active. Keep a backup of existing data before persistence experiments.

Run these against v0.3.1 or the updated source build. Automated regressions cover duration/state/credit/storage behavior; Windows rendering and keyboard interaction remain manual acceptance.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Reported focus reset | Save focus as 1 minute, start, pause after about 10 seconds. Save focus as 10, then Reset Block and confirm | Save preserves paused remaining time; reset shows 10:00 idle, with no added task/report credit. Start counts down from 10:00. |
| Save before start | With focus idle, save 1 then 10 minutes, reopen Settings, then start | Idle timer immediately shows 10:00; Settings retains 10; Start uses 10 minutes. Cancel an unsaved edit to 2 and verify the saved duration remains 10. |
| Both breaks and shorter rules | Skip idle focus to a short break. Save short break as 3, start/pause, save it as 1, reset. Reach a long break by completing the configured focus cycle; repeat with long break 7 then 2 | Each idle break refreshes on save; active/paused break preserves progress until reset. Reset shows the latest full duration for that break, with unchanged focus credit. |
| Resume after saving | Start a 1-minute focus, pause near 0:50, save focus as 10, then Resume without reset | Countdown resumes near 0:50. Its completion adds one minute to Reports; the next focus uses 10 minutes. |
| Transition and cycle rules | Set both breaks to 1 and cycle length 2. Disable automatic transitions while focus runs and let it end. Acknowledge/start, finish the break, then complete the second focus | Upcoming transitions wait for acknowledgement; the second completed focus leads to a long break. Existing progress and counts are preserved on save. |
| Preferences and relaunch | Change completion sound, clicks, reminders and Reduce motion; save/reopen Settings. Save focus as 10, reset, close normally and relaunch | Toggles reflect saved values and their next interactions/alerts use them; Reduce motion applies immediately. Relaunch shows 10:00 idle with tasks/history preserved. |

Native Windows results for these scenarios are pending; record outcomes before closing manual acceptance gates.

## Aggressive interface and regression checks

Use 1-minute durations for these checks. Save refreshes an idle block immediately; reset any running/paused block to load the saved duration before starting.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Alarm preview | Open Settings and press TEST FOCUS ALARM or TEST BREAK ALARM, including with completion sound disabled; press repeatedly | A distinct multi-pulse alarm plays without stacked clips. Preview does not complete a phase or change sound settings. |
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

## Sound and motion checks — 2026-10-01

Use Settings to set all durations to 1 minute, save, then skip idle focus and idle break to create a fresh short focus. These changes do not affect the currently created phase. Restore your preferred rules afterward.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Distinct motifs | Settings: TEST FOCUS ALARM, then TEST BREAK ALARM; finish focus and both break types | Focus has an ascending three-pulse chord, breaks a sharper return motif. Completion uses the phase that ended; preview changes no totals/preferences. |
| Preview lifetime | Preview, immediately Cancel; repeat with Save; repeat rapid previews of each type | Dismiss/save stops preview. At most one alarm plays; no late sound after dismissal. |
| Preview preemption | Start 1-minute focus, open Settings near its deadline, preview just before completion, including with completion sound muted | Settings yields and preview stops. Enabled completion plays the correct event motif; muted completion stays silent and visible. |
| Offline clicks / click mute | Disconnect networking; use Start/Pause/Resume and task controls. Disable button clicks, save, then repeat rapid controls | Bundled click works offline, never stacks or masks an alarm. Save disabling clicks and subsequent controls are silent; completion preference unchanged. |
| Audio output recovery | Disconnect output during preview/completion, reconnect, then preview again | Failure shows an explanation; timer/credit stays correct. Successful preview clears warning. |
| Bounded transitions | Start/pause/resume focus, enter final minute, finish focus, acknowledge; repeat quickly and resize across 850 dp width | Brief entrances and phase-color change; final-minute emphasis finishes once per entry. Clock has no outgoing stale digits; controls stay stable/reachable. New phase progress starts at its own value without a backward sweep. |
| Exact paused progress | Pause during progress movement; leave paused 10 seconds. Reset with confirmation; use confirmation transition mode | Progress/clock hold exactly when paused/waiting and reset immediately. No animation changes timer state or counts. |
| Reduce motion persists | Enable Reduce motion, save, repeat transitions, exit/reopen and inspect Settings | Custom entrances, scaling, accent interpolation and progress smoothing stop; timer/alerts work and preference remains enabled. Built-in Material control feedback still works. |
| Keyboard / narrow layout | Tab and Space through both previews and Reduce motion; resize to 560×620, scroll Settings, use 180-minute focus | Labels remain readable, keyboard focus visible, all options reachable, countdown and emphasis remain within panel. |
| Queued/restored phase sounds | Leave focus and break unreviewed, wait for a reminder, acknowledge first; exit/reopen with remaining break event | Immediate break sound identifies break completion; reminders use first pending event, then remaining break event. No duplicate credit. |

Automated audio tests use fake clips and latches to verify races without relying on a real audio device. Native playback quality, keyboard, resize, sleep, and taskbar attention remain manual acceptance gates.

Local verification (2026-10-01): Kotlin build passed; all 93 tests passed (32 shared, 61 desktop), zero failures or skips. Executable JAR packaging passed, and bundled click/license/credit resources were read from the produced JAR and matched the source. Strict OpenSpec validation and Git whitespace checks passed. The manual sound/motion scenarios above have not been run; no new version or installer release was produced.

Release preparation (2026-10-01): the owner subsequently requested publication as v0.3.0 with these manual gaps disclosed. Build, all 93 tests with no failures/skips, executable JAR packaging, strict OpenSpec validation, and diff checks passed again after the version update. Packaged metadata reports 0.3.0 and bundled sound/license resources match source. Automated Windows release gates and fresh asset verification are required before a publication claim; manual acceptance remains pending.

Published verification (2026-10-01): [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36872068972) and [release validation/publication](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36872068933) passed for `acd7b7b3c61d7f1293b2d4f6d5d0053232addc93`, including all 93 tests, clean MSI installation/launch, and v0.1.0 upgrade with old-product removal and paused timer/task/history preservation. The immutable v0.3.0 tag points to that commit. Fresh downloads passed GitHub asset size/digest, MSI manifest checksum, and pinned MIT License checks; hashes are recorded in [RELEASING.md](RELEASING.md#v030-published-verification). These automated results do not close the manual scenarios above.

## Recorded aggressive-refresh verification — 2026-09-28, before updater additions

- `./kotlin.bat build`: passed, including shared and desktop test compilation.
- `./kotlin.bat test`: 62 passed, zero failures (32 shared domain tests, 30 desktop storage/controller/audio tests).
- `openspec validate build-aggressive-pomodoro-desktop --strict`: passed.
- The aggressive-interface Windows manual matrix above has not been executed in this update. Existing release acceptance tasks remain open; no new installer or public release was produced.

## Manual update and About checks

For embedded notes in v0.3.1 or the source build (`.\kotlin.bat run -m desktopApp`), check these Windows scenarios. These are manual acceptance steps, not recorded passes:

| Scenario | Action | Expected result |
| --- | --- | --- |
| Current release notes | About → CHECK FOR UPDATES → VIEW RELEASE NOTES while installed/current versions match | Notes and the fetched release version appear in the dialog; no browser opens and no installer download starts. |
| Reading and keyboard | Expand notes, scroll with wheel/keyboard, select/copy text, Tab to HIDE RELEASE NOTES and CLOSE, then use Escape | Long content remains readable; controls remain reachable; text is selectable; links/HTML do not navigate or execute. |
| Newer-release notes and retry | On an older updater-capable build, read notes, download, cancel, retry, then close/reopen from About | Notes remain associated with the same release through progress/cancel/retry and are available after reopening. |
| Fresh offline check | Read notes, disconnect network, then CHECK FOR UPDATES again | Previous notes are cleared; an error and retry are visible; timer continues. |
| Completion priority | Let a focus block finish while notes are expanded | Completion alert replaces the update dialog; no installation starts. |

Missing/null/non-string/blank release bodies and Unicode-safe long-note truncation use fake metadata in deterministic service tests; do not change public release metadata for manual testing.

Embedded-notes local verification (2026-10-01): Kotlin build, all 97 automated tests (32 shared, 65 desktop; zero failures/skips), executable JAR packaging, strict OpenSpec validation, and diff checks passed. Service tests verify one-request current/older/newer notes, optional-body tolerance, and bounded Unicode-safe text. Controller checks cover retained notes during download/cancel/retry/install failures and cleared notes on fresh/offline checks. The manual reading/keyboard/scrolling/selection and completion-priority scenarios above remain pending. This source change does not replace the published v0.3.0 installer.

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

## Recorded v0.3.1 release verification — 2026-10-01

[Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36875954691) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36875955080) passed for `2a57bff99ac4c27ca1dfaf69646bf690247ea699`, including all 106 tests (39 shared, 67 desktop), runtime packaging, clean MSI installation/launch, and the published v0.1.0 upgrade/data-preservation gate. Fresh readback verified the immutable v0.3.1 tag and independently downloaded MSI/checksum/license assets; hashes are recorded in [RELEASING.md](RELEASING.md). The new saved-rules/reset and embedded-notes manual scenarios above remain pending, along with the earlier native Windows acceptance checks.

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

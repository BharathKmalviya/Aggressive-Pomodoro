# Spec Delta

## Purpose

Defines how phase completion gets the user's attention and how the next phase starts, including unattended and muted desktop conditions.

## ADDED Requirements

### Requirement: Distinct offline sounds and bounded lifetime
The app SHALL use bundled licensed button feedback and distinct focus-complete and break-complete sounds selected from the completed event. Settings SHALL preview both without changing timer or sound preferences. Preview SHALL stop when Settings closes or a completion takes priority. Mute, acknowledgement, disabled click feedback, and shutdown SHALL invalidate corresponding queued/opening playback. Stale callbacks SHALL NOT clear newer playback. Cleanup SHALL be idempotent and safe on audio callback threads.

#### Scenario: Break completes with a queued focus alert
- **WHEN** a break completes before the focus alert is reviewed
- **THEN** its immediate sound identifies break completion; subsequent reminders identify the first pending event

#### Scenario: Dismiss during delayed preview open
- **WHEN** Settings closes while preview is opening an audio device
- **THEN** the late clip is released without starting or overwriting feedback

#### Scenario: Disable clicks during device open
- **WHEN** click sound is disabled while a prior click is queued or opening
- **THEN** the stale click cannot start and future interactions remain silent

### Requirement: Persistent aggressive reminders
The application SHALL default to aggressive reminders that repeat attention and enabled completion sound every ten seconds while a completion remains pending. Reminders SHALL NOT add completion events or task/history credit. Users SHALL be able to disable reminders independently of sound, mute directly from an alert, and preview the completion alarm. Playback failures SHALL leave visible feedback and preserve timer operation.

#### Scenario: Unacknowledged completion
- **WHEN** a pending completion remains unacknowledged for ten seconds
- **THEN** the app attempts one reminder, keeps the same event queue, and stops reminders after the queue is acknowledged

#### Scenario: Delayed checks and restored alerts
- **WHEN** a check is delayed beyond several reminder intervals or a saved pending event is restored
- **THEN** the app makes at most one immediate alert attempt without replaying missed reminders

#### Scenario: Muted persistent alert
- **WHEN** the user mutes the completion alarm from its dialog
- **THEN** current playback stops, later reminders remain visual only, and the saved sound preference is disabled

### Requirement: Phase-completion alert
The application SHALL show a clearly labeled completion dialog for every focus or break phase that ends while the application is running. It SHALL play an audible cue when sound is enabled and request desktop attention when the window is hidden or unfocused. The dialog SHALL remain discoverable until acknowledged, even when system notification or sound delivery is unavailable.

#### Scenario: Focus ends in background
- **WHEN** a focus phase ends while the window is minimized
- **THEN** the application requests desktop attention, attempts the enabled sound, and presents a focus-complete dialog when the window is viewed

#### Scenario: Sound unavailable
- **WHEN** sound is disabled or the audio device cannot play the cue
- **THEN** the visual dialog still appears and the timer transition still follows the selected setting

### Requirement: Configurable transition mode
The application SHALL offer an automatic mode, enabled by default, and a confirmation mode. In automatic mode the next phase SHALL start at completion while the completion dialog is shown. In confirmation mode the next phase SHALL remain idle until the user confirms start from the dialog. This preference SHALL apply to both focus-to-break and break-to-focus transitions.

#### Scenario: Automatic focus-to-break transition
- **WHEN** a focus phase ends with automatic mode enabled
- **THEN** the next break begins immediately and a focus-complete dialog is displayed

#### Scenario: Confirmation mode
- **WHEN** a break ends with confirmation mode enabled
- **THEN** the next focus phase remains idle until the user starts it from the break-complete dialog

### Requirement: Bounded unattended progression
The application SHALL avoid unbounded automatic cycles while a completion dialog remains unacknowledged. If the automatically started phase also ends before acknowledgement, the application SHALL stop at its completion, retain both completion events for review, and wait for acknowledgement before starting another phase.

#### Scenario: Unattended short break ends
- **WHEN** an automatic short break ends while its preceding focus-complete dialog is still unacknowledged
- **THEN** no new focus timer starts, both completions remain visible to the user, and acknowledging them allows the next focus phase to start according to the selected mode

### Requirement: No duplicate alert on redraw
Each completed phase SHALL generate one completion event and one logical dialog entry, regardless of recomposition, minimize/restore, or repeated timer checks.

#### Scenario: Repeated expiration checks
- **WHEN** multiple timer checks observe the same expired phase
- **THEN** the user receives one logical completion event for that phase

### Requirement: Sound preference
The application SHALL allow the audible cue to be enabled or disabled independently of dialog alerts and SHALL preserve that preference across restarts.

#### Scenario: Disable sound
- **WHEN** the user disables sound and a phase ends
- **THEN** no application cue is played and the completion dialog still appears

### Requirement: Independent button feedback
The application SHALL offer optional short audio feedback for timer and task controls independently of the phase-completion cue. Muting button feedback SHALL not mute completion sound or suppress a visual alert.

#### Scenario: Button clicks disabled
- **WHEN** the user disables button-click sound and starts or pauses a timer
- **THEN** those controls act normally without a click sound, while the completion-sound setting remains unchanged

# Windows release process

The release destination is `https://github.com/BharathKmalviya/Aggressive-Pomodoro`. Releases use immutable `vMAJOR.MINOR.PATCH` tags matching `desktopApp/resources/version.properties`. The Windows artifact is a runtime-bundled MSI; macOS and Linux packages are not produced.

## Gates before a tag

1. Confirm `LICENSE` contains the standard MIT License text with `Copyright (c) 2026 Bharath Malviya`, and keep the README and contribution guidance consistent. The release workflow verifies the exact license file before packaging.
2. Run the automated build and tests plus every scenario in [TESTING.md](TESTING.md) on Windows before raising the version. Record the results. In particular, verify sound, minimize, sleep, second instance, recovery, and the full four-focus cycle.
3. Add a `## MAJOR.MINOR.PATCH` section to `CHANGELOG.md`, raise the version resource to a version greater than every published release, review the current OpenSpec change, and run `openspec validate build-aggressive-pomodoro-desktop --strict`.
4. On a clean Windows environment, install the produced MSI, launch it without a separately installed Java runtime, check About version, and verify task and timer persistence. An unsigned installer may show a Windows warning; do not claim it is signed or trusted by Windows.

## Reproduce the package

Version 0.3.2 adds two optional sound IDs to snapshot format 3; back up the profile before rollback, because older builds ignore and can drop those choices on save. Nine adapted WAVs and sound credits must be present in the executable JAR; original source recordings remain local in `sounds/`, with hashes/provenance in its tracked manifest. Regeneration needs Python/FFmpeg, but normal CI/package builds use checked-in cues and need neither. Keep third-party recording licenses distinct from the MIT code license. Selectable-alarm Windows scenarios remain pending in [TESTING.md](TESTING.md#selectable-alarm-checks--2026-10-09), under the owner-authorized release scope below.

Version 0.3.2 keeps the app running until explicit Exit. For the upgraded build, the release upgrade harness requests a normal window close, locates the `Close Aggressive Pomodoro` dialog belonging to its tracked process/descendants, verifies foreground ownership, and uses the documented E shortcut to save and exit. The immutable v0.1.0 baseline still uses its original direct-close path. Forced termination is cleanup only and cannot pass the gate. Tray/keyboard/scaling/notification checks remain pending in [TESTING.md](TESTING.md#close-choice-and-background-checks--2026-10-09), under the release scope below.

Install JDK 21 and WiX 3.14.1 build tools on Windows. Add `candle.exe` and `light.exe` to `PATH`. Then run:

```powershell
.\scripts\package-windows.ps1 -Msi
Get-Content .\build\distribution\artifacts\SHA256SUMS.txt
Get-FileHash .\build\distribution\artifacts\AggressivePomodoro-0.3.5.msi -Algorithm SHA256
```

The script uses the checked-in Kotlin Toolchain wrapper, runs build and tests, builds the executable JAR, creates a runtime-bundled app image and MSI with `jpackage`, smoke-checks the app-image process, and writes a SHA-256 checksum for the exact MSI. It places the MIT License alongside the artifacts and passes it to the MSI packager. The versioned filename follows `desktopApp/resources/version.properties`; replace `0.3.5` in the example after a version change.

CI and releases also run `./scripts/verify-update-download.ps1` after packaging. This network-only gate compiles `UpdateDownloadProbe.java` against the executable JAR's bundled libraries, checks GitHub and downloads/reverifies the latest published stable MSI through the production updater service. Its isolated files stay under `build/updater-verification`; it opens no app/installer and touches no user profile. The gate needs JDK 21 and reachable GitHub/CDN servers. It is not proof of native dialog, UAC or installed-runtime interaction.

## v0.3.5 updater correction scope

This patch continues the owner's release request after the v0.3.3 updater stalled while downloading v0.3.4. Diagnostics reproduced Java's timeout on the first system-resolved release-assets address; three other addresses connected and curl downloaded successfully. Use system DNS with alternate-address fallback, cancellable blocking IO, explicit download stages and actionable retry/errors. Do not change machine DNS, pin GitHub IPs or relax TLS/redirect/checksum validation. Publish after the exact commit's mandatory automated Windows gates and the packaged updater network probe pass, then verify the immutable tag and all assets independently. The prior installed updater cannot download its own correction on an affected route: provide a one-time direct MSI upgrade, with explicit Exit before installation. Keep snapshot format 3, UpgradeCode and active OpenSpec unchanged. Native updater stage/cancel/retry, completion priority, keyboard/scaling and interactive Install & Exit/UAC acceptance remains pending in TESTING.md.

## v0.3.4 correction scope

This patch continues the owner's requested tray improvement and release after saved close options. Following v0.3.3, the owner reported that nearly every menu option appeared disabled and the menu did not feel ready for use, then requested intuitive launcher reopening in place of the Already running message. Correct blanket close-choice blocking, make summaries actionable, group timer/alert commands, add direct background operation, explicit save-error continuation and bounded local activation of the existing owner from subsequent launches. Keep snapshot format 3, installer UpgradeCode, phase-bound commands, completion priority and explicit tray Exit intact. Publish only after the exact commit's automated Windows build/tests/runtime packaging/clean MSI install and launch/baseline upgrade/data-preservation/checksum/license gates pass, then independently read back and verify the immutable tag and assets. The clean-install smoke also launches a second packaged process, requires it to exit successfully with a matching activation acknowledgement, and requires the existing owner to stay alive; it does not prove foreground focus. The reported screenshots establish the usability issues; updated launcher focus/native behavior, keyboard/scaling and recovery scenarios remain manual acceptance in `TESTING.md`, not automated proof. Keep OpenSpec active.

## v0.3.3 release scope

On 2026-10-09 the owner requested release after adding saved close options, including the previously implemented tray expansion. This authorizes v0.3.3 publication through the mandatory Windows build/tests/runtime-package/clean MSI install and launch/baseline upgrade/data preservation/checksum/license gates. Native close-choice/tray/tooltip/keyboard/scaling/audio/sleep/updater acceptance remains pending in `TESTING.md`; this request does not mark those manual checks passed. Keep the active OpenSpec change open.

Snapshot format remains 3 and the pinned installer UpgradeCode is unchanged. `closeBehavior` is optional; absent/unknown values default to Ask without discarding valid data. The baseline upgrade profile has no close preference and still opens the close chooser for the harness's explicit Exit. Earlier format-3 builds ignore and may drop this preference on save. Exit and back up `session.properties` before upgrading or rolling back. The installer remains unsigned. Publication evidence must identify the exact validated commit, immutable tag and freshly verified MSI/checksum/license assets.

## v0.3.2 release scope

On 2026-10-09 the owner requested release of the close/background choice and selectable supplied sounds after source build, all 111 tests, executable JAR packaging, strict OpenSpec validation and Windows CI passed, with native manual acceptance disclosed as pending. This authorizes publication through the existing automated gates and is an exception for this release to manual acceptance timing, not a manual pass. Automated Windows build/tests, runtime packaging, clean MSI installation/launch, baseline upgrade/data preservation, checksum, and MIT License gates remain mandatory. Publication evidence is recorded below.

Version 0.3.2 includes every-state close choices, retained-window tray background operation with a taskbar fallback, nine offline sound choices, separate saved focus/break alarms, unsaved previews, bounded resource decoding and original-alarm fallback. All third-party sound credits travel inside the application. Snapshot format remains 3; v0.3.1 can read timer/tasks/history but ignores and may drop new sound choices on save. Close the app using explicit Exit and back up `%APPDATA%\AggressivePomodoro\session.properties` before upgrading or rolling back. The installer remains unsigned; audio quality, tray notifications, keyboard/scaling, sleep and interactive updater/UAC acceptance stay pending. The active OpenSpec change remains open.

## v0.3.1 release scope

On 2026-10-01 the owner requested a new release after the saved-rules fixes passed build, all 106 tests, executable JAR packaging, and strict OpenSpec validation, with native Windows manual acceptance disclosed as pending. This authorizes publication through the existing automated gates and is an exception for this release to manual acceptance timing, not a manual pass. Automated Windows build/tests, runtime packaging, clean MSI installation/launch, baseline upgrade/data preservation, checksum, and MIT License gates remain mandatory.

Version 0.3.1 includes refreshed idle/waiting durations on Save Rules, latest saved durations on Reset Block, stale unstarted snapshot repair, truthful reset confirmation, and embedded selectable release notes. Running/paused progress and task/history credit stay intact. Snapshot format remains 3 with v0.2.0/v0.3.0 readability; close the app and back up `session.properties` before upgrade or rollback. Installer signing and manual UI, audio, keyboard, sleep, and interactive updater/UAC acceptance remain unchanged. The active OpenSpec change stays open for its remaining manual gates.

## v0.3.0 release scope

On 2026-10-01 the owner requested publication of the sound/motion update after the outstanding Windows manual checks were disclosed. This is an exception for this release to the manual acceptance timing above, not a manual test pass. Automated Windows build/tests, runtime packaging, clean MSI install/launch, baseline upgrade/data preservation, checksums, and MIT License gates remain mandatory. The active OpenSpec change stays open for the remaining manual acceptance.

This release bundles Kenney CC0 button feedback and its license, distinct focus/break completion alarms and previews, bounded timer animations, saved Reduce motion, and hardened playback cancellation/cleanup. Snapshot format stays at 3. v0.2.0 ignores the optional new motion property and can read the same timer/tasks/history; close the app and back up `session.properties` before installation or rollback. v0.1.0 still requires a pre-format-3 backup. Installer signing status and manual audio, keyboard, minimize/sleep, and interactive updater/UAC acceptance are unchanged.

## CI and publication

`.github/workflows/ci.yml` runs build, tests, app-image packaging, and a process smoke check for pushes and pull requests on Windows. On every push to `main`, `.github/workflows/release.yml` first checks whether `version.properties` names a new version. An already published version is skipped without packaging. A new version runs release prerequisites, pinned and checksum-verified WiX, build and tests, MSI installation and launch on the runner, artifact checksum verification, and MIT License verification. After those jobs pass, the workflow publishes the MSI, checksum, license, and notes for that version from the validated commit. A failed validation cannot publish. The `release` environment records publication but has no required reviewer; GitHub release immutability protects published tags and assets. `workflow_dispatch` on `main` can retry a failed run while the version remains unpublished.

Do not create or move release tags by hand. If a matching tag exists without a published release, automation stops for investigation. Update the version and changelog only after the manual acceptance checks, then push to `main`. Ordinary code pushes with the same published version do not create another release.

After publication, read the release page back and download the MSI and checksum from a fresh browser session. Verify the hash and clean-machine launch. If a release fails after publication, document the failure and publish a corrected version; immutable release assets and tags cannot be replaced.

## First release verification

On 2026-09-28, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36442587045) and the [v0.1.0 release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36442586887) passed. The release workflow built and tested the app, installed and launched the MSI on a fresh Windows runner, checked the MSI and MIT License, and published the [immutable v0.1.0 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.1.0) from commit `98d92dd469f96c1be4af9a3256e1e5676621b07b`. A separate download of all three release assets verified the MSI SHA-256 as `96ea04e7893d8f8cd9d9ac070dd014ece6ecacb6e866658c625e369459e28bb6` against `SHA256SUMS.txt` and the license against its pinned hash. A [repeat dispatch](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36443480071) skipped packaging and publication for the existing version.

The hands-on desktop scenarios in [TESTING.md](TESTING.md), including sound, minimize, sleep, and keyboard behavior, still need a recorded Windows acceptance pass.

## v0.2.0 release scope

The owner requested publication of the aggressive-interface update after being informed that the 62 automated tests passed and the Windows manual checks were still pending. This release proceeds through the existing automated Windows packaging, installation, launch, checksum, and license gates. That request is an exception for this release to the manual acceptance timing above; it does not count as a manual test pass or waive automated gates. The OpenSpec change stays active until its remaining acceptance work is complete.

Snapshot format advances from version 2 to 3. Close the app and back up `%APPDATA%\AggressivePomodoro\session.properties` before upgrading if rollback may be needed. v0.1.0 cannot read version 3; restore a pre-upgrade backup before running it again. Do not launch the older app against the migrated file, because its unsupported-format recovery starts fresh.

The same release includes the subsequently requested manual updater and GitHub About link. Keep the exact `AggressivePomodoro-VERSION.msi` and `SHA256SUMS.txt` asset naming: the updater validates the repository, numeric stable tag, versioned filenames, and checksum. Never change the pinned Windows UpgradeCode; it identifies upgrades to the same installed product. User-controlled in-app installation re-verifies the download, saves and backs up state, opens interactive Windows Installer, and exits. It does not bypass UAC or promise automatic relaunch.

The published v0.1.0 MSI has UpgradeCode `{8B4BB341-127A-3A18-945B-B92F5C0CD1FD}` and ProductCode `{50B90F77-B8D1-3BDD-AC8F-48DF142D69B8}`. Packaging now verifies the UpgradeCode, product name, and version. The release workflow additionally installs that checksum-pinned baseline, upgrades it to the new MSI, verifies old-product removal, and checks that a paused session's task/history snapshot survives in an isolated profile. This augments the clean-install launch gate.

The first v0.2.0 release attempt passed build, tests, packaging, and clean installation, but its upgrade harness could not close the window through `Process.CloseMainWindow`. Publication was blocked. The jpackage launcher can place the UI in a child process. The harness now waits for the titled window owned by the launch or its descendants with the same executable path (including hidden windows), posts the normal Windows close request, and requires a successful process exit before validating saved state; forced termination is cleanup only and cannot pass the gate.

## v0.2.0 published verification

On 2026-09-28, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36462170882) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36462170953) passed for commit `e93020082591517574ac35efbd1f48b9f2c00405`. The workflow verified clean MSI installation and launch, then upgraded the published v0.1.0 installation while preserving the paused timer, captured task, earned task blocks, and daily history. Old-product removal and migrated snapshot format 3 were checked before publication.

The [immutable v0.2.0 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.2.0) was published at `2026-09-28T18:05:37Z`. Fresh API readback confirmed its tag points to that exact validated commit. An independent download of all three public assets verified their sizes and GitHub SHA-256 digests, the MSI against `SHA256SUMS.txt`, and the MIT License against its pinned hash:

- `AggressivePomodoro-0.2.0.msi`: 93,455,256 bytes; SHA-256 `5f26b6ad03a8141d0fe80fc04ed1db564737eaa6168fd972e757b0a356dabfa7`.
- `SHA256SUMS.txt`: SHA-256 `92a4f8911d7dd956ded166e5d60d36f06aebeb5abeaf1bc9d1605ca493a8d33c`.
- `LICENSE`: SHA-256 `cc9829233de2b0ba9f107178cf4da58612848a70f07b2344eab1f1ec6df1f3fc`.

The packaged updater also read the newly published release: version 0.2.0 correctly reported no newer update, and a simulated installed version 0.1.0 correctly detected the actual 0.2.0 MSI. No installer was executed on the development machine. The manual Windows acceptance gaps in [TESTING.md](TESTING.md), including audio, keyboard, sleep, and interactive UAC, remain open.

## v0.3.0 published verification

On 2026-10-01, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36872068972) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36872068933) passed for `acd7b7b3c61d7f1293b2d4f6d5d0053232addc93`. The release log records all 93 tests passing (32 shared, 61 desktop), zero failures. Runtime packaging, clean MSI installation/launch, upgrade from the checksum-pinned v0.1.0 installer, old-product removal, paused timer/task/history preservation, checksum, and MIT License gates all passed.

The [immutable v0.3.0 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.0) was published at 19:27:30 IST on 2026-10-01. Fresh API readback confirmed stable, published, immutable status and the tag's exact validated commit. Independent downloads of all three public assets matched their GitHub sizes/digests; the MSI also matched `SHA256SUMS.txt` and the MIT License matched its pinned hash:

- `AggressivePomodoro-0.3.0.msi`: 93,475,736 bytes; SHA-256 `61f64f9420fcfb856a2e9063484850a35c6403113254a4c4ef986b143519e50e`.
- `SHA256SUMS.txt`: 96 bytes; SHA-256 `1622639fe3f030641722193faff4c72e7ca4c2c4acd74e0f28b8e3d392e93ebf`.
- `LICENSE`: 1,072 bytes; SHA-256 `cc9829233de2b0ba9f107178cf4da58612848a70f07b2344eab1f1ec6df1f3fc`.

No installer was executed on the development machine. Manual sound/motion quality, keyboard, minimize/sleep, and interactive updater/UAC acceptance remain pending in [TESTING.md](TESTING.md). Snapshot format remains 3 and the active OpenSpec change remains open for its acceptance tasks.

## v0.3.1 published verification

On 2026-10-01, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36875954691) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/36875955080) passed for `2a57bff99ac4c27ca1dfaf69646bf690247ea699`. The release log confirms all 106 tests passed (39 shared, 67 desktop), zero failures. Runtime packaging, clean MSI installation/launch, baseline v0.1.0 upgrade, old-product removal, paused timer/task/history preservation, checksum, and MIT License gates passed before publication.

The [immutable v0.3.1 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.1) was published at 19:57:25 IST on 2026-10-01. Fresh API and remote-tag readback confirmed stable, published, immutable status and that the tag targets the validated commit. Independent downloads of all three assets matched their GitHub sizes/digests; the MSI also matched `SHA256SUMS.txt` and the MIT License matched its pinned hash:

- `AggressivePomodoro-0.3.1.msi`: 93,479,832 bytes; SHA-256 `33eb9193df97752cdd00e3ea9449b5d66c26b94647f35135094ce586a0cbf160`.
- `SHA256SUMS.txt`: 96 bytes; SHA-256 `84b28a7bee12448f56228d8417521fa65c2c6b7fbdc1b8a86fc6d4aa2bf28cab`.
- `LICENSE`: 1,072 bytes; SHA-256 `cc9829233de2b0ba9f107178cf4da58612848a70f07b2344eab1f1ec6df1f3fc`.

No installer was executed on the development machine. The saved-rules and embedded-note Windows manual checks and earlier native acceptance remain pending in [TESTING.md](TESTING.md). Snapshot format stays at 3 and the active OpenSpec change remains open.

## v0.3.2 published verification

On 2026-10-09, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37928822524) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37928822448) passed for `c17d48023fc087cf2588145025afd544ed417561`. Release logs record all 111 tests passing (40 shared, 71 desktop), zero failures. Runtime packaging, clean MSI installation/launch, v0.1.0 baseline upgrade, old-product removal, paused timer/task/history preservation, checksum and MIT License gates passed before publication. The upgraded app's new close-choice dialog was located, focused and exited normally during the upgrade gate.

The [immutable v0.3.2 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.2) was published at `2026-10-09T12:19:21Z` (17:49:21 IST). Fresh API/latest-release and remote-tag readback confirmed stable, published, immutable status and the exact validated tag commit. Independent downloads matched all three asset sizes and GitHub SHA-256 digests; the MSI also matched `SHA256SUMS.txt`, and the MIT License matched its pinned hash:

- `AggressivePomodoro-0.3.2.msi`: 101,585,816 bytes; SHA-256 `0e47b0ff415e6fc9df5d62110bec147e9461e7e9c2e64053c89bc44aa9df7625`.
- `SHA256SUMS.txt`: 96 bytes; SHA-256 `25ec986448082a8e76eb997fdee42832ee1303799d2b5438fa736d9b83c57af4`.
- `LICENSE`: 1,072 bytes; SHA-256 `cc9829233de2b0ba9f107178cf4da58612848a70f07b2344eab1f1ec6df1f3fc`.

Read-only Windows Installer database/cabinet inspection of the public MSI confirmed ProductVersion 0.3.2 and the pinned UpgradeCode. Its embedded executable JAR contains version 0.3.2 and all nine WAVs matching the tracked prepared hashes, PCM formats and eight-second limits. Bundled sound-credit content matches source after Git line-ending normalization. The MSI and app were not executed on the development machine. Native audio quality, tray notifications, keyboard/scaling, sleep and interactive updater/UAC checks remain pending in [TESTING.md](TESTING.md). Snapshot format stays at 3 and the active OpenSpec change remains open.

## v0.3.3 published verification

On 2026-10-09, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37935932956) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37935932971) passed for `7b5b5ef95a2bda642e3b74e5930ec22f6ec016f2`. Release logs record all 121 tests passing (40 shared, 81 desktop), zero failures. Runtime packaging, clean MSI installation/launch, v0.1.0 baseline upgrade, old-product removal, paused timer/task/history preservation, MSI checksum and pinned MIT License gates passed before publication.

The [immutable stable v0.3.3 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.3) was published at `2026-10-09T13:22:17Z` (18:52:17 IST). Fresh API/latest-release and tag readback confirmed the release and exact validated commit. Independent downloads verified all three GitHub asset sizes/digests, the MSI's `SHA256SUMS.txt` entry and the pinned MIT License:

- `AggressivePomodoro-0.3.3.msi`: 101,659,544 bytes; SHA-256 `10ffdcf45944af902c70856ee07ff41f16118bf31513f2722673e9a112f9d02f`.
- `SHA256SUMS.txt`: 96 bytes; SHA-256 `31d171608ec3f33d554432fe2a0f2f62ea4fb724b517525110ad104d9a7cf52f`.
- `LICENSE`: 1,072 bytes; SHA-256 `cc9829233de2b0ba9f107178cf4da58612848a70f07b2344eab1f1ec6df1f3fc`.

Read-only inspection of the downloaded MSI confirms ProductName AggressivePomodoro, ProductVersion 0.3.3 and UpgradeCode `{8B4BB341-127A-3A18-945B-B92F5C0CD1FD}`. The installer and native app were not executed on the development machine. Manual saved close-choice/tray/tooltip/keyboard/scaling/audio/sleep/updater/UAC acceptance remains pending in [TESTING.md](TESTING.md). Snapshot format stays at 3, installer signing status is unchanged, and the active OpenSpec change remains open.

## v0.3.4 published verification

On 2026-10-09, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37940027472) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37940027327) passed for `ebd50725c0b52d085d5fdf46842ab647be92e54d`. Release logs record all 131 tests passing (40 shared, 91 desktop), zero failures. Runtime packaging, clean MSI installation/launch, v0.1.0 baseline upgrade/old-product removal/paused timer/task/history preservation, checksum and pinned MIT License gates passed. The clean-install smoke launched a second packaged process: it exited successfully, the owner acknowledged the activation token and remained alive. This proves packaged activation delivery, not Windows foreground focus or native popup appearance.

The [immutable stable v0.3.4 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.4) was published at `2026-10-09T13:56:57Z` (19:26:57 IST). Fresh API/latest-release and tag readback confirmed the release and exact validated commit. Independent downloads verified all three GitHub asset sizes/digests, the MSI's `SHA256SUMS.txt` entry and pinned MIT License:

- `AggressivePomodoro-0.3.4.msi`: 101,704,600 bytes; SHA-256 `2fd09c445b3bef0261e235287d7aa6ceebe414c991daf3c2cedb367cd76d0fcd`.
- `SHA256SUMS.txt`: 96 bytes; SHA-256 `254699bd584d4db9f0b543f8497399d129ad67b2f0bd9c9962885277874789d2`.
- `LICENSE`: 1,072 bytes; SHA-256 `cc9829233de2b0ba9f107178cf4da58612848a70f07b2344eab1f1ec6df1f3fc`.

Read-only Windows Installer metadata confirms ProductName AggressivePomodoro, ProductVersion 0.3.4 and the unchanged UpgradeCode `{8B4BB341-127A-3A18-945B-B92F5C0CD1FD}`. Local executable JAR SHA-256 is `dcf5bbaf4fcc6e50d599973fd92a70cd7d62d12f0bd6b43caa8cd0dbabb85ae9`; inspection confirmed version 0.3.4 and the menu/activation/tray-mode classes. No native app or installer was executed on the development machine. Launcher focus, tray popup/keyboard/scaling, close-choice cancellation and save recovery remain manual acceptance in [TESTING.md](TESTING.md#tray-usability-correction-checks--2026-10-09); earlier manual gates also remain open. Snapshot format stays at 3 and the active OpenSpec change remains open.

## v0.3.5 published verification

On 2026-10-09, [Windows CI](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37946042697) and the [release workflow](https://github.com/BharathKmalviya/Aggressive-Pomodoro/actions/runs/37946042644) passed for `a2ba6f79ba8ad123ba4117508d8d7cb2caba8c04`. Both logs record all 138 tests passing (40 shared, 98 desktop), zero failures. Runtime packaging, clean MSI installation/launch, second-launch acknowledgement, baseline v0.1.0 upgrade/old-product removal/paused timer/task/history preservation, checksum and pinned MIT License gates passed. Both jobs also used the packaged production updater to download/reverify the complete v0.3.4 MSI (101,704,600 bytes; matching published SHA-256), with bundled dependency license checks and no UI or installation in that probe.

The [immutable stable v0.3.5 release](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.5) was published at `2026-10-09T14:45:44Z` (20:15:44 IST). Fresh API/latest-release and tag readback confirmed the release and exact validated commit. Independent downloads verified all three asset sizes/digests, the exact MSI entry in `SHA256SUMS.txt` and the pinned MIT License:

- `AggressivePomodoro-0.3.5.msi`: 103,027,608 bytes; SHA-256 `45c4d007b172a0bd6b6e6dd6d9b7764087df63e707ef3d829cdf853274434b96`.
- `SHA256SUMS.txt`: 96 bytes; SHA-256 `c30f5f06318dc6efd16c3e097cce55c994405c4d7199883a14293c33c00466ae`.
- `LICENSE`: 1,072 bytes; SHA-256 `cc9829233de2b0ba9f107178cf4da58612848a70f07b2344eab1f1ec6df1f3fc`.

Read-only Windows Installer metadata confirms ProductName AggressivePomodoro, ProductVersion 0.3.5 and unchanged UpgradeCode `{8B4BB341-127A-3A18-945B-B92F5C0CD1FD}`. Local executable JAR SHA-256 is `8bf1acfc82563b250daeb6879a2853013b71cf4ec032dca9d13865aba953d053`; inspection verified version, OkHttp/Okio dependencies and bundled Apache license/attribution. The full updater probe also passed twice on the development machine's affected route. No native app or installer was run there. For a failing v0.3.3/v0.3.4 updater, [download this MSI directly](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/download/v0.3.5/AggressivePomodoro-0.3.5.msi), explicitly Exit, back up the profile and install once. Native updater/keyboard/scaling/UAC and earlier acceptance remain pending in [TESTING.md](TESTING.md#updater-connection-correction-checks--2026-10-09); snapshot format remains 3 and the OpenSpec change stays active.

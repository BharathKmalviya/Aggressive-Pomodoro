# Windows release process

The release destination is `https://github.com/BharathKmalviya/Aggressive-Pomodoro`. Releases use immutable `vMAJOR.MINOR.PATCH` tags matching `desktopApp/resources/version.properties`. The Windows artifact is a runtime-bundled MSI; macOS and Linux packages are not produced.

## Gates before a tag

1. Confirm `LICENSE` contains the standard MIT License text with `Copyright (c) 2026 Bharath Malviya`, and keep the README and contribution guidance consistent. The release workflow verifies the exact license file before packaging.
2. Run the automated build and tests plus every scenario in [TESTING.md](TESTING.md) on Windows before raising the version. Record the results. In particular, verify sound, minimize, sleep, second instance, recovery, and the full four-focus cycle.
3. Add a `## MAJOR.MINOR.PATCH` section to `CHANGELOG.md`, raise the version resource to a version greater than every published release, review the current OpenSpec change, and run `openspec validate build-aggressive-pomodoro-desktop --strict`.
4. On a clean Windows environment, install the produced MSI, launch it without a separately installed Java runtime, check About version, and verify task and timer persistence. An unsigned installer may show a Windows warning; do not claim it is signed or trusted by Windows.

## Reproduce the package

Install JDK 21 and WiX 3.14.1 build tools on Windows. Add `candle.exe` and `light.exe` to `PATH`. Then run:

```powershell
.\scripts\package-windows.ps1 -Msi
Get-Content .\build\distribution\artifacts\SHA256SUMS.txt
Get-FileHash .\build\distribution\artifacts\AggressivePomodoro-0.3.1.msi -Algorithm SHA256
```

The script uses the checked-in Kotlin Toolchain wrapper, runs build and tests, builds the executable JAR, creates a runtime-bundled app image and MSI with `jpackage`, smoke-checks the app-image process, and writes a SHA-256 checksum for the exact MSI. It places the MIT License alongside the artifacts and passes it to the MSI packager. The versioned filename follows `desktopApp/resources/version.properties`; replace `0.3.1` in the example after a version change.

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

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
Get-FileHash .\build\distribution\artifacts\AggressivePomodoro-0.2.0.msi -Algorithm SHA256
```

The script uses the checked-in Kotlin Toolchain wrapper, runs build and tests, builds the executable JAR, creates a runtime-bundled app image and MSI with `jpackage`, smoke-checks the app-image process, and writes a SHA-256 checksum for the exact MSI. It places the MIT License alongside the artifacts and passes it to the MSI packager. The versioned filename follows `desktopApp/resources/version.properties`; replace `0.2.0` in the example after a version change.

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

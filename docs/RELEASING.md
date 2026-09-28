# Windows release process

The release destination is `https://github.com/BharathKmalviya/Aggressive-Pomodoro`. Releases use immutable `vMAJOR.MINOR.PATCH` tags matching `desktopApp/resources/version.properties`. The first intended public artifact is a runtime-bundled Windows MSI; macOS and Linux packages are not produced.

## Gates before a tag

1. Confirm `LICENSE` contains the standard MIT License text with `Copyright (c) 2026 Bharath Malviya`, and keep the README and contribution guidance consistent. The release workflow verifies the exact license file before packaging.
2. Run the automated build and tests plus every scenario in [TESTING.md](TESTING.md) on Windows. Record the results. In particular, verify sound, minimize, sleep, second instance, recovery, and the full four-focus cycle.
3. Update `CHANGELOG.md` and the version resource. Review the current OpenSpec change and run `openspec validate build-aggressive-pomodoro-desktop --strict`.
4. On a clean Windows environment, install the produced MSI, launch it without a separately installed Java runtime, check About version, and verify task and timer persistence. An unsigned installer may show a Windows warning; do not claim it is signed or trusted by Windows.

## Reproduce the package

Install JDK 21 and WiX 3.14.1 build tools on Windows. Add `candle.exe` and `light.exe` to `PATH`. Then run:

```powershell
.\scripts\package-windows.ps1 -Msi
Get-Content .\build\distribution\artifacts\SHA256SUMS.txt
Get-FileHash .\build\distribution\artifacts\AggressivePomodoro-0.1.0.msi -Algorithm SHA256
```

The script uses the checked-in Kotlin Toolchain wrapper, runs build and tests, builds the executable JAR, creates a runtime-bundled app image and MSI with `jpackage`, smoke-checks the app-image process, and writes a SHA-256 checksum for the exact MSI. It places the MIT License alongside the artifacts and passes it to the MSI packager. The versioned filename follows `desktopApp/resources/version.properties`; replace `0.1.0` in the example after a version change.

## CI and publication

`.github/workflows/ci.yml` runs build, tests, app-image packaging, and a process smoke check for pushes and pull requests on Windows. `.github/workflows/release.yml` is manually dispatched from a version tag. Its validation job checks release prerequisites, pins and verifies WiX, builds and tests, installs and launches the MSI on the runner, verifies its checksum, and preserves the verified files as a short-lived workflow artifact. Only after that job succeeds does the publishing job request `release` environment approval. Once approved, it rechecks the artifact checksum and MIT License and publishes the MSI, checksum, and license to GitHub Releases. The repository's `release` environment requires approval by `BharathKmalviya` (verified 2026-09-28); confirm that protection remains active before dispatch because GitHub environment settings live outside this repository.

After publication, read the release page back and download the MSI and checksum from a fresh browser session. Verify the hash and clean-machine launch. If a release fails, remove the affected download from the release page, document the failure, fix it, and publish a new version tag; do not retarget an existing version tag.

# Aggressive Pomodoro

Aggressive Pomodoro is a Windows desktop focus timer built with Kotlin and Compose Multiplatform. It combines focus and break cycles, local tasks, completion alerts, and a record of completed work.

**Downloads:** [GitHub Releases](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases) lists verified Windows installers. Windows is the only packaged target at present.

## Features

- Focus, short break, and long break phases. The default schedule is 25 / 5 / 15 minutes, with a long break after four completed focus blocks. Durations and transition behavior are configurable.
- Start, pause, resume, reset, and skip controls. Automatic transitions can be disabled when each next phase should wait for confirmation. Completion alerts remain visible until acknowledged.
- Local tasks with estimated focus blocks. A completed focus block is credited to the task selected when that block began; skipped blocks receive no credit.
- Today's completed blocks and focused minutes, plus a report covering the current day and previous six calendar days. Reports use planned focus duration and the local date at completion.
- Independent completion and button-click sounds. A visual completion alert remains available if sound cannot play.

The timer continues while the window is minimized. It cannot alert after the application exits, and it does not block other applications.

## Run from source

Use Windows with JDK 21 available on `PATH`. The checked-in Kotlin Toolchain wrapper downloads its pinned build tool and dependencies on first use. In PowerShell, from the repository root:

```powershell
.\kotlin.bat build
.\kotlin.bat test
.\kotlin.bat run -m desktopApp
```

## Build a Windows package

The packaging script runs the build and tests, creates an app image with a bundled Java runtime, and checks that its process launches:

```powershell
.\scripts\package-windows.ps1
```

To create an MSI and `SHA256SUMS.txt`, install WiX 3.14.1 and put `candle.exe` and `light.exe` on `PATH`, then run:

```powershell
.\scripts\package-windows.ps1 -Msi
```

Output is written to `build/distribution/artifacts/`. Packaging does not publish a release. See the [release procedure](docs/RELEASING.md) for versioning, installer acceptance, and publication gates.

Obtain the MSI, checksum file, and MIT License from [GitHub Releases](https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases). Compare the MSI's SHA-256 with `SHA256SUMS.txt` before installing. The installer is unsigned and may prompt a Windows publisher warning. No macOS or Linux native package is provided.

## Data and recovery

On Windows, the application stores its timer state, settings, tasks, and daily totals in `%APPDATA%\AggressivePomodoro\session.properties`. Changes are saved without blocking the UI. After a restart, an expired phase is completed once; missed cycles are not backfilled. If the saved file cannot be read, the application starts a fresh focus session and shows a recovery message. See [Architecture](docs/ARCHITECTURE.md) for the persistence and clock rules.

Only one instance can use the saved state at a time. The application has no account, telemetry, or cloud sync.

## Project documentation

| Document | Contents |
| --- | --- |
| [Architecture](docs/ARCHITECTURE.md) | Module boundaries, timer rules, clocks, and persistence |
| [Windows acceptance checks](docs/TESTING.md) | Manual scenarios for UI, sound, recovery, and lifecycle behavior |
| [Release procedure](docs/RELEASING.md) | Packaging, checksums, CI gates, and publication |
| [Changelog](CHANGELOG.md) | Version history |
| [Contributing](CONTRIBUTING.md) | Issue, pull request, testing, and copyright guidance |
| [Community conduct](CODE_OF_CONDUCT.md) | Participation and reporting expectations |
| [Security policy](SECURITY.md) | Private vulnerability reporting |

## License

Aggressive Pomodoro is open source under the [MIT License](LICENSE). Copyright (c) 2026 Bharath Malviya. The license permits use, modification, and distribution, including commercial use, provided its notice is retained.
